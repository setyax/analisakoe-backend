package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.entity.CashBankTrx;
import id.analisakoe.fin.repository.ApPaymentRepository;
import id.analisakoe.fin.repository.ArCollectionRepository;
import id.analisakoe.fin.repository.BankAccountRepository;
import id.analisakoe.fin.repository.CashBankTrxRepository;
import id.analisakoe.fin.service.FinanceService;
import id.analisakoe.md.repository.CustomerRepository;
import id.analisakoe.md.repository.SupplierRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/ui/finance")
public class UiFinanceController {

    private final BankAccountRepository bankRepo;
    private final CashBankTrxRepository cashRepo;
    private final ArCollectionRepository arRepo;
    private final ApPaymentRepository apRepo;
    private final CustomerRepository custRepo;
    private final SupplierRepository supRepo;
    private final FinanceService finService;
    private final MenuService menuService;

    public UiFinanceController(BankAccountRepository bankRepo, CashBankTrxRepository cashRepo,
                               ArCollectionRepository arRepo, ApPaymentRepository apRepo,
                               CustomerRepository custRepo, SupplierRepository supRepo,
                               FinanceService finService, MenuService menuService) {
        this.bankRepo = bankRepo; this.cashRepo = cashRepo;
        this.arRepo = arRepo; this.apRepo = apRepo;
        this.custRepo = custRepo; this.supRepo = supRepo;
        this.finService = finService; this.menuService = menuService;
    }

    private void addCommonData(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    // ==================== BANK ====================
    @GetMapping("/banks")
    public String listBanks(HttpSession session, Model model) {
        addCommonData(session, model, "Bank Accounts");
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        return "ui/finance/banks";
    }

    // ==================== CASH & BANK ====================
    @GetMapping("/cashbank")
    public String listCashBank(HttpSession session, Model model) {
        addCommonData(session, model, "Cash & Bank Transactions");
        model.addAttribute("trxs", cashRepo.findAllByOrderByDocDateDesc());
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        return "ui/finance/cashbank_list";
    }

    @GetMapping("/cashbank/new")
    public String newCashBank(HttpSession session, Model model) {
        addCommonData(session, model, "New Cash & Bank Transaction");
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/cashbank_form";
    }

    @PostMapping("/cashbank")
    public String saveCashBank(@RequestParam LocalDate docDate, @RequestParam String trxType,
                               @RequestParam Long bankId, @RequestParam BigDecimal amount,
                               @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            CashBankTrx t = new CashBankTrx();
            t.setTenantId(1L); t.setCompanyId(1L); t.setStatus("POSTED");
            t.setDocNo("CB-" + System.currentTimeMillis());
            t.setDocDate(docDate); t.setTrxType(trxType); t.setBankId(bankId);
            t.setAmount(amount); t.setRemarks(remarks);
            cashRepo.save(t);
            ra.addFlashAttribute("success", "Transaksi Kas/Bank berhasil disimpan.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/cashbank";
    }

    // ==================== AR COLLECTION ====================
    @GetMapping("/ar")
    public String listAr(HttpSession session, Model model) {
        addCommonData(session, model, "AR Collections");
        model.addAttribute("collections", arRepo.findAllByOrderByDocDateDesc());
        return "ui/finance/ar_list";
    }

    @GetMapping("/ar/new")
    public String newAr(HttpSession session, Model model) {
        addCommonData(session, model, "New AR Collection");
        model.addAttribute("customers", custRepo.findByTenantIdAndDeletedAtIsNull(1L, PageRequest.of(0, 100)).getContent());
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/ar_form";
    }

    @PostMapping("/ar")
    public String saveAr(@RequestParam LocalDate docDate, @RequestParam Long customerId,
                         @RequestParam Long bankId, @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            finService.saveArCollection(docDate, customerId, bankId, amount, remarks);
            ra.addFlashAttribute("success", "Pelunasan Piutang berhasil dicatat.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/ar";
    }

    // ==================== AP PAYMENT ====================
    @GetMapping("/ap")
    public String listAp(HttpSession session, Model model) {
        addCommonData(session, model, "AP Payments");
        model.addAttribute("payments", apRepo.findAllByOrderByDocDateDesc());
        return "ui/finance/ap_list";
    }

    @GetMapping("/ap/new")
    public String newAp(HttpSession session, Model model) {
        addCommonData(session, model, "New AP Payment");
        model.addAttribute("suppliers", supRepo.findByTenantIdAndDeletedAtIsNull(1L, PageRequest.of(0, 100)).getContent());
        model.addAttribute("banks", bankRepo.findAllByOrderByNameAsc());
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/ap_form";
    }

    @PostMapping("/ap")
    public String saveAp(@RequestParam LocalDate docDate, @RequestParam Long supplierId,
                         @RequestParam Long bankId, @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            finService.saveApPayment(docDate, supplierId, bankId, amount, remarks);
            ra.addFlashAttribute("success", "Pelunasan Hutang berhasil dicatat.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/ap";
    }

    // ==================== BATCH POSTING ====================
    @GetMapping("/batch-posting")
    public String batchPosting(HttpSession session, Model model) {
        addCommonData(session, model, "Batch Posting");
        var ars = arRepo.findAllByOrderByDocDateDesc().stream().filter(a -> "DRAFT".equals(a.getStatus())).toList();
        var aps = apRepo.findAllByOrderByDocDateDesc().stream().filter(a -> "DRAFT".equals(a.getStatus())).toList();
        
        model.addAttribute("draftArs", ars);
        model.addAttribute("draftAps", aps);
        return "ui/finance/batch_posting";
    }

    @PostMapping("/batch-posting/post")
    public String processBatchPost(@RequestParam(value = "arIds", required = false) List<Long> arIds,
                                   @RequestParam(value = "apIds", required = false) List<Long> apIds,
                                   RedirectAttributes ra) {
        try {
            int total = 0;
            if (arIds != null) total += finService.batchPostAr(arIds);
            if (apIds != null) total += finService.batchPostAp(apIds);
            
            ra.addFlashAttribute("success", total + " transaksi berhasil di-Posting.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/ui/finance/batch-posting";
    }
}