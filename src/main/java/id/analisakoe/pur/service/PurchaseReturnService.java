package id.analisakoe.pur.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.inv.dto.InventoryDtos.TransactionLine;
import id.analisakoe.inv.dto.InventoryDtos.TransactionRequest;
import id.analisakoe.inv.service.InventoryService;
import id.analisakoe.pur.entity.PurchaseReturnDtl;
import id.analisakoe.pur.entity.PurchaseReturnHdr;
import id.analisakoe.pur.repository.PurchaseReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PurchaseReturnService {
    private final PurchaseReturnRepository repo;
    private final InventoryService invService;
    private final JournalService journalService;

    public PurchaseReturnService(PurchaseReturnRepository repo, InventoryService invService, JournalService journalService) {
        this.repo = repo; this.invService = invService; this.journalService = journalService;
    }

    @Transactional
    public void create(LocalDate date, Long supplierId, Long invoiceId, Long warehouseId, String remarks, List<LineReq> lines) {
        if(lines == null || lines.isEmpty()) throw new BusinessException("Minimal 1 item diretur");

        PurchaseReturnHdr hdr = new PurchaseReturnHdr();
        hdr.setTenantId(1L); hdr.setCompanyId(1L);
        hdr.setDocNo("PRT-" + System.currentTimeMillis());
        hdr.setDocDate(date); hdr.setSupplierId(supplierId); hdr.setPurchaseInvoiceId(invoiceId);
        hdr.setWarehouseId(warehouseId); hdr.setRemarks(remarks); hdr.setStatus("DRAFT");

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        for(var l : lines) {
            PurchaseReturnDtl d = new PurchaseReturnDtl();
            d.setItemId(l.itemId()); d.setUomId(l.uomId());
            d.setQty(l.qty()); d.setPrice(l.price());
            BigDecimal lineTotal = l.qty().multiply(l.price());
            d.setTotal(lineTotal);
            hdr.addDetail(d);

            total = total.add(lineTotal);
            tax = tax.add(lineTotal.multiply(new BigDecimal("0.11"))); // Asumsi PPN 11%
        }

        hdr.setTotalAmount(total.add(tax));
        hdr.setTaxAmount(tax);
        repo.save(hdr);
    }

    @Transactional
    public void post(Long id) {
        PurchaseReturnHdr hdr = repo.findById(id).orElseThrow(() -> new BusinessException("Retur tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya Retur DRAFT yang bisa diposting.");

        // 1. Kurangi Stok (Mutasi OUT)
        List<TransactionLine> invLines = new ArrayList<>();
        for (PurchaseReturnDtl d : hdr.getDetails()) {
            invLines.add(new TransactionLine(d.getItemId(), d.getUomId(), d.getQty()));
        }
        invService.createTransaction(new TransactionRequest("OUT", hdr.getDocDate(), hdr.getWarehouseId(), "Retur Pembelian " + hdr.getDocNo(), invLines));

        // 2. Jurnal Retur Pembelian
        // Debit: Hutang Usaha (2101) sebesar Total
        // Kredit: Retur Pembelian (5102) sebesar DPP
        // Kredit: Hutang PPN (2201) sebesar Pajak
        BigDecimal dpp = hdr.getTotalAmount().subtract(hdr.getTaxAmount());
        journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getDocDate(), "PURCHASE_RETURN", "Retur Pembelian " + hdr.getDocNo(), List.of(
            new JournalService.JournalLine("2101", hdr.getTotalAmount(), BigDecimal.ZERO, "Pengurangan Hutang"),
            new JournalService.JournalLine("5102", BigDecimal.ZERO, dpp, "Retur Pembelian"),
            new JournalService.JournalLine("2201", BigDecimal.ZERO, hdr.getTaxAmount(), "Pengurangan PPN")
        ));

        hdr.setStatus("POSTED");
        repo.save(hdr);
    }

    public record LineReq(Long itemId, Long uomId, BigDecimal qty, BigDecimal price) {}
}