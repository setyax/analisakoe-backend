package id.analisakoe.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Facade untuk injection ke service/controller tanpa mengakses ThreadLocal langsung.
 *
 * <p>Mode warn (default): bila context kosong, nilai lama dipakai sebagai fallback
 * dan dicatat di log. Mode strict: context kosong melempar exception.</p>
 */
@Service
public class ContextService {

    private static final Logger log = LoggerFactory.getLogger(ContextService.class);

    /** Fallback sementara selama masa transisi (Fase 4 akan dihapus). */
    private static final Long LEGACY_TENANT_ID = 1L;
    private static final Long LEGACY_COMPANY_ID = 11L;

    @Value("${app.context.strict:false}")
    private boolean strict;

    @Value("${app.context.warn-on-fallback:true}")
    private boolean warnOnFallback;

    public RequestContext context() {
        return ContextHolder.get();
    }

    /** Tenant aktif dari session (UI) atau JWT (REST). */
    public Long tenantId() {
        return resolve("tenant", LEGACY_TENANT_ID);
    }

    /** Company/cabang aktif dari session (UI) atau JWT (REST). */
    public Long companyId() {
        return resolve("company", LEGACY_COMPANY_ID);
    }

    public Long userId() {
        RequestContext ctx = ContextHolder.get();
        return ctx == null ? null : ctx.userId();
    }

    private Long resolve(String name, Long legacy) {
        RequestContext ctx = ContextHolder.get();
        Long value = switch (name) {
            case "tenant" -> ctx == null ? null : ctx.tenantId();
            case "company" -> ctx == null ? null : ctx.companyId();
            default -> null;
        };

        if (value != null) return value;

        if (strict) {
            throw new IllegalStateException(
                name.substring(0, 1).toUpperCase() + name.substring(1)
                    + " context tidak tersedia untuk request ini"
            );
        }
        if (warnOnFallback) {
            log.warn("Context {} kosong; memakai fallback lama {} (mode warn)", name, legacy);
        }
        return legacy;
    }
}
