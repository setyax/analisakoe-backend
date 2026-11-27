package id.analisakoe.auth.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Satu tempat untuk mengecek company/cabang yang boleh diakses user.
 * Dipakai oleh:
 * - AuthService (claim company di JWT + /auth/switch-company)
 * - BranchController (switch branch UI)
 *
 * <p>Sumber kebenaran: core.user_company + core.app_user.default_company_id.</p>
 */
@Service
@Transactional(readOnly = true)
public class CompanyAccessService {

    private final EntityManager em;

    public CompanyAccessService(EntityManager em) {
        this.em = em;
    }

    /** Daftar company aktif yang di-assign ke user, urut nama. */
    public List<Map<String, Object>> allowedCompanies(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) return List.of();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT c.id, c.name
                FROM core.company c
                JOIN core.user_company uc ON uc.company_id = c.id
                WHERE uc.user_id = :userId
                  AND c.tenant_id = :tenantId
                  AND c.status = 'ACTIVE'
                ORDER BY c.name
                """)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(Map.of(
                    "id", ((Number) row[0]).longValue(),
                    "name", String.valueOf(row[1])
            ));
        }
        return result;
    }

    /**
     * Company yang dipakai saat login/refresh:
     * default_company_id jika masih valid, jika tidak company pertama yang di-assign.
     */
    public Long resolveDefaultCompanyId(Long userId, Long tenantId) {
        List<Map<String, Object>> allowed = allowedCompanies(userId, tenantId);
        if (allowed.isEmpty()) return null;

        Long preferred = null;
        try {
            Object value = em.createNativeQuery("""
                    SELECT default_company_id
                    FROM core.app_user
                    WHERE id = :userId AND tenant_id = :tenantId
                    """)
                    .setParameter("userId", userId)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            if (value != null) preferred = ((Number) value).longValue();
        } catch (Exception ignored) {
            // default null / user tidak ketemu -> gunakan company pertama
        }

        for (Map<String, Object> c : allowed) {
            Object id = c.get("id");
            if (preferred != null && id instanceof Number n && preferred == n.longValue()) {
                return preferred;
            }
        }
        return ((Number) allowed.get(0).get("id")).longValue();
    }

    /** Cek companyId benar-benar milik user & tenant, dan statusnya ACTIVE. */
    public boolean isAllowed(Long userId, Long tenantId, Long companyId) {
        if (userId == null || tenantId == null || companyId == null) return false;
        Object total = em.createNativeQuery("""
                SELECT COUNT(*)
                FROM core.company c
                JOIN core.user_company uc ON uc.company_id = c.id
                WHERE uc.user_id = :userId
                  AND c.tenant_id = :tenantId
                  AND c.id = :companyId
                  AND c.status = 'ACTIVE'
                """)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .setParameter("companyId", companyId)
                .getSingleResult();
        return total != null && ((Number) total).longValue() > 0;
    }

    public String companyName(Long userId, Long tenantId, Long companyId) {
        try {
            Object value = em.createNativeQuery("""
                    SELECT c.name
                    FROM core.company c
                    JOIN core.user_company uc ON uc.company_id = c.id
                    WHERE uc.user_id = :userId
                      AND c.tenant_id = :tenantId
                      AND c.id = :companyId
                      AND c.status = 'ACTIVE'
                    """)
                    .setParameter("userId", userId)
                    .setParameter("tenantId", tenantId)
                    .setParameter("companyId", companyId)
                    .getSingleResult();
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Simpan company aktif sebagai default user.
     * Ini membuat refresh token berikutnya tetap memakai company yang dipilih.
     */
    @Transactional
    public void setDefaultCompany(Long userId, Long tenantId, Long companyId) {
        em.createNativeQuery("""
                UPDATE core.app_user
                SET default_company_id = :companyId, updated_at = NOW()
                WHERE id = :userId AND tenant_id = :tenantId
                """)
                .setParameter("companyId", companyId)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .executeUpdate();
    }
}
