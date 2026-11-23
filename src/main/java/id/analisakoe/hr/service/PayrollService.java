package id.analisakoe.hr.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.hr.entity.*;
import id.analisakoe.hr.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class PayrollService {
    private final PayrollRunRepository runRepo;
    private final EmployeeSalaryRepository salRepo;
    private final EmployeeRepository empRepo;
    private final AttendanceRepository attRepo;
    private final JournalService journalService;

    // --- PENGATURAN BESARAN DENDA & POTONGAN ---
    private static final BigDecimal DENDA_PER_MENIT = new BigDecimal("10000"); // Rp 10.000 per menit terlambat
    private static final BigDecimal HARI_KERJA_SEBULAN = new BigDecimal("22");
    // ------------------------------------------

    public PayrollService(PayrollRunRepository runRepo, EmployeeSalaryRepository salRepo, EmployeeRepository empRepo, AttendanceRepository attRepo, JournalService journalService) {
        this.runRepo = runRepo; 
        this.salRepo = salRepo; 
        this.empRepo = empRepo; 
        this.attRepo = attRepo; 
        this.journalService = journalService;
    }

    @Transactional
    public PayrollRun processPayroll(String period) {
        PayrollRun run = runRepo.findByPeriod(period).orElse(null);

        if (run != null) {
            if (!"DRAFT".equals(run.getStatus())) {
                throw new BusinessException("Payroll periode " + period + " sudah di-POSTING ke Jurnal. Tidak bisa diproses ulang.");
            }
            run.getDetails().clear(); 
            run.setTotalGross(BigDecimal.ZERO);
            run.setTotalDeduction(BigDecimal.ZERO);
            run.setTotalNet(BigDecimal.ZERO);
        } else {
            run = new PayrollRun();
            run.setTenantId(1L); 
            run.setCompanyId(1L);
            run.setPeriod(period); 
            run.setStatus("DRAFT");
        }

        YearMonth ym = YearMonth.parse(period);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Employee> employees = empRepo.findByTenantIdAndStatus(1L, "ACTIVE");
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeduction = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (Employee emp : employees) {
            EmployeeSalary sal = salRepo.findById(emp.getId()).orElse(null);
            if (sal == null) continue;

            List<Attendance> atts = attRepo.findByEmployeeIdAndAttDateBetween(emp.getId(), start, end);
            
            // 1. Hitung Jumlah Hari ALPHA
            long alphaDays = atts.stream().filter(a -> "ALPHA".equals(a.getStatus())).count();
            
            // 2. Hitung Total Menit Terlambat
            long totalLateMinutes = atts.stream()
                    .mapToLong(a -> a.getLateMinutes() != null ? a.getLateMinutes() : 0)
                    .sum();

            // 3. Gaji Bruto Dasar
            BigDecimal gross = sal.getBaseSalary().add(sal.getFixedAllowance());
            
            // 4. Potongan Alpha (Gaji Pokok / 22 hari * jumlah hari alpha)
            BigDecimal dailyRate = sal.getBaseSalary().divide(HARI_KERJA_SEBULAN, 2, RoundingMode.HALF_UP);
            BigDecimal alphaDeduction = dailyRate.multiply(new BigDecimal(alphaDays));
            
            // 5. Potongan Denda Keterlambatan (Menit * Denda)
            BigDecimal lateDeduction = DENDA_PER_MENIT.multiply(new BigDecimal(totalLateMinutes));

            // Hitung Gaji Kotor Akhir (setelah dipotong Alpha & Terlambat)
            gross = gross.subtract(alphaDeduction).subtract(lateDeduction);
            
            // Pastikan gaji kotor tidak minus
            if (gross.compareTo(BigDecimal.ZERO) < 0) gross = BigDecimal.ZERO;

            // 6. Potongan BPJS (DITANGGUNG PERUSAHAAN = 0)
            // Karena ditanggung perusahaan, potongan BPJS untuk karyawan dibuat 0.
            // (Jika sewaktu-waktu ingin membebankan ke karyawan, tinggal ubah totalBpjs di bawah)
            BigDecimal totalBpjs = BigDecimal.ZERO;
            
            /* 
            // --- KODE ASLI POTONGAN BPJS KARYAWAN (Simpan jika suatu saat dibutuhkan) ---
            BigDecimal bpjsKes = sal.getBaseSalary().multiply(sal.getBpjsKesehatanRate()).divide(new BigDecimal("100"));
            BigDecimal bpjsJht = sal.getBaseSalary().multiply(sal.getBpjsJhtRate()).divide(new BigDecimal("100"));
            BigDecimal bpjsJp = sal.getBaseSalary().multiply(sal.getBpjsJpRate()).divide(new BigDecimal("100"));
            BigDecimal totalBpjs = bpjsKes.add(bpjsJht).add(bpjsJp);
            */

            // 7. PPh 21 (Flat 5% dari Gross dikurangi BPJS Karyawan)
            BigDecimal pph21 = gross.subtract(totalBpjs).multiply(new BigDecimal("0.05"));
            if (gross.compareTo(new BigDecimal("5000000")) < 0) pph21 = BigDecimal.ZERO;

            // 8. Gaji Bersih
            BigDecimal net = gross.subtract(totalBpjs).subtract(pph21);

            PayrollDtl dtl = new PayrollDtl();
            dtl.setEmployeeId(emp.getId());
            dtl.setGrossSalary(gross);
            dtl.setBpjsDeduction(totalBpjs);
            dtl.setPph21Deduction(pph21);
            dtl.setNetSalary(net);
            run.addDetail(dtl);

            totalGross = totalGross.add(gross);
            totalDeduction = totalDeduction.add(totalBpjs).add(pph21);
            totalNet = totalNet.add(net);
        }

        run.setTotalGross(totalGross);
        run.setTotalDeduction(totalDeduction);
        run.setTotalNet(totalNet);
        return runRepo.save(run);
    }

    @Transactional
    public void postPayroll(Long id) {
        PayrollRun run = runRepo.findById(id).orElseThrow(() -> new BusinessException("Payroll tidak ditemukan"));
        if (!"DRAFT".equals(run.getStatus())) throw new BusinessException("Payroll sudah diposting.");

        BigDecimal totalBpjs = run.getDetails().stream().map(PayrollDtl::getBpjsDeduction).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPph = run.getDetails().stream().map(PayrollDtl::getPph21Deduction).reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Karena BPJS ditanggung perusahaan, jurnal Beban Gaji adalah Total Gross (Net + PPh21).
        // (Tidak ada hutang BPJS yang dipotong dari karyawan)
        
        journalService.createJournal(run.getTenantId(), run.getCompanyId(), YearMonth.parse(run.getPeriod()).atEndOfMonth(), "PAYROLL", "Payroll " + run.getPeriod(), List.of(
            new JournalService.JournalLine("6105", run.getTotalGross(), BigDecimal.ZERO, "Beban Gaji"),
            new JournalService.JournalLine("2204", BigDecimal.ZERO, totalPph, "Hutang PPh 21"),
            new JournalService.JournalLine("2203", BigDecimal.ZERO, run.getTotalNet(), "Hutang Gaji")
            // (Jika BPJS ditanggung perusahaan, perusahaan mencatat Beban BPJS dan Hutang BPJS secara terpisah di luar jurnal payroll karyawan)
        ));

        run.setStatus("POSTED");
        runRepo.save(run);
    }

    /**
     * Unpost: kembalikan ke DRAFT dengan menghapus jurnal PAYROLL yang dibuat saat posting.
     */
    @Transactional
    public void unpostPayroll(Long id) {
        PayrollRun run = runRepo.findById(id).orElseThrow(() -> new BusinessException("Payroll tidak ditemukan"));
        if (!"POSTED".equals(run.getStatus()))
            throw new BusinessException("Hanya Payroll berstatus POSTED yang dapat di-unpost.");

        int deleted = journalService.deleteByTypeAndRemarks("PAYROLL", "Payroll " + run.getPeriod());
        if (deleted == 0)
            throw new BusinessException("Jurnal posting tidak ditemukan. Payroll tidak diubah.");

        run.setStatus("DRAFT");
        runRepo.save(run);
    }

    /** Hapus payroll run — hanya DRAFT. Detail ikut terhapus (orphanRemoval). */
    @Transactional
    public void deletePayroll(Long id) {
        PayrollRun run = runRepo.findById(id).orElseThrow(() -> new BusinessException("Payroll tidak ditemukan"));
        if (!"DRAFT".equals(run.getStatus()))
            throw new BusinessException("Hanya Payroll berstatus DRAFT yang dapat dihapus. Unpost dulu jika sudah diposting.");
        runRepo.delete(run);
    }
}