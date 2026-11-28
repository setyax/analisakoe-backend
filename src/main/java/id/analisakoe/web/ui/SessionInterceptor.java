package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SessionInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER = "user";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SESSION_USER);

        if (user == null) {
            // HTMX request: gunakan HX-Redirect agar browser client redirect
            if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
                response.setHeader("HX-Redirect", request.getContextPath() + "/ui/login");
                response.setStatus(200);
                return false;
            }
            // Redirect biasa
            String redirect = request.getRequestURI();
            if (request.getQueryString() != null) {
                redirect += "?" + request.getQueryString();
            }

            // AUTO-FIX: strip context-path dari redirect (mencegah /api/api/)
            String ctxPath = request.getContextPath();
            if (ctxPath != null && !ctxPath.isEmpty() && redirect.startsWith(ctxPath)) {
                redirect = redirect.substring(ctxPath.length());
            }
            if (!redirect.startsWith("/")) {
                redirect = "/" + redirect;
            }

            String encoded = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/ui/login?redirect=" + encoded);
            return false;
        }

        // Akun demo bersifat read-only: semua mutasi via UI ditolak.
        String method = request.getMethod();
        if (user.getRoles() != null && user.getRoles().contains("DEMO")
                && !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Akun demo hanya bisa melihat data (read-only).");
            return false;
        }
        return true;
    }
}