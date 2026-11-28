package id.analisakoe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Akun demo bersifat read-only untuk seluruh API.
 * UI sudah ditangani oleh SessionInterceptor; filter ini melindungi endpoint REST
 * yang bisa dipanggil langsung (termasuk yang tanpa @PreAuthorize).
 */
@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean demo = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_DEMO".equals(a.getAuthority()));

        String method = request.getMethod();
        if (demo && !"GET".equalsIgnoreCase(method)
                 && !"HEAD".equalsIgnoreCase(method)
                 && !"OPTIONS".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\","
                + "\"message\":\"Akun demo hanya bisa melihat data (read-only).\","
                + "\"path\":\"" + request.getRequestURI() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx))
            ? uri.substring(ctx.length()) : uri;

        return path.startsWith("/ui/")
            || path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/images/")
            || path.equals("/favicon.ico")
            || path.equals("/ping")
            || path.equals("/auth/login")
            || path.equals("/auth/refresh");
    }
}
