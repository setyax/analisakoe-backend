package id.analisakoe.web.ui;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dedicated UI endpoints for picking, Goods Issue and POD. */
@Controller
@RequestMapping({"/ui/sales/do", "/ui/sales/delivery-orders"})
public class UiDeliveryOrderGoodsIssueController {

    private static final Logger log = LoggerFactory.getLogger(UiDeliveryOrderGoodsIssueController.class);

    @PersistenceContext
    private EntityManager em;

    private final DeliveryOrderService doService;

    public UiDeliveryOrderGoodsIssueController(DeliveryOrderService doService) {
        this.doService = doService;
    }

    @GetMapping("/{id}/goods-issue")
    public String goodsIssuePage(@PathVariable Long id, HttpSession session, Model model) {
        Long compId = activeCompany(session);
        return loadPage(id, compId, model, UUID.randomUUID().toString());
    }

    @PostMapping("/{id}/goods-issue")
    public String postGoodsIssue(@PathVariable Long id,
                                 @RequestParam("detailIds") List<Long> detailIds,
                                 @RequestParam("qtys") List<BigDecimal> quantities,
                                 @RequestParam("idempotencyKey") String idempotencyKey,
                                 HttpSession session, Model model, RedirectAttributes ra) {
        Long compId = activeCompany(session);
        try {
            String giDocNo = doService.postGoodsIssue(id, compId, idempotencyKey, detailIds, quantities);
            ra.addFlashAttribute("success", "Goods Issue " + giDocNo + " berhasil diposting. Stok dan valuasi diperbarui.");
            return "redirect:/ui/sales/do/" + id + "/goods-issue";
        } catch (Exception e) {
            log.error("Gagal Post Goods Issue untuk DO {}", id, e);
            model.addAttribute("error", rootMessage(e));
            return loadPage(id, compId, model, idempotencyKey);
        }
    }

    @PostMapping("/{id}/warehouse")
    public String changeWarehouse(@PathVariable Long id, @RequestParam Long warehouseId,
                                  HttpSession session, RedirectAttributes ra) {
        try {
            doService.changeWarehouse(id, activeCompany(session), warehouseId);
            ra.addFlashAttribute("success", "Warehouse DO berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal mengubah warehouse: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do/" + id + "/goods-issue";
    }

    @PostMapping("/{id}/picking/start")
    public String startPicking(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            doService.startPicking(id, activeCompany(session));
            ra.addFlashAttribute("success", "DO masuk proses In Picking. Stok belum dikurangi.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memulai picking: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do/" + id + "/goods-issue";
    }

    @PostMapping("/{id}/picking/ready")
    public String readyToShip(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            doService.readyToShip(id, activeCompany(session));
            ra.addFlashAttribute("success", "DO Ready to Ship. Stok belum dikurangi.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menandai Ready to Ship: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do/" + id + "/goods-issue";
    }

    @PostMapping("/{id}/goods-issue/{transactionId}/reverse")
    public String reverseGoodsIssue(@PathVariable Long id, @PathVariable Long transactionId,
                                    HttpSession session, RedirectAttributes ra) {
        try {
            String reversalDocNo = doService.reverseGoodsIssue(id, activeCompany(session), transactionId);
            ra.addFlashAttribute("success", "Reversal " + reversalDocNo + " berhasil dibuat sebagai transaksi IN. Transaksi OUT asal tetap tersimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal reversal Goods Issue: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do/" + id + "/goods-issue";
    }

    @PostMapping("/{id}/delivered")
    public String delivered(@PathVariable Long id,
                            @RequestParam(value = "podReference", required = false) String podReference,
                            @RequestParam(value = "podRemarks", required = false) String podRemarks,
                            HttpSession session, RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);

            if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
                throw new IllegalStateException("Sesi login atau username petugas tidak ditemukan.");
            }

            String deliveredBy = user.getUsername().trim();

            String docNo = doService.markDelivered(
                    id,
                    activeCompany(session),
                    podReference,
                    podRemarks,
                    deliveredBy
            );
            ra.addFlashAttribute("success", "DO " + docNo + " tercatat Delivered/POD. Tidak ada pengurangan stok tambahan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal mencatat POD: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do/" + id + "/goods-issue";
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            doService.closeDo(id, activeCompany(session));
            ra.addFlashAttribute("success", "DO berhasil ditutup.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menutup DO: " + rootMessage(e));
        }
        return "redirect:/ui/sales/do/" + id + "/goods-issue";
    }

    private String loadPage(Long id, Long compId, Model model, String idempotencyKey) {
        @SuppressWarnings("unchecked")
        List<Object[]> headers = em.createNativeQuery(
                "SELECT h.id, h.tenant_id, h.doc_no, h.doc_date, h.status, h.warehouse_id, " +
                "COALESCE(w.code || ' - ' || w.name, 'Warehouse belum dipilih'), " +
                "COALESCE(h.picking_status,'NOT_STARTED'), COALESCE(h.goods_issue_status,'NOT_POSTED'), " +
                "COALESCE(h.pod_status,'PENDING'), COALESCE(h.pod_reference,''), COALESCE(h.pod_remarks,'') " +
                "FROM sal.delivery_order_hdr h LEFT JOIN md.warehouse w ON w.id=h.warehouse_id " +
                "WHERE h.id=:id AND h.company_id=:compId")
                .setParameter("id", id).setParameter("compId", compId).getResultList();
        if (headers.isEmpty()) throw new IllegalArgumentException("Delivery Order tidak ditemukan untuk company aktif.");
        Object[] h = headers.get(0);
        Long tenantId = ((Number) h[1]).longValue();

        List<?> detailRows = em.createNativeQuery(
                "SELECT d.id, d.item_id, COALESCE(i.code,'-'), COALESCE(i.name,'-'), " +
                "COALESCE(u.code,'-'), d.qty, d.gi_posted_qty, (d.qty-d.gi_posted_qty) " +
                "FROM sal.delivery_order_dtl d LEFT JOIN md.item i ON i.id=d.item_id " +
                "LEFT JOIN md.uom u ON u.id=d.uom_id WHERE d.header_id=:id ORDER BY d.id")
                .setParameter("id", id).getResultList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object raw : detailRows) {
            Object[] row = (Object[]) raw;
            Map<String, Object> item = new HashMap<>();
            item.put("detailId", row[0]); item.put("itemId", row[1]);
            item.put("code", row[2]); item.put("name", row[3]); item.put("uom", row[4]);
            item.put("qty", row[5]); item.put("postedQty", row[6]); item.put("remainingQty", row[7]);
            items.add(item);
        }

        List<?> warehouseRows = em.createNativeQuery(
                "SELECT id, code, name FROM md.warehouse WHERE tenant_id=:tenantId AND company_id=:compId " +
                "AND status='ACTIVE' ORDER BY code")
                .setParameter("tenantId", tenantId).setParameter("compId", compId).getResultList();
        List<Map<String, Object>> warehouses = new ArrayList<>();
        for (Object raw : warehouseRows) {
            Object[] row = (Object[]) raw;
            Map<String, Object> warehouse = new HashMap<>();
            warehouse.put("id", row[0]); warehouse.put("code", row[1]); warehouse.put("name", row[2]);
            warehouses.add(warehouse);
        }

        List<?> giRows = em.createNativeQuery(
                "SELECT th.id, th.doc_no, th.doc_date, th.status, " +
                "EXISTS(SELECT 1 FROM inv.transaction_header rv WHERE rv.reversal_of_header_id=th.id) " +
                "FROM inv.transaction_header th WHERE th.source_do_id=:id AND th.doc_type='OUT' ORDER BY th.id DESC")
                .setParameter("id", id).getResultList();
        List<Map<String, Object>> goodsIssues = new ArrayList<>();
        for (Object raw : giRows) {
            Object[] row = (Object[]) raw;
            Map<String, Object> gi = new HashMap<>();
            gi.put("id", row[0]); gi.put("docNo", row[1]); gi.put("docDate", row[2]);
            gi.put("status", row[3]); gi.put("reversed", row[4]);
            goodsIssues.add(gi);
        }

        model.addAttribute("id", h[0]);
        model.addAttribute("docNo", h[2]);
        model.addAttribute("docDate", h[3]);
        model.addAttribute("status", h[4]);
        model.addAttribute("warehouseId", h[5]);
        model.addAttribute("warehouseName", h[6]);
        model.addAttribute("pickingStatus", h[7]);
        model.addAttribute("goodsIssueStatus", h[8]);
        model.addAttribute("podStatus", h[9]);
        model.addAttribute("podReference", h[10]);
        model.addAttribute("podRemarks", h[11]);
        model.addAttribute("items", items);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("goodsIssues", goodsIssues);
        model.addAttribute("idempotencyKey", idempotencyKey);
        return "ui/sales/do_goods_issue";
    }

    private Long activeCompany(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user == null || user.getActiveCompanyId() == null) throw new IllegalStateException("Sesi login atau company aktif tidak ditemukan.");
        return user.getActiveCompanyId();
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : e.toString();
    }
}
