package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.dto.UomRequest;
import id.analisakoe.md.dto.UomResponse;
import id.analisakoe.md.service.UomService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/uoms")
public class UiUomController {

    private final UomService service;
    private final MenuService menuService;

    public UiUomController(UomService service, MenuService menuService) {
        this.service = service;
        this.menuService = menuService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        PageResponse<UomResponse> data = service.list(q, PageRequest.of(page, 20, Sort.by("code")));
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Units of Measure");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("uoms", data);
        model.addAttribute("q", q);
        return "ui/uoms/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "New UOM");
        model.addAttribute("uom", new UomFormData());
        model.addAttribute("isNew", true);
        return "ui/uoms/form";
    }

    @PostMapping
    public String create(@ModelAttribute("uom") UomFormData form, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.create(new UomRequest(form.getCode(), form.getName()));
            ra.addFlashAttribute("success", "UOM berhasil dibuat");
            return "redirect:/ui/uoms";
        } catch (Exception e) {
            model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
            model.addAttribute("pageTitle", "New UOM");
            model.addAttribute("uom", form);
            model.addAttribute("isNew", true);
            model.addAttribute("error", e.getMessage());
            return "ui/uoms/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        UomResponse u = service.get(id);
        UomFormData f = new UomFormData();
        f.setId(u.id()); f.setCode(u.code()); f.setName(u.name());
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "Edit UOM");
        model.addAttribute("uom", f);
        model.addAttribute("isNew", false);
        return "ui/uoms/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("uom") UomFormData form, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.update(id, new UomRequest(form.getCode(), form.getName()));
            ra.addFlashAttribute("success", "UOM berhasil diubah");
            return "redirect:/ui/uoms";
        } catch (Exception e) {
            model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
            model.addAttribute("pageTitle", "Edit UOM");
            model.addAttribute("uom", form);
            model.addAttribute("isNew", false);
            model.addAttribute("error", e.getMessage());
            return "ui/uoms/form";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public String delete(@PathVariable Long id) { service.delete(id); return ""; }

    public static class UomFormData {
        private Long id; private String code; private String name;
        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public String getCode() { return code; } public void setCode(String v) { this.code = v; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
    }
}