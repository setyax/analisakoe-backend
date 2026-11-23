package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.md.dto.CustomerRequest;
import id.analisakoe.md.dto.CustomerResponse;
import id.analisakoe.md.entity.Customer;
import id.analisakoe.md.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class CustomerService {

    private static final Long TENANT = 1L;
    private static final Long COMPANY = 1L;
    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, Pageable pageable) {
        Page<Customer> page = (search == null || search.isBlank())
            ? repo.findByTenantIdAndDeletedAtIsNull(TENANT, pageable)
            : repo.findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(TENANT, search, pageable);
        return PageResponse.of(page.map(CustomerResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        Customer e = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Customer tidak ditemukan: " + id));
        return CustomerResponse.from(e);
    }

    @Transactional
    public CustomerResponse create(CustomerRequest req) {
        if (repo.existsByTenantIdAndCompanyIdAndCode(TENANT, COMPANY, req.code())) {
            throw new BusinessException("Kode customer sudah dipakai: " + req.code());
        }
        Customer e = new Customer();
        e.setTenantId(TENANT);
        e.setCompanyId(COMPANY);
        apply(e, req);
        return CustomerResponse.from(repo.save(e));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest req) {
        Customer e = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Customer tidak ditemukan: " + id));
        apply(e, req);
        return CustomerResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        Customer e = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Customer tidak ditemukan: " + id));
        e.setDeletedAt(OffsetDateTime.now());
        repo.save(e);
    }

    private void apply(Customer e, CustomerRequest req) {
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        e.setTaxId(req.taxId());
        e.setCurrency(req.currency());
        e.setCreditLimit(req.creditLimit() == null ? BigDecimal.ZERO : req.creditLimit());
        e.setPaymentTermDays(req.paymentTermDays() == null ? (short)0 : req.paymentTermDays());
        e.setStatus(req.status() == null ? "ACTIVE" : req.status());
    }
}