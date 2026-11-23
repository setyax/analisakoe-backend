package id.analisakoe.web.ui;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BranchController {

    @PostMapping("/ui/switch-branch")
    public String switchBranch(@RequestParam Long companyId, 
                               HttpServletRequest request, 
                               HttpSession session) {
        
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        
        if (user != null && user.getAllowedCompanies() != null) {
            // VALIDASI: Apakah companyId yang diminta ada di daftar allowedCompanies?
            boolean isAllowed = user.getAllowedCompanies().stream()
                .anyMatch(c -> ((Number) c.get("id")).longValue() == companyId);

            if (isAllowed) {
                user.setActiveCompanyId(companyId);
                user.getAllowedCompanies().stream()
                    .filter(c -> ((Number) c.get("id")).longValue() == companyId)
                    .findFirst()
                    .ifPresent(c -> user.setActiveCompanyName((String) c.get("name")));
                    
                session.setAttribute(SessionInterceptor.SESSION_USER, user);
            }
        }

        // Kembali ke halaman tempat user berada
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/ui/dashboard");
    }
}