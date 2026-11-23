package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

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
@RequestMapping("/ui/finance/reports/income-statement")
public class UiIncomeStatementController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiIncomeStatementController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Income Statement");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);

        List<Map<String, Object>> pendapatan = new ArrayList<>();
        List<Map<String, Object>> beban = new ArrayList<>();
        BigDecimal totalPendapatan = BigDecimal.ZERO;
        BigDecimal totalBeban = BigDecimal.ZERO;

        try {
            // Ambil jurnal POSTED di periode ini
            String sql = "SELECT a.code, a.name, SUM(d.debit) AS debit, SUM(d.credit) AS credit " +
                         "FROM fin.chart_of_account a " +
                         "JOIN fin.journal_dtl d ON d.account_id = a.id " +
                         "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                         "WHERE h.status = 'POSTED' AND h.doc_date BETWEEN :start AND :end " +
                         "AND (a.code LIKE '4%' OR a.code LIKE '5%' OR a.code LIKE '6%' OR a.code LIKE '8%') " +
                         "GROUP BY a.code, a.name ORDER BY a.code";

            @SuppressWarnings("unchecked")
            List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("start", start).setParameter("end", end).getResultList();

            for (Object[] row : results) {
                String code = (String) row[0];
                String name = (String) row[1];
                BigDecimal debit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                BigDecimal credit = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

                Map<String, Object> m = new HashMap<>();
                m.put("code", code);
                m.put("name", name);

                if (code.startsWith("4") || code.startsWith("8")) {
                    // Pendapatan (Normal Kredit, jadi Pendapatan = Credit - Debit)
                    BigDecimal saldo = credit.subtract(debit);
                    if (saldo.compareTo(BigDecimal.ZERO) != 0) {
                        m.put("amount", saldo);
                        pendapatan.add(m);
                        totalPendapatan = totalPendapatan.add(saldo);
                    }
                } else if (code.startsWith("5") || code.startsWith("6")) {
                    // Beban (Normal Debit, jadi Beban = Debit - Credit)
                    BigDecimal saldo = debit.subtract(credit);
                    if (saldo.compareTo(BigDecimal.ZERO) != 0) {
                        m.put("amount", saldo);
                        beban.add(m);
                        totalBeban = totalBeban.add(saldo);
                    }
                }
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat: " + e.getMessage());
        }

        BigDecimal netProfit = totalPendapatan.subtract(totalBeban);

        model.addAttribute("pendapatan", pendapatan);
        model.addAttribute("totalPendapatan", totalPendapatan);
        model.addAttribute("beban", beban);
        model.addAttribute("totalBeban", totalBeban);
        model.addAttribute("netProfit", netProfit);

        return "ui/finance/income_statement";
    }
}