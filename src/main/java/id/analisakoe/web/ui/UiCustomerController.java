package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.dto.CustomerRequest;
import id.analisakoe.md.dto.CustomerResponse;
import id.analisakoe.md.repository.CurrencyRepository;
import id.analisakoe.md.service.CustomerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/ui/customers")
public class UiCustomerController {

    private final CustomerService service;
    private final CurrencyRepository currencyRepo;
    private final MenuService menuService;

    public UiCustomerController(CustomerService service, CurrencyRepository currencyRepo, MenuService menuService) {
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
        PageResponse<CustomerResponse> data = service.list(q, PageRequest.of(page, size, Sort.by("id").ascending()));
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Customers");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("customers", data);
        model.addAttribute("q", q);
        return "ui/customers/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "New Customer");
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        CustomerFormData f = new CustomerFormData();
        f.setStatus("ACTIVE");
        f.setCurrency("IDR");
        f.setCreditLimit(BigDecimal.ZERO);
        f.setPaymentTermDays((short) 0);
        model.addAttribute("customer", f);
        model.addAttribute("isNew", true);
        return "ui/customers/form";
    }

    @PostMapping
    public String create(@ModelAttribute("customer") CustomerFormData form,
                         HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.create(form.toRequest());
            ra.addFlashAttribute("success", "Customer berhasil dibuat");
            return "redirect:/ui/customers";
        } catch (Exception e) {
            return reRender(session, model, form, true, e.getMessage());
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        CustomerResponse c = service.get(id);
        CustomerFormData f = new CustomerFormData();
        f.setId(c.id()); f.setCode(c.code()); f.setName(c.name());
        f.setTaxId(c.taxId()); f.setCurrency(c.currency());
        f.setCreditLimit(c.creditLimit()); f.setPaymentTermDays(c.paymentTermDays());
        f.setStatus(c.status());

        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "Edit Customer");
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        model.addAttribute("customer", f);
        model.addAttribute("isNew", false);
        return "ui/customers/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("customer") CustomerFormData form,
                         HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.update(id, form.toRequest());
            ra.addFlashAttribute("success", "Customer berhasil diubah");
            return "redirect:/ui/customers";
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

    private String reRender(HttpSession session, Model model, CustomerFormData form, boolean isNew, String error) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", isNew ? "New Customer" : "Edit Customer");
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        model.addAttribute("customer", form);
        model.addAttribute("isNew", isNew);
        model.addAttribute("error", error);
        return "ui/customers/form";
    }

    public static class CustomerFormData {
        private Long id;
        private String code;
        private String name;
        private String taxId;
        private String currency;
        private BigDecimal creditLimit;
        private Short paymentTermDays;
        private String status;

        public CustomerRequest toRequest() {
            return new CustomerRequest(code, name, taxId, currency, creditLimit, paymentTermDays, status);
        }
        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public String getCode() { return code; } public void setCode(String v) { this.code = v; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
        public String getTaxId() { return taxId; } public void setTaxId(String v) { this.taxId = v; }
        public String getCurrency() { return currency; } public void setCurrency(String v) { this.currency = v; }
        public BigDecimal getCreditLimit() { return creditLimit; } public void setCreditLimit(BigDecimal v) { this.creditLimit = v; }
        public Short getPaymentTermDays() { return paymentTermDays; } public void setPaymentTermDays(Short v) { this.paymentTermDays = v; }
        public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    }
}