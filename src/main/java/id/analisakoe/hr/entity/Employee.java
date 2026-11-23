package id.analisakoe.hr.entity;
import jakarta.persistence.*;
@Entity @Table(name = "employee", schema = "hr")
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    private String nip;
    private String name;
    private String department;
    private String position;
    private String status;
    
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getNip() { return nip; } public void setNip(String v) { this.nip = v; }
    public String getName() { return name; } public void setName(String v) { this.name = v; }
    public String getDepartment() { return department; } public void setDepartment(String v) { this.department = v; }
    public String getPosition() { return position; } public void setPosition(String v) { this.position = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}