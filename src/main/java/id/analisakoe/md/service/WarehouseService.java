package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
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

    private static final Long TENANT = 1L;
    private static final Long COMPANY = 1L;
    private final WarehouseRepository repo;

    public WarehouseService(WarehouseRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(String search, Pageable pageable) {
        Page<Warehouse> page = (search == null || search.isBlank())
            ? repo.findByTenantId(TENANT, pageable)
            : repo.findByTenantIdAndNameContainingIgnoreCase(TENANT, search, pageable);
        return PageResponse.of(page.map(WarehouseResponse::from));
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(Long id) {
        Warehouse e = repo.findByIdAndTenantId(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Warehouse tidak ditemukan: " + id));
        return WarehouseResponse.from(e);
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest req) {
        if (repo.existsByTenantIdAndCode(TENANT, req.code())) {
            throw new BusinessException("Kode warehouse sudah dipakai: " + req.code());
        }
        Warehouse e = new Warehouse();
        e.setTenantId(TENANT);
        e.setCompanyId(COMPANY);
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        e.setAddress(req.address());
        e.setStatus(req.status() == null ? "ACTIVE" : req.status());
        return WarehouseResponse.from(repo.save(e));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest req) {
        Warehouse e = repo.findByIdAndTenantId(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("Warehouse tidak ditemukan: " + id));
        e.setName(req.name().trim());
        e.setAddress(req.address());
        if (req.status() != null) e.setStatus(req.status());
        return WarehouseResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Warehouse tidak ditemukan: " + id);
        repo.deleteById(id);
    }
}