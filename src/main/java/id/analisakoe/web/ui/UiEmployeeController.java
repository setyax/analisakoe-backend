package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.hr.entity.Employee;
import id.analisakoe.hr.entity.EmployeeSalary;
import id.analisakoe.hr.repository.EmployeeRepository;
import id.analisakoe.hr.repository.EmployeeSalaryRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/ui/hr/employees")
public class UiEmployeeController {

    @Autowired
    private ContextService context;

    private final EmployeeRepository repo;
    private final EmployeeSalaryRepository salRepo;
    private final MenuService menuService;

    // Constructor yang benar: mengisi SEMUA variabel final
    public UiEmployeeController(EmployeeRepository repo, 
                                EmployeeSalaryRepository salRepo,
                                MenuService menuService) {
        this.repo = repo;
        this.salRepo = salRepo;
        this.menuService = menuService;
    }

    // ==================== KARYAWAN ====================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Employees");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("employees", repo.findAll());
        return "ui/hr/employee_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Employee");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("emp", new Employee());
        model.addAttribute("isNew", true);
        return "ui/hr/employee_form";
    }

    @PostMapping
    public String save(@ModelAttribute Employee emp, RedirectAttributes ra) {
        try {
            if (emp.getTenantId() == null) emp.setTenantId(context.tenantId());
            if (emp.getCompanyId() == null) emp.setCompanyId(context.companyId());
            if (emp.getStatus() == null) emp.setStatus("ACTIVE");
            repo.save(emp);
            ra.addFlashAttribute("success", "Karyawan berhasil ditambahkan.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/employees";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Employee");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("emp", repo.findById(id).orElseThrow());
        model.addAttribute("isNew", false);
        return "ui/hr/employee_form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Employee emp, RedirectAttributes ra) {
        try {
            Employee existing = repo.findById(id).orElseThrow();
            existing.setNip(emp.getNip());
            existing.setName(emp.getName());
            existing.setDepartment(emp.getDepartment());
            existing.setPosition(emp.getPosition());
            existing.setStatus(emp.getStatus());
            repo.save(existing);
            ra.addFlashAttribute("success", "Data Karyawan berhasil diubah.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/employees";
    }

        // UBAH DARI @DeleteMapping MENJADI @PostMapping
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            repo.deleteById(id);
            ra.addFlashAttribute("success", "Karyawan berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menghapus karyawan: " + e.getMessage());
        }
        return "redirect:/ui/hr/employees";
    }

    // ==================== GAJI KARYAWAN ====================
    // PERBAIKAN: URL tidak perlu /employees lagi karena sudah ada di @RequestMapping
    @GetMapping("/{id}/salary")
    public String editSalary(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Setting Gaji Karyawan");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        try {
            Employee emp = repo.findById(id).orElseThrow(() -> new Exception("Karyawan tidak ditemukan"));
            EmployeeSalary sal = salRepo.findById(id).orElse(new EmployeeSalary()); 
            
            model.addAttribute("emp", emp);
            model.addAttribute("sal", sal);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/ui/hr/employees";
        }
        return "ui/hr/salary_form";
    }

    // PERBAIKAN: URL tidak perlu /employees lagi
    @PostMapping("/{id}/salary")
    public String saveSalary(@PathVariable Long id,
                             @RequestParam BigDecimal baseSalary,
                             @RequestParam BigDecimal fixedAllowance,
                             @RequestParam BigDecimal bpjsKesehatanRate,
                             @RequestParam BigDecimal bpjsJhtRate,
                             @RequestParam BigDecimal bpjsJpRate,
                             @RequestParam String ptkpStatus,
                             RedirectAttributes ra) {
        try {
            EmployeeSalary sal = salRepo.findById(id).orElse(new EmployeeSalary());
            sal.setEmployeeId(id);
            sal.setBaseSalary(baseSalary);
            sal.setFixedAllowance(fixedAllowance);
            sal.setBpjsKesehatanRate(bpjsKesehatanRate);
            sal.setBpjsJhtRate(bpjsJhtRate);
            sal.setBpjsJpRate(bpjsJpRate);
            sal.setPtkpStatus(ptkpStatus);

            salRepo.save(sal);
            ra.addFlashAttribute("success", "Data gaji berhasil disimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menyimpan gaji: " + e.getMessage());
        }
        return "redirect:/ui/hr/employees";
    }
}