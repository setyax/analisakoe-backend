package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/finance/assets")
public class UiFixedAssetController {

    @Autowired
    private ContextService context;

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiFixedAssetController(MenuService menuService) {
        this.menuService = menuService;
    }

    private SessionUser currentUser(HttpSession session) {
        return (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
    }

    private void base(Model model, SessionUser user, String title) {
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    // ======================= LIST + SEARCH =======================
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       HttpSession session, Model model) {
        base(model, currentUser(session), "Fixed Assets");

        List<Map<String, Object>> assets = new ArrayList<>();
        try {
            boolean hasKw     = keyword != null && !keyword.isBlank();
            boolean hasStatus = status  != null && !status.isBlank();

            StringBuilder sql = new StringBuilder(
                "SELECT id, asset_code, asset_name, category, acquisition_date, " +
                "acquisition_cost, useful_life_years, salvage_value, " +
                "accumulated_depreciation, book_value, status " +
                "FROM fin.asset_master WHERE tenant_id = :tenantId AND company_id = :compId");
            if (hasKw)     sql.append(" AND (LOWER(asset_code) LIKE :kw OR LOWER(asset_name) LIKE :kw OR LOWER(category) LIKE :kw)");
            if (hasStatus) sql.append(" AND status = :status");
            sql.append(" ORDER BY id DESC");

            var q = em.createNativeQuery(sql.toString())
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId());
            if (hasKw)     q.setParameter("kw", "%" + keyword.toLowerCase() + "%");
            if (hasStatus) q.setParameter("status", status);

            List<?> rows = q.getResultList();
            for (Object obj : rows) {
                Object[] row = (Object[]) obj;
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("code", row[1]);
                m.put("name", row[2]);
                m.put("category", row[3]);

                Object dateObj = row[4];
                String acqDateStr = "-";
                if (dateObj != null) {
                    try {
                        LocalDate ld;
                        if (dateObj instanceof java.sql.Date) ld = ((java.sql.Date) dateObj).toLocalDate();
                        else if (dateObj instanceof java.sql.Timestamp) ld = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                        else if (dateObj instanceof LocalDate) ld = (LocalDate) dateObj;
                        else ld = LocalDate.parse(dateObj.toString());
                        acqDateStr = ld.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception ex) {
                        acqDateStr = dateObj.toString();
                    }
                }
                m.put("acqDate", acqDateStr);
                m.put("acqCost", row[5]);
                m.put("usefulLife", row[6]);
                m.put("salvage", row[7]);
                m.put("accumDep", row[8]);
                m.put("bookValue", row[9]);
                m.put("status", row[10]);
                assets.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data: " + e.getMessage());
        }
        model.addAttribute("assets", assets);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        return "ui/finance/asset_list";
    }

    // ======================= FORM CREATE =======================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        base(model, currentUser(session), "New Fixed Asset");
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/asset_form";
    }

    // ======================= FORM EDIT =======================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        base(model, currentUser(session), "Edit Fixed Asset");
        model.addAttribute("today", LocalDate.now());
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                "SELECT id, asset_code, asset_name, category, acquisition_date, " +
                "acquisition_cost, useful_life_years, salvage_value, status " +
                "FROM fin.asset_master WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();

            Map<String, Object> a = new HashMap<>();
            a.put("id", row[0]);
            a.put("code", row[1]);
            a.put("name", row[2]);
            a.put("category", row[3]);

            Object dateObj = row[4];
            LocalDate acqDate = LocalDate.now();
            if (dateObj instanceof java.sql.Date) acqDate = ((java.sql.Date) dateObj).toLocalDate();
            else if (dateObj instanceof java.sql.Timestamp) acqDate = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
            else if (dateObj instanceof LocalDate) acqDate = (LocalDate) dateObj;
            a.put("acqDate", acqDate);
            a.put("acqCost", row[5]);
            a.put("usefulLife", row[6]);
            a.put("salvage", row[7]);
            a.put("status", row[8]);

            model.addAttribute("asset", a);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Aset tidak ditemukan: " + e.getMessage());
            return "redirect:/ui/finance/assets";
        }
        return "ui/finance/asset_form";
    }

    // ======================= CREATE =======================
    @PostMapping
    @Transactional
    public String save(@RequestParam String assetCode, @RequestParam String assetName,
                       @RequestParam String category, @RequestParam LocalDate acquisitionDate,
                       @RequestParam BigDecimal acquisitionCost, @RequestParam Integer usefulLifeYears,
                       @RequestParam BigDecimal salvageValue, HttpSession session, RedirectAttributes ra) {
        SessionUser user = currentUser(session);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            em.createNativeQuery(
                "INSERT INTO fin.asset_master (tenant_id, company_id, asset_code, asset_name, category, " +
                "acquisition_date, acquisition_cost, useful_life_years, salvage_value, " +
                "accumulated_depreciation, book_value, status) " +
                "VALUES (:tenantId, :compId, :code, :name, :cat, :acqDate, :acqCost, :life, :salvage, 0, :acqCost, 'ACTIVE')")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("code", assetCode)
                .setParameter("name", assetName)
                .setParameter("cat", category)
                .setParameter("acqDate", acquisitionDate)
                .setParameter("acqCost", acquisitionCost)
                .setParameter("life", usefulLifeYears)
                .setParameter("salvage", salvageValue)
                .executeUpdate();
            ra.addFlashAttribute("success", "Aset berhasil ditambahkan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menyimpan aset: " + e.getMessage());
        }
        return "redirect:/ui/finance/assets";
    }

    // ======================= UPDATE =======================
    @PostMapping("/{id}")
    @Transactional
    public String update(@PathVariable Long id,
                         @RequestParam String assetCode, @RequestParam String assetName,
                         @RequestParam String category, @RequestParam LocalDate acquisitionDate,
                         @RequestParam BigDecimal acquisitionCost, @RequestParam Integer usefulLifeYears,
                         @RequestParam BigDecimal salvageValue,
                         @RequestParam(required = false, defaultValue = "ACTIVE") String status,
                         RedirectAttributes ra) {
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            int n = em.createNativeQuery(
                "UPDATE fin.asset_master SET asset_code=:code, asset_name=:name, category=:cat, " +
                "acquisition_date=:acqDate, acquisition_cost=:acqCost, useful_life_years=:life, " +
                "salvage_value=:salvage, status=:status WHERE id=:id AND tenant_id=:tenantId AND company_id=:compId")
                .setParameter("code", assetCode)
                .setParameter("name", assetName)
                .setParameter("cat", category)
                .setParameter("acqDate", acquisitionDate)
                .setParameter("acqCost", acquisitionCost)
                .setParameter("life", usefulLifeYears)
                .setParameter("salvage", salvageValue)
                .setParameter("status", status)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .executeUpdate();
            ra.addFlashAttribute(n > 0 ? "success" : "error",
                                 n > 0 ? "Aset berhasil diupdate." : "Aset tidak ditemukan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update aset: " + e.getMessage());
        }
        return "redirect:/ui/finance/assets";
    }

    // ======================= DELETE =======================
    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            em.createNativeQuery("DELETE FROM fin.asset_depreciation_log WHERE asset_id = :id "
              + "AND asset_id IN (SELECT id FROM fin.asset_master WHERE tenant_id = :tenantId AND company_id = :compId)")
              .setParameter("id", id).setParameter("tenantId", tenantId).setParameter("compId", compId)
              .executeUpdate();
            int n = em.createNativeQuery("DELETE FROM fin.asset_master WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                      .setParameter("id", id).setParameter("tenantId", tenantId).setParameter("compId", compId)
                      .executeUpdate();
            ra.addFlashAttribute(n > 0 ? "success" : "error",
                                 n > 0 ? "Aset berhasil dihapus." : "Aset tidak ditemukan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus aset: " + e.getMessage());
        }
        return "redirect:/ui/finance/assets";
    }

    // ======================= RUN DEPRECIATION =======================
    @PostMapping("/run-depreciation")
    @Transactional
    public String runDepreciation(@RequestParam String period, RedirectAttributes ra) {
        try {
            int count = 0;
            List<?> rows = em.createNativeQuery(
                "SELECT id, acquisition_cost, useful_life_years, salvage_value FROM fin.asset_master "
                + "WHERE status = 'ACTIVE' AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();

            for (Object obj : rows) {
                Object[] asset = (Object[]) obj;
                Long assetId = ((Number) asset[0]).longValue();
                BigDecimal cost = (BigDecimal) asset[1];
                Integer lifeYears = (Integer) asset[2];
                BigDecimal salvage = asset[3] != null ? (BigDecimal) asset[3] : BigDecimal.ZERO;

                List<?> exists = em.createNativeQuery(
                    "SELECT id FROM fin.asset_depreciation_log WHERE asset_id = :id AND period = :period")
                    .setParameter("id", assetId).setParameter("period", period).getResultList();

                if (exists.isEmpty()) {
                    int lifeMonths = lifeYears * 12;
                    BigDecimal depAmount = cost.subtract(salvage)
                        .divide(new BigDecimal(lifeMonths), 2, RoundingMode.HALF_UP);
                    em.createNativeQuery(
                        "INSERT INTO fin.asset_depreciation_log (asset_id, period, depreciation_amount, status) " +
                        "VALUES (:id, :period, :amount, 'CONFIRMED')")
                        .setParameter("id", assetId)
                        .setParameter("period", period)
                        .setParameter("amount", depAmount)
                        .executeUpdate();
                    count++;
                }
            }
            ra.addFlashAttribute("success", "Proses depresiasi periode " + period + " selesai. " + count + " aset diproses.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memproses depresiasi: " + e.getMessage());
        }
        return "redirect:/ui/finance/assets";
    }

    // ======================= LOCAL FALLBACK =======================
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, RedirectAttributes ra) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        ra.addFlashAttribute("error", "Error: " + rootCause.getMessage());
        return "redirect:/ui/finance/assets";
    }
}
