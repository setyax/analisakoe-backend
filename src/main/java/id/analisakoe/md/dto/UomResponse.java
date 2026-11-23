package id.analisakoe.md.dto;

import id.analisakoe.md.entity.Uom;

public record UomResponse(Long id, String code, String name) {
    public static UomResponse from(Uom e) {
        return new UomResponse(e.getId(), e.getCode(), e.getName());
    }
}