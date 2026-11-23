package id.analisakoe.fin.dto;

import java.math.BigDecimal;

public record BankBalanceDto(
    Long bankId,
    String bankName,
    String accountNo,
    BigDecimal totalIn,
    BigDecimal totalOut,
    BigDecimal balance
) {}