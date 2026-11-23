package id.analisakoe.pur.service;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.service.NumberRangeService;

import id.analisakoe.common.BusinessException;
import id.analisakoe.pur.entity.PurchaseOrderDtl;
import id.analisakoe.pur.entity.PurchaseOrderHdr;
import id.analisakoe.pur.entity.PurchaseRequestDtl;
import id.analisakoe.pur.entity.PurchaseRequestHdr;
import id.analisakoe.pur.repository.PurchaseOrderRepository;
import id.analisakoe.pur.repository.PurchaseRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import id.analisakoe.web.ui.SessionUser;
import java.time.OffsetDateTime;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class PurchasingService {

    @Autowired
    private NumberRangeService numberRangeService;
    private final PurchaseRequestRepository prRepo;
    private final PurchaseOrderRepository poRepo;
	private final PurchasePolicyService policyService;

    public PurchasingService(PurchaseRequestRepository prRepo,
                          PurchaseOrderRepository poRepo,
                          PurchasePolicyService policyService) {
    this.prRepo = prRepo;
    this.poRepo = poRepo;
    this.policyService = policyService;
}

    // ==================== PURCHASE REQUEST ====================

    @Transactional
    public void createPr(LocalDate date, String remarks, Long requisitionerId, List<LineReq> lines) {
        if(lines == null || lines.isEmpty()) throw new BusinessException("Minimal 1 item");
        PurchaseRequestHdr hdr = new PurchaseRequestHdr();
        hdr.setTenantId(1L); hdr.setCompanyId(1L);
        hdr.setDocNo(numberRangeService.generate("PR", "PR"));
        hdr.setDocDate(date); hdr.setRemarks(remarks); hdr.setStatus("DRAFT");
        hdr.setDocType("STANDARD"); hdr.setPurchasingOrg("1000"); hdr.setPurchasingGroup("001"); 
        hdr.setRequisitionerId(requisitionerId);

        for(var l : lines) {
            PurchaseRequestDtl d = new PurchaseRequestDtl();
            d.setItemId(l.itemId()); d.setUomId(l.uomId()); d.setQty(l.qty());
            d.setShortText(l.shortText()); d.setDeliveryDate(l.deliveryDate());
            d.setAccountAssignment(l.accountAssignment());
            hdr.addDetail(d);
        }
        prRepo.save(hdr);
    }

    @Transactional
    public void updatePr(Long id, LocalDate date, String remarks, Long requisitionerId, List<LineReq> lines) {
        PurchaseRequestHdr hdr = prRepo.findById(id)
            .orElseThrow(() -> new BusinessException("Purchase Request tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya PR berstatus DRAFT yang bisa diedit.");

        hdr.setDocDate(date); hdr.setRemarks(remarks); hdr.setRequisitionerId(requisitionerId);
        hdr.getDetails().clear();
        for(var l : lines) {
            PurchaseRequestDtl d = new PurchaseRequestDtl();
            d.setItemId(l.itemId()); d.setUomId(l.uomId()); d.setQty(l.qty());
            d.setShortText(l.shortText()); d.setDeliveryDate(l.deliveryDate());
            d.setAccountAssignment(l.accountAssignment());
            hdr.addDetail(d);
        }
        prRepo.save(hdr);
    }

    /** Batalkan PR — hanya yang masih DRAFT. */
    @Transactional
    public void cancelPr(Long id) {
        PurchaseRequestHdr hdr = prRepo.findById(id)
            .orElseThrow(() -> new BusinessException("Purchase Request tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus()))
            throw new BusinessException("Hanya PR berstatus DRAFT yang bisa dibatalkan.");
        hdr.setStatus("CANCELLED");
        prRepo.save(hdr);
    }

    /** Hapus PR — hanya DRAFT / CANCELLED. Detail ikut terhapus (cascade). */
    @Transactional
    public void deletePr(Long id) {
        PurchaseRequestHdr hdr = prRepo.findById(id)
            .orElseThrow(() -> new BusinessException("Purchase Request tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus()) && !"CANCELLED".equals(hdr.getStatus()))
            throw new BusinessException("Hanya PR berstatus DRAFT/CANCELLED yang bisa dihapus.");
        prRepo.delete(hdr);
    }

    // ==================== PURCHASE ORDER ====================

    @Transactional
    public PurchaseOrderHdr createPo(LocalDate date, Long supplierId, Long prId, String remarks,
                                  String currency, String taxType, List<LineReq> lines) {
    PurchaseOrderHdr hdr = new PurchaseOrderHdr();
    hdr.setDocNo(numberRangeService.generate("PO", "PO"));
    hdr.setDocDate(date);
    hdr.setSupplierId(supplierId);
    hdr.setPrId(prId);
    // ↓ TAMBAH BARIS INI
    hdr.setPoType(prId != null
        ? PurchaseOrderHdr.PoType.FROM_PR
        : PurchaseOrderHdr.PoType.DIRECT);
    hdr.setStatus("DRAFT");
    hdr.setRemarks(remarks);
    hdr.setCurrency(currency != null ? currency : "IDR");
    hdr.setTaxType(taxType != null ? taxType : "NO_TAX");
    hdr.setTenantId(1L);
    hdr.setCompanyId(1L);

    processPoLines(hdr, lines, taxType);
    poRepo.save(hdr);          // ← simpan
    return hdr;                 // ← TAMBAH return
}
	
	// ============================================================
// V28: DIRECT PO + BYPASS
// ============================================================

/**
 * Buat Direct PO (tanpa PR), dengan validasi policy.
 * Kalau policy mewajibkan PR → wajib `bypassReason`.
 */
@Transactional
public PurchaseOrderHdr createDirectPo(LocalDate date,
                                        Long supplierId,
                                        java.math.BigDecimal totalAmount,
                                        String remarks,
                                        String currency,
                                        String taxType,
                                        Boolean isNewSupplier,
                                        String bypassReason,
                                        List<LineReq> lines) {

    // 1. Cek policy
    boolean requiresPr = policyService.requiresPr(
        1L,
        new PurchasePolicyService.PurchaseContext(
            totalAmount,
            Boolean.TRUE.equals(isNewSupplier),
            java.util.List.of()
        )
    );

    if (requiresPr && (bypassReason == null || bypassReason.isBlank())) {
        throw new IllegalStateException(
            "PO ini wajib melalui PR. Ajukan PR terlebih dahulu, " +
            "atau isi alasan bypass untuk disetujui manager.");
    }

    // 2. Buat PO via createPo (prId = null → DIRECT)
    PurchaseOrderHdr po = createPo(date, supplierId, null, remarks, currency, taxType, lines);

    // 3. Set bypass kalau policy mewajibkan
    if (requiresPr) {
        po.setBypassReason(bypassReason);
        poRepo.save(po);
    }

    return po;
}

/**
 * Approve bypass Direct PO.
 */
@Transactional
public void approveBypass(Long poId, SessionUser approver) {
    PurchaseOrderHdr po = poRepo.findById(poId)
        .orElseThrow(() -> new IllegalArgumentException("PO tidak ditemukan"));

    if (po.getPoType() != PurchaseOrderHdr.PoType.DIRECT
            || po.getBypassReason() == null) {
        throw new IllegalStateException("PO ini tidak memerlukan approval bypass");
    }
    if (!approver.hasPermission("pur.po.bypass.approve")) {
        throw new SecurityException("Anda tidak berhak menyetujui bypass PR");
    }

    po.setBypassApprovedBy(approver.getUserId());
    po.setBypassApprovedAt(OffsetDateTime.now());
    poRepo.save(po);
}

    @Transactional
    public void updatePo(Long id, LocalDate date, Long supplierId, String remarks, String currency, String taxType, List<LineReq> lines) {
        PurchaseOrderHdr hdr = poRepo.findById(id)
            .orElseThrow(() -> new BusinessException("PO tidak ditemukan"));
        
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya PO berstatus DRAFT yang bisa diedit.");

        hdr.setDocDate(date); hdr.setSupplierId(supplierId); hdr.setRemarks(remarks);
        hdr.setCurrency(currency != null ? currency : "IDR");
        hdr.setTaxType(taxType != null ? taxType : "NO_TAX");
        
        hdr.getDetails().clear();
        processPoLines(hdr, lines, taxType);
        poRepo.save(hdr);
    }

    private void processPoLines(PurchaseOrderHdr hdr, List<LineReq> lines, String taxType) {
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for(var l : lines) {
            PurchaseOrderDtl d = new PurchaseOrderDtl();
            d.setItemId(l.itemId()); d.setUomId(l.uomId());
            d.setQty(l.qty()); 
            BigDecimal price = l.price() == null ? BigDecimal.ZERO : l.price();
            d.setPrice(price);
            
            BigDecimal subtotal = l.qty().multiply(price);
            BigDecimal dpp = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal lineTotal = subtotal;

            if ("EXCLUDE".equals(taxType)) {
                dpp = subtotal;
                tax = dpp.multiply(new BigDecimal("0.11")).setScale(2, RoundingMode.HALF_UP);
                lineTotal = dpp.add(tax);
            } else if ("INCLUDE".equals(taxType)) {
                lineTotal = subtotal;
                dpp = lineTotal.divide(new BigDecimal("1.11"), 2, RoundingMode.HALF_UP);
                tax = lineTotal.subtract(dpp);
            } else {
                dpp = subtotal;
            }

            d.setSubtotal(subtotal); d.setDpp(dpp); d.setTaxAmount(tax); d.setLineTotal(lineTotal);
            hdr.addDetail(d);

            totalTax = totalTax.add(tax);
            grandTotal = grandTotal.add(lineTotal);
        }
        hdr.setTaxAmount(totalTax);
        hdr.setTotalAmount(grandTotal);
    }

    public record LineReq(Long itemId, Long uomId, BigDecimal qty, BigDecimal price, String shortText, LocalDate deliveryDate, String accountAssignment) {}
    @Transactional
    public void confirmPo(Long poId) {
        PurchaseOrderHdr po = poRepo.findById(poId)
            .orElseThrow(() -> new BusinessException("PO tidak ditemukan"));
        if (!"DRAFT".equals(po.getStatus())) {
            throw new BusinessException("Hanya PO berstatus DRAFT yang bisa dikonfirmasi.");
        }
        po.setStatus("CONFIRMED");
        poRepo.save(po);
    }
	
	// ===== Direct PO support (V28) =====
public enum PoType { FROM_PR, DIRECT }

@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
@jakarta.persistence.Column(name = "po_type", nullable = false)
private PoType poType = PoType.FROM_PR;

@jakarta.persistence.Column(name = "bypass_reason", columnDefinition = "text")
private String bypassReason;

@jakarta.persistence.Column(name = "bypass_approved_by")
private Long bypassApprovedBy;

@jakarta.persistence.Column(name = "bypass_approved_at")
private java.time.OffsetDateTime bypassApprovedAt;

public PoType getPoType() { return poType; }
public void setPoType(PoType v) { this.poType = v; }
public String getBypassReason() { return bypassReason; }
public void setBypassReason(String v) { this.bypassReason = v; }
public Long getBypassApprovedBy() { return bypassApprovedBy; }
public void setBypassApprovedBy(Long v) { this.bypassApprovedBy = v; }
public java.time.OffsetDateTime getBypassApprovedAt() { return bypassApprovedAt; }
public void setBypassApprovedAt(java.time.OffsetDateTime v) { this.bypassApprovedAt = v; }
}