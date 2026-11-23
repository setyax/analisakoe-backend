package id.analisakoe.inv.service;

import id.analisakoe.core.service.StockValuationService;
import id.analisakoe.inv.entity.StockTransferDtl;
import id.analisakoe.inv.entity.StockTransferHdr;
import id.analisakoe.inv.repository.StockTransferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StockTransferService {
    private final StockTransferRepository repo;
    private final StockValuationService stockValuationService;
    @PersistenceContext
    private EntityManager em;

    public StockTransferService(StockTransferRepository repo, StockValuationService stockValuationService) {
        this.repo = repo;
        this.stockValuationService = stockValuationService;
    }

    @Transactional
    public void create(StockTransferHdr hdr) {
        if (hdr.getFromWarehouseId() == null || hdr.getToWarehouseId() == null) {
            throw new IllegalArgumentException("Gudang asal dan tujuan wajib dipilih.");
        }
        if (hdr.getFromWarehouseId().equals(hdr.getToWarehouseId())) {
            throw new IllegalArgumentException("Gudang asal dan tujuan tidak boleh sama.");
        }
        Long tenantId = validateWarehousePair(hdr.getFromWarehouseId(), hdr.getToWarehouseId());
        if (hdr.getDetails() == null || hdr.getDetails().isEmpty()) {
            throw new IllegalArgumentException("Transfer minimal harus memiliki satu detail barang.");
        }
        hdr.setTenantId(tenantId);
        hdr.setStatus("DRAFT");
        repo.save(hdr);
    }

    @Transactional
    public void confirm(Long id) {
        StockTransferHdr hdr = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer tidak ditemukan."));
        if (!"DRAFT".equalsIgnoreCase(hdr.getStatus())) {
            throw new IllegalStateException("Hanya transfer berstatus DRAFT yang dapat di-confirm.");
        }
        Long tenantId = validateWarehousePair(hdr.getFromWarehouseId(), hdr.getToWarehouseId());
        if (hdr.getTenantId() == null || !tenantId.equals(hdr.getTenantId())) {
            throw new IllegalStateException("Tenant transfer tidak sesuai dengan tenant gudang.");
        }
        if (hdr.getTransferDate() == null) throw new IllegalStateException("Tanggal transfer belum diisi.");
        if (hdr.getDetails() == null || hdr.getDetails().isEmpty()) {
            throw new IllegalStateException("Transfer tidak memiliki detail barang.");
        }

        // Satu transaksi database: jika OUT atau IN gagal, seluruh posting di-rollback.
        for (StockTransferDtl detail : hdr.getDetails()) {
            if (detail.getItemId() == null || detail.getQty() == null || detail.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Detail transfer memiliki item atau qty yang tidak valid.");
            }
            BigDecimal transferCost = stockValuationService.stockOutForTenant(
                    tenantId, detail.getItemId(), hdr.getFromWarehouseId(), detail.getQty(),
                    hdr.getTransferDate(), "TRANSFER_OUT", hdr.getId(), hdr.getDocNo(),
                    "Transfer keluar " + hdr.getDocNo());

            stockValuationService.stockInForTenant(
                    tenantId, detail.getItemId(), hdr.getToWarehouseId(), detail.getQty(), transferCost,
                    hdr.getTransferDate(), "TRANSFER_IN", hdr.getId(), hdr.getDocNo(),
                    "Transfer masuk " + hdr.getDocNo());
        }

        hdr.setStatus("CONFIRMED");
        repo.save(hdr);
    }

    @Transactional
    public void voidTransfer(Long id) {
        StockTransferHdr hdr = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer tidak ditemukan."));
        if ("CONFIRMED".equalsIgnoreCase(hdr.getStatus())) {
            throw new IllegalStateException("Transfer CONFIRMED tidak boleh di-VOID langsung. Gunakan Unconfirm terlebih dahulu.");
        }
        if ("VOID".equalsIgnoreCase(hdr.getStatus())) {
            throw new IllegalStateException("Transfer sudah VOID.");
        }
        hdr.setStatus("VOID");
        repo.save(hdr);
    }

    /**
     * Unconfirm: kembalikan stok yang sudah dipindahkan saat confirm
     * (stok keluar dari gudang tujuan, masuk kembali ke gudang asal).
     */
    @Transactional
    public void unconfirm(Long id) {
        StockTransferHdr hdr = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer tidak ditemukan."));
        if (!"CONFIRMED".equalsIgnoreCase(hdr.getStatus())) {
            throw new IllegalStateException("Hanya transfer berstatus CONFIRMED yang dapat di-unconfirm.");
        }
        Long tenantId = validateWarehousePair(hdr.getFromWarehouseId(), hdr.getToWarehouseId());
        if (hdr.getDetails() == null || hdr.getDetails().isEmpty()) {
            throw new IllegalStateException("Transfer tidak memiliki detail barang.");
        }
        if (hdr.getTransferDate() == null) hdr.setTransferDate(java.time.LocalDate.now());

        for (StockTransferDtl detail : hdr.getDetails()) {
            if (detail.getItemId() == null || detail.getQty() == null || detail.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Detail transfer memiliki item atau qty yang tidak valid.");
            }
            // Stok keluar dari gudang TUJUAN (memakai avg cost gudang tujuan)
            BigDecimal cost = stockValuationService.stockOutForTenant(
                    tenantId, detail.getItemId(), hdr.getToWarehouseId(), detail.getQty(),
                    java.time.LocalDate.now(), "TRANSFER_IN_VOID", hdr.getId(), hdr.getDocNo(),
                    "Unconfirm: keluar " + hdr.getDocNo());
            // Stok masuk kembali ke gudang ASAL
            stockValuationService.stockInForTenant(
                    tenantId, detail.getItemId(), hdr.getFromWarehouseId(), detail.getQty(), cost,
                    java.time.LocalDate.now(), "TRANSFER_OUT_VOID", hdr.getId(), hdr.getDocNo(),
                    "Unconfirm: kembali " + hdr.getDocNo());
        }
        hdr.setStatus("DRAFT");
        repo.save(hdr);
    }

    /** Hapus transfer: hanya DRAFT atau VOID. */
    @Transactional
    public void deleteTransfer(Long id) {
        StockTransferHdr hdr = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer tidak ditemukan."));
        String st = hdr.getStatus() == null ? "" : hdr.getStatus().toUpperCase();
        if (!"DRAFT".equals(st) && !"VOID".equals(st)) {
            throw new IllegalStateException("Hanya transfer berstatus DRAFT atau VOID yang dapat dihapus. Status saat ini: " + st);
        }
        repo.delete(hdr); // details ter-cascade (orphanRemoval + CascadeType.ALL)
    }

    private Long validateWarehousePair(Long fromWarehouseId, Long toWarehouseId) {
        Object[] from = warehouseScope(fromWarehouseId);
        Object[] to = warehouseScope(toWarehouseId);
        Long fromTenant = ((Number) from[0]).longValue();
        Long toTenant = ((Number) to[0]).longValue();
        Long fromCompany = ((Number) from[1]).longValue();
        Long toCompany = ((Number) to[1]).longValue();
        if (!fromTenant.equals(toTenant) || !fromCompany.equals(toCompany)) {
            throw new IllegalArgumentException("Gudang asal dan tujuan harus berada pada tenant dan company yang sama.");
        }
        return fromTenant;
    }

    private Object[] warehouseScope(Long warehouseId) {
        List<?> rows = em.createNativeQuery(
                "SELECT tenant_id, company_id FROM md.warehouse WHERE id=:warehouseId")
                .setParameter("warehouseId", warehouseId).getResultList();
        if (rows.isEmpty()) throw new IllegalArgumentException("Gudang tidak ditemukan: " + warehouseId);
        return (Object[]) rows.get(0);
    }
}