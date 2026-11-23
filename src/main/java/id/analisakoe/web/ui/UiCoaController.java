package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.dto.ChartOfAccountRequest;
import id.analisakoe.fin.dto.ChartOfAccountResponse;
import id.analisakoe.fin.service.ChartOfAccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/coa")
public class UiCoaController {

    private final ChartOfAccountService service;
    private final MenuService menuService;

    public UiCoaController(ChartOfAccountService service, MenuService menuService) {
        this.service = service;
        this.menuService = menuService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        PageResponse<ChartOfAccountResponse> data = service.list(q, PageRequest.of(page, 20, Sort.by("code")));
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Chart of Accounts");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("coas", data);
        model.addAttribute("q", q);
        return "ui/coa/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "New Account");
        model.addAttribute("parents", service.listHeaders());
        CoaFormData f = new CoaFormData();
        f.setStatus("ACTIVE"); f.setIsHeader(false); f.setAccountType("ASSET");
        model.addAttribute("coa", f);
        model.addAttribute("isNew", true);
        return "ui/coa/form";
    }

    @PostMapping
    public String create(@ModelAttribute("coa") CoaFormData form, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.create(form.toRequest());
            ra.addFlashAttribute("success", "Account berhasil dibuat");
            return "redirect:/ui/coa";
        } catch (Exception e) {
            model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
            model.addAttribute("pageTitle", "New Account");
            model.addAttribute("parents", service.listHeaders());
            model.addAttribute("coa", form);
            model.addAttribute("isNew", true);
            model.addAttribute("error", e.getMessage());
            return "ui/coa/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        ChartOfAccountResponse c = service.get(id);
        CoaFormData f = new CoaFormData();
        f.setId(c.id()); f.setCode(c.code()); f.setName(c.name()); f.setAccountType(c.accountType());
        f.setParentId(c.parentId()); f.setIsHeader(c.isHeader());
        f.setCurrency(c.currency()); f.setStatus(c.status());
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "Edit Account");
        model.addAttribute("parents", service.listHeaders());
        model.addAttribute("coa", f);
        model.addAttribute("isNew", false);
        return "ui/coa/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("coa") CoaFormData form, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.update(id, form.toRequest());
            ra.addFlashAttribute("success", "Account berhasil diubah");
            return "redirect:/ui/coa";
        } catch (Exception e) {
            model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
            model.addAttribute("pageTitle", "Edit Account");
            model.addAttribute("parents", service.listHeaders());
            model.addAttribute("coa", form);
            model.addAttribute("isNew", false);
            model.addAttribute("error", e.getMessage());
            return "ui/coa/form";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public String delete(@PathVariable Long id) { service.delete(id); return ""; }

    public static class CoaFormData {
        private Long id; private String code; private String name;
        private String accountType; private Long parentId;
        private Boolean isHeader; private String currency; private String status;

        public ChartOfAccountRequest toRequest() {
            return new ChartOfAccountRequest(code, name, accountType, parentId, isHeader, currency, status);
        }
        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public String getCode() { return code; } public void setCode(String v) { this.code = v; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
        public String getAccountType() { return accountType; } public void setAccountType(String v) { this.accountType = v; }
        public Long getParentId() { return parentId; } public void setParentId(Long v) { this.parentId = v; }
        public Boolean getIsHeader() { return isHeader; } public void setIsHeader(Boolean v) { this.isHeader = v; }
        public String getCurrency() { return currency; } public void setCurrency(String v) { this.currency = v; }
        public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    }
}