package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.entity.AppUser;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.repository.AppUserRepository;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.UomRepository;
import id.analisakoe.pur.entity.PurchaseRequestHdr;
import id.analisakoe.pur.repository.PurchaseRequestRepository;
import id.analisakoe.pur.service.PurchasingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/ui/my-requests")
public class UiMyRequestController {

    private final PurchasingService service;
    private final PurchaseRequestRepository prRepo;
    private final AppUserRepository userRepo;
    private final MenuService menuService;
    private final ItemRepository itemRepo;
    private final UomRepository uomRepo;
    private final ContextService context;

    public UiMyRequestController(PurchasingService service, PurchaseRequestRepository prRepo, AppUserRepository userRepo,
                                 MenuService menuService, ItemRepository itemRepo, UomRepository uomRepo,
                                 ContextService context) {
        this.service = service; this.prRepo = prRepo; this.userRepo = userRepo;
        this.menuService = menuService; this.itemRepo = itemRepo; this.uomRepo = uomRepo;
        this.context = context;
    }

    /** PR harus milik tenant + company yang sedang aktif. */
    private PurchaseRequestHdr ownedPr(Long id) {
        return prRepo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new IllegalArgumentException("Request tidak ditemukan"));
    }

    private AppUser getLoggedInUser(HttpSession session) {
        SessionUser sessionUser = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        return userRepo.findById(sessionUser.getUserId()).orElseThrow();
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        AppUser appUser = getLoggedInUser(session);
        
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "My Requests");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        
        List<PurchaseRequestHdr> myRequests = new ArrayList<>();
        if (appUser.getEmployeeId() != null) {
            myRequests = prRepo.findByTenantIdAndCompanyIdAndRequisitionerIdOrderByIdDesc(
                context.tenantId(), context.companyId(), appUser.getEmployeeId(), PageRequest.of(0, 50)).getContent();
        }
        model.addAttribute("requests", myRequests);
        
        return "ui/my_requests/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Request");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("items", itemRepo.findByTenantIdAndCompanyIdAndDeletedAtIsNull(
            context.tenantId(), context.companyId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("uoms", uomRepo.findByTenantId(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("today", LocalDate.now());
        return "ui/my_requests/form";
    }

    @PostMapping
    public String save(@RequestParam LocalDate docDate, 
                       @RequestParam(required = false) String remarks,
                       @RequestParam(value = "itemId", required = false) ArrayList<Long> itemIds,
                       @RequestParam(value = "uomId", required = false) ArrayList<Long> uomIds,
                       @RequestParam(value = "qty", required = false) ArrayList<BigDecimal> qtys,
                       @RequestParam(value = "shortText", required = false) ArrayList<String> shortTexts,
                       @RequestParam(value = "deliveryDate", required = false) ArrayList<String> deliveryDates,
                       HttpSession session, RedirectAttributes ra) {
        try {
            AppUser appUser = getLoggedInUser(session);
            if (appUser.getEmployeeId() == null) {
                throw new Exception("Akun Anda belum terhubung dengan data Karyawan. Hubungi Admin.");
            }

            // PENGAMAN: Jika tidak ada barang yang dipilih
            if (itemIds == null || itemIds.isEmpty()) {
                throw new Exception("Minimal harus ada 1 barang yang diminta.");
            }

            var lines = new ArrayList<PurchasingService.LineReq>();
            for (int i = 0; i < itemIds.size(); i++) {
                String ddStr = (deliveryDates != null && i < deliveryDates.size() && deliveryDates.get(i) != null && !deliveryDates.get(i).isEmpty()) 
                               ? deliveryDates.get(i) : LocalDate.now().toString();
                String st = (shortTexts != null && i < shortTexts.size()) ? shortTexts.get(i) : "";
                
                lines.add(new PurchasingService.LineReq(
                    itemIds.get(i), uomIds.get(i), qtys.get(i), BigDecimal.ZERO,
                    st, LocalDate.parse(ddStr), "Internal Request"
                ));
            }
            service.createPr(docDate, remarks, appUser.getEmployeeId(), lines);
            ra.addFlashAttribute("success", "Permintaan berhasil dikirim ke Purchasing.");
        } catch (Exception e) { 
            ra.addFlashAttribute("error", e.getMessage()); 
        }
        return "redirect:/ui/my-requests";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        AppUser appUser = getLoggedInUser(session);
        try {
            var pr = ownedPr(id);
            if (appUser.getEmployeeId() == null || !pr.getRequisitionerId().equals(appUser.getEmployeeId()))
                throw new IllegalArgumentException("Request ini bukan milik Anda.");
            if (!"DRAFT".equals(pr.getStatus()))
                throw new IllegalArgumentException("Hanya Request berstatus DRAFT yang dapat diedit.");

            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Edit Request");
            model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
            model.addAttribute("items", itemRepo.findByTenantIdAndCompanyIdAndDeletedAtIsNull(
                context.tenantId(), context.companyId(), PageRequest.of(0, 100)).getContent());
            model.addAttribute("uoms", uomRepo.findByTenantId(context.tenantId(), PageRequest.of(0, 100)).getContent());
            model.addAttribute("pr", pr);

            StringBuilder json = new StringBuilder("[");
            for (var d : pr.getDetails()) {
                if (json.length() > 1) json.append(',');
                json.append("{")
                    .append("\"itemId\":").append(d.getItemId()).append(',')
                    .append("\"uomId\":").append(d.getUomId()).append(',')
                    .append("\"qty\":").append(d.getQty()).append(',')
                    .append("\"shortText\":\"").append(d.getShortText() == null ? "" : d.getShortText().replace("\\", "\\\\").replace("\"", "\\\"")).append("\",")
                    .append("\"deliveryDate\":\"").append(d.getDeliveryDate()).append('"')
                    .append('}');
            }
            json.append(']');
            model.addAttribute("prefillLines", json.toString());
            return "ui/my_requests/form";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/ui/my-requests";
        }
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @RequestParam LocalDate docDate,
                         @RequestParam(required = false) String remarks,
                         @RequestParam(value = "itemId", required = false) ArrayList<Long> itemIds,
                         @RequestParam(value = "uomId", required = false) ArrayList<Long> uomIds,
                         @RequestParam(value = "qty", required = false) ArrayList<BigDecimal> qtys,
                         @RequestParam(value = "shortText", required = false) ArrayList<String> shortTexts,
                         @RequestParam(value = "deliveryDate", required = false) ArrayList<String> deliveryDates,
                         HttpSession session, RedirectAttributes ra) {
        try {
            AppUser appUser = getLoggedInUser(session);
            if (appUser.getEmployeeId() == null)
                throw new Exception("Akun Anda belum terhubung dengan data Karyawan. Hubungi Admin.");
            if (itemIds == null || itemIds.isEmpty())
                throw new Exception("Minimal harus ada 1 barang yang diminta.");

            var pr = ownedPr(id);
            if (!pr.getRequisitionerId().equals(appUser.getEmployeeId()))
                throw new Exception("Request ini bukan milik Anda.");

            var lines = new ArrayList<PurchasingService.LineReq>();
            for (int i = 0; i < itemIds.size(); i++) {
                String ddStr = (deliveryDates != null && i < deliveryDates.size() && deliveryDates.get(i) != null && !deliveryDates.get(i).isEmpty()) 
                               ? deliveryDates.get(i) : LocalDate.now().toString();
                String st = (shortTexts != null && i < shortTexts.size()) ? shortTexts.get(i) : "";
                lines.add(new PurchasingService.LineReq(
                    itemIds.get(i), uomIds.get(i), qtys.get(i), BigDecimal.ZERO,
                    st, LocalDate.parse(ddStr), "Internal Request"
                ));
            }
            service.updatePr(id, docDate, remarks, appUser.getEmployeeId(), lines);
            ra.addFlashAttribute("success", "Permintaan berhasil diperbarui.");
        } catch (Exception e) { 
            ra.addFlashAttribute("error", e.getMessage()); 
        }
        return "redirect:/ui/my-requests";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            AppUser appUser = getLoggedInUser(session);
            var pr = ownedPr(id);
            if (appUser.getEmployeeId() == null || !pr.getRequisitionerId().equals(appUser.getEmployeeId()))
                throw new Exception("Request ini bukan milik Anda.");
            service.cancelPr(id);
            ra.addFlashAttribute("success", "Permintaan berhasil dibatalkan (CANCELLED).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/my-requests";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            AppUser appUser = getLoggedInUser(session);
            var pr = ownedPr(id);
            if (appUser.getEmployeeId() == null || !pr.getRequisitionerId().equals(appUser.getEmployeeId()))
                throw new Exception("Request ini bukan milik Anda.");
            service.deletePr(id);
            ra.addFlashAttribute("success", "Permintaan berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/my-requests";
    }
}