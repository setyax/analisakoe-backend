package id.analisakoe.sal.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.inv.dto.InventoryDtos.TransactionLine;
import id.analisakoe.inv.dto.InventoryDtos.TransactionRequest;
import id.analisakoe.inv.service.InventoryService;
import id.analisakoe.sal.entity.SalesReturnDtl;
import id.analisakoe.sal.entity.SalesReturnHdr;
import id.analisakoe.sal.repository.SalesReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SalesReturnService {
    private final SalesReturnRepository repo;
    private final InventoryService invService;
    private final JournalService journalService;

    public SalesReturnService(SalesReturnRepository repo, InventoryService invService, JournalService journalService) {
        this.repo = repo; this.invService = invService; this.journalService = journalService;
    }

    @Transactional
    public void create(LocalDate date, Long customerId, Long invoiceId, Long warehouseId, String remarks, List<LineReq> lines) {
        if(lines == null || lines.isEmpty()) throw new BusinessException("Minimal 1 item diretur");

        SalesReturnHdr hdr = new SalesReturnHdr();
        hdr.setTenantId(1L); hdr.setCompanyId(11L);
        hdr.setDocNo("SR-" + System.currentTimeMillis());
        hdr.setDocDate(date); hdr.setCustomerId(customerId); hdr.setSalesInvoiceId(invoiceId);
        hdr.setWarehouseId(warehouseId); hdr.setRemarks(remarks); hdr.setStatus("DRAFT");

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        for(var l : lines) {
            SalesReturnDtl d = new SalesReturnDtl();
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
        SalesReturnHdr hdr = repo.findById(id).orElseThrow(() -> new BusinessException("Retur tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya Retur DRAFT yang bisa diposting.");

        // 1. Tambah Stok ke Inventory (Mutasi IN)
        List<TransactionLine> invLines = new ArrayList<>();
        for (SalesReturnDtl d : hdr.getDetails()) {
            invLines.add(new TransactionLine(d.getItemId(), d.getUomId(), d.getQty()));
        }
        invService.createTransaction(new TransactionRequest("IN", hdr.getDocDate(), hdr.getWarehouseId(), "Retur Penjualan " + hdr.getDocNo(), invLines));

        // 2. Jurnal Retur Penjualan
        // Debit: Retur Penjualan (4102) sebesar DPP
        // Debit: Hutang PPN (2201) sebesar Pajak
        // Kredit: Piutang Usaha (1201) sebesar Total
        BigDecimal dpp = hdr.getTotalAmount().subtract(hdr.getTaxAmount());
        journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getDocDate(), "SALES_RETURN", "Retur Penjualan " + hdr.getDocNo(), List.of(
            new JournalService.JournalLine("4102", dpp, BigDecimal.ZERO, "Retur Penjualan"),
            new JournalService.JournalLine("2201", hdr.getTaxAmount(), BigDecimal.ZERO, "Pengurangan PPN"),
            new JournalService.JournalLine("1201", BigDecimal.ZERO, hdr.getTotalAmount(), "Pengurangan Piutang")
        ));

        hdr.setStatus("POSTED");
        repo.save(hdr);
    }

    public record LineReq(Long itemId, Long uomId, BigDecimal qty, BigDecimal price) {}
}