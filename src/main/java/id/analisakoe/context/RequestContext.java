package id.analisakoe.context;

/**
 * Konteks tenant/company/user untuk satu request.
 *
 * <p>Dipopulasikan oleh {@link ContextFilter} dari session (UI) atau JWT (REST).
 * Disimpan di ThreadLocal oleh {@link ContextHolder} dan wajib dibersihkan
 * di akhir request.</p>
 */
public record RequestContext(
        Long tenantId,
        Long companyId,
        Long userId,
        String source
) {

    public static RequestContext ui(Long tenantId, Long companyId, Long userId) {
        return new RequestContext(tenantId, companyId, userId, "UI");
    }

    public static RequestContext jwt(Long tenantId, Long companyId, Long userId) {
        return new RequestContext(tenantId, companyId, userId, "JWT");
    }

    public static RequestContext system(Long tenantId, Long companyId, Long userId) {
        return new RequestContext(tenantId, companyId, userId, "SYSTEM");
    }

    public boolean hasTenant() { return tenantId != null; }
    public boolean hasCompany() { return companyId != null; }
}
