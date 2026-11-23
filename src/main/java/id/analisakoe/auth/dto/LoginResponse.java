package id.analisakoe.auth.dto;

import java.util.Set;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    long refreshExpiresInSeconds,
    UserInfo user
) {
    public record UserInfo(
        Long id,
        Long tenantId,
        String username,
        String email,
        String fullName,
        Set<String> roles,
        Set<String> permissions
    ) {}
}