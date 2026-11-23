package id.analisakoe.md.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UomRequest(
    @NotBlank(message = "Kode wajib") @Size(max = 20) String code,
    @NotBlank(message = "Nama wajib") @Size(max = 100) String name
) {}