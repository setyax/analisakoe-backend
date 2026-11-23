package id.analisakoe.md.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ItemRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    String description,
    Long categoryId,
    @NotNull Long baseUomId,
    String itemType,
    Boolean trackLot,
    Boolean trackSerial,
    String barcode,
    String status,
    String purchasingGroup,
    Integer leadTimeDays,
    String priceControl,
    String valuationClass,
    BigDecimal standardPrice,
    String djpGoodsCode
) {}