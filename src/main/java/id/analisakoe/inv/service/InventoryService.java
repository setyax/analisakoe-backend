package id.analisakoe.inv.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.context.ContextService;
import id.analisakoe.core.service.StockValuationService;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.inv.dto.InventoryDtos.*;
import id.analisakoe.inv.entity.InvStockBalance;
import id.analisakoe.inv.entity.InvTransactionDetail;
import id.analisakoe.inv.entity.InvTransactionHeader;
import id.analisakoe.inv.repository.InvStockBalanceRepository;
import id.analisakoe.inv.repository.InvTransactionRepository;
import id.analisakoe.md.repository.ItemRepository;
import id.analisakoe.md.repository.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    @Autowired
    private ContextService context;

    private final InvTransactionRepository trxRepo;
    private final InvStockBalanceRepository balanceRepo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository whRepo;
    private final StockValuationService stockValuationService;

    public InventoryService(InvTransactionRepository trxRepo, InvStockBalanceRepository balanceRepo,
                            ItemRepository itemRepo, WarehouseRepository whRepo,
                            StockValuationService stockValuationService) {
        this.trxRepo = trxRepo;
        this.balanceRepo = balanceRepo;
        this.itemRepo = itemRepo;
        this.whRepo = whRepo;
        this.stockValuationService = stockValuationService;
    }

    private Long tenant() { return context.tenantId(); }
    private Long company() { return context.companyId(); }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getBalances() {
        return balanceRepo.findAllWithStock().stream().map(b -> {
            var item = itemRepo.findById(b.getItemId()).orElse(null);
            var wh = whRepo.findById(b.getWarehouseId()).orElse(null);
            return new BalanceResponse(
                b.getWarehouseId(), wh != null ? wh.getName() : "Unknown",
                b.getItemId(), item != null ? item.getCode() : "?", item != null ? item.getName() : "?",
                b.getQtyOnHand()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions() {
        return trxRepo.findByTenantIdOrderByIdDesc(tenant(), PageRequest.of(0, 50)).getContent().stream().map(h -> {
            var wh = whRepo.findById(h.getWarehouseId()).orElse(null);
            return new TransactionResponse(
                h.getId(), h.getDocNo(), h.getDocType(), h.getDocDate(),
                wh != null ? wh.getName() : "Unknown", h.getStatus()
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public void createTransaction(TransactionRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new BusinessException("Minimal harus ada 1 barang dalam transaksi");
        }

        InvTransactionHeader header = new InvTransactionHeader();
        header.setTenantId(tenant());
        header.setCompanyId(company());
        header.setDocType(req.docType());
        header.setDocDate(req.docDate());
        header.setWarehouseId(req.warehouseId());
        header.setRemarks(req.remarks());
        header.setDocNo("TRX-" + System.currentTimeMillis()); // Simple auto-generate

        for (var line : req.lines()) {
            if (line.qty() == null || line.qty().compareTo(BigDecimal.ZERO) <= 0) continue;
            
            InvTransactionDetail dtl = new InvTransactionDetail();
            dtl.setItemId(line.itemId());
            dtl.setUomId(line.uomId());
            dtl.setQty(line.qty());
            header.addDetail(dtl);

            // Konversi qty dari satuan baris ke satuan dasar item
            BigDecimal qtyBase = stockValuationService.convertLineToBase(line.itemId(), line.uomId(), line.qty(), BigDecimal.ZERO)[0];
            if (qtyBase.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Update Stock Balance
            InvStockBalance balance = balanceRepo.findByWarehouseAndItem(req.warehouseId(), line.itemId())
                .orElseGet(() -> {
                    InvStockBalance b = new InvStockBalance();
                    b.setTenantId(tenant());
                    b.setWarehouseId(req.warehouseId());
                    b.setItemId(line.itemId());
                    b.setQtyOnHand(BigDecimal.ZERO);
                    return b;
                });

            if ("IN".equals(req.docType())) {
                balance.setQtyOnHand(balance.getQtyOnHand().add(qtyBase));
            } else if ("OUT".equals(req.docType())) {
                balance.setQtyOnHand(balance.getQtyOnHand().subtract(qtyBase));
            }
            balanceRepo.save(balance);
        }

        trxRepo.save(header);
    }
}