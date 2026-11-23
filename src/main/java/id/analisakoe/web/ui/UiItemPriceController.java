package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/master/item-price")
public class UiItemPriceController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiItemPriceController(MenuService menuService) {
        this.menuService = menuService;
    }

    private SessionUser getUser(HttpSession session) {
        SessionUser user = (SessionUser)
            session.getAttribute(SessionInterceptor.SESSION_USER);

        if (user == null ||
            user.getTenantId() == null ||
            user.getActiveCompanyId() == null) {
            throw new IllegalStateException(
                "Session, tenant, atau company aktif tidak ditemukan."
            );
        }

        return user;
    }

    private void prepareModel(
            HttpSession session,
            Model model,
            String title) {

        SessionUser user = getUser(session);

        Long tenantId = user.getTenantId();
        Long companyId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute(
            "menus",
            menuService.buildTreeForUser(user.getPermissions())
        );

        // MASTER ITEMS
        List<Object[]> itemRaw = em.createNativeQuery(
            "SELECT id, code, name " +
            "FROM md.item " +
            "WHERE tenant_id = :tenantId " +
            "AND deleted_at IS NULL " +
            "ORDER BY name"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String, Object>> items = new ArrayList<>();

        for (Object[] r : itemRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r[0]);
            m.put("code", r[1]);
            m.put("name", r[2]);
            items.add(m);
        }

        model.addAttribute("items", items);

        // MASTER UOM
        List<Object[]> uomRaw = em.createNativeQuery(
            "SELECT id, code " +
            "FROM md.uom " +
            "WHERE tenant_id = :tenantId " +
            "ORDER BY code"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String, Object>> uoms = new ArrayList<>();

        for (Object[] r : uomRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r[0]);
            m.put("code", r[1]);
            uoms.add(m);
        }

        model.addAttribute("uoms", uoms);

        // ITEM PRICE
        List<Object[]> raw = em.createNativeQuery(
            "SELECT ip.id, i.code, i.name, " +
            "ip.uom_id, COALESCE(u.code, '-') AS uom_code, " +
            "ip.currency, ip.price, ip.valid_from, " +
            "ip.valid_to, ip.status, ip.item_id " +
            "FROM md.item_price ip " +
            "JOIN md.item i ON i.id = ip.item_id " +
            "LEFT JOIN md.uom u ON u.id = ip.uom_id " +
            "WHERE ip.tenant_id = :tenantId " +
            "AND ip.company_id = :companyId " +
            "ORDER BY i.code, ip.valid_from DESC"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("companyId", companyId)
        .getResultList();

        List<Map<String, Object>> prices = new ArrayList<>();

        for (Object[] o : raw) {
            Map<String, Object> m = new HashMap<>();

            m.put("id", o[0]);
            m.put("itemCode", o[1]);
            m.put("itemName", o[2]);
            m.put("uomId", o[3]);
            m.put("uomCode", o[4]);
            m.put("currency", o[5]);
            m.put("price", o[6]);
            m.put("validFrom", o[7] != null ? o[7].toString() : "");
            m.put("validTo", o[8] != null ? o[8].toString() : "");
            m.put("status", o[9]);
            m.put("itemId", o[10]);

            prices.add(m);
        }

        model.addAttribute("prices", prices);
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        try {
            prepareModel(session, model, "Master Item Price");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("prices", new ArrayList<>());
        }

        return "ui/master/item_price";
    }

    @PostMapping("/save")
    @Transactional
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam Long itemId,
            @RequestParam Long uomId,
            @RequestParam String currency,
            @RequestParam BigDecimal price,
            @RequestParam String validFrom,
            @RequestParam(required = false) String validTo,
            @RequestParam(defaultValue = "ACTIVE") String status,
            HttpSession session,
            RedirectAttributes ra) {

        try {
            SessionUser user = getUser(session);

            Long tenantId = user.getTenantId();
            Long companyId = user.getActiveCompanyId();

            if (price == null ||
                price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                    "Harga tidak boleh negatif."
                );
            }

            if (!"IDR".equals(currency) &&
                !"USD".equals(currency)) {
                throw new IllegalArgumentException(
                    "Currency tidak valid."
                );
            }

            if (!"ACTIVE".equals(status) &&
                !"INACTIVE".equals(status)) {
                throw new IllegalArgumentException(
                    "Status harga tidak valid."
                );
            }

            LocalDate from = LocalDate.parse(validFrom);

            LocalDate to =
                (validTo == null || validTo.isBlank())
                ? null
                : LocalDate.parse(validTo);

            if (to != null && to.isBefore(from)) {
                throw new IllegalArgumentException(
                    "Valid To tidak boleh lebih awal dari Valid From."
                );
            }

            // VALIDASI ITEM
            Long itemCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.item " +
                "WHERE id = :id " +
                "AND tenant_id = :tenantId " +
                "AND deleted_at IS NULL"
            )
            .setParameter("id", itemId)
            .setParameter("tenantId", tenantId)
            .getSingleResult()).longValue();

            if (itemCount == 0) {
                throw new IllegalArgumentException(
                    "Item tidak ditemukan untuk tenant aktif."
                );
            }

            // VALIDASI UOM
            Long uomCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.uom " +
                "WHERE id = :id AND tenant_id = :tenantId"
            )
            .setParameter("id", uomId)
            .setParameter("tenantId", tenantId)
            .getSingleResult()).longValue();

            if (uomCount == 0) {
                throw new IllegalArgumentException(
                    "UOM tidak ditemukan untuk tenant aktif."
                );
            }

            java.sql.Date vf = java.sql.Date.valueOf(from);
            java.sql.Date vt =
                to == null ? null : java.sql.Date.valueOf(to);

            // VALIDASI RECORD EDIT
            if (id != null) {
                Long owned = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM md.item_price " +
                    "WHERE id = :id " +
                    "AND tenant_id = :tenantId " +
                    "AND company_id = :companyId"
                )
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("companyId", companyId)
                .getSingleResult()).longValue();

                if (owned == 0) {
                    throw new IllegalArgumentException(
                        "Price entry tidak ditemukan untuk company aktif."
                    );
                }
            }

            // DUPLICATE CHECK
            String duplicateSql =
                "SELECT COUNT(*) FROM md.item_price " +
                "WHERE tenant_id = :tenantId " +
                "AND company_id = :companyId " +
                "AND item_id = :itemId " +
                "AND uom_id = :uomId " +
                "AND currency = :currency " +
                "AND valid_from = :validFrom ";

            if (id != null) {
                duplicateSql += "AND id <> :id ";
            }

            var duplicateQuery =
                em.createNativeQuery(duplicateSql)
                    .setParameter("tenantId", tenantId)
                    .setParameter("companyId", companyId)
                    .setParameter("itemId", itemId)
                    .setParameter("uomId", uomId)
                    .setParameter("currency", currency)
                    .setParameter("validFrom", vf);

            if (id != null) {
                duplicateQuery.setParameter("id", id);
            }

            Long duplicates =
                ((Number) duplicateQuery.getSingleResult()).longValue();

            if (duplicates > 0) {
                throw new IllegalArgumentException(
                    "Harga untuk Item, UOM, Currency dan tanggal ini sudah ada."
                );
            }

            if (id == null) {
                em.createNativeQuery(
                    "INSERT INTO md.item_price " +
                    "(tenant_id, company_id, item_id, uom_id, " +
                    "currency, price, valid_from, valid_to, status) " +
                    "VALUES " +
                    "(:tenantId, :companyId, :itemId, :uomId, " +
                    ":currency, :price, :validFrom, :validTo, :status)"
                )
                .setParameter("tenantId", tenantId)
                .setParameter("companyId", companyId)
                .setParameter("itemId", itemId)
                .setParameter("uomId", uomId)
                .setParameter("currency", currency)
                .setParameter("price", price)
                .setParameter("validFrom", vf)
                .setParameter("validTo", vt)
                .setParameter("status", status)
                .executeUpdate();

                ra.addFlashAttribute(
                    "success",
                    "Harga item dan UOM berhasil ditambahkan."
                );

            } else {
                em.createNativeQuery(
                    "UPDATE md.item_price SET " +
                    "item_id = :itemId, " +
                    "uom_id = :uomId, " +
                    "currency = :currency, " +
                    "price = :price, " +
                    "valid_from = :validFrom, " +
                    "valid_to = :validTo, " +
                    "status = :status " +
                    "WHERE id = :id " +
                    "AND tenant_id = :tenantId " +
                    "AND company_id = :companyId"
                )
                .setParameter("itemId", itemId)
                .setParameter("uomId", uomId)
                .setParameter("currency", currency)
                .setParameter("price", price)
                .setParameter("validFrom", vf)
                .setParameter("validTo", vt)
                .setParameter("status", status)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("companyId", companyId)
                .executeUpdate();

                ra.addFlashAttribute(
                    "success",
                    "Harga item berhasil diperbarui."
                );
            }

        } catch (Exception e) {
            ra.addFlashAttribute(
                "error",
                "Gagal menyimpan Item Price: " + e.getMessage()
            );
        }

        return "redirect:/ui/master/item-price";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {

        try {
            SessionUser user = getUser(session);

            int deleted = em.createNativeQuery(
                "DELETE FROM md.item_price " +
                "WHERE id = :id " +
                "AND tenant_id = :tenantId " +
                "AND company_id = :companyId"
            )
            .setParameter("id", id)
            .setParameter("tenantId", user.getTenantId())
            .setParameter("companyId", user.getActiveCompanyId())
            .executeUpdate();

            if (deleted == 0) {
                throw new IllegalArgumentException(
                    "Price entry tidak ditemukan."
                );
            }

            ra.addFlashAttribute(
                "success",
                "Harga item berhasil dihapus."
            );

        } catch (Exception e) {
            ra.addFlashAttribute(
                "error",
                "Gagal menghapus: " + e.getMessage()
            );
        }

        return "redirect:/ui/master/item-price";
    }
}
