package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.entity.BankAccount;
import id.analisakoe.fin.repository.BankAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/ui/master/banks")
public class UiBankAccountController {

    @Autowired
    private ContextService context;

    private final BankAccountRepository repo;
    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public UiBankAccountController(BankAccountRepository repo, MenuService menuService) {
        this.repo = repo;
        this.menuService = menuService;
    }

    // ==================== LIST ====================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Bank Accounts");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        model.addAttribute("banks", repo.findByCompanyIdOrderByNameAsc(compId));
        return "ui/master/bank_list";
    }

    // ==================== FORM CREATE ====================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Bank Account");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("coas", em.createNativeQuery(
                "SELECT id, code || ' - ' || name FROM fin.chart_of_account WHERE is_header = false ORDER BY code")
                .getResultList());
        model.addAttribute("isNew", true);
        return "ui/master/bank_form";
    }

    // ==================== FORM EDIT ====================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Bank Account");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        try {
            var bank = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Bank tidak ditemukan"));
            model.addAttribute("bank", bank);
            model.addAttribute("coas", em.createNativeQuery(
                    "SELECT id, code || ' - ' || name FROM fin.chart_of_account WHERE is_header = false ORDER BY code")
                    .getResultList());
            model.addAttribute("isNew", false);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/ui/master/banks";
        }
        return "ui/master/bank_form";
    }

    // ==================== CREATE ====================
    @PostMapping
    public String save(@RequestParam String name,
                       @RequestParam(required = false) String accountNo,
                       @RequestParam(required = false) String bankCode,
                       @RequestParam(required = false) String currency,
                       @RequestParam(required = false) String branch,
                       @RequestParam(required = false) Long coaId,
                       @RequestParam(required = false) BigDecimal openingBalance,
                       HttpSession session, RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            Long compId = user.getActiveCompanyId();

            transactionTemplate.executeWithoutResult(tx -> {
                BankAccount b = new BankAccount();
                b.setTenantId(context.tenantId());
                b.setCompanyId(compId);
                fill(b, name, accountNo, bankCode, currency, branch, coaId, openingBalance);
                b.setStatus("ACTIVE");
                if (b.getCreatedAt() == null) b.setCreatedAt(java.time.OffsetDateTime.now());
                if (b.getCurrentBalance() == null) b.setCurrentBalance(b.getOpeningBalance() == null ? BigDecimal.ZERO : b.getOpeningBalance());
                repo.save(b);
            });
            ra.addFlashAttribute("success", "Bank Account \"" + name + "\" berhasil dibuat.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal simpan: " + rootMessage(e));
        }
        return "redirect:/ui/master/banks";
    }

    // ==================== UPDATE ====================
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String name,
                         @RequestParam(required = false) String accountNo,
                         @RequestParam(required = false) String bankCode,
                         @RequestParam(required = false) String currency,
                         @RequestParam(required = false) String branch,
                         @RequestParam(required = false) Long coaId,
                         @RequestParam(required = false) BigDecimal openingBalance,
                         @RequestParam(required = false) String status,
                         RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                BankAccount b = repo.findById(id).orElseThrow(() -> new IllegalStateException("Bank tidak ditemukan"));
                fill(b, name, accountNo, bankCode, currency, branch, coaId, openingBalance);
                if (status != null && (status.equals("ACTIVE") || status.equals("SUSPENDED"))) {
                    b.setStatus(status);
                }
                repo.save(b);
            });
            ra.addFlashAttribute("success", "Bank Account berhasil diupdate.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update: " + rootMessage(e));
        }
        return "redirect:/ui/master/banks";
    }

    // ==================== DELETE ====================
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                BankAccount b = repo.findById(id).orElseThrow(() -> new IllegalStateException("Bank tidak ditemukan"));

                int refs = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM fin.cash_bank_trx WHERE bank_id = :id")
                        .setParameter("id", id).getSingleResult()).intValue();
                refs += ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM hr.cash_advance WHERE bank_id = :id")
                        .setParameter("id", id).getSingleResult()).intValue();
                refs += ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM fin.ar_collection WHERE bank_id = :id")
                        .setParameter("id", id).getSingleResult()).intValue();
                refs += ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM fin.ap_payment WHERE bank_id = :id")
                        .setParameter("id", id).getSingleResult()).intValue();
                if (refs > 0)
                    throw new IllegalStateException("Bank ini sudah dipakai di " + refs +
                            " transaksi (Kas & Bank, Cash Advance, Payment, Collection). Ubah statusnya menjadi SUSPENDED, jangan dihapus.");

                repo.delete(b);
            });
            ra.addFlashAttribute("success", "Bank Account berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + rootMessage(e));
        }
        return "redirect:/ui/master/banks";
    }

    private void fill(BankAccount b, String name, String accountNo, String bankCode,
                      String currency, String branch, Long coaId, BigDecimal openingBalance) {
        b.setName(name);
        b.setAccountNo(accountNo);
        b.setBankCode(bankCode);
        b.setCurrency(currency != null && !currency.isEmpty() ? currency : "IDR");
        b.setBranch(branch);
        b.setCoaId(coaId);
        b.setOpeningBalance(openingBalance == null ? BigDecimal.ZERO : openingBalance);
    }

    private String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }
}
