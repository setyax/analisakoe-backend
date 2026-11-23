package id.analisakoe.md.dto;

import id.analisakoe.md.entity.Supplier;
import java.time.OffsetDateTime;

public record SupplierResponse(
    Long id, String code, String name, String taxId, String currency,
    Short paymentTermDays, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    public static SupplierResponse from(Supplier e) {
        return new SupplierResponse(
            e.getId(), e.getCode(), e.getName(), e.getTaxId(), e.getCurrency(),
            e.getPaymentTermDays(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}