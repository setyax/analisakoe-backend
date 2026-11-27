package id.analisakoe.web.ui;

import id.analisakoe.core.entity.Notification;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/notifications")
public class UiNotificationController {

    private final NotificationService notificationService;
    private final MenuService menuService;

    public UiNotificationController(NotificationService notificationService, MenuService menuService) {
        this.notificationService = notificationService;
        this.menuService = menuService;
    }

    // ============================================================
    // HALAMAN DAFTAR NOTIFIKASI
    // ============================================================
    @GetMapping
    public String page(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Notifikasi");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("items", notificationService.latest(user.getId()));
        model.addAttribute("unread", notificationService.unreadCount(user.getId()));
        return "ui/notifications";
    }

    // ============================================================
    // API UNTUK POLLING (JSON)
    // ============================================================
    @GetMapping("/api/list")
    @ResponseBody
    public Map<String, Object> apiList(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Map<String, Object> out = new HashMap<>();
        if (user == null) { out.put("error", "unauthenticated"); return out; }

        out.put("unread", notificationService.unreadCount(user.getId()));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM HH:mm");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Notification n : notificationService.latest(user.getId())) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("message", n.getMessage());
            m.put("link", n.getLink());
            m.put("isRead", Boolean.TRUE.equals(n.getIsRead()));
            m.put("actor", n.getActor());
            m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().format(fmt) : "");
            items.add(m);
        }
        out.put("items", items);
        return out;
    }

    // ============================================================
    // TANDAI DIBACA
    // ============================================================
    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        notificationService.markRead(id, user.getId());
        return "redirect:/ui/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        notificationService.markAllRead(user.getId());
        return "redirect:/ui/notifications";
    }

    @PostMapping("/api/{id}/read")
    @ResponseBody
    public Map<String, Object> apiMarkRead(@PathVariable Long id, HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        notificationService.markRead(id, user.getId());
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("unread", notificationService.unreadCount(user.getId()));
        return out;
    }

    @PostMapping("/api/read-all")
    @ResponseBody
    public Map<String, Object> apiMarkAllRead(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        notificationService.markAllRead(user.getId());
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("unread", 0);
        return out;
    }
}
