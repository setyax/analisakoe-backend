package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stock Balance per item dan gudang, dibatasi tenant dan company aktif. */
@Controller
@RequestMapping("/ui/inventory/stock-balance")
public class UiStockBalanceController {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final MenuService menuService;
    @PersistenceContext
    private EntityManager em;

    public UiStockBalanceController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long warehouseId,
                       @RequestParam(defaultValue = "false") boolean nonZero,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user == null || user.getTenantId() == null || user.getActiveCompanyId() == null) {
            throw new IllegalStateException("Sesi login, tenant, atau company aktif tidak ditemukan.");
        }
        Long tenantId = user.getTenantId();
        Long compId = user.getActiveCompanyId();

        if (size < 10) size = 10;
        if (size > 200) size = 200;
        if (page < 0) page = 0;
        String keyword = (q == null || q.trim().isEmpty()) ? null : q.trim();

        StringBuilder from = new StringBuilder(
            "FROM inv.stock_balance sb " +
            "JOIN md.item i ON i.id = sb.item_id " +
            "JOIN md.warehouse w ON w.id = sb.warehouse_id " +
            "LEFT JOIN md.uom u ON u.id = i.base_uom_id " +
            "WHERE sb.tenant_id = :tenantId AND w.tenant_id = :tenantId AND w.company_id = :compId "
        );
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("compId", compId);

        if (warehouseId != null) {
            from.append("AND sb.warehouse_id = :warehouseId ");
            params.put("warehouseId", warehouseId);
        }
        if (keyword != null) {
            from.append("AND (i.code ILIKE :kw OR i.name ILIKE :kw) ");
            params.put("kw", "%" + keyword + "%");
        }
        if (nonZero) from.append("AND sb.qty <> 0 ");

        Query countQuery = em.createNativeQuery("SELECT COUNT(*), COALESCE(SUM(sb.total_value), 0) " + from);
        params.forEach(countQuery::setParameter);
        Object[] totals = (Object[]) countQuery.getSingleResult();
        long totalRows = ((Number) totals[0]).longValue();
        BigDecimal totalValue = bd(totals[1]);

        int totalPages = (int) Math.max(1, (totalRows + size - 1) / size);
        if (page > totalPages - 1) page = totalPages - 1;

        Query dataQuery = em.createNativeQuery(
            "SELECT i.id, w.id, i.code, i.name, w.code, w.name, sb.qty, sb.qty_base, u.code, " +
            "sb.avg_cost, sb.total_value, sb.last_updated " + from +
            "ORDER BY i.code, w.code LIMIT :limit OFFSET :offset"
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setParameter("limit", size);
        dataQuery.setParameter("offset", page * size);

        List<Object[]> dbRows = dataQuery.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : dbRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", ((Number) r[0]).longValue());
            m.put("warehouseId", ((Number) r[1]).longValue());
            m.put("itemCode", r[2]);
            m.put("itemName", r[3]);
            m.put("warehouseCode", r[4]);
            m.put("warehouseName", r[5]);
            m.put("qty", bd(r[6]));
            m.put("qtyBase", bd(r[7]));
            m.put("uom", r[8] == null ? "-" : r[8].toString());
            m.put("avgCost", bd(r[9]));
            m.put("totalValue", bd(r[10]));
            m.put("lastUpdated", formatTs(r[11]));
            rows.add(m);
        }

        List<Object[]> whRows = em.createNativeQuery(
            "SELECT id, code, name FROM md.warehouse " +
            "WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY code"
        ).setParameter("tenantId", tenantId).setParameter("compId", compId).getResultList();
        List<Map<String, Object>> warehouses = new ArrayList<>();
        for (Object[] r : whRows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("code", r[1]);
            m.put("name", r[2]);
            warehouses.add(m);
        }

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Stock Balance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("rows", rows);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("q", keyword);
        model.addAttribute("warehouseId", warehouseId);
        model.addAttribute("nonZero", nonZero);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalRows", totalRows);
        model.addAttribute("totalValue", totalValue);
        return "ui/inventory/stock_balance";
    }

    private static BigDecimal bd(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private static String formatTs(Object o) {
        if (o == null) return "-";
        try {
            if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().format(TS_FORMAT);
            if (o instanceof java.time.OffsetDateTime odt) return odt.format(TS_FORMAT);
            if (o instanceof java.time.Instant instant)
                return instant.atZone(java.time.ZoneId.systemDefault()).format(TS_FORMAT);
        } catch (Exception ignored) { }
        return o.toString();
    }
}