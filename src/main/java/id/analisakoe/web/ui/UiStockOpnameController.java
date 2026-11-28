package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.md.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/inventory/opname")
public class UiStockOpnameController {

    @Autowired
    private ContextService context;
    private final MenuService menuService;
    private final ItemRepository itemRepo;
    private final UomRepository uomRepo;
    private final WarehouseRepository whRepo;
    private final EntityManager em;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public UiStockOpnameController(MenuService menuService, ItemRepository itemRepo, UomRepository uomRepo, WarehouseRepository whRepo, EntityManager em) {
        this.menuService = menuService; this.itemRepo = itemRepo; this.uomRepo = uomRepo; this.whRepo = whRepo; this.em = em;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Stock Opname");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.doc_date, w.name, h.status " +
                     "FROM inv.stock_opname_hdr h LEFT JOIN md.warehouse w ON h.warehouse_id = w.id " +
                     "WHERE h.tenant_id = :tenantId AND h.company_id = :compId " +
                     "ORDER BY h.id DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1]);
                map.put("docDate", row[2]);
                map.put("warehouse", row[3] != null ? row[3] : "-");
                map.put("status", row[4]);
                list.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("opnames", list);
        return "ui/inventory/opname_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Stock Opname");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("items", itemRepo.findByTenantIdAndCompanyIdAndDeletedAtIsNull(
                context.tenantId(), context.companyId(),
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(context.tenantId(),
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent());
        model.addAttribute("warehouses", whRepo.findByTenantIdAndCompanyId(
                context.tenantId(), context.companyId(),
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent());
        return "ui/inventory/opname_form";
    }

    @PostMapping
    @Transactional
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam(required = false) Long warehouseId,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemId") ArrayList<Long> itemIds,
                       @RequestParam("uomId") ArrayList<Long> uomIds,
                       @RequestParam("systemQty") ArrayList<BigDecimal> sysQtys,
                       @RequestParam("physicalQty") ArrayList<BigDecimal> physQtys,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            String docNo = "OPN-" + System.currentTimeMillis();
            String sqlHdr = "INSERT INTO inv.stock_opname_hdr (tenant_id, company_id, doc_no, doc_date, warehouse_id, status, remarks) " +
                            "VALUES (:tenantId, :compId, :docNo, :docDate, :wh, 'CONFIRMED', :remarks) RETURNING id";
            Long hdrId = ((Number) em.createNativeQuery(sqlHdr)
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", docNo).setParameter("docDate", docDate)
                .setParameter("wh", warehouseId).setParameter("remarks", remarks)
                .getSingleResult()).longValue();

            int adjCount = 0;
            for (int i = 0; i < itemIds.size(); i++) {
                BigDecimal sysQty = sysQtys.get(i);
                BigDecimal physQty = physQtys.get(i);
                BigDecimal diff = physQty.subtract(sysQty);

                // Simpan detail opname
                em.createNativeQuery("INSERT INTO inv.stock_opname_dtl (header_id, item_id, uom_id, system_qty, physical_qty, diff_qty) " +
                                     "VALUES (:hdrId, :itemId, :uomId, :sysQty, :physQty, :diff)")
                    .setParameter("hdrId", hdrId).setParameter("itemId", itemIds.get(i))
                    .setParameter("uomId", uomIds.get(i)).setParameter("sysQty", sysQty)
                    .setParameter("physQty", physQty).setParameter("diff", diff).executeUpdate();

                // Sesuaikan stock_balance jika ada selisih
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    adjCount++;
                    // Cek dulu apakah record stock_balance sudah ada
                    List<?> existing = em.createNativeQuery("SELECT id FROM inv.stock_balance WHERE tenant_id = :tenantId AND item_id = :itemId "
                        + "AND (warehouse_id = :wh OR (:wh IS NULL AND warehouse_id IS NULL))")
                        .setParameter("tenantId", tenantId)
                        .setParameter("itemId", itemIds.get(i)).setParameter("wh", warehouseId).getResultList();
                    
                    if (existing.isEmpty()) {
                        em.createNativeQuery("INSERT INTO inv.stock_balance (tenant_id, item_id, warehouse_id, qty) VALUES (:tenantId, :itemId, :wh, :physQty)")
                            .setParameter("itemId", itemIds.get(i)).setParameter("wh", warehouseId).setParameter("physQty", physQty).setParameter("tenantId", context.tenantId()).executeUpdate();
                    } else {
                        em.createNativeQuery("UPDATE inv.stock_balance SET qty = :physQty WHERE tenant_id = :tenantId AND item_id = :itemId "
                            + "AND (warehouse_id = :wh OR (:wh IS NULL AND warehouse_id IS NULL))")
                            .setParameter("physQty", physQty).setParameter("tenantId", tenantId)
                            .setParameter("itemId", itemIds.get(i)).setParameter("wh", warehouseId).executeUpdate();
                    }
                }
            }
            ra.addFlashAttribute("success", "Stock Opname berhasil disimpan. " + adjCount + " item disesuaikan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/inventory/opname";
    }

    // ==================== VOID (kembalikan stok ke system qty) ====================
    @PostMapping("/{id}/void")
    public String voidOpname(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                List<?> hdrList = em.createNativeQuery(
                        "SELECT status, warehouse_id FROM inv.stock_opname_hdr "
                        + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                        .setParameter("id", id)
                        .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                        .getResultList();
                if (hdrList.isEmpty()) throw new IllegalStateException("Opname tidak ditemukan.");
                Object[] hdrRow = (Object[]) hdrList.get(0);
                String status = hdrRow[0].toString();
                if ("VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Opname sudah VOID.");
                final Long opnameWhId = hdrRow[1] != null ? ((Number) hdrRow[1]).longValue() : null;

                // Kembalikan stock_balance ke system_qty untuk baris yang punya selisih.
                // Query dibangun dinamis agar PostgreSQL tidak error 42P18 (:param IS NULL).
                List<Object[]> details = em.createNativeQuery(
                        "SELECT d.item_id, d.system_qty, d.diff_qty FROM inv.stock_opname_dtl d "
                        + "JOIN inv.stock_opname_hdr h ON h.id = d.header_id "
                        + "WHERE d.header_id = :id AND h.tenant_id = :tenantId AND h.company_id = :compId")
                        .setParameter("id", id)
                        .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                        .getResultList();
                int restored = 0;
                for (Object[] d : details) {
                    Long itemId = ((Number) d[0]).longValue();
                    BigDecimal systemQty = d[1] != null ? new BigDecimal(d[1].toString()) : BigDecimal.ZERO;
                    BigDecimal diff = d[2] != null ? new BigDecimal(d[2].toString()) : BigDecimal.ZERO;
                    if (diff.compareTo(BigDecimal.ZERO) == 0) continue;

                    if (opnameWhId != null) {
                        em.createNativeQuery("UPDATE inv.stock_balance SET qty = :sysQty, last_updated = NOW() " +
                                "WHERE tenant_id = :tenantId AND item_id = :itemId AND warehouse_id = :wh")
                            .setParameter("sysQty", systemQty).setParameter("itemId", itemId)
                            .setParameter("wh", opnameWhId).setParameter("tenantId", context.tenantId()).executeUpdate();
                    } else {
                        em.createNativeQuery("UPDATE inv.stock_balance SET qty = :sysQty, last_updated = NOW() " +
                                "WHERE tenant_id = :tenantId AND item_id = :itemId AND warehouse_id IS NULL")
                            .setParameter("sysQty", systemQty).setParameter("itemId", itemId)
                            .setParameter("tenantId", context.tenantId()).executeUpdate();
                    }
                    restored++;
                }

                em.createNativeQuery("UPDATE inv.stock_opname_hdr SET status = 'VOID' WHERE id = :id "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .executeUpdate();

                ra.addFlashAttribute("success", "Opname berhasil di-VOID. " + restored + " item dikembalikan ke system qty.");
            });
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal void: " + rootMessage(e));
        }
        return "redirect:/ui/inventory/opname";
    }

    // ==================== DELETE (hanya VOID) ====================
    @PostMapping("/{id}/delete")
    public String deleteOpname(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                List<?> hdrList = em.createNativeQuery(
                        "SELECT status FROM inv.stock_opname_hdr "
                        + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                        .setParameter("id", id)
                        .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                        .getResultList();
                if (hdrList.isEmpty()) throw new IllegalStateException("Opname tidak ditemukan.");
                String status = hdrList.get(0).toString();
                if (!"VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Hanya opname berstatus VOID yang dapat dihapus. Void dulu.");

                em.createNativeQuery("DELETE FROM inv.stock_opname_dtl WHERE header_id = :id")
                    .setParameter("id", id).executeUpdate();
                em.createNativeQuery("DELETE FROM inv.stock_opname_hdr WHERE id = :id "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .executeUpdate();
            });
            ra.addFlashAttribute("success", "Opname berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + rootMessage(e));
        }
        return "redirect:/ui/inventory/opname";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }
}
