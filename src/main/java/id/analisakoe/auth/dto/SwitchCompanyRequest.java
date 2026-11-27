package id.analisakoe.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param companyId    company/cabang yang dipilih (wajib, harus milik user)
 * @param refreshToken opsional. Jika diisi, token lama akan di-rotate.
 */
public record SwitchCompanyRequest(
        @NotNull(message = "companyId wajib diisi") Long companyId,
        String refreshToken
) {}
