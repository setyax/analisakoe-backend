package id.analisakoe.pur.service;

import id.analisakoe.pur.entity.PurchasePolicy;
import id.analisakoe.pur.repository.PurchasePolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PurchasePolicyService {

    private final PurchasePolicyRepository repo;

    public PurchasePolicyService(PurchasePolicyRepository repo) {
        this.repo = repo;
    }

    public boolean requiresPr(Long companyId, PurchaseContext ctx) {
        for (PurchasePolicy p : repo.findActiveByCompanyOrderByPriority(companyId)) {
            if (matches(p, ctx)) return p.isRequiresPr();
        }
        return false;
    }

    private boolean matches(PurchasePolicy p, PurchaseContext c) {
        return switch (p.getRuleType()) {
            case "AMOUNT_THRESHOLD" ->
                c.amount() != null && p.getThresholdValue() != null
                    && c.amount().compareTo(p.getThresholdValue()) >= 0;
            case "SUPPLIER_NEW" -> c.isNewSupplier();
            case "USER_ROLE" ->
                c.userRoleIds() != null && p.getReferenceId() != null
                    && c.userRoleIds().contains(p.getReferenceId());
            default -> false;
        };
    }

    public record PurchaseContext(
        BigDecimal amount,
        boolean isNewSupplier,
        List<Long> userRoleIds
    ) {}
}
