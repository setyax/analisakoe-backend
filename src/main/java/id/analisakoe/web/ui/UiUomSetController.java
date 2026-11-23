package id.analisakoe.web.ui;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/master/uom-sets")
public class UiUomSetController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiUomSetController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ============================================================
    // LIST
    // ============================================================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "UoM Sets");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Object[]> sets = em.createNativeQuery(
            "SELECT id, code, name, COALESCE(category,'-'), COALESCE(description,'-'), " +
            "COALESCE(is_active, true) FROM md.uom_set ORDER BY code").getResultList();
        model.addAttribute("sets", sets);

        // Hitung jumlah detail per set
        Map<Long, Long> detailCounts = new HashMap<>();
        List<Object[]> counts = em.createNativeQuery(
            "SELECT uom_set_id, COUNT(*) FROM md.uom_set_dtl GROUP BY uom_set_id").getResultList();
        for (Object[] r : counts) {
            detailCounts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        model.addAttribute("detailCounts", detailCounts);

        // Jumlah item pakai set ini
        Map<Long, Long> itemCounts = new HashMap<>();
        try {
            List<Object[]> ic = em.createNativeQuery(
                "SELECT uom_set_id, COUNT(*) FROM md.item WHERE uom_set_id IS NOT NULL GROUP BY uom_set_id").getResultList();
            for (Object[] r : ic) {
                itemCounts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
            }
        } catch (Exception e) {}
        model.addAttribute("itemCounts", itemCounts);

        return "ui/master/uom_set_list";
    }

    // ============================================================
    // FORM CREATE
    // ============================================================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New UoM Set");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);

        // Dropdown UoM master
        List<Object[]> uoms = em.createNativeQuery(
            "SELECT id, code, name FROM md.uom ORDER BY code").getResultList();
        model.addAttribute("uoms", uoms);

        return "ui/master/uom_set_form";
    }

    // ============================================================
    // FORM EDIT
    // ============================================================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit UoM Set");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT id, code, name, COALESCE(category,''), COALESCE(description,''), COALESCE(is_active, true) " +
                "FROM md.uom_set WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            Map<String, Object> h = new HashMap<>();
            h.put("id", hdr[0]);
            h.put("code", hdr[1]);
            h.put("name", hdr[2]);
            h.put("category", hdr[3]);
            h.put("description", hdr[4]);
            h.put("isActive", hdr[5]);
            model.addAttribute("uomSet", h);

            // Detail hierarki (urut level ASC)
            List<Object[]> details = em.createNativeQuery(
                "SELECT d.id, d.uom_id, u.code AS uom_code, u.name AS uom_name, " +
                "d.parent_uom_id, pu.code AS parent_code, d.conversion_factor, d.level, COALESCE(d.is_base, false) " +
                "FROM md.uom_set_dtl d " +
                "JOIN md.uom u ON u.id = d.uom_id " +
                "LEFT JOIN md.uom pu ON pu.id = d.parent_uom_id " +
                "WHERE d.uom_set_id = :id ORDER BY d.level ASC, d.id ASC")
                .setParameter("id", id).getResultList();
            model.addAttribute("details", details);

            // Semua UoM master
            List<Object[]> uoms = em.createNativeQuery(
                "SELECT id, code, name FROM md.uom ORDER BY code").getResultList();
            model.addAttribute("uoms", uoms);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "UoM Set tidak ditemukan: " + e.getMessage());
            return "redirect:/ui/master/uom-sets";
        }

        return "ui/master/uom_set_form";
    }

    // ============================================================
    // SAVE (CREATE / UPDATE)
    // ============================================================
    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String description,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       @RequestParam(value = "uomIds", required = false) List<Long> uomIds,
                       @RequestParam(value = "parentUomIds", required = false) List<Long> parentUomIds,
                       @RequestParam(value = "conversionFactors", required = false) List<BigDecimal> conversionFactors,
                       @RequestParam(value = "isBases", required = false) List<String> isBases,
                       RedirectAttributes ra) {
        try {
            // VALIDASI
            if (uomIds == null || uomIds.isEmpty()) {
                throw new Exception("Minimal 1 UoM harus ditambahkan.");
            }

            // Hitung base count
            int baseCount = 0;
            for (String b : isBases) {
                if ("true".equals(b) || "on".equals(b)) baseCount++;
            }
            if (baseCount != 1) {
                throw new Exception("Harus ada tepat 1 UoM yang ditandai sebagai BASE.");
            }

            Long setId;
            if (id == null) {
                // INSERT HEADER
                Object newId = em.createNativeQuery(
                    "INSERT INTO md.uom_set (tenant_id, code, name, category, description, is_active, created_at, updated_at) " +
                    "VALUES (1, :code, :name, :cat, :desc, :act, now(), now()) RETURNING id")
                    .setParameter("code", code)
                    .setParameter("name", name)
                    .setParameter("cat", category)
                    .setParameter("desc", description)
                    .setParameter("act", isActive)
                    .getSingleResult();
                setId = ((Number) newId).longValue();
            } else {
                // UPDATE HEADER
                em.createNativeQuery(
                    "UPDATE md.uom_set SET code=:code, name=:name, category=:cat, description=:desc, is_active=:act, updated_at=now() WHERE id=:id")
                    .setParameter("code", code).setParameter("name", name).setParameter("cat", category)
                    .setParameter("desc", description).setParameter("act", isActive).setParameter("id", id)
                    .executeUpdate();
                setId = id;
                // Hapus detail lama
                em.createNativeQuery("DELETE FROM md.uom_set_dtl WHERE uom_set_id = :sid")
                    .setParameter("sid", setId).executeUpdate();
            }

            // INSERT DETAIL
            // Level dihitung dari chain parent, atau urutan input
            Map<Long, Long> parentMap = new HashMap<>();
            Map<Long, Boolean> baseMap = new HashMap<>();
            for (int i = 0; i < uomIds.size(); i++) {
                Long uid = uomIds.get(i);
                Long pid = (parentUomIds != null && i < parentUomIds.size()) ? parentUomIds.get(i) : null;
                if (pid != null && pid == 0) pid = null;
                parentMap.put(uid, pid);
                boolean isBase = isBases != null && i < isBases.size() 
                    && ("true".equals(isBases.get(i)) || "on".equals(isBases.get(i)));
                baseMap.put(uid, isBase);
            }

            for (int i = 0; i < uomIds.size(); i++) {
                Long uid = uomIds.get(i);
                Long pid = parentMap.get(uid);
                BigDecimal factor = (conversionFactors != null && i < conversionFactors.size()) ? conversionFactors.get(i) : BigDecimal.ONE;
                boolean isBase = baseMap.get(uid);

                // Hitung level: walk up
                int level = 1;
                Long cur = uid;
                int guard = 0;
                while (parentMap.get(cur) != null) {
                    level++;
                    cur = parentMap.get(cur);
                    if (++guard > 20) throw new Exception("Hierarki terlalu dalam / ada cycle di UoM ID " + uid);
                }

                if (isBase) {
                    if (pid != null) throw new Exception("Base UoM tidak boleh punya parent.");
                    level = 1;
                    factor = BigDecimal.ONE;
                }

                em.createNativeQuery(
                    "INSERT INTO md.uom_set_dtl (uom_set_id, uom_id, parent_uom_id, conversion_factor, level, is_base) " +
                    "VALUES (:sid, :uid, :pid, :factor, :lvl, :isBase)")
                    .setParameter("sid", setId)
                    .setParameter("uid", uid)
                    .setParameter("pid", pid)
                    .setParameter("factor", factor)
                    .setParameter("lvl", level)
                    .setParameter("isBase", isBase)
                    .executeUpdate();
            }

            ra.addFlashAttribute("success", "UoM Set berhasil disimpan (" + uomIds.size() + " detail).");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("error", "Gagal simpan: " + e.getMessage());
        }
        return "redirect:/ui/master/uom-sets";
    }

    // ============================================================
    // DELETE
    // ============================================================
    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            // Cek apakah set sedang dipakai item
            Object used = em.createNativeQuery("SELECT COUNT(*) FROM md.item WHERE uom_set_id = :id")
                .setParameter("id", id).getSingleResult();
            Long usedCount = ((Number) used).longValue();
            if (usedCount > 0) {
                throw new Exception("Tidak bisa hapus: " + usedCount + " item masih pakai set ini.");
            }

            em.createNativeQuery("DELETE FROM md.uom_set_dtl WHERE uom_set_id = :id")
                .setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM md.uom_set WHERE id = :id")
                .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "UoM Set berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/uom-sets";
    }
}