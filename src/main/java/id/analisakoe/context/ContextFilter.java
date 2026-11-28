package id.analisakoe.context;

import id.analisakoe.auth.jwt.JwtService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Mengisi {@link ContextHolder} dari session (UI) atau JWT (REST).
 *
 * <p>Mode warn (default): context yang tidak ditemukan atau tidak cocok dengan
 * fallback lama HANYA dicatat, request tetap diteruskan. Mode strict
 * ({@code app.context.strict=true}) akan menolak request tanpa context.</p>
 */
@Component
public class ContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ContextFilter.class);

    /** Fallback sementara yang masih dipakai service lama. */
    private static final Long LEGACY_TENANT_ID = 1L;
    private static final Long LEGACY_COMPANY_ID = 11L;

    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/ui", "/ui/", "/ui/login", "/ui/login/", "/ui/logout", "/ui/logout/",
            "/auth/login", "/auth/refresh",
            "/ping", "/error", "/favicon.ico"
    );

    private static final Set<String> PUBLIC_PREFIX = Set.of(
            "/css/", "/js/", "/images/"
    );

    @Value("${app.context.strict:false}")
    private boolean strict;

    @Value("${app.context.warn-on-fallback:true}")
    private boolean warnOnFallback;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = stripContextPath(request);
        RequestContext ctx = resolve(path, request);

        ContextHolder.set(ctx);
        putMdc(ctx);

        try {
            if (strict && !isPublic(path) && !isUsable(ctx)) {
                log.warn("STRICT: menolak {} karena tenant/company context tidak lengkap (source={})",
                        path, ctx == null ? "-" : ctx.source());
                reject(path, request, response);
                return;
            }

            if (warnOnFallback && ctx != null) {
                warnIfLegacyMismatch(path, ctx);
            }

            chain.doFilter(request, response);
        } finally {
            ContextHolder.clear();
            clearMdc();
        }
    }

    private RequestContext resolve(String path, HttpServletRequest request) {
        // 1) UI — dari SessionUser di HttpSession
        if (isUiPath(path)) {
            HttpSession session = request.getSession(false);
            SessionUser user = session == null ? null
                    : (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);

            if (user == null) {
                if (!isPublic(path) && warnOnFallback) {
                    log.warn("WARN: {} tanpa SessionUser — tenant/company belum bisa ditentukan", path);
                }
                return null;
            }
            return RequestContext.ui(user.getTenantId(), user.getActiveCompanyId(), user.getId());
        }

        // 2) REST — dari Claims yang disimpan JwtAuthFilter
        Claims claims = (Claims) request.getAttribute(JwtService.CLAIMS_REQUEST_ATTRIBUTE);
        if (claims != null) {
            Long tenantId = number(claims.get("tenant"));
            Long companyId = number(claims.get("company"));
            Long userId = number(claims.get("uid"));

            if (tenantId == null && warnOnFallback) {
                log.warn("WARN: {} JWT tidak memiliki claim tenant", path);
            }
            if (companyId == null && warnOnFallback && !isPublic(path)) {
                log.warn("WARN: {} JWT tidak memiliki claim company — service masih pakai fallback {}",
                        path, LEGACY_COMPANY_ID);
            }
            return RequestContext.jwt(tenantId, companyId, userId);
        }

        // 3) Endpoint publik / tanpa autentikasi
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !isPublic(path) && warnOnFallback) {
            log.warn("WARN: {} terautentikasi sebagai {} tetapi tidak ada JWT claims", path, auth.getName());
        }
        return null;
    }

    private void warnIfLegacyMismatch(String path, RequestContext ctx) {
        if (ctx.tenantId() != null && !LEGACY_TENANT_ID.equals(ctx.tenantId())) {
            log.warn("WARN: {} tenant aktif={} berbeda dari fallback lama={}",
                    path, ctx.tenantId(), LEGACY_TENANT_ID);
        }
        if (ctx.companyId() != null && !LEGACY_COMPANY_ID.equals(ctx.companyId())) {
            log.warn("WARN: {} company aktif={} berbeda dari fallback lama={}. "
                            + "Transaksi masih berpotensi masuk ke company {} sampai konversi service selesai.",
                    path, ctx.companyId(), LEGACY_COMPANY_ID, LEGACY_COMPANY_ID);
        }
    }

    /**
     * Strict hanya boleh menolak request yang memang sudah terautentikasi tetapi
     * context-nya tidak lengkap. Request UI tanpa session di-redirect ke login,
     * request REST tanpa JWT dijawab 401 (bukan 400).
     */
    private void reject(String path, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String ctx = request.getContextPath();
        String prefix = ctx == null ? "" : ctx;

        if (isUiPath(path)) {
            String login = prefix + "/ui/login";
            if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
                response.setHeader("HX-Redirect", login);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            response.sendRedirect(login);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Tenant/company context tidak ditemukan. Silakan login ulang.\","
                        + "\"path\":\"" + path + "\"}"
        );
    }

    private boolean isUiPath(String path) {
        return path.equals("/ui") || path.startsWith("/ui/");
    }

    private boolean isUsable(RequestContext ctx) {
        return ctx != null && ctx.hasTenant() && ctx.hasCompany();
    }

    private boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) return true;
        return PUBLIC_PREFIX.stream().anyMatch(path::startsWith);
    }

    private String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }

    private Long number(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void putMdc(RequestContext ctx) {
        MDC.put("tenantId", ctx != null && ctx.tenantId() != null ? String.valueOf(ctx.tenantId()) : "-");
        MDC.put("companyId", ctx != null && ctx.companyId() != null ? String.valueOf(ctx.companyId()) : "-");
        MDC.put("userId", ctx != null && ctx.userId() != null ? String.valueOf(ctx.userId()) : "-");
    }

    private void clearMdc() {
        MDC.remove("tenantId");
        MDC.remove("companyId");
        MDC.remove("userId");
    }
}
