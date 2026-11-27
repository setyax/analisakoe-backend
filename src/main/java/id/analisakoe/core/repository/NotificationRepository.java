package id.analisakoe.core.repository;

import id.analisakoe.core.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    long countByUserIdAndIsReadFalse(Long userId);
    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);
}
