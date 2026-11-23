package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/finance/consolidation")
public class UiConsolidationController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiConsolidationController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String consolidatedReport(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Consolidated Financial Report");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        // Query mengambil semua transaksi dari SELURUH CABANG (Tanpa filter company_id)
        String sql = "SELECT c.code, c.name, c.account_type, " +
                     "SUM(COALESCE(d.credit, 0) - COALESCE(d.debit, 0)) as balance " +
                     "FROM fin.journal_dtl d " +
                     "JOIN fin.journal_hdr h ON d.header_id = h.id " +
                     "JOIN fin.chart_of_account c ON d.account_id = c.id " +
                     "WHERE h.status = 'POSTED' " +
                     "GROUP BY c.code, c.name, c.account_type " +
                     "ORDER BY c.code";

        List<Object[]> rawData = em.createNativeQuery(sql).getResultList();

        // Kelompokkan berdasarkan Tipe Akun
        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liabilities = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        List<Map<String, Object>> revenues = new ArrayList<>();
        List<Map<String, Object>> expenses = new ArrayList<>();

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Object[] row : rawData) {
            String code = (String) row[0];
            String name = (String) row[1];
            String type = (String) row[2];
            BigDecimal rawBalance = (BigDecimal) row[3];
            
            // Untuk Aset & Beban, dibalik agar positif (karena query tadi credit-debit)
            BigDecimal amount = rawBalance;
            if ("ASSET".equalsIgnoreCase(type) || "EXPENSE".equalsIgnoreCase(type)) {
                amount = rawBalance.negate();
            }

            Map<String, Object> account = new HashMap<>();
            account.put("code", code);
            account.put("name", name);
            account.put("amount", amount);

            if ("ASSET".equalsIgnoreCase(type)) {
                assets.add(account);
                totalAssets = totalAssets.add(amount);
            } else if ("LIABILITY".equalsIgnoreCase(type)) {
                liabilities.add(account);
                totalLiabilities = totalLiabilities.add(amount);
            } else if ("EQUITY".equalsIgnoreCase(type)) {
                equity.add(account);
                totalEquity = totalEquity.add(amount);
            } else if ("REVENUE".equalsIgnoreCase(type)) {
                revenues.add(account);
                totalRevenue = totalRevenue.add(amount);
            } else if ("EXPENSE".equalsIgnoreCase(type)) {
                expenses.add(account);
                totalExpense = totalExpense.add(amount);
            }
        }

        // Hitung Laba Bersih
        BigDecimal netIncome = totalRevenue.subtract(totalExpense);
        
        // Tambahkan Laba Bersih ke Ekuitas (Laba Ditahan)
        totalEquity = totalEquity.add(netIncome);

        model.addAttribute("assets", assets);
        model.addAttribute("liabilities", liabilities);
        model.addAttribute("equity", equity);
        model.addAttribute("revenues", revenues);
        model.addAttribute("expenses", expenses);

        model.addAttribute("totalAssets", totalAssets);
        model.addAttribute("totalLiabilities", totalLiabilities);
        model.addAttribute("totalEquity", totalEquity);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("netIncome", netIncome);

        return "ui/finance/consolidated_report";
    }
}