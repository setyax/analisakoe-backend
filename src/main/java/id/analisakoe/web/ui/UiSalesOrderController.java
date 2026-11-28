package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.repository.CustomerRepository;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.sal.repository.SalesOrderRepository;
import id.analisakoe.sal.service.SalesOrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/ui/sales/orders")
public class UiSalesOrderController {

    @Autowired
    private ContextService context;

    private final SalesOrderService service;
    private final SalesOrderRepository repo;
    private final MenuService menuService;
    private final CustomerRepository custRepo;
    private final ItemRepository itemRepo;
    private final UomRepository uomRepo;

        @PersistenceContext
    private EntityManager em;

    public UiSalesOrderController(SalesOrderService service, SalesOrderRepository repo, MenuService menuService,
                                  CustomerRepository custRepo, ItemRepository itemRepo, UomRepository uomRepo) {
        this.service = service; this.repo = repo; this.menuService = menuService;
        this.custRepo = custRepo; this.itemRepo = itemRepo; this.uomRepo = uomRepo;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Sales Orders");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        try {
            // URUTAN KOLOM: Tgl, No SO, Customer, Currency, Total, Remarks, Status, Salesperson
            String sql = "SELECT h.id, COALESCE(to_char(h.doc_date, 'DD-MM-YYYY'), '-') AS doc_date, h.doc_no, " +
                         "COALESCE(c.name, '-') AS customer_name, " +
                         "COALESCE(h.currency, 'IDR') AS currency, " +
                         "COALESCE(h.total_amount, 0) AS total_amount, " +
                         "COALESCE(h.remarks, '-') AS remarks, " +
                         "COALESCE(h.status, 'DRAFT') AS status, " +
                         "COALESCE(sp.name, '-') AS salesperson " +
                         "FROM sal.sales_order_hdr h " +
                         "LEFT JOIN md.customer c ON h.customer_id = c.id " +
                         "LEFT JOIN md.salesperson sp ON h.salesperson_id = sp.id " +
                         "WHERE h.tenant_id = :tenantId AND h.company_id = :compId " +
                         "ORDER BY h.id DESC";
            List<Object[]> salesOrders = em.createNativeQuery(sql)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", compId)
                    .getResultList();
            model.addAttribute("salesOrders", salesOrders);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Gagal memuat Sales Order: " + e.getMessage());
            model.addAttribute("salesOrders", new java.util.ArrayList<>());
        }

        return "ui/sales/order_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Sales Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        model.addAttribute("customers", custRepo.findByTenantIdAndCompanyIdAndDeletedAtIsNull(context.tenantId(), context.companyId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("items", itemRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("salespersons", em.createNativeQuery(
                "SELECT id, code || ' - ' || name FROM md.salesperson WHERE is_active = true "
                + "AND tenant_id = :tenantId AND company_id = :compId ORDER BY code")
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", true);
        return "ui/sales/form";
    }

    @PostMapping
    public String save(@RequestParam LocalDate docDate, @RequestParam Long customerId,
                       @RequestParam String currency, @RequestParam String taxType,
                       @RequestParam(required = false) String remarks,
                       @RequestParam(value = "salespersonId", required = false) Long salespersonId,
                       @RequestParam(value = "itemId", required = false) ArrayList<Long> itemIds,
                       @RequestParam(value = "uomId",  required = false) ArrayList<Long> uomIds,
                       @RequestParam(value = "qty",    required = false) ArrayList<BigDecimal> qtys,
                       @RequestParam(value = "price",  required = false) ArrayList<BigDecimal> prices,
                       RedirectAttributes ra) {
        // FIX: guard kalau form kosong
        if (itemIds == null || itemIds.isEmpty()) {
            ra.addFlashAttribute("error", "Tambahkan minimal satu baris item sebelum menyimpan.");
            return "redirect:/ui/sales/orders/new";
        }
        if (itemIds.size() != uomIds.size() || itemIds.size() != qtys.size() || itemIds.size() != prices.size()) {
            ra.addFlashAttribute("error", "Jumlah item/UOM/qty/harga tidak konsisten.");
            return "redirect:/ui/sales/orders/new";
        }
        try {
            var lines = new ArrayList<SalesOrderService.LineRequest>();
            for (int i = 0; i < itemIds.size(); i++) {
                lines.add(new SalesOrderService.LineRequest(itemIds.get(i), uomIds.get(i), qtys.get(i), prices.get(i)));
            }
            service.create(docDate, customerId, remarks, currency, taxType, lines, salespersonId);
            ra.addFlashAttribute("success", "Sales Order berhasil dibuat.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/sales/orders";
    }

    @PostMapping("/{id}/confirm")
    public String confirmOrder(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.confirmOrder(id);
            ra.addFlashAttribute("success", "Sales Order berhasil dikonfirmasi.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal Konfirmasi: " + e.getMessage());
        }
        return "redirect:/ui/sales/orders";
    }

    // FIX #1: handler GET untuk /{id} — redirect ke edit (belum ada halaman detail terpisah)
    // Ini yang bikin GET /api/ui/sales/orders/4 sekarang tidak lagi 405/500.
        @PostMapping("/{id}/unconfirm")
    @Transactional
    public String unconfirmOrder(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var so = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() ->
                new IllegalArgumentException("Sales Order #" + id + " tidak ditemukan."));
            String st = so.getStatus();

            if ("CONFIRMED".equals(st)) {
                // boleh langsung unconfirm (belum ada DO)
            } else if ("DELIVERED".equals(st)) {
                Long activeDo = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM sal.delivery_order_hdr WHERE so_id = :id AND status <> 'VOID' "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .getSingleResult()).longValue();
                if (activeDo > 0) {
                    ra.addFlashAttribute("error",
                        "Tidak bisa unconfirm: masih ada " + activeDo +
                        " Delivery Order aktif. Void DO terlebih dahulu.");
                    return "redirect:/ui/sales/orders";
                }
            } else {
                ra.addFlashAttribute("error", "Status " + st + " tidak bisa di-unconfirm.");
                return "redirect:/ui/sales/orders";
            }

            em.createNativeQuery("UPDATE sal.sales_order_hdr SET status = 'DRAFT' WHERE id = :id "
              + "AND tenant_id = :tenantId AND company_id = :compId")
              .setParameter("id", id)
              .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
              .executeUpdate();
            ra.addFlashAttribute("success", "Sales Order dikembalikan ke DRAFT, siap diedit.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal Unconfirm: " + e.getMessage());
        }
        return "redirect:/ui/sales/orders";
    }
    @GetMapping("/api/item-price/{itemId}")
    @ResponseBody
    public Map<String, Object> itemPrice(@PathVariable Long itemId,
                                         @RequestParam(required = false) String currency,
                                         @RequestParam(required = false) Long uomId) {
        Map<String, Object> res = new HashMap<>();
        try {
            String baseSql = "SELECT ip.price, ip.currency, ip.uom_id, u.code " +
                "FROM md.item_price ip " +
                "LEFT JOIN md.uom u ON u.id = ip.uom_id " +
                "WHERE ip.item_id = :itemId " +
                "  AND ip.status = 'ACTIVE' " +
                "  AND ip.valid_from <= CURRENT_DATE " +
                "  AND (ip.valid_to IS NULL OR ip.valid_to >= CURRENT_DATE) ";

            jakarta.persistence.Query query;
            if (uomId != null) {
                // Harga untuk satuan tertentu
                query = em.createNativeQuery(baseSql + " AND ip.uom_id = :uomId ORDER BY ip.valid_from DESC LIMIT 1")
                        .setParameter("itemId", itemId).setParameter("uomId", uomId);
            } else if (currency != null && !currency.isEmpty()) {
                query = em.createNativeQuery(baseSql + " AND ip.currency = :currency ORDER BY ip.valid_from DESC LIMIT 1")
                        .setParameter("itemId", itemId).setParameter("currency", currency);
            } else {
                query = em.createNativeQuery(baseSql + " ORDER BY ip.valid_from DESC LIMIT 1")
                        .setParameter("itemId", itemId);
            }

            List<Object[]> rows = query.getResultList();

            if (rows.isEmpty()) {
                res.put("found", false);
            } else {
                Object[] r = rows.get(0);
                res.put("found", true);
                res.put("price", r[0]);
                res.put("currency", r[1]);
                res.put("uomId", r[2]);
                res.put("uomCode", r[3]);
            }
        } catch (Exception e) {
            res.put("found", false);
            res.put("error", e.getMessage());
        }
        return res;
    }
@GetMapping("/{id}")
    public String detail(@PathVariable Long id) {
        return "redirect:/ui/sales/orders/" + id + "/edit";
    }

                @PostMapping("/{id}/void")
    public String voidOrder(@PathVariable Long id, RedirectAttributes ra) {
        List<?> res = em.createNativeQuery(
            "SELECT status FROM sal.sales_order_hdr WHERE id = :id "
            + "AND tenant_id = :tenantId AND company_id = :compId")
            .setParameter("id", id)
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .getResultList();

        if (res.isEmpty()) {
            ra.addFlashAttribute("error", "Sales Order #" + id + " tidak ditemukan.");
            return "redirect:/ui/sales/orders";
        }

        String st = res.get(0).toString();

        if ("VOID".equals(st)) {
            ra.addFlashAttribute("error", "Sales Order sudah VOID.");
            return "redirect:/ui/sales/orders";
        }
        if (!"DRAFT".equals(st)) {
            ra.addFlashAttribute("error",
                "Hanya Sales Order berstatus DRAFT yang bisa di-void. Status sekarang: " + st +
                ". Gunakan Unconfirm terlebih dahulu.");
            return "redirect:/ui/sales/orders";
        }

        int n = em.createNativeQuery(
            "UPDATE sal.sales_order_hdr SET status = 'VOID' WHERE id = :id AND status = 'DRAFT' "
            + "AND tenant_id = :tenantId AND company_id = :compId")
            .setParameter("id", id)
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .executeUpdate();

        ra.addFlashAttribute(n > 0 ? "success" : "error",
            n > 0 ? "Sales Order berhasil di-void." : "Gagal void: tidak ada baris terupdate.");

        return "redirect:/ui/sales/orders";
    }
	
@GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Sales Order");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        var order = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow();
        model.addAttribute("order", order);
        model.addAttribute("customers", custRepo.findByTenantIdAndCompanyIdAndDeletedAtIsNull(context.tenantId(), context.companyId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("items", itemRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("salespersons", em.createNativeQuery(
                "SELECT id, code || ' - ' || name FROM md.salesperson WHERE is_active = true "
                + "AND tenant_id = :tenantId AND company_id = :compId ORDER BY code")
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList());
        model.addAttribute("isNew", false);
        return "ui/sales/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam LocalDate docDate, @RequestParam Long customerId,
                         @RequestParam String currency, @RequestParam String taxType,
                         @RequestParam(required = false) String remarks,
                         @RequestParam(value = "salespersonId", required = false) Long salespersonId,
                         @RequestParam(value = "itemId", required = false) ArrayList<Long> itemIds,
                         @RequestParam(value = "uomId",  required = false) ArrayList<Long> uomIds,
                         @RequestParam(value = "qty",    required = false) ArrayList<BigDecimal> qtys,
                         @RequestParam(value = "price",  required = false) ArrayList<BigDecimal> prices,
                         RedirectAttributes ra) {
        // FIX #2: guard supaya POST tanpa itemId tidak jadi 500
        if (itemIds == null || itemIds.isEmpty()) {
            ra.addFlashAttribute("error",
                "Tidak ada baris item pada request. Buka halaman Edit terlebih dahulu lalu isi item.");
            return "redirect:/ui/sales/orders/" + id + "/edit";
        }
        if (itemIds.size() != uomIds.size() || itemIds.size() != qtys.size() || itemIds.size() != prices.size()) {
            ra.addFlashAttribute("error", "Jumlah item/UOM/qty/harga tidak konsisten.");
            return "redirect:/ui/sales/orders/" + id + "/edit";
        }
        try {
            var lines = new ArrayList<SalesOrderService.LineRequest>();
            for (int i = 0; i < itemIds.size(); i++) {
                lines.add(new SalesOrderService.LineRequest(itemIds.get(i), uomIds.get(i), qtys.get(i), prices.get(i)));
            }
            service.update(id, docDate, customerId, remarks, currency, taxType, lines, salespersonId);
            ra.addFlashAttribute("success", "Sales Order berhasil diubah.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/sales/orders";
    }
}