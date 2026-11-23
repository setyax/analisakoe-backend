package id.analisakoe.web.ui;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.dto.ItemRequest;
import id.analisakoe.md.dto.ItemResponse;
import id.analisakoe.md.dto.UomResponse;
import id.analisakoe.md.service.ItemService;
import id.analisakoe.md.service.UomService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/items")
public class UiItemController {

    private final ItemService itemService;
    private final UomService uomService;
    private final MenuService menuService;
    private final TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    public UiItemController(ItemService itemService, UomService uomService,
                            MenuService menuService, PlatformTransactionManager txManager) {
        this.itemService = itemService;
        this.uomService = uomService;
        this.menuService = menuService;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Input form yang dikosongkan dikirim browser sebagai "", ubah menjadi null (juga trim spasi).
     *  Tanpa ini, kolom seperti price_control tersimpan sebagai '' dan melanggar check constraint. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        PageResponse<ItemResponse> items = itemService.list(q, PageRequest.of(page, size, Sort.by("id").ascending()));

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Items");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("items", items);
        model.addAttribute("q", q);
        return "ui/items/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        fillFormModel(session, model, "New Item", new ItemFormData(), true);
        return "ui/items/form";
    }

    @PostMapping
    public String create(@ModelAttribute("item") ItemFormData form,
                         HttpSession session, Model model, RedirectAttributes ra) {
        try {
            validateUomSet(form);
            validateDjpCode(form);
            itemService.create(form.toRequest());
            saveItemUomSet(findItemIdByCode(form.getCode()), form.getUomSetId());
            ra.addFlashAttribute("success", "Item berhasil dibuat");
            return "redirect:/ui/items";
        } catch (Exception e) {
            fillFormModel(session, model, "New Item", form, true);
            model.addAttribute("error", e.getMessage());
            return "ui/items/form";
        }
    }

    @GetMapping("/{id}/edit")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        ItemResponse item = itemService.get(id);
        ItemFormData form = new ItemFormData();
        form.setId(item.id());
        form.setCode(item.code());
        form.setName(item.name());
        form.setDescription(item.description());
        form.setBaseUomId(item.baseUomId());
        form.setItemType(item.itemType());
        form.setTrackLot(item.trackLot());
        form.setTrackSerial(item.trackSerial());
        form.setBarcode(item.barcode());
        form.setStatus(item.status());

        // Field SAP
        form.setPurchasingGroup(item.purchasingGroup());
        form.setLeadTimeDays(item.leadTimeDays());
        form.setPriceControl(item.priceControl());
        form.setValuationClass(item.valuationClass());
        form.setStandardPrice(item.standardPrice());
        form.setDjpGoodsCode(item.djpGoodsCode());

        // UoM Set (kolom md.item.uom_set_id dibaca langsung)
        form.setUomSetId(loadItemUomSetId(id));

        fillFormModel(session, model, "Edit Item", form, false);
        return "ui/items/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("item") ItemFormData form,
                         HttpSession session, Model model, RedirectAttributes ra) {
        try {
            validateUomSet(form);
            validateDjpCode(form);
            itemService.update(id, form.toRequest());
            saveItemUomSet(id, form.getUomSetId());
            ra.addFlashAttribute("success", "Item berhasil diubah");
            return "redirect:/ui/items";
        } catch (Exception e) {
            form.setId(id);
            fillFormModel(session, model, "Edit Item", form, false);
            model.addAttribute("error", e.getMessage());
            return "ui/items/form";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public String delete(@PathVariable Long id) {
        itemService.delete(id);
        return "";
    }

    // ============================================================
    // HELPER
    // ============================================================

    /** Isi model yang dibutuhkan template form (dipakai new, edit, dan saat validasi gagal). */
    private void fillFormModel(HttpSession session, Model model, String title, ItemFormData form, boolean isNew) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("uoms", loadUoms());
        model.addAttribute("uomSets", loadUomSets());
        model.addAttribute("djpCodes", loadDjpCodes());
        model.addAttribute("item", form);
        model.addAttribute("isNew", isNew);
    }

    private List<UomResponse> loadUoms() {
        return uomService.list(null, PageRequest.of(0, 100, Sort.by("code"))).content();
    }

    /** Daftar UoM Set untuk dropdown. Sesuaikan nama tabel header jika berbeda. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadUomSets() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id, code, name FROM md.uom_set ORDER BY code").getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("code", r[1]);
            m.put("name", r[2]);
            list.add(m);
        }
        return list;
    }

    private Long loadItemUomSetId(Long itemId) {
        List<?> r = em.createNativeQuery("SELECT uom_set_id FROM md.item WHERE id = :id")
                .setParameter("id", itemId).getResultList();
        return (r.isEmpty() || r.get(0) == null) ? null : ((Number) r.get(0)).longValue();
    }

    /** Item baru: cari id dari kode (baris terbaru dengan kode tersebut). */
    private Long findItemIdByCode(String code) {
        List<?> r = em.createNativeQuery(
                "SELECT id FROM md.item WHERE code = :code ORDER BY id DESC LIMIT 1")
                .setParameter("code", code).getResultList();
        return r.isEmpty() ? null : ((Number) r.get(0)).longValue();
    }

    private void saveItemUomSet(Long itemId, Long uomSetId) {
        if (itemId == null) return;
        tx.executeWithoutResult(status -> {
            if (uomSetId == null) {
                em.createNativeQuery("UPDATE md.item SET uom_set_id = NULL WHERE id = :id")
                        .setParameter("id", itemId).executeUpdate();
            } else {
                em.createNativeQuery("UPDATE md.item SET uom_set_id = :setId WHERE id = :id")
                        .setParameter("setId", uomSetId)
                        .setParameter("id", itemId).executeUpdate();
            }
        });
    }

    /** Base UOM item harus sama dengan base UoM di UoM Set (baris tanpa parent). */
    private void validateUomSet(ItemFormData form) {
        if (form.getUomSetId() == null) return;

        List<?> roots = em.createNativeQuery(
                "SELECT uom_id FROM md.uom_set_dtl WHERE uom_set_id = :setId AND parent_uom_id IS NULL")
                .setParameter("setId", form.getUomSetId()).getResultList();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("UoM Set yang dipilih belum punya base UoM.");
        }

        boolean match = false;
        for (Object o : roots) {
            if (form.getBaseUomId() != null && ((Number) o).longValue() == form.getBaseUomId()) {
                match = true;
                break;
            }
        }
        if (!match) {
            throw new IllegalArgumentException(
                    "Base UOM item harus sama dengan base UoM pada UoM Set yang dipilih.");
        }
    }

    /** Daftar kode DJP untuk saran pengetikan (datalist). */
    @SuppressWarnings("unchecked")
    private List<String> loadDjpCodes() {
        List<Object> rows = em.createNativeQuery(
                "SELECT code FROM md.djp_goods_code ORDER BY code").getResultList();
        List<String> codes = new ArrayList<>();
        for (Object o : rows) codes.add(o.toString());
        return codes;
    }

    /** Kode DJP harus ada di master (foreign key item_djp_goods_code_fkey). */
    private void validateDjpCode(ItemFormData form) {
        String code = form.getDjpGoodsCode();
        if (code == null) return;
        Number n = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM md.djp_goods_code WHERE code = :code")
                .setParameter("code", code).getSingleResult();
        if (n.longValue() == 0) {
            throw new IllegalArgumentException(
                    "Kode DJP '" + code + "' tidak terdaftar di master DJP Goods Code.");
        }
    }

    public static class ItemFormData {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Long baseUomId;
        private Long uomSetId;
        private String itemType = "STOCK";
        private Boolean trackLot = false;
        private Boolean trackSerial = false;
        private String barcode;
        private String status = "ACTIVE";

        // Field SAP
        private String purchasingGroup = "001";
        private Integer leadTimeDays = 0;
        private String priceControl = "V";
        private String valuationClass = "3000";
        private BigDecimal standardPrice = BigDecimal.ZERO;
        private String djpGoodsCode;

        public ItemRequest toRequest() {
            return new ItemRequest(
                code, name, description, null, baseUomId,
                itemType, trackLot, trackSerial, barcode, status,
                purchasingGroup, leadTimeDays, priceControl, valuationClass, standardPrice,
                djpGoodsCode
            );
        }

        // Getters and Setters
        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public String getCode() { return code; } public void setCode(String v) { this.code = v; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
        public String getDescription() { return description; } public void setDescription(String v) { this.description = v; }
        public Long getBaseUomId() { return baseUomId; } public void setBaseUomId(Long v) { this.baseUomId = v; }
        public Long getUomSetId() { return uomSetId; } public void setUomSetId(Long v) { this.uomSetId = v; }
        public String getItemType() { return itemType; } public void setItemType(String v) { this.itemType = v; }
        public Boolean getTrackLot() { return trackLot; } public void setTrackLot(Boolean v) { this.trackLot = v; }
        public Boolean getTrackSerial() { return trackSerial; } public void setTrackSerial(Boolean v) { this.trackSerial = v; }
        public String getBarcode() { return barcode; } public void setBarcode(String v) { this.barcode = v; }
        public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }

        public String getPurchasingGroup() { return purchasingGroup; } public void setPurchasingGroup(String v) { this.purchasingGroup = v; }
        public Integer getLeadTimeDays() { return leadTimeDays; } public void setLeadTimeDays(Integer v) { this.leadTimeDays = v; }
        public String getPriceControl() { return priceControl; } public void setPriceControl(String v) { this.priceControl = v; }
        public String getValuationClass() { return valuationClass; } public void setValuationClass(String v) { this.valuationClass = v; }
        public BigDecimal getStandardPrice() { return standardPrice; } public void setStandardPrice(BigDecimal v) { this.standardPrice = v; }
        public String getDjpGoodsCode() { return djpGoodsCode; } public void setDjpGoodsCode(String v) { this.djpGoodsCode = v; }
    }
}