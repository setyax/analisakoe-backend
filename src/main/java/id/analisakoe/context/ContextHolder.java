package id.analisakoe.context;

/**
 * Penyimpan konteks request berbasis ThreadLocal.
 *
 * <p>ContextFilter WAJIB memanggil {@link #clear()} di blok finally agar
 * tidak bocor ke thread pool Tomcat.</p>
 */
public final class ContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private ContextHolder() {}

    public static void set(RequestContext ctx) {
        CONTEXT.set(ctx);
    }

    public static RequestContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /** Tenant aktif. Melempar exception bila belum terinisialisasi. */
    public static Long tenantId() {
        RequestContext ctx = CONTEXT.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalStateException("Tenant context tidak tersedia untuk request ini");
        }
        return ctx.tenantId();
    }

    /** Company/cabang aktif. Melempar exception bila belum terinisialisasi. */
    public static Long companyId() {
        RequestContext ctx = CONTEXT.get();
        if (ctx == null || ctx.companyId() == null) {
            throw new IllegalStateException("Company context tidak tersedia untuk request ini");
        }
        return ctx.companyId();
    }

    /** User aktif; null diperbolehkan untuk endpoint publik. */
    public static Long userId() {
        RequestContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.userId();
    }

    /** Eksekusi task dengan konteks tertentu (untuk job/async). */
    public static void runAs(Long tenantId, Long companyId, Long userId, Runnable task) {
        RequestContext previous = CONTEXT.get();
        try {
            CONTEXT.set(new RequestContext(tenantId, companyId, userId, "SYSTEM"));
            task.run();
        } finally {
            CONTEXT.set(previous);
        }
    }
}
