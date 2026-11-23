package id.analisakoe.web.ui;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SessionUser {
    private Long id;
    private Long tenantId;
    private String username;
    private String fullName;
    private String accessToken;
    private String refreshToken;
    private Set<String> roles;
    private Set<String> permissions;

    // MULTI CABANG
    private Long activeCompanyId;
    private String activeCompanyName;
    private List<Map<String, Object>> allowedCompanies;

    public SessionUser() {}

    // Constructor lama dipertahankan agar kompatibel
    // dengan kode lain yang mungkin menggunakannya.
    public SessionUser(Long id, String username, String fullName,
                       String accessToken, String refreshToken,
                       Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.roles = roles;
        this.permissions = permissions;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public Long getActiveCompanyId() { return activeCompanyId; }
    public void setActiveCompanyId(Long activeCompanyId) {
        this.activeCompanyId = activeCompanyId;
    }

    public String getActiveCompanyName() { return activeCompanyName; }
    public void setActiveCompanyName(String activeCompanyName) {
        this.activeCompanyName = activeCompanyName;
    }

    public List<Map<String, Object>> getAllowedCompanies() {
        return allowedCompanies;
    }

    public void setAllowedCompanies(List<Map<String, Object>> allowedCompanies) {
        this.allowedCompanies = allowedCompanies;
    }
}