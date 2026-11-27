package id.analisakoe.core.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user", schema = "core")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Company/cabang aktif terakhir (dipakai sebagai claim company di JWT). */
    @Column(name = "default_company_id")
    private Long defaultCompanyId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 200)
    private String fullName;
    @Column(name = "employee_id") private Long employeeId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "must_change_pwd", nullable = false)
    private Boolean mustChangePwd;

    @Column(name = "failed_attempts", nullable = false)
    private Short failedAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_role",
        schema = "core",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (mustChangePwd == null) mustChangePwd = false;
        if (failedAttempts == null) failedAttempts = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public Long getDefaultCompanyId() { return defaultCompanyId; }
    public void setDefaultCompanyId(Long v) { this.defaultCompanyId = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public Long getEmployeeId() { return employeeId; } public void setEmployeeId(Long v) { this.employeeId = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Boolean getMustChangePwd() { return mustChangePwd; }
    public void setMustChangePwd(Boolean v) { this.mustChangePwd = v; }
    public Short getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Short v) { this.failedAttempts = v; }
    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(OffsetDateTime v) { this.lockedUntil = v; }
    public OffsetDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(OffsetDateTime v) { this.lastLogin = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> v) { this.roles = v; }
}