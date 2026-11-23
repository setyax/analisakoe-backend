package id.analisakoe.core.dto;

import id.analisakoe.core.entity.Role;
import java.util.List;

public record RoleResponse(
    Long id,
    String code,
    String name,
    String description,
    List<String> permissions
) {
    public static RoleResponse from(Role r) {
        return new RoleResponse(
            r.getId(),
            r.getCode(),
            r.getName(),
            r.getDescription(),
            r.getPermissions().stream().map(p -> p.getCode()).sorted().toList()
        );
    }
}