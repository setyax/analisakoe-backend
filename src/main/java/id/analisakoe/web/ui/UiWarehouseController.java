package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.dto.WarehouseRequest;
import id.analisakoe.md.dto.WarehouseResponse;
import id.analisakoe.md.service.WarehouseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/warehouses")
public class UiWarehouseController {

    private final WarehouseService service;
    private final MenuService menuService;

    public UiWarehouseController(WarehouseService service, MenuService menuService) {
        this.service = service;
        this.menuService = menuService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        PageResponse<WarehouseResponse> data = service.list(q, PageRequest.of(page, 20, Sort.by("code")));
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Warehouses");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("warehouses", data);
        model.addAttribute("q", q);
        return "ui/warehouses/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "New Warehouse");
        WarehouseFormData f = new WarehouseFormData();
        f.setStatus("ACTIVE");
        model.addAttribute("wh", f);
        model.addAttribute("isNew", true);
        return "ui/warehouses/form";
    }

    @PostMapping
    public String create(@ModelAttribute("wh") WarehouseFormData form, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.create(new WarehouseRequest(form.getCode(), form.getName(), form.getAddress(), form.getStatus()));
            ra.addFlashAttribute("success", "Warehouse berhasil dibuat");
            return "redirect:/ui/warehouses";
        } catch (Exception e) {
            model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
            model.addAttribute("pageTitle", "New Warehouse");
            model.addAttribute("wh", form);
            model.addAttribute("isNew", true);
            model.addAttribute("error", e.getMessage());
            return "ui/warehouses/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        WarehouseResponse w = service.get(id);
        WarehouseFormData f = new WarehouseFormData();
        f.setId(w.id()); f.setCode(w.code()); f.setName(w.name());
        f.setAddress(w.address()); f.setStatus(w.status());
        model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
        model.addAttribute("pageTitle", "Edit Warehouse");
        model.addAttribute("wh", f);
        model.addAttribute("isNew", false);
        return "ui/warehouses/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("wh") WarehouseFormData form, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            service.update(id, new WarehouseRequest(form.getCode(), form.getName(), form.getAddress(), form.getStatus()));
            ra.addFlashAttribute("success", "Warehouse berhasil diubah");
            return "redirect:/ui/warehouses";
        } catch (Exception e) {
            model.addAttribute("user", (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER));
            model.addAttribute("pageTitle", "Edit Warehouse");
            model.addAttribute("wh", form);
            model.addAttribute("isNew", false);
            model.addAttribute("error", e.getMessage());
            return "ui/warehouses/form";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public String delete(@PathVariable Long id) { service.delete(id); return ""; }

    public static class WarehouseFormData {
        private Long id; private String code; private String name; private String address; private String status;
        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public String getCode() { return code; } public void setCode(String v) { this.code = v; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
        public String getAddress() { return address; } public void setAddress(String v) { this.address = v; }
        public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    }
}