package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.service.StockValuationService;      // ======= TAMBAHAN =======
import id.analisakoe.fin.service.JournalService;         // ======= TAMBAHAN =======
import id.analisakoe.fin.service.JournalService.JournalLine; // ======= TAMBAHAN =======
import id.analisakoe.md.entity.Item;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.pur.entity.GrnHdr;
import id.analisakoe.pur.repository.GrnRepository;
import id.analisakoe.pur.repository.PurchaseOrderRepository;
import id.analisakoe.pur.service.GrnService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui/purchasing/grn")
public class UiGrnController {

    private final GrnService service;
    private final GrnRepository repo;
    private final PurchaseOrderRepository poRepo;
    private final MenuService menuService;
    private final ItemRepository itemRepo;
    private final UomRepository uomRepo;

    @Autowired
    private EntityManager em;

    // ======= TAMBAHAN: Inject Inventory & Journal Service =======
    @Autowired
    private StockValuationService inventoryService;

    @Autowired
    private JournalService journalService;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public UiGrnController(GrnService service, GrnRepository repo, PurchaseOrderRepository poRepo,
                           MenuService menuService, ItemRepository itemRepo, UomRepository uomRepo) {
        this.service = service;
        this.repo = repo;
        this.poRepo = poRepo;
        this.menuService = menuService;
        this.itemRepo = itemRepo;
        this.uomRepo = uomRepo;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Goods Receipts");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.doc_date, h.remarks, h.status, " +
                     "COALESCE(s.name, '-') AS supplier_name, " +
                     "COALESCE(po.doc_no, '-') AS po_number " +
                     "FROM pur.grn_hdr h " +
                     "LEFT JOIN pur.purchase_order_hdr po ON h.po_id = po.id " +
                     "LEFT JOIN md.supplier s ON po.supplier_id = s.id " +
                     "WHERE h.tenant_id = 1 ORDER BY h.id DESC";

        List<Map<String, Object>> grnList = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1] != null ? row[1].toString() : "-");
                Object dateObj = row[2];
                map.put("docDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : null);
                map.put("remarks", row[3]);
                map.put("status", row[4]);
                map.put("supplierName", row[5]);
                map.put("poNumber", row[6]);
                grnList.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("grns", grnList);
        return "ui/purchasing/grn_list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long poId, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Penerimaan Barang (GRN)");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        var availablePos = poRepo.findByTenantIdOrderByIdDesc(1L,
                org.springframework.data.domain.PageRequest.of(0, 100))
            .getContent().stream().filter(p -> "CONFIRMED".equals(p.getStatus())).toList();
        model.addAttribute("pos", availablePos);
        model.addAttribute("items", itemRepo.findByTenantIdAndDeletedAtIsNull(1L,
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(1L,
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("selectedPoId", poId);
        return "ui/purchasing/grn_form";
    }

    @PostMapping
    public String save(@RequestParam Long poId, @RequestParam LocalDate docDate,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemId") ArrayList<Long> itemIds,
                       @RequestParam("uomId") ArrayList<Long> uomIds,
                       @RequestParam("qtyReceived") ArrayList<BigDecimal> qtys,
                       RedirectAttributes ra) {
        try {
            var lines = new ArrayList<GrnService.LineReq>();
            for (int i = 0; i < itemIds.size(); i++) {
                lines.add(new GrnService.LineReq(itemIds.get(i), uomIds.get(i), qtys.get(i)));
            }
            service.createGrn(poId, docDate, remarks, lines);
            ra.addFlashAttribute("success", "GRN berhasil disimpan (DRAFT).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/purchasing/grn";
    }

    @GetMapping("/{id}/view")
    @Transactional(readOnly = true)
    public String viewGrn(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "View GRN");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        var grn = repo.findById(id).orElseThrow();
        model.addAttribute("grn", grn);

        Map<Long, String> itemMap = itemRepo.findAll().stream()
                .collect(Collectors.toMap(Item::getId, Item::getName));
        model.addAttribute("itemMap", itemMap);

        Map<Long, String> uomMap = uomRepo.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getCode()));
        model.addAttribute("uomMap", uomMap);

        // Baris detail via native query: nama item, qty, uom selalu terisi
        List<?> dtlRows = em.createNativeQuery(
                "SELECT d.id, i.code, i.name, d.qty_received, u.code " +
                "FROM pur.grn_dtl d " +
                "LEFT JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "WHERE d.header_id = :id ORDER BY d.id")
                .setParameter("id", id).getResultList();
        List<Map<String, Object>> details = new java.util.ArrayList<>();
        for (Object r : dtlRows) {
            Object[] rr = (Object[]) r;
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("detailId", rr[0]);
            m.put("itemName", (rr[1] != null ? rr[1].toString() : "") + " - " + (rr[2] != null ? rr[2].toString() : ""));
            m.put("qty", rr[3] != null ? new java.math.BigDecimal(rr[3].toString()).stripTrailingZeros().toPlainString() : "");
            m.put("uomCode", rr[4] != null ? rr[4].toString() : "-");
            details.add(m);
        }
        model.addAttribute("detailRows", details);

        if (grn.getPoId() != null) {
            poRepo.findById(grn.getPoId()).ifPresent(po -> model.addAttribute("refPoNo", po.getDocNo()));
        }
        return "ui/purchasing/grn_view";
    }

    // ============================================================
    // CONFIRM GRN — DENGAN INTEGRASI INVENTORY & JOURNAL
    // ============================================================
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
            // === 1. Ambil data GRN SEBELUM confirm (untuk diproses inventory & journal) ===
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT doc_no, doc_date, status, tenant_id, company_id FROM pur.grn_hdr WHERE id = :id")
                .setParameter("id", id).getSingleResult();

            String grnNo = hdr[0].toString();
            LocalDate grnDate = ((java.sql.Date) hdr[1]).toLocalDate();
            String status = hdr[2] != null ? hdr[2].toString() : "";
            Long grnTenant = ((Number) hdr[3]).longValue();
            Long grnCompany = ((Number) hdr[4]).longValue();

            // Validasi: Hanya DRAFT yang bisa di-confirm
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error",
                    "Hanya GRN berstatus DRAFT yang bisa di-confirm. Status: " + status);
                return;
            }

            // === 2. Ambil detail GRN + harga dari PO ===
            List<Object[]> details = em.createNativeQuery(
                "SELECT gd.item_id, gd.uom_id, gd.qty_received, COALESCE(pod.price, 0) AS unit_cost " +
                "FROM pur.grn_dtl gd " +
                "JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
                "LEFT JOIN pur.purchase_order_dtl pod " +
                "  ON pod.header_id = gh.po_id AND pod.item_id = gd.item_id " +
                "WHERE gd.header_id = :id").setParameter("id", id).getResultList();

            if (details.isEmpty()) {
                ra.addFlashAttribute("error", "GRN tidak memiliki detail item.");
                return;
            }

            // === 3. Panggil service untuk update status GRN jadi CONFIRMED ===
            service.confirmGrn(id);
			
			            // ============================================================
            // 3-WAY MATCH: Cek Total Qty GRN ≤ Qty PO per item
            // ============================================================
            for (Object[] d : details) {
                Long itemId = ((Number) d[0]).longValue();
                BigDecimal qtyReceived = new BigDecimal(d[1].toString());

                // Ambil PO ID dari GRN header
                Long poId = ((Number) em.createNativeQuery(
                    "SELECT po_id FROM pur.grn_hdr WHERE id = :id")
                    .setParameter("id", id).getSingleResult()).longValue();

                // Total qty PO untuk item ini
                List<?> poQtyList = em.createNativeQuery(
                    "SELECT COALESCE(qty, 0) FROM pur.purchase_order_dtl WHERE header_id = :poId AND item_id = :itemId")
                    .setParameter("poId", poId).setParameter("itemId", itemId).getResultList();
                BigDecimal poQty = poQtyList.isEmpty() ? BigDecimal.ZERO 
                    : new BigDecimal(poQtyList.get(0).toString());

                // Total qty GRN lain untuk item yang sama (selain GRN yang sedang confirm)
                                BigDecimal grnQtyOther = BigDecimal.ZERO;
                Object otherGrnList = em.createNativeQuery(
                    "SELECT COALESCE(SUM(gd.qty_received), 0) FROM pur.grn_dtl gd " +
                    "JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
                    "WHERE gh.po_id = :poId AND gd.item_id = :itemId " +
                    "AND gh.id <> :currentGrnId AND UPPER(TRIM(gh.status)) NOT IN ('VOID', 'DRAFT')")
                    .setParameter("poId", poId).setParameter("itemId", itemId).setParameter("currentGrnId", id)
                    .getSingleResult();
                if (otherGrnList != null) {
                    grnQtyOther = new BigDecimal(otherGrnList.toString());
                }

                BigDecimal totalGrn = grnQtyOther.add(qtyReceived);
                if (totalGrn.compareTo(poQty) > 0) {
                    throw new RuntimeException(
                        "❌ OVER-RECEIVE: Total Qty GRN untuk item ID " + itemId + " (" + totalGrn + ") " +
                        "melebihi Qty PO (" + poQty + "). " +
                        "Sudah diterima sebelumnya: " + grnQtyOther + ", GRN ini: " + qtyReceived + ".");
                }
            }
            // ============================================================

            // === 4. Proses Inventory (Stock IN) + Hitung Total Value ===
            BigDecimal totalValue = BigDecimal.ZERO;
            for (Object[] d : details) {
                Long itemId = ((Number) d[0]).longValue();
                Long uomId = ((Number) d[1]).longValue();
                BigDecimal qty = new BigDecimal(d[2].toString());
                BigDecimal price = new BigDecimal(d[3].toString());

                // Konversi qty & harga ke satuan dasar item sebelum masuk stok
                BigDecimal[] conv = inventoryService.convertLineToBase(itemId, uomId, qty, price);
                BigDecimal qtyBase = conv[0];
                BigDecimal costBase = conv[1];

                // Stok masuk dengan unit_cost = harga PO (dalam satuan dasar)
                inventoryService.stockIn(itemId, 1L, qtyBase, costBase,
                    grnDate, "GRN", id, grnNo, "Penerimaan dari GRN " + grnNo);

                totalValue = totalValue.add(qtyBase.multiply(costBase));
            }

            // === 5. Buat Jurnal Otomatis ===
            //    Debit:  1301 Persediaan Barang Dagang (Inventory)
            //    Credit: 2105 GR/IR Clearing (bukan Hutang Usaha — hutang proper baru saat PI)
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                List<JournalLine> lines = List.of(
                    new JournalLine("1301", totalValue, BigDecimal.ZERO,
                        "Penerimaan barang " + grnNo),
                    new JournalLine("2105", BigDecimal.ZERO, totalValue,
                        "GR/IR Clearing " + grnNo)
                );
                journalService.createJournal(grnTenant, grnCompany, grnDate, "GRN",
                    "Auto journal dari GRN [" + grnNo + "]", lines);
            }

            ra.addFlashAttribute("success",
                "GRN " + grnNo + " berhasil di-confirm. Stok & Jurnal telah di-update.");
            });
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal confirm GRN: " + rootMessage(e));
        }
        return "redirect:/ui/purchasing/grn";
    }

    @PostMapping("/{id}/unconfirm")
    public String unconfirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {

        Object[] hdr = (Object[]) em.createNativeQuery(
            "SELECT doc_no, doc_date, status, tenant_id, company_id FROM pur.grn_hdr WHERE id = :id"
        ).setParameter("id", id).getSingleResult();

        String grnNo = hdr[0].toString();
        LocalDate grnDate = ((java.sql.Date) hdr[1]).toLocalDate();
        String status = hdr[2] != null ? hdr[2].toString() : "";
        Long grnTenant = ((Number) hdr[3]).longValue();
        Long grnCompany = ((Number) hdr[4]).longValue();

        if (!"CONFIRMED".equals(status)) {
            ra.addFlashAttribute("error",
                "Hanya GRN CONFIRMED yang dapat di-unconfirm. Status saat ini: " + status);
            return;
        }

        Long piCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pur.purchase_invoice_hdr " +
            "WHERE grn_id = :id AND status <> 'VOID'"
        ).setParameter("id", id).getSingleResult()).longValue();

        if (piCount > 0) {
            ra.addFlashAttribute("error",
                "GRN " + grnNo + " tidak dapat di-unconfirm karena masih memiliki " +
                piCount + " Purchase Invoice aktif. Void invoice terlebih dahulu.");
            return;
        }

        // Ambil satu harga PO untuk setiap detail GRN.
        // LATERAL mencegah satu detail GRN bergabung dengan beberapa baris PO.
        // po_detail_id diprioritaskan; fallback dipakai untuk data GRN lama.
        List<Object[]> details = em.createNativeQuery(
            "SELECT gd.item_id, gd.uom_id, gd.qty_received, COALESCE(pod.price, 0) " +
            "FROM pur.grn_dtl gd " +
            "JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
            "LEFT JOIN LATERAL ( " +
            "   SELECT p.price " +
            "   FROM pur.purchase_order_dtl p " +
            "   WHERE p.header_id = gh.po_id " +
            "     AND p.item_id = gd.item_id " +
            "   ORDER BY CASE WHEN p.id = gd.po_detail_id THEN 0 ELSE 1 END, p.id " +
            "   LIMIT 1 " +
            ") pod ON TRUE " +
            "WHERE gd.header_id = :id"
        ).setParameter("id", id).getResultList();

        if (details.isEmpty()) {
            throw new IllegalStateException("GRN tidak mempunyai detail untuk reversal.");
        }

        BigDecimal totalValue = BigDecimal.ZERO;

        // Stock OUT hanya dilakukan di sini. Tidak ada OUT kedua dari GrnService.
        for (Object[] d : details) {
            Long itemId = ((Number) d[0]).longValue();
            Long uomId = ((Number) d[1]).longValue();
            BigDecimal qty = new BigDecimal(d[2].toString());
            BigDecimal price = new BigDecimal(d[3].toString());

            BigDecimal[] conv = inventoryService.convertLineToBase(itemId, uomId, qty, price);
            BigDecimal qtyBase = conv[0];
            BigDecimal costBase = conv[1];

            inventoryService.stockOut(
                itemId,
                1L,
                qtyBase,
                grnDate,
                "GRN_REVERSAL",
                id,
                grnNo,
                "Reversal unconfirm GRN " + grnNo
            );

            totalValue = totalValue.add(qtyBase.multiply(costBase));
        }

        // Jurnal pembalik: Debit GR/IR, Kredit Persediaan.
        if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
            List<JournalService.JournalLine> lines = List.of(
                new JournalService.JournalLine(
                    "2105", totalValue, BigDecimal.ZERO,
                    "Reversal GR/IR dari unconfirm " + grnNo
                ),
                new JournalService.JournalLine(
                    "1301", BigDecimal.ZERO, totalValue,
                    "Reversal persediaan dari unconfirm " + grnNo
                )
            );

            journalService.createJournal(
                grnTenant, grnCompany, grnDate,
                "GRN_REVERSAL",
                "Reversal journal dari unconfirm GRN [" + grnNo + "]",
                lines
            );
        }

        // Service hanya mengubah status GRN dan PO.
        // Tetap bergabung dengan transaksi @Transactional controller.
        service.unconfirmGrn(id);

        ra.addFlashAttribute("success",
            "GRN " + grnNo + " berhasil di-unconfirm. Stok dan jurnal telah di-reverse.");

            });
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal unconfirm GRN: " + rootMessage(e));
        }
        return "redirect:/ui/purchasing/grn";
    }

    @PostMapping("/{id}/void")
public String voidGrn(@PathVariable Long id, RedirectAttributes ra) {
    try {
        transactionTemplate.executeWithoutResult(tx -> {
        Object[] hdr = (Object[]) em.createNativeQuery(
            "SELECT doc_no, doc_date, status, tenant_id, company_id FROM pur.grn_hdr WHERE id = :id")
            .setParameter("id", id).getSingleResult();
        String grnNo = hdr[0].toString();
        LocalDate grnDate = ((java.sql.Date) hdr[1]).toLocalDate();
        String status = hdr[2] != null ? hdr[2].toString() : "";
        Long grnTenant = ((Number) hdr[3]).longValue();
        Long grnCompany = ((Number) hdr[4]).longValue();

        if ("VOID".equals(status)) {
            ra.addFlashAttribute("error", "GRN " + grnNo + " sudah VOID.");
            return;
        }

        // CEK: Apakah ada PI aktif?
        Long piCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pur.purchase_invoice_hdr " +
            "WHERE grn_id = :id AND status <> 'VOID'")
            .setParameter("id", id).getSingleResult()).longValue();

        if (piCount > 0) {
            ra.addFlashAttribute("error",
                "❌ GRN " + grnNo + " tidak bisa di-void karena sudah dibuatkan " +
                piCount + " Purchase Invoice aktif. Void invoice terlebih dahulu.");
            return;
        }

        // Kalau CONFIRMED, reverse stock & jurnal
        if ("CONFIRMED".equals(status)) {
            List<Object[]> details = em.createNativeQuery(
                "SELECT gd.item_id, gd.uom_id, gd.qty_received, COALESCE(pod.price, 0) " +
                "FROM pur.grn_dtl gd " +
                "JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
                "LEFT JOIN pur.purchase_order_dtl pod " +
                "  ON pod.header_id = gh.po_id AND pod.item_id = gd.item_id " +
                "WHERE gd.header_id = :id").setParameter("id", id).getResultList();

            BigDecimal totalValue = BigDecimal.ZERO;
            for (Object[] d : details) {
                Long itemId = ((Number) d[0]).longValue();
                Long uomId = ((Number) d[1]).longValue();
                BigDecimal qty = new BigDecimal(d[2].toString());
                BigDecimal price = new BigDecimal(d[3].toString());

                BigDecimal[] conv = inventoryService.convertLineToBase(itemId, uomId, qty, price);
                inventoryService.stockOut(itemId, 1L, conv[0], grnDate,
                    "GRN_VOID", id, grnNo, "Reversal void GRN " + grnNo);
                totalValue = totalValue.add(conv[0].multiply(conv[1]));
            }

            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                List<JournalService.JournalLine> lines = List.of(
                    new JournalService.JournalLine("2105", totalValue, BigDecimal.ZERO,
                        "Reversal GR/IR dari void " + grnNo),
                    new JournalService.JournalLine("1301", BigDecimal.ZERO, totalValue,
                        "Reversal persediaan dari void " + grnNo)
                );
                journalService.createJournal(grnTenant, grnCompany, grnDate, "GRN_VOID",
                    "Reversal journal dari void GRN [" + grnNo + "]", lines);
            }
        }

        // Set VOID
        GrnHdr grn = repo.findById(id).orElseThrow();
        grn.setStatus("VOID");
        repo.save(grn);

        String msg = "GRN " + grnNo + " berhasil di-void";
        if ("CONFIRMED".equals(status)) msg += " dan stok telah di-reverse.";
        ra.addFlashAttribute("success", msg + ".");
        });
    } catch (Exception e) {
        ra.addFlashAttribute("error", "Gagal void GRN: " + rootMessage(e));
    }
    return "redirect:/ui/purchasing/grn";
}

    @PostMapping("/{id}/delete")
    public String deleteGrn(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
            List<?> statusList = em.createNativeQuery("SELECT status FROM pur.grn_hdr WHERE id = :id")
                .setParameter("id", id).getResultList();
            String status = statusList.isEmpty() ? "" : statusList.get(0).toString();

            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya GRN berstatus DRAFT yang bisa dihapus. Status: " + status);
                return;
            }

            List<?> piRef = em.createNativeQuery("SELECT id FROM pur.purchase_invoice_hdr WHERE grn_id = :id")
                .setParameter("id", id).getResultList();
            if (!piRef.isEmpty()) {
                ra.addFlashAttribute("error", "GRN tidak bisa dihapus karena sudah memiliki Invoice.");
                return;
            }

            em.createNativeQuery("DELETE FROM pur.grn_dtl WHERE header_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM pur.grn_hdr WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "GRN berhasil dihapus.");
            });
        } catch (Exception e) { ra.addFlashAttribute("error", "Gagal: " + rootMessage(e)); }
        return "redirect:/ui/purchasing/grn";
    }

    @GetMapping("/{id}/edit")
    public String editGrn(@PathVariable Long id, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Fitur Edit GRN belum tersedia.");
        return "redirect:/ui/purchasing/grn";
    }

    /** Update qty diterima per baris detail (GRN DRAFT saja). */
    @PostMapping("/{id}/update-qty")
    public String updateQty(@PathVariable Long id,
                            @RequestParam("detailId") ArrayList<Long> detailIds,
                            @RequestParam("qty") ArrayList<BigDecimal> qtys,
                            RedirectAttributes ra) {
        try {
            if (detailIds.size() != qtys.size()) {
                throw new IllegalArgumentException("Jumlah detailId dan qty tidak konsisten.");
            }
            var lines = new ArrayList<GrnService.LineQty>();
            for (int i = 0; i < detailIds.size(); i++) {
                lines.add(new GrnService.LineQty(detailIds.get(i), qtys.get(i)));
            }
            service.updateQty(id, lines);
            ra.addFlashAttribute("success", "Qty GRN berhasil diupdate.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update qty: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/grn/" + id + "/view";
    }

    // ============ API: Get PO Details (untuk auto-fill GRN form) ============
    @GetMapping("/api/po-details/{id}")
    @ResponseBody
    public java.util.List<java.util.Map<String, Object>> getPoDetails(@PathVariable Long id) {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        try {
            String sql = "SELECT d.id, d.item_id, i.code, i.name, d.qty, d.uom_id, u.code AS uom_code, " +
                         "COALESCE((SELECT SUM(g.qty_received) FROM pur.grn_dtl g " +
                         "  JOIN pur.grn_hdr gh ON gh.id = g.header_id " +
                         "  WHERE g.item_id = d.item_id AND gh.po_id = :id AND gh.status <> 'VOID'), 0) AS qty_received " +
                         "FROM pur.purchase_order_dtl d " +
                         "LEFT JOIN md.item i ON i.id = d.item_id " +
                         "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                         "WHERE d.header_id = :id ORDER BY d.id";
            java.util.List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("id", id).getResultList();
            for (Object[] row : rows) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("poDetailId", row[0]);
                map.put("itemId", row[1]);
                map.put("itemCode", row[2] != null ? row[2].toString() : "");
                map.put("itemName", row[3] != null ? row[3].toString() : "");
                map.put("qty", row[4] != null ? new java.math.BigDecimal(row[4].toString()).stripTrailingZeros().toPlainString() : 0);
                map.put("uomId", row[5]);
                map.put("uomCode", row[6] != null ? row[6].toString() : "");
                map.put("qtyReceived", row[7] != null ? new java.math.BigDecimal(row[7].toString()).stripTrailingZeros().toPlainString() : 0);
                list.add(map);
            }
        } catch (Exception e) {
            java.util.Map<String, Object> err = new java.util.HashMap<>();
            err.put("error", e.getMessage());
            list.add(err);
        }
        return list;
    }

    private String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }
}
