package id.analisakoe.auth.token;

import id.analisakoe.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository repo;
    private final long ttlSeconds;

    public RefreshTokenService(
            RefreshTokenRepository repo,
            @Value("${app.jwt.refresh-token-ttl-seconds:604800}") long ttlSeconds) {
        this.repo = repo;
        this.ttlSeconds = ttlSeconds;
    }

    public long getTtlSeconds() { return ttlSeconds; }

    @Transactional
    public IssuedToken issue(Long userId, UUID familyId, String userAgent, String ip) {
        String plaintext = generatePlaintext();
        String hash = hash(plaintext);

        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash(hash);
        t.setFamilyId(familyId == null ? UUID.randomUUID() : familyId);
        t.setUserAgent(truncate(userAgent, 255));
        t.setIpAddress(ip);
        t.setExpiresAt(OffsetDateTime.now().plusSeconds(ttlSeconds));
        repo.save(t);

        return new IssuedToken(plaintext, t);
    }

    /**
     * Cari token berdasarkan plaintext. Return Optional.empty() jika token tidak ada / expired / revoked.
     * Throw BusinessException jika terdeteksi replay.
     */
    @Transactional
    public RefreshToken validateAndTouch(String plaintext) {
        String hash = hash(plaintext);
        Optional<RefreshToken> opt = repo.findByTokenHash(hash);
        if (opt.isEmpty()) return null;

        RefreshToken t = opt.get();

        if (t.getRevokedAt() != null) {
            // Token sudah di-revoke. Kalau ada replaced_by_hash, itu tanda ROTATION yang sudah dilakukan.
            // Pemakaian ulang token yang sudah dirotasi = indikasi token bocor. Revoke seluruh family.
            if (t.getReplacedByHash() != null) {
                repo.revokeFamily(t.getFamilyId(), "REPLAY_DETECTED", OffsetDateTime.now());
                throw new BusinessException("Refresh token sudah dipakai. Semua sesi family ini di-revoke.");
            }
            return null;
        }

        if (t.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return null;
        }

        return t;
    }

    @Transactional
    public void rotate(RefreshToken oldToken, String newHash) {
        oldToken.setRevokedAt(OffsetDateTime.now());
        oldToken.setRevokedReason("ROTATED");
        oldToken.setReplacedByHash(newHash);
        repo.save(oldToken);
    }

    @Transactional
    public void revoke(String plaintext, String reason) {
        String hash = hash(plaintext);
        repo.findByTokenHash(hash).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(OffsetDateTime.now());
                t.setRevokedReason(reason);
                repo.save(t);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        repo.revokeAllForUser(userId, reason, OffsetDateTime.now());
    }

    @Transactional
    public int cleanupExpired(int olderThanDays) {
        return repo.deleteExpiredBefore(OffsetDateTime.now().minusDays(olderThanDays));
    }

    public String hash(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    private String generatePlaintext() {
        byte[] buf = new byte[TOKEN_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record IssuedToken(String plaintext, RefreshToken entity) {}
}