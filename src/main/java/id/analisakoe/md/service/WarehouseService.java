package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.context.ContextService;
import id.analisakoe.md.dto.WarehouseRequest;
import id.analisakoe.md.dto.WarehouseResponse;
import id.analisakoe.md.entity.Warehouse;
import id.analisakoe.md.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {

    private final WarehouseRepository repo;
    private final ContextService context;

    public WarehouseService(WarehouseRepository repo, ContextService context) {
        this.repo = repo;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(String search, Pageable pageable) {
        Long tenantId = context.tenantId();
        Long companyId = context.companyId();
        Page<Warehouse> page = (search == null || search.isBlank())
            ? repo.findByTenantIdAndCompanyId(tenantId, companyId, pageable)
            : repo.findByTenantIdAndCompanyIdAndNameContainingIgnoreCase(
                tenantId, companyId, search, pageable);
        return PageResponse.of(page.map(WarehouseResponse::from));
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(Long id) {
        Warehouse e = repo.findByIdAndTenantIdAndCompanyId(
                id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new EntityNotFoundException("Warehouse tidak ditemukan: " + id));
        return WarehouseResponse.from(e);
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest req) {
        Long tenantId = context.tenantId();
        Long companyId = context.companyId();
        if (repo.existsByTenantIdAndCompanyIdAndCode(tenantId, companyId, req.code())) {
            throw new BusinessException("Kode warehouse sudah dipakai: " + req.code());
        }
        Warehouse e = new Warehouse();
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        e.setAddress(req.address());
        e.setStatus(req.status() == null ? "ACTIVE" : req.status());
        return WarehouseResponse.from(repo.save(e));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest req) {
        Warehouse e = repo.findByIdAndTenantIdAndCompanyId(
                id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new EntityNotFoundException("Warehouse tidak ditemukan: " + id));
        e.setName(req.name().trim());
        e.setAddress(req.address());
        if (req.status() != null) e.setStatus(req.status());
        return WarehouseResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        Warehouse e = repo.findByIdAndTenantIdAndCompanyId(
                id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new EntityNotFoundException("Warehouse tidak ditemukan: " + id));
        repo.delete(e);
    }
}
