package id.analisakoe.md.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 255) String name,
    String taxId,
    String currency,
    Short paymentTermDays,
    String status
) {}