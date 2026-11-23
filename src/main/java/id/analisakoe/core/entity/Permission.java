package id.analisakoe.core.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permission", schema = "core")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 30)
    private String module;

    private String description;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getModule() { return module; }
    public String getDescription() { return description; }
}