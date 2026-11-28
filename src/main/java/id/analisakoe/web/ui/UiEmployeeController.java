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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/hr/employees")
public class UiEmployeeController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final EmployeeRepository repo;
    private final EmployeeSalaryRepository salRepo;
    private final MenuService menuService;

    public UiEmployeeController(EmployeeRepository repo,
                                EmployeeSalaryRepository salRepo,
                                MenuService menuService) {
        this.repo = repo;
        this.salRepo = salRepo;
        this.menuService = menuService;
    }

    private void commonModel(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    /** Company yang boleh dipilih user yang sedang login (sesuai user_company). */
    private List<Object[]> allowedCompanies(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        return em.createNativeQuery(
                "SELECT c.id, c.code, c.name FROM core.company c " +
                "WHERE c.tenant_id = :tenantId AND c.status = 'ACTIVE' " +
                "AND c.id IN (SELECT uc.company_id FROM core.user_company uc WHERE uc.user_id = :userId) " +
                "ORDER BY c.name")
            .setParameter("tenantId", context.tenantId())
            .setParameter("userId", user.getId())
            .getResultList();
    }

    private void validateCompany(HttpSession session, Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company wajib dipilih.");
        }
        boolean allowed = allowedCompanies(session).stream()
            .anyMatch(row -> ((Number) row[0]).longValue() == companyId.longValue());
        if (!allowed) {
            throw new AccessDeniedException("Company tidak tersedia untuk user ini.");
        }
    }

    private Map<Long, String> companyNames() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id, name FROM core.company WHERE tenant_id = :tenantId")
            .setParameter("tenantId", context.tenantId()).getResultList();
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
        }
        return result;
    }

    private Employee ownedEmployee(Long id) {
        return repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new AccessDeniedException("Karyawan tidak ditemukan atau di luar company aktif."));
    }

    // ==================== KARYAWAN ====================
    @GetMapping
    public String list(HttpSession session, Model model) {
        commonModel(session, model, "Employees");
        model.addAttribute("employees", repo.findByTenantIdAndCompanyIdOrderByIdDesc(
            context.tenantId(), context.companyId()));
        model.addAttribute("companyNames", companyNames());
        return "ui/hr/employee_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        commonModel(session, model, "New Employee");
        Employee emp = new Employee();
        emp.setTenantId(context.tenantId());
        emp.setCompanyId(context.companyId());
        emp.setStatus("ACTIVE");
        model.addAttribute("emp", emp);
        model.addAttribute("companies", allowedCompanies(session));
        model.addAttribute("isNew", true);
        return "ui/hr/employee_form";
    }

    @PostMapping
    public String save(@ModelAttribute Employee emp, HttpSession session, RedirectAttributes ra) {
        try {
            validateCompany(session, emp.getCompanyId() != null ? emp.getCompanyId() : context.companyId());
            emp.setTenantId(context.tenantId());
            if (emp.getCompanyId() == null) emp.setCompanyId(context.companyId());
            if (emp.getStatus() == null) emp.setStatus("ACTIVE");
            repo.save(emp);
            ra.addFlashAttribute("success", "Karyawan berhasil ditambahkan.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/hr/employees";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        commonModel(session, model, "Edit Employee");
        model.addAttribute("emp", ownedEmployee(id));
        model.addAttribute("companies", allowedCompanies(session));
        model.addAttribute("isNew", false);
        return "ui/hr/employee_form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Employee emp,
                         HttpSession session, RedirectAttributes ra) {
        try {
            validateCompany(session, emp.getCompanyId());
            Employee existing = ownedEmployee(id);
            existing.setNip(emp.getNip());
            existing.setName(emp.getName());
            existing.setDepartment(emp.getDepartment());
            existing.setPosition(emp.getPosition());
            existing.setStatus(emp.getStatus());
            existing.setCompanyId(emp.getCompanyId());
            repo.save(existing);
            ra.addFlashAttribute("success", "Data Karyawan berhasil diubah.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/hr/employees";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            repo.delete(ownedEmployee(id));
            ra.addFlashAttribute("success", "Karyawan berhasil dihapus.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menghapus karyawan: " + e.getMessage());
        }
        return "redirect:/ui/hr/employees";
    }

    // ==================== GAJI KARYAWAN ====================
    @GetMapping("/{id}/salary")
    public String editSalary(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        commonModel(session, model, "Setting Gaji Karyawan");
        try {
            Employee emp = ownedEmployee(id);
            EmployeeSalary sal = salRepo.findById(id).orElse(new EmployeeSalary());
            model.addAttribute("emp", emp);
            model.addAttribute("sal", sal);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/ui/hr/employees";
        }
        return "ui/hr/salary_form";
    }

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
            ownedEmployee(id);
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
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menyimpan gaji: " + e.getMessage());
        }
        return "redirect:/ui/hr/employees";
    }
}
