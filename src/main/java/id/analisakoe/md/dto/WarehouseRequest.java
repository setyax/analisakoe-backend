package id.analisakoe.md.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 200) String name,
    String address,
    String status
) {}