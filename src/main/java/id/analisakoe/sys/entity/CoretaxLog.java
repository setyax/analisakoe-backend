package id.analisakoe.sys.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "coretax_log", schema = "sys")
public class CoretaxLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "invoice_id") private Long invoiceId;
    @Column(name = "request_payload", columnDefinition = "TEXT") private String requestPayload;
    @Column(name = "response_payload", columnDefinition = "TEXT") private String responsePayload;
    private String status;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getInvoiceId() { return invoiceId; } public void setInvoiceId(Long v) { this.invoiceId = v; }
    public String getRequestPayload() { return requestPayload; } public void setRequestPayload(String v) { this.requestPayload = v; }
    public String getResponsePayload() { return responsePayload; } public void setResponsePayload(String v) { this.responsePayload = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}