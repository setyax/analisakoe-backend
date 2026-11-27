package id.analisakoe.auth.dto;

public record SwitchCompanyResponse(
        Long companyId,
        String companyName,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshExpiresInSeconds
) {}
