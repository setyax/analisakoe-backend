package id.analisakoe.fin.dto;

import id.analisakoe.fin.entity.ChartOfAccount;

public record ChartOfAccountResponse(
    Long id, String code, String name, String accountType,
    Long parentId, Boolean isHeader, String currency, String status
) {
    public static ChartOfAccountResponse from(ChartOfAccount e) {
        return new ChartOfAccountResponse(
            e.getId(), e.getCode(), e.getName(), e.getAccountType(),
            e.getParentId(), e.getIsHeader(), e.getCurrency(), e.getStatus()
        );
    }
}