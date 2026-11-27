package id.analisakoe.web.ui;

import id.analisakoe.auth.service.CompanyAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

@Controller
public class BranchController {

    private final CompanyAccessService companyAccessService;

    public BranchController(CompanyAccessService companyAccessService) {
        this.companyAccessService = companyAccessService;
    }

    @PostMapping("/ui/switch-branch")
    public String switchBranch(@RequestParam Long companyId,
                               HttpServletRequest request,
                               HttpSession session,
                               RedirectAttributes ra) {

        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user == null) {
            return "redirect:/ui/login";
        }

        String back = safeRedirect(request.getHeader("Referer"));

        if (!companyAccessService.isAllowed(user.getId(), user.getTenantId(), companyId)) {
            ra.addFlashAttribute("error", "Company/cabang ini tidak tersedia untuk akun Anda.");
            return "redirect:" + back;
        }

        user.setActiveCompanyId(companyId);
        String name = companyAccessService.companyName(user.getId(), user.getTenantId(), companyId);
        if (name != null) {
            user.setActiveCompanyName(name);
        }
        session.setAttribute(SessionInterceptor.SESSION_USER, user);

        return "redirect:" + back;
    }

    /** Terima hanya path internal agar Referer tidak bisa dipakai open redirect. */
    private String safeRedirect(String referer) {
        if (referer == null || referer.isBlank()) return "/ui/dashboard";
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            if (path == null) return "/ui/dashboard";
            if (path.startsWith("/api/ui/")) {
                path = path.substring(4); // buang "/api"
            }
            if (path.startsWith("/ui/")) {
                return path + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            }
        } catch (Exception ignored) {
            // Referer bukan URI valid -> fallback dashboard
        }
        return "/ui/dashboard";
    }
}
