package id.analisakoe.inv.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.inv.dto.InventoryDtos.TransactionLine;
import id.analisakoe.inv.dto.InventoryDtos.TransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryAdjustmentService {
    private final InventoryService invService;
    private final JournalService journalService;

    public InventoryAdjustmentService(InventoryService invService, JournalService journalService) {
        this.invService = invService; this.journalService = journalService;
    }

    @Transactional
    public void createAdjustment(Long tenantId, Long companyId, String type, LocalDate date, Long warehouseId, String remarks, List<LineReq> lines) {
        if(lines == null || lines.isEmpty()) throw new BusinessException("Minimal 1 item di-adjust.");

        // 1. Update Stok via InventoryService (IN atau OUT)
        List<TransactionLine> invLines = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for(var l : lines) {
            invLines.add(new TransactionLine(l.itemId(), l.uomId(), l.qty()));
            // Asumsi nilai penyesuaian menggunakan harga standar atau modal rata-rata
            totalValue = totalValue.add(l.qty().multiply(l.price() != null ? l.price() : BigDecimal.ZERO));
        }

        String docType = "DB".equals(type) ? "IN" : "OUT";
        invService.createTransaction(new TransactionRequest(docType, date, warehouseId, "Adj " + type + ": " + remarks, invLines));

        // 2. Buat Jurnal
        if ("DB".equals(type)) {
            // Adjustment DB (Tambah Stok)
            journalService.createJournal(tenantId, companyId, date, "INV_ADJ_DB", "Inventory Adjustment DB: " + remarks, List.of(
                new JournalService.JournalLine("1301", totalValue, BigDecimal.ZERO, "Persediaan"),
                new JournalService.JournalLine("8103", BigDecimal.ZERO, totalValue, "Pendapatan Selisih Persediaan")
            ));
        } else {
            // Adjustment CR (Kurangi Stok)
            journalService.createJournal(tenantId, companyId, date, "INV_ADJ_CR", "Inventory Adjustment CR: " + remarks, List.of(
                new JournalService.JournalLine("6197", totalValue, BigDecimal.ZERO, "Beban Selisih Persediaan"),
                new JournalService.JournalLine("1301", BigDecimal.ZERO, totalValue, "Persediaan")
            ));
        }
    }

    public record LineReq(Long itemId, Long uomId, BigDecimal qty, BigDecimal price) {}
}