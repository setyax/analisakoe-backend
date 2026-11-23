package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/inventory/reorder-point")
public class UiReorderPointController {
    private final MenuService menuService;
    private final EntityManager em;

    public UiReorderPointController(MenuService menuService, EntityManager em) {
        this.menuService = menuService; this.em = em;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Reorder Point");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        // Cari item yang stoknya <= reorder_point
        String sql = "SELECT i.id, i.code, i.name, u.code as uom, " +
                     "COALESCE(i.reorder_point, 0) as reorder_point, COALESCE(i.min_stock, 0) as min_stock, " +
                     "COALESCE((SELECT SUM(sb.qty) FROM inv.stock_balance sb WHERE sb.item_id = i.id), 0) as current_stock " +
                     "FROM md.item i LEFT JOIN md.uom u ON i.base_uom_id = u.id " +
                     "WHERE i.deleted_at IS NULL AND COALESCE(i.reorder_point, 0) > 0 " +
                     "AND COALESCE((SELECT SUM(sb.qty) FROM inv.stock_balance sb WHERE sb.item_id = i.id), 0) <= COALESCE(i.reorder_point, 0) " +
                     "ORDER BY current_stock ASC";

        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("code", row[1]);
                map.put("name", row[2]);
                map.put("uom", row[3] != null ? row[3] : "-");
                map.put("reorderPoint", row[4]);
                map.put("minStock", row[5]);
                map.put("currentStock", row[6]);
                // Berapa yang perlu dipesan (sampai min_stock atau 2x reorder_point)
                BigDecimal cp = (BigDecimal) row[4];
                BigDecimal cs = (BigDecimal) row[6];
                map.put("suggestedOrder", cp.subtract(cs));
                list.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("items", list);
        return "ui/inventory/reorder_list";
    }

    @GetMapping("/setup")
    public String setup(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Setup Reorder Point");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT id, code, name, COALESCE(reorder_point, 0), COALESCE(min_stock, 0) FROM md.item WHERE deleted_at IS NULL ORDER BY code";
        List<Map<String, Object>> items = new ArrayList<>();
        List<Object[]> results = em.createNativeQuery(sql).getResultList();
        for (Object[] row : results) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", row[0]);
            map.put("code", row[1]);
            map.put("name", row[2]);
            map.put("reorderPoint", row[3]);
            map.put("minStock", row[4]);
            items.add(map);
        }
        model.addAttribute("items", items);
        return "ui/inventory/reorder_setup";
    }

    @PostMapping("/setup")
    @org.springframework.transaction.annotation.Transactional
    public String saveSetup(@RequestParam("itemId") ArrayList<Long> itemIds,
                            @RequestParam("reorderPoint") ArrayList<BigDecimal> rops,
                            @RequestParam("minStock") ArrayList<BigDecimal> mins,
                            RedirectAttributes ra) {
        try {
            for (int i = 0; i < itemIds.size(); i++) {
                em.createNativeQuery("UPDATE md.item SET reorder_point = :rop, min_stock = :min WHERE id = :id")
                    .setParameter("rop", rops.get(i))
                    .setParameter("min", mins.get(i))
                    .setParameter("id", itemIds.get(i)).executeUpdate();
            }
            ra.addFlashAttribute("success", "Setup Reorder Point berhasil disimpan.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/inventory/reorder-point";
    }

    /** Reset (hapus) aturan reorder point satu item. */
    @PostMapping("/{itemId}/clear")
    public String clearItem(@PathVariable Long itemId, RedirectAttributes ra) {
        try {
            int updated = em.createNativeQuery(
                    "UPDATE md.item SET reorder_point = 0, min_stock = 0 WHERE id = :id")
                    .setParameter("id", itemId).executeUpdate();
            if (updated == 0) {
                ra.addFlashAttribute("error", "Item tidak ditemukan.");
            } else {
                ra.addFlashAttribute("success", "Reorder Point item berhasil di-reset ke 0.");
            }
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/inventory/reorder-point/setup";
    }
}