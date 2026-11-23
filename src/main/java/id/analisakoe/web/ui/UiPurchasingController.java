package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.service.NumberRangeService;
import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import id.analisakoe.pur.service.PurchasingService;          
import id.analisakoe.pur.service.PurchasePolicyService;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequestMapping("/ui/purchasing")
public class UiPurchasingController {

    @Autowired
    private NumberRangeService numberRangeService;

    private final MenuService menuService;
    private final PurchasingService purchasingService;
    private final PurchasePolicyService policyService;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

     public UiPurchasingController(MenuService menuService,
                                   PurchasingService purchasingService,
                                   PurchasePolicyService policyService) {
        this.menuService = menuService;
        this.purchasingService = purchasingService;
        this.policyService = policyService;
    }

    // ============================================================
    // PR - PURCHASE REQUEST
    // ============================================================

    @GetMapping("/pr")
    public String listPr(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Purchase Requests");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.doc_date, h.status, h.remarks, " +
                     "COALESCE(u.username, '-') AS requestor " +
                     "FROM pur.purchase_request_hdr h " +
                     "LEFT JOIN core.app_user u ON h.requisitioner_id = u.id " +
                     "WHERE h.tenant_id = 1 ORDER BY h.id DESC";

        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1] != null ? row[1].toString() : "-");
                Object dateObj = row[2];
                map.put("docDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : null);
                map.put("status", row[3]);
                map.put("remarks", row[4]);
                map.put("requestor", row[5]);
                list.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("prs", list);
        return "ui/purchasing/pr_list";
    }

    @GetMapping("/pr/new")
    public String newPr(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Purchase Request");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", true);
        model.addAttribute("prId", null);
        
        try {
            List<Object[]> itemsRaw = em.createNativeQuery("SELECT id, code, name FROM md.item WHERE tenant_id = 1 AND deleted_at IS NULL ORDER BY name").getResultList();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : itemsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]); map.put("name", row[2]);
                items.add(map);
            }
            model.addAttribute("items", items);
            
            List<Object[]> uomsRaw = em.createNativeQuery("SELECT id, code FROM md.uom WHERE tenant_id = 1 ORDER BY code").getResultList();
            List<Map<String, Object>> uoms = new ArrayList<>();
            for (Object[] row : uomsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]);
                uoms.add(map);
            }
            model.addAttribute("uoms", uoms);
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data master: " + e.getMessage());
        }
        return "ui/purchasing/pr_form";
    }

    @PostMapping("/pr")
    @Transactional
    public String savePr(@RequestParam LocalDate docDate,
                         @RequestParam(required = false) String remarks,
                         @RequestParam("itemId") ArrayList<Long> itemIds,
                         @RequestParam("uomId") ArrayList<Long> uomIds,
                         @RequestParam("qty") ArrayList<BigDecimal> qtys,
                         HttpSession session,
                         RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
            Long requisitionerId = null;
            if (user != null && user.getUsername() != null) {
                try {
                    List<?> result = em.createNativeQuery("SELECT id FROM core.app_user WHERE username = :username")
                        .setParameter("username", user.getUsername())
                        .getResultList();
                    if (!result.isEmpty()) {
                        requisitionerId = ((Number) result.get(0)).longValue();
                    }
                } catch (Exception ignore) { }
            }

            String docNo = numberRangeService.generate("PR", "PR");
            Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
            String sqlHdr = "INSERT INTO pur.purchase_request_hdr (tenant_id, company_id, doc_no, doc_date, remarks, status, requisitioner_id) " +
                            "VALUES (:tenantId, :compId, :docNo, :docDate, :remarks, 'DRAFT', :requisitionerId) RETURNING id";
            Long hdrId = ((Number) em.createNativeQuery(sqlHdr)
                .setParameter("tenantId", tenantId)
                .setParameter("compId", compId)
                .setParameter("docNo", docNo)
                .setParameter("docDate", docDate)
                .setParameter("remarks", remarks)
                .setParameter("requisitionerId", requisitionerId)
                .getSingleResult()).longValue();

            for (int i = 0; i < itemIds.size(); i++) {
                if (qtys.get(i).compareTo(BigDecimal.ZERO) > 0) {
                    em.createNativeQuery("INSERT INTO pur.purchase_request_dtl (header_id, item_id, uom_id, qty) VALUES (:hdrId, :itemId, :uomId, :qty)")
                        .setParameter("hdrId", hdrId)
                        .setParameter("itemId", itemIds.get(i))
                        .setParameter("uomId", uomIds.get(i))
                        .setParameter("qty", qtys.get(i)).executeUpdate();
                }
            }
            ra.addFlashAttribute("success", "PR berhasil disimpan. Requestor: " + (user != null ? user.getUsername() : "-"));
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/pr";
    }

    @PostMapping("/pr/{id}/confirm")
    @Transactional
    public String confirmPr(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.purchase_request_hdr SET status = 'CONFIRMED' WHERE id = :id")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PR berhasil di-confirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/pr";
    }

    @PostMapping("/pr/{id}/unconfirm")
    @Transactional
    public String unconfirmPr(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.purchase_request_hdr SET status = 'DRAFT' WHERE id = :id")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PR berhasil di-unconfirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/pr";
    }

    @PostMapping("/pr/{id}/void")
    @Transactional
    public String voidPr(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.purchase_request_hdr SET status = 'VOID' WHERE id = :id")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PR berhasil di-void.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/pr";
    }

    @PostMapping("/pr/{id}/delete")
    @Transactional
    public String deletePr(@PathVariable Long id, RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery("SELECT status FROM pur.purchase_request_hdr WHERE id = :id")
                .setParameter("id", id).getResultList();
            String status = statusList.isEmpty() ? "" : statusList.get(0).toString();

            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya PR berstatus DRAFT yang bisa dihapus. Status: " + status);
                return "redirect:/ui/purchasing/pr";
            }

            List<?> poRef = em.createNativeQuery("SELECT id FROM pur.purchase_order_dtl WHERE pr_dtl_id IN (SELECT id FROM pur.purchase_request_dtl WHERE header_id = :id)")
                .setParameter("id", id).getResultList();
            if (!poRef.isEmpty()) {
                ra.addFlashAttribute("error", "PR tidak bisa dihapus karena sudah diproses menjadi PO.");
                return "redirect:/ui/purchasing/pr";
            }

            em.createNativeQuery("DELETE FROM pur.purchase_request_dtl WHERE header_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM pur.purchase_request_hdr WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PR berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", "Gagal: " + e.getMessage()); }
        return "redirect:/ui/purchasing/pr";
    }

    @GetMapping("/pr/{id}/edit")
    public String editPr(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Purchase Request");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("prId", id);

        try {
            List<Object[]> hdrRaw = em.createNativeQuery(
                "SELECT id, doc_no, doc_date, remarks, status FROM pur.purchase_request_hdr WHERE id = :id")
                .setParameter("id", id).getResultList();
            
            if (hdrRaw.isEmpty()) {
                ra.addFlashAttribute("error", "PR tidak ditemukan.");
                return "redirect:/ui/purchasing/pr";
            }
            Object[] hdr = hdrRaw.get(0);
            String status = hdr[4] != null ? hdr[4].toString() : "";
            
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya PR berstatus DRAFT yang bisa diedit. Status: " + status);
                return "redirect:/ui/purchasing/pr";
            }
            
            model.addAttribute("prDocNo", hdr[1]);
            Object dateObj = hdr[2];
            model.addAttribute("prDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : LocalDate.now());
            model.addAttribute("prRemarks", hdr[3] != null ? hdr[3].toString() : "");

            List<Object[]> itemsRaw = em.createNativeQuery("SELECT id, code, name FROM md.item WHERE tenant_id = 1 AND deleted_at IS NULL ORDER BY name").getResultList();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : itemsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]); map.put("name", row[2]);
                items.add(map);
            }
            model.addAttribute("items", items);
            
            List<Object[]> uomsRaw = em.createNativeQuery("SELECT id, code FROM md.uom WHERE tenant_id = 1 ORDER BY code").getResultList();
            List<Map<String, Object>> uoms = new ArrayList<>();
            for (Object[] row : uomsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]);
                uoms.add(map);
            }
            model.addAttribute("uoms", uoms);

            List<Object[]> detailsRaw = em.createNativeQuery(
                "SELECT d.id, d.item_id, i.code, i.name, d.qty, d.uom_id, u.code " +
                "FROM pur.purchase_request_dtl d " +
                "LEFT JOIN md.item i ON d.item_id = i.id " +
                "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                "WHERE d.header_id = :id ORDER BY d.id")
                .setParameter("id", id).getResultList();
            List<Map<String, Object>> details = new ArrayList<>();
            for (Object[] row : detailsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("dtlId", row[0]);
                map.put("itemId", row[1]);
                map.put("itemCode", row[2] != null ? row[2] : "");
                map.put("itemName", row[3] != null ? row[3] : "");
                map.put("qty", row[4]);
                map.put("uomId", row[5]);
                map.put("uomCode", row[6] != null ? row[6] : "");
                details.add(map);
            }
            model.addAttribute("existingDetails", details);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat PR: " + e.getMessage());
            return "redirect:/ui/purchasing/pr";
        }
        return "ui/purchasing/pr_form";
    }

    @PostMapping("/pr/{id}")
    @Transactional
    public String updatePr(@PathVariable Long id,
                           @RequestParam LocalDate docDate,
                           @RequestParam(required = false) String remarks,
                           @RequestParam(value = "itemId", required = false) ArrayList<Long> itemIds,
                           @RequestParam(value = "uomId", required = false) ArrayList<Long> uomIds,
                           @RequestParam(value = "qty", required = false) ArrayList<BigDecimal> qtys,
                           RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery("SELECT status FROM pur.purchase_request_hdr WHERE id = :id")
                .setParameter("id", id).getResultList();
            String status = statusList.isEmpty() ? "" : statusList.get(0).toString();
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya PR berstatus DRAFT yang bisa diubah. Status: " + status);
                return "redirect:/ui/purchasing/pr";
            }

            em.createNativeQuery("UPDATE pur.purchase_request_hdr SET doc_date = :docDate, remarks = :remarks WHERE id = :id")
                .setParameter("docDate", docDate)
                .setParameter("remarks", remarks)
                .setParameter("id", id).executeUpdate();

            em.createNativeQuery("DELETE FROM pur.purchase_request_dtl WHERE header_id = :id")
                .setParameter("id", id).executeUpdate();

            if (itemIds != null && !itemIds.isEmpty()) {
                for (int i = 0; i < itemIds.size(); i++) {
                    if (qtys.get(i).compareTo(BigDecimal.ZERO) > 0) {
                        em.createNativeQuery("INSERT INTO pur.purchase_request_dtl (header_id, item_id, uom_id, qty) VALUES (:hdrId, :itemId, :uomId, :qty)")
                            .setParameter("hdrId", id)
                            .setParameter("itemId", itemIds.get(i))
                            .setParameter("uomId", uomIds.get(i))
                            .setParameter("qty", qtys.get(i)).executeUpdate();
                    }
                }
            }

            ra.addFlashAttribute("success", "PR berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update PR: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/pr";
    }


    // ============================================================
    // ============================================================
    // PR PRINT - PRINT PURCHASE REQUEST
    // ============================================================

    @GetMapping("/pr/{id}/print")
    @Transactional(readOnly = true)
    public String printPurchaseRequest(
            @PathVariable Long id,
            HttpSession session,
            Model model) {

        try {
            SessionUser user = (SessionUser)
                session.getAttribute(SessionInterceptor.SESSION_USER);

            if (user == null || user.getTenantId() == null) {
                throw new IllegalStateException("Session tidak valid.");
            }

            List<?> headers = em.createNativeQuery(
                "SELECT h.id, h.doc_no, h.doc_date, h.status, " +
                "COALESCE(h.remarks,''), COALESCE(u.username,'-'), " +
                "COALESCE(h.currency,'IDR') " +
                "FROM pur.purchase_request_hdr h " +
                "LEFT JOIN core.app_user u ON u.id = h.requisitioner_id " +
                "WHERE h.id = :id AND h.tenant_id = :tenantId"
            )
            .setParameter("id", id)
            .setParameter("tenantId", user.getTenantId())
            .getResultList();

            if (headers.isEmpty()) {
                return "redirect:/ui/purchasing/pr";
            }

            Object[] h = (Object[]) headers.get(0);

            model.addAttribute("docNo", h[1]);
            model.addAttribute("docDate",
                h[2] instanceof java.sql.Date
                    ? ((java.sql.Date) h[2]).toLocalDate()
                    : h[2]);
            model.addAttribute("status", h[3]);
            model.addAttribute("remarks", h[4]);
            model.addAttribute("requestor", h[5]);
            model.addAttribute("currency", h[6]);

            List<?> detailRows = em.createNativeQuery(
                "SELECT COALESCE(i.code,'-'), " +
                "COALESCE(i.name,'-'), " +
                "COALESCE(d.short_text,''), " +
                "COALESCE(d.qty,0), " +
                "COALESCE(u.code,'-'), " +
                "d.delivery_date, " +
                "COALESCE(d.estimated_price,0), " +
                "COALESCE(d.account_assignment,'') " +
                "FROM pur.purchase_request_dtl d " +
                "LEFT JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "WHERE d.header_id = :id ORDER BY d.id"
            )
            .setParameter("id", id)
            .getResultList();

            List<Map<String,Object>> items = new ArrayList<>();

            for (Object raw : detailRows) {
                Object[] r = (Object[]) raw;

                Map<String,Object> m = new HashMap<>();
                m.put("code", r[0]);
                m.put("name", r[1]);
                m.put("description", r[2]);
                m.put("qty", r[3]);
                m.put("uom", r[4]);

                m.put("deliveryDate",
                    r[5] instanceof java.sql.Date
                        ? ((java.sql.Date) r[5]).toLocalDate()
                        : r[5]);

                m.put("estimatedPrice", r[6]);
                m.put("accountAssignment", r[7]);

                items.add(m);
            }

            model.addAttribute("items", items);
            model.addAttribute("totalLines", items.size());

            model.addAttribute("pageTitle", "Print Purchase Request");

            List<?> companyRows = em.createNativeQuery(
                "SELECT name FROM core.company " +
                "WHERE id = :companyId"
            )
            .setParameter("companyId", user.getActiveCompanyId())
            .getResultList();

            model.addAttribute(
                "companyName",
                companyRows.isEmpty()
                    ? "AnalisaKoe ERP"
                    : companyRows.get(0).toString()
            );

            return "ui/purchasing/pr_print";

        } catch (Exception e) {
            model.addAttribute("error",
                "Gagal menampilkan PR: " + e.getMessage());
            return "ui/purchasing/pr_print";
        }
    }

    // ============================================================
    // PR REPORT - REKAP PURCHASE REQUEST
    // ============================================================

    @GetMapping("/pr/report")
    @Transactional(readOnly = true)
    public String reportPurchaseRequest(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Long requestorId,
            @RequestParam(required = false) String status,
            HttpSession session,
            Model model) {

        SessionUser user = (SessionUser)
            session.getAttribute(SessionInterceptor.SESSION_USER);

        if (user == null || user.getTenantId() == null) {
            throw new IllegalStateException("Session tidak valid.");
        }

        LocalDate from = dateFrom != null
            ? dateFrom
            : LocalDate.now().withDayOfMonth(1);

        LocalDate to = dateTo != null
            ? dateTo
            : LocalDate.now();

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                "Sampai tanggal tidak boleh lebih awal dari Dari tanggal."
            );
        }

        Long tenantId = user.getTenantId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Purchase Request Recap");
        model.addAttribute("menus",
            menuService.buildTreeForUser(user.getPermissions()));

        model.addAttribute("dateFrom", from);
        model.addAttribute("dateTo", to);
        model.addAttribute("selectedRequestorId", requestorId);
        model.addAttribute("selectedStatus",
            status == null ? "ALL" : status);

        // REQUESTOR DROPDOWN: user yang pernah menjadi requestor
        List<Object[]> requestorRows = em.createNativeQuery(
            "SELECT DISTINCT u.id, u.username " +
            "FROM pur.purchase_request_hdr h " +
            "JOIN core.app_user u ON u.id = h.requisitioner_id " +
            "WHERE h.tenant_id = :tenantId " +
            "ORDER BY u.username"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<Map<String,Object>> requestors = new ArrayList<>();

        for (Object[] r : requestorRows) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", r[0]);
            m.put("username", r[1]);
            requestors.add(m);
        }

        model.addAttribute("requestors", requestors);

        // FILTER
        StringBuilder sql = new StringBuilder(
            "SELECT h.id, h.doc_no, h.doc_date, " +
            "COALESCE(u.username,'-'), " +
            "COALESCE(h.remarks,''), " +
            "COALESCE(h.status,''), " +
            "COUNT(d.id) AS line_count " +
            "FROM pur.purchase_request_hdr h " +
            "LEFT JOIN core.app_user u " +
            "ON u.id = h.requisitioner_id " +
            "LEFT JOIN pur.purchase_request_dtl d " +
            "ON d.header_id = h.id " +
            "WHERE h.tenant_id = :tenantId " +
            "AND h.doc_date BETWEEN :dateFrom AND :dateTo "
        );

        if (requestorId != null) {
            sql.append("AND h.requisitioner_id = :requestorId ");
        }

        if (status != null &&
            !status.isBlank() &&
            !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND h.status = :status ");
        }

        sql.append(
            "GROUP BY h.id, h.doc_no, h.doc_date, " +
            "u.username, h.remarks, h.status " +
            "ORDER BY h.doc_date, h.doc_no"
        );

        var query = em.createNativeQuery(sql.toString())
            .setParameter("tenantId", tenantId)
            .setParameter("dateFrom", java.sql.Date.valueOf(from))
            .setParameter("dateTo", java.sql.Date.valueOf(to));

        if (requestorId != null) {
            query.setParameter("requestorId", requestorId);
        }

        if (status != null &&
            !status.isBlank() &&
            !"ALL".equalsIgnoreCase(status)) {
            query.setParameter("status", status);
        }

        List<?> resultRows = query.getResultList();

        List<Map<String,Object>> reportRows = new ArrayList<>();

        long totalPR = 0;
        long totalLines = 0;
        long draftCount = 0;
        long confirmedCount = 0;
        long voidCount = 0;

        for (Object raw : resultRows) {

            Object[] r = (Object[]) raw;

            Map<String,Object> m = new HashMap<>();

            m.put("id", r[0]);
            m.put("docNo", r[1]);
            m.put("docDate",
                r[2] instanceof java.sql.Date
                    ? ((java.sql.Date) r[2]).toLocalDate()
                    : r[2]);
            m.put("requestor", r[3]);
            m.put("remarks", r[4]);
            m.put("status", r[5]);

            long lines = ((Number) r[6]).longValue();

            m.put("lineCount", lines);

            reportRows.add(m);

            totalPR++;
            totalLines += lines;

            String st = r[5] == null ? "" : r[5].toString();

            if ("DRAFT".equalsIgnoreCase(st)) draftCount++;
            if ("CONFIRMED".equalsIgnoreCase(st)) confirmedCount++;
            if ("VOID".equalsIgnoreCase(st)) voidCount++;
        }

        model.addAttribute("reportRows", reportRows);
        model.addAttribute("totalPR", totalPR);
        model.addAttribute("totalLines", totalLines);
        model.addAttribute("draftCount", draftCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("voidCount", voidCount);

        return "ui/purchasing/pr_report";
    }
    // PO - PURCHASE ORDER
    // ============================================================

    @GetMapping("/po")
    public String listPo(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Purchase Orders");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.doc_date, COALESCE(s.name, '-'), COALESCE(h.currency, 'IDR'), COALESCE(h.total_amount, 0), h.status " +
                     "FROM pur.purchase_order_hdr h LEFT JOIN md.supplier s ON h.supplier_id = s.id " +
                     "WHERE h.tenant_id = 1 ORDER BY h.id DESC";
        List<Map<String, Object>> orderList = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1] != null ? row[1].toString() : "-");
                Object dateObj = row[2];
                map.put("docDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : null);
                map.put("supplierName", row[3]);
                map.put("currency", row[4]);
                map.put("totalAmount", row[5]);
                map.put("status", row[6]);
                orderList.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("orders", orderList);
        return "ui/purchasing/po_list";
    }

    @GetMapping("/po/new")
    public String newPo(@RequestParam(required = false) Long prId, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Purchase Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("prId", prId);
        model.addAttribute("isNew", true);
        model.addAttribute("poId", null);
        model.addAttribute("isView", false);
        model.addAttribute("poStatus", null);
        model.addAttribute("existingDetails", new ArrayList<>());
        model.addAttribute("poTaxType", "NO_TAX");

        try {
            List<Object[]> supRaw = em.createNativeQuery("SELECT id, name FROM md.supplier WHERE tenant_id = 1 ORDER BY name").getResultList();
            List<Map<String, Object>> suppliers = new ArrayList<>();
            for (Object[] row : supRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("name", row[1]);
                suppliers.add(map);
            }
            model.addAttribute("suppliers", suppliers);

            List<Object[]> uomRaw = em.createNativeQuery("SELECT id, code FROM md.uom WHERE tenant_id = 1 ORDER BY code").getResultList();
            List<Map<String, Object>> uoms = new ArrayList<>();
            for (Object[] row : uomRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]);
                uoms.add(map);
            }
            model.addAttribute("uoms", uoms);

            List<Object[]> itemRaw = em.createNativeQuery("SELECT id, code, name FROM md.item WHERE tenant_id = 1 AND deleted_at IS NULL ORDER BY name").getResultList();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : itemRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]); map.put("name", row[2]);
                items.add(map);
            }
            model.addAttribute("items", items);
			            // --- DAFTAR PR: yang masih punya sisa qty (CONFIRMED atau PO_CREATED parsial) ---
            // Jangan pakai NOT EXISTS terhadap PO, karena PR yang baru sebagian
            // diambil PO (mis. 30 dari 50) ikut hilang dari pilihan.
            List<Object[]> prRaw = em.createNativeQuery(
                "SELECT h.id, h.doc_no, h.doc_date FROM pur.purchase_request_hdr h " +
                "WHERE h.tenant_id = 1 AND h.status IN ('CONFIRMED', 'PO_CREATED') " +
                "AND EXISTS (" +
                "    SELECT 1 FROM pur.purchase_request_dtl d " +
                "    WHERE d.header_id = h.id " +
                "    AND d.qty > COALESCE((" +
                "        SELECT SUM(pod.qty) FROM pur.purchase_order_dtl pod " +
                "        JOIN pur.purchase_order_hdr poh ON poh.id = pod.header_id " +
                "        WHERE poh.pr_id = h.id " +
                "        AND pod.item_id = d.item_id " +
                "        AND poh.status <> 'VOID'" +
                "    ), 0)" +
                ") " +
                "ORDER BY h.id DESC"
            ).getResultList();
            
            List<Map<String, Object>> availablePrs = new ArrayList<>();
            for (Object[] row : prRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1]);
                availablePrs.add(map);
            }
            model.addAttribute("availablePrs", availablePrs);
            // ----------------------------------------------------

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data master: " + e.getMessage());
        }
        return "ui/purchasing/po_form";
    }

    /**
     * Validasi kuantitas PO.
     * Jika PO berasal dari PR, total Qty PO tidak boleh melebihi
     * total Qty PR berdasarkan kombinasi item dan UOM.
     * excludePoId dipakai ketika mengubah PO agar PO tersebut
     * tidak dihitung sebagai pemakaian PO lain.
     */
    private void validatePoPrQuantities(
            Long prId,
            Long excludePoId,
            ArrayList<String> itemIds,
            ArrayList<BigDecimal> quantities,
            ArrayList<String> uomIds) {

        if (itemIds == null || itemIds.isEmpty()) {
            if (prId != null) {
                throw new IllegalArgumentException(
                    "PO dari PR wajib memiliki minimal satu baris item."
                );
            }
            return;
        }

        if (quantities == null || uomIds == null ||
                quantities.size() != itemIds.size() ||
                uomIds.size() != itemIds.size()) {
            throw new IllegalArgumentException(
                "Data item, Qty, dan UOM tidak sesuai. Silakan muat ulang form PO."
            );
        }

        Map<String, BigDecimal> submitted = new HashMap<>();

        for (int i = 0; i < itemIds.size(); i++) {
            String itemText = itemIds.get(i);
            String uomText = uomIds.get(i);
            BigDecimal qty = quantities.get(i);

            if (itemText == null || itemText.trim().isEmpty()) {
                continue;
            }

            if (uomText == null || uomText.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "UOM wajib dipilih untuk setiap baris item."
                );
            }

            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                    "Qty harus lebih besar dari nol pada baris " + (i + 1) + "."
                );
            }

            Long itemId;
            Long uomId;

            try {
                itemId = Long.parseLong(itemText.trim());
                uomId = Long.parseLong(uomText.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                    "Item atau UOM tidak valid pada baris " + (i + 1) + "."
                );
            }

            if (prId != null) {
                String key = itemId + "|" + uomId;
                submitted.merge(key, qty, BigDecimal::add);
            }
        }

        if (prId == null) {
            return;
        }

        for (Map.Entry<String, BigDecimal> entry : submitted.entrySet()) {
            String[] keyParts = entry.getKey().split("\\|");
            Long itemId = Long.valueOf(keyParts[0]);
            Long uomId = Long.valueOf(keyParts[1]);
            BigDecimal requestedQty = entry.getValue();

            Object requestedObj = em.createNativeQuery(
                    "SELECT COALESCE(SUM(d.qty), 0) " +
                    "FROM pur.purchase_request_dtl d " +
                    "WHERE d.header_id = :prId " +
                    "AND d.item_id = :itemId " +
                    "AND d.uom_id = :uomId"
                )
                .setParameter("prId", prId)
                .setParameter("itemId", itemId)
                .setParameter("uomId", uomId)
                .getSingleResult();

            BigDecimal prQty = requestedObj != null
                ? new BigDecimal(requestedObj.toString())
                : BigDecimal.ZERO;

            if (prQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                    "Item ID " + itemId + " dengan UOM ID " + uomId +
                    " tidak ditemukan pada PR yang dipilih."
                );
            }

            // Kondisi excludePoId dibangun dinamis: PostgreSQL tidak dapat
            // menentukan tipe parameter null dalam bentuk ":excludePoId IS NULL"
            // (SQLState 42P18), sehingga pola "(:x IS NULL OR ...)" harus dihindari.
            String usedSql =
                "SELECT COALESCE(SUM(pod.qty), 0) " +
                "FROM pur.purchase_order_dtl pod " +
                "JOIN pur.purchase_order_hdr poh ON poh.id = pod.header_id " +
                "WHERE poh.pr_id = :prId " +
                "AND pod.item_id = :itemId " +
                "AND pod.uom_id = :uomId " +
                "AND poh.status <> 'VOID'";

            jakarta.persistence.Query usedQuery = em.createNativeQuery(usedSql)
                .setParameter("prId", prId)
                .setParameter("itemId", itemId)
                .setParameter("uomId", uomId);
            if (excludePoId != null) {
                usedSql += " AND poh.id <> :excludePoId";
                usedQuery = em.createNativeQuery(usedSql)
                    .setParameter("prId", prId)
                    .setParameter("itemId", itemId)
                    .setParameter("uomId", uomId)
                    .setParameter("excludePoId", excludePoId);
            }

            Object usedObj = usedQuery.getSingleResult();

            BigDecimal usedQty = usedObj != null
                ? new BigDecimal(usedObj.toString())
                : BigDecimal.ZERO;

            BigDecimal remainingQty = prQty.subtract(usedQty);

            if (requestedQty.compareTo(remainingQty) > 0) {
                throw new IllegalArgumentException(
                    "Qty PO melebihi sisa PR. Item ID " + itemId +
                    ", UOM ID " + uomId +
                    ". Qty PR: " + prQty.stripTrailingZeros().toPlainString() +
                    ", sudah dibuat PO: " + usedQty.stripTrailingZeros().toPlainString() +
                    ", sisa tersedia: " + remainingQty.max(BigDecimal.ZERO)
                        .stripTrailingZeros().toPlainString() +
                    ", Qty yang dimasukkan: " + requestedQty.stripTrailingZeros().toPlainString() + "."
                );
            }
        }
    }
     
    @PostMapping("/po")
public String savePo(@RequestParam String docDate,
                     @RequestParam Long supplierId,
                     @RequestParam(required = false) Long prId,
                     @RequestParam String currency,
                     @RequestParam(required = false) String remarks,
                     @RequestParam(required = false) String taxType,
                     @RequestParam(value = "itemId", required = false) ArrayList<String> itemIdStrs,
                     @RequestParam(value = "qty", required = false) ArrayList<BigDecimal> qtys,
                     @RequestParam(value = "uomId", required = false) ArrayList<String> uomIdStrs,
                     @RequestParam(value = "price", required = false) ArrayList<String> prices,
                     HttpSession session,
                     RedirectAttributes ra) {
    SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
    final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
    final Long compId = (user != null && user.getActiveCompanyId() != null) ? user.getActiveCompanyId() : 1L;
    try {
        // Transaksi dibungkus TransactionTemplate agar exception asli dari
        // dalam transaksi tetap sampai ke catch di bawah ini. Jika memakai
        // @Transactional pada method ini, exception yang tertelan akan
        // meninggalkan transaksi rollback-only dan commit gagal dengan
        // UnexpectedRollbackException yang menutupi error sebenarnya.
        String docNo = transactionTemplate.execute(status ->
            doSavePo(tenantId, compId, docDate, supplierId, prId, currency, remarks,
                     taxType, itemIdStrs, qtys, uomIdStrs, prices));
        ra.addFlashAttribute("success", "PO " + docNo + " berhasil dibuat.");
    } catch (Exception e) {
        ra.addFlashAttribute("error", "Gagal menyimpan PO: " + rootMessage(e));
    }
    return "redirect:/ui/purchasing/po";
}

protected String doSavePo(Long tenantId, Long companyId, String docDate, Long supplierId, Long prId, String currency,
                          String remarks, String taxType,
                          ArrayList<String> itemIdStrs, ArrayList<BigDecimal> qtys,
                          ArrayList<String> uomIdStrs, ArrayList<String> prices) {

    validatePoPrQuantities(prId, null, itemIdStrs, qtys, uomIdStrs);

    String docNo = numberRangeService.generate("PO", "PO");
    LocalDate parsedDate = LocalDate.parse(docDate);
    if (taxType == null || taxType.isEmpty()) taxType = "NO_TAX";

    String sqlHdr = "INSERT INTO pur.purchase_order_hdr " +
        "(tenant_id, company_id, doc_no, doc_date, supplier_id, currency, remarks, status, " +
        " total_amount, tax_type, tax_amount, created_at, pr_id) " +
        "VALUES (:tenantId, :compId, :docNo, :docDate, :supplierId, :currency, :remarks, 'DRAFT', 0, :taxType, 0, NOW(), :prId) " +
        "RETURNING id";

    Long hdrId = ((Number) em.createNativeQuery(sqlHdr)
        .setParameter("tenantId", tenantId)
        .setParameter("compId", companyId)
        .setParameter("docNo", docNo)
        .setParameter("docDate", parsedDate)
        .setParameter("supplierId", supplierId)
        .setParameter("currency", currency)
        .setParameter("remarks", remarks)
        .setParameter("taxType", taxType)
        .setParameter("prId", prId)
        .getSingleResult()).longValue();

    BigDecimal grandTotal = BigDecimal.ZERO;
    BigDecimal totalTax = BigDecimal.ZERO;

    if (itemIdStrs != null) {
        for (int i = 0; i < itemIdStrs.size(); i++) {
            String itemIdStr = itemIdStrs.get(i);
            if (itemIdStr == null || itemIdStr.trim().isEmpty()) continue;

            String uomIdStr = uomIdStrs.get(i);
            Long itemId = Long.parseLong(itemIdStr.trim());
            Long uomId = Long.parseLong(uomIdStr.trim());
            BigDecimal qty = qtys.get(i);

            String priceStr = prices.get(i) == null ? "" : prices.get(i).replaceAll("[^0-9]", "");
            BigDecimal price = priceStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(priceStr);

            BigDecimal subtotal = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal;

            if ("EXCLUDE".equals(taxType)) {
                BigDecimal ppn = subtotal.multiply(new BigDecimal("0.11")).setScale(2, RoundingMode.HALF_UP);
                total = subtotal.add(ppn);
                totalTax = totalTax.add(ppn);
            } else if ("INCLUDE".equals(taxType)) {
                BigDecimal ppn = subtotal.multiply(new BigDecimal("11"))
                    .divide(new BigDecimal("111"), 2, RoundingMode.HALF_UP);
                totalTax = totalTax.add(ppn);
            }

            grandTotal = grandTotal.add(total);

            em.createNativeQuery(
                "INSERT INTO pur.purchase_order_dtl (header_id, item_id, uom_id, qty, price, subtotal) " +
                "VALUES (:hdrId, :itemId, :uomId, :qty, :price, :subtotal)")
              .setParameter("hdrId", hdrId)
              .setParameter("itemId", itemId)
              .setParameter("uomId", uomId)
              .setParameter("qty", qty)
              .setParameter("price", price)
              .setParameter("subtotal", subtotal)
              .executeUpdate();
        }
    }

    em.createNativeQuery("UPDATE pur.purchase_order_hdr SET total_amount = :total, tax_amount = :taxAmt WHERE id = :id")
      .setParameter("total", grandTotal)
      .setParameter("taxAmt", totalTax)
      .setParameter("id", hdrId)
      .executeUpdate();

    if (prId != null && prId > 0) {
        em.createNativeQuery("UPDATE pur.purchase_request_hdr SET status = 'PO_CREATED' WHERE id = :prId")
          .setParameter("prId", prId)
          .executeUpdate();
    }

    return docNo;
}

private String rootMessage(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
    return cur.getMessage() != null ? cur.getMessage() : cur.toString();
}

    @GetMapping("/po/{id}/edit")
    public String editPo(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Purchase Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("poId", id);
        model.addAttribute("today", LocalDate.now());

        try {
            List<Object[]> hdrRaw = em.createNativeQuery(
                "SELECT id, doc_no, doc_date, supplier_id, currency, remarks, status, total_amount, pr_id, tax_type, tax_amount " +
                "FROM pur.purchase_order_hdr WHERE id = :id")
                .setParameter("id", id).getResultList();

            if (hdrRaw.isEmpty()) {
                ra.addFlashAttribute("error", "PO tidak ditemukan.");
                return "redirect:/ui/purchasing/po";
            }
            Object[] hdr = hdrRaw.get(0);
            String status = hdr[6] != null ? hdr[6].toString() : "";

            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya PO berstatus DRAFT yang bisa diedit. Status: " + status);
                return "redirect:/ui/purchasing/po";
            }

            model.addAttribute("poDocNo", hdr[1]);
            Object dateObj = hdr[2];
            model.addAttribute("poDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : LocalDate.now());
            model.addAttribute("poSupplierId", hdr[3]);
            model.addAttribute("poCurrency", hdr[4] != null ? hdr[4].toString() : "IDR");
            model.addAttribute("poRemarks", hdr[5] != null ? hdr[5].toString() : "");
            model.addAttribute("poTotal", hdr[7]);
            
            model.addAttribute("isView", false);
            model.addAttribute("poStatus", status);
            model.addAttribute("poTaxType", (hdr.length > 9 && hdr[9] != null) ? hdr[9].toString() : "NO_TAX");
            model.addAttribute("poTaxAmount", (hdr.length > 10 && hdr[10] != null) ? hdr[10] : BigDecimal.ZERO);

            List<Object[]> supRaw = em.createNativeQuery("SELECT id, name FROM md.supplier WHERE tenant_id = 1 ORDER BY name").getResultList();
            List<Map<String, Object>> suppliers = new ArrayList<>();
            for (Object[] row : supRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("name", row[1]);
                suppliers.add(map);
            }
            model.addAttribute("suppliers", suppliers);

            List<Object[]> uomRaw = em.createNativeQuery("SELECT id, code FROM md.uom WHERE tenant_id = 1 ORDER BY code").getResultList();
            List<Map<String, Object>> uoms = new ArrayList<>();
            for (Object[] row : uomRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]);
                uoms.add(map);
            }
            model.addAttribute("uoms", uoms);

            List<Object[]> itemRaw = em.createNativeQuery("SELECT id, code, name FROM md.item WHERE tenant_id = 1 AND deleted_at IS NULL ORDER BY name").getResultList();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : itemRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("code", row[1]); map.put("name", row[2]);
                items.add(map);
            }
            model.addAttribute("items", items);

            String detailSql = "SELECT d.id, d.item_id, i.code, i.name, d.qty, d.uom_id, u.code, d.price, d.subtotal " +
                "FROM pur.purchase_order_dtl d " +
                "LEFT JOIN md.item i ON d.item_id = i.id " +
                "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                "WHERE d.header_id = :id ORDER BY d.id";

            List<Object[]> detailsRaw = em.createNativeQuery(detailSql).setParameter("id", id).getResultList();
            List<Map<String, Object>> details = new ArrayList<>();
            for (Object[] row : detailsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("dtlId", row[0]);
                map.put("itemId", row[1]);
                map.put("itemCode", row[2] != null ? row[2] : "");
                map.put("itemName", row[3] != null ? row[3] : "");
                map.put("qty", row[4]);
                Object uomIdObj = row[5];
                map.put("uomId", uomIdObj != null ? ((Number) uomIdObj).longValue() : null);
                map.put("uomCode", row[6] != null ? row[6] : "");
                map.put("price", row[7]);
                map.put("subtotal", row[8]);
                details.add(map);
            }
            model.addAttribute("existingDetails", details);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat PO: " + e.getMessage());
            return "redirect:/ui/purchasing/po";
        }
        return "ui/purchasing/po_form";
    }

    @PostMapping("/po/{id}")
    public String updatePo(@PathVariable Long id,
                           @RequestParam String docDate,
                           @RequestParam Long supplierId,
                           @RequestParam String currency,
                           @RequestParam(required = false) String remarks,
                           @RequestParam(required = false) String taxType,
                           @RequestParam(value = "itemId", required = false) ArrayList<String> itemIdStrs,
                           @RequestParam(value = "qty", required = false) ArrayList<BigDecimal> qtys,
                           @RequestParam(value = "uomId", required = false) ArrayList<String> uomIdStrs,
                           @RequestParam(value = "price", required = false) ArrayList<String> prices,
                           RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery(
                    "SELECT status, pr_id FROM pur.purchase_order_hdr WHERE id = :id"
                )
                .setParameter("id", id).getResultList();

            if (statusList.isEmpty()) {
                ra.addFlashAttribute("error", "PO tidak ditemukan.");
                return "redirect:/ui/purchasing/po";
            }

            Object[] poHeader = (Object[]) statusList.get(0);
            String status = poHeader[0] != null ? poHeader[0].toString() : "";
            Long prId = poHeader[1] != null
                ? ((Number) poHeader[1]).longValue()
                : null;

            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya PO berstatus DRAFT yang bisa diubah. Status: " + status);
                return "redirect:/ui/purchasing/po";
            }

            // Validasi dilakukan sebelum header diperbarui dan detail lama dihapus.
            // Semua penulisan dibungkus TransactionTemplate (bukan @Transactional
            // pada method controller) supaya exception asli tetap sampai ke catch
            // di bawah, bukan UnexpectedRollbackException saat commit.
            transactionTemplate.executeWithoutResult(tx -> {
                validatePoPrQuantities(prId, id, itemIdStrs, qtys, uomIdStrs);

                LocalDate parsedDate = LocalDate.parse(docDate);
                String taxTypeLocal = (taxType == null || taxType.isEmpty()) ? "NO_TAX" : taxType;

                em.createNativeQuery("UPDATE pur.purchase_order_hdr SET doc_date = :docDate, supplier_id = :supplierId, " +
                                     "currency = :currency, remarks = :remarks, tax_type = :taxType WHERE id = :id")
                    .setParameter("docDate", parsedDate)
                    .setParameter("supplierId", supplierId)
                    .setParameter("currency", currency)
                    .setParameter("remarks", remarks)
                    .setParameter("taxType", taxTypeLocal)
                    .setParameter("id", id).executeUpdate();

                em.createNativeQuery("DELETE FROM pur.purchase_order_dtl WHERE header_id = :id")
                    .setParameter("id", id).executeUpdate();

                BigDecimal grandTotal = BigDecimal.ZERO;
                BigDecimal totalTax = BigDecimal.ZERO;

                if (itemIdStrs != null && !itemIdStrs.isEmpty()) {
                    for (int i = 0; i < itemIdStrs.size(); i++) {
                        String itemIdStr = itemIdStrs.get(i);
                        if (itemIdStr == null || itemIdStr.trim().isEmpty()) continue;

                        String uomIdStr = uomIdStrs.get(i);
                        if (uomIdStr == null || uomIdStr.trim().isEmpty()) {
                            throw new RuntimeException("UOM wajib dipilih untuk setiap baris item.");
                        }

                        Long itemId = Long.parseLong(itemIdStr);
                        Long uomId = Long.parseLong(uomIdStr);
                        BigDecimal qty = qtys.get(i);
                        BigDecimal price = BigDecimal.ZERO;
                        String priceStr = prices.get(i) != null ? prices.get(i).replaceAll("[^0-9]", "") : "";
                        if (!priceStr.isEmpty()) price = new BigDecimal(priceStr);

                        BigDecimal subtotal = qty.multiply(price);
                        BigDecimal total = subtotal;
                        if ("EXCLUDE".equals(taxTypeLocal)) {
                            BigDecimal ppn = subtotal.multiply(new BigDecimal("0.11"));
                            total = subtotal.add(ppn);
                            totalTax = totalTax.add(ppn);
                        } else if ("INCLUDE".equals(taxTypeLocal)) {
                            BigDecimal ppn = subtotal.multiply(new BigDecimal("11").divide(new BigDecimal("111"), 2, RoundingMode.HALF_UP));
                            totalTax = totalTax.add(ppn);
                        }

                        grandTotal = grandTotal.add(total);

                        em.createNativeQuery("INSERT INTO pur.purchase_order_dtl (header_id, item_id, uom_id, qty, price, subtotal) " +
                                             "VALUES (:hdrId, :itemId, :uomId, :qty, :price, :subtotal)")
                            .setParameter("hdrId", id)
                            .setParameter("itemId", itemId)
                            .setParameter("uomId", uomId)
                            .setParameter("qty", qty)
                            .setParameter("price", price)
                            .setParameter("subtotal", subtotal).executeUpdate();
                    }
                }

                em.createNativeQuery("UPDATE pur.purchase_order_hdr SET total_amount = :total, tax_amount = :taxAmt WHERE id = :id")
                    .setParameter("total", grandTotal).setParameter("taxAmt", totalTax).setParameter("id", id).executeUpdate();
            });

            ra.addFlashAttribute("success", "Purchase Order berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update PO: " + rootMessage(e));
        }
        return "redirect:/ui/purchasing/po";
    }

    @PostMapping("/po/{id}/confirm")
    @Transactional
    public String confirmPo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.purchase_order_hdr SET status = 'CONFIRMED' WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PO berhasil di-confirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/po";
    }

    @PostMapping("/po/{id}/unconfirm")
    @Transactional
    public String unconfirmPo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.purchase_order_hdr SET status = 'DRAFT' WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PO berhasil di-unconfirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/po";
    }

    @PostMapping("/po/{id}/void")
    @Transactional
    public String voidPo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE pur.purchase_order_hdr SET status = 'VOID' WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PO berhasil di-void.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/purchasing/po";
    }

    @PostMapping("/po/{id}/delete")
    @Transactional
    public String deletePo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery("SELECT status FROM pur.purchase_order_hdr WHERE id = :id").setParameter("id", id).getResultList();
            String status = statusList.isEmpty() ? "" : statusList.get(0).toString();
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya PO berstatus DRAFT yang bisa dihapus. Status: " + status);
                return "redirect:/ui/purchasing/po";
            }
            List<?> grnRef = em.createNativeQuery("SELECT id FROM pur.grn_hdr WHERE po_id = :id").setParameter("id", id).getResultList();
            if (!grnRef.isEmpty()) {
                ra.addFlashAttribute("error", "PO tidak bisa dihapus karena sudah memiliki GRN. Silakan Void PO ini.");
                return "redirect:/ui/purchasing/po";
            }
            em.createNativeQuery("DELETE FROM pur.purchase_order_dtl WHERE header_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM pur.purchase_order_hdr WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "PO berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", "Gagal: " + e.getMessage()); }
        return "redirect:/ui/purchasing/po";
    }

    @GetMapping("/po/{id}/view")
    public String viewPo(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "View Purchase Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("isView", true);
        model.addAttribute("poId", id);
        model.addAttribute("today", LocalDate.now());

        try {
            // Tambahkan tax_type dan tax_amount di query
            List<Object[]> hdrRaw = em.createNativeQuery(
                "SELECT id, doc_no, doc_date, supplier_id, currency, remarks, status, total_amount, pr_id, tax_type, tax_amount " +
                "FROM pur.purchase_order_hdr WHERE id = :id").setParameter("id", id).getResultList();
            if (hdrRaw.isEmpty()) {
                ra.addFlashAttribute("error", "PO tidak ditemukan.");
                return "redirect:/ui/purchasing/po";
            }
            Object[] hdr = hdrRaw.get(0);
            model.addAttribute("poDocNo", hdr[1]);
            Object dateObj = hdr[2];
            model.addAttribute("poDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : LocalDate.now());
            model.addAttribute("poSupplierId", hdr[3]);
            model.addAttribute("poCurrency", hdr[4] != null ? hdr[4].toString() : "IDR");
            model.addAttribute("poRemarks", hdr[5] != null ? hdr[5].toString() : "");
            model.addAttribute("poStatus", hdr[6] != null ? hdr[6].toString() : "");
            model.addAttribute("poTotal", hdr[7]);
            
            // TAMBAHKAN 2 BARIS INI UNTUK PAJAK
            model.addAttribute("poTaxType", hdr[9] != null ? hdr[9].toString() : "NO_TAX");
            model.addAttribute("poTaxAmount", hdr[10] != null ? hdr[10] : 0);

            List<Object[]> supRaw = em.createNativeQuery("SELECT id, name FROM md.supplier WHERE tenant_id = 1 ORDER BY name").getResultList();
            List<Map<String, Object>> suppliers = new ArrayList<>();
            for (Object[] row : supRaw) { Map<String, Object> map = new HashMap<>(); map.put("id", row[0]); map.put("name", row[1]); suppliers.add(map); }
            model.addAttribute("suppliers", suppliers);

            List<Object[]> uomRaw = em.createNativeQuery("SELECT id, code FROM md.uom WHERE tenant_id = 1 ORDER BY code").getResultList();
            List<Map<String, Object>> uoms = new ArrayList<>();
            for (Object[] row : uomRaw) { Map<String, Object> map = new HashMap<>(); map.put("id", row[0]); map.put("code", row[1]); uoms.add(map); }
            model.addAttribute("uoms", uoms);

            List<Object[]> itemRaw = em.createNativeQuery("SELECT id, code, name FROM md.item WHERE tenant_id = 1 AND deleted_at IS NULL ORDER BY name").getResultList();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : itemRaw) { Map<String, Object> map = new HashMap<>(); map.put("id", row[0]); map.put("code", row[1]); map.put("name", row[2]); items.add(map); }
            model.addAttribute("items", items);

            List<Object[]> detailsRaw = em.createNativeQuery(
                "SELECT d.id, d.item_id, i.code, i.name, d.qty, d.uom_id, u.code, d.price, d.subtotal " +
                "FROM pur.purchase_order_dtl d LEFT JOIN md.item i ON d.item_id = i.id " +
                "LEFT JOIN md.uom u ON d.uom_id = u.id WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();
            List<Map<String, Object>> details = new ArrayList<>();
            for (Object[] row : detailsRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("dtlId", row[0]); map.put("itemId", row[1]);
                map.put("itemCode", row[2] != null ? row[2] : ""); map.put("itemName", row[3] != null ? row[3] : "");
                map.put("qty", row[4]); Object uomIdObj = row[5];
                map.put("uomId", uomIdObj != null ? ((Number) uomIdObj).longValue() : null);
                map.put("uomCode", row[6] != null ? row[6] : ""); map.put("price", row[7]); map.put("subtotal", row[8]);
                details.add(map);
            }
            model.addAttribute("existingDetails", details);

            String supplierName = "-";
            for (Object[] s : supRaw) {
                if (s[0] != null && hdr[3] != null && s[0].toString().equals(hdr[3].toString())) {
                    supplierName = s[1] != null ? s[1].toString() : "-"; break;
                }
            }
            model.addAttribute("poSupplierName", supplierName);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat PO: " + e.getMessage());
            return "redirect:/ui/purchasing/po";
        }
        return "ui/purchasing/po_form";
    }

    // ============================================================
    // API UNTUK FORM (PR & PRICE)
    // ============================================================

        @GetMapping("/api/pr-available")
    @ResponseBody
    public List<Map<String, Object>> getAvailablePrs() {
        // Tampilkan PR berstatus CONFIRMED atau PO_CREATED yang masih ada sisa item
        // (PR sebagian diambil PO mendapat status PO_CREATED, tapi sisa qty tetap boleh dibuat PO lagi)
        String sql = "SELECT h.id, h.doc_no, h.remarks " +
                     "FROM pur.purchase_request_hdr h " +
                     "WHERE h.status IN ('CONFIRMED', 'PO_CREATED') " +
                     "AND EXISTS (" +
                     "    SELECT 1 FROM pur.purchase_request_dtl d " +
                     "    WHERE d.header_id = h.id " +
                     "    AND d.qty > COALESCE((" +
                     "        SELECT SUM(pod.qty) FROM pur.purchase_order_dtl pod " +
                     "        JOIN pur.purchase_order_hdr poh ON poh.id = pod.header_id " +
                     "        WHERE poh.pr_id = h.id " +
                     "        AND pod.item_id = d.item_id " +
                     "        AND poh.status <> 'VOID'" +
                     "    ), 0)" +
                     ") " +
                     "ORDER BY h.id DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql).getResultList()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]); map.put("docNo", row[1]); map.put("remarks", row[2]);
                list.add(map);
            }
        } catch (Exception e) { 
            System.err.println("[getAvailablePrs Error] " + e.getMessage());
        }
        return list;
    }

    @GetMapping("/api/pr-details/{id}")
    @ResponseBody
    public List<Map<String, Object>> getPrDetails(@PathVariable Long id) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String sql = "SELECT d.id, d.item_id, i.name, d.qty, d.uom_id, u.code " +
                         "FROM pur.purchase_request_dtl d " +
                         "JOIN md.item i ON d.item_id = i.id " +
                         "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                         "WHERE d.header_id = :id";
            List<Object[]> prItems = em.createNativeQuery(sql).setParameter("id", id).getResultList();
            
            for (Object[] row : prItems) {
                Long dtlId = ((Number) row[0]).longValue();
                Long itemId = ((Number) row[1]).longValue();
                String itemName = row[2] != null ? row[2].toString() : "";
                BigDecimal originalQty = new BigDecimal(row[3].toString());
                Long uomId = row[4] != null ? ((Number) row[4]).longValue() : null;
                String uomCode = row[5] != null ? row[5].toString() : "";
                
                String sumSql = "SELECT COALESCE(SUM(pod.qty), 0) " +
                                "FROM pur.purchase_order_dtl pod " +
                                "JOIN pur.purchase_order_hdr poh ON poh.id = pod.header_id " +
                                "WHERE poh.pr_id = :prId AND pod.item_id = :itemId AND poh.status <> 'VOID'";
                Object sumObj = em.createNativeQuery(sumSql)
                    .setParameter("prId", id).setParameter("itemId", itemId).getSingleResult();
                
                BigDecimal usedQty = sumObj != null ? new BigDecimal(sumObj.toString()) : BigDecimal.ZERO;
                BigDecimal remainingQty = originalQty.subtract(usedQty);
                
                if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("prDtlId", dtlId); map.put("itemId", itemId); map.put("itemName", itemName);
                    map.put("qty", remainingQty); // Kirim SISA qty
                    map.put("uomId", uomId); map.put("uom", uomCode);
					map.put("price", 0);
                    list.add(map);
                }
            }
        } catch (Exception e) { 
            System.err.println("[getPrDetails Error] " + e.getMessage());
        }
        return list;
    }

        @GetMapping("/api/get-price")
    @ResponseBody
    public Map<String, Object> getPrice(
            @RequestParam Long supplierId,
            @RequestParam Long itemId,
            @RequestParam(required = false) Long uomId,
            @RequestParam(required = false) String date) {

        Map<String, Object> result = new HashMap<>();

        try {
            if (supplierId == null || itemId == null || uomId == null) {
                result.put("found", false);
                result.put("message", "Supplier, item, dan UOM wajib dipilih.");
                return result;
            }

            LocalDate poDate = (date != null && !date.isEmpty())
                    ? LocalDate.parse(date)
                    : LocalDate.now();

            String sql =
                    "SELECT price, currency FROM pur.price_list " +
                    "WHERE supplier_id = :supId " +
                    "AND item_id = :itemId " +
                    "AND uom_id = :uomId " +
                    "AND valid_from <= :poDate " +
                    "AND (valid_to IS NULL OR valid_to >= :poDate) " +
                    "AND tenant_id = 1 " +
                    "ORDER BY min_qty DESC LIMIT 1";

            List<?> rows = em.createNativeQuery(sql)
                    .setParameter("supId", supplierId)
                    .setParameter("itemId", itemId)
                    .setParameter("uomId", uomId)
                    .setParameter("poDate", poDate)
                    .getResultList();

            if (!rows.isEmpty()) {
                Object[] row = (Object[]) rows.get(0);

                result.put("found", true);
                result.put("price", row[0]);
                result.put("currency", row[1] != null ? row[1] : "IDR");
                result.put("uomId", uomId);
            } else {
                result.put("found", false);
                result.put("message", "Harga untuk UOM yang dipilih belum tersedia.");
            }

        } catch (Exception e) {
            result.put("found", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
	
	// ============================================================
// V28: DIRECT PO
// ============================================================

@GetMapping("/po/new-direct")
public String newDirectPo(HttpSession session, Model model) {
    SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
    if (user == null) return "redirect:/login";

    model.addAttribute("user", user);
    model.addAttribute("pageTitle", "Buat Direct PO");
    model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

    // Supplier list
    List<Map<String, Object>> suppliers = new ArrayList<>();
    for (Object[] r : (List<Object[]>) em.createNativeQuery(
            "SELECT id, code, name FROM md.supplier WHERE tenant_id = 1 ORDER BY name")
            .getResultList()) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r[0]);
        m.put("code", r[1]);
        m.put("name", r[2]);
        suppliers.add(m);
    }
    model.addAttribute("suppliers", suppliers);

    return "ui/purchasing/po_form_direct";
}

@PostMapping("/po/create-direct")
public String createDirectPo(@RequestParam Long supplierId,
                              @RequestParam(required = false) String docDate,
                              @RequestParam java.math.BigDecimal totalAmount,
                              @RequestParam(defaultValue = "IDR") String currency,
                              @RequestParam(defaultValue = "NO_TAX") String taxType,
                              @RequestParam(required = false) String remarks,
                              @RequestParam(required = false) Boolean isNewSupplier,
                              @RequestParam(required = false) String bypassReason,
                              HttpSession session, Model model,
                              RedirectAttributes ra) {
    SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
    if (user == null) return "redirect:/login";

    try {
        LocalDate dt = (docDate != null && !docDate.isBlank())
            ? LocalDate.parse(docDate) : LocalDate.now();

        var po = purchasingService.createDirectPo(
            dt, supplierId, totalAmount, remarks, currency, taxType,
            isNewSupplier, bypassReason, java.util.List.of()   // lines kosong dulu
        );
        ra.addFlashAttribute("success", "Direct PO berhasil dibuat: " + po.getDocNo());
        return "redirect:/ui/purchasing/po";
    } catch (IllegalStateException | IllegalArgumentException e) {
        model.addAttribute("error", e.getMessage());
        return newDirectPo(session, model);
    }
}

@PostMapping("/po/check-policy")
@ResponseBody
public Map<String, Object> checkPolicy(@RequestBody Map<String, Object> body,
                                        HttpSession session) {
    SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
    if (user == null) return Map.of("requiresPr", false, "error", "not logged in");

    try {
        java.math.BigDecimal total = body.get("totalAmount") != null
            ? new java.math.BigDecimal(body.get("totalAmount").toString())
            : java.math.BigDecimal.ZERO;
        boolean isNew = Boolean.TRUE.equals(body.get("isNewSupplier"));

        boolean requires = policyService.requiresPr(
            1L,
            new PurchasePolicyService.PurchaseContext(total, isNew, java.util.List.of())
        );
        return Map.of("requiresPr", requires);
    } catch (Exception e) {
        return Map.of("requiresPr", false, "error", e.getMessage());
    }
}

@PostMapping("/po/{id}/approve-bypass")
public String approveBypass(@PathVariable Long id,
                             HttpSession session,
                             RedirectAttributes ra) {
    SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
    if (user == null) return "redirect:/login";
    try {
        purchasingService.approveBypass(id, user);
        ra.addFlashAttribute("success", "Bypass disetujui.");
    } catch (Exception e) {
        ra.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/ui/purchasing/po";
}
}
