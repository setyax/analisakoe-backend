package id.analisakoe.sal.service;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.service.NumberRangeService;

import id.analisakoe.common.BusinessException;
import id.analisakoe.inv.dto.InventoryDtos.TransactionLine;
import id.analisakoe.inv.dto.InventoryDtos.TransactionRequest;
import id.analisakoe.inv.service.InventoryService;
import id.analisakoe.sal.entity.*;
import id.analisakoe.sal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SalesDeliveryService {

    @Autowired
    private NumberRangeService numberRangeService;
    private final SalesOrderRepository soRepo;
    private final DeliveryOrderRepository doRepo;
    private final SalesInvoiceRepository invRepo;
    private final InventoryService invService;
    private final id.analisakoe.fin.service.JournalService journalService;

    public SalesDeliveryService(SalesOrderRepository soRepo, DeliveryOrderRepository doRepo, SalesInvoiceRepository invRepo, InventoryService invService, id.analisakoe.fin.service.JournalService journalService)  {
        this.soRepo = soRepo; this.doRepo = doRepo; this.invRepo = invRepo; this.invService = invService; this.journalService = journalService;
    }

        @Transactional
    public void createDoFromSo(Long soId) {
        SalesOrderHdr so = soRepo.findById(soId)
            .orElseThrow(() -> new BusinessException("SO tidak ditemukan"));
        
        if (!"CONFIRMED".equals(so.getStatus())) {
            throw new BusinessException("Hanya SO berstatus CONFIRMED yang bisa dikirim.");
        }

        // ============================================================
        // BUAT DO DENGAN STATUS DRAFT (bukan SHIPPED!)
        // Stok TIDAK dipotong di sini — akan dipotong saat Confirm DO
        // SO TIDAK diubah — tetap CONFIRMED sampai invoice dibuat
        // ============================================================
        DeliveryOrderHdr hdr = new DeliveryOrderHdr();
        hdr.setTenantId(1L);
        hdr.setCompanyId(11L);
        hdr.setDocNo("DOC-" + System.currentTimeMillis());
        hdr.setDocDate(LocalDate.now());
        hdr.setSoId(soId);
		hdr.setCustomerId(so.getCustomerId()); // <--- TAMBAHKAN INI
        hdr.setStatus("DRAFT");   // ← FIX: DRAFT, bukan SHIPPED

        for (SalesOrderDtl sDtl : so.getDetails()) {
            DeliveryOrderDtl d = new DeliveryOrderDtl();
            d.setItemId(sDtl.getItemId());
            d.setUomId(sDtl.getUomId());
            d.setQty(sDtl.getQty());
            hdr.addDetail(d);
        }

        doRepo.save(hdr);
        
        // ❌ JANGAN potong stok di sini — akan dipotong saat Confirm DO
        // ❌ JANGAN ubah status SO — tetap CONFIRMED sampai proses selesai
        
        System.out.println("[createDoFromSo] DO " + hdr.getDocNo() + " dibuat DRAFT dari SO " + so.getDocNo() + 
                           " — stok akan dipotong saat Confirm");
    }
	
	    @Transactional
    public void createDoWithItems(LocalDate docDate, Long soId, String remarks,
                                   java.util.List<Long> itemIds,
                                   java.util.List<Long> uomIds,
                                   java.util.List<java.math.BigDecimal> qtys) {
        SalesOrderHdr so = soRepo.findById(soId)
            .orElseThrow(() -> new BusinessException("SO tidak ditemukan"));
        if (!"CONFIRMED".equalsIgnoreCase(so.getStatus())) {
            throw new BusinessException("Hanya SO berstatus CONFIRMED yang bisa dibuatkan DO.");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("Minimal 1 item harus ada.");
        }

        DeliveryOrderHdr hdr = new DeliveryOrderHdr();
        hdr.setTenantId(1L);
        hdr.setCompanyId(11L);
        hdr.setDocNo("DOC-" + System.currentTimeMillis());
        hdr.setDocDate(docDate != null ? docDate : LocalDate.now());
        hdr.setSoId(soId);
		hdr.setCustomerId(so.getCustomerId()); // <--- TAMBAHKAN INI
        hdr.setStatus("DRAFT");
        hdr.setRemarks(remarks);

        for (int i = 0; i < itemIds.size(); i++) {
            BigDecimal qty = qtys.get(i);
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            DeliveryOrderDtl d = new DeliveryOrderDtl();
            d.setItemId(itemIds.get(i));
            d.setUomId(uomIds.get(i));
            d.setQty(qty);
            hdr.addDetail(d);
        }

        doRepo.save(hdr);
        System.out.println("[createDoWithItems] DO " + hdr.getDocNo() + 
            " dibuat DRAFT dari SO " + so.getDocNo());
    }

    @Transactional
    public void createInvoiceFromDo(Long doId, LocalDate invDate, LocalDate dueDate) {
        DeliveryOrderHdr doHdr = doRepo.findById(doId).orElseThrow(() -> new BusinessException("DO tidak ditemukan"));
        SalesOrderHdr so = soRepo.findById(doHdr.getSoId()).orElseThrow();

        SalesInvoiceHdr hdr = new SalesInvoiceHdr();
        hdr.setTenantId(1L); hdr.setCompanyId(11L);
        hdr.setDocNo(numberRangeService.generate("SI", "SI"));
        hdr.setDocDate(invDate); hdr.setDueDate(dueDate);
        hdr.setDoId(doId); hdr.setCustomerId(so.getCustomerId()); hdr.setStatus("POSTED");
        hdr.setSalespersonId(so.getSalespersonId());

        // === COPY TAX INFO dari SO ===
        hdr.setTaxType(so.getTaxType());
        if ("EXCLUDE".equals(so.getTaxType()) && so.getTaxAmount() != null) {
            // EXCLUDE: total dari SO adalah subtotal, tambah PPN
            hdr.setTaxAmount(so.getTaxAmount());
            hdr.setTotalAmount(so.getTotalAmount().add(so.getTaxAmount()));
        } else if ("INCLUDE".equals(so.getTaxType()) && so.getTaxAmount() != null) {
            // INCLUDE: total dari SO sudah termasuk PPN
            hdr.setTaxAmount(so.getTaxAmount());
            hdr.setTotalAmount(so.getTotalAmount());
        } else {
            // NO_TAX: tidak ada PPN
            hdr.setTaxAmount(java.math.BigDecimal.ZERO);
            hdr.setTotalAmount(so.getTotalAmount());
        }
        // === END COPY TAX INFO ===

        BigDecimal total = BigDecimal.ZERO;
        
        // Ambil detail dari SO untuk mendapatkan harga
        for (SalesOrderDtl soDtl : so.getDetails()) {
            SalesInvoiceDtl invDtl = new SalesInvoiceDtl();
            invDtl.setItemId(soDtl.getItemId());
            invDtl.setQty(soDtl.getQty());
            invDtl.setPrice(soDtl.getPrice());
            BigDecimal lineTotal = soDtl.getQty().multiply(soDtl.getPrice());
            invDtl.setTotal(lineTotal);
            hdr.addDetail(invDtl);
            total = total.add(lineTotal);
        }
        // hdr.setTotalAmount(total); // superseded by tax calc above
        invRepo.save(hdr);

        

        doHdr.setStatus("INVOICED");
        doRepo.save(doHdr);
    }
    @Transactional
    public void postInvoice(Long invId) {
        SalesInvoiceHdr hdr = invRepo.findById(invId).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya Invoice DRAFT yang bisa di-posting.");

        SalesOrderHdr so = soRepo.findById(hdr.getDoId()).orElseThrow(); // Note: usually via DO->SO, but this is simplified

        journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getDocDate(), "SALES", "Auto-Journal Sales Invoice " + hdr.getDocNo(), java.util.List.of(
            new id.analisakoe.fin.service.JournalService.JournalLine("1201", hdr.getTotalAmount(), java.math.BigDecimal.ZERO, "Piutang Usaha"),
            new id.analisakoe.fin.service.JournalService.JournalLine("4101", java.math.BigDecimal.ZERO, hdr.getTotalAmount().subtract(so != null ? so.getTaxAmount() : java.math.BigDecimal.ZERO), "Pendapatan Penjualan"),
            new id.analisakoe.fin.service.JournalService.JournalLine("2201", java.math.BigDecimal.ZERO, so != null ? so.getTaxAmount() : java.math.BigDecimal.ZERO, "Hutang PPN")
        ));

        hdr.setStatus("POSTED");
        invRepo.save(hdr);
    }
    @Transactional
    public void voidInvoice(Long invId) {
        SalesInvoiceHdr hdr = invRepo.findById(invId).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if ("VOID".equals(hdr.getStatus())) throw new BusinessException("Invoice sudah VOID.");

        // Jika invoice sudah POSTED, buat Jurnal Pembalik
        if ("POSTED".equals(hdr.getStatus())) {
            journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getDocDate(), "REVERSE", "Jurnal Pembalik: " + hdr.getDocNo(), java.util.List.of(
                new id.analisakoe.fin.service.JournalService.JournalLine("4101", hdr.getTotalAmount(), java.math.BigDecimal.ZERO, "Pendapatan (Reversal)"),
                new id.analisakoe.fin.service.JournalService.JournalLine("1201", java.math.BigDecimal.ZERO, hdr.getTotalAmount(), "Piutang (Reversal)")
            ));
        }

        hdr.setStatus("VOID");
        invRepo.save(hdr);

        // Kembalikan status DO ke SHIPPED agar bisa di-invoice ulang (jika perlu)
                // Kembalikan status DO ke CONFIRMED (sebelum invoice dibuat)
        DeliveryOrderHdr doHdr = doRepo.findById(hdr.getDoId()).orElse(null);
        if (doHdr != null) {
            doHdr.setStatus("CONFIRMED");   // ← FIX: CONFIRMED, bukan SHIPPED
            doRepo.save(doHdr);
        }
    }

    @Transactional
    public void voidDo(Long doId) {
        DeliveryOrderHdr doHdr = doRepo.findById(doId).orElseThrow(() -> new BusinessException("DO tidak ditemukan"));
        if ("INVOICED".equals(doHdr.getStatus())) throw new BusinessException("DO sudah ditagih, batalkan Invoice terlebih dahulu.");
        
        doHdr.setStatus("VOID");
        doRepo.save(doHdr);
    }
    @Transactional
    public void updateInvoice(Long id, LocalDate dueDate, String remarks) {
        SalesInvoiceHdr hdr = invRepo.findById(id).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"DRAFT".equals(hdr.getStatus())) throw new BusinessException("Hanya Invoice DRAFT yang bisa diedit.");
        
        hdr.setDueDate(dueDate);
        hdr.setRemarks(remarks);
        invRepo.save(hdr);
    }

    @Transactional
    public void unpostInvoice(Long id) {
        SalesInvoiceHdr hdr = invRepo.findById(id).orElseThrow(() -> new BusinessException("Invoice tidak ditemukan"));
        if (!"POSTED".equals(hdr.getStatus())) throw new BusinessException("Hanya Invoice POSTED yang bisa di-Unpost.");

        // 1. Buat Jurnal Pembalik (Reversal)
        // Asumsi jurnal awal: Debit AR (Total), Kredit Pendapatan (DPP), Kredit PPN (Tax)
        BigDecimal dpp = hdr.getTotalAmount().subtract(hdr.getTaxAmount());
        journalService.createJournal(hdr.getTenantId(), hdr.getCompanyId(), hdr.getDocDate(), "REVERSE", "Unpost Sales Invoice " + hdr.getDocNo(), java.util.List.of(
            new id.analisakoe.fin.service.JournalService.JournalLine("4101", dpp, BigDecimal.ZERO, "Pendapatan (Reversal)"),
            new id.analisakoe.fin.service.JournalService.JournalLine("2201", hdr.getTaxAmount(), BigDecimal.ZERO, "PPN (Reversal)"),
            new id.analisakoe.fin.service.JournalService.JournalLine("1201", BigDecimal.ZERO, hdr.getTotalAmount(), "Piutang (Reversal)")
        ));

        // 2. Ubah Status ke DRAFT
        hdr.setStatus("DRAFT");
        invRepo.save(hdr);
    }
}