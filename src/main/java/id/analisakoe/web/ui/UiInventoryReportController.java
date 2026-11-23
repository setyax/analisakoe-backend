package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
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
@RequestMapping("/ui/inventory/reports/movement")
public class UiInventoryReportController {
    private final MenuService menuService;
    @PersistenceContext
    private EntityManager em;

    public UiInventoryReportController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(required = false) Long warehouseId,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user == null || user.getTenantId() == null || user.getActiveCompanyId() == null) {
            throw new IllegalStateException("Sesi login, tenant, atau company aktif tidak ditemukan.");
        }
        Long tenantId = user.getTenantId();
        Long companyId = user.getActiveCompanyId();
        LocalDate start = startDate == null || startDate.isBlank()
                ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        if (end.isBefore(start)) throw new IllegalArgumentException("Tanggal akhir tidak boleh sebelum tanggal awal.");

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Inventory Movement Report");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());
        model.addAttribute("selectedWh", warehouseId);

        List<Object[]> whRows = em.createNativeQuery(
            "SELECT id, code, name FROM md.warehouse " +
            "WHERE tenant_id=:tenantId AND company_id=:companyId ORDER BY code"
        ).setParameter("tenantId", tenantId).setParameter("companyId", companyId).getResultList();
        List<Map<String, Object>> warehouses = new ArrayList<>();
        for (Object[] r : whRows) {
            Map<String, Object> w = new HashMap<>();
            w.put("id", ((Number) r[0]).longValue());
            w.put("code", r[1]);
            w.put("name", r[2]);
            warehouses.add(w);
        }
        model.addAttribute("warehouses", warehouses);

        StringBuilder sql = new StringBuilder(InventoryMovementQuery.CTE);
        sql.append("""
            SELECT i.id, i.code, i.name, COALESCE(u.code, '-') AS uom_code,
                   COALESCE(SUM(CASE WHEN ms.movement_date < :startDate THEN
                       CASE WHEN ms.direction='IN' THEN ms.qty WHEN ms.direction='OUT' THEN -ms.qty ELSE 0 END
                       ELSE 0 END), 0) AS opening,
                   COALESCE(SUM(CASE WHEN ms.movement_date BETWEEN :startDate AND :endDate
                       AND ms.direction='IN' THEN ms.qty ELSE 0 END), 0) AS in_qty,
                   COALESCE(SUM(CASE WHEN ms.movement_date BETWEEN :startDate AND :endDate
                       AND ms.direction='OUT' THEN ms.qty ELSE 0 END), 0) AS out_qty
            FROM movement_source ms
            JOIN md.item i ON i.id = ms.item_id
            JOIN md.warehouse w ON w.id = ms.warehouse_id
            LEFT JOIN md.uom u ON u.id = i.base_uom_id
            WHERE ms.tenant_id=:tenantId
              AND w.tenant_id=:tenantId
              AND w.company_id=:companyId
              AND (ms.source_company_id IS NULL OR ms.source_company_id=:companyId)
            """);
        if (warehouseId != null) sql.append(" AND ms.warehouse_id=:warehouseId ");
        sql.append(" GROUP BY i.id, i.code, i.name, u.code ORDER BY i.code, i.name");

        try {
            var query = em.createNativeQuery(sql.toString())
                    .setParameter("tenantId", tenantId)
                    .setParameter("companyId", companyId)
                    .setParameter("startDate", start)
                    .setParameter("endDate", end);
            if (warehouseId != null) query.setParameter("warehouseId", warehouseId);

            List<Object[]> results = query.getResultList();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object[] r : results) {
                BigDecimal opening = bd(r[4]);
                BigDecimal inQty = bd(r[5]);
                BigDecimal outQty = bd(r[6]);
                if (opening.signum() == 0 && inQty.signum() == 0 && outQty.signum() == 0) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("itemId", ((Number) r[0]).longValue());
                row.put("itemCode", r[1]);
                row.put("itemName", r[2]);
                row.put("uomCode", r[3] == null ? "-" : r[3].toString());
                row.put("opening", opening);
                row.put("inQty", inQty);
                row.put("outQty", outQty);
                row.put("closing", opening.add(inQty).subtract(outQty));
                rows.add(row);
            }
            model.addAttribute("movements", rows);
        } catch (Exception e) {
            model.addAttribute("movements", List.of());
            model.addAttribute("error", "Gagal memuat Movement Report: " + rootMessage(e));
        }
        return "ui/inventory/movement_report";
    }

    private static BigDecimal bd(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? e.toString() : t.getMessage();
    }
}