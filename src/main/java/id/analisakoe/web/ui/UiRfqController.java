package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.service.NumberRangeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/ui/purchasing/rfq")
public class UiRfqController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private NumberRangeService numberRangeService;

    @Autowired
    private ContextService context;

    private final MenuService menuService;

    public UiRfqController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ============================================================
    // LIST
    // ============================================================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Request for Quotation");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT r.id, r.doc_no, r.doc_date, r.due_date, r.status, " +
                     "(SELECT COUNT(*) FROM pur.rfq_supplier rs WHERE rs.rfq_id = r.id) AS supplier_count, " +
                     "(SELECT COUNT(*) FROM pur.rfq_dtl rd WHERE rd.header_id = r.id) AS item_count " +
                     "FROM pur.rfq_hdr r WHERE r.tenant_id = :tenantId AND r.company_id = :companyId " +
                     "ORDER BY r.id DESC";

        List<Map<String, Object>> rfqs = new ArrayList<>();
        try {
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql)
                    .setParameter("tenantId", context.tenantId())
                    .setParameter("companyId", context.companyId())
                    .getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("docNo", row[1] != null ? row[1].toString() : "-");

                Object docDateObj = row[2];
                LocalDate docDate = null;
                if (docDateObj instanceof java.sql.Date) {
                    docDate = ((java.sql.Date) docDateObj).toLocalDate();
                } else if (docDateObj instanceof LocalDate) {
                    docDate = (LocalDate) docDateObj;
                }
                m.put("docDate", docDate);

                Object dueDateObj = row[3];
                LocalDate dueDate = null;
                if (dueDateObj instanceof java.sql.Date) {
                    dueDate = ((java.sql.Date) dueDateObj).toLocalDate();
                } else if (dueDateObj instanceof LocalDate) {
                    dueDate = (LocalDate) dueDateObj;
                }
                m.put("dueDate", dueDate);

                m.put("status", row[4] != null ? row[4].toString() : "DRAFT");
                m.put("supplierCount", row[5] != null ? ((Number) row[5]).intValue() : 0);
                m.put("itemCount", row[6] != null ? ((Number) row[6]).intValue() : 0);
                rfqs.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data RFQ: " + e.getMessage());
        }
        model.addAttribute("rfqs", rfqs);
        return "ui/purchasing/rfq_list";
    }

    // ============================================================
    // FORM NEW (DENGAN INTEGRASI PR)
    // ============================================================
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long prId, 
                          HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New RFQ");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("selectedPrId", prId);

        // 1. Ambil list PR (hanya yang CONFIRMED)
        List<Map<String, Object>> prs = new ArrayList<>();
        // SEBELUMNYA:


// GANTI MENJADI:
String sqlPr = "SELECT h.id, h.doc_no, h.doc_date FROM pur.purchase_request_hdr h " +
               "WHERE h.tenant_id = :tenantId AND h.company_id = :companyId AND h.status = 'CONFIRMED' " +
               "AND NOT EXISTS (SELECT 1 FROM pur.purchase_order_hdr po " +
               "WHERE po.pr_id = h.id AND po.tenant_id = :tenantId AND po.company_id = :companyId) " +
               "ORDER BY h.id DESC";
        for (Object[] row : (List<Object[]>) em.createNativeQuery(sqlPr)
                .setParameter("tenantId", context.tenantId())
                .setParameter("companyId", context.companyId())
                .getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]);
            m.put("docNo", row[1]);
            m.put("docDate", row[2]);
            prs.add(m);
        }
        model.addAttribute("prs", prs);

        // 2. Item
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, code, name FROM md.item " +
                "WHERE tenant_id = :tenantId AND company_id = :companyId AND deleted_at IS NULL ORDER BY name")
                .setParameter("tenantId", context.tenantId())
                .setParameter("companyId", context.companyId())
                .getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("code", row[1]); m.put("name", row[2]);
            items.add(m);
        }
        model.addAttribute("items", items);

        // 3. UOM
        List<Map<String, Object>> uoms = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, code FROM md.uom WHERE tenant_id = :tenantId ORDER BY code")
                .setParameter("tenantId", context.tenantId())
                .getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("code", row[1]);
            uoms.add(m);
        }
        model.addAttribute("uoms", uoms);

        // 4. Supplier
        List<Map<String, Object>> suppliers = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, name FROM md.supplier " +
                "WHERE tenant_id = :tenantId AND company_id = :companyId AND status = 'ACTIVE' ORDER BY name")
                .setParameter("tenantId", context.tenantId())
                .setParameter("companyId", context.companyId())
                .getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("name", row[1]);
            suppliers.add(m);
        }
        model.addAttribute("suppliers", suppliers);

        return "ui/purchasing/rfq_form";
    }

    // ============================================================
    // API: GET PR DETAILS
    // ============================================================
    @GetMapping("/api/pr-details/{prId}")
    @ResponseBody
    public List<Map<String, Object>> getPrDetails(@PathVariable Long prId) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String sql = "SELECT d.item_id, i.code, i.name, d.qty, d.uom_id, u.code " +
                         "FROM pur.purchase_request_dtl d " +
                         "JOIN pur.purchase_request_hdr h ON h.id = d.header_id " +
                         "JOIN md.item i ON i.id = d.item_id " +
                         "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                         "WHERE d.header_id = :id AND h.tenant_id = :tenantId AND h.company_id = :companyId " +
                         "ORDER BY d.id";
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql)
                    .setParameter("id", prId)
                    .setParameter("tenantId", context.tenantId())
                    .setParameter("companyId", context.companyId())
                    .getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("itemId", row[0]);
                m.put("itemCode", row[1]);
                m.put("itemName", row[2]);
                m.put("qty", row[3]);
                m.put("uomId", row[4]);
                m.put("uomCode", row[5]);
                list.add(m);
            }
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            list.add(err);
        }
        return list;
    }

    // ============================================================
    // SAVE NEW
    // ============================================================
    @PostMapping
    @Transactional
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam(required = false) LocalDate dueDate,
                       @RequestParam(required = false) Long prId,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemId") List<Long> itemIds,
                       @RequestParam("uomId") List<Long> uomIds,
                       @RequestParam("qty") List<BigDecimal> qtys,
                       @RequestParam(value = "supplierId", required = false) List<Long> supplierIds,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            if (supplierIds == null || supplierIds.isEmpty()) {
                throw new RuntimeException("Pilih minimal 1 supplier.");
            }
            if (itemIds == null || itemIds.isEmpty()) {
                throw new RuntimeException("Minimal 1 item harus ada.");
            }

            String docNo = numberRangeService.generate("RFQ", "RFQ");
            Long hdrId = ((Number) em.createNativeQuery(
                "INSERT INTO pur.rfq_hdr (tenant_id, company_id, doc_no, doc_date, due_date, remarks, status, pr_id) " +
                "VALUES (:tenantId, :compId, :docNo, :docDate, :dueDate, :remarks, 'DRAFT', :prId) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", docNo)
                .setParameter("docDate", docDate)
                .setParameter("dueDate", dueDate)
                .setParameter("remarks", remarks)
                .setParameter("prId", prId)
                .getSingleResult()).longValue();

            for (int i = 0; i < itemIds.size(); i++) {
                if (qtys.get(i) == null || qtys.get(i).compareTo(BigDecimal.ZERO) <= 0) continue;
                em.createNativeQuery(
                    "INSERT INTO pur.rfq_dtl (header_id, item_id, uom_id, qty) VALUES (:hdr, :item, :uom, :qty)")
                    .setParameter("hdr", hdrId).setParameter("item", itemIds.get(i))
                    .setParameter("uom", uomIds.get(i)).setParameter("qty", qtys.get(i))
                    .executeUpdate();
            }

            for (Long supId : supplierIds) {
                em.createNativeQuery(
                    "INSERT INTO pur.rfq_supplier (rfq_id, supplier_id, status) VALUES (:rfq, :sup, 'PENDING')")
                    .setParameter("rfq", hdrId).setParameter("sup", supId).executeUpdate();
            }

            ra.addFlashAttribute("success", "RFQ " + docNo + " berhasil dibuat.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", root.getMessage());
        }
        return "redirect:/ui/purchasing/rfq";
    }

    // ============================================================
    // DETAIL (INPUT HARGA & APPROVAL)
    // ============================================================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "RFQ Detail");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("rfqId", id);

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT doc_no, doc_date, due_date, remarks, status, total_level, current_level, recommended_supplier_id, recommended_amount " +
                "FROM pur.rfq_hdr WHERE id = :id AND tenant_id = :tenant AND company_id = :company")
                .setParameter("id", id).setParameter("tenant", approvalTenantId(user))
                .setParameter("company", context.companyId()).getSingleResult();
            
            model.addAttribute("rfqDocNo", hdr[0]);
            model.addAttribute("rfqDate", hdr[1]);
            model.addAttribute("rfqDueDate", hdr[2]);
            model.addAttribute("rfqRemarks", hdr[3]);
            model.addAttribute("rfqStatus", hdr[4]);
            
            int totalLevel = hdr[5] != null ? ((Number) hdr[5]).intValue() : 0;
            int currentLevel = hdr[6] != null ? ((Number) hdr[6]).intValue() : 0;
            Long recSupplierId = hdr[7] != null ? ((Number) hdr[7]).longValue() : 0L;
            
            model.addAttribute("totalLevel", totalLevel);
            model.addAttribute("currentLevel", currentLevel);
            model.addAttribute("recommendedSupplierId", recSupplierId);

            BigDecimal recommendedAmount = hdr[8] != null ? new BigDecimal(hdr[8].toString()) : null;
            model.addAttribute("recommendedAmount", recommendedAmount);

            if ("PENDING_APPROVAL".equals(hdr[4].toString()) && recommendedAmount != null) {
                Object[] band = findRfqApprovalBand(approvalTenantId(user), recommendedAmount);
                int selectedLevel = ((Number) band[0]).intValue();
                model.addAttribute("currentLevel", selectedLevel);
                model.addAttribute("totalLevel", selectedLevel);
                model.addAttribute("requiredRole", band[1].toString());
            }

            // Items
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT rd.id, rd.item_id, i.code, i.name, rd.qty, rd.uom_id, u.code " +
                    "FROM pur.rfq_dtl rd JOIN md.item i ON i.id = rd.item_id " +
                    "LEFT JOIN md.uom u ON u.id = rd.uom_id WHERE rd.header_id = :id ORDER BY rd.id")
                    .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("dtlId", row[0]); m.put("itemId", row[1]);
                m.put("itemCode", row[2]); m.put("itemName", row[3]);
                m.put("qty", row[4]); m.put("uomCode", row[6]);
                items.add(m);
            }
            model.addAttribute("items", items);

            // Suppliers
            List<Map<String, Object>> suppliers = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT rs.id, rs.supplier_id, s.name, rs.status " +
                    "FROM pur.rfq_supplier rs JOIN md.supplier s ON s.id = rs.supplier_id " +
                    "WHERE rs.rfq_id = :id ORDER BY rs.id").setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                Long rsId = ((Number) row[0]).longValue();
                m.put("rfqSupplierId", rsId); m.put("supplierId", row[1]); m.put("supplierName", row[2]); m.put("status", row[3]);

                Map<Long, BigDecimal> prices = new HashMap<>();
                for (Object[] p : (List<Object[]>) em.createNativeQuery(
                        "SELECT rfq_dtl_id, unit_price FROM pur.rfq_response WHERE rfq_supplier_id = :rsId")
                        .setParameter("rsId", rsId).getResultList()) {
                    prices.put(((Number) p[0]).longValue(), (BigDecimal) p[1]);
                }
                m.put("prices", prices);
                suppliers.add(m);
            }
            model.addAttribute("suppliers", suppliers);

            // Approval History
            List<Map<String, Object>> approvalHistory = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT level, approver_username, approver_role, action, remarks, action_date " +
                    "FROM core.approval_log WHERE doc_type = 'RFQ' AND doc_id = :id ORDER BY action_date ASC")
                    .setParameter("id", id).getResultList()) {
                Map<String, Object> h = new HashMap<>();
                h.put("level", row[0]); h.put("username", row[1]); h.put("role", row[2]); h.put("action", row[3]); h.put("remarks", row[4]); h.put("date", row[5]);
                approvalHistory.add(h);
            }
            model.addAttribute("approvalHistory", approvalHistory);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat RFQ: " + e.getMessage());
            return "redirect:/ui/purchasing/rfq";
        }
        return "ui/purchasing/rfq_detail";
    }
	
	    // ============================================================
    // COMPARISON VIEW — Perbandingan Harga Supplier
    // ============================================================
    @GetMapping("/{id}/comparison")
    public String comparison(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "RFQ Comparison");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("rfqId", id);

        try {
            // 1. Header RFQ
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT doc_no, doc_date, due_date, remarks, status, recommended_supplier_id " +
                "FROM pur.rfq_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();
            model.addAttribute("rfqDocNo", hdr[0]);
            model.addAttribute("rfqDate", hdr[1]);
            model.addAttribute("rfqDueDate", hdr[2]);
            model.addAttribute("rfqRemarks", hdr[3]);
            model.addAttribute("rfqStatus", hdr[4]);
            model.addAttribute("recommendedSupplierId", hdr[5] != null ? ((Number) hdr[5]).longValue() : 0L);

            // 2. Items
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT rd.id, i.code, i.name, rd.qty, u.code " +
                    "FROM pur.rfq_dtl rd JOIN md.item i ON i.id = rd.item_id " +
                    "LEFT JOIN md.uom u ON u.id = rd.uom_id WHERE rd.header_id = :id ORDER BY rd.id")
                    .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("dtlId", row[0]);
                m.put("itemCode", row[1]);
                m.put("itemName", row[2]);
                m.put("qty", row[3]);
                m.put("uomCode", row[4]);
                items.add(m);
            }
            model.addAttribute("items", items);

            // 3. Suppliers + Prices + Totals + Ranking
            List<Map<String, Object>> suppliers = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT rs.id, rs.supplier_id, s.name " +
                    "FROM pur.rfq_supplier rs JOIN md.supplier s ON s.id = rs.supplier_id " +
                    "WHERE rs.rfq_id = :id ORDER BY s.name").setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                Long rsId = ((Number) row[0]).longValue();
                m.put("rfqSupplierId", rsId);
                m.put("supplierId", row[1]);
                m.put("supplierName", row[2]);

                Map<Long, BigDecimal> prices = new HashMap<>();
                BigDecimal totalAmount = BigDecimal.ZERO;
                int itemQuoted = 0;
                for (Object[] p : (List<Object[]>) em.createNativeQuery(
                        "SELECT rr.rfq_dtl_id, rr.unit_price, rd.qty " +
                        "FROM pur.rfq_response rr JOIN pur.rfq_dtl rd ON rd.id = rr.rfq_dtl_id " +
                        "WHERE rr.rfq_supplier_id = :rsId").setParameter("rsId", rsId).getResultList()) {
                    Long dtlId = ((Number) p[0]).longValue();
                    BigDecimal price = (BigDecimal) p[1];
                    BigDecimal qty = (BigDecimal) p[2];
                    prices.put(dtlId, price);
                    totalAmount = totalAmount.add(price.multiply(qty));
                    itemQuoted++;
                }
                m.put("prices", prices);
                m.put("totalAmount", totalAmount);
                m.put("itemQuoted", itemQuoted);
                suppliers.add(m);
            }

            // 4. Hitung ranking berdasarkan total (termurah = 1)
            List<Map<String, Object>> sorted = new ArrayList<>(suppliers);
            sorted.sort((a, b) -> ((BigDecimal) a.get("totalAmount")).compareTo((BigDecimal) b.get("totalAmount")));
            for (int i = 0; i < sorted.size(); i++) {
                sorted.get(i).put("rank", i + 1);
            }

            // 5. Cari harga terendah & tertinggi per item (untuk highlight)
            Map<Long, BigDecimal> lowestPerItem = new HashMap<>();
            Map<Long, BigDecimal> highestPerItem = new HashMap<>();
            for (Map<String, Object> it : items) {
                Long dtlId = ((Long) it.get("dtlId"));
                BigDecimal min = null, max = null;
                for (Map<String, Object> s : suppliers) {
                    Map<Long, BigDecimal> p = (Map<Long, BigDecimal>) s.get("prices");
                    BigDecimal price = p.get(dtlId);
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        if (min == null || price.compareTo(min) < 0) min = price;
                        if (max == null || price.compareTo(max) > 0) max = price;
                    }
                }
                if (min != null) lowestPerItem.put(dtlId, min);
                if (max != null) highestPerItem.put(dtlId, max);
            }
            model.addAttribute("lowestPerItem", lowestPerItem);
            model.addAttribute("highestPerItem", highestPerItem);

            model.addAttribute("suppliers", suppliers);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat comparison: " + e.getMessage());
            return "redirect:/ui/purchasing/rfq/" + id;
        }
        return "ui/purchasing/rfq_comparison";
    }

    // ============================================================
    // PRINT BAPH — Berita Acara Perbandingan Harga
    // ============================================================
        @GetMapping("/{id}/print-baph")
    public String printBaph(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            // 1. Header RFQ
            List<?> hdrList = em.createNativeQuery(
                "SELECT doc_no, doc_date, due_date, remarks, status, " +
                "COALESCE(recommended_supplier_id, 0), COALESCE(recommended_amount, 0) " +
                "FROM pur.rfq_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            
            if (hdrList.isEmpty()) {
                ra.addFlashAttribute("error", "RFQ tidak ditemukan.");
                return "redirect:/ui/purchasing/rfq";
            }
            
            Object[] hdr = (Object[]) hdrList.get(0);
            model.addAttribute("rfqDocNo", hdr[0]);
            model.addAttribute("rfqDate", hdr[1]);
            model.addAttribute("rfqDueDate", hdr[2]);
            model.addAttribute("rfqRemarks", hdr[3]);
            model.addAttribute("recommendedSupplierId", ((Number) hdr[5]).longValue());
            model.addAttribute("recommendedAmount", hdr[6]);

            // 2. Items
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT rd.id, i.code, i.name, rd.qty, u.code " +
                    "FROM pur.rfq_dtl rd JOIN md.item i ON i.id = rd.item_id " +
                    "LEFT JOIN md.uom u ON u.id = rd.uom_id WHERE rd.header_id = :id ORDER BY rd.id")
                    .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("dtlId", row[0]);
                m.put("itemCode", row[1]);
                m.put("itemName", row[2]);
                m.put("qty", row[3]);
                m.put("uomCode", row[4]);
                items.add(m);
            }
            model.addAttribute("items", items);

            // 3. Suppliers + Prices + Total
            List<Map<String, Object>> suppliers = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT rs.id, rs.supplier_id, s.name FROM pur.rfq_supplier rs " +
                    "JOIN md.supplier s ON s.id = rs.supplier_id WHERE rs.rfq_id = :id ORDER BY s.name")
                    .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                Long rsId = ((Number) row[0]).longValue();
                m.put("supplierId", row[1]);
                m.put("supplierName", row[2]);

                Map<Long, BigDecimal> prices = new HashMap<>();
                BigDecimal total = BigDecimal.ZERO;
                int itemQuoted = 0;
                
                for (Object[] p : (List<Object[]>) em.createNativeQuery(
                        "SELECT rr.rfq_dtl_id, rr.unit_price, rd.qty " +
                        "FROM pur.rfq_response rr JOIN pur.rfq_dtl rd ON rd.id = rr.rfq_dtl_id " +
                        "WHERE rr.rfq_supplier_id = :rsId").setParameter("rsId", rsId).getResultList()) {
                    Long dtlId = ((Number) p[0]).longValue();
                    BigDecimal price = (BigDecimal) p[1];
                    BigDecimal qty = (BigDecimal) p[2];
                    prices.put(dtlId, price);
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        total = total.add(price.multiply(qty));
                        itemQuoted++;
                    }
                }
                m.put("prices", prices);
                m.put("totalAmount", total);
                m.put("itemQuoted", itemQuoted);
                suppliers.add(m);
            }

            // 4. Hitung ranking berdasarkan total (termurah = 1) — WAJIB!
            List<Map<String, Object>> sortedForRanking = new ArrayList<>(suppliers);
            sortedForRanking.sort((a, b) -> ((BigDecimal) a.get("totalAmount")).compareTo((BigDecimal) b.get("totalAmount")));
            for (int i = 0; i < sortedForRanking.size(); i++) {
                sortedForRanking.get(i).put("rank", i + 1);
            }
            
            // Set rank di list utama juga
            Map<Long, Integer> rankMap = new HashMap<>();
            for (Map<String, Object> s : sortedForRanking) {
                rankMap.put((Long) s.get("supplierId"), (Integer) s.get("rank"));
            }
            for (Map<String, Object> s : suppliers) {
                s.put("rank", rankMap.get((Long) s.get("supplierId")));
            }

            // 5. Lowest & Highest per item (untuk highlight)
            Map<Long, BigDecimal> lowestPerItem = new HashMap<>();
            Map<Long, BigDecimal> highestPerItem = new HashMap<>();
            for (Map<String, Object> it : items) {
                Long dtlId = (Long) it.get("dtlId");
                BigDecimal min = null, max = null;
                for (Map<String, Object> s : suppliers) {
                    BigDecimal price = ((Map<Long, BigDecimal>) s.get("prices")).get(dtlId);
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        if (min == null || price.compareTo(min) < 0) min = price;
                        if (max == null || price.compareTo(max) > 0) max = price;
                    }
                }
                if (min != null) lowestPerItem.put(dtlId, min);
                if (max != null) highestPerItem.put(dtlId, max);
            }
            model.addAttribute("lowestPerItem", lowestPerItem);
            model.addAttribute("highestPerItem", highestPerItem);
            model.addAttribute("suppliers", suppliers);

            // 6. Company Info (safe)
            Map<String, Object> company = new HashMap<>();
            company.put("name", "AnalisaKoe ERP");
            try {
                Object r = em.createNativeQuery("SELECT name FROM core.company WHERE id = :companyId AND tenant_id = :tenantId")
            .setParameter("companyId", context.companyId())
            .setParameter("tenantId", context.tenantId())
            .getSingleResult();
                if (r != null) company.put("name", r.toString());
            } catch (Exception ignore) { }
            model.addAttribute("company", company);

        } catch (Exception e) {
            System.err.println("[printBaph] Error: " + e.getMessage());
            e.printStackTrace();
            ra.addFlashAttribute("error", "Gagal print BAPH: " + e.getMessage());
            return "redirect:/ui/purchasing/rfq/" + id;
        }
        return "ui/print/rfq_baph";
    }

    // Helper getCompanyInfo (tambahkan jika belum ada)
    private Map<String, Object> getCompanyInfo() {
        Map<String, Object> c = new HashMap<>();
        c.put("name", "AnalisaKoe ERP");
        try {
            Object r = em.createNativeQuery("SELECT name FROM core.company WHERE id = :companyId AND tenant_id = :tenantId")
            .setParameter("companyId", context.companyId())
            .setParameter("tenantId", context.tenantId())
            .getSingleResult();
            if (r != null) c.put("name", r.toString());
        } catch (Exception ignore) { }
        return c;
    }
	
    // ============================================================
    // SAVE RESPONSES
    // ============================================================
    @PostMapping("/{id}/response")
    @Transactional
    public String saveResponse(@PathVariable Long id, @RequestParam Map<String, String> allParams, RedirectAttributes ra) {
        try {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("price_")) continue;
                String val = entry.getValue();
                if (val == null || val.trim().isEmpty()) continue;

                String[] parts = key.split("_");
                if (parts.length != 3) continue;
                Long rsId = Long.parseLong(parts[1]);
                Long dtlId = Long.parseLong(parts[2]);
                BigDecimal price = new BigDecimal(val.replaceAll("[^0-9.]", ""));

                List<?> existing = em.createNativeQuery(
                    "SELECT id FROM pur.rfq_response WHERE rfq_supplier_id = :rsId AND rfq_dtl_id = :dtlId")
                    .setParameter("rsId", rsId).setParameter("dtlId", dtlId).getResultList();

                if (existing.isEmpty()) {
                    em.createNativeQuery("INSERT INTO pur.rfq_response (rfq_supplier_id, rfq_dtl_id, unit_price) VALUES (:rsId, :dtlId, :price)")
                        .setParameter("rsId", rsId).setParameter("dtlId", dtlId).setParameter("price", price).executeUpdate();
                } else {
                    em.createNativeQuery("UPDATE pur.rfq_response SET unit_price = :price WHERE rfq_supplier_id = :rsId AND rfq_dtl_id = :dtlId")
                        .setParameter("rsId", rsId).setParameter("dtlId", dtlId).setParameter("price", price).executeUpdate();
                }
            }
            em.createNativeQuery("UPDATE pur.rfq_hdr SET status = 'QUOTED' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "Harga supplier berhasil disimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal simpan: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/rfq/" + id;
    }

    // ============================================================
    // ============================================================
    // RFQ APPROVAL MATRIX BAND-BASED PATCH
    // SUBMIT APPROVAL - satu approver dipilih berdasarkan rentang nominal
    // ============================================================
    @PostMapping("/{id}/submit")
    @Transactional
    public String submitForApproval(@PathVariable Long id,
                                    @RequestParam Long recommendedSupplierId,
                                    @RequestParam BigDecimal recommendedAmount,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            if (user == null) throw new RuntimeException("Session pengguna tidak ditemukan.");
            Long tenantId = approvalTenantId(user);
            if (recommendedAmount == null || recommendedAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Nominal rekomendasi tidak valid.");
            }

            Object statusObj = em.createNativeQuery(
                    "SELECT status FROM pur.rfq_hdr WHERE id = :id AND tenant_id = :tenant "
                    + "AND company_id = :company FOR UPDATE")
                    .setParameter("id", id).setParameter("tenant", tenantId)
                    .setParameter("company", context.companyId()).getSingleResult();
            String status = statusObj != null ? statusObj.toString() : "";
            if (!"QUOTED".equalsIgnoreCase(status)) {
                throw new RuntimeException("RFQ harus berstatus QUOTED sebelum diajukan untuk approval. Status sekarang: " + status);
            }

            Long responseCount = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.rfq_response rr " +
                    "JOIN pur.rfq_supplier rs ON rs.id = rr.rfq_supplier_id " +
                    "JOIN pur.rfq_hdr rh ON rh.id = rs.rfq_id " +
                    "WHERE rs.rfq_id = :id AND rh.tenant_id = :tenant AND rh.company_id = :company "
                    + "AND rr.unit_price > 0")
                    .setParameter("id", id).setParameter("tenant", tenantId)
                    .setParameter("company", context.companyId()).getSingleResult()).longValue();
            if (responseCount == 0) throw new RuntimeException("Belum ada harga supplier yang diinput.");

            Long supplierCount = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.rfq_supplier WHERE rfq_id = :id AND supplier_id = :supplierId")
                    .setParameter("id", id).setParameter("supplierId", recommendedSupplierId).getSingleResult()).longValue();
            if (supplierCount == 0) throw new RuntimeException("Supplier rekomendasi bukan supplier pada RFQ ini.");

            Object[] band = findRfqApprovalBand(tenantId, recommendedAmount);
            int approvalLevel = ((Number) band[0]).intValue();
            String requiredRole = band[1].toString();

            // Level pada matrix adalah kategori nominal, bukan rangkaian approval berurutan.
            em.createNativeQuery(
                    "UPDATE pur.rfq_hdr SET status = 'PENDING_APPROVAL', " +
                    "recommended_supplier_id = :supplierId, recommended_amount = :amount, " +
                    "current_level = :level, total_level = :level " +
                    "WHERE id = :id AND tenant_id = :tenant AND company_id = :company")
                    .setParameter("supplierId", recommendedSupplierId)
                    .setParameter("amount", recommendedAmount)
                    .setParameter("level", approvalLevel)
                    .setParameter("id", id).setParameter("tenant", tenantId)
                     .setParameter("company", context.companyId()).executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO core.approval_log " +
                    "(tenant_id, doc_type, doc_id, level, approver_username, action, amount, remarks) " +
                    "VALUES (:tenant, 'RFQ', :docId, 0, :username, 'SUBMIT', :amount, :remarks)")
                    .setParameter("tenant", tenantId).setParameter("docId", id)
                    .setParameter("username", user.getUsername() != null ? user.getUsername() : "-")
                    .setParameter("amount", recommendedAmount)
                    .setParameter("remarks", "Submitted; required role: " + requiredRole)
                    .executeUpdate();

            ra.addFlashAttribute("success", "RFQ diajukan. Approval ditujukan ke role " + requiredRole + " (Level " + approvalLevel + ").");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal submit approval: " + root.getMessage());
        }
        return "redirect:/ui/purchasing/rfq/" + id;
    }

    // ============================================================
    // APPROVE - role wajib sesuai rentang nominal RFQ
    // ============================================================
    @PostMapping("/{id}/approve")
    @Transactional
    public String approveRfq(@PathVariable Long id,
                             @RequestParam(required = false) String remarks,
                             HttpSession session,
                             RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            if (user == null) throw new RuntimeException("Session pengguna tidak ditemukan.");
            Long tenantId = approvalTenantId(user);

            Object[] hdr = (Object[]) em.createNativeQuery(
                    "SELECT status, current_level, total_level, recommended_amount " +
                    "FROM pur.rfq_hdr WHERE id = :id AND tenant_id = :tenant "
                    + "AND company_id = :company FOR UPDATE")
                    .setParameter("id", id).setParameter("tenant", tenantId)
                    .setParameter("company", context.companyId()).getSingleResult();
            String status = hdr[0] != null ? hdr[0].toString() : "";
            if (!"PENDING_APPROVAL".equalsIgnoreCase(status)) {
                throw new RuntimeException("RFQ tidak dalam status PENDING_APPROVAL.");
            }
            BigDecimal amount = hdr[3] != null ? new BigDecimal(hdr[3].toString()) : null;
            if (amount == null) throw new RuntimeException("Nominal rekomendasi RFQ belum tersedia.");

            Object[] band = findRfqApprovalBand(tenantId, amount);
            int matrixLevel = ((Number) band[0]).intValue();
            String requiredRole = band[1].toString();
            if (!userHasApprovalRole(user, requiredRole)) {
                throw new RuntimeException("Akses ditolak. RFQ ini memerlukan role " + requiredRole + ". Role Anda: " +
                        (user.getRoles() == null ? "-" : user.getRoles().toString()));
            }

            em.createNativeQuery(
                    "INSERT INTO core.approval_log " +
                    "(tenant_id, doc_type, doc_id, level, approver_username, approver_role, action, amount, remarks) " +
                    "VALUES (:tenant, 'RFQ', :docId, :level, :username, :role, 'APPROVE', :amount, :remarks)")
                    .setParameter("tenant", tenantId).setParameter("docId", id)
                    .setParameter("level", matrixLevel)
                    .setParameter("username", user.getUsername() != null ? user.getUsername() : "-")
                    .setParameter("role", requiredRole).setParameter("amount", amount)
                    .setParameter("remarks", remarks).executeUpdate();

            em.createNativeQuery(
                    "UPDATE pur.rfq_hdr SET status = 'APPROVED', current_level = :level, total_level = :level " +
                    "WHERE id = :id AND tenant_id = :tenant AND company_id = :company "
                     + "AND status = 'PENDING_APPROVAL'")
                    .setParameter("level", matrixLevel).setParameter("id", id).setParameter("tenant", tenantId)
                     .setParameter("company", context.companyId()).executeUpdate();
            ra.addFlashAttribute("success", "RFQ berhasil disetujui oleh role " + requiredRole + ".");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal approve: " + root.getMessage());
        }
        return "redirect:/ui/purchasing/rfq/" + id;
    }

    // ============================================================
    // REJECT - role wajib sesuai rentang nominal RFQ
    // ============================================================
    @PostMapping("/{id}/reject")
    @Transactional
    public String rejectRfq(@PathVariable Long id,
                            @RequestParam(required = false) String remarks,
                            HttpSession session,
                            RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            if (user == null) throw new RuntimeException("Session pengguna tidak ditemukan.");
            Long tenantId = approvalTenantId(user);

            Object[] hdr = (Object[]) em.createNativeQuery(
                    "SELECT status, recommended_amount FROM pur.rfq_hdr " +
                    "WHERE id = :id AND tenant_id = :tenant AND company_id = :company FOR UPDATE")
                    .setParameter("id", id).setParameter("tenant", tenantId)
                    .setParameter("company", context.companyId()).getSingleResult();
            String status = hdr[0] != null ? hdr[0].toString() : "";
            if (!"PENDING_APPROVAL".equalsIgnoreCase(status)) {
                throw new RuntimeException("Hanya RFQ PENDING_APPROVAL yang dapat ditolak.");
            }
            BigDecimal amount = hdr[1] != null ? new BigDecimal(hdr[1].toString()) : null;
            if (amount == null) throw new RuntimeException("Nominal rekomendasi RFQ belum tersedia.");

            Object[] band = findRfqApprovalBand(tenantId, amount);
            int matrixLevel = ((Number) band[0]).intValue();
            String requiredRole = band[1].toString();
            if (!userHasApprovalRole(user, requiredRole)) {
                throw new RuntimeException("Akses ditolak. Penolakan RFQ ini memerlukan role " + requiredRole + ".");
            }

            em.createNativeQuery(
                    "INSERT INTO core.approval_log " +
                    "(tenant_id, doc_type, doc_id, level, approver_username, approver_role, action, amount, remarks) " +
                    "VALUES (:tenant, 'RFQ', :docId, :level, :username, :role, 'REJECT', :amount, :remarks)")
                    .setParameter("tenant", tenantId).setParameter("docId", id)
                    .setParameter("level", matrixLevel)
                    .setParameter("username", user.getUsername() != null ? user.getUsername() : "-")
                    .setParameter("role", requiredRole).setParameter("amount", amount)
                    .setParameter("remarks", remarks).executeUpdate();

            em.createNativeQuery(
                    "UPDATE pur.rfq_hdr SET status = 'QUOTED', current_level = 0, total_level = 0 " +
                    "WHERE id = :id AND tenant_id = :tenant AND company_id = :company "
                     + "AND status = 'PENDING_APPROVAL'")
                    .setParameter("id", id).setParameter("tenant", tenantId)
                     .setParameter("company", context.companyId()).executeUpdate();
            ra.addFlashAttribute("warning", "RFQ ditolak oleh role " + requiredRole + " dan dikembalikan ke status QUOTED.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal reject: " + root.getMessage());
        }
        return "redirect:/ui/purchasing/rfq/" + id;
    }

    // Helper approval RFQ: rentang nominal menggunakan [min_amount, max_amount).
    private Long approvalTenantId(SessionUser user) {
        return context.tenantId();
    }

    private Object[] findRfqApprovalBand(Long tenantId, BigDecimal amount) {
        List<Object[]> matches = (List<Object[]>) em.createNativeQuery(
                "SELECT level, role_required FROM core.approval_matrix " +
                "WHERE tenant_id = :tenant AND doc_type = 'RFQ' AND is_active = true " +
                "AND :amount >= min_amount AND :amount < max_amount ORDER BY level")
                .setParameter("tenant", tenantId).setParameter("amount", amount).getResultList();
        if (matches.isEmpty()) {
            throw new RuntimeException("Tidak ada approval matrix RFQ aktif untuk nominal " + amount + ".");
        }
        if (matches.size() > 1) {
            throw new RuntimeException("Ada lebih dari satu approval matrix RFQ yang cocok. Periksa rentang nominal yang tumpang tindih.");
        }
        return matches.get(0);
    }

    private String normalizeApprovalRole(String value) {
        if (value == null) return "";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ROLE_")) normalized = normalized.substring(5);
        return normalized;
    }

    private boolean userHasApprovalRole(SessionUser user, String requiredRole) {
        if (user == null || requiredRole == null || requiredRole.trim().isEmpty()) return false;
        String required = normalizeApprovalRole(requiredRole);
        if (user.getRoles() == null) return false;
        for (String role : user.getRoles()) {
            String normalized = normalizeApprovalRole(role);
            if (required.equals(normalized) || "ADMIN".equals(normalized) ||
                    "SUPER_ADMIN".equals(normalized) || "SYSADMIN".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
    // CONVERT TO PO
    // ============================================================
    @PostMapping("/{id}/convert-to-po")
    @Transactional
    public String convertToPo(@PathVariable Long id, @RequestParam Long winningSupplierId, @RequestParam LocalDate poDate, HttpSession session, RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            final Long tenantId = context.tenantId();
            final Long compId = context.companyId();
            Object[] hdr = (Object[]) em.createNativeQuery("SELECT doc_no, status FROM pur.rfq_hdr " +
                    "WHERE id = :id AND tenant_id = :tenantId AND company_id = :companyId")
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("companyId", compId)
                .getSingleResult();
            String rfqNo = hdr[0].toString();
            String status = hdr[1].toString();

            if (!"APPROVED".equals(status)) throw new RuntimeException("RFQ belum disetujui.");

            List<Object[]> items = em.createNativeQuery("SELECT rd.item_id, rd.qty, rd.uom_id, rr.unit_price, rd.id FROM pur.rfq_dtl rd JOIN pur.rfq_supplier rs ON rs.rfq_id = rd.header_id LEFT JOIN pur.rfq_response rr ON rr.rfq_dtl_id = rd.id AND rr.rfq_supplier_id = rs.id WHERE rd.header_id = :id AND rs.supplier_id = :supId ORDER BY rd.id")
                .setParameter("id", id).setParameter("supId", winningSupplierId).getResultList();

            if (items.isEmpty()) throw new RuntimeException("Tidak ada harga untuk supplier pemenang.");

            String poNo = numberRangeService.generate("PO", "PO");
                        Long poId = ((Number) em.createNativeQuery("INSERT INTO pur.purchase_order_hdr (tenant_id, company_id, doc_no, doc_date, supplier_id, currency, remarks, status, total_amount, tax_type, tax_amount, created_at, rfq_id) VALUES (:tenantId, :compId, :docNo, :poDate, :supId, 'IDR', :remarks, 'DRAFT', 0, 'EXCLUDE', 0, NOW(), :rfqId) RETURNING id")
                            .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", poNo)
                .setParameter("poDate", poDate)
                .setParameter("supId", winningSupplierId)
                .setParameter("remarks", "Auto-generated dari RFQ " + rfqNo)
                .setParameter("rfqId", id)
                .getSingleResult()).longValue();

            // UPDATE STATUS PR MENJADI PO_CREATED
            // Kita gunakan sub-query untuk mencari PR ID dari RFQ ini
            try {
                em.createNativeQuery("UPDATE pur.purchase_request_hdr SET status = 'PO_CREATED' "
                  + "WHERE tenant_id = :tenantId AND company_id = :compId "
                  + "AND id IN (SELECT pr_id FROM pur.rfq_hdr WHERE id = :rfqId "
                  + "AND tenant_id = :tenantId AND company_id = :compId)")
                  .setParameter("rfqId", id)
                  .setParameter("tenantId", tenantId).setParameter("compId", compId)
                  .executeUpdate();
            } catch (Exception e) {
                System.err.println("Gagal update status PR: " + e.getMessage());
            }

            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;

            for (Object[] item : items) {
                Long itemId = ((Number) item[0]).longValue();
                BigDecimal qty = new BigDecimal(item[1].toString());
                Long uomId = ((Number) item[2]).longValue();
                BigDecimal price = item[3] != null ? new BigDecimal(item[3].toString()) : BigDecimal.ZERO;
                if (price.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal subtotal = qty.multiply(price);
                BigDecimal ppn = subtotal.multiply(new BigDecimal("0.11"));
                grandTotal = grandTotal.add(subtotal.add(ppn));
                totalTax = totalTax.add(ppn);

                Long dtlId = ((Number) em.createNativeQuery("INSERT INTO pur.purchase_order_dtl " +
                    "(header_id, item_id, uom_id, qty, price, subtotal, price_source, condition_type, price_unit) " +
                    "VALUES (:hdr, :item, :uom, :qty, :price, :subtotal, 'RFQ', 'RFQ', 1) RETURNING id")
                    .setParameter("hdr", poId).setParameter("item", itemId).setParameter("uom", uomId)
                    .setParameter("qty", qty).setParameter("price", price).setParameter("subtotal", subtotal)
                    .getSingleResult()).longValue();

                em.createNativeQuery("INSERT INTO pur.po_condition " +
                    "(tenant_id, company_id, header_id, dtl_id, condition_source, condition_class, " +
                    " condition_type, rate, amount, calculation_order) " +
                    "VALUES (:tenant, :company, :hdr, :dtl, 'RFQ', 'PRICE', 'RFQ', NULL, :amount, 10)")
                    .setParameter("tenant", tenantId).setParameter("company", compId)
                    .setParameter("hdr", poId).setParameter("dtl", dtlId)
                    .setParameter("amount", price).executeUpdate();
            }

            em.createNativeQuery("UPDATE pur.purchase_order_hdr SET total_amount = :total, tax_amount = :taxAmt "
                + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("total", grandTotal).setParameter("taxAmt", totalTax).setParameter("id", poId)
                .setParameter("tenantId", tenantId).setParameter("compId", compId).executeUpdate();

            em.createNativeQuery("UPDATE pur.rfq_hdr SET status = 'CLOSED' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", tenantId).setParameter("compId", compId).executeUpdate();
            em.createNativeQuery("UPDATE pur.rfq_supplier SET status = 'WON' WHERE rfq_id = :rfqId AND supplier_id = :supId").setParameter("rfqId", id).setParameter("supId", winningSupplierId).executeUpdate();
            em.createNativeQuery("UPDATE pur.rfq_supplier SET status = 'LOST' WHERE rfq_id = :rfqId AND supplier_id <> :supId").setParameter("rfqId", id).setParameter("supId", winningSupplierId).executeUpdate();

            ra.addFlashAttribute("success", "PO " + poNo + " berhasil dibuat.");
        } catch (Exception e) { Throwable root = e; while (root.getCause() != null && root.getCause() != root) root = root.getCause(); ra.addFlashAttribute("error", "Gagal convert: " + root.getMessage()); }
        return "redirect:/ui/purchasing/rfq/" + id;
    }

    // ============================================================
    // VOID & HELPER
    // ============================================================
    @PostMapping("/{id}/void")
    @Transactional
    public String voidRfq(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.rfq_hdr SET status = 'VOID' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "RFQ berhasil di-void.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/rfq";
    }

    private List<Map<String, Object>> getApprovalMatrix(String docType, BigDecimal amount) {
        List<Map<String, Object>> matrix = new ArrayList<>();
        String sql = "SELECT level, role_required, min_amount, max_amount FROM core.approval_matrix " +
                     "WHERE tenant_id = :tenantId AND doc_type = :docType AND is_active = true " +
                     "AND :amount >= min_amount AND :amount <= max_amount ORDER BY level ASC";
        for (Object[] row : (List<Object[]>) em.createNativeQuery(sql)
                .setParameter("tenantId", context.tenantId())
                .setParameter("docType", docType).setParameter("amount", amount).getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("level", ((Number) row[0]).intValue());
            m.put("roleRequired", row[1]);
            m.put("minAmount", row[2]);
            m.put("maxAmount", row[3]);
            matrix.add(m);
        }
        return matrix;
    }
}
