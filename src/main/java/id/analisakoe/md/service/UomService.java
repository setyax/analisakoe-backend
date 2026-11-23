package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
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

    private static final Long TENANT = 1L;
    private final UomRepository repo;

    public UomService(UomRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public PageResponse<UomResponse> list(String search, Pageable pageable) {
        Page<Uom> page = (search == null || search.isBlank())
            ? repo.findByTenantId(TENANT, pageable)
            : repo.findByTenantIdAndNameContainingIgnoreCase(TENANT, search, pageable);
        return PageResponse.of(page.map(UomResponse::from));
    }

    @Transactional(readOnly = true)
    public UomResponse get(Long id) {
        Uom e = repo.findByIdAndTenantId(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("UOM tidak ditemukan: " + id));
        return UomResponse.from(e);
    }

    @Transactional
    public UomResponse create(UomRequest req) {
        if (repo.existsByTenantIdAndCode(TENANT, req.code())) {
            throw new BusinessException("Kode UOM sudah dipakai: " + req.code());
        }
        Uom e = new Uom();
        e.setTenantId(TENANT);
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        return UomResponse.from(repo.save(e));
    }

    @Transactional
    public UomResponse update(Long id, UomRequest req) {
        Uom e = repo.findByIdAndTenantId(id, TENANT)
            .orElseThrow(() -> new EntityNotFoundException("UOM tidak ditemukan: " + id));
        e.setName(req.name().trim());
        return UomResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("UOM tidak ditemukan: " + id);
        repo.deleteById(id);
    }
}