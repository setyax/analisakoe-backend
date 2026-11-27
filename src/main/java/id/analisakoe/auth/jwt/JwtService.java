package id.analisakoe.auth.jwt;

import id.analisakoe.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    /** Request attribute tempat JwtAuthFilter menyimpan Claims untuk ContextFilter. */
    public static final String CLAIMS_REQUEST_ATTRIBUTE = "jwt.claims";

    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-token-ttl-seconds:900}") long ttlSeconds,
        @Value("${app.jwt.issuer:analisakoe}") String issuer
    ) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    /** Generate token dengan company default milik user. */
    public String generateToken(UserPrincipal principal) {
        return generateToken(principal, principal.getUser().getDefaultCompanyId());
    }

    /**
     * Generate token dengan company/cabang aktif yang sudah divalidasi.
     * Claim "company" dibaca ContextFilter sebagai CompanyContext untuk REST.
     */
    public String generateToken(UserPrincipal principal, Long companyId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlSeconds * 1000);

        List<String> authorities = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        return Jwts.builder()
            .subject(principal.getUsername())
            .issuer(issuer)
            .issuedAt(now)
            .expiration(exp)
            .claim("uid", principal.getUser().getId())
            .claim("tenant", principal.getUser().getTenantId())
            .claim("company", companyId)
            .claim("auth", authorities)
            .signWith(key)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public long getTtlSeconds() { return ttlSeconds; }
}