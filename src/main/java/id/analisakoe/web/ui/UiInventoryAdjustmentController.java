package id.analisakoe.web.ui;
import java.util.List;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.inv.service.InventoryAdjustmentService;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.md.repository.WarehouseRepository;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

@Controller
@RequestMapping("/ui/inventory/adjustment")
public class UiInventoryAdjustmentController {
    private final InventoryAdjustmentService service;
    private final ItemRepository itemRepo;
    private final UomRepository uomRepo;
    private final WarehouseRepository whRepo;
    private final MenuService menuService;
    private final JournalService journalService;

    @PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public UiInventoryAdjustmentController(InventoryAdjustmentService service, ItemRepository itemRepo,
                                           UomRepository uomRepo, WarehouseRepository whRepo, MenuService menuService,
                                           JournalService journalService) {
        this.service = service; this.itemRepo = itemRepo;
        this.uomRepo = uomRepo; this.whRepo = whRepo; this.menuService = menuService;
        this.journalService = journalService;
    }

    @GetMapping
    public String form(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Inventory Adjustment");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("items", itemRepo.findByTenantIdAndDeletedAtIsNull(1L, PageRequest.of(0, 100)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(1L, PageRequest.of(0, 100)).getContent());
        model.addAttribute("warehouses", whRepo.findByTenantId(1L, PageRequest.of(0, 100)).getContent());
        model.addAttribute("today", LocalDate.now());

        // Riwayat adjustment (transaction_header dengan remarks 'Adj ...') per cabang aktif
        try {
            String histSql =
                "SELECT h.id, h.doc_no, h.doc_date, h.doc_type, h.remarks, h.status, w.name " +
                "FROM inv.transaction_header h LEFT JOIN md.warehouse w ON w.id = h.warehouse_id " +
                "WHERE h.remarks LIKE 'Adj %' AND h.company_id = :compId " +
                "ORDER BY h.id DESC LIMIT 25";
            List<Object[]> histRows = em.createNativeQuery(histSql)
                    .setParameter("compId", user.getActiveCompanyId()).getResultList();
            List<java.util.Map<String, Object>> history = new ArrayList<>();
            for (Object[] row : histRows) {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", row[0]);
                m.put("docNo", row[1]);
                Object d = row[2];
                m.put("docDate", d instanceof java.sql.Timestamp
                        ? ((java.sql.Timestamp) d).toLocalDateTime().toLocalDate().toString() : String.valueOf(d));
                m.put("docType", row[3]);
                m.put("remarks", row[4]);
                m.put("status", row[5] != null ? row[5].toString() : "DRAFT");
                m.put("warehouse", row[6]);
                history.add(m);
            }
            model.addAttribute("history", history);
        } catch (Exception e) {
            model.addAttribute("history", new ArrayList<>());
        }
        return "ui/inventory/adjustment_form";
    }

    @PostMapping
    public String save(HttpSession session, @RequestParam String adjType, @RequestParam LocalDate docDate,
                       @RequestParam Long warehouseId, @RequestParam(required = false) String remarks,
                       @RequestParam(value = "itemId", required = false) ArrayList<Long> itemIds, @RequestParam(value = "uomId", required = false) ArrayList<Long> uomIds,
                       @RequestParam(value = "qty", required = false) ArrayList<BigDecimal> qtys, @RequestParam(value = "price", required = false) ArrayList<BigDecimal> prices,
                       RedirectAttributes ra) {
        try {
            SessionUser sessUser = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            Long sessCompId = sessUser.getActiveCompanyId();
            // Validasi minimal satu item.
            if (itemIds == null || itemIds.isEmpty()) {
                throw new IllegalArgumentException(
                    "Minimal harus memilih satu item untuk Inventory Adjustment.");
            }

            // Pastikan semua daftar memiliki jumlah baris yang sama.
            if (uomIds == null || qtys == null || prices == null ||
                    itemIds.size() != uomIds.size() ||
                    itemIds.size() != qtys.size() ||
                    itemIds.size() != prices.size()) {
                throw new IllegalArgumentException(
                    "Data Item, UOM, Qty, dan Harga tidak lengkap atau tidak konsisten.");
            }

            // Validasi setiap baris sebelum transaksi inventory dibuat.
            for (int i = 0; i < itemIds.size(); i++) {
                if (itemIds.get(i) == null ||
                        uomIds.get(i) == null ||
                        qtys.get(i) == null ||
                        qtys.get(i).compareTo(BigDecimal.ZERO) <= 0 ||
                        prices.get(i) == null ||
                        prices.get(i).compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException(
                        "Data item baris " + (i + 1) +
                        " tidak valid. Periksa Item, UOM, Qty, dan Harga.");
                }
            }

            var lines = new ArrayList<InventoryAdjustmentService.LineReq>();
            for (int i = 0; i < itemIds.size(); i++) {
                lines.add(new InventoryAdjustmentService.LineReq(itemIds.get(i), uomIds.get(i), qtys.get(i), prices.get(i)));
            }
            service.createAdjustment(1L, sessCompId, adjType, docDate, warehouseId, remarks, lines);
            ra.addFlashAttribute("success", "Inventory Adjustment berhasil. Stok & Jurnal telah diperbarui.");
        } catch (Exception e) { 
            ra.addFlashAttribute("error", e.getMessage()); 
        }
        return "redirect:/ui/inventory/adjustment";
    }

    // ==================== VOID (stok dibalik + jurnal pembalik) ====================
    @PostMapping("/{id}/void")
    public String voidAdjustment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                List<?> hdrList = em.createNativeQuery(
                        "SELECT doc_no, doc_type, status, warehouse_id, remarks, tenant_id, company_id, doc_date " +
                        "FROM inv.transaction_header WHERE id = :id")
                        .setParameter("id", id).getResultList();
                if (hdrList.isEmpty()) throw new IllegalStateException("Adjustment tidak ditemukan.");
                Object[] h = (Object[]) hdrList.get(0);
                String docNo = h[0].toString();
                String docType = h[1].toString();
                String status = h[2] != null ? h[2].toString() : "DRAFT";
                Long whId = ((Number) h[3]).longValue();
                String remarks = h[4] != null ? h[4].toString() : "";
                Long tenantId = ((Number) h[5]).longValue();
                Long compId = ((Number) h[6]).longValue();
                LocalDate docDate = h[7] != null ? ((java.sql.Timestamp) h[7]).toLocalDateTime().toLocalDate() : LocalDate.now();

                if ("VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Adjustment " + docNo + " sudah VOID.");
                if (!"IN".equals(docType) && !"OUT".equals(docType))
                    throw new IllegalStateException("Dokumen ini bukan adjustment (tipe " + docType + ").");

                List<Object[]> details = em.createNativeQuery(
                        "SELECT item_id, qty FROM inv.transaction_detail WHERE header_id = :id")
                        .setParameter("id", id).getResultList();
                if (details.isEmpty()) throw new IllegalStateException("Adjustment tidak memiliki detail item.");

                // 1. Balikkan stok (arah berlawanan)
                String counterType = "IN".equals(docType) ? "OUT" : "IN";
                for (Object[] d : details) {
                    Long itemId = ((Number) d[0]).longValue();
                    BigDecimal qty = new BigDecimal(d[1].toString());

                    // pastikan baris saldo ada
                    em.createNativeQuery(
                            "INSERT INTO inv.stock_balance (tenant_id, item_id, warehouse_id, qty, avg_cost, total_value) " +
                            "SELECT :tenantId, :itemId, :whId, 0, 0, 0 WHERE NOT EXISTS " +
                            "(SELECT 1 FROM inv.stock_balance WHERE tenant_id = :tenantId AND item_id = :itemId AND warehouse_id = :whId)")
                        .setParameter("tenantId", tenantId).setParameter("itemId", itemId)
                        .setParameter("whId", whId).executeUpdate();

                    if ("OUT".equals(counterType)) {
                        em.createNativeQuery("UPDATE inv.stock_balance SET qty = qty - :qty, last_updated = NOW() " +
                                "WHERE tenant_id = :tenantId AND item_id = :itemId AND warehouse_id = :whId")
                            .setParameter("qty", qty).setParameter("tenantId", tenantId)
                            .setParameter("itemId", itemId).setParameter("whId", whId).executeUpdate();
                    } else {
                        em.createNativeQuery("UPDATE inv.stock_balance SET qty = qty + :qty, last_updated = NOW() " +
                                "WHERE tenant_id = :tenantId AND item_id = :itemId AND warehouse_id = :whId")
                            .setParameter("qty", qty).setParameter("tenantId", tenantId)
                            .setParameter("itemId", itemId).setParameter("whId", whId).executeUpdate();
                    }
                }

                // 2. Dokumen penyeimbang (counter transaction)
                Long counterId = ((Number) em.createNativeQuery(
                        "INSERT INTO inv.transaction_header (tenant_id, company_id, doc_type, doc_no, doc_date, warehouse_id, remarks, status) " +
                        "VALUES (:tenantId, :compId, :docType, :docNo, CURRENT_DATE, :whId, :remarks, 'VOID') RETURNING id")
                        .setParameter("tenantId", tenantId).setParameter("compId", compId)
                        .setParameter("docType", counterType)
                        .setParameter("docNo", "VOID-" + docNo)
                        .setParameter("whId", whId)
                        .setParameter("remarks", "Void dari " + docNo + " (" + remarks + ")")
                        .getSingleResult()).longValue();
                // Salin seluruh detail ke dokumen penyeimbang (satu statement)
                em.createNativeQuery(
                        "INSERT INTO inv.transaction_detail (header_id, item_id, uom_id, qty) " +
                        "SELECT :hdrId, item_id, uom_id, qty FROM inv.transaction_detail WHERE header_id = :origId")
                    .setParameter("hdrId", counterId).setParameter("origId", id).executeUpdate();

                // 3. Jurnal pembalik (cari jurnal asli berdasarkan remarks)
                String typeLetter = "IN".equals(docType) ? "DB" : "CR";
                String jrnlType = "INV_ADJ_" + typeLetter;
                String jrnlRemarks = "Inventory Adjustment " + typeLetter + ": "
                        + remarks.replaceFirst("^Adj " + typeLetter + ": ", "");
                List<Object[]> jrnlLines = em.createNativeQuery(
                        "SELECT c.code, d.debit, d.credit, COALESCE(d.description,'') " +
                        "FROM fin.journal_dtl d JOIN fin.journal_hdr j ON j.id = d.header_id " +
                        "JOIN fin.chart_of_account c ON c.id = d.account_id " +
                        "WHERE j.journal_type = :jt AND j.remarks = :rm")
                        .setParameter("jt", jrnlType).setParameter("rm", jrnlRemarks).getResultList();
                if (!jrnlLines.isEmpty()) {
                    List<JournalService.JournalLine> reversal = new ArrayList<>();
                    for (Object[] l : jrnlLines) {
                        BigDecimal dr = l[1] != null ? new BigDecimal(l[1].toString()) : BigDecimal.ZERO;
                        BigDecimal cr = l[2] != null ? new BigDecimal(l[2].toString()) : BigDecimal.ZERO;
                        reversal.add(new JournalService.JournalLine(l[0].toString(), cr, dr, "Void: " + l[3]));
                    }
                    journalService.createJournal(tenantId, compId, LocalDate.now(), jrnlType, "VOID: " + jrnlRemarks, reversal);
                }

                // 4. Tandai dokumen asli VOID
                em.createNativeQuery("UPDATE inv.transaction_header SET status = 'VOID' WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            });
            ra.addFlashAttribute("success", "Adjustment berhasil di-VOID. Stok & jurnal dibalik.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal void: " + rootMessage(e));
        }
        return "redirect:/ui/inventory/adjustment";
    }

    // ==================== DELETE (hanya VOID) ====================
    @PostMapping("/{id}/delete")
    public String deleteAdjustment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                List<?> hdrList = em.createNativeQuery(
                        "SELECT doc_no, status FROM inv.transaction_header WHERE id = :id")
                        .setParameter("id", id).getResultList();
                if (hdrList.isEmpty()) throw new IllegalStateException("Adjustment tidak ditemukan.");
                Object[] h = (Object[]) hdrList.get(0);
                String docNo = h[0].toString();
                String status = h[1] != null ? h[1].toString() : "DRAFT";
                if (!"VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Adjustment " + docNo + " masih aktif. Void dulu sebelum menghapus.");

                // Hapus dokumen asli + dokumen penyeimbangnya
                List<Integer> pairIds = em.createNativeQuery(
                        "SELECT id FROM inv.transaction_header WHERE id = :id " +
                        "OR (doc_no = 'VOID-' || :docNo AND remarks LIKE 'Void dari %')")
                        .setParameter("id", id).setParameter("docNo", docNo).getResultList();
                for (Object pid : pairIds) {
                    Long pidL = ((Number) pid).longValue();
                    em.createNativeQuery("DELETE FROM inv.transaction_detail WHERE header_id = :hid")
                        .setParameter("hid", pidL).executeUpdate();
                    em.createNativeQuery("DELETE FROM inv.transaction_header WHERE id = :hid")
                        .setParameter("hid", pidL).executeUpdate();
                }
            });
            ra.addFlashAttribute("success", "Adjustment berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + rootMessage(e));
        }
        return "redirect:/ui/inventory/adjustment";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }
}