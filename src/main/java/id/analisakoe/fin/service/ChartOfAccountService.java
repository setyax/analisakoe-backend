package id.analisakoe.fin.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.fin.dto.ChartOfAccountRequest;
import id.analisakoe.fin.dto.ChartOfAccountResponse;
import id.analisakoe.fin.entity.ChartOfAccount;
import id.analisakoe.fin.repository.ChartOfAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChartOfAccountService {
    private static final Long TENANT = 1L;
    private static final Long COMPANY = 11L;

    private final ChartOfAccountRepository repo;

    public ChartOfAccountService(ChartOfAccountRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public PageResponse<ChartOfAccountResponse> list(String search, Pageable pageable) {
        Page<ChartOfAccount> page = (search == null || search.isBlank())
            ? repo.findByTenantId(TENANT, pageable)
            : repo.findByTenantIdAndNameContainingIgnoreCase(TENANT, search, pageable);
        return PageResponse.of(page.map(ChartOfAccountResponse::from));
    }

    @Transactional(readOnly = true)
    public ChartOfAccountResponse get(Long id) {
        return ChartOfAccountResponse.from(repo.findByIdAndTenantId(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("COA tidak ditemukan: " + id)));
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccountResponse> listHeaders() {
        return repo.findByTenantIdAndIsHeaderTrue(TENANT).stream()
            .map(ChartOfAccountResponse::from).toList();
    }

    @Transactional
    public ChartOfAccountResponse create(ChartOfAccountRequest req) {
        if (repo.existsByTenantIdAndCompanyIdAndCode(TENANT, COMPANY, req.code())) {
            throw new BusinessException("Kode Akun sudah dipakai: " + req.code());
        }
        ChartOfAccount e = new ChartOfAccount();
        e.setTenantId(TENANT);
        e.setCompanyId(COMPANY);
        apply(e, req);
        return ChartOfAccountResponse.from(repo.save(e));
    }

    @Transactional
    public ChartOfAccountResponse update(Long id, ChartOfAccountRequest req) {
        ChartOfAccount e = repo.findByIdAndTenantId(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("COA tidak ditemukan: " + id));
        apply(e, req);
        return ChartOfAccountResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("COA tidak ditemukan");
        repo.deleteById(id);
    }

    private void apply(ChartOfAccount e, ChartOfAccountRequest req) {
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        e.setAccountType(req.accountType());
        e.setParentId(req.parentId());
        e.setIsHeader(req.isHeader() == null ? Boolean.FALSE : req.isHeader());
        e.setCurrency(req.currency());
        e.setStatus(req.status() == null ? "ACTIVE" : req.status());
    }
}