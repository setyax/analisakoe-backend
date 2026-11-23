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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/dashboard")
public class UiDashboardController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiDashboardController(MenuService menuService) {
        this.menuService = menuService;
    }

        @GetMapping
    public String dashboard(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Executive Dashboard");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        Long compId = user.getActiveCompanyId();

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal arOutstanding = BigDecimal.ZERO;
        BigDecimal apOutstanding = BigDecimal.ZERO;
        BigDecimal bankBalance = BigDecimal.ZERO;
        BigDecimal stockValue = BigDecimal.ZERO;
        Long totalEmployees = 0L;
        Long soDraft = 0L, poDraft = 0L, prWaitingPo = 0L, rfqPending = 0L, prDraft = 0L;

        List<String> labels = new ArrayList<>();
        List<BigDecimal> dataSales = new ArrayList<>();
        List<BigDecimal> dataExpenses = new ArrayList<>();

        try {
            // 1. TOTAL PENJUALAN
            try {
                totalSales = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM sal.sales_invoice_hdr WHERE status = 'POSTED' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn Sales: " + e.getMessage()); }

            // 2. TOTAL PENGELUARAN (UBAH KE fin.cash_bank_trx)
            try {
                totalExpenses = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(amount), 0) FROM fin.cash_bank_trx WHERE status = 'POSTED' AND trx_type = 'OUT' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn Expense: " + e.getMessage()); }
                
            // 3. TOTAL ASET
            try {
                totalAssets = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(book_value), 0) FROM fin.asset_master WHERE status = 'ACTIVE' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn Asset: " + e.getMessage()); }
                
            // 3b. LABA (Penjualan - Pengeluaran kas)
            try {
                totalProfit = totalSales.subtract(totalExpenses);
            } catch (Exception e) { System.out.println("Warn Profit: " + e.getMessage()); }

            // 3c. PIUTANG — saldo jurnal COA 1201 (Piutang Usaha): semua piutang yang sudah diakui
            try {
                arOutstanding = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(d.debit - d.credit), 0) FROM fin.journal_dtl d " +
                    "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                    "JOIN fin.chart_of_account c ON c.id = d.account_id " +
                    "WHERE h.company_id = :compId AND c.code = '1201' AND h.status = 'POSTED'")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn AR: " + e.getMessage()); }

            // 3d. HUTANG — saldo jurnal COA 2101 (Hutang Usaha / GR-IR): semua hutang yang sudah diakui
            try {
                apOutstanding = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(d.credit - d.debit), 0) FROM fin.journal_dtl d " +
                    "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                    "JOIN fin.chart_of_account c ON c.id = d.account_id " +
                    "WHERE h.company_id = :compId AND c.code = '2101' AND h.status = 'POSTED'")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn AP: " + e.getMessage()); }

            // 3e. SALDO KAS & BANK
            try {
                bankBalance = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(current_balance), 0) FROM fin.bank_account WHERE company_id = :compId")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn Bank: " + e.getMessage()); }

            // 3f. NILAI PERSEDIAAN (stok akhir per cabang aktif)
            try {
                stockValue = (BigDecimal) em.createNativeQuery(
                    "SELECT COALESCE(SUM(sb.total_value), 0) FROM inv.stock_balance sb " +
                    "JOIN md.warehouse w ON w.id = sb.warehouse_id WHERE w.company_id = :compId")
                    .setParameter("compId", compId).getSingleResult();
            } catch (Exception e) { System.out.println("Warn Stock: " + e.getMessage()); }

            // 3g. DOKUMEN PERLU TINDAKAN
            try {
                soDraft = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM sal.sales_order_hdr WHERE status = 'DRAFT' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult()).longValue();
                poDraft = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.purchase_order_hdr WHERE status = 'DRAFT' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult()).longValue();
                prWaitingPo = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.purchase_request_hdr WHERE status = 'CONFIRMED' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult()).longValue();
                rfqPending = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.rfq_hdr WHERE UPPER(status) = 'PENDING_APPROVAL' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult()).longValue();
                prDraft = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.purchase_request_hdr WHERE status = 'DRAFT' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult()).longValue();
            } catch (Exception e) { System.out.println("Warn ActionItems: " + e.getMessage()); }
                
            // 4. TOTAL KARYAWAN
            try {
                totalEmployees = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM hr.employee WHERE status = 'ACTIVE' AND company_id = :compId")
                    .setParameter("compId", compId).getSingleResult()).longValue();
            } catch (Exception e) { System.out.println("Warn Employee: " + e.getMessage()); }

            model.addAttribute("totalSales", totalSales);
            model.addAttribute("totalExpenses", totalExpenses);
            model.addAttribute("totalAssets", totalAssets);
            model.addAttribute("totalProfit", totalProfit);
            model.addAttribute("arOutstanding", arOutstanding);
            model.addAttribute("apOutstanding", apOutstanding);
            model.addAttribute("bankBalance", bankBalance);
            model.addAttribute("stockValue", stockValue);
            model.addAttribute("totalEmployees", totalEmployees);
            model.addAttribute("soDraft", soDraft);
            model.addAttribute("poDraft", poDraft);
            model.addAttribute("prWaitingPo", prWaitingPo);
            model.addAttribute("rfqPending", rfqPending);
            model.addAttribute("prDraft", prDraft);

            // KPI cards — angka ringkas (tidak wrap) + angka lengkap kecil di bawah
            List<Map<String, Object>> kpis = new ArrayList<>();
            kpis.add(kpi("Total Penjualan", "fa-chart-line", "#eff6ff", "#3b82f6", compactRupiah(totalSales), fullRupiah(totalSales), "", 0.10));
            kpis.add(kpi("Total Pengeluaran", "fa-money-bill-transfer", "#fef2f2", "#ef4444", compactRupiah(totalExpenses), fullRupiah(totalExpenses), "", 0.15));
            kpis.add(kpi("Laba", "fa-sack-dollar", "#ecfdf5", "#059669", compactRupiah(totalProfit), fullRupiah(totalProfit),
                    totalProfit.doubleValue() < 0 ? "color:#dc2626;" : "color:#059669;", 0.20));
            kpis.add(kpi("Nilai Buku Aset", "fa-building", "#f0fdfa", "#0d9488", compactRupiah(totalAssets), fullRupiah(totalAssets), "", 0.25));
            kpis.add(kpi("Piutang", "fa-hand-holding-dollar", "#fffbeb", "#d97706", compactRupiah(arOutstanding), fullRupiah(arOutstanding), "", 0.30));
            kpis.add(kpi("Hutang", "fa-file-invoice-dollar", "#fef2f2", "#dc2626", compactRupiah(apOutstanding), fullRupiah(apOutstanding), "", 0.35));
            kpis.add(kpi("Saldo Kas & Bank", "fa-building-columns", "#eff6ff", "#2563eb", compactRupiah(bankBalance), fullRupiah(bankBalance), "", 0.40));
            kpis.add(kpi("Nilai Persediaan", "fa-boxes-stacked", "#f5f3ff", "#7c3aed", compactRupiah(stockValue), fullRupiah(stockValue), "", 0.45));
            kpis.add(kpi("Karyawan Aktif", "fa-users", "#faf5ff", "#8b5cf6",
                    totalEmployees + " Orang", "", "", 0.50));
            model.addAttribute("kpis", kpis);

            // 5. CHART DATA (PENJUALAN & PENGELUARAN)
            // generate_series: bulan tanpa transaksi tetap muncul dengan nilai 0
            String sqlChart = 
                "SELECT to_char(m.bulan, 'YYYY-MM') as bulan, " +
                "COALESCE(SUM(CASE WHEN jenis = 'SALES' THEN amount ELSE 0 END), 0) as sales, " +
                "COALESCE(SUM(CASE WHEN jenis = 'EXPENSE' THEN amount ELSE 0 END), 0) as expense " +
                "FROM generate_series(date_trunc('month', CURRENT_DATE) - interval '5 months', date_trunc('month', CURRENT_DATE), interval '1 month') AS m(bulan) " +
                "LEFT JOIN ( " +
                "   SELECT doc_date, total_amount as amount, 'SALES' as jenis FROM sal.sales_invoice_hdr WHERE status = 'POSTED' AND company_id = :compId " +
                "   UNION ALL " +
                "   SELECT doc_date, amount, 'EXPENSE' as jenis FROM fin.cash_bank_trx WHERE status = 'POSTED' AND trx_type = 'OUT' AND company_id = :compId " +
                ") AS combined ON date_trunc('month', combined.doc_date) = m.bulan " +
                "GROUP BY m.bulan " +
                "ORDER BY m.bulan";
            
            List<Object[]> rawTren = em.createNativeQuery(sqlChart)
                    .setParameter("compId", compId)
                    .setParameter("compId", compId).getResultList();
            
            for (int i = 0; i < rawTren.size(); i++) {
                Object[] row = rawTren.get(i);
                labels.add((String) row[0]);
                dataSales.add(row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
                dataExpenses.add(row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO);
            }

            model.addAttribute("chartLabels", labels);
            model.addAttribute("chartSales", dataSales);
            model.addAttribute("chartExpenses", dataExpenses);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat dashboard: " + e.getMessage());
        }

        return "ui/dashboard";
    }

    // ===================== HELPERS FORMAT ANGKA =====================

    private Map<String, Object> kpi(String title, String icon, String iconBg, String color,
                                    String compact, String full, String valueStyle, double delay) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("icon", icon);
        m.put("iconBg", iconBg);
        m.put("color", color);
        m.put("compact", compact);
        m.put("full", full);
        m.put("valueStyle", valueStyle);
        m.put("delay", delay);
        return m;
    }

    /** Angka ringkas untuk KPI: Rp 206,5 jt / Rp 1,25 M / Rp 45 rb — tidak pernah wrap */
    private String compactRupiah(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        BigDecimal abs = v.abs();
        String sign = v.compareTo(BigDecimal.ZERO) < 0 ? "−" : "";
        BigDecimal miliar = new BigDecimal("1000000000");
        BigDecimal juta   = new BigDecimal("1000000");
        BigDecimal ribu   = new BigDecimal("1000");
        if (abs.compareTo(miliar) >= 0) {
            return "Rp " + sign + div(abs, miliar) + " M";
        }
        if (abs.compareTo(juta) >= 0) {
            return "Rp " + sign + div(abs, juta) + " jt";
        }
        if (abs.compareTo(ribu) >= 0) {
            return "Rp " + sign + div(abs, ribu) + " rb";
        }
        return "Rp " + sign + abs.toBigInteger().toString();
    }

    private String div(BigDecimal abs, BigDecimal by) {
        String s = abs.divide(by, 2, java.math.RoundingMode.HALF_UP)
                      .stripTrailingZeros().toPlainString();
        return s.replace('.', ',');
    }

    /** Angka lengkap: Rp 206.500.000 (pemisah ribuan gaya Indonesia) */
    private String fullRupiah(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        java.text.NumberFormat nf = java.text.NumberFormat.getIntegerInstance(new java.util.Locale("id", "ID"));
        return "Rp " + nf.format(v.longValue());
    }
}