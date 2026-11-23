package id.analisakoe.inv.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class InventoryDtos {
    public record TransactionLine(Long itemId, Long uomId, BigDecimal qty) {}
    public record TransactionRequest(
        String docType, LocalDate docDate, Long warehouseId, String remarks, List<TransactionLine> lines
    ) {}
    public record BalanceResponse(
        Long warehouseId, String warehouseName, Long itemId, String itemCode, String itemName, BigDecimal qtyOnHand
    ) {}
    public record TransactionResponse(
        Long id, String docNo, String docType, LocalDate docDate, String warehouseName, String status
    ) {}
}