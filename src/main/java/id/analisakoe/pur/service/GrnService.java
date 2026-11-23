package id.analisakoe.pur.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.inv.service.InventoryService;
import id.analisakoe.core.service.NumberRangeService;
import id.analisakoe.pur.entity.GrnDtl;
import id.analisakoe.pur.entity.GrnHdr;
import id.analisakoe.pur.entity.PurchaseOrderHdr;
import id.analisakoe.pur.repository.GrnRepository;
import id.analisakoe.pur.repository.PurchaseOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GrnService {
    private final GrnRepository grnRepo;
    private final PurchaseOrderRepository poRepo;
    private final NumberRangeService numberRangeService;
    private final id.analisakoe.fin.service.FiscalPeriodGuardService fiscalPeriodGuard;

    public GrnService(GrnRepository grnRepo, PurchaseOrderRepository poRepo, NumberRangeService numberRangeService, id.analisakoe.fin.service.FiscalPeriodGuardService fiscalPeriodGuard) {
        this.numberRangeService = numberRangeService;
        this.fiscalPeriodGuard = fiscalPeriodGuard;
        this.grnRepo = grnRepo; this.poRepo = poRepo;
    }

    @Transactional
    public void createGrn(Long poId, LocalDate date, String remarks, List<LineReq> lines) {
        PurchaseOrderHdr po = poRepo.findById(poId)
            .orElseThrow(() -> new BusinessException("PO tidak ditemukan"));
			
		validateQtyAgainstPo(po, null, lines);	

        GrnHdr hdr = new GrnHdr();
        hdr.setTenantId(po.getTenantId()); hdr.setCompanyId(po.getCompanyId());
        hdr.setDocNo(numberRangeService.generate("GRN", "GRN"));
        hdr.setDocDate(date); hdr.setPoId(poId); hdr.setRemarks(remarks);
        hdr.setStatus("DRAFT");

        for (var l : lines) {
            if (l.qtyReceived().compareTo(BigDecimal.ZERO) <= 0) continue;
            GrnDtl d = new GrnDtl();
            d.setItemId(l.itemId()); d.setUomId(l.uomId()); d.setQtyReceived(l.qtyReceived());
            hdr.addDetail(d);
        }
		
		// ===== V28: Guard Direct PO =====
if (hdr.getPoId() != null) {
    var poCheck = poRepo.findById(hdr.getPoId())
        .orElseThrow(() -> new IllegalArgumentException("PO tidak ditemukan"));

    if (!"CONFIRMED".equals(poCheck.getStatus())) {
        throw new IllegalStateException(
            "GRN hanya bisa dibuat untuk PO berstatus CONFIRMED. " +
            "Status saat ini: " + poCheck.getStatus());
    }

    if (poCheck.getPoType() == id.analisakoe.pur.entity.PurchaseOrderHdr.PoType.DIRECT
            && poCheck.getBypassReason() != null
            && poCheck.getBypassApprovedBy() == null) {
        throw new IllegalStateException(
            "Direct PO ini memiliki bypass yang belum disetujui manager. " +
            "GRN tidak bisa dibuat sampai bypass disetujui.");
    }
}
        grnRepo.save(hdr);
    }

    @Transactional
    public void confirmGrn(Long id) {
        GrnHdr hdr = grnRepo.findById(id).orElseThrow(() -> new BusinessException("GRN tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya GRN berstatus DRAFT yang bisa di-Confirm.");
		
		// BARU: validasi final sebelum stok bertambah
        if (hdr.getPoId() != null) {
            PurchaseOrderHdr po = poRepo.findById(hdr.getPoId()).orElse(null);
            if (po != null) {
                List<LineReq> current = new ArrayList<>();
                for (var d : hdr.getDetails()) {
                    current.add(new LineReq(d.getItemId(), d.getUomId(),
                        d.getQtyReceived() != null ? d.getQtyReceived() : BigDecimal.ZERO));
                }
                validateQtyAgainstPo(po, id, current);
            }
        }

        fiscalPeriodGuard.assertOpen(hdr.getTenantId(), hdr.getCompanyId(), hdr.getDocDate(), "GRN", hdr.getDocNo());

        // 1. Update Status PO menjadi RECEIVED
        PurchaseOrderHdr po = poRepo.findById(hdr.getPoId()).orElse(null);
        if (po != null) {
            po.setStatus("RECEIVED");
            poRepo.save(po);
        }

        // 2. Update Status GRN
        hdr.setStatus("CONFIRMED");
        grnRepo.save(hdr);
    }

    @Transactional
    public void unconfirmGrn(Long id) {
        GrnHdr hdr = grnRepo.findById(id)
            .orElseThrow(() -> new BusinessException("GRN tidak ditemukan"));

        if (!"CONFIRMED".equals(hdr.getStatus())) {
            throw new BusinessException(
                "Hanya GRN berstatus CONFIRMED yang bisa di-Unconfirm."
            );
        }

        // Tanggal kontrol mengikuti tanggal dokumen reversal yang digunakan
        // oleh Stock Movement dan Journal pada controller.
        fiscalPeriodGuard.assertOpen(
            hdr.getTenantId(),
            hdr.getCompanyId(),
            hdr.getDocDate(),
            "GRN UNCONFIRM",
            hdr.getDocNo()
        );

        // Jangan membuat Inventory OUT di sini.
        // StockValuationService pada controller sudah melakukan satu kali OUT.

        PurchaseOrderHdr po = poRepo.findById(hdr.getPoId()).orElse(null);
        if (po != null) {
            po.setStatus("CONFIRMED");
            poRepo.save(po);
        }

        hdr.setStatus("DRAFT");
        grnRepo.save(hdr);
    }

    @Transactional
    public void voidGrn(Long id) {
        GrnHdr hdr = grnRepo.findById(id).orElseThrow(() -> new BusinessException("GRN tidak ditemukan"));
        if ("CONFIRMED".equals(hdr.getStatus())) {
            throw new BusinessException("GRN sudah CONFIRMED. Lakukan Unconfirm terlebih dahulu sebelum Void.");
        }
        hdr.setStatus("VOID");
        grnRepo.save(hdr);
    }
	
	        @Transactional
    public void updateQty(Long grnId, List<LineQty> lines) {
        GrnHdr hdr = grnRepo.findById(grnId)
            .orElseThrow(() -> new BusinessException("GRN tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) {
            throw new BusinessException("Hanya GRN berstatus DRAFT yang bisa diubah.");
        }
        if (hdr.getPoId() == null) {
            throw new BusinessException("GRN tidak punya referensi PO ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â tidak bisa divalidasi.");
        }

        // Map detailId -> qty baru
        Map<Long, BigDecimal> newQty = new HashMap<>();
        for (LineQty l : lines) {
            if (l.qty() != null && l.qty().compareTo(BigDecimal.ZERO) > 0) {
                newQty.put(l.detailId(), l.qty());
            }
        }

        // Bangun "proposed state" = detail lama + qty baru
        List<LineReq> proposed = new ArrayList<>();
        for (var d : hdr.getDetails()) {
            BigDecimal q = newQty.getOrDefault(d.getId(), d.getQtyReceived());
            proposed.add(new LineReq(d.getItemId(), d.getUomId(),
                q != null ? q : BigDecimal.ZERO));
        }

        // Validasi terhadap PO
        PurchaseOrderHdr po = poRepo.findById(hdr.getPoId())
            .orElseThrow(() -> new BusinessException("PO tidak ditemukan"));
        validateQtyAgainstPo(po, grnId, proposed);

        // Apply
        for (var d : hdr.getDetails()) {
            BigDecimal q = newQty.get(d.getId());
            if (q != null) d.setQtyReceived(q);
        }
        grnRepo.save(hdr);
    }
	
	    /**
     * Validasi: total qty yang akan diterima untuk tiap item
     * (dari GRN ini + GRN lain non-VOID untuk PO yang sama) <= qty PO item tersebut.
     *
     * @param po            header PO
     * @param excludeGrnId  id GRN yang SEDANG diedit (agar tidak dobel-hitung). null untuk GRN baru.
     * @param proposedLines line yang akan disimpan (itemId + qtyReceived)
     */
    private void validateQtyAgainstPo(PurchaseOrderHdr po, Long excludeGrnId, List<LineReq> proposedLines) {
        // 1. Qty PO per item
        Map<Long, BigDecimal> poQty = new HashMap<>();
        for (var pod : po.getDetails()) {
            BigDecimal q = pod.getQty() != null ? pod.getQty() : BigDecimal.ZERO;
            poQty.merge(pod.getItemId(), q, BigDecimal::add);
        }

        // 2. Qty dari GRN lain (non-VOID, selain GRN yang sedang diedit)
        Map<Long, BigDecimal> otherQty = new HashMap<>();
        for (GrnHdr other : grnRepo.findByPoId(po.getId())) {
            if (excludeGrnId != null && excludeGrnId.equals(other.getId())) continue;
            if ("VOID".equals(other.getStatus())) continue;
            for (var d : other.getDetails()) {
                if (d.getQtyReceived() != null) {
                    otherQty.merge(d.getItemId(), d.getQtyReceived(), BigDecimal::add);
                }
            }
        }

        // 3. Qty dari line yang akan disimpan
        Map<Long, BigDecimal> thisQty = new HashMap<>();
        for (var l : proposedLines) {
            if (l.qtyReceived() != null && l.qtyReceived().compareTo(BigDecimal.ZERO) > 0) {
                thisQty.merge(l.itemId(), l.qtyReceived(), BigDecimal::add);
            }
        }

        // 4. Validasi
        for (var e : thisQty.entrySet()) {
            Long itemId = e.getKey();
            BigDecimal t = e.getValue();
            BigDecimal o = otherQty.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal p = poQty.getOrDefault(itemId, BigDecimal.ZERO);
            if (t.add(o).compareTo(p) > 0) {
                throw new BusinessException(
                    "Qty item #" + itemId + " melebihi PO. " +
                    "PO=" + p.toPlainString() +
                    ", sudah diterima GRN lain=" + o.toPlainString() +
                    ", GRN ini=" + t.toPlainString()
                );
            }
        }
    }

	
    public record LineQty(Long detailId, BigDecimal qty) {}

	
    public record LineReq(Long itemId, Long uomId, BigDecimal qtyReceived) {}
}