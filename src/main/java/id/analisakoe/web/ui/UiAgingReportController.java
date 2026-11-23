package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/ui/finance/reports")
public class UiAgingReportController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiAgingReportController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/aging")
    public String agingReport(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "AR Aging Report");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        try {
            // Query SQL untuk menghitung umur piutang secara otomatis
            String sql = 
                "SELECT " +
                "    c.name AS customer_name, " +
                "    h.doc_no AS invoice_no, " +
                "    h.doc_date AS invoice_date, " +
                "    (h.total_amount - COALESCE(h.paid_amount, 0)) AS outstanding, " +
                "    (CURRENT_DATE - h.doc_date) AS age_days, " +
                "    CASE " +
                "        WHEN (CURRENT_DATE - h.doc_date) <= 30 THEN '1-30 Hari' " +
                "        WHEN (CURRENT_DATE - h.doc_date) <= 60 THEN '31-60 Hari' " +
                "        WHEN (CURRENT_DATE - h.doc_date) <= 90 THEN '61-90 Hari' " +
                "        ELSE '> 90 Hari' " +
                "    END AS category " +
                "FROM sal.sales_invoice_hdr h " +
                "JOIN md.customer c ON h.customer_id = c.id " +
                "WHERE h.status = 'POSTED' " +
                "  AND (h.total_amount - COALESCE(h.paid_amount, 0)) > 0 " +
                "  AND h.company_id = :compId " +
                "ORDER BY age_days DESC";

            List<Object[]> agingData = em.createNativeQuery(sql)
                    .setParameter("compId", compId)
                    .getResultList();

            model.addAttribute("agingData", agingData);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat AR Aging: " + e.getMessage());
            e.printStackTrace();
        }

        return "ui/finance/aging_report";
    }
}