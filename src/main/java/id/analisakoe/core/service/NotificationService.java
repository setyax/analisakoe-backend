package id.analisakoe.core.service;

import id.analisakoe.core.entity.Notification;
import id.analisakoe.core.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    @PersistenceContext
    private EntityManager em;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /**
     * Kirim notifikasi ke semua user ACTIVE dalam tenant, kecuali user yang melakukan aksi.
     * Aman dipanggil di dalam try-catch agar gagal notifikasi tidak menggagalkan transaksi utama.
     */
    @Transactional
    public void notifyAllUsers(Long tenantId, Long excludeUserId, String excludeUsername,
                               String type, String title, String message, String link) {
        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery(
                "SELECT id FROM core.app_user WHERE tenant_id = :t AND status = 'ACTIVE' " +
                "AND (:excludeId IS NULL OR id <> :excludeId)")
            .setParameter("t", tenantId)
            .setParameter("excludeId", excludeUserId)
            .getResultList();

        for (Number uid : ids) {
            Notification n = new Notification();
            n.setTenantId(tenantId);
            n.setUserId(uid.longValue());
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message);
            n.setLink(link);
            n.setActor(excludeUsername);
            repo.save(n);
        }
    }

    public long unreadCount(Long userId) {
        return repo.countByUserIdAndIsReadFalse(userId);
    }

    public List<Notification> latest(Long userId) {
        return repo.findTop30ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        repo.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setIsRead(true);
                n.setReadAt(OffsetDateTime.now());
                repo.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> unread = repo.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        OffsetDateTime now = OffsetDateTime.now();
        for (Notification n : unread) {
            n.setIsRead(true);
            n.setReadAt(now);
        }
        repo.saveAll(unread);
    }
}
