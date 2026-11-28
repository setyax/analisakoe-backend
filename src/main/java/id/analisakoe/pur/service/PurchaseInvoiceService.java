package id.analisakoe.pur.service;

import id.analisakoe.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.service.NumberRangeService;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.pur.entity.*;
import id.analisakoe.pur.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PurchaseInvoiceService {

    @Autowired
    private ContextService context;

    @Autowired
    private NumberRangeService numberRangeService;
    private final PurchaseInvoiceRepository invRepo;
    private final GrnRepository grnRepo;
    private final PurchaseOrderRepository poRepo;
    private final JournalService journalService;

    public PurchaseInvoiceService(PurchaseInvoiceRepository invRepo, GrnRepository grnRepo, PurchaseOrderRepository poRepo, JournalService journalService)  {
        this.invRepo = invRepo;
        this.grnRepo = grnRepo;
        this.poRepo = poRepo;
        this.journalService = journalService;
    }

    @Transactional
    public void createFromGrn(Long grnId, String supplierInvoiceNo, LocalDate invDate, LocalDate dueDate, String remarks) {
        GrnHdr grn = grnRepo.findById(grnId).orElseThrow(() -> new BusinessException("GRN tidak ditemukan"));
        
        // Cek apakah GRN sudah memiliki Invoice aktif
        List<PurchaseInvoiceHdr> existing = invRepo.findByGrnIdAndStatusNot(grnId, "VOID");
        if (!existing.isEmpty()) {
            throw new BusinessException("GRN ini sudah ditagih. Silakan Void Invoice sebelumnya jika ingin membuat ulang.");
        }
        
        // Ambil PO untuk mendapatkan harga
        PurchaseOrderHdr po = poRepo.findById(grn.getPoId()).orElse(null);

        PurchaseInvoiceHdr hdr = new PurchaseInvoiceHdr();
        hdr.setTenantId(context.tenantId()); hdr.setCompanyId(context.companyId());
        hdr.setDocNo("DOC-" + System.currentTimeMillis());
        hdr.setSupplierInvoiceNo(supplierInvoiceNo);
        hdr.setInvoiceDate(invDate); hdr.setDueDate(dueDate);
        hdr.setGrnId(grnId);
        hdr.setRemarks(remarks);
        hdr.setStatus("DRAFT");
        hdr.setSupplierId(po != null ? po.getSupplierId() : 1L);
        
        BigDecimal total = BigDecimal.ZERO;
        for (var gDtl : grn.getDetails()) {
            PurchaseInvoiceDtl d = new PurchaseInvoiceDtl();
            d.setItemId(gDtl.getItemId());
            d.setUomId(gDtl.getUomId());
            d.setQty(gDtl.getQtyReceived());
            
            // Cari harga dari PO berdasarkan Item ID
            BigDecimal price = BigDecimal.ZERO;
            if (po != null) {
                price = po.getDetails().stream()
                    .filter(pDtl -> pDtl.getItemId().equals(gDtl.getItemId()))
                    .map(PurchaseOrderDtl::getPrice)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            }
            
            d.setPrice(price);
            BigDecimal lineTotal = gDtl.getQtyReceived().multiply(price);
            d.setTotal(lineTotal);
            
            hdr.addDetail(d);
            total = total.add(lineTotal);
        }
        hdr.setTotalAmount(total);
        invRepo.save(hdr);
    }

    @Transactional
    public void postInvoice(Long invId) {
        PurchaseInvoiceHdr hdr = invRepo.findByIdAndTenantIdAndCompanyId(invId, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya Invoice DRAFT yang bisa di-posting.");

        journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getInvoiceDate(), "PURCHASE", "Auto-Journal Purchase Invoice " + hdr.getDocNo(), List.of(
            new JournalService.JournalLine("2105", hdr.getTotalAmount(), BigDecimal.ZERO, "GR/IR Clearing (penyelesaian)"),
            new JournalService.JournalLine("2101", BigDecimal.ZERO, hdr.getTotalAmount(), "Hutang Usaha")
        ));

        hdr.setStatus("POSTED");
        invRepo.save(hdr);
    }

    @Transactional
    public void updateInvoice(Long id, LocalDate dueDate, String remarks) {
        PurchaseInvoiceHdr hdr = invRepo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya Invoice DRAFT yang bisa diedit.");
        
        hdr.setDueDate(dueDate);
        hdr.setRemarks(remarks);
        invRepo.save(hdr);
    }

    @Transactional
    public void unpostInvoice(Long id) {
        PurchaseInvoiceHdr hdr = invRepo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"POSTED".equals(hdr.getStatus())) throw new BusinessException("Hanya Invoice POSTED yang bisa di-Unpost.");

        // Jurnal Pembalik (Reversal)
        journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getInvoiceDate(), "REVERSE", "Unpost Purchase Invoice " + hdr.getDocNo(), List.of(
            new JournalService.JournalLine("2101", hdr.getTotalAmount(), BigDecimal.ZERO, "Hutang Usaha (Reversal)"),
            new JournalService.JournalLine("2105", BigDecimal.ZERO, hdr.getTotalAmount(), "GR/IR Clearing (Reversal)")
        ));

        hdr.setStatus("DRAFT");
        invRepo.save(hdr);
    }
    @Transactional
    public void voidInvoice(Long id) {
        PurchaseInvoiceHdr hdr = invRepo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if ("VOID".equals(hdr.getStatus())) throw new BusinessException("Invoice sudah VOID.");

        // Jika invoice sudah POSTED, buat jurnal pembalik
        if ("POSTED".equals(hdr.getStatus())) {
            journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getInvoiceDate(), "REVERSE", "Void Purchase Invoice " + hdr.getDocNo(), List.of(
                new JournalService.JournalLine("2101", hdr.getTotalAmount(), BigDecimal.ZERO, "Hutang Usaha (Void)"),
                new JournalService.JournalLine("2105", BigDecimal.ZERO, hdr.getTotalAmount(), "GR/IR Clearing (Void)")
            ));
        }

        hdr.setStatus("VOID");
        invRepo.save(hdr);
    }
	
	    @org.springframework.transaction.annotation.Transactional
    public void deleteInvoice(Long id) {
        PurchaseInvoiceHdr inv = invRepo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId())
            .orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"DRAFT".equals(inv.getStatus())) {
            throw new BusinessException("Hanya Invoice berstatus DRAFT yang bisa dihapus.");
        }
        invRepo.delete(inv);
    }
}