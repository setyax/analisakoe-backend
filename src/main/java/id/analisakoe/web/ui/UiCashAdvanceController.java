package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.repository.BankAccountRepository;
import id.analisakoe.hr.entity.CashAdvanceExpense;
import id.analisakoe.hr.repository.CashAdvanceExpenseRepository;
import id.analisakoe.hr.repository.CashAdvanceRepository;
import id.analisakoe.hr.repository.EmployeeRepository;
import id.analisakoe.hr.service.CashAdvanceService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui/hr/cash-advance")
public class UiCashAdvanceController {

    private final CashAdvanceService service;
    private final CashAdvanceRepository repo;
    private final EmployeeRepository empRepo;
    private final BankAccountRepository bankRepo;
    private final CashAdvanceExpenseRepository expenseRepo;
    private final MenuService menuService;

    public UiCashAdvanceController(CashAdvanceService service, 
                                   CashAdvanceRepository repo, 
                                   EmployeeRepository empRepo, 
                                   BankAccountRepository bankRepo, 
                                   CashAdvanceExpenseRepository expenseRepo, 
                                   MenuService menuService) {
        this.service = service; 
        this.repo = repo; 
        this.empRepo = empRepo; 
        this.bankRepo = bankRepo; 
        this.expenseRepo = expenseRepo;
        this.menuService = menuService;
    }

    // ==================== LIST ====================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Cash Advances");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        
        // Filter berdasarkan Cabang (Company)
        model.addAttribute("advances", repo.findByCompanyIdOrderByDocDateDesc(compId));

        // Map untuk Karyawan
        Map<Long, id.analisakoe.hr.entity.Employee> employeeMap = empRepo.findAll().stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(id.analisakoe.hr.entity.Employee::getId, emp -> emp, (a, b) -> a));
        model.addAttribute("employeeMap", employeeMap);

        // Map untuk Bank
        Map<Long, id.analisakoe.fin.entity.BankAccount> bankMap = bankRepo.findAll().stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(id.analisakoe.fin.entity.BankAccount::getId, bank -> bank, (a, b) -> a));
        model.addAttribute("bankMap", bankMap);

        return "ui/hr/cash_advance_list";
    }

    // ==================== FORM CREATE ====================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Cash Advance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("employees", empRepo.findByTenantIdAndStatus(1L, "ACTIVE"));
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", true);
        return "ui/hr/cash_advance_form";
    }

    // ==================== PROSES SIMPAN (CREATE) ====================
    @PostMapping
    public String save(@RequestParam LocalDate docDate, 
                       @RequestParam Long employeeId, 
                       @RequestParam Long bankId,
                       @RequestParam BigDecimal amount, 
                       @RequestParam(required = false) String remarks,
                       HttpSession session, 
                       RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            Long compId = user.getActiveCompanyId(); // Ambil ID Cabang

            // Panggil service dengan parameter compId
            service.create(compId, docDate, employeeId, bankId, amount, remarks);
            ra.addFlashAttribute("success", "Kas Bon berhasil dibuat (DRAFT).");
        } catch (Exception e) { 
            ra.addFlashAttribute("error", e.getMessage()); 
        }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== FORM EDIT ====================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Cash Advance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        id.analisakoe.hr.entity.CashAdvance ca = repo.findById(id).orElseThrow();
        if (!"DRAFT".equals(ca.getStatus())) {
            ra.addFlashAttribute("error", "Hanya Kas Bon berstatus DRAFT yang bisa diedit.");
            return "redirect:/ui/hr/cash-advance";
        }

        model.addAttribute("ca", ca);
        model.addAttribute("employees", empRepo.findByTenantIdAndStatus(1L, "ACTIVE"));
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        model.addAttribute("isNew", false);
        return "ui/hr/cash_advance_form";
    }

    // ==================== PROSES UPDATE ====================
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, 
                         @RequestParam LocalDate docDate, 
                         @RequestParam Long employeeId, 
                         @RequestParam Long bankId, 
                         @RequestParam BigDecimal amount, 
                         @RequestParam(required = false) String remarks, 
                         RedirectAttributes ra) {
        try {
            id.analisakoe.hr.entity.CashAdvance ca = repo.findById(id).orElseThrow();
            if (!"DRAFT".equals(ca.getStatus())) {
                throw new Exception("Hanya Kas Bon berstatus DRAFT yang bisa diedit.");
            }
            ca.setDocDate(docDate);
            ca.setEmployeeId(employeeId);
            ca.setBankId(bankId);
            ca.setAmount(amount);
            ca.setRemarks(remarks);
            repo.save(ca);
            ra.addFlashAttribute("success", "Kas Bon berhasil diupdate.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== PROSES HAPUS ====================
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            id.analisakoe.hr.entity.CashAdvance ca = repo.findById(id).orElseThrow();
            if (!"DRAFT".equals(ca.getStatus())) {
                throw new Exception("Hanya Kas Bon berstatus DRAFT yang bisa dihapus.");
            }
            repo.deleteById(id);
            ra.addFlashAttribute("success", "Kas Bon berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== CAIRKAN / POST ====================
    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.post(id);
            ra.addFlashAttribute("success", "Kas Bon berhasil dicairkan dan dijurnal.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== BATAL CAIR (UNPOST) ====================
    @PostMapping("/{id}/unpost")
    public String unpost(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.unpost(id);
            ra.addFlashAttribute("success", "Pencairan Kas Bon dibatalkan. Jurnal & kas keluar dihapus, kembali ke DRAFT.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== BATAL LPJ (UNSETTLE) ====================
    @PostMapping("/{id}/unpost-settle")
    public String unpostSettle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.unpostSettle(id);
            ra.addFlashAttribute("success", "LPJ Kas Bon dibatalkan. Jurnal LPJ & kas masuk sisa dihapus, kembali ke POSTED.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== FORM LPJ (SETTLE) ====================
    @GetMapping("/{id}/settle")
    public String settleForm(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "LPJ Kas Bon");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        
        id.analisakoe.hr.entity.CashAdvance ca = repo.findById(id).orElseThrow();
        model.addAttribute("ca", ca);
        
        id.analisakoe.hr.entity.Employee emp = empRepo.findById(ca.getEmployeeId()).orElse(null);
        model.addAttribute("emp", emp);
        
        model.addAttribute("details", expenseRepo.findByCashAdvanceId(id));
        model.addAttribute("today", LocalDate.now());
        return "ui/hr/cash_advance_settle";
    }

    // ==================== PROSES LPJ (SETTLE) ====================
    @PostMapping("/{id}/settle")
    public String settle(@PathVariable Long id, 
                         @RequestParam LocalDate settleDate,
                         @RequestParam BigDecimal returnAmount,
                         @RequestParam(required = false) String remarks,
                         @RequestParam("desc") List<String> descriptions,
                         @RequestParam("amt") List<BigDecimal> amounts,
                         RedirectAttributes ra) {
        try {
            BigDecimal totalExpense = BigDecimal.ZERO;
            for (BigDecimal amt : amounts) {
                if (amt != null) totalExpense = totalExpense.add(amt);
            }

            expenseRepo.deleteAll(expenseRepo.findByCashAdvanceId(id));
            for (int i = 0; i < descriptions.size(); i++) {
                if (descriptions.get(i) != null && !descriptions.get(i).isEmpty() && amounts.get(i) != null) {
                    CashAdvanceExpense exp = new CashAdvanceExpense();
                    exp.setCashAdvanceId(id);
                    exp.setDescription(descriptions.get(i));
                    exp.setAmount(amounts.get(i));
                    expenseRepo.save(exp);
                }
            }

            service.settle(id, settleDate, totalExpense, returnAmount, remarks);
            ra.addFlashAttribute("success", "LPJ berhasil disimpan. Piutang Karyawan telah lunas.");
        } catch (Exception e) { 
            ra.addFlashAttribute("error", e.getMessage()); 
        }
        return "redirect:/ui/hr/cash-advance";
    }

    // ==================== PRINT ====================
    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        
        id.analisakoe.hr.entity.CashAdvance ca = repo.findById(id).orElseThrow();
        model.addAttribute("ca", ca);
        
        id.analisakoe.hr.entity.Employee emp = empRepo.findById(ca.getEmployeeId()).orElse(null);
        model.addAttribute("emp", emp);
        
        id.analisakoe.fin.entity.BankAccount bank = ca.getBankId() != null ? bankRepo.findById(ca.getBankId()).orElse(null) : null;
        model.addAttribute("bank", bank);
        
        return "ui/hr/cash_advance_print";
    }
}