package id.analisakoe.md.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CustomerRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 255) String name,
    String taxId,
    String currency,
    BigDecimal creditLimit,
    Short paymentTermDays,
    String status
) {}