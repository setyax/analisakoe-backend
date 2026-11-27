package id.analisakoe.core.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification", schema = "core")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(length = 500)
    private String link;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(length = 64)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { this.userId = v; }
    public String getType() { return type; } public void setType(String v) { this.type = v; }
    public String getTitle() { return title; } public void setTitle(String v) { this.title = v; }
    public String getMessage() { return message; } public void setMessage(String v) { this.message = v; }
    public String getLink() { return link; } public void setLink(String v) { this.link = v; }
    public Boolean getIsRead() { return isRead; } public void setIsRead(Boolean v) { this.isRead = v; }
    public OffsetDateTime getReadAt() { return readAt; } public void setReadAt(OffsetDateTime v) { this.readAt = v; }
    public String getActor() { return actor; } public void setActor(String v) { this.actor = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
