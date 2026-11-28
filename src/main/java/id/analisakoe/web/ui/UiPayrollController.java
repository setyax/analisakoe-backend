package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.hr.entity.Attendance;
import id.analisakoe.hr.entity.PayrollRun;
import id.analisakoe.hr.entity.PayrollDtl;
import id.analisakoe.hr.entity.Employee;
import id.analisakoe.hr.repository.AttendanceRepository;
import id.analisakoe.hr.repository.EmployeeRepository;
import id.analisakoe.hr.repository.PayrollRunRepository;
import id.analisakoe.hr.service.PayrollService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui/hr")
public class UiPayrollController {

    @Autowired
    private ContextService context;
    private final PayrollService payrollService;
    private final PayrollRunRepository payrollRepo;
    private final AttendanceRepository attRepo;
    private final EmployeeRepository empRepo;
    private final MenuService menuService;

    public UiPayrollController(PayrollService payrollService, PayrollRunRepository payrollRepo,
                               AttendanceRepository attRepo, EmployeeRepository empRepo, MenuService menuService) {
        this.payrollService = payrollService; this.payrollRepo = payrollRepo;
        this.attRepo = attRepo; this.empRepo = empRepo; this.menuService = menuService;
    }

    // ==================== ATTENDANCE ====================
    @GetMapping("/attendance")
    public String listAttendance(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Attendance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("attendances", attRepo.findAll());

        // Map untuk menampilkan NIK & Nama
        Map<Long, Employee> employeeMap = empRepo.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));
        model.addAttribute("employeeMap", employeeMap);

        return "ui/hr/attendance_list";
    }

    @GetMapping("/attendance/new")
    public String newAttendance(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Attendance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("employees", empRepo.findByTenantIdAndStatus(context.tenantId(), "ACTIVE"));
        model.addAttribute("today", LocalDate.now());
        return "ui/hr/attendance_form";
    }

    @PostMapping("/attendance")
    public String saveAttendance(@RequestParam Long employeeId, @RequestParam LocalDate attDate,
                                 @RequestParam String status, @RequestParam(required = false) Integer lateMinutes,
                                 @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            Attendance a = new Attendance();
            a.setEmployeeId(employeeId); a.setAttDate(attDate);
            a.setStatus(status); a.setLateMinutes(lateMinutes != null ? lateMinutes : 0);
            a.setRemarks(remarks);
            attRepo.save(a);
            ra.addFlashAttribute("success", "Attendance berhasil dicatat.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/attendance";
    }

    @GetMapping("/attendance/{id}/edit")
    public String editAttendance(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Attendance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        
        Attendance att = attRepo.findById(id).orElseThrow();
        model.addAttribute("attendance", att);
        model.addAttribute("employees", empRepo.findByTenantIdAndStatus(context.tenantId(), "ACTIVE"));
        
        return "ui/hr/attendance_form";
    }

    @PostMapping("/attendance/{id}")
    public String updateAttendance(@PathVariable Long id,
                                   @RequestParam Long employeeId, @RequestParam LocalDate attDate,
                                   @RequestParam String status, @RequestParam(required = false) Integer lateMinutes,
                                   @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            Attendance a = attRepo.findById(id).orElseThrow();
            a.setEmployeeId(employeeId); a.setAttDate(attDate);
            a.setStatus(status); a.setLateMinutes(lateMinutes != null ? lateMinutes : 0);
            a.setRemarks(remarks);
            attRepo.save(a);
            ra.addFlashAttribute("success", "Attendance berhasil diubah.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/attendance";
    }

    @PostMapping("/attendance/{id}/delete")
    public String deleteAttendance(@PathVariable Long id, RedirectAttributes ra) {
        try {
            attRepo.deleteById(id);
            ra.addFlashAttribute("success", "Attendance berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage()); }
        return "redirect:/ui/hr/attendance";
    }

    // ==================== PAYROLL ====================
    @GetMapping("/payroll")
    public String listPayroll(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Payroll");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("runs", payrollRepo.findAll());

        // Map untuk menampilkan NIK & Nama
        Map<Long, Employee> employeeMap = empRepo.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));
        model.addAttribute("employeeMap", employeeMap);

        return "ui/hr/payroll_list";
    }

    @PostMapping("/payroll/process")
    public String processPayroll(@RequestParam String month, @RequestParam String year, RedirectAttributes ra) {
        try {
            String period = year + "-" + month;
            payrollService.processPayroll(period);
            ra.addFlashAttribute("success", "Payroll periode " + period + " berhasil dihitung (DRAFT).");
        } catch (Exception e) { 
            ra.addFlashAttribute("error", e.getMessage()); 
        }
        return "redirect:/ui/hr/payroll";
    }

    @PostMapping("/payroll/{id}/post")
    public String postPayroll(@PathVariable Long id, RedirectAttributes ra) {
        try {
            payrollService.postPayroll(id);
            ra.addFlashAttribute("success", "Payroll berhasil diposting ke Jurnal.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/payroll";
    }

    @PostMapping("/payroll/{id}/unpost")
    public String unpostPayroll(@PathVariable Long id, RedirectAttributes ra) {
        try {
            payrollService.unpostPayroll(id);
            ra.addFlashAttribute("success", "Payroll berhasil di-unpost. Jurnal PAYROLL dihapus, kembali ke DRAFT.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/payroll";
    }

    @PostMapping("/payroll/{id}/delete")
    public String deletePayroll(@PathVariable Long id, RedirectAttributes ra) {
        try {
            payrollService.deletePayroll(id);
            ra.addFlashAttribute("success", "Payroll berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/payroll";
    }

    // ==================== PAYSLIP (SLIP GAJI) ====================
    @GetMapping("/payroll/{runId}/payslip/{empId}")
    public String viewPayslip(@PathVariable Long runId, @PathVariable Long empId, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Slip Gaji");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        try {
            PayrollRun run = payrollRepo.findById(runId)
                .orElseThrow(() -> new Exception("Payroll Run tidak ditemukan"));
            
            PayrollDtl dtl = run.getDetails().stream()
                .filter(d -> d.getEmployeeId().equals(empId))
                .findFirst()
                .orElseThrow(() -> new Exception("Data gaji karyawan tidak ditemukan"));

            Employee emp = empRepo.findById(empId)
                .orElseThrow(() -> new Exception("Karyawan tidak ditemukan"));

            model.addAttribute("run", run);
            model.addAttribute("dtl", dtl);
            model.addAttribute("emp", emp);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat slip gaji: " + e.getMessage());
        }

        return "ui/hr/payslip"; 
    }
}