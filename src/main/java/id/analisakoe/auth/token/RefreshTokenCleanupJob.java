package id.analisakoe.auth.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private final RefreshTokenService service;

    public RefreshTokenCleanupJob(RefreshTokenService service) {
        this.service = service;
    }

    // Setiap hari jam 02:30
    @Scheduled(cron = "0 30 2 * * *")
    public void cleanup() {
        try {
            int n = service.cleanupExpired(30);
            if (n > 0) log.info("Cleaned up {} expired refresh tokens", n);
        } catch (Exception e) {
            log.warn("Refresh token cleanup failed: {}", e.getMessage());
        }
    }
}