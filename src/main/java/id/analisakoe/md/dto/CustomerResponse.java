package id.analisakoe.md.dto;

import id.analisakoe.md.entity.Customer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerResponse(
    Long id, String code, String name, String taxId, String currency,
    BigDecimal creditLimit, Short paymentTermDays, String status,
    OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    public static CustomerResponse from(Customer e) {
        return new CustomerResponse(
            e.getId(), e.getCode(), e.getName(), e.getTaxId(), e.getCurrency(),
            e.getCreditLimit(), e.getPaymentTermDays(), e.getStatus(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}