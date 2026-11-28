package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
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
@RequestMapping("/ui/finance/fin-reports")
public class UiFinancialReportController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiFinancialReportController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ============================================================
    // 1. NERACA (BALANCE SHEET) â€” As of Date
    // ============================================================
    @GetMapping("/balance-sheet")
    public String balanceSheet(@RequestParam(required = false) String asOfDate,
                               HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Neraca (Balance Sheet)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate asOf = (asOfDate != null && !asOfDate.isEmpty()) ? LocalDate.parse(asOfDate) : LocalDate.now();
        model.addAttribute("asOfDate", asOf.toString());

        try {
            // Total per Account Type
            Map<String, BigDecimal> totals = new HashMap<>();
            Map<String, List<Map<String, Object>>> accountsByType = new HashMap<>();

            String sql = "SELECT coa.code, coa.name, coa.account_type, " +
                "COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.debit ELSE 0 END), 0) AS total_debit, " +
                "COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.credit ELSE 0 END), 0) AS total_credit " +
                "FROM fin.chart_of_account coa " +
                "LEFT JOIN fin.journal_dtl jd ON jd.account_id = coa.id " +
                "LEFT JOIN fin.journal_hdr jh ON jh.id = jd.header_id AND jh.doc_date <= :asOfDate " +
                "WHERE coa.tenant_id = :tenantId AND (coa.is_header IS NULL OR coa.is_header = false) " +
                "GROUP BY coa.code, coa.name, coa.account_type " +
                "HAVING COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.debit ELSE 0 END), 0) <> 0 " +
                "   OR COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.credit ELSE 0 END), 0) <> 0 " +
                "ORDER BY coa.code";

            List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("asOfDate", asOf).setParameter("tenantId", context.tenantId()).getResultList();

            BigDecimal totalAssets = BigDecimal.ZERO;
            BigDecimal totalLiabilities = BigDecimal.ZERO;
            BigDecimal totalEquity = BigDecimal.ZERO;

            for (Object[] row : rows) {
                String code = row[0] != null ? row[0].toString() : "-";
                String name = row[1] != null ? row[1].toString() : "-";
                String type = row[2] != null ? row[2].toString() : "";
                BigDecimal debit = new BigDecimal(row[3].toString());
                BigDecimal credit = new BigDecimal(row[4].toString());

                BigDecimal saldo = BigDecimal.ZERO;
                if ("ASSET".equals(type) || "EXPENSE".equals(type)) {
                    saldo = debit.subtract(credit);
                } else if ("LIABILITY".equals(type) || "EQUITY".equals(type) || "REVENUE".equals(type)) {
                    saldo = credit.subtract(debit);
                }

                if (saldo.compareTo(BigDecimal.ZERO) == 0) continue;

                Map<String, Object> m = new HashMap<>();
                m.put("code", code);
                m.put("name", name);
                m.put("saldo", saldo);
                accountsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(m);

                if ("ASSET".equals(type)) totalAssets = totalAssets.add(saldo);
                else if ("LIABILITY".equals(type)) totalLiabilities = totalLiabilities.add(saldo);
                else if ("EQUITY".equals(type)) totalEquity = totalEquity.add(saldo);
            }

            // Hitung Laba Berjalan (Revenue - Expense dari awal tahun s/d asOfDate)
            LocalDate yearStart = asOf.withDayOfMonth(1).withMonth(1);
            String labaSql = "SELECT " +
                "COALESCE(SUM(CASE WHEN coa.account_type = 'REVENUE' AND jh.status = 'POSTED' THEN jd.credit - jd.debit ELSE 0 END), 0) AS revenue, " +
                "COALESCE(SUM(CASE WHEN coa.account_type = 'EXPENSE' AND jh.status = 'POSTED' THEN jd.debit - jd.credit ELSE 0 END), 0) AS expense " +
                "FROM fin.chart_of_account coa " +
                "JOIN fin.journal_dtl jd ON jd.account_id = coa.id " +
                "JOIN fin.journal_hdr jh ON jh.id = jd.header_id " +
                "WHERE coa.tenant_id = :tenantId AND jh.doc_date BETWEEN :start AND :end";
            Object[] labaArr = (Object[]) em.createNativeQuery(labaSql)
                .setParameter("start", yearStart).setParameter("end", asOf).setParameter("tenantId", context.tenantId()).getSingleResult();
            BigDecimal netIncome = new BigDecimal(labaArr[0].toString()).subtract(new BigDecimal(labaArr[1].toString()));

            // Tambahkan Laba Berjalan ke Ekuitas
            if (netIncome.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> laba = new HashMap<>();
                laba.put("code", "-");
                laba.put("name", "Laba (Rugi) Periode Berjalan");
                laba.put("saldo", netIncome);
                accountsByType.computeIfAbsent("EQUITY", k -> new ArrayList<>()).add(laba);
                totalEquity = totalEquity.add(netIncome);
            }

            model.addAttribute("assets", accountsByType.getOrDefault("ASSET", new ArrayList<>()));
            model.addAttribute("liabilities", accountsByType.getOrDefault("LIABILITY", new ArrayList<>()));
            model.addAttribute("equity", accountsByType.getOrDefault("EQUITY", new ArrayList<>()));
            model.addAttribute("totalAssets", totalAssets);
            model.addAttribute("totalLiabilities", totalLiabilities);
            model.addAttribute("totalEquity", totalEquity);
            model.addAttribute("totalLiabEq", totalLiabilities.add(totalEquity));

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat Neraca: " + e.getMessage());
            e.printStackTrace();
        }
        return "ui/finance/balance_sheet";
    }

    // ============================================================
    // 2. LABA RUGI (INCOME STATEMENT) â€” Periode
    // ============================================================
    @GetMapping("/income-statement")
    public String incomeStatement(@RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate,
                                  HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Laba Rugi (Income Statement)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());

        try {
            String sql = "SELECT coa.code, coa.name, coa.account_type, " +
                "COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.debit ELSE 0 END), 0) AS total_debit, " +
                "COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.credit ELSE 0 END), 0) AS total_credit " +
                "FROM fin.chart_of_account coa " +
                "JOIN fin.journal_dtl jd ON jd.account_id = coa.id " +
                "JOIN fin.journal_hdr jh ON jh.id = jd.header_id " +
                "WHERE coa.tenant_id = :tenantId AND jh.doc_date BETWEEN :start AND :end " +
                "  AND coa.account_type IN ('REVENUE', 'EXPENSE') " +
                "  AND (coa.is_header IS NULL OR coa.is_header = false) " +
                "GROUP BY coa.code, coa.name, coa.account_type ORDER BY coa.code";

            List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("start", start).setParameter("end", end).setParameter("tenantId", context.tenantId()).getResultList();

            List<Map<String, Object>> revenues = new ArrayList<>();
            List<Map<String, Object>> expenses = new ArrayList<>();
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;

            for (Object[] row : rows) {
                String code = row[0].toString();
                String name = row[1].toString();
                String type = row[2].toString();
                BigDecimal debit = new BigDecimal(row[3].toString());
                BigDecimal credit = new BigDecimal(row[4].toString());

                BigDecimal saldo = "REVENUE".equals(type) ? credit.subtract(debit) : debit.subtract(credit);
                if (saldo.compareTo(BigDecimal.ZERO) == 0) continue;

                Map<String, Object> m = new HashMap<>();
                m.put("code", code);
                m.put("name", name);
                m.put("saldo", saldo);

                if ("REVENUE".equals(type)) {
                    revenues.add(m);
                    totalRevenue = totalRevenue.add(saldo);
                } else {
                    expenses.add(m);
                    totalExpense = totalExpense.add(saldo);
                }
            }

            BigDecimal netIncome = totalRevenue.subtract(totalExpense);

            model.addAttribute("revenues", revenues);
            model.addAttribute("expenses", expenses);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("totalExpense", totalExpense);
            model.addAttribute("netIncome", netIncome);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat Laba Rugi: " + e.getMessage());
            e.printStackTrace();
        }
        return "ui/finance/income_statement";
    }

    // ============================================================
    // 3. NERACA SALDO (TRIAL BALANCE) â€” Periode
    // ============================================================
    @GetMapping("/trial-balance")
    public String trialBalance(@RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Neraca Saldo (Trial Balance)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());

        try {
            String sql = "SELECT coa.code, coa.name, coa.account_type, " +
                "COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.debit ELSE 0 END), 0) AS total_debit, " +
                "COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.credit ELSE 0 END), 0) AS total_credit " +
                "FROM fin.chart_of_account coa " +
                "LEFT JOIN fin.journal_dtl jd ON jd.account_id = coa.id " +
                "LEFT JOIN fin.journal_hdr jh ON jh.id = jd.header_id AND jh.doc_date BETWEEN :start AND :end " +
                "WHERE coa.tenant_id = :tenantId AND (coa.is_header IS NULL OR coa.is_header = false) " +
                "GROUP BY coa.code, coa.name, coa.account_type " +
                "HAVING COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.debit ELSE 0 END), 0) <> 0 " +
                "   OR COALESCE(SUM(CASE WHEN jh.status = 'POSTED' THEN jd.credit ELSE 0 END), 0) <> 0 " +
                "ORDER BY coa.code";

            List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("start", start).setParameter("end", end).setParameter("tenantId", context.tenantId()).getResultList();

            List<Map<String, Object>> accounts = new ArrayList<>();
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;

            for (Object[] row : rows) {
                String code = row[0].toString();
                String name = row[1].toString();
                String type = row[2].toString();
                BigDecimal debit = new BigDecimal(row[3].toString());
                BigDecimal credit = new BigDecimal(row[4].toString());

                // Saldo normal per tipe
                BigDecimal saldoDebit = BigDecimal.ZERO;
                BigDecimal saldoCredit = BigDecimal.ZERO;
                if ("ASSET".equals(type) || "EXPENSE".equals(type)) {
                    BigDecimal net = debit.subtract(credit);
                    if (net.compareTo(BigDecimal.ZERO) > 0) saldoDebit = net;
                    else if (net.compareTo(BigDecimal.ZERO) < 0) saldoCredit = net.negate();
                } else {
                    BigDecimal net = credit.subtract(debit);
                    if (net.compareTo(BigDecimal.ZERO) > 0) saldoCredit = net;
                    else if (net.compareTo(BigDecimal.ZERO) < 0) saldoDebit = net.negate();
                }

                Map<String, Object> m = new HashMap<>();
                m.put("code", code);
                m.put("name", name);
                //m.addAll(new HashMap<>(){});
                m.put("type", type);
                m.put("debit", saldoDebit);
                m.put("credit", saldoCredit);
                accounts.add(m);

                totalDebit = totalDebit.add(saldoDebit);
                totalCredit = totalCredit.add(saldoCredit);
            }

            model.addAttribute("accounts", accounts);
            model.addAttribute("totalDebit", totalDebit);
            model.addAttribute("totalCredit", totalCredit);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat Trial Balance: " + e.getMessage());
            e.printStackTrace();
        }
        return "ui/finance/trial_balance";
    }

    // ============================================================
    // 4. ARUS KAS (CASH FLOW) â€” Periode
    // ============================================================
    @GetMapping("/cash-flow")
    public String cashFlow(@RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate,
                           HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Arus Kas (Cash Flow)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());

        try {
            // 1. Saldo awal kas/bank (kode akun mulai 11, exclude 1103, 1104 - biasanya bukan kas)
            String saldoAwalSql = "SELECT COALESCE(SUM(jd.debit - jd.credit), 0) " +
                "FROM fin.chart_of_account coa " +
                "JOIN fin.journal_dtl jd ON jd.account_id = coa.id " +
                "JOIN fin.journal_hdr jh ON jh.id = jd.header_id " +
                "WHERE coa.tenant_id = :tenantId AND coa.code LIKE '11%' AND jh.status = 'POSTED' " +
                "  AND jh.doc_date < :start";
            BigDecimal saldoAwal = new BigDecimal(
                em.createNativeQuery(saldoAwalSql).setParameter("start", start).setParameter("tenantId", context.tenantId()).getSingleResult().toString());

            // 2. Mutasi per kategori (berdasarkan ref_type)
            String mutasiSql = "SELECT COALESCE(jh.ref_type, 'MANUAL') AS kategori, " +
                "COALESCE(SUM(jd.debit - jd.credit), 0) AS mutasi " +
                "FROM fin.chart_of_account coa " +
                "JOIN fin.journal_dtl jd ON jd.account_id = coa.id " +
                "JOIN fin.journal_hdr jh ON jh.id = jd.header_id " +
                "WHERE coa.tenant_id = :tenantId AND coa.code LIKE '11%' AND jh.status = 'POSTED' " +
                "  AND jh.doc_date BETWEEN :start AND :end " +
                "GROUP BY jh.ref_type ORDER BY jh.ref_type";

            List<Object[]> rows = em.createNativeQuery(mutasiSql)
                .setParameter("start", start).setParameter("end", end).setParameter("tenantId", context.tenantId()).getResultList();

            List<Map<String, Object>> inflows = new ArrayList<>();
            List<Map<String, Object>> outflows = new ArrayList<>();
            BigDecimal totalIn = BigDecimal.ZERO;
            BigDecimal totalOut = BigDecimal.ZERO;

            for (Object[] row : rows) {
                String kategori = row[0] != null ? row[0].toString() : "MANUAL";
                BigDecimal mutasi = new BigDecimal(row[1].toString());
                if (mutasi.compareTo(BigDecimal.ZERO) == 0) continue;

                Map<String, Object> m = new HashMap<>();
                m.put("kategori", labelKategori(kategori));
                m.put("nilai", mutasi.abs());

                if (mutasi.compareTo(BigDecimal.ZERO) > 0) {
                    inflows.add(m);
                    totalIn = totalIn.add(mutasi);
                } else {
                    outflows.add(m);
                    totalOut = totalOut.add(mutasi.abs());
                }
            }

            BigDecimal saldoAkhir = saldoAwal.add(totalIn).subtract(totalOut);

            model.addAttribute("saldoAwal", saldoAwal);
            model.addAttribute("inflows", inflows);
            model.addAttribute("outflows", outflows);
            model.addAttribute("totalIn", totalIn);
            model.addAttribute("totalOut", totalOut);
            model.addAttribute("saldoAkhir", saldoAkhir);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat Arus Kas: " + e.getMessage());
            e.printStackTrace();
        }
        return "ui/finance/cash_flow";
    }

    private String labelKategori(String ref) {
        switch (ref) {
            case "SALES_INVOICE": return "Penerimaan dari Penjualan";
            case "CUSTOMER_RECEIPT": return "Penerimaan Piutang Customer";
            case "PURCHASE_INVOICE": return "Pembelian";
            case "AP_PAYMENT": return "Pembayaran Hutang Supplier";
            case "GRN": return "Penerimaan Barang";
            case "DO": return "Pengiriman Barang";
            case "MANUAL": return "Transaksi Manual";
            default: return ref;
        }
    }
}
