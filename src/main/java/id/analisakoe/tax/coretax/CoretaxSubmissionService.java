package id.analisakoe.tax.coretax;

import id.analisakoe.sal.entity.SalesInvoiceHdr;
import id.analisakoe.sal.repository.SalesInvoiceRepository;
import id.analisakoe.sys.entity.CoretaxLog;
import id.analisakoe.sys.repository.CoretaxLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoretaxSubmissionService {
    private final SalesInvoiceRepository invRepo;
    private final CoretaxXmlGeneratorService xmlGen;
    private final PjapApiClient apiClient;
    private final CoretaxLogRepository logRepo;

    public CoretaxSubmissionService(SalesInvoiceRepository invRepo, CoretaxXmlGeneratorService xmlGen, PjapApiClient apiClient, CoretaxLogRepository logRepo) {
        this.invRepo = invRepo; this.xmlGen = xmlGen; this.apiClient = apiClient; this.logRepo = logRepo;
    }

    @Transactional
    public void submit(Long invoiceId) {
        SalesInvoiceHdr inv = invRepo.findById(invoiceId).orElseThrow();
        if (!"POSTED".equals(inv.getStatus())) throw new RuntimeException("Invoice harus POSTED sebelum dikirim ke Coretax.");

        String xml = xmlGen.generate(inv);
        CoretaxLog log = new CoretaxLog();
        log.setInvoiceId(invoiceId);
        log.setRequestPayload(xml);

        try {
            String response = apiClient.submitEFaktur(xml);
            log.setResponsePayload(response);
            log.setStatus("SUCCESS");
            
            // Asumsi: Response berisi NSFP jika sukses. Di production, parsing response JSON/XML dibutuhkan di sini.
            inv.setCoretaxStatus("SUCCESS");
            inv.setNsfp("010.000-25." + System.currentTimeMillis()); // Dummy NSFP untuk simulasi
            inv.setCoretaxMessage("Faktur berhasil dikirim ke Coretax.");
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setResponsePayload(e.getMessage());
            inv.setCoretaxStatus("FAILED");
            inv.setCoretaxMessage(e.getMessage());
        }

        logRepo.save(log);
        invRepo.save(inv);
    }
}