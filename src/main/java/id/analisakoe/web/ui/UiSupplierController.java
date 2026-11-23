package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.dto.SupplierRequest;
import id.analisakoe.md.dto.SupplierResponse;
import id.analisakoe.md.repository.CurrencyRepository;
import id.analisakoe.md.service.SupplierService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/suppliers")
public class UiSupplierController {

    private final SupplierService service;
    private final CurrencyRepository currencyRepo;
    private final MenuService menuService;

    public UiSupplierController(SupplierService service, CurrencyRepository currencyRepo, MenuService menuService) {
        this.service = service;
        this.currencyRepo = currencyRepo;
        this.menuService = menuService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        PageResponse<SupplierResponse> data = service.list(q, PageRequest.of(page, size, Sort.by("id").ascending()));
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Suppliers");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("suppliers", data);
        model.addAttribute("q", q);
        return "ui/suppliers/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "New Supplier");
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        SupplierFormData f = new SupplierFormData();
        f.setStatus("ACTIVE");
        f.setCurrency("IDR");
        f.setPaymentTermDays((short) 0);
        model.addAttribute("supplier", f);
        model.addAttribute("isNew", true);
        return "ui/suppliers/form";
    }

    @PostMapping
    public String create(@ModelAttribute("supplier") SupplierFormData form,
                         HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.create(form.toRequest());
            ra.addFlashAttribute("success", "Supplier berhasil dibuat");
            return "redirect:/ui/suppliers";
        } catch (Exception e) {
            return reRender(session, model, form, true, e.getMessage());
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        SupplierResponse s = service.get(id);
        SupplierFormData f = new SupplierFormData();
        f.setId(s.id()); f.setCode(s.code()); f.setName(s.name());
        f.setTaxId(s.taxId()); f.setCurrency(s.currency());
        f.setPaymentTermDays(s.paymentTermDays()); f.setStatus(s.status());

        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "Edit Supplier");
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        model.addAttribute("supplier", f);
        model.addAttribute("isNew", false);
        return "ui/suppliers/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("supplier") SupplierFormData form,
                         HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.update(id, form.toRequest());
            ra.addFlashAttribute("success", "Supplier berhasil diubah");
            return "redirect:/ui/suppliers";
        } catch (Exception e) {
            return reRender(session, model, form, false, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "";
    }

    private String reRender(HttpSession session, Model model, SupplierFormData form, boolean isNew, String error) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", isNew ? "New Supplier" : "Edit Supplier");
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        model.addAttribute("supplier", form);
        model.addAttribute("isNew", isNew);
        model.addAttribute("error", error);
        return "ui/suppliers/form";
    }

    public static class SupplierFormData {
        private Long id;
        private String code;
        private String name;
        private String taxId;
        private String currency;
        private Short paymentTermDays;
        private String status;

        public SupplierRequest toRequest() {
            return new SupplierRequest(code, name, taxId, currency, paymentTermDays, status);
        }
        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public String getCode() { return code; } public void setCode(String v) { this.code = v; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
        public String getTaxId() { return taxId; } public void setTaxId(String v) { this.taxId = v; }
        public String getCurrency() { return currency; } public void setCurrency(String v) { this.currency = v; }
        public Short getPaymentTermDays() { return paymentTermDays; } public void setPaymentTermDays(Short v) { this.paymentTermDays = v; }
        public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    }
}