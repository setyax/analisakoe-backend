package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.sal.service.DeliveryOrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller TIDAK memakai @Transactional.
 * Operasi tulis ada di DeliveryOrderService (@Transactional); setiap pemanggilan
 * service = satu transaksi sendiri, sehingga batch bisa sukses sebagian.
 */
@Controller
@RequestMapping({"/ui/sales/do", "/ui/sales/delivery-orders"})
public class UiDeliveryOrderController {

    private static final Logger log = LoggerFactory.getLogger(UiDeliveryOrderController.class);
    private static final DateTimeFormatter PRINT_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;
    private final DeliveryOrderService doService;

    public UiDeliveryOrderController(MenuService menuService, DeliveryOrderService doService) {
        this.menuService = menuService;
        this.doService = doService;
    }

    // ============================================================
    // LIST DELIVERY ORDER
    // ============================================================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Delivery Orders");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Object[]> deliveries = new ArrayList<>();
        try {
            String sql = "SELECT d.id, d.doc_no, d.doc_date, d.status, COALESCE(d.remarks, '-'), " +
                         "COALESCE(c.name, 'Customer Tidak Ditemukan') AS customer_name, " +
                         "COALESCE(so.doc_no, '-') AS so_no " +
                         "FROM sal.delivery_order_hdr d " +
                         "LEFT JOIN sal.sales_order_hdr so ON d.so_id = so.id " +
                         "LEFT JOIN md.customer c ON d.customer_id = c.id " +
                         "WHERE d.company_id = :compId " +
                         "ORDER BY d.id DESC";

            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(sql)
                    .setParameter("compId", compId)
                    .getResultList();
            deliveries = rows;
        } catch (Exception e) {
            log.error("Gagal memuat daftar DO", e);
            model.addAttribute("error", "Gagal memuat DO: " + rootMessage(e));
        }

        model.addAttribute("deliveries", deliveries);
        return "ui/sales/do_list";
    }

    // ============================================================
    // FORM TAMBAH DO
    // ============================================================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Delivery Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("nextDocNo", doService.previewDocNo(compId));

        // SO yang masih punya sisa qty (status PARTIAL tidak ada di constraint, jadi hanya CONFIRMED)
        String sqlSO = "SELECT h.id, h.doc_no, COALESCE(c.name, '-'), h.customer_id, h.status " +
                       "FROM sal.sales_order_hdr h " +
                       "LEFT JOIN md.customer c ON h.customer_id = c.id " +
                       "WHERE h.status = 'CONFIRMED' " +
                       "AND h.company_id = :compId " +
                       "AND EXISTS ( " +
                       "    SELECT 1 FROM sal.sales_order_dtl sod " +
                       "    WHERE sod.header_id = h.id " +
                       "    AND sod.qty > COALESCE(( " +
                       "        SELECT SUM(dod.qty) FROM sal.delivery_order_dtl dod " +
                       "        JOIN sal.delivery_order_hdr doh ON dod.header_id = doh.id " +
                       "        WHERE doh.so_id = h.id AND dod.item_id = sod.item_id " +
                       "        AND doh.status NOT IN ('VOID', 'CANCELLED') " +
                       "    ), 0) " +
                       ") " +
                       "ORDER BY h.id DESC";

        @SuppressWarnings("unchecked")
        List<Object[]> salesOrders = em.createNativeQuery(sqlSO)
                .setParameter("compId", compId)
                .getResultList();
        model.addAttribute("salesOrders", salesOrders);

        return "ui/sales/do_form";
    }

    // ============================================================
    // GET SO DETAIL (AJAX untuk mengisi form saat SO dipilih)
    // ============================================================
    @GetMapping("/so-detail/{soId}")
    @ResponseBody
    public Map<String, Object> getSODetail(@PathVariable Long soId, HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        Map<String, Object> result = new HashMap<>();

        try {
            String sql = "SELECT sod.item_id, i.code, i.name, sod.uom_id, u.code AS uom_code, " +
                         "sod.qty AS so_qty, " +
                         "COALESCE(( " +
                         "    SELECT SUM(dod2.qty) FROM sal.delivery_order_dtl dod2 " +
                         "    JOIN sal.delivery_order_hdr doh2 ON dod2.header_id = doh2.id " +
                         "    WHERE doh2.so_id = sod.header_id AND dod2.item_id = sod.item_id " +
                         "    AND doh2.status NOT IN ('VOID', 'CANCELLED') " +
                         "), 0) AS delivered_qty, " +
                         "sod.price " +
                         "FROM sal.sales_order_dtl sod " +
                         "JOIN sal.sales_order_hdr soh ON sod.header_id = soh.id " +
                         "JOIN md.item i ON sod.item_id = i.id " +
                         "LEFT JOIN md.uom u ON sod.uom_id = u.id " +
                         "WHERE sod.header_id = :soId AND soh.company_id = :compId";

            @SuppressWarnings("unchecked")
            List<Object[]> details = em.createNativeQuery(sql)
                    .setParameter("soId", soId)
                    .setParameter("compId", compId)
                    .getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : details) {
                BigDecimal soQty = new BigDecimal(row[5].toString());
                BigDecimal deliveredQty = new BigDecimal(row[6].toString());
                BigDecimal remainingQty = soQty.subtract(deliveredQty);

                if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("itemId", row[0]);
                    item.put("itemCode", row[1]);
                    item.put("itemName", row[2]);
                    item.put("uomId", row[3]);
                    item.put("uomCode", row[4] != null ? row[4] : "-");
                    item.put("soQty", soQty);
                    item.put("deliveredQty", deliveredQty);
                    item.put("remainingQty", remainingQty);
                    item.put("price", row[7]);
                    items.add(item);
                }
            }
            result.put("success", true);
            result.put("items", items);
        } catch (Exception e) {
            log.error("Gagal memuat detail SO {}", soId, e);
            result.put("success", false);
            result.put("message", rootMessage(e));
        }
        return result;
    }

    // ============================================================
    // SIMPAN DO
    // ============================================================
    @PostMapping
    public String save(@RequestParam Long soId,
                       @RequestParam LocalDate doDate,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemIds") List<Long> itemIds,
                       @RequestParam(value = "uomIds", required = false) List<Long> uomIds,
                       @RequestParam("qtys") List<BigDecimal> qtys,
                       @RequestParam(value = "prices", required = false) List<BigDecimal> prices,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        try {
            String docNo = doService.create(compId, soId, doDate, remarks,
                    itemIds, uomIds, qtys, prices);
            ra.addFlashAttribute("success", "Delivery Order " + docNo + " berhasil dibuat (DRAFT).");
        } catch (Exception e) {
            log.error("Gagal menyimpan DO", e);
            ra.addFlashAttribute("error", "Gagal menyimpan DO: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do";
    }

    // ============================================================
    // CONFIRM / UNCONFIRM / VOID / HAPUS (satu DO)
    // ============================================================
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        Long compId = companyId(session);
        try {
            String docNo = doService.confirm(id, compId);
            ra.addFlashAttribute("success", "DO " + docNo + " berhasil di-confirm menjadi OPEN. Stok belum dikurangi.");
        } catch (Exception e) {
            log.error("Gagal confirm DO {}", id, e);
            ra.addFlashAttribute("error", "Gagal confirm: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do";
    }

    @PostMapping("/{id}/unconfirm")
    public String unconfirm(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        Long compId = companyId(session);
        try {
            String docNo = doService.unconfirm(id, compId);
            ra.addFlashAttribute("success", "DO " + docNo + " dikembalikan ke DRAFT. Tidak ada perubahan stok.");
        } catch (Exception e) {
            log.error("Gagal unconfirm DO {}", id, e);
            ra.addFlashAttribute("error", "Gagal unconfirm: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do";
    }

    @PostMapping("/{id}/void")
    public String voidDo(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        Long compId = companyId(session);
        try {
            String docNo = doService.voidDo(id, compId);
            ra.addFlashAttribute("success", "DO " + docNo + " berhasil di-void.");
        } catch (Exception e) {
            log.error("Gagal void DO {}", id, e);
            ra.addFlashAttribute("error", "Gagal void: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        Long compId = companyId(session);
        try {
            doService.delete(id, compId);
            ra.addFlashAttribute("success", "Delivery Order berhasil dihapus.");
        } catch (Exception e) {
            log.error("Gagal hapus DO {}", id, e);
            ra.addFlashAttribute("error", "Gagal hapus: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do";
    }

    // ============================================================
    // BATCH CONFIRM / HAPUS (dipanggil tombol batch di do_list.html)
    // Setiap DO diproses di transaksi sendiri: yang gagal tidak membatalkan yang lain.
    // ============================================================
    @PostMapping("/batch/confirm")
    public String batchConfirm(@RequestParam(value = "ids", required = false) List<Long> ids,
                               HttpSession session, RedirectAttributes ra) {
        Long compId = companyId(session);
        runBatch(ids, "Confirm", id -> doService.confirm(id, compId), ra);
        return "redirect:/ui/sales/do";
    }

    @PostMapping("/batch/delete")
    public String batchDelete(@RequestParam(value = "ids", required = false) List<Long> ids,
                              HttpSession session, RedirectAttributes ra) {
        Long compId = companyId(session);
        runBatch(ids, "Hapus", id -> doService.delete(id, compId), ra);
        return "redirect:/ui/sales/do";
    }

    private void runBatch(List<Long> ids, String label, Consumer<Long> action, RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "Tidak ada DO yang dipilih.");
            return;
        }
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                action.accept(id);
                ok++;
            } catch (Exception e) {
                log.error("Batch {} gagal untuk DO {}", label, id, e);
                failed.add("ID " + id + ": " + rootMessage(e));
            }
        }
        if (ok > 0) {
            ra.addFlashAttribute("success", label + " batch berhasil: " + ok + " DO.");
        }
        if (!failed.isEmpty()) {
            ra.addFlashAttribute("error", label + " batch gagal " + failed.size() + " DO -> "
                    + String.join(" | ", failed));
        }
    }

    // ============================================================
    // BATCH PICKING LIST (gabungan item dari DO terpilih)
    // ============================================================
        // ============================================================
    // BATCH PICKING LIST -> template ui/print/picking_batch
    // Model: company.name, dos[docNo, customer, docDate], items[code, name, uom, qty]
    // ============================================================
    @PostMapping("/batch/picking-list")
    public String batchPickingList(@RequestParam(value = "ids", required = false) List<Long> ids,
                                   HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "Tidak ada DO yang dipilih.");
            return "redirect:/ui/sales/do";
        }

        try {
            @SuppressWarnings("unchecked")
            List<Object[]> hdrs = em.createNativeQuery(
                    "SELECT h.doc_no, h.doc_date, COALESCE(c.name, '-') " +
                    "FROM sal.delivery_order_hdr h " +
                    "LEFT JOIN md.customer c ON h.customer_id = c.id " +
                    "WHERE h.id IN (:ids) AND h.company_id = :compId " +
                    "AND h.status NOT IN ('VOID', 'CANCELLED') " +
                    "ORDER BY h.doc_no")
                    .setParameter("ids", ids)
                    .setParameter("compId", compId)
                    .getResultList();

            if (hdrs.isEmpty()) {
                ra.addFlashAttribute("error", "DO terpilih tidak ditemukan atau sudah VOID/CANCELLED.");
                return "redirect:/ui/sales/do";
            }

            List<Map<String, Object>> dos = new ArrayList<>();
            for (Object[] h : hdrs) {
                Map<String, Object> m = new HashMap<>();
                m.put("docNo", h[0]);
                m.put("docDate", String.valueOf(h[1]));
                m.put("customer", h[2]);
                dos.add(m);
            }

            @SuppressWarnings("unchecked")
            List<Object[]> lines = em.createNativeQuery(
                    "SELECT COALESCE(i.code, '-'), COALESCE(i.name, '-'), COALESCE(u.code, '-'), SUM(d.qty) " +
                    "FROM sal.delivery_order_dtl d " +
                    "JOIN sal.delivery_order_hdr h ON d.header_id = h.id " +
                    "LEFT JOIN md.item i ON d.item_id = i.id " +
                    "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                    "WHERE h.id IN (:ids) AND h.company_id = :compId " +
                    "AND h.status NOT IN ('VOID', 'CANCELLED') " +
                    "GROUP BY i.code, i.name, u.code " +
                    "ORDER BY i.code")
                    .setParameter("ids", ids)
                    .setParameter("compId", compId)
                    .getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] l : lines) {
                items.add(itemMap(l[0], l[1], l[2], l[3]));
            }

            model.addAttribute("company", companyMap(user));
            model.addAttribute("dos", dos);
            model.addAttribute("items", items);
            return "ui/print/picking_batch";
        } catch (Exception e) {
            log.error("Gagal membuat picking list", e);
            ra.addFlashAttribute("error", "Gagal membuat picking list: " + rootMessage(e));
            return "redirect:/ui/sales/do";
        }
    }

    // ============================================================
    // BATCH LOADSHEET -> template ui/print/loadsheet_batch
    // Model: company.name, dos[docNo, customer, items[code, name, uom, qty]]
    // ============================================================
    @PostMapping("/batch/loadsheet")
    public String batchLoadsheet(@RequestParam(value = "ids", required = false) List<Long> ids,
                                 HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "Tidak ada DO yang dipilih.");
            return "redirect:/ui/sales/do";
        }

        try {
                        @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT h.id, h.doc_no, h.doc_date, COALESCE(c.name, '-'), " +
                    "COALESCE(so.doc_no, '-'), COALESCE(i.code, '-'), COALESCE(i.name, '-'), " +
                    "COALESCE(u.code, '-'), d.qty " +
                    "FROM sal.delivery_order_hdr h " +
                    "JOIN sal.delivery_order_dtl d ON d.header_id = h.id " +
                    "LEFT JOIN md.customer c ON h.customer_id = c.id " +
                    "LEFT JOIN sal.sales_order_hdr so ON h.so_id = so.id " +
                    "LEFT JOIN md.item i ON d.item_id = i.id " +
                    "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                    "WHERE h.id IN (:ids) AND h.company_id = :compId " +
                    "AND h.status NOT IN ('VOID', 'CANCELLED') " +
                    "ORDER BY h.doc_no, d.id")
                    .setParameter("ids", ids)
                    .setParameter("compId", compId)
                    .getResultList();

            if (rows.isEmpty()) {
                ra.addFlashAttribute("error", "DO terpilih tidak ditemukan atau sudah VOID/CANCELLED.");
                return "redirect:/ui/sales/do";
            }

            Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
            for (Object[] r : rows) {
                Long hid = ((Number) r[0]).longValue();
                Map<String, Object> d = grouped.get(hid);
                if (d == null) {
                    d = new HashMap<>();
                    d.put("docNo", r[1]);
                    d.put("docDate", String.valueOf(r[2]));
                    d.put("customer", r[3]);
                    d.put("soNo", r[4]);
                    d.put("items", new ArrayList<Map<String, Object>>());
                    grouped.put(hid, d);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> its = (List<Map<String, Object>>) d.get("items");
                its.add(itemMap(r[5], r[6], r[7], r[8]));
            }

            model.addAttribute("company", companyMap(user));
            model.addAttribute("dos", new ArrayList<>(grouped.values()));
            return "ui/print/loadsheet_batch";
        } catch (Exception e) {
            log.error("Gagal membuat loadsheet", e);
            ra.addFlashAttribute("error", "Gagal membuat loadsheet: " + rootMessage(e));
            return "redirect:/ui/sales/do";
        }
    }

    // ============================================================
    // PRINT DO (satu DO). Harga diambil dari baris SO karena
    // delivery_order_dtl tidak punya kolom price/subtotal.
    // ============================================================
    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "View Delivery Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                    "SELECT d.doc_no, d.doc_date, COALESCE(d.remarks, ''), COALESCE(c.name, '-'), " +
                    "COALESCE(so.doc_no, '-'), d.status, d.company_id " +
                    "FROM sal.delivery_order_hdr d " +
                    "LEFT JOIN md.customer c ON d.customer_id = c.id " +
                    "LEFT JOIN sal.sales_order_hdr so ON d.so_id = so.id " +
                    "WHERE d.id = :id AND d.company_id = :compId")
                    .setParameter("id", id).setParameter("compId", compId)
                    .getSingleResult();

            model.addAttribute("docNo", hdr[0]);
            Object dd = hdr[1];
            model.addAttribute("docDate", dd != null ? (dd instanceof java.sql.Timestamp
                    ? ((java.sql.Timestamp) dd).toLocalDateTime().toLocalDate()
                    : ((java.sql.Date) dd).toLocalDate()) : null);
            model.addAttribute("remarks", hdr[2]);
            model.addAttribute("customerName", hdr[3]);
            model.addAttribute("soNo", hdr[4]);
            model.addAttribute("status", hdr[5]);

            List<?> detailRows = em.createNativeQuery(
                    "SELECT i.code, i.name, d.qty, COALESCE(u.code, '-') " +
                    "FROM sal.delivery_order_dtl d " +
                    "LEFT JOIN md.item i ON d.item_id = i.id " +
                    "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                    "WHERE d.header_id = :id ORDER BY d.id")
                    .setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            java.math.BigDecimal totalQty = java.math.BigDecimal.ZERO;
            for (Object value : detailRows) {
                Object[] row = (Object[]) value;
                Map<String, Object> m = new HashMap<>();
                m.put("code", row[0] != null ? row[0].toString() : "-");
                m.put("name", row[1] != null ? row[1].toString() : "-");
                BigDecimal qty = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                m.put("qty", qty.stripTrailingZeros().toPlainString());
                m.put("uom", row[3] != null ? row[3].toString() : "-");
                totalQty = totalQty.add(qty);
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("totalQty", totalQty.stripTrailingZeros().toPlainString());
            model.addAttribute("totalLines", items.size());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat DO: " + e.getMessage());
            return "redirect:/ui/sales/do";
        }
        return "ui/sales/do_view";
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        model.addAttribute("user", user);
        // Data perusahaan aktif untuk kop surat
        model.addAttribute("company", companyMap(user));

        // Default atribut template
        model.addAttribute("docNo", "-");
        model.addAttribute("docDate", "-");
        model.addAttribute("customerName", "-");
        model.addAttribute("soNo", "-");
        model.addAttribute("remarks", "");
        model.addAttribute("items", new ArrayList<Map<String, Object>>());

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                    "SELECT d.id, d.doc_no, d.doc_date, COALESCE(d.remarks, ''), " +
                    "COALESCE(c.name, '-'), '-', COALESCE(so.doc_no, '-'), " +
                    "COALESCE(comp.name, '-') " +
                    "FROM sal.delivery_order_hdr d " +
                    "LEFT JOIN md.customer c ON d.customer_id = c.id " +
                    "LEFT JOIN sal.sales_order_hdr so ON d.so_id = so.id " +
                    "LEFT JOIN sys.company comp ON d.company_id = comp.id " +
                    "WHERE d.id = :id AND d.company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("compId", compId)
                    .getSingleResult();

            Map<String, Object> header = new HashMap<>();
            header.put("docNo", hdr[1]);
            header.put("docDate", hdr[2]);
            header.put("remarks", hdr[3]);
            header.put("customerName", hdr[4]);
            header.put("customerAddress", hdr[5]);
            header.put("soNo", hdr[6]);
            header.put("companyName", hdr[7]);
            model.addAttribute("header", header);
            model.addAttribute("doId", hdr[0]);
            model.addAttribute("docNo", header.get("docNo"));
            model.addAttribute("docDate", header.get("docDate"));
            model.addAttribute("customerName", header.get("customerName"));
            model.addAttribute("soNo", header.get("soNo"));
            model.addAttribute("remarks", header.get("remarks"));

            List<?> detailRows = em.createNativeQuery(
                    "SELECT i.code, i.name, d.qty, COALESCE(u.code, '-'), " +
                    "COALESCE(sod.price, 0), d.qty * COALESCE(sod.price, 0) " +
                    "FROM sal.delivery_order_dtl d " +
                    "JOIN sal.delivery_order_hdr h ON d.header_id = h.id " +
                    "LEFT JOIN sal.sales_order_dtl sod ON sod.header_id = h.so_id AND sod.item_id = d.item_id " +
                    "LEFT JOIN md.item i ON d.item_id = i.id " +
                    "LEFT JOIN md.uom u ON d.uom_id = u.id " +
                    "WHERE d.header_id = :id ORDER BY d.id")
                    .setParameter("id", id)
                    .getResultList();

            List<Map<String, Object>> items = new ArrayList<>();

            for (Object value : detailRows) {
                Object[] row = (Object[]) value;

                Map<String, Object> item = new HashMap<>();
                item.put("code", row[0]);
                item.put("name", row[1]);
                item.put("qty", formatQty(row[2]));
                item.put("uom", row[3]);
                item.put("price", row[4]);
                item.put("subtotal", row[5]);

                items.add(item);
            }

            model.addAttribute("items", items);
        } catch (Exception e) {
            log.error("Gagal memuat print DO {}", id, e);
            model.addAttribute("error", "Gagal memuat print: " + rootMessage(e));
        }

        return "ui/print/delivery_order";
    }

    // ============================================================
    // HELPER
    // ============================================================
    private Long companyId(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        return user.getActiveCompanyId();
    }

    /** Pesan dari exception paling dalam (penyebab asli). */
    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.toString();
    }
	
    private String formatQty(Object value) {
        if (value == null) {
            return "0";
        }

        try {
            return new java.math.BigDecimal(value.toString())
                    .stripTrailingZeros()
                    .toPlainString();
        } catch (NumberFormatException e) {
            return value.toString();
        }
    }
	    private Map<String, Object> companyMap(SessionUser user) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", user.getActiveCompanyName());
        return m;
    }

    private Map<String, Object> itemMap(Object code, Object name, Object uom, Object qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("uom", uom);
        m.put("qty", new BigDecimal(qty.toString()));
        return m;
    }
}
