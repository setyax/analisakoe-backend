package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/finance/reports/summary-balance")
public class UiSummaryBalanceController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiSummaryBalanceController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Summary Balance (Trial Balance)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        // Default: awal bulan ini s.d hari ini
        LocalDate start = (startDate != null && !startDate.isEmpty()) 
            ? LocalDate.parse(startDate) 
            : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isEmpty()) 
            ? LocalDate.parse(endDate) 
            : LocalDate.now();

        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal grandDebit = BigDecimal.ZERO;
        BigDecimal grandCredit = BigDecimal.ZERO;

        try {
            // Query: Group per akun COA, hanya jurnal POSTED
            String sql = "SELECT a.code, a.name, " +
                         "COALESCE(SUM(d.debit), 0) AS total_debit, " +
                         "COALESCE(SUM(d.credit), 0) AS total_credit " +
                         "FROM fin.chart_of_account a " +
                         "LEFT JOIN fin.journal_dtl d ON d.account_id = a.id " +
                         "LEFT JOIN fin.journal_hdr h ON h.id = d.header_id " +
                         "  AND h.status = 'POSTED' " +
                         "  AND h.doc_date BETWEEN :startDate AND :endDate " +
                         "GROUP BY a.id, a.code, a.name " +
                         "HAVING COALESCE(SUM(d.debit), 0) > 0 OR COALESCE(SUM(d.credit), 0) > 0 " +
                         "ORDER BY a.code";

            @SuppressWarnings("unchecked")
            List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .getResultList();

            for (Object[] row : results) {
                String code = (String) row[0];
                String name = (String) row[1];
                BigDecimal debit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                BigDecimal credit = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
                BigDecimal balance = debit.subtract(credit);

                Map<String, Object> m = new HashMap<>();
                m.put("code", code);
                m.put("name", name);
                m.put("debit", debit);
                m.put("credit", credit);
                m.put("balance", balance);
                m.put("balanceType", balance.compareTo(BigDecimal.ZERO) >= 0 ? "D" : "K");

                rows.add(m);
                grandDebit = grandDebit.add(debit);
                grandCredit = grandCredit.add(credit);
            }

            model.addAttribute("rows", rows);
            model.addAttribute("grandDebit", grandDebit);
            model.addAttribute("grandCredit", grandCredit);
            model.addAttribute("grandBalance", grandDebit.subtract(grandCredit));
            model.addAttribute("isBalanced", grandDebit.compareTo(grandCredit) == 0);
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data: " + e.getMessage());
        }

        return "ui/finance/summary_balance";
    }
}