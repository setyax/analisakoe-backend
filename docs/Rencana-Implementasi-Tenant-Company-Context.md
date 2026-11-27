# Rencana Implementasi TenantContext + CompanyContext
AnalisaKoe ERP — Spring Boot 3.3.5 / Java 17 / PostgreSQL

## 1. Tujuan

Menggantikan seluruh hardcoded `tenantId = 1L` dan `companyId = 11L` dengan konteks request yang berasal dari:
- **UI (Thymeleaf):** `SessionUser` di `HttpSession` (`tenantId`, `activeCompanyId`)
- **REST (JWT):** claim `tenant` + claim `company` (hasil lookup `core.user_company` / `default_company_id`)

Hasil akhir:
1. Ganti cabang di header benar-benar mengisolasi data dan posting.
2. Semua dokumen, jurnal, stok, dan laporan otomatis memakai tenant/company aktif.
3. Ada pertahanan berlapis: context → repository/native query → service → database (RLS).
4. Bisa diaudit: `created_by`, `changed_by`, tenant/company tercatat saat mutasi.

---

## 2. Kondisi saat ini (baseline)

| Aspek | Implementasi saat ini | Masalah |
|---|---|---|
| Tenant | `UiLoginController.java:59,86` mengambil `resp.user().tenantId()` → `SessionUser.tenantId` | JWT sudah punya claim `tenant` (`JwtService.java:40`) tapi belum dipakai sebagai context |
| Company/cabang | `core.company`, relasi `core.user_company`, `default_company_id` di `core.app_user` | Banyak service memakai literal `11L` (hasil perubahan sementara) |
| Switch cabang | `BranchController.java:14-40` mengubah `activeCompanyId` di session | Tidak berpengaruh ke service yang hardcode; redirect memakai `Referer` |
| Session guard | `SessionInterceptor` + `WebConfig.addPathPatterns("/ui/**")` hanya cek session | Tidak ada autorisasi per aksi |
| Query | Campuran JPA + native query dengan `:tenantId`, `:compId` | Banyak yang masih `findByTenantId(1L)` tanpa company |
| Penomoran | `NumberRangeService.java:23-24` hardcode tenant `1`, unik per `(tenant, doc_type, period)` | Nomor dokumen belum per company/tahun |
| Periode | `FiscalPeriodGuardService.assertOpen(tenantId, companyId, ...)` | Tidak semua transaksi memanggil guard |
| DB | Koneksi sebagai `postgres` superuser | RLS akan bypass jika belum dibuat role aplikasi |
| Flyway | `spring.flyway.enabled: false` | Migrasi baru belum bisa jalan otomatis |

---

## 3. Arsitektur target

```
Request
  │
  ├─ UI  : HttpSession → SessionUser(tenantId, activeCompanyId, id)
  └─ REST: JWT claims tenant + company (+ lookup DB bila perlu)
             │
             ▼
      ContextFilter (populate ThreadLocal, selalu clear di finally)
             │
             ▼
      ContextHolder  ← dipakai service / controller / audit
             │
             ├─ JPA     : Hibernate @Filter (tenant, company)
             ├─ Native  : ScopedQueryHelper → wajib :tenantId / :companyId
             ├─ Service : context.tenantId() / context.companyId()
             └─ DB      : RLS SET LOCAL app.tenant_id / app.company_id
```

### Prinsip
- **Satu sumber kebenaran:** `ContextHolder`, bukan parameter yang dilempar-lempar.
- **Default deny:** mode strict → jika context tidak ada, request ditolak (`400/403`), bukan jatuh ke `1L`/`11L`.
- **Transisi bertahap:** mode `warn` sebelum `strict`, dengan log perbedaan antara context dan literal lama.

---

## 4. Komponen yang dibuat

### 4.1 `RequestContext` + `ContextHolder`

Lokasi: `src/main/java/id/analisakoe/context/`

```java
public record RequestContext(
    Long tenantId,
    Long companyId,
    Long userId,
    String source   // "UI" | "JWT" | "SYSTEM"
) {}

public final class ContextHolder {
    private static final ThreadLocal<RequestContext> CTX = new ThreadLocal<>();

    public static void set(RequestContext c) { CTX.set(c); }
    public static RequestContext get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }

    public static Long tenantId() {
        RequestContext c = CTX.get();
        if (c == null || c.tenantId() == null) throw new IllegalStateException("Tenant context kosong");
        return c.tenantId();
    }

    public static Long companyId() {
        RequestContext c = CTX.get();
        if (c == null || c.companyId() == null) throw new IllegalStateException("Company context kosong");
        return c.companyId();
    }
}
```

Tambahkan `ContextService` (bean) untuk memudahkan injection:

```java
@Service
public class ContextService {
    public Long tenantId()  { return ContextHolder.tenantId(); }
    public Long companyId() { return ContextHolder.companyId(); }
    public Long userId()    { return ContextHolder.get().userId(); }
}
```

### 4.2 `ContextFilter`

Lokasi: `src/main/java/id/analisakoe/context/ContextFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20) // setelah JwtAuthFilter, sebelum MVC
public class ContextFilter extends OncePerRequestFilter {

    @Value("${app.context.strict:false}") boolean strict;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        try {
            RequestContext ctx = null;

            // 1) UI: session
            if (path.startsWith("/ui/")) {
                HttpSession s = req.getSession(false);
                SessionUser u = s == null ? null : (SessionUser) s.getAttribute(SessionInterceptor.SESSION_USER);
                if (u != null) ctx = new RequestContext(
                    u.getTenantId(), u.getActiveCompanyId(), u.getId(), "UI");
            }
            // 2) REST: JWT (diproses JwtAuthFilter → SecurityContext)
            else if (SecurityContextHolder.getContext().getAuthentication() != null) {
                Claims claims = ...; // dari JwtService, simpan di request attribute di JwtAuthFilter
                ctx = new RequestContext(
                    ((Number) claims.get("tenant")).longValue(),
                    claims.get("company") != null ? ((Number) claims.get("company")).longValue() : null,
                    ((Number) claims.get("uid")).longValue(), "JWT");
            }

            ContextHolder.set(ctx);
            MDC.put("tenantId",  ctx != null ? String.valueOf(ctx.tenantId()) : "-");
            MDC.put("companyId", ctx != null ? String.valueOf(ctx.companyId()) : "-");

            if (strict && ctx == null && !isPublic(path)) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant/company context tidak ditemukan");
                return;
            }
            chain.doFilter(req, res);
        } finally {
            ContextHolder.clear();
            MDC.remove("tenantId");
            MDC.remove("companyId");
        }
    }
}
```

**Catatan penting:** `JwtAuthFilter` perlu menyimpan `Claims` ke `request.setAttribute("jwt.claims", claims)` agar `ContextFilter` tidak parse ulang token.

### 4.3 `ScopedQueryHelper` untuk native query

Karena `@Filter` Hibernate **tidak berlaku untuk native query**, semua native query wajib distandardisasi:

```java
public final class ScopedQueryHelper {
    public static void apply(Query<?> q) {
        q.setParameter("tenantId", ContextHolder.tenantId());
        q.setParameter("companyId", ContextHolder.companyId());
    }
}
```

Pemakaian:

```java
String sql = """
    SELECT id, doc_no FROM pur.purchase_order_hdr
    WHERE tenant_id = :tenantId AND company_id = :companyId AND status = 'OPEN'
    """;
var q = em.createNativeQuery(sql).setParameter("status", "OPEN");
ScopedQueryHelper.apply(q); // akan throw di mode strict jika lupa
```

Tambahkan ArchUnit/test statis: **dilarang** native query mengandung `tenant_id = 1` / `company_id = 11`.

### 4.4 `RunAs` untuk job/async

Scheduled job/async tidak punya request context. Sediakan wrapper:

```java
public static void runAs(Long tenantId, Long companyId, Long userId, Runnable task) {
    RequestContext prev = ContextHolder.get();
    try {
        ContextHolder.set(new RequestContext(tenantId, companyId, userId, "SYSTEM"));
        task.run();
    } finally {
        ContextHolder.set(prev);
    }
}
```

---

## 5. Perubahan pada JWT & switch company

### 5.1 `JwtService`
Tambahkan claim company:

```java
.claim("tenant", principal.getUser().getTenantId())
.claim("company", principal.getUser().getDefaultCompanyId()) // tambahkan field di AppUser jika belum ada
```

### 5.2 `JwtAuthFilter`
- Perbaiki bug: gunakan authority dari `UserDetails` (DB), bukan dari claim `auth` yang stale.
- Simpan `claims` ke request attribute untuk `ContextFilter`.
- Cek `user.isEnabled()` dan `isAccountNonLocked()`.

### 5.3 REST switch company
Endpoint baru:

```
POST /auth/switch-company  { "companyId": 9 }
```

Validasi:
- `companyId` harus ada di `core.user_company` untuk user & tenant tersebut.
- Status company `ACTIVE`.
- Response: access token baru dengan claim `company` terbaru.

### 5.4 UI switch company
`BranchController` tetap, tapi:
- Pindahkan validasi ke `CompanyAccessService.isAllowed(userId, tenantId, companyId)`.
- Ganti redirect `Referer` → allowlist path internal.
- Setelah switch, `ContextFilter` akan otomatis memakai company baru pada request berikutnya.

---

## 6. Perubahan service & controller (bertahap)

### 6.1 Pola lama → baru

**Lama**
```java
final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
final Long compId = (user != null && user.getActiveCompanyId() != null)
                  ? user.getActiveCompanyId() : 11L;
hdr.setTenantId(1L);
hdr.setCompanyId(11L);
```

**Baru**
```java
hdr.setTenantId(context.tenantId());
hdr.setCompanyId(context.companyId());
```

Controller UI cukup memanggil `context.tenantId()` / `context.companyId()`; tidak perlu lagi mengambil `SessionUser` kecuali untuk ditampilkan di view.

### 6.2 Urutan konversi modul (minimal risiko)

1. **Master data** (`md.*`): item, customer, supplier, UOM, warehouse, price list, exchange rate.
2. **Purchasing** (`pur.*`): PR → PO → GRN → PI → retur → 3-way match.
3. **Sales** (`sal.*`): quotation → SO → DO/GI → SI → retur → payment.
4. **Inventory** (`inv.*`, `StockValuationService`): stock in/out, transfer, adjustment, opname.
5. **Finance** (`fin.*`): COA, journal, AR/AP, cash bank, laporan.
6. **HR / tax / notification**: payroll, cash advance, Coretax, notifikasi ber-tenant.

Setiap modul: konversi → test → deploy → baru lanjut modul berikutnya.

### 6.3 Penomoran & periode (kerjakan bersama modul terkait)

- `NumberRangeService.generate(docType)` → pakai `(tenantId, companyId, year)` dari context.
- Ubah unik `core.number_range` menjadi `(tenant_id, company_id, doc_type, period)` via Flyway.
- `FiscalPeriodGuardService.assertOpen(date, docType, refId)` → tenant/company dari context; panggil di **semua** service posting (SI, payment, retur, adjustment, opname, cash bank).

---

## 7. Pertahanan database (RLS)

Lakukan setelah aplikasi stabil di mode strict.

### 7.1 Role aplikasi (wajib, karena `postgres` superuser bypass RLS)

```sql
CREATE ROLE analisakoe_app LOGIN PASSWORD '...';
GRANT CONNECT ON DATABASE analisakoe TO analisakoe_app;
GRANT USAGE ON SCHEMA core, md, pur, sal, inv, fin, hr TO analisakoe_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core, md, pur, sal, inv, fin, hr TO analisakoe_app;
```

### 7.2 Policy (contoh `md.customer`)

```sql
ALTER TABLE md.customer ENABLE ROW LEVEL SECURITY;
ALTER TABLE md.customer FORCE ROW LEVEL SECURITY;

CREATE POLICY customer_tenant_company ON md.customer
  USING (
    tenant_id  = current_setting('app.tenant_id', true)::bigint
    AND company_id = current_setting('app.company_id', true)::bigint
  )
  WITH CHECK (
    tenant_id  = current_setting('app.tenant_id', true)::bigint
    AND company_id = current_setting('app.company_id', true)::bigint
  );
```

Terapkan ke semua tabel yang punya `tenant_id`/`company_id` (generate SQL dari katalog).

### 7.3 Set setting per transaksi

Gunakan `TransactionSynchronizationManager.registerSynchronization()` di `ContextFilter` (atau Hibernate `StatementInspector`):

```sql
SET LOCAL app.tenant_id  = :tenantId;
SET LOCAL app.company_id = :companyId;
```

`SET LOCAL` aman karena ikut transaction dan otomatis hilang setelah commit/rollback.

---

## 8. Config baru

`application.yml`:

```yaml
app:
  context:
    strict: false        # true setelah masa transisi
    warn-on-fallback: true
  jwt:
    secret: ${APP_JWT_SECRET}

spring:
  flyway:
    enabled: true        # WAJIB sebelum migrasi V56+
    validate-on-migrate: true
```

Tambahkan `APP_JWT_SECRET` ke environment (docker-compose sudah punya `JWT_SECRET`, tapi belum dipetakan ke `app.jwt.secret`).

---

## 9. Tahapan implementasi (roadmap)

| Fase | Aktivitas | Exit criteria |
|---|---|---|
| **0 — Inventory** | Daftar semua literal tenant/company, native query tanpa filter, service yang belum pakai session | Laporan daftar lengkap + baseline test |
| **1 — Fondasi** | `RequestContext`, `ContextHolder`, `ContextFilter`, `ContextService`, `ScopedQueryHelper`, MDC | Context terisi di UI & REST; ThreadLocal bersih; log mismatch muncul |
| **2 — JWT & switch** | Claim `company`, `/auth/switch-company`, perbaiki authority stale, validasi `BranchController` | Switch company mengubah context di UI dan REST |
| **3 — Konversi modul** | Master → P2P → O2C → Inventory → Finance → HR (Fase 6.2) | Tiap modul lolos test isolasi |
| **4 — Strict mode** | `app.context.strict=true`, hapus fallback `1L`/`11L`, native query guard | Tidak ada literal tenant/company di source |
| **5 — DB RLS** | Role aplikasi + policy + `SET LOCAL` + Flyway ON | User company 11 tidak bisa SELECT data company 9 walau lewat SQL manual |
| **6 — Observability** | Audit trail pakai `context.userId()`, metrik context-missing, alert | Audit log berisi user/tenant/company; alert saat context gagal |

---

## 10. Testing

### Wajib
1. **Unit:** `ContextHolder` set/get/clear, `strict` melempar exception bila kosong.
2. **MockMvc:**
   - Login sebagai user company 11 → hanya lihat data company 11.
   - Switch ke company 9 → hasil berubah, data company 11 tidak muncul.
   - User company 11 POST dokumen → `company_id = 11` tersimpan.
3. **JWT:** token tanpa claim `company` ditolak di strict mode.
4. **Native query guard:** query tanpa `:tenantId`/`:companyId` gagal saat test.
5. **RLS (Testcontainers Postgres):** query langsung sebagai `analisakoe_app` dengan `app.company_id=11` tidak melihat baris company 9.
6. **Regresi finansial:** jurnal debit=kredit, HPP/COGS, reversal, dan saldo stok tetap benar setelah konversi.
7. **CI gate:** grep/ArchUnit melarang `1L`/`11L` sebagai tenant/company di `src/main/java`.

### Progres implementasi (27 Nov 2026)

- Fase 1–3 selesai: `RequestContext`, `ContextHolder`, `ContextFilter`, `ContextService`, claim JWT `company`, `/auth/switch-company`, `CompanyAccessService`, serta 5 service master data.
- Fase 4 (P2P) sebagian besar selesai:
  - **Purchase Request**: list, form, create, edit, view, update, confirm/unconfirm/void/delete, print, report, `/api/pr-available`, `/api/pr-details`, dan **My Requests** sudah memakai `context.tenantId()` + `context.companyId()` (baik query maupun parameter bound).
  - **Purchase Order**: list, master, create/edit/view/update, mutasi status, validasi qty PR, dan policy check sudah terfilter company.
  - **GRN / Invoice / RFQ / 3-way match**: daftar dokumen dan master sudah terfilter company;penulisan dokumen mengikuti company aktif atau company dokumen induk.
  - `NumberRangeService` memakai tenant dari context (entity `core.number_range` masih per-tenant, bukan per-company).
  - Retur pembelian: tenant literal `1` pada INSERT transaksi, header, dan jurnal diganti `context.tenantId()`.
- **Price list pembelian (27 Nov 2026)**: diselaraskan dengan pendekatan condition record SAP. `pur.price_list` kini punya `company_id`, `condition_type` (default `PB00`), dan `price_unit`; migrasi `V58__price_list_company_condition.sql` menambah unique condition key `(tenant, company, supplier, item, uom, currency, valid_from, valid_to, min_qty, condition_type)`. Semua query price list (form, simpan, hapus, wizard copy, `/api/get-price`, retur pembelian)sudah difilter tenant + company. Determinasi harga memakai scale terbesar dengan `min_qty <= qty` (bukan sekadar `ORDER BY min_qty DESC`). Baris PO menyimpan `price_source` (`PRICE_LIST`/`RFQ`/`MANUAL`), `condition_type`, dan `price_unit`.
- Migrasi `V58` sudah dijalankan manual via `psql` karena `spring.flyway.enabled=false`; file migrasi tetap disimpan untuk berikutnya.
- Verifikasi price list: user company 11 melihat 21 baris harga dan PR company 11; user company 9 melihat 0 baris dan harga tidak ditemukan. Uji scale: qty 1 → 450.000, qty 10 → 400.000. Data/user uji sudah dihapus.
- **Urutan determinasi + condition terpisah (27 Nov 2026)**: migrasi `V59__price_condition_sequence.sql` menambah `condition_source` (`CONTRACT`/`INFO_RECORD`/`RFQ`/`LAST_PO`/`MANUAL`), `condition_class` (`PRICE`/`DISCOUNT`/`SURCHARGE`/`TAX`), `rate`, `calculation_order`, serta tabel audit `pur.po_condition`. Urutan: kontrak → price list/info record → RFQ yang menang → PO terakhir → manual. Diskon/surcharge/pajak dihitung setelah harga dasar dan disimpan per baris PO. `/api/get-price` mengembalikan `basePrice`, `price` (setelah condition non-pajak), `taxRate`, dan daftar condition; form PO menampilkan ringkasan condition dan mengirim `basePrice` agar tidak double discount saat simpan.
- Verifikasi condition: diskon 10% + pajak 11% mengubah harga dasar 450.000 → 405.000 (`taxRate=11`); saat ada record CONTRACT 400.000, urutan memilih CONTRACT dan harga akhir 360.000. Simpan PO menghasilkan header `total=799.200`, `tax=79.200` untuk qty 2, dengan 3 baris `pur.po_condition` (PRICE CONTRACT, DISCOUNT, TAX). Data/user uji sudah dihapus.
- Verifikasi RFQ & LAST_PO (28 Nov 2026): kombinasi supplier 1 / item 2 / uom 1 (tanpa price list) dengan RFQ `WON` harga 300.000 dan PO `RECEIVED` harga 250.000 menghasilkan `priceSource=RFQ`, `price=300.000`. Setelah status RFQ diubah jadi `LOST`, determinasi jatuh ke `priceSource=LAST_PO`, `price=250.000`. Semua fixture dan user uji sudah dihapus; jumlah baris kembali ke 6 PO, 2 RFQ, 21 price list, 0 `po_condition`.
- Mode masih `warn` (`app.context.strict=false`); fallback transisi `1`/`11` masih ada di `ContextService`.
- Belum selesai: RLS/DB role, Flyway, audit trail, test automatis, konversi O2C/R2R, dan service-service P2P yang dormant (`PurchaseInvoiceService.createFromGrn`, `PurchaseReturnService.create`).

---

## 11. Risiko & mitigasi

| Risiko | Mitigasi |
|---|---|
| ThreadLocal bocor ke thread pool | `finally { ContextHolder.clear(); }` di filter; test async |
| Native query lolos dari `@Filter` | `ScopedQueryHelper` + static check + strict mode |
| Data lama company 1 vs user company 11 | Backfill via SQL `BEGIN; ... ROLLBACK;` dulu, baru commit setelah verifikasi |
| Flyway mati → migrasi tidak jalan | Aktifkan Flyway + `validate-on-migrate` di Fase 0/5 |
| Superuser bypass RLS | Buat role `analisakoe_app`, jangan pakai `postgres` di runtime |
| Job scheduled tanpa context | `ContextHolder.runAs(...)` |
| Switch cabang tapi token REST lama | `/auth/switch-company` mengeluarkan token baru; TTL access token pendek |
| User tidak punya company | Login sudah ditolak; pertahankan, jangan fallback ke 11 |

---

## 12. Definition of Done

- [ ] Tidak ada `tenantId = 1L` / `companyId = 11L` di `src/main/java`
- [ ] Semua native query transaksi memakai `:tenantId` dan `:companyId`
- [ ] Ganti cabang di UI langsung mengubah data yang tampil & company dokumen baru
- [ ] REST dengan JWT company 11 tidak bisa mengakses data company 9
- [ ] Audit log mencatat `user_id`, `tenant_id`, `company_id` untuk setiap mutasi
- [ ] Flyway aktif dan migrasi tervalidasi
- [ ] Test automatis alur P2P/O2C/R2R lolos di CI
- [ ] Role DB bukan superuser dan RLS aktif

---

## 13. File yang disentuh (ringkas)

**Baru**
- `context/RequestContext.java`
- `context/ContextHolder.java`
- `context/ContextFilter.java`
- `context/ContextService.java`
- `context/ScopedQueryHelper.java`
- `auth/CompanyAccessService.java`
- `db/migration/V56__number_range_company.sql`
- `db/migration/V57__app_role_and_rls.sql`

**Diubah**
- `auth/jwt/JwtService.java`
- `auth/jwt/JwtAuthFilter.java`
- `config/WebConfig.java`, `config/SecurityConfig.java`
- `web/ui/UiLoginController.java`, `web/ui/BranchController.java`
- `core/service/NumberRangeService.java`
- `fin/service/FiscalPeriodGuardService.java`, `JournalService.java`
- Seluruh service/controller pada Fase 6.2
- `src/main/resources/application.yml`
- `docker-compose.yml` (mapping `APP_JWT_SECRET`)
