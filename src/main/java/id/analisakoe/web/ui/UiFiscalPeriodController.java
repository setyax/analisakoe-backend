package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/ui/finance/fiscal-periods")
public class UiFiscalPeriodController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiFiscalPeriodController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Fiscal Periods");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery(
            "SELECT id, year, month, start_date, end_date, status FROM fin.fiscal_period "
            + "WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY year DESC, month DESC")
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .getResultList();
        model.addAttribute("items", items);
        return "ui/finance/fiscal_period_list";
    }

    @GetMapping("/generate")
    public String generateForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Generate Fiscal Periods");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("currentYear", LocalDate.now().getYear());
        return "ui/finance/fiscal_period_generate";
    }

    @Transactional
    @PostMapping("/generate")
    public String generate(@RequestParam Integer year, HttpSession session, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        try {
            int created = 0;
            for (int m = 1; m <= 12; m++) {
                Object existing = em.createNativeQuery(
                    "SELECT COUNT(*) FROM fin.fiscal_period WHERE tenant_id=:tenantId "
                    + "AND company_id=:compId AND year=:yr AND month=:mn")
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .setParameter("yr", year).setParameter("mn", m).getSingleResult();
                if (((Number) existing).longValue() == 0) {
                    LocalDate start = LocalDate.of(year, m, 1);
                    LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                    em.createNativeQuery("INSERT INTO fin.fiscal_period (tenant_id, company_id, year, month, start_date, end_date, status) VALUES (:tenantId, :compId, :yr, :mn, :start, :end, 'OPEN')")
                      .setParameter("compId", compId).setParameter("yr", year).setParameter("mn", m)
                      .setParameter("start", start).setParameter("end", end).setParameter("tenantId", context.tenantId()).executeUpdate();
                    created++;
                }
            }
            ra.addFlashAttribute("success", created + " periode berhasil dibuat untuk tahun " + year);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal generate: " + e.getMessage());
        }
        return "redirect:/ui/finance/fiscal-periods";
    }

    @Transactional
    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        try {
            em.createNativeQuery("UPDATE fin.fiscal_period SET status='CLOSED', closed_at=now(), closed_by=:uid "
              + "WHERE id=:id AND tenant_id=:tenantId AND company_id=:compId")
              .setParameter("uid", user.getId()).setParameter("id", id)
              .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
              .executeUpdate();
            ra.addFlashAttribute("success", "Periode berhasil ditutup.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal tutup: " + e.getMessage());
        }
        return "redirect:/ui/finance/fiscal-periods";
    }

    @Transactional
    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE fin.fiscal_period SET status='OPEN', closed_at=NULL, closed_by=NULL "
              + "WHERE id=:id AND tenant_id=:tenantId AND company_id=:compId")
              .setParameter("id", id)
              .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
              .executeUpdate();
            ra.addFlashAttribute("success", "Periode berhasil dibuka kembali.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal buka: " + e.getMessage());
        }
        return "redirect:/ui/finance/fiscal-periods";
    }
}