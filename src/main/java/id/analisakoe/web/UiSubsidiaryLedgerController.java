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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/finance/reports/subsidiary-ledger")
public class UiSubsidiaryLedgerController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiSubsidiaryLedgerController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String view(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Subsidiary Ledger (AR/AP Aging)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate today = LocalDate.now();

        // --- 1. QUERY AR (PIUTANG) ---
        String sqlAR = "SELECT c.name AS customer, si.doc_no, si.invoice_date, si.due_date, si.total_amount " +
                       "FROM sal.sales_invoice_hdr si " +
                       "JOIN md.customer c ON c.id = si.customer_id " +
                       "JOIN fin.journal_hdr jh ON jh.ref_type = 'SALES_INVOICE' AND jh.ref_id = si.id " +
                       "WHERE jh.status = 'POSTED' AND si.status != 'VOID' " +
                       "ORDER BY c.name, si.invoice_date";

        List<Map<String, Object>> arList = new ArrayList<>();
        BigDecimal totalAR = BigDecimal.ZERO;

        @SuppressWarnings("unchecked")
        List<Object[]> arResults = em.createNativeQuery(sqlAR).getResultList();
        for (Object[] row : arResults) {
            String customer = (String) row[0];
            String docNo = (String) row[1];
            LocalDate invDate = toLocalDate(row[2]);
            LocalDate dueDate = toLocalDate(row[3]);
            BigDecimal amount = (BigDecimal) row[4];

            long ageDays = ChronoUnit.DAYS.between(invDate, today);
            String aging = getAgingBucket(ageDays);

            Map<String, Object> m = new HashMap<>();
            m.put("customer", customer);
            m.put("docNo", docNo);
            m.put("invDate", invDate);
            m.put("dueDate", dueDate);
            m.put("amount", amount);
            m.put("ageDays", ageDays);
            m.put("aging", aging);
            arList.add(m);
            totalAR = totalAR.add(amount);
        }

        // --- 2. QUERY AP (HUTANG) ---
        String sqlAP = "SELECT s.name AS supplier, pi.doc_no, pi.invoice_date, pi.due_date, pi.total_amount " +
                       "FROM pur.purchase_invoice_hdr pi " +
                       "JOIN md.supplier s ON s.id = pi.supplier_id " +
                       "JOIN fin.journal_hdr jh ON jh.ref_type = 'PURCHASE_INVOICE' AND jh.ref_id = pi.id " +
                       "WHERE jh.status = 'POSTED' AND pi.status != 'VOID' " +
                       "ORDER BY s.name, pi.invoice_date";

        List<Map<String, Object>> apList = new ArrayList<>();
        BigDecimal totalAP = BigDecimal.ZERO;

        @SuppressWarnings("unchecked")
        List<Object[]> apResults = em.createNativeQuery(sqlAP).getResultList();
        for (Object[] row : apResults) {
            String supplier = (String) row[0];
            String docNo = (String) row[1];
            LocalDate invDate = toLocalDate(row[2]);
            LocalDate dueDate = toLocalDate(row[3]);
            BigDecimal amount = (BigDecimal) row[4];

            long ageDays = ChronoUnit.DAYS.between(invDate, today);
            String aging = getAgingBucket(ageDays);

            Map<String, Object> m = new HashMap<>();
            m.put("supplier", supplier);
            m.put("docNo", docNo);
            m.put("invDate", invDate);
            m.put("dueDate", dueDate);
            m.put("amount", amount);
            m.put("ageDays", ageDays);
            m.put("aging", aging);
            apList.add(m);
            totalAP = totalAP.add(amount);
        }

        model.addAttribute("arList", arList);
        model.addAttribute("totalAR", totalAR);
        model.addAttribute("apList", apList);
        model.addAttribute("totalAP", totalAP);

        return "ui/finance/subsidiary_ledger";
    }

    private String getAgingBucket(long days) {
        if (days <= 30) return "1-30 Hari";
        if (days <= 60) return "31-60 Hari";
        if (days <= 90) return "61-90 Hari";
        return "> 90 Hari";
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.sql.Date) return ((java.sql.Date) obj).toLocalDate();
        if (obj instanceof java.time.LocalDate) return (java.time.LocalDate) obj;
        if (obj instanceof java.sql.Timestamp) return ((java.sql.Timestamp) obj).toLocalDateTime().toLocalDate();
        return LocalDate.parse(obj.toString());
    }
}