package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.md.dto.SupplierRequest;
import id.analisakoe.md.dto.SupplierResponse;
import id.analisakoe.md.entity.Supplier;
import id.analisakoe.md.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class SupplierService {

    private static final Long TENANT = 1L;
    private static final Long COMPANY = 1L;
    private final SupplierRepository repo;

    public SupplierService(SupplierRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String search, Pageable pageable) {
        Page<Supplier> page = (search == null || search.isBlank())
            ? repo.findByTenantIdAndDeletedAtIsNull(TENANT, pageable)
            : repo.findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(TENANT, search, pageable);
        return PageResponse.of(page.map(SupplierResponse::from));
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        Supplier e = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Supplier tidak ditemukan: " + id));
        return SupplierResponse.from(e);
    }

    @Transactional
    public SupplierResponse create(SupplierRequest req) {
        if (repo.existsByTenantIdAndCompanyIdAndCode(TENANT, COMPANY, req.code())) {
            throw new BusinessException("Kode supplier sudah dipakai: " + req.code());
        }
        Supplier e = new Supplier();
        e.setTenantId(TENANT);
        e.setCompanyId(COMPANY);
        apply(e, req);
        return SupplierResponse.from(repo.save(e));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest req) {
        Supplier e = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Supplier tidak ditemukan: " + id));
        apply(e, req);
        return SupplierResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        Supplier e = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Supplier tidak ditemukan: " + id));
        e.setDeletedAt(OffsetDateTime.now());
        repo.save(e);
    }

    private void apply(Supplier e, SupplierRequest req) {
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        e.setTaxId(req.taxId());
        e.setCurrency(req.currency());
        e.setPaymentTermDays(req.paymentTermDays() == null ? (short)0 : req.paymentTermDays());
        e.setStatus(req.status() == null ? "ACTIVE" : req.status());
    }
}