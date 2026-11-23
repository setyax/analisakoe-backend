package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.service.JournalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/purchasing/returns")
public class UiPurchaseReturnController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;
    private final JournalService journalService;

    public UiPurchaseReturnController(MenuService menuService, JournalService journalService) {
        this.menuService = menuService;
        this.journalService = journalService;
    }

    // ============================================================
    // LIST
    // ============================================================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Purchase Returns");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.doc_date, COALESCE(s.name, '-') AS supplier_name, " +
                     "h.total_amount, COALESCE(h.status, 'DRAFT') " +
                     "FROM pur.purchase_return_hdr h " +
                     "LEFT JOIN md.supplier s ON h.supplier_id = s.id " +
                     "WHERE h.company_id = :compId " +
                     "ORDER BY h.id DESC";
        List<Object[]> returns = em.createNativeQuery(sql)
                .setParameter("compId", compId)
                .getResultList();
        model.addAttribute("returns", returns);

        return "ui/purchasing/purchase_return_list";
    }

    // ============================================================
    // FORM TAMBAH
    // ============================================================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Purchase Return");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("nextDocNo", generateDocNo(user.getActiveCompanyId()));

        // 1. Supplier
        List<Object[]> suppliers = em.createNativeQuery(
                "SELECT id, name FROM md.supplier WHERE deleted_at IS NULL ORDER BY name")
                .getResultList();
        model.addAttribute("suppliers", suppliers);

        // 2. Item
        List<Object[]> items = em.createNativeQuery(
                "SELECT id, code, name FROM md.item WHERE deleted_at IS NULL ORDER BY name")
                .getResultList();
        model.addAttribute("items", items);

        // 3. UOM
        List<Object[]> uoms = em.createNativeQuery(
                "SELECT id, code, name FROM md.uom ORDER BY code")
                .getResultList();
        model.addAttribute("uoms", uoms);

        // 4. Price List + UOM dari price list
        Map<String, BigDecimal> priceMap = new HashMap<>();
        Map<String, Long> uomMap = new HashMap<>();
        try {
            List<Object[]> prices = em.createNativeQuery(
                    "SELECT supplier_id, item_id, price, uom_id FROM pur.price_list")
                    .getResultList();
            for (Object[] p : prices) {
                String key = p[0] + "-" + p[1];
                priceMap.put(key, (BigDecimal) p[2]);
                if (p[3] != null) {
                    uomMap.put(key, ((Number) p[3]).longValue());
                }
            }
        } catch (Exception e) {
            System.out.println("Gagal memuat price list: " + e.getMessage());
        }
        model.addAttribute("priceMap", priceMap);
        model.addAttribute("uomMap", uomMap);

        return "ui/purchasing/purchase_return_form";
    }

    // ============================================================
    // SIMPAN
    // ============================================================
        @Transactional
    @PostMapping
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam Long supplierId,
                       @RequestParam String docNo,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemIds") List<Long> itemIds,
                       @RequestParam("qtys") List<BigDecimal> qtys,
                       @RequestParam("prices") List<BigDecimal> prices,
                       @RequestParam(value = "uomIds", required = false) List<Long> uomIds,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        // 1. Validasi & hitung total
        if (itemIds == null || itemIds.isEmpty()) {
            ra.addFlashAttribute("error", "Minimal harus ada 1 item yang diretur.");
            return "redirect:/ui/purchasing/returns/new";
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < itemIds.size(); i++) {
            if (itemIds.get(i) == null) continue;
            BigDecimal qty = qtys.get(i);
            BigDecimal price = prices.get(i);
            if (qty == null || price == null) continue;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalAmount = totalAmount.add(qty.multiply(price));
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            ra.addFlashAttribute("error", "Total retur harus lebih dari 0.");
            return "redirect:/ui/purchasing/returns/new";
        }

        // 2. Insert Header
        Object newIdRaw = em.createNativeQuery(
            "INSERT INTO pur.purchase_return_hdr " +
            "(tenant_id, company_id, doc_no, doc_date, supplier_id, total_amount, remarks, status) " +
            "VALUES (1, :compId, :docNo, :docDate, :supplierId, :totalAmount, :remarks, 'POSTED') " +
            "RETURNING id")
          .setParameter("compId", compId)
          .setParameter("docNo", docNo)
          .setParameter("docDate", docDate)
          .setParameter("supplierId", supplierId)
          .setParameter("totalAmount", totalAmount)
          .setParameter("remarks", remarks)
          .getSingleResult();
        Long headerId = ((Number) newIdRaw).longValue();

        // 3. Siapkan default UOM
        Long defaultUomId = null;
        try {
            Object defUom = em.createNativeQuery("SELECT id FROM md.uom LIMIT 1").getSingleResult();
            if (defUom != null) defaultUomId = ((Number) defUom).longValue();
        } catch (Exception ignored) {}

        // 4. Insert Detail + Update Stok
        Long whId = getDefaultWarehouse(compId);

        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            BigDecimal qty = qtys.get(i);
            BigDecimal price = prices.get(i);

            if (itemId == null || qty == null || price == null) continue;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            Long uomId = null;
            if (uomIds != null && i < uomIds.size() && uomIds.get(i) != null) {
                uomId = uomIds.get(i);
            } else {
                uomId = defaultUomId;
            }
            if (uomId == null) {
                throw new RuntimeException("Tidak ada data UOM. Silakan tambahkan UOM di Master Data.");
            }

            BigDecimal subtotal = qty.multiply(price);

            // Insert Detail
            em.createNativeQuery(
                "INSERT INTO pur.purchase_return_dtl " +
                "(header_id, item_id, uom_id, qty, price, total, subtotal) " +
                "VALUES (:hdrId, :itemId, :uomId, :qty, :price, :total, :subtotal)")
              .setParameter("hdrId", headerId)
              .setParameter("itemId", itemId)
              .setParameter("uomId", uomId)
              .setParameter("qty", qty)
              .setParameter("price", price)
              .setParameter("total", subtotal)
              .setParameter("subtotal", subtotal)
              .executeUpdate();

            // Update Stok
            if (whId != null) {
                // QUERY INI YANG DIPERBAIKI: tenant_id DITAMBAHKAN
                em.createNativeQuery(
                    "INSERT INTO inv.transaction_header " +
                    "(tenant_id, company_id, doc_no, doc_date, doc_type, warehouse_id, remarks, status) " +
                    "VALUES (1, :compId, :docNo, :docDate, 'OUT', :whId, :remarks, 'POSTED')")
                  .setParameter("compId", compId)
                  .setParameter("docNo", docNo + "-ITEM-" + itemId)
                  .setParameter("docDate", docDate)
                  .setParameter("whId", whId)
                  .setParameter("remarks", "Retur Pembelian ke Supplier")
                  .executeUpdate();

                em.createNativeQuery(
                    "UPDATE inv.stock_balance SET qty = qty - :qty " +
                    "WHERE item_id = :itemId AND warehouse_id = :whId")
                  .setParameter("qty", qty)
                  .setParameter("itemId", itemId)
                  .setParameter("whId", whId)
                  .executeUpdate();
            }
        }

        // 5. Jurnal Otomatis
        journalService.createJournal(
            1L, compId,
            docDate,
            "PURCHASE_RETURN",
            "Retur Pembelian " + docNo,
            List.of(
                new JournalService.JournalLine("2101", totalAmount, BigDecimal.ZERO, "Potong Hutang Supplier"),
                new JournalService.JournalLine("1301", BigDecimal.ZERO, totalAmount, "Kurangi Persediaan")
            )
        );

        ra.addFlashAttribute("success", "Purchase Return berhasil disimpan. Stok & Hutang sudah disesuaikan.");
        return "redirect:/ui/purchasing/returns";
    }

    // ============================================================
    // HAPUS
    // ============================================================
    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            Long compId = user.getActiveCompanyId();

            // Cek status
            Object status = em.createNativeQuery(
                    "SELECT status FROM pur.purchase_return_hdr WHERE id = :id AND company_id = :compId")
                    .setParameter("id", id).setParameter("compId", compId).getSingleResult();
            if (!"DRAFT".equals(status) && !"POSTED".equals(status)) {
                throw new Exception("Status tidak valid untuk dihapus.");
            }

            // Hapus detail dulu
            em.createNativeQuery(
                "DELETE FROM pur.purchase_return_dtl WHERE header_id = :id")
              .setParameter("id", id).executeUpdate();

            // Hapus header
            em.createNativeQuery(
                "DELETE FROM pur.purchase_return_hdr WHERE id = :id AND company_id = :compId")
              .setParameter("id", id).setParameter("compId", compId).executeUpdate();

            ra.addFlashAttribute("success", "Purchase Return berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/returns";
    }
	
	    // ============================================================
    // EDIT FORM
    // ============================================================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Purchase Return");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", false);

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT id, doc_no, doc_date, supplier_id, total_amount, remarks, status, tenant_id, company_id " +
                "FROM pur.purchase_return_hdr WHERE id = :id AND company_id = :compId")
                .setParameter("id", id).setParameter("compId", compId).getSingleResult();
            
            if (!"DRAFT".equals(hdr[6])) {
                ra.addFlashAttribute("error", "Hanya dokumen DRAFT yang bisa diedit.");
                return "redirect:/ui/purchasing/returns";
            }

            Map<String, Object> header = new HashMap<>();
            header.put("id", hdr[0]);
            header.put("docNo", hdr[1]);
            header.put("docDate", hdr[2]);
            header.put("supplierId", hdr[3]);
            header.put("totalAmount", hdr[4]);
            header.put("remarks", hdr[5]);
            model.addAttribute("header", header);

            // Detail
            List<Object[]> details = em.createNativeQuery(
                "SELECT item_id, uom_id, qty, price, subtotal FROM pur.purchase_return_dtl WHERE header_id = :id")
                .setParameter("id", id).getResultList();
            model.addAttribute("details", details);

            // Master data
            model.addAttribute("suppliers", em.createNativeQuery(
                "SELECT id, name FROM md.supplier WHERE deleted_at IS NULL ORDER BY name").getResultList());
            model.addAttribute("items", em.createNativeQuery(
                "SELECT id, code, name FROM md.item WHERE deleted_at IS NULL ORDER BY name").getResultList());
            model.addAttribute("uoms", em.createNativeQuery(
                "SELECT id, code, name FROM md.uom ORDER BY code").getResultList());
            model.addAttribute("nextDocNo", hdr[1]);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Retur tidak ditemukan: " + e.getMessage());
            return "redirect:/ui/purchasing/returns";
        }

        return "ui/purchasing/purchase_return_form";
    }

    // ============================================================
    // UPDATE (SIMPAN EDIT)
    // ============================================================
    @Transactional
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam LocalDate docDate,
                         @RequestParam Long supplierId,
                         @RequestParam String docNo,
                         @RequestParam(required = false) String remarks,
                         @RequestParam("itemIds") List<Long> itemIds,
                         @RequestParam("qtys") List<BigDecimal> qtys,
                         @RequestParam("prices") List<BigDecimal> prices,
                         @RequestParam(value = "uomIds", required = false) List<Long> uomIds,
                         HttpSession session,
                         RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        try {
            // Hitung total
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (int i = 0; i < itemIds.size(); i++) {
                if (itemIds.get(i) == null) continue;
                if (qtys.get(i) == null || prices.get(i) == null) continue;
                if (qtys.get(i).compareTo(BigDecimal.ZERO) <= 0) continue;
                totalAmount = totalAmount.add(qtys.get(i).multiply(prices.get(i)));
            }

            // Update header
            em.createNativeQuery(
                "UPDATE pur.purchase_return_hdr SET doc_date = :docDate, supplier_id = :supplierId, " +
                "total_amount = :totalAmount, remarks = :remarks WHERE id = :id AND company_id = :compId")
              .setParameter("docDate", docDate)
              .setParameter("supplierId", supplierId)
              .setParameter("totalAmount", totalAmount)
              .setParameter("remarks", remarks)
              .setParameter("id", id)
              .setParameter("compId", compId)
              .executeUpdate();

            // Hapus detail lama
            em.createNativeQuery("DELETE FROM pur.purchase_return_dtl WHERE header_id = :id")
              .setParameter("id", id).executeUpdate();

            // Insert detail baru
            Long defaultUomId = null;
            try {
                Object defUom = em.createNativeQuery("SELECT id FROM md.uom LIMIT 1").getSingleResult();
                if (defUom != null) defaultUomId = ((Number) defUom).longValue();
            } catch (Exception ignored) {}

            for (int i = 0; i < itemIds.size(); i++) {
                Long itemId = itemIds.get(i);
                BigDecimal qty = qtys.get(i);
                BigDecimal price = prices.get(i);
                if (itemId == null || qty == null || price == null) continue;
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

                Long uomId = (uomIds != null && i < uomIds.size() && uomIds.get(i) != null) 
                             ? uomIds.get(i) : defaultUomId;

                em.createNativeQuery(
                    "INSERT INTO pur.purchase_return_dtl (header_id, item_id, uom_id, qty, price, total, subtotal) " +
                    "VALUES (:hdrId, :itemId, :uomId, :qty, :price, :total, :subtotal)")
                  .setParameter("hdrId", id)
                  .setParameter("itemId", itemId)
                  .setParameter("uomId", uomId)
                  .setParameter("qty", qty)
                  .setParameter("price", price)
                  .setParameter("total", qty.multiply(price))
                  .setParameter("subtotal", qty.multiply(price))
                  .executeUpdate();
            }

            ra.addFlashAttribute("success", "Purchase Return berhasil diupdate.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/returns";
    }

    // ============================================================
    // PRINT
    // ============================================================
    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        model.addAttribute("user", user);

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT h.id, h.doc_no, h.doc_date, h.total_amount, COALESCE(h.remarks,''), " +
                "COALESCE(s.name,'-'), COALESCE(c.name,'-') " +
                "FROM pur.purchase_return_hdr h " +
                "LEFT JOIN md.supplier s ON h.supplier_id = s.id " +
                "LEFT JOIN sys.company c ON h.company_id = c.id " +
                "WHERE h.id = :id AND h.company_id = :compId")
                .setParameter("id", id).setParameter("compId", compId).getSingleResult();

            Map<String, Object> header = new HashMap<>();
            header.put("docNo", hdr[1]);
            header.put("docDate", hdr[2]);
            header.put("totalAmount", hdr[3]);
            header.put("remarks", hdr[4]);
            header.put("supplierName", hdr[5]);
            header.put("companyName", hdr[6]);
            model.addAttribute("header", header);

            model.addAttribute("details", em.createNativeQuery(
                "SELECT d.item_id, i.code, i.name, d.qty, u.code, d.price, d.subtotal " +
                "FROM pur.purchase_return_dtl d " +
                "LEFT JOIN md.item i ON d.item_id = i.id " +
                "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                "WHERE d.header_id = :id ORDER BY d.id")
                .setParameter("id", id).getResultList());

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat print: " + e.getMessage());
        }
        return "ui/purchasing/purchase_return_print";
    }

    // ============================================================
    // HELPER: Generate Doc No
    // ============================================================
    private String generateDocNo(Long compId) {
        try {
            Object result = em.createNativeQuery(
                    "SELECT COUNT(*) FROM pur.purchase_return_hdr WHERE company_id = :compId")
                    .setParameter("compId", compId).getSingleResult();
            Long count = ((Number) result).longValue() + 1;
            return String.format("RET-%d-%04d", LocalDate.now().getYear(), count);
        } catch (Exception e) {
            return "RET-" + System.currentTimeMillis();
        }
    }

    // ============================================================
    // HELPER: Get Default Warehouse
    // ============================================================
    private Long getDefaultWarehouse(Long compId) {
        try {
            Object result = em.createNativeQuery(
                    "SELECT id FROM md.warehouse WHERE company_id = :compId LIMIT 1")
                    .setParameter("compId", compId).getSingleResult();
            return ((Number) result).longValue();
        } catch (Exception e) {
            try {
                Object result = em.createNativeQuery("SELECT id FROM md.warehouse LIMIT 1")
                        .getSingleResult();
                return ((Number) result).longValue();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
