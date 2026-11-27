package id.analisakoe.md.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.context.ContextService;
import id.analisakoe.md.dto.ItemRequest;
import id.analisakoe.md.dto.ItemResponse;
import id.analisakoe.md.entity.Item;
import id.analisakoe.md.repository.ItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ItemService {

    private final ItemRepository repo;
    private final ContextService context;

    public ItemService(ItemRepository repo, ContextService context) {
        this.repo = repo;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> list(String search, Pageable pageable) {
        Long tenantId = context.tenantId();
        Long companyId = context.companyId();
        Page<Item> page = (search == null || search.isBlank())
            ? repo.findByTenantIdAndCompanyIdAndDeletedAtIsNull(tenantId, companyId, pageable)
            : repo.findByTenantIdAndCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                tenantId, companyId, search, pageable);
        return PageResponse.of(page.map(ItemResponse::from));
    }

    @Transactional(readOnly = true)
    public ItemResponse get(Long id) {
        Item e = repo.findByIdAndTenantIdAndCompanyIdAndDeletedAtIsNull(
                id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new EntityNotFoundException("Item tidak ditemukan: " + id));
        return ItemResponse.from(e);
    }

    @Transactional
    public ItemResponse create(ItemRequest req) {
        Long tenantId = context.tenantId();
        Long companyId = context.companyId();
        if (repo.existsByTenantIdAndCompanyIdAndCode(tenantId, companyId, req.code())) {
            throw new BusinessException("Kode item sudah dipakai: " + req.code());
        }
        Item e = new Item();
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        apply(e, req);
        return ItemResponse.from(repo.save(e));
    }

    @Transactional
    public ItemResponse update(Long id, ItemRequest req) {
        Item e = repo.findByIdAndTenantIdAndCompanyIdAndDeletedAtIsNull(
                id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new EntityNotFoundException("Item tidak ditemukan: " + id));
        apply(e, req);
        return ItemResponse.from(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        Item e = repo.findByIdAndTenantIdAndCompanyIdAndDeletedAtIsNull(
                id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new EntityNotFoundException("Item tidak ditemukan: " + id));
        e.setDeletedAt(OffsetDateTime.now());
        repo.save(e);
    }

    private void apply(Item e, ItemRequest req) {
        e.setCode(req.code().trim());
        e.setName(req.name().trim());
        e.setDescription(req.description());
        e.setCategoryId(req.categoryId());
        e.setBaseUomId(req.baseUomId());
        e.setItemType(req.itemType() == null ? "STOCK" : req.itemType());
        e.setTrackLot(req.trackLot() == null ? Boolean.FALSE : req.trackLot());
        e.setTrackSerial(req.trackSerial() == null ? Boolean.FALSE : req.trackSerial());
        e.setBarcode(req.barcode());
        e.setStatus(req.status() == null ? "ACTIVE" : req.status());

        // SAP Purchasing & Inventory Valuation fields
        e.setPurchasingGroup(req.purchasingGroup());
        e.setLeadTimeDays(req.leadTimeDays());
        e.setPriceControl(req.priceControl());
        e.setValuationClass(req.valuationClass());
        e.setStandardPrice(req.standardPrice());

        // DJP goods code is an optional FK. An empty form value must be SQL NULL,
        // not an empty string that cannot match a row in md.djp_goods_code.
        String djpCode = req.djpGoodsCode();
        e.setDjpGoodsCode(djpCode == null || djpCode.isBlank() ? null : djpCode.trim());
    }
}
