package id.analisakoe.hr.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name = "attendance", schema = "hr")
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "employee_id") private Long employeeId;
    @Column(name = "att_date") private LocalDate attDate;
    private String status;
    @Column(name = "late_minutes") private Integer lateMinutes;
    private String remarks;

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; } public void setEmployeeId(Long v) { this.employeeId = v; }
    public LocalDate getAttDate() { return attDate; } public void setAttDate(LocalDate v) { this.attDate = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public Integer getLateMinutes() { return lateMinutes; } public void setLateMinutes(Integer v) { this.lateMinutes = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
}