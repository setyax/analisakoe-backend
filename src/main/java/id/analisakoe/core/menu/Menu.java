package id.analisakoe.core.menu;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "menu", schema = "core")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private String path;

    private String icon;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_header", nullable = false)
    private Boolean isHeader;

    @Column(name = "required_permission", length = 80)
    private String requiredPermission;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getPath() { return path; }
    public String getIcon() { return icon; }
    public Integer getSortOrder() { return sortOrder; }
    public Boolean getIsHeader() { return isHeader; }
    public String getRequiredPermission() { return requiredPermission; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}