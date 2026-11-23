package id.analisakoe.md.dto;

import id.analisakoe.md.entity.Warehouse;

public record WarehouseResponse(Long id, String code, String name, String address, String status) {
    public static WarehouseResponse from(Warehouse e) {
        return new WarehouseResponse(e.getId(), e.getCode(), e.getName(), e.getAddress(), e.getStatus());
    }
}