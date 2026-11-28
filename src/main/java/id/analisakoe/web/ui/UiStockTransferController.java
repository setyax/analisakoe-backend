package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import java.util.List;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.inv.entity.StockTransferHdr;
import id.analisakoe.inv.entity.StockTransferDtl;
import id.analisakoe.inv.repository.StockTransferRepository;
import id.analisakoe.inv.service.StockTransferService;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.md.repository.WarehouseRepository;
import id.analisakoe.md.entity.Item;
import id.analisakoe.md.entity.Warehouse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui/inventory/transfers")
public class UiStockTransferController {

    @Autowired
    private ContextService context;

    private final StockTransferService service;
    private final StockTransferRepository repo;
    private final WarehouseRepository whRepo;
    private final ItemRepository itemRepo;
    private final UomRepository uomRepo;
    private final MenuService menuService;

    public UiStockTransferController(StockTransferService service, StockTransferRepository repo,
                                     WarehouseRepository whRepo, ItemRepository itemRepo,
                                     UomRepository uomRepo, MenuService menuService) {
        this.service = service; this.repo = repo; this.whRepo = whRepo;
        this.itemRepo = itemRepo; this.uomRepo = uomRepo; this.menuService = menuService;
    }

    private void prepareModel(Model model, SessionUser user) {
        model.addAttribute("user", user);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("warehouses", whRepo.findAll());
        model.addAttribute("items", itemRepo.findAll());
        model.addAttribute("uoms", uomRepo.findAll());
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        prepareModel(model, user);
        model.addAttribute("pageTitle", "Stock Transfers");
        
        var list = repo.findByTenantIdOrderByIdDesc(context.tenantId());
        model.addAttribute("transfers", list);
        
        // Map untuk nama gudang dan barang
        Map<Long, String> whMap = whRepo.findAll().stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<Long, String> itemMap = itemRepo.findAll().stream().collect(Collectors.toMap(Item::getId, Item::getName));
        model.addAttribute("whMap", whMap);
        model.addAttribute("itemMap", itemMap);
        
        return "ui/inventory/transfer_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        prepareModel(model, user);
        model.addAttribute("pageTitle", "New Stock Transfer");
        model.addAttribute("today", LocalDate.now());
        return "ui/inventory/transfer_form";
    }

    @PostMapping
    public String save(@RequestParam LocalDate transferDate,
                       @RequestParam Long fromWarehouseId,
                       @RequestParam Long toWarehouseId,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemId") ArrayList<Long> itemIds,
                       @RequestParam("uomId") ArrayList<Long> uomIds,
                       @RequestParam("qty") ArrayList<BigDecimal> qtys,
                       RedirectAttributes ra) {
        try {
            StockTransferHdr hdr = new StockTransferHdr();
            hdr.setDocNo("TRF-" + System.currentTimeMillis());
            hdr.setTransferDate(transferDate);
            hdr.setFromWarehouseId(fromWarehouseId);
            hdr.setToWarehouseId(toWarehouseId);
            hdr.setRemarks(remarks);

            for (int i = 0; i < itemIds.size(); i++) {
                if (qtys.get(i).compareTo(BigDecimal.ZERO) > 0) {
                    StockTransferDtl d = new StockTransferDtl();
                    d.setItemId(itemIds.get(i));
                    d.setUomId(uomIds.get(i));
                    d.setQty(qtys.get(i));
                    hdr.addDetail(d);
                }
            }
            service.create(hdr);
            ra.addFlashAttribute("success", "Stock Transfer berhasil dibuat.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/inventory/transfers";
    }

    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        prepareModel(model, user);
        model.addAttribute("pageTitle", "View Stock Transfer");
        
        var trf = repo.findById(id).orElseThrow();
        model.addAttribute("trf", trf);
        
        Map<Long, String> whMap = whRepo.findAll().stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<Long, String> itemMap = itemRepo.findAll().stream().collect(Collectors.toMap(Item::getId, Item::getName));
        Map<Long, String> uomMap = uomRepo.findAll().stream().collect(Collectors.toMap(u -> u.getId(), u -> u.getCode()));
        model.addAttribute("whMap", whMap);
        model.addAttribute("itemMap", itemMap);
        model.addAttribute("uomMap", uomMap);
        
        return "ui/inventory/transfer_view";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.confirm(id);
            ra.addFlashAttribute("success", "Transfer berhasil di-confirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/inventory/transfers/" + id + "/view";
    }

    @PostMapping("/{id}/unconfirm")
    public String unconfirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.unconfirm(id);
            ra.addFlashAttribute("success", "Transfer berhasil di-unconfirm. Stok dikembalikan ke gudang asal.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/inventory/transfers/" + id + "/view";
    }

    @PostMapping("/{id}/void")
    public String voidTransfer(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.voidTransfer(id);
            ra.addFlashAttribute("success", "Transfer berhasil di-VOID.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/inventory/transfers";
    }

    @PostMapping("/{id}/delete")
    public String deleteTransfer(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.deleteTransfer(id);
            ra.addFlashAttribute("success", "Transfer berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/inventory/transfers";
    }
}