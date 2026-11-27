package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.service.StockValuationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/inventory/cogs")
public class UiCogsReportController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;
    private final StockValuationService stockValuationService;

    public UiCogsReportController(MenuService menuService, StockValuationService stockValuationService) {
        this.menuService = menuService;
        this.stockValuationService = stockValuationService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long itemId,
            HttpSession session, Model model) {

        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "COGS / HPP Report");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate start = (startDate != null && !startDate.isEmpty())
            ? LocalDate.parse(startDate)
            : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isEmpty())
            ? LocalDate.parse(endDate)
            : LocalDate.now();

        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());
        model.addAttribute("itemIdFilter", itemId);

        // ============================================================
        // ITEMS UNTUK DROPDOWN
        // ============================================================
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            List<Object[]> rows = em.createNativeQuery(
                "SELECT id, code, name FROM md.item WHERE tenant_id = 1 AND deleted_at IS NULL ORDER BY code")
                .getResultList();
            for (Object[] row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("code", row[1] != null ? row[1].toString() : "");
                m.put("name", row[2] != null ? row[2].toString() : "");
                items.add(m);
            }
        } catch (Exception e) {
            System.err.println("[COGS] Load items error: " + e.getMessage());
        }
        model.addAttribute("items", items);

        // ============================================================
        // COGS LIST — dengan konversi tipe aman
        // ============================================================
        List<Map<String, Object>> cogsList = new ArrayList<>();
        BigDecimal grandTotalCogs = BigDecimal.ZERO;
        BigDecimal grandTotalQty = BigDecimal.ZERO;
        BigDecimal grandTotalQtyBase = BigDecimal.ZERO;
        int rowCount = 0;

        try {
            StringBuilder sql = new StringBuilder(
                "SELECT hdr.id, hdr.doc_no, hdr.doc_date, " +
                "  d.item_id, i.code, i.name, " +
                "  d.qty, d.cogs_unit, d.cogs_total, " +
                "  COALESCE(u.code, '-') AS uom_code, " +
                "  d.uom_id, COALESCE(bu.code, '-') AS base_uom_code " +
                "FROM sal.delivery_order_dtl d " +
                "JOIN sal.delivery_order_hdr hdr ON hdr.id = d.header_id " +
                "JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "LEFT JOIN md.uom bu ON bu.id = i.base_uom_id " +
                "WHERE hdr.tenant_id = 1 AND d.gi_posted_qty > 0 " +
                "  AND UPPER(TRIM(hdr.status)) IN ('CONFIRMED','READY_TO_SHIP','PARTIALLY_SHIPPED','SHIPPED','DELIVERED','CLOSED') " +
                "  AND hdr.doc_date BETWEEN :startDate AND :endDate ");

            if (itemId != null) {
                sql.append(" AND d.item_id = :itemId ");
            }
            sql.append(" ORDER BY hdr.doc_date DESC, hdr.id DESC, d.id ");

            var query = em.createNativeQuery(sql.toString())
                .setParameter("startDate", java.sql.Date.valueOf(start))
                .setParameter("endDate", java.sql.Date.valueOf(end));
            if (itemId != null) {
                query.setParameter("itemId", itemId);
            }

            List<Object[]> rows = query.getResultList();
            System.out.println("[COGS] Found " + rows.size() + " rows for " + start + " to " + end);

            for (Object[] row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("doId", row[0]);
                m.put("doNo", row[1] != null ? row[1].toString() : "-");

                // ====== KONVERSI TANGGAL AMAN (LocalDate atau java.sql.Date) ======
                Object dateObj = row[2];
                LocalDate doDate = null;
                if (dateObj instanceof java.sql.Date) {
                    doDate = ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof java.time.LocalDate) {
                    doDate = (java.time.LocalDate) dateObj;
                } else if (dateObj instanceof java.sql.Timestamp) {
                    doDate = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                }
                m.put("doDate", doDate);

                m.put("itemId", row[3]);
                m.put("itemCode", row[4] != null ? row[4].toString() : "");
                m.put("itemName", row[5] != null ? row[5].toString() : "");
                m.put("qty", row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO);
                m.put("cogsUnit", row[7] != null ? new BigDecimal(row[7].toString()) : BigDecimal.ZERO);
                m.put("cogsTotal", row[8] != null ? new BigDecimal(row[8].toString()) : BigDecimal.ZERO);
                m.put("uomCode", row[9] != null ? row[9].toString() : "-");

                // ====== KONVERSI QTY KE SATUAN DASAR (BASE UOM) ======
                BigDecimal qtyLine = (BigDecimal) m.get("qty");
                Long uomId = row[10] != null ? ((Number) row[10]).longValue() : null;
                BigDecimal qtyBase = stockValuationService
                    .convertLineToBase((Long) m.get("itemId"), uomId, qtyLine, BigDecimal.ZERO)[0];
                m.put("qtyBase", qtyBase);
                m.put("baseUomCode", row[11] != null ? row[11].toString() : "-");
                cogsList.add(m);

                grandTotalCogs = grandTotalCogs.add((BigDecimal) m.get("cogsTotal"));
                grandTotalQty = grandTotalQty.add(qtyLine);
                grandTotalQtyBase = grandTotalQtyBase.add(qtyBase);
                rowCount++;
            }
        } catch (Exception e) {
            System.err.println("[COGS] ERROR loading COGS: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Gagal memuat COGS: " + e.getMessage());
        }

        model.addAttribute("cogsList", cogsList);
        model.addAttribute("grandTotalCogs", grandTotalCogs);
        model.addAttribute("grandTotalQty", grandTotalQty);
        model.addAttribute("grandTotalQtyBase", grandTotalQtyBase);
        model.addAttribute("rowCount", rowCount);

        return "ui/inventory/cogs_report";
    }
}