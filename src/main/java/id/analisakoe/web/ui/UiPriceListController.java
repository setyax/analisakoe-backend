package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;
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
    private final ContextService context;

    @PersistenceContext
    private EntityManager em;

    public UiPriceListController(
            MenuService menuService,
            PriceListRepository repo,
            ContextService context) {
        this.menuService = menuService;
        this.repo = repo;
        this.context = context;
    }

    private Long tenantId() { return context.tenantId(); }
    private Long companyId() { return context.companyId(); }

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

    private List<Map<String, Object>> loadSuppliers() {

        List<Object[]> rows = em.createNativeQuery(
            "SELECT s.id, s.name, COUNT(p.id) AS price_count " +
            "FROM md.supplier s " +
            "LEFT JOIN pur.price_list p " +
            "ON p.tenant_id = s.tenant_id " +
            "AND p.company_id = s.company_id " +
            "AND p.supplier_id = s.id " +
            "WHERE s.tenant_id = :tenantId AND s.company_id = :companyId " +
            "GROUP BY s.id, s.name " +
            "ORDER BY s.name"
        )
        .setParameter("tenantId", tenantId())
        .setParameter("companyId", companyId())
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

    private String supplierName(Long supplierId) {

        List<?> rows = em.createNativeQuery(
            "SELECT name FROM md.supplier " +
            "WHERE id = :id AND tenant_id = :tenantId AND company_id = :companyId"
        )
        .setParameter("id", supplierId)
        .setParameter("tenantId", tenantId())
        .setParameter("companyId", companyId())
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

        prepareBase(model, user, "Price List");

        String sql =
            "SELECT p.id, p.supplier_id, s.name, " +
            "p.item_id, i.code, i.name, " +
            "p.price, p.currency, p.valid_from, p.valid_to, " +
            "p.min_qty, p.uom_id, COALESCE(u.code, '-'), " +
            "p.condition_type, p.price_unit, p.condition_source, " +
            "p.condition_class, p.rate, p.calculation_order " +
            "FROM pur.price_list p " +
            "LEFT JOIN md.supplier s ON s.id = p.supplier_id " +
            "LEFT JOIN md.item i ON i.id = p.item_id " +
            "LEFT JOIN md.uom u ON u.id = p.uom_id " +
            "WHERE p.tenant_id = :tenantId AND p.company_id = :companyId " +
            "ORDER BY p.id DESC";

        List<Object[]> rows = em.createNativeQuery(sql)
            .setParameter("tenantId", tenantId())
            .setParameter("companyId", companyId())
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
            m.put("conditionType", r[13] == null ? "PB00" : r[13]);
            m.put("priceUnit", r[14] == null ? BigDecimal.ONE : r[14]);
            m.put("conditionSource", r[15] == null ? "INFO_RECORD" : r[15]);
            m.put("conditionClass", r[16] == null ? "PRICE" : r[16]);
            m.put("rate", r[17]);
            m.put("calculationOrder", r[18]);

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

        prepareBase(model, user, "New Price Entry");

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", true);

        loadMaster(model);

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
            !tenantId().equals(p.getTenantId()) ||
            !companyId().equals(p.getCompanyId())) {

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

        loadMaster(model);

        return "ui/purchasing/price_form";
    }

    private void loadMaster(Model model) {

        List<Object[]> suppliersRaw = em.createNativeQuery(
            "SELECT id, name FROM md.supplier " +
            "WHERE tenant_id = :tenantId AND company_id = :companyId ORDER BY name"
        )
        .setParameter("tenantId", tenantId())
        .setParameter("companyId", companyId())
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
            "WHERE tenant_id = :tenantId AND company_id = :companyId " +
            "AND deleted_at IS NULL ORDER BY name"
        )
        .setParameter("tenantId", tenantId())
        .setParameter("companyId", companyId())
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
        .setParameter("tenantId", tenantId())
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
            @RequestParam(required = false, defaultValue = "PB00") String conditionType,
            @RequestParam(required = false, defaultValue = "1") BigDecimal priceUnit,
            @RequestParam(required = false, defaultValue = "INFO_RECORD") String conditionSource,
            @RequestParam(required = false, defaultValue = "PRICE") String conditionClass,
            @RequestParam(required = false) BigDecimal rate,
            @RequestParam(required = false, defaultValue = "10") Integer calculationOrder,
            @RequestParam(required = false) Long id,
            HttpSession session,
            RedirectAttributes ra) {

        try {

            requireUser(session);
            Long tenantId = tenantId();
            Long companyId = companyId();

            if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Harga tidak valid.");
            }

            if (minQty == null || minQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Min Qty harus lebih dari nol.");
            }

            if (priceUnit == null || priceUnit.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price Unit harus lebih dari nol.");
            }

            String cls = conditionClass == null ? "PRICE" : conditionClass.trim().toUpperCase();
            if (!java.util.List.of("PRICE", "DISCOUNT", "SURCHARGE", "TAX").contains(cls)) {
                throw new IllegalArgumentException("Condition Class tidak valid.");
            }
            if (rate != null && rate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Rate tidak boleh negatif.");
            }

            if (validTo != null && validTo.isBefore(validFrom)) {
                throw new IllegalArgumentException(
                    "Valid To tidak boleh lebih awal dari Valid From."
                );
            }

            supplierName(supplierId);

            Long itemCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.item " +
                "WHERE id=:id AND tenant_id=:tenantId AND company_id=:companyId " +
                "AND deleted_at IS NULL"
            )
            .setParameter("id", itemId)
            .setParameter("tenantId", tenantId)
            .setParameter("companyId", companyId)
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
                    !tenantId.equals(p.getTenantId()) ||
                    !companyId.equals(p.getCompanyId())) {
                    throw new IllegalArgumentException(
                        "Price entry tidak ditemukan."
                    );
                }
            } else {
                p = new PurPriceList();
            }

            p.setTenantId(tenantId);
            p.setCompanyId(companyId);
            p.setConditionType(conditionType == null || conditionType.isBlank()
                ? "PB00" : conditionType.trim().toUpperCase());
            p.setPriceUnit(priceUnit);
            p.setConditionSource(conditionSource == null || conditionSource.isBlank()
                ? "INFO_RECORD" : conditionSource.trim().toUpperCase());
            p.setConditionClass(conditionClass == null || conditionClass.isBlank()
                ? "PRICE" : conditionClass.trim().toUpperCase());
            p.setRate(rate);
            p.setCalculationOrder(calculationOrder == null ? 10 : calculationOrder);
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

            requireUser(session);

            int deleted = em.createNativeQuery(
                "DELETE FROM pur.price_list " +
                "WHERE id=:id AND tenant_id=:tenantId AND company_id=:companyId"
            )
            .setParameter("id", id)
            .setParameter("tenantId", tenantId())
            .setParameter("companyId", companyId())
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
            loadSuppliers()
        );

        return "ui/purchasing/price_copy_wizard";
    }

    // ========================================================
    // VALIDATE SOURCE AND TARGET SUPPLIERS
    // ========================================================

    private List<Long> validateTargets(
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

        supplierName(sourceId);

        Long sourceCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pur.price_list " +
            "WHERE tenant_id=:tenantId AND company_id=:companyId AND supplier_id=:sourceId"
        )
        .setParameter("tenantId", tenantId())
        .setParameter("companyId", companyId())
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

            supplierName(targetId);

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

    private Long sourcePriceCount(Long sourceId) {

        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pur.price_list " +
            "WHERE tenant_id=:tenantId AND company_id=:companyId AND supplier_id=:sourceId"
        )
        .setParameter("tenantId", tenantId())
        .setParameter("companyId", companyId())
        .setParameter("sourceId", sourceId)
        .getSingleResult()).longValue();
    }

    // ========================================================
    // COUNT COPYABLE PRICE
    // ========================================================

    private Long countCopyable(
            Long sourceId,
            Long targetId) {

        String sql =
            "SELECT COUNT(*) " +
            "FROM pur.price_list src " +
            "WHERE src.tenant_id=:tenantId AND src.company_id=:companyId " +
            "AND src.supplier_id=:sourceId " +
            "AND NOT EXISTS (" +
            " SELECT 1 FROM pur.price_list dst " +
            " WHERE dst.tenant_id=src.tenant_id AND dst.company_id=src.company_id " +
            " AND dst.supplier_id=:targetId " +
            " AND dst.item_id=src.item_id " +
            " AND dst.uom_id IS NOT DISTINCT FROM src.uom_id " +
            " AND dst.currency IS NOT DISTINCT FROM src.currency " +
            " AND dst.valid_from=src.valid_from " +
            " AND dst.valid_to IS NOT DISTINCT FROM src.valid_to " +
            " AND dst.min_qty IS NOT DISTINCT FROM src.min_qty " +
            " AND dst.condition_type = src.condition_type " +
            " AND dst.condition_source = src.condition_source " +
            " AND dst.condition_class = src.condition_class " +
            ")";

        return ((Number) em.createNativeQuery(sql)
            .setParameter("tenantId", tenantId())
            .setParameter("companyId", companyId())
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

            List<Long> targets = validateTargets(
                sourceSupplierId,
                targetSupplierIds
            );

            prepareBase(
                model,
                user,
                "Preview Copy Supplier Price List"
            );

            Long totalSource = sourcePriceCount(
                sourceSupplierId
            );

            List<Map<String, Object>> plans = new ArrayList<>();

            long totalNew = 0;
            long totalSkipped = 0;

            for (Long targetId : targets) {

                Long newCount = countCopyable(
                    sourceSupplierId,
                    targetId
                );

                Long skipped = totalSource - newCount;

                Map<String, Object> plan = new HashMap<>();

                plan.put("supplierId", targetId);
                plan.put("supplierName", supplierName(targetId));
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
                supplierName(sourceSupplierId)
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

            List<Long> targets = validateTargets(
                sourceSupplierId,
                targetSupplierIds
            );

            String insertSql =
                "INSERT INTO pur.price_list " +
                "(tenant_id, company_id, supplier_id, item_id, price, currency, " +
                "valid_from, valid_to, min_qty, uom_id, condition_type, price_unit, " +
                "condition_source, condition_class, rate, calculation_order, " +
                "created_at, updated_at) " +
                "SELECT src.tenant_id, src.company_id, :targetId, src.item_id, " +
                "src.price, src.currency, src.valid_from, src.valid_to, " +
                "src.min_qty, src.uom_id, src.condition_type, src.price_unit, " +
                "src.condition_source, src.condition_class, src.rate, src.calculation_order, " +
                "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                "FROM pur.price_list src " +
                "WHERE src.tenant_id=:tenantId AND src.company_id=:companyId " +
                "AND src.supplier_id=:sourceId " +
                "AND NOT EXISTS (" +
                " SELECT 1 FROM pur.price_list dst " +
                " WHERE dst.tenant_id=src.tenant_id AND dst.company_id=src.company_id " +
                " AND dst.supplier_id=:targetId " +
                " AND dst.item_id=src.item_id " +
                " AND dst.uom_id IS NOT DISTINCT FROM src.uom_id " +
                " AND dst.currency IS NOT DISTINCT FROM src.currency " +
                " AND dst.valid_from=src.valid_from " +
                " AND dst.valid_to IS NOT DISTINCT FROM src.valid_to " +
                " AND dst.min_qty IS NOT DISTINCT FROM src.min_qty " +
                " AND dst.condition_type = src.condition_type " +
                " AND dst.condition_source = src.condition_source " +
                " AND dst.condition_class = src.condition_class " +
                ")";

            long totalInserted = 0;
            long totalSkipped = 0;
            long totalSource = sourcePriceCount(
                sourceSupplierId
            );

            List<String> resultLines = new ArrayList<>();

            for (Long targetId : targets) {

                int inserted = em.createNativeQuery(insertSql)
                    .setParameter("tenantId", tenantId())
                    .setParameter("companyId", companyId())
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
                    supplierName(targetId) +
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