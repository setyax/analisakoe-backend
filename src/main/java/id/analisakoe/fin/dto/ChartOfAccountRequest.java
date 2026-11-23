package id.analisakoe.fin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChartOfAccountRequest(
    @NotBlank @Size(max = 30) String code,
    @NotBlank @Size(max = 200) String name,
    @NotBlank String accountType,
    Long parentId,
    Boolean isHeader,
    String currency,
    String status
) {}