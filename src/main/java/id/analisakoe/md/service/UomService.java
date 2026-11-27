package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.context.ContextService;
import id.analisakoe.md.dto.UomRequest;
import id.analisakoe.md.dto.UomResponse;
import id.analisakoe.md.entity.Uom;
import id.analisakoe.md.repository.UomRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UomService {

    private final UomRepository repo;
    private final ContextService context;

    public UomService(UomRepository repo, ContextService context) {
        this.repo = repo;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public PageResponse<UomResponse> list(String search, Pageable pageable) {
        Long tenantId = context.tenantId();
        Page<Uom> page = (search == null || search.isBlank())
            ? repo.findByTenantId(tenantId, pageable)
            : repo.findByTenantIdAndNameContainingIgnoreCase(tenantId, search, pageable);
        return PageResponse.of(page.map(UomResponse::from));
    }

    @Transactional(readOnly = true)
    public UomResponse get(Long id) {
        Uom e = repo.findByIdAndTenantId(id, context.tenantId())
            .orElseThrow(() -> new EntityNotFoundException("UOM tidak ditemukan: " + id));
        return UomResponse.from(e);
    }

    @Transactional
    public UomResponse create(UomRequest req) {
        Long tenantId = context.tenantId();
        if (repo.existsByTenantIdAndCode(tenantId, req.code())) {
            throw new BusinessException("Kode UOM sudah dipakai: " + req.code());
        }
        Uom e = new Uom();
        e.setTenantId(tenantId);
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        return UomResponse.from(repo.save(e));
    }

    @Transactional
    public UomResponse update(Long id, UomRequest req) {
        Uom e = repo.findByIdAndTenantId(id, context.tenantId())
            .orElseThrow(() -> new EntityNotFoundException("UOM tidak ditemukan: " + id));
        e.setName(req.name().trim());
        return UomResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        Uom e = repo.findByIdAndTenantId(id, context.tenantId())
            .orElseThrow(() -> new EntityNotFoundException("UOM tidak ditemukan: " + id));
        repo.delete(e);
    }
}
