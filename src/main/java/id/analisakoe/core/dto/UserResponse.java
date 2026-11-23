package id.analisakoe.core.dto;

import id.analisakoe.core.entity.AppUser;
import java.time.OffsetDateTime;
import java.util.List;

public record UserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    String status,
    Boolean mustChangePwd,
    OffsetDateTime lastLogin,
    List<String> roles,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(
            u.getId(),
            u.getUsername(),
            u.getEmail(),
            u.getFullName(),
            u.getStatus(),
            u.getMustChangePwd(),
            u.getLastLogin(),
            u.getRoles().stream().map(r -> r.getCode()).sorted().toList(),
            u.getCreatedAt(),
            u.getUpdatedAt()
        );
    }
}