package id.analisakoe.md.dto;

import id.analisakoe.md.entity.Item;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record ItemResponse(
    Long id, String code, String name, String description,
    Long categoryId, Long baseUomId, String itemType,
    Boolean trackLot, Boolean trackSerial, String barcode,
    String status, OffsetDateTime createdAt, OffsetDateTime updatedAt,
    String purchasingGroup, Integer leadTimeDays, String priceControl, String valuationClass, BigDecimal standardPrice,
    String djpGoodsCode
) {
    public static ItemResponse from(Item e) {
        return new ItemResponse(
            e.getId(), e.getCode(), e.getName(), e.getDescription(),
            e.getCategoryId(), e.getBaseUomId(), e.getItemType(),
            e.getTrackLot(), e.getTrackSerial(), e.getBarcode(),
            e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(),
            e.getPurchasingGroup(), e.getLeadTimeDays(), e.getPriceControl(), e.getValuationClass(), e.getStandardPrice(),
            e.getDjpGoodsCode()
        );
    }
}