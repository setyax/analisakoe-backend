package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.pur.entity.PurPriceList;
import id.analisakoe.pur.repository.PriceListRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/ui/purchasing/price-list")
public class UiPriceListController {

    private final MenuService menuService;
    private final PriceListRepository repo;

    @PersistenceContext
    private EntityManager em;

    public UiPriceListController(
            MenuService menuService,
            PriceListRepository repo) {
        this.menuService = menuService;
        this.repo = repo;
    }

    // ========================================================
    // SESSION DAN TENANT
    // ========================================================

    private SessionUser requireUser(HttpSession session) {
        SessionUser user = (SessionUser)
            session.getAttribute(SessionInterceptor.SESSION_USER);

        if (user == null || user.getTenantId() == null) {
            throw new IllegalStateException(
                "Session login atau Tenant ID tidak ditemukan."
            );
        }

        return user;
    }

    private void prepareBase(
            Model model,
            SessionUser user,
            String title) {

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute(
            "menus",
            menuService.buildTreeForUser(user.getPermissions())
        );
    }

    private LocalDate localDate(Object value) {
        if (value == null) return null;

        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }

        return LocalDate.parse(value.toString());
    }

    // ========================================================
    // SUPPLIER MASTER
    // ========================================================

    private List<Map<String, Object>> loadSuppliers(Long tenantId) {

        List<Object[]> rows = em.createNativeQuery(
            "SELECT s.id, s.name, COUNT(p.id) AS price_count " +
            "FROM md.supplier s " +
            "LEFT JOIN pur.price_list p " +
            "ON p.tenant_id = s.tenant_id " +
            "AND p.supplier_id = s.id " +
            "WHERE s.tenant_id = :tenantId " +
            "GROUP BY s.id, s.name " +
            "ORDER BY s.name"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("name", r[1]);
            m.put("priceCount", ((Number) r[2]).longValue());
            result.add(m);
        }

        return result;
    }

    private String supplierName(Long tenantId, Long supplierId) {

        List<?> rows = em.createNativeQuery(
            "SELECT name FROM md.supplier " +
            "WHERE id = :id AND tenant_id = :tenantId"
        )
        .setParameter("id", supplierId)
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                "Supplier tidak ditemukan dalam tenant aktif: " + supplierId
            );
        }

        return rows.get(0).toString();
    }

    // ========================================================
    // PRICE LIST
    // ========================================================

    @GetMapping
    public String list(HttpSession session, Model model) {

        SessionUser user = requireUser(session);
        Long tenantId = user.getTenantId();

        prepareBase(model, user, "Price List");

        String sql =
            "SELECT p.id, p.supplier_id, s.name, " +
            "p.item_id, i.code, i.name, " +
            "p.price, p.currency, p.valid_from, p.valid_to, " +
            "p.min_qty, p.uom_id, COALESCE(u.code, '-') " +
            "FROM pur.price_list p " +
            "LEFT JOIN md.supplier s ON s.id = p.supplier_id " +
            "LEFT JOIN md.item i ON i.id = p.item_id " +
            "LEFT JOIN md.uom u ON u.id = p.uom_id " +
            "WHERE p.tenant_id = :tenantId " +
            "ORDER BY p.id DESC";

        List<Object[]> rows = em.createNativeQuery(sql)
            .setParameter("tenantId", tenantId)
            .getResultList();

        List<Map<String, Object>> prices = new ArrayList<>();

        for (Object[] r : rows) {

            Map<String, Object> m = new HashMap<>();

            m.put("id", r[0]);
            m.put("supplierId", r[1]);
            m.put("supplierName", r[2] == null ? "-" : r[2]);
            m.put("itemId", r[3]);
            m.put("itemCode", r[4] == null ? "-" : r[4]);
            m.put("itemName", r[5] == null ? "-" : r[5]);
            m.put("price", r[6]);
            m.put("currency", r[7] == null ? "IDR" : r[7]);
            m.put("validFrom", localDate(r[8]));
            m.put("validTo", localDate(r[9]));
            m.put("minQty", r[10]);
            m.put("uomId", r[11]);
            m.put("uomCode", r[12]);

            prices.add(m);
        }

        model.addAttribute("prices", prices);

        return "ui/purchasing/price_list";
    }

    // ========================================================
    // NEW PRICE ENTRY
    // ========================================================

    @GetMapping("/new")
    public String newForm(
            HttpSession session,
            Model model) {

        SessionUser user = requireUser(session);
        Long tenantId = user.getTenantId();

        prepareBase(model, user, "New Price Entry");

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", true);

        loadMaster(model, tenantId);

        return "ui/purchasing/price_form";
    }

    // ========================================================
    // EDIT PRICE ENTRY
    // ========================================================

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        SessionUser user = requireUser(session);

        PurPriceList p = repo.findById(id).orElse(null);

        if (p == null ||
            !user.getTenantId().equals(p.getTenantId())) {

            ra.addFlashAttribute(
                "error",
                "Price entry tidak ditemukan."
            );

            return "redirect:/ui/purchasing/price-list";
        }

        prepareBase(model, user, "Edit Price Entry");

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", false);
        model.addAttribute("priceEntry", p);

        loadMaster(model, user.getTenantId());

        return "ui/purchasing/price_form";
    }

    private void loadMaster(Model model, Long tenantId) {

        List<Object[]> suppliersRaw = em.createNativeQuery(
            "SELECT id, name FROM md.supplier " +
            "WHERE tenant_id = :tenantId ORDER BY name"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String, Object>> suppliers = new ArrayList<>();

        for (Object[] r : suppliersRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r[0]);
            m.put("name", r[1]);
            suppliers.add(m);
        }

        model.addAttribute("suppliers", suppliers);

        List<Object[]> itemsRaw = em.createNativeQuery(
            "SELECT id, code, name FROM md.item " +
            "WHERE tenant_id = :tenantId " +
            "AND deleted_at IS NULL ORDER BY name"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String, Object>> items = new ArrayList<>();

        for (Object[] r : itemsRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r[0]);
            m.put("code", r[1]);
            m.put("name", r[2]);
            items.add(m);
        }

        model.addAttribute("items", items);

        List<Object[]> uomRaw = em.createNativeQuery(
            "SELECT id, code, name FROM md.uom " +
            "WHERE tenant_id = :tenantId ORDER BY code"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String, Object>> uoms = new ArrayList<>();

        for (Object[] r : uomRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r[0]);
            m.put("code", r[1]);
            m.put("name", r[2]);
            uoms.add(m);
        }

        model.addAttribute("uoms", uoms);
    }

    // ========================================================
    // SAVE PRICE ENTRY
    // ========================================================

    @PostMapping
    @Transactional
    public String save(
            @RequestParam Long supplierId,
            @RequestParam Long itemId,
            @RequestParam Long uomId,
            @RequestParam BigDecimal price,
            @RequestParam(defaultValue = "IDR") String currency,
            @RequestParam LocalDate validFrom,
            @RequestParam(required = false) LocalDate validTo,
            @RequestParam(defaultValue = "1") BigDecimal minQty,
            @RequestParam(required = false) Long id,
            HttpSession session,
            RedirectAttributes ra) {

        try {

            SessionUser user = requireUser(session);
            Long tenantId = user.getTenantId();

            if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Harga tidak valid.");
            }

            if (minQty == null || minQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Min Qty harus lebih dari nol.");
            }

            if (validTo != null && validTo.isBefore(validFrom)) {
                throw new IllegalArgumentException(
                    "Valid To tidak boleh lebih awal dari Valid From."
                );
            }

            supplierName(tenantId, supplierId);

            Long itemCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.item " +
                "WHERE id=:id AND tenant_id=:tenantId " +
                "AND deleted_at IS NULL"
            )
            .setParameter("id", itemId)
            .setParameter("tenantId", tenantId)
            .getSingleResult()).longValue();

            if (itemCount == 0) {
                throw new IllegalArgumentException("Item tidak ditemukan.");
            }

            Long uomCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.uom " +
                "WHERE id=:id AND tenant_id=:tenantId"
            )
            .setParameter("id", uomId)
            .setParameter("tenantId", tenantId)
            .getSingleResult()).longValue();

            if (uomCount == 0) {
                throw new IllegalArgumentException("UOM tidak ditemukan.");
            }

            PurPriceList p;

            if (id != null) {
                p = repo.findById(id).orElse(null);

                if (p == null ||
                    !tenantId.equals(p.getTenantId())) {
                    throw new IllegalArgumentException(
                        "Price entry tidak ditemukan."
                    );
                }
            } else {
                p = new PurPriceList();
            }

            p.setTenantId(tenantId);
            p.setSupplierId(supplierId);
            p.setItemId(itemId);
            p.setUomId(uomId);
            p.setPrice(price);
            p.setCurrency(currency);
            p.setValidFrom(validFrom);
            p.setValidTo(validTo);
            p.setMinQty(minQty);

            repo.save(p);

            ra.addFlashAttribute(
                "success",
                "Price entry berhasil disimpan."
            );

        } catch (Exception e) {
            ra.addFlashAttribute(
                "error",
                "Gagal menyimpan harga: " + e.getMessage()
            );
        }

        return "redirect:/ui/purchasing/price-list";
    }

    // ========================================================
    // DELETE PRICE ENTRY
    // ========================================================

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra) {

        try {

            SessionUser user = requireUser(session);

            int deleted = em.createNativeQuery(
                "DELETE FROM pur.price_list " +
                "WHERE id=:id AND tenant_id=:tenantId"
            )
            .setParameter("id", id)
            .setParameter("tenantId", user.getTenantId())
            .executeUpdate();

            if (deleted == 0) {
                throw new IllegalArgumentException(
                    "Price entry tidak ditemukan."
                );
            }

            ra.addFlashAttribute(
                "success",
                "Price entry berhasil dihapus."
            );

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/ui/purchasing/price-list";
    }

    // ========================================================
    // WIZARD: LOAD FORM
    // ========================================================

    @GetMapping("/copy-wizard")
    public String copyWizard(
            HttpSession session,
            Model model) {

        SessionUser user = requireUser(session);

        prepareBase(model, user, "Wizard Copy Supplier Price List");

        model.addAttribute(
            "suppliers",
            loadSuppliers(user.getTenantId())
        );

        return "ui/purchasing/price_copy_wizard";
    }

    // ========================================================
    // VALIDATE SOURCE AND TARGET SUPPLIERS
    // ========================================================

    private List<Long> validateTargets(
            Long tenantId,
            Long sourceId,
            List<Long> targetSupplierIds) {

        if (sourceId == null) {
            throw new IllegalArgumentException(
                "Supplier sumber wajib dipilih."
            );
        }

        if (targetSupplierIds == null ||
            targetSupplierIds.isEmpty()) {

            throw new IllegalArgumentException(
                "Pilih minimal satu supplier tujuan."
            );
        }

        supplierName(tenantId, sourceId);

        Long sourceCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pur.price_list " +
            "WHERE tenant_id=:tenantId AND supplier_id=:sourceId"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("sourceId", sourceId)
        .getSingleResult()).longValue();

        if (sourceCount == 0) {
            throw new IllegalArgumentException(
                "Supplier sumber belum mempunyai Price List."
            );
        }

        Set<Long> unique = new LinkedHashSet<>(targetSupplierIds);

        List<Long> targets = new ArrayList<>();

        for (Long targetId : unique) {

            if (targetId == null) continue;

            if (sourceId.equals(targetId)) {
                throw new IllegalArgumentException(
                    "Supplier sumber tidak boleh menjadi supplier tujuan."
                );
            }

            supplierName(tenantId, targetId);

            targets.add(targetId);
        }

        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                "Supplier tujuan tidak valid."
            );
        }

        return targets;
    }

    // ========================================================
    // COUNT SOURCE PRICE
    // ========================================================

    private Long sourcePriceCount(Long tenantId, Long sourceId) {

        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pur.price_list " +
            "WHERE tenant_id=:tenantId AND supplier_id=:sourceId"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("sourceId", sourceId)
        .getSingleResult()).longValue();
    }

    // ========================================================
    // COUNT COPYABLE PRICE
    // ========================================================

    private Long countCopyable(
            Long tenantId,
            Long sourceId,
            Long targetId) {

        String sql =
            "SELECT COUNT(*) " +
            "FROM pur.price_list src " +
            "WHERE src.tenant_id=:tenantId " +
            "AND src.supplier_id=:sourceId " +
            "AND NOT EXISTS (" +
            " SELECT 1 FROM pur.price_list dst " +
            " WHERE dst.tenant_id=src.tenant_id " +
            " AND dst.supplier_id=:targetId " +
            " AND dst.item_id=src.item_id " +
            " AND dst.uom_id IS NOT DISTINCT FROM src.uom_id " +
            " AND dst.currency IS NOT DISTINCT FROM src.currency " +
            " AND dst.valid_from=src.valid_from " +
            " AND dst.valid_to IS NOT DISTINCT FROM src.valid_to " +
            " AND dst.min_qty IS NOT DISTINCT FROM src.min_qty " +
            ")";

        return ((Number) em.createNativeQuery(sql)
            .setParameter("tenantId", tenantId)
            .setParameter("sourceId", sourceId)
            .setParameter("targetId", targetId)
            .getSingleResult()).longValue();
    }

    // ========================================================
    // WIZARD: PREVIEW
    // ========================================================

    @PostMapping("/copy-wizard/preview")
    public String copyPreview(
            @RequestParam Long sourceSupplierId,
            @RequestParam(required = false)
                List<Long> targetSupplierIds,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        try {

            SessionUser user = requireUser(session);
            Long tenantId = user.getTenantId();

            List<Long> targets = validateTargets(
                tenantId,
                sourceSupplierId,
                targetSupplierIds
            );

            prepareBase(
                model,
                user,
                "Preview Copy Supplier Price List"
            );

            Long totalSource = sourcePriceCount(
                tenantId,
                sourceSupplierId
            );

            List<Map<String, Object>> plans = new ArrayList<>();

            long totalNew = 0;
            long totalSkipped = 0;

            for (Long targetId : targets) {

                Long newCount = countCopyable(
                    tenantId,
                    sourceSupplierId,
                    targetId
                );

                Long skipped = totalSource - newCount;

                Map<String, Object> plan = new HashMap<>();

                plan.put("supplierId", targetId);
                plan.put("supplierName", supplierName(tenantId, targetId));
                plan.put("sourceCount", totalSource);
                plan.put("newCount", newCount);
                plan.put("skipCount", skipped);

                plans.add(plan);

                totalNew += newCount;
                totalSkipped += skipped;
            }

            model.addAttribute("sourceSupplierId", sourceSupplierId);
            model.addAttribute(
                "sourceSupplierName",
                supplierName(tenantId, sourceSupplierId)
            );

            model.addAttribute("targetSupplierIds", targets);
            model.addAttribute("plans", plans);
            model.addAttribute("totalSource", totalSource);
            model.addAttribute("totalNew", totalNew);
            model.addAttribute("totalSkipped", totalSkipped);

            return "ui/purchasing/price_copy_preview";

        } catch (Exception e) {

            ra.addFlashAttribute(
                "error",
                "Preview gagal: " + e.getMessage()
            );

            return "redirect:/ui/purchasing/price-list/copy-wizard";
        }
    }

    // ========================================================
    // WIZARD: EXECUTE COPY
    // ========================================================

    @PostMapping("/copy-wizard/execute")
    @Transactional
    public String executeCopy(
            @RequestParam Long sourceSupplierId,
            @RequestParam(required = false)
                List<Long> targetSupplierIds,
            HttpSession session,
            RedirectAttributes ra) {

        try {

            SessionUser user = requireUser(session);
            Long tenantId = user.getTenantId();

            List<Long> targets = validateTargets(
                tenantId,
                sourceSupplierId,
                targetSupplierIds
            );

            String insertSql =
                "INSERT INTO pur.price_list " +
                "(tenant_id, supplier_id, item_id, price, currency, " +
                "valid_from, valid_to, min_qty, uom_id, created_at, updated_at) " +
                "SELECT src.tenant_id, :targetId, src.item_id, " +
                "src.price, src.currency, src.valid_from, src.valid_to, " +
                "src.min_qty, src.uom_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                "FROM pur.price_list src " +
                "WHERE src.tenant_id=:tenantId " +
                "AND src.supplier_id=:sourceId " +
                "AND NOT EXISTS (" +
                " SELECT 1 FROM pur.price_list dst " +
                " WHERE dst.tenant_id=src.tenant_id " +
                " AND dst.supplier_id=:targetId " +
                " AND dst.item_id=src.item_id " +
                " AND dst.uom_id IS NOT DISTINCT FROM src.uom_id " +
                " AND dst.currency IS NOT DISTINCT FROM src.currency " +
                " AND dst.valid_from=src.valid_from " +
                " AND dst.valid_to IS NOT DISTINCT FROM src.valid_to " +
                " AND dst.min_qty IS NOT DISTINCT FROM src.min_qty " +
                ")";

            long totalInserted = 0;
            long totalSkipped = 0;
            long totalSource = sourcePriceCount(
                tenantId,
                sourceSupplierId
            );

            List<String> resultLines = new ArrayList<>();

            for (Long targetId : targets) {

                int inserted = em.createNativeQuery(insertSql)
                    .setParameter("tenantId", tenantId)
                    .setParameter("sourceId", sourceSupplierId)
                    .setParameter("targetId", targetId)
                    .executeUpdate();

                long skipped = Math.max(
                    0,
                    totalSource - inserted
                );

                totalInserted += inserted;
                totalSkipped += skipped;

                resultLines.add(
                    supplierName(tenantId, targetId) +
                    ": " + inserted + " berhasil, " +
                    skipped + " dilewati"
                );
            }

            ra.addFlashAttribute(
                "success",
                "Copy Supplier Price List selesai. Total berhasil: " +
                totalInserted + ", dilewati: " + totalSkipped + ". " +
                String.join(" | ", resultLines)
            );

        } catch (Exception e) {

            TransactionAspectSupport
                .currentTransactionStatus()
                .setRollbackOnly();

            ra.addFlashAttribute(
                "error",
                "Copy dibatalkan: " + e.getMessage()
            );
        }

        return "redirect:/ui/purchasing/price-list";
    }
}