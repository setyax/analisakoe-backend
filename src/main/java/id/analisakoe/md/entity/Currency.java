package id.analisakoe.md.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "currency", schema = "md")
public class Currency {
    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 10)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private Short decimalPlaces;

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public Short getDecimalPlaces() { return decimalPlaces; }
}