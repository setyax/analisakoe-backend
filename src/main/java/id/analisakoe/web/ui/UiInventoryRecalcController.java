package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.service.StockValuationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
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
@RequestMapping("/ui/inventory/recalculate")
public class UiInventoryRecalcController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private StockValuationService stockService;

    private final MenuService menuService;

    public UiInventoryRecalcController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String form(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Recalculate Inventory");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("currentMonth", LocalDate.now().toString().substring(0, 7));

        // Tampilkan 12 snapshot terbaru (jika ada)
        String sql = "SELECT s.period, s.item_id, i.code, i.name, " +
                     "s.begin_qty, s.begin_avg_cost, s.begin_value, " +
                     "s.in_qty, s.in_value, s.out_qty, s.out_value, " +
                     "s.end_qty, s.end_avg_cost, s.end_value, s.cogs, " +
                     "TO_CHAR(s.recalculated_at, 'DD-MM-YYYY HH24:MI') AS recalc_at " +
                     "FROM inv.stock_period_snapshot s " +
                     "JOIN md.item i ON i.id = s.item_id " +
                     "WHERE s.tenant_id = 1 " +
                     "ORDER BY s.period DESC, i.code " +
                     "LIMIT 50";
        List<Map<String, Object>> snapshots = new ArrayList<>();
        try {
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("period", row[0]);
                m.put("itemCode", row[2]);
                m.put("itemName", row[3]);
                m.put("beginQty", row[4]);
                m.put("beginAvgCost", row[5]);
                m.put("beginValue", row[6]);
                m.put("inQty", row[7]);
                m.put("inValue", row[8]);
                m.put("outQty", row[9]);
                m.put("outValue", row[10]);
                m.put("endQty", row[11]);
                m.put("endAvgCost", row[12]);
                m.put("endValue", row[13]);
                m.put("cogs", row[14]);
                m.put("recalcAt", row[15]);
                snapshots.add(m);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("snapshots", snapshots);
        return "ui/inventory/recalculate_form";
    }

    @PostMapping("/run")
    public String runRecalc(@RequestParam String period,
                            @RequestParam(required = false) Long warehouseId,
                            RedirectAttributes ra) {
        try {
            LocalDate start = LocalDate.parse(period + "-01");
            LocalDate end   = start.withDayOfMonth(start.lengthOfMonth());

            Map<String, Object> result = stockService.recalculatePeriod(start, end, warehouseId);

            ra.addFlashAttribute("success",
                "Recalculate berhasil untuk periode " + result.get("period") +
                ". Jumlah item diproses: " + result.get("items_processed"));
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal recalculate: " + e.getMessage());
        }
        return "redirect:/ui/inventory/recalculate";
    }
}