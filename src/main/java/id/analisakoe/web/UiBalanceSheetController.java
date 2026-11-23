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
@RequestMapping("/ui/finance/reports/balance-sheet")
public class UiBalanceSheetController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiBalanceSheetController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) String endDate,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Balance Sheet");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        model.addAttribute("endDate", end);

        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liabilities = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO, totalLiabilities = BigDecimal.ZERO, totalEquity = BigDecimal.ZERO;
        
        // Laba Rugi Berjalan (Current Year Earnings)
        BigDecimal currentEarnings = BigDecimal.ZERO;

        try {
            String sql = "SELECT a.code, a.name, SUM(d.debit) AS debit, SUM(d.credit) AS credit " +
                         "FROM fin.chart_of_account a " +
                         "JOIN fin.journal_dtl d ON d.account_id = a.id " +
                         "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                         "WHERE h.status = 'POSTED' AND h.doc_date <= :end " +
                         "GROUP BY a.code, a.name ORDER BY a.code";

            @SuppressWarnings("unchecked")
            List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("end", end).getResultList();

            for (Object[] row : results) {
                String code = (String) row[0];
                String name = (String) row[1];
                BigDecimal debit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                BigDecimal credit = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

                Map<String, Object> m = new HashMap<>();
                m.put("code", code);
                m.put("name", name);

                if (code.startsWith("1")) {
                    // Aset = Debit - Kredit
                    BigDecimal saldo = debit.subtract(credit);
                    if (saldo.compareTo(BigDecimal.ZERO) != 0) {
                        m.put("amount", saldo);
                        assets.add(m);
                        totalAssets = totalAssets.add(saldo);
                    }
                } else if (code.startsWith("2")) {
                    // Kewajiban = Kredit - Debit
                    BigDecimal saldo = credit.subtract(debit);
                    if (saldo.compareTo(BigDecimal.ZERO) != 0) {
                        m.put("amount", saldo);
                        liabilities.add(m);
                        totalLiabilities = totalLiabilities.add(saldo);
                    }
                } else if (code.startsWith("3")) {
                    // Ekuitas = Kredit - Debit
                    BigDecimal saldo = credit.subtract(debit);
                    if (saldo.compareTo(BigDecimal.ZERO) != 0) {
                        m.put("amount", saldo);
                        equity.add(m);
                        totalEquity = totalEquity.add(saldo);
                    }
                } else if (code.startsWith("4") || code.startsWith("5") || code.startsWith("6") || code.startsWith("8")) {
                    // Akumulasi Pendapatan - Beban untuk Laba Berjalan
                    if (code.startsWith("4") || code.startsWith("8")) {
                        currentEarnings = currentEarnings.add(credit.subtract(debit));
                    } else {
                        currentEarnings = currentEarnings.subtract(debit.subtract(credit));
                    }
                }
            }

            // Tambahkan Laba Berjalan ke Ekuitas
            if (currentEarnings.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> m = new HashMap<>();
                m.put("code", "-");
                m.put("name", "Laba/Rugi Berjalan");
                m.put("amount", currentEarnings);
                equity.add(m);
                totalEquity = totalEquity.add(currentEarnings);
            }

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat: " + e.getMessage());
        }

        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);
        boolean isBalanced = totalAssets.compareTo(totalLiabilitiesAndEquity) == 0;

        model.addAttribute("assets", assets);
        model.addAttribute("totalAssets", totalAssets);
        model.addAttribute("liabilities", liabilities);
        model.addAttribute("totalLiabilities", totalLiabilities);
        model.addAttribute("equity", equity);
        model.addAttribute("totalEquity", totalEquity);
        model.addAttribute("totalLiabilitiesAndEquity", totalLiabilitiesAndEquity);
        model.addAttribute("isBalanced", isBalanced);

        return "ui/finance/balance_sheet";
    }
}