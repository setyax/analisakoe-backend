package id.analisakoe.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties props;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, RateLimitProperties.Endpoint> rulesByPattern = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (!props.isEnabled()) {
            log.info("Rate limiting DISABLED");
            return;
        }
        if (props.getEndpoints() == null || props.getEndpoints().isEmpty()) {
            log.info("Rate limiting: no endpoints configured");
            return;
        }
        for (var ep : props.getEndpoints()) {
            rulesByPattern.put(ep.getPattern(), ep);
            log.info("Rate limit rule: {} -> {} tokens / {} min (key: {})",
                ep.getPattern(), ep.getCapacity(), ep.getRefillMinutes(), ep.getKeyType());
        }
        log.info("Rate limiting ENABLED, {} rules", rulesByPattern.size());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }

        RateLimitProperties.Endpoint matched = matchRule(path);
        if (matched == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = computeKey(matched, request);
        String bucketKey = matched.getPattern() + "|" + key;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(matched));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long waitSec = probe.getNanosToWaitForRefill() / 1000000000L;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(Math.max(1, waitSec)));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            String msg = "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"Terlalu banyak percobaan. Coba lagi dalam " + Math.max(1, waitSec) + " detik.\"," +
                "\"path\":\"" + path + "\"," +
                "\"retryAfterSeconds\":" + Math.max(1, waitSec) + "}";
            response.getWriter().write(msg);

            log.warn("Rate limit EXCEEDED: path={} key={} retryAfter={}s", path, key, waitSec);
        }
    }

    private RateLimitProperties.Endpoint matchRule(String path) {
        for (var ep : rulesByPattern.values()) {
            if (antMatch(ep.getPattern(), path)) return ep;
        }
        return null;
    }

    /**
     * Simple Ant-style match.
     * Satu wildcard cocok dengan satu segmen, dua wildcard cocok dengan sisa path.
     * Contoh: /core/users/{id}/reset-password cocok dengan /core/users/123/reset-password
     */
    private static boolean antMatch(String pattern, String path) {
        if (pattern.contains("**")) {
            String prefix = pattern.substring(0, pattern.indexOf("**"));
            return path.startsWith(prefix);
        }
        String[] p = pattern.split("/");
        String[] a = path.split("/");
        if (p.length != a.length) return false;
        for (int i = 0; i < p.length; i++) {
            if (!p[i].equals("*") && !p[i].equals(a[i])) return false;
        }
        return true;
    }

    private String computeKey(RateLimitProperties.Endpoint ep, HttpServletRequest req) {
        if ("USER".equalsIgnoreCase(ep.getKeyType())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                return "u:" + auth.getName();
            }
        }
        return "ip:" + ClientIpResolver.resolve(req);
    }

    private Bucket buildBucket(RateLimitProperties.Endpoint ep) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(ep.getCapacity())
            .refillGreedy(ep.getRefillTokens(), Duration.ofMinutes(ep.getRefillMinutes()))
            .build();
        return Bucket.builder().addLimit(limit).build();
    }
}