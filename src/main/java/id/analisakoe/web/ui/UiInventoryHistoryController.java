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
@RequestMapping("/ui/inventory/stock-balance")
public class UiInventoryHistoryController {
    private final MenuService menuService;
    @PersistenceContext
    private EntityManager em;

    public UiInventoryHistoryController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/history")
    public String history(@RequestParam Long itemId,
                          @RequestParam(required = false) Long warehouseId,
                          @RequestParam(required = false) String startDate,
                          @RequestParam(required = false) String endDate,
                          HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user == null || user.getTenantId() == null || user.getActiveCompanyId() == null) {
            throw new IllegalStateException("Sesi login, tenant, atau company aktif tidak ditemukan.");
        }
        Long tenantId = user.getTenantId();
        Long companyId = user.getActiveCompanyId();
        LocalDate start = startDate == null || startDate.isBlank() ? null : LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isBlank() ? null : LocalDate.parse(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("Tanggal akhir tidak boleh sebelum tanggal awal.");
        }

        List<?> itemRows = em.createNativeQuery(
            "SELECT i.code, i.name, COALESCE(u.code, '-') FROM md.item i " +
            "LEFT JOIN md.uom u ON u.id=i.base_uom_id WHERE i.id=:itemId"
        ).setParameter("itemId", itemId).getResultList();
        if (itemRows.isEmpty()) throw new IllegalArgumentException("Barang tidak ditemukan.");
        Object[] item = (Object[]) itemRows.get(0);

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

        StringBuilder sql = new StringBuilder(InventoryMovementQuery.CTE);
        sql.append("""
            SELECT ms.source_row_id, ms.movement_date, ms.reference_type, ms.reference_id,
                   ms.reference_no, ms.direction, ms.qty, ms.unit_cost, ms.total_cost,
                   ms.balance_qty, ms.remarks, w.code, w.name, ms.source_kind
            FROM movement_source ms
            JOIN md.warehouse w ON w.id=ms.warehouse_id
            WHERE ms.tenant_id=:tenantId
              AND w.tenant_id=:tenantId
              AND w.company_id=:companyId
              AND (ms.source_company_id IS NULL OR ms.source_company_id=:companyId)
              AND ms.item_id=:itemId
            """);
        if (warehouseId != null) sql.append(" AND ms.warehouse_id=:warehouseId ");
        if (start != null) sql.append(" AND ms.movement_date>=:startDate ");
        if (end != null) sql.append(" AND ms.movement_date<=:endDate ");
        sql.append(" ORDER BY ms.movement_date DESC, ms.source_row_id DESC");

        var query = em.createNativeQuery(sql.toString())
                .setParameter("tenantId", tenantId)
                .setParameter("companyId", companyId)
                .setParameter("itemId", itemId);
        if (warehouseId != null) query.setParameter("warehouseId", warehouseId);
        if (start != null) query.setParameter("startDate", start);
        if (end != null) query.setParameter("endDate", end);

        List<Object[]> dbRows = query.getResultList();
        List<Map<String, Object>> history = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Object[] r : dbRows) {
            String direction = r[5] == null ? "" : r[5].toString().toUpperCase();
            BigDecimal qty = bd(r[6]);
            if ("IN".equals(direction)) totalIn = totalIn.add(qty);
            if ("OUT".equals(direction)) totalOut = totalOut.add(qty);
            Map<String, Object> row = new HashMap<>();
            row.put("movementId", r[0]);
            row.put("movementDate", r[1]);
            row.put("referenceType", r[2]);
            row.put("referenceId", r[3]);
            row.put("referenceNo", r[4] == null ? "-" : r[4]);
            row.put("direction", direction);
            row.put("qty", qty);
            row.put("unitCost", bdNullable(r[7]));
            row.put("totalCost", bdNullable(r[8]));
            row.put("recordedBalance", bdNullable(r[9]));
            row.put("remarks", r[10] == null ? "" : r[10]);
            row.put("warehouseCode", r[11]);
            row.put("warehouseName", r[12]);
            row.put("sourceKind", r[13]);
            history.add(row);
        }

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Historical Transaksi Inventory");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("itemId", itemId);
        model.addAttribute("itemCode", item[0]);
        model.addAttribute("itemName", item[1]);
        model.addAttribute("uomCode", item[2]);
        model.addAttribute("warehouseId", warehouseId);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("startDate", start == null ? "" : start.toString());
        model.addAttribute("endDate", end == null ? "" : end.toString());
        model.addAttribute("history", history);
        model.addAttribute("totalIn", totalIn);
        model.addAttribute("totalOut", totalOut);
        model.addAttribute("netMovement", totalIn.subtract(totalOut));
        return "ui/inventory/stock_balance_history";
    }

    private static BigDecimal bd(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }
    private static BigDecimal bdNullable(Object o) {
        return o == null ? null : new BigDecimal(o.toString());
    }
}