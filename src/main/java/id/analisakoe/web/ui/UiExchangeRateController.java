package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.md.entity.ExchangeRate;
import id.analisakoe.md.repository.CurrencyRepository;
import id.analisakoe.md.repository.ExchangeRateRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/ui/exchange-rates")
public class UiExchangeRateController {

    @Autowired
    private ContextService context;

    private final ExchangeRateRepository repo;
    private final CurrencyRepository currencyRepo;
    private final MenuService menuService;

    public UiExchangeRateController(ExchangeRateRepository repo, CurrencyRepository currencyRepo, MenuService menuService) {
        this.repo = repo;
        this.currencyRepo = currencyRepo;
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Exchange Rates");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("rates", repo.findByTenantIdOrderByEffectiveDateDesc(context.tenantId(), PageRequest.of(0, 50)).getContent());
        return "ui/exchange_rates/list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Exchange Rate");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", true);
        model.addAttribute("xrate", new ExchangeRate());
        return "ui/exchange_rates/form";
    }

    @PostMapping
    public String save(@RequestParam String fromCurrency,
                       @RequestParam String toCurrency,
                       @RequestParam BigDecimal rate,
                       @RequestParam LocalDate effectiveDate,
                       RedirectAttributes ra) {
        try {
            ExchangeRate x = new ExchangeRate();
            x.setTenantId(context.tenantId());
            x.setFromCurrency(fromCurrency);
            x.setToCurrency(toCurrency);
            x.setRate(rate);
            x.setEffectiveDate(effectiveDate);
            repo.save(x);
            ra.addFlashAttribute("success", "Kurs berhasil ditambahkan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/exchange-rates";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        ExchangeRate xrate = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate tidak ditemukan: " + id));

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Exchange Rate");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("currencies", currencyRepo.findAllByOrderByCode());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isNew", false);
        model.addAttribute("xrate", xrate);
        return "ui/exchange_rates/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String fromCurrency,
                         @RequestParam String toCurrency,
                         @RequestParam BigDecimal rate,
                         @RequestParam LocalDate effectiveDate,
                         RedirectAttributes ra) {
        try {
            ExchangeRate x = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Exchange rate tidak ditemukan: " + id));
            x.setFromCurrency(fromCurrency);
            x.setToCurrency(toCurrency);
            x.setRate(rate);
            x.setEffectiveDate(effectiveDate);
            repo.save(x);
            ra.addFlashAttribute("success", "Kurs berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/exchange-rates";
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public String delete(@PathVariable Long id) {
        repo.deleteById(id);
        return "";
    }
}
