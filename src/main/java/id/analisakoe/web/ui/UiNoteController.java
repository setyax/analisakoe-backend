package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.repository.NoteRepository;
import id.analisakoe.fin.service.NoteService;
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

@Controller
@RequestMapping("/ui/finance/notes")
public class UiNoteController {

    @Autowired
    private ContextService context;
    private final NoteService service;
    private final NoteRepository repo;
    private final CustomerRepository custRepo;
    private final SupplierRepository supRepo;
    private final MenuService menuService;

    public UiNoteController(NoteService service, NoteRepository repo, CustomerRepository custRepo, SupplierRepository supRepo, MenuService menuService) {
        this.service = service; this.repo = repo; this.custRepo = custRepo; this.supRepo = supRepo; this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Debit / Credit Notes");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("notes", repo.findAllByOrderByDocDateDesc());
        return "ui/finance/note_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Note");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("customers", custRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("suppliers", supRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/note_form";
    }

    @PostMapping
    public String save(@RequestParam LocalDate docDate, @RequestParam String noteType, @RequestParam Long partnerId,
                       @RequestParam BigDecimal totalAmount, @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            service.create(docDate, noteType, partnerId, totalAmount, remarks);
            ra.addFlashAttribute("success", "Note berhasil dibuat (DRAFT).");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/notes";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Note");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("customers", custRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("suppliers", supRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        try {
            var note = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Note tidak ditemukan"));
            if (!"DRAFT".equals(note.getStatus())) {
                ra.addFlashAttribute("error", "Hanya Note berstatus DRAFT yang dapat diedit.");
                return "redirect:/ui/finance/notes";
            }
            model.addAttribute("note", note);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/ui/finance/notes";
        }
        return "ui/finance/note_form";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @RequestParam LocalDate docDate, @RequestParam String noteType,
                         @RequestParam Long partnerId, @RequestParam BigDecimal totalAmount,
                         @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            service.update(id, docDate, noteType, partnerId, totalAmount, remarks);
            ra.addFlashAttribute("success", "Note berhasil diperbarui.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/notes";
    }

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.post(id);
            ra.addFlashAttribute("success", "Note berhasil di-Posting ke Jurnal.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/notes";
    }

    @PostMapping("/{id}/unpost")
    public String unpost(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.unpost(id);
            ra.addFlashAttribute("success", "Note berhasil di-unpost. Jurnal posting dihapus, kembali ke DRAFT.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/notes";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("success", "Note berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/notes";
    }
}