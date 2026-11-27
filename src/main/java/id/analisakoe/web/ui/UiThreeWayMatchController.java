package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/purchasing/three-way-match")
public class UiThreeWayMatchController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;
    private final ContextService context;

    public UiThreeWayMatchController(MenuService menuService, ContextService context) {
        this.menuService = menuService;
        this.context = context;
    }

    // ============================================================
    // LIST PO dengan status 3-way match
    // ============================================================
    @GetMapping
    public String list(@RequestParam(required = false) String statusFilter,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "3-Way Match Report");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("statusFilter", statusFilter);

        String sql = "SELECT po.id, po.doc_no, po.doc_date, s.name AS supplier_name, " +
                     "po.status AS po_status, " +
                     "COALESCE((SELECT SUM(pod.qty) FROM pur.purchase_order_dtl pod WHERE pod.header_id = po.id), 0) AS total_po, " +
                     "COALESCE((SELECT SUM(gd.qty_received) FROM pur.grn_dtl gd " +
                     "  JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
                     "  WHERE gh.po_id = po.id AND UPPER(TRIM(gh.status)) <> 'VOID'), 0) AS total_grn, " +
                     "COALESCE((SELECT SUM(pid.qty) FROM pur.purchase_invoice_dtl pid " +
                     "  JOIN pur.purchase_invoice_hdr pih ON pih.id = pid.header_id " +
                     "  JOIN pur.grn_hdr gh ON gh.id = pih.grn_id " +
                     "  WHERE gh.po_id = po.id AND UPPER(TRIM(pih.status)) <> 'VOID'), 0) AS total_inv, " +
                     "(SELECT COUNT(DISTINCT pod.uom_id) FROM pur.purchase_order_dtl pod WHERE pod.header_id = po.id) AS uom_count, " +
                     "(SELECT MIN(u2.code) FROM pur.purchase_order_dtl pod JOIN md.uom u2 ON u2.id = pod.uom_id WHERE pod.header_id = po.id) AS uom_code, " +
                     "(SELECT COUNT(DISTINCT gd.uom_id) FROM pur.grn_dtl gd JOIN pur.grn_hdr gh2 ON gh2.id = gd.header_id WHERE gh2.po_id = po.id AND UPPER(TRIM(gh2.status)) <> 'VOID') AS grn_uom_count, " +
                     "(SELECT MIN(u3.code) FROM pur.grn_dtl gd JOIN pur.grn_hdr gh2 ON gh2.id = gd.header_id JOIN md.uom u3 ON u3.id = gd.uom_id WHERE gh2.po_id = po.id AND UPPER(TRIM(gh2.status)) <> 'VOID') AS grn_uom_code " +
                     "FROM pur.purchase_order_hdr po " +
                     "LEFT JOIN md.supplier s ON s.id = po.supplier_id " +
                     "WHERE po.tenant_id = :tenantId AND po.company_id = :companyId " +
                     "  AND po.status NOT IN ('DRAFT', 'VOID') " +
                     "ORDER BY po.id DESC";

        List<Map<String, Object>> orders = new ArrayList<>();
        try {
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql)
                    .setParameter("tenantId", context.tenantId())
                    .setParameter("companyId", context.companyId())
                    .getResultList()) {
                Long poId = ((Number) row[0]).longValue();
                BigDecimal poQty = new BigDecimal(row[5].toString());
                BigDecimal grnQty = new BigDecimal(row[6].toString());
                BigDecimal invQty = new BigDecimal(row[7].toString());
                int uomCount = ((Number) row[8]).intValue();
                String uomCode = (uomCount == 1 && row[9] != null) ? row[9].toString() : null;
                int grnUomCount = ((Number) row[10]).intValue();
                String grnUomCode = (grnUomCount == 1 && row[11] != null) ? row[11].toString() : null;

                String matchStatus;
if (poQty.compareTo(BigDecimal.ZERO) == 0) {
    matchStatus = "NO_DATA";
} else if (invQty.compareTo(grnQty) > 0) {
    matchStatus = "OVER_INVOICE"; // Cek ini DULUAN (paling bahaya)
} else if (grnQty.compareTo(poQty) > 0) {
    matchStatus = "OVER_RECEIVE";
} else if (invQty.compareTo(grnQty) == 0 && grnQty.compareTo(poQty) == 0) {
    matchStatus = "MATCH";
} else {
    matchStatus = "PARTIAL";
}

                // Apply filter
                if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equals(matchStatus)) {
                    continue;
                }

                Map<String, Object> m = new HashMap<>();
                m.put("id", poId);
                m.put("docNo", row[1] != null ? row[1].toString() : "-");

                Object dateObj = row[2];
                LocalDate parsedDate = null;
                if (dateObj instanceof java.sql.Date) parsedDate = ((java.sql.Date) dateObj).toLocalDate();
                else if (dateObj instanceof LocalDate) parsedDate = (LocalDate) dateObj;
                m.put("docDate", parsedDate);

                m.put("supplierName", row[3] != null ? row[3].toString() : "-");
                m.put("poStatus", row[4] != null ? row[4].toString() : "-");
                m.put("totalPo", poQty);
                m.put("totalGrn", grnQty);
                m.put("totalInv", invQty);
                m.put("uomCode", uomCode);       // UOM PO (null jika item pakai campuran unit)
                m.put("grnUomCode", grnUomCode); // UOM GRN (null jika campuran unit)
                m.put("matchStatus", matchStatus);
                orders.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat 3-way match: " + e.getMessage());
        }
        model.addAttribute("orders", orders);
        return "ui/purchasing/three_way_match_list";
    }

    // ============================================================
    // DETAIL per PO (breakdown per item)
    // ============================================================
    @GetMapping("/{poId}")
    public String detail(@PathVariable Long poId, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "3-Way Match Detail");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("poId", poId);

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT po.doc_no, po.doc_date, s.name, po.status " +
                "FROM pur.purchase_order_hdr po LEFT JOIN md.supplier s ON s.id = po.supplier_id " +
                "WHERE po.id = :id").setParameter("id", poId).getSingleResult();
            model.addAttribute("poDocNo", hdr[0]);
            model.addAttribute("poDate", hdr[1]);
            model.addAttribute("supplierName", hdr[2]);
            model.addAttribute("poStatus", hdr[3]);

            String sql = "SELECT pod.item_id, i.code, i.name, pod.uom_id, u.code, " +
             "SUM(pod.qty) AS po_qty, MAX(pod.price) AS po_price, " +
             "COALESCE((SELECT SUM(gd.qty_received) FROM pur.grn_dtl gd " +
             "  JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
             "  WHERE gh.po_id = :poId AND gd.item_id = pod.item_id AND gd.uom_id = pod.uom_id AND UPPER(TRIM(gh.status)) <> 'VOID'), 0) AS grn_qty, " +
             "COALESCE((SELECT MIN(u2.code) FROM pur.grn_dtl gd " +
             "  JOIN pur.grn_hdr gh ON gh.id = gd.header_id JOIN md.uom u2 ON u2.id = gd.uom_id " +
             "  WHERE gh.po_id = :poId AND gd.item_id = pod.item_id AND gd.uom_id = pod.uom_id AND UPPER(TRIM(gh.status)) <> 'VOID'), '-') AS grn_uom, " +
             "COALESCE((SELECT SUM(pid.qty) FROM pur.purchase_invoice_dtl pid " +
             "  JOIN pur.purchase_invoice_hdr pih ON pih.id = pid.header_id " +
             "  JOIN pur.grn_hdr gh ON gh.id = pih.grn_id " +
             "  WHERE gh.po_id = :poId AND pid.item_id = pod.item_id " +
             "    AND COALESCE(pid.uom_id, (SELECT gd2.uom_id FROM pur.grn_dtl gd2 WHERE gd2.header_id = pih.grn_id AND gd2.item_id = pid.item_id LIMIT 1)) = pod.uom_id " +
             "    AND UPPER(TRIM(pih.status)) <> 'VOID'), 0) AS inv_qty, " +
             "COALESCE((SELECT SUM(pid.qty) FROM pur.purchase_invoice_dtl pid " +
             "  JOIN pur.purchase_invoice_hdr pih ON pih.id = pid.header_id " +
             "  JOIN pur.grn_hdr gh ON gh.id = pih.grn_id " +
             "  WHERE gh.po_id = :poId AND pid.item_id = pod.item_id " +
             "    AND COALESCE(pid.uom_id, (SELECT gd2.uom_id FROM pur.grn_dtl gd2 WHERE gd2.header_id = pih.grn_id AND gd2.item_id = pid.item_id LIMIT 1)) <> pod.uom_id " +
             "    AND UPPER(TRIM(pih.status)) <> 'VOID'), 0) AS inv_other_qty, " +
             "(SELECT MIN(u4.code) FROM pur.purchase_invoice_dtl pid " +
             "  JOIN md.uom u4 ON u4.id = pid.uom_id " +
             "  JOIN pur.purchase_invoice_hdr pih ON pih.id = pid.header_id " +
             "  JOIN pur.grn_hdr gh ON gh.id = pih.grn_id " +
             "  WHERE gh.po_id = :poId AND pid.item_id = pod.item_id " +
             "    AND COALESCE(pid.uom_id, (SELECT gd2.uom_id FROM pur.grn_dtl gd2 WHERE gd2.header_id = pih.grn_id AND gd2.item_id = pid.item_id LIMIT 1)) <> pod.uom_id " +
             "    AND UPPER(TRIM(pih.status)) <> 'VOID') AS inv_other_uom, " +
             "COALESCE((SELECT SUM(gd.qty_received) FROM pur.grn_dtl gd " +
             "  JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
             "  WHERE gh.po_id = :poId AND gd.item_id = pod.item_id AND gd.uom_id <> pod.uom_id AND UPPER(TRIM(gh.status)) <> 'VOID'), 0) AS grn_other_qty, " +
             "(SELECT MIN(u3.code) FROM pur.grn_dtl gd " +
             "  JOIN pur.grn_hdr gh ON gh.id = gd.header_id JOIN md.uom u3 ON u3.id = gd.uom_id " +
             "  WHERE gh.po_id = :poId AND gd.item_id = pod.item_id AND gd.uom_id <> pod.uom_id AND UPPER(TRIM(gh.status)) <> 'VOID') AS grn_other_uom " +
             "FROM pur.purchase_order_dtl pod " +
             "JOIN md.item i ON i.id = pod.item_id " +
             "JOIN md.uom u ON u.id = pod.uom_id " +
             "WHERE pod.header_id = :poId " +
             "GROUP BY pod.item_id, i.code, i.name, pod.uom_id, u.code " +
             "ORDER BY pod.item_id, pod.uom_id";

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql)
                    .setParameter("poId", poId).getResultList()) {
                BigDecimal poQty = new BigDecimal(row[5].toString());
                BigDecimal grnQty = new BigDecimal(row[7].toString());
                BigDecimal invQty = new BigDecimal(row[9].toString());
                BigDecimal invOtherQty = new BigDecimal(row[10].toString());
                String invOtherUom = row[11] != null ? row[11].toString() : null;
                BigDecimal grnOtherQty = new BigDecimal(row[12].toString());
                String grnOtherUom = row[13] != null ? row[13].toString() : null;

                String itemStatus;
                if (invQty.compareTo(grnQty) == 0 && grnQty.compareTo(poQty) == 0) {
                    itemStatus = "MATCH";
                } else if (grnQty.compareTo(poQty) > 0) {
                    itemStatus = "OVER_RECEIVE";
                } else if (invQty.compareTo(grnQty) > 0) {
                    itemStatus = "OVER_INVOICE";
                } else {
                    itemStatus = "PARTIAL";
                }

                Map<String, Object> m = new HashMap<>();
                m.put("itemCode", row[1]);
                m.put("itemName", row[2]);
                m.put("uomCode", row[4] != null ? row[4].toString() : "-");
                m.put("poQty", poQty);
                m.put("grnQty", grnQty);
                m.put("grnUom", row[8] != null ? row[8].toString() : "-");
                m.put("invQty", invQty);
                m.put("invOtherQty", invOtherQty);
                m.put("invOtherUom", invOtherUom);
                m.put("grnOtherQty", grnOtherQty);
                m.put("grnOtherUom", grnOtherUom);
                m.put("poPrice", row[6]);
                m.put("itemStatus", itemStatus);
                items.add(m);
            }
            model.addAttribute("items", items);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat detail: " + e.getMessage());
        }
        return "ui/purchasing/three_way_match_detail";
    }
}