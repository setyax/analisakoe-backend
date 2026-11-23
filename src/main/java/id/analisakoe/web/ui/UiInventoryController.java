package id.analisakoe.web.ui;
import java.util.List;
import java.math.BigDecimal;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.inv.dto.InventoryDtos.*;
import id.analisakoe.inv.service.InventoryService;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.md.repository.WarehouseRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;

@Controller
@RequestMapping("/ui/inventory")
public class UiInventoryController {

    private final InventoryService inventoryService;
    private final MenuService menuService;
    private final ItemRepository itemRepo;
    private final WarehouseRepository whRepo;
    private final UomRepository uomRepo;

    public UiInventoryController(InventoryService inventoryService, MenuService menuService,
                                 ItemRepository itemRepo, WarehouseRepository whRepo, UomRepository uomRepo) {
        this.inventoryService = inventoryService;
        this.menuService = menuService;
        this.itemRepo = itemRepo;
        this.whRepo = whRepo;
        this.uomRepo = uomRepo;
    }

    @GetMapping("/balance")
    public String balance(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Stock Balance");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("balances", inventoryService.getBalances());
        return "ui/inventory/balance";
    }

    @GetMapping("/transactions")
    public String transactions(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Transactions");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("transactions", inventoryService.getTransactions());
        return "ui/inventory/transactions";
    }

    @GetMapping("/transactions/new")
    public String newTransaction(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Transaction");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        
        model.addAttribute("items", itemRepo.findByTenantIdAndDeletedAtIsNull(1L, org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        model.addAttribute("warehouses", whRepo.findByTenantId(1L, org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(1L, org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        model.addAttribute("today", LocalDate.now());
        
        return "ui/inventory/form";
    }

    @PostMapping("/transactions")
    public String saveTransaction(@RequestParam String docType,
                                  @RequestParam String docDate,
                                  @RequestParam Long warehouseId,
                                  @RequestParam(required = false) String remarks,
                                  @RequestParam("itemId") ArrayList<Long> itemIds,
                                  @RequestParam("uomId") ArrayList<Long> uomIds,
                                  @RequestParam("qty") ArrayList<java.math.BigDecimal> qtys,
                                  RedirectAttributes ra) {
        try {
            var lines = new ArrayList<TransactionLine>();
            for (int i = 0; i < itemIds.size(); i++) {
                lines.add(new TransactionLine(itemIds.get(i), uomIds.get(i), qtys.get(i)));
            }
            var req = new TransactionRequest(docType, LocalDate.parse(docDate), warehouseId, remarks, lines);
            inventoryService.createTransaction(req);
            ra.addFlashAttribute("success", "Transaksi berhasil disimpan dan stok telah diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/inventory/transactions";
    }
}