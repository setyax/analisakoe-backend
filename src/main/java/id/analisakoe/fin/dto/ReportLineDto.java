package id.analisakoe.fin.dto;

import java.math.BigDecimal;

public record ReportLineDto(
    String accountCode,
    String accountName,
    String accountType,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balance
) {}