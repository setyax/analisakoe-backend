package id.analisakoe.core.dto;

import id.analisakoe.core.entity.Permission;

public record PermissionResponse(Long id, String code, String module, String description) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getCode(), p.getModule(), p.getDescription());
    }
}