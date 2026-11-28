package id.analisakoe.fin.service;

import id.analisakoe.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.entity.Note;
import id.analisakoe.fin.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class NoteService {

    @Autowired
    private ContextService context;
    private final NoteRepository repo;
    private final JournalService journalService;

    public NoteService(NoteRepository repo, JournalService journalService) {
        this.repo = repo; this.journalService = journalService;
    }

    @Transactional
    public void create(LocalDate date, String type, Long partnerId, BigDecimal amount, String remarks) {
        Note note = new Note();
        note.setTenantId(context.tenantId()); note.setCompanyId(context.companyId());
        note.setDocNo(type + "-" + System.currentTimeMillis());
        note.setDocDate(date); note.setNoteType(type);
        note.setPartnerId(partnerId);
        note.setTotalAmount(amount); note.setTaxAmount(BigDecimal.ZERO); // Asumsi tanpa pajak untuk simplifikasi
        note.setRemarks(remarks); note.setStatus("DRAFT");
        repo.save(note);
    }

    @Transactional
    public void post(Long id) {
        Note note = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Note tidak ditemukan"));
        if (!"DRAFT".equals(note.getStatus())) throw new BusinessException("Sudah diposting.");

        BigDecimal amount = note.getTotalAmount();

        switch (note.getNoteType()) {
            case "AR_DN":
                // Menambah Piutang Customer (Debit AR, Kredit Pendapatan Lain)
                journalService.createJournal(note.getTenantId(), note.getCompanyId(), note.getDocDate(), "AR_DN", "AR Debit Note " + note.getDocNo(), List.of(
                    new JournalService.JournalLine("1201", amount, BigDecimal.ZERO, "Piutang Usaha"),
                    new JournalService.JournalLine("4103", BigDecimal.ZERO, amount, "Pendapatan Lain-lain")
                ));
                break;
            case "AR_CN":
                // Mengurangi Piutang Customer (Debit Diskon/Retur, Kredit AR)
                journalService.createJournal(note.getTenantId(), note.getCompanyId(), note.getDocDate(), "AR_CN", "AR Credit Note " + note.getDocNo(), List.of(
                    new JournalService.JournalLine("4102", amount, BigDecimal.ZERO, "Diskon Penjualan"),
                    new JournalService.JournalLine("1201", BigDecimal.ZERO, amount, "Piutang Usaha")
                ));
                break;
            case "AP_DN":
                // Mengurangi Hutang Supplier (Debit AP, Kredit Beban Lain/Inventory)
                journalService.createJournal(note.getTenantId(), note.getCompanyId(), note.getDocDate(), "AP_DN", "AP Debit Note " + note.getDocNo(), List.of(
                    new JournalService.JournalLine("2101", amount, BigDecimal.ZERO, "Hutang Usaha"),
                    new JournalService.JournalLine("1301", BigDecimal.ZERO, amount, "Pengurangan Persediaan/Beban")
                ));
                break;
            case "AP_CN":
                // Menambah Hutang Supplier (Debit Beban Lain, Kredit AP)
                journalService.createJournal(note.getTenantId(), note.getCompanyId(), note.getDocDate(), "AP_CN", "AP Credit Note " + note.getDocNo(), List.of(
                    new JournalService.JournalLine("6198", amount, BigDecimal.ZERO, "Beban Lain-lain"),
                    new JournalService.JournalLine("2101", BigDecimal.ZERO, amount, "Hutang Usaha")
                ));
                break;
        }

        note.setStatus("POSTED");
        repo.save(note);
    }

    /** Update note — hanya yang masih DRAFT. */
    @Transactional
    public void update(Long id, LocalDate date, String type, Long partnerId, BigDecimal amount, String remarks) {
        Note note = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Note tidak ditemukan"));
        if (!"DRAFT".equals(note.getStatus()))
            throw new BusinessException("Hanya Note berstatus DRAFT yang dapat diedit. Unpost dulu jika sudah diposting.");
        note.setDocDate(date);
        note.setNoteType(type);
        note.setPartnerId(partnerId);
        note.setTotalAmount(amount);
        note.setRemarks(remarks);
        repo.save(note);
    }

    /**
     * Unpost: kembalikan ke DRAFT dengan menghapus jurnal yang dibuat saat posting
n     * (jurnal dicari berdasarkan type + remarks yang memuat docNo note).
     */
    @Transactional
    public void unpost(Long id) {
        Note note = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Note tidak ditemukan"));
        if (!"POSTED".equals(note.getStatus()))
            throw new BusinessException("Hanya Note berstatus POSTED yang dapat di-unpost.");

        String jrnlType;
        String jrnlRemarks;
        switch (note.getNoteType()) {
            case "AR_DN": jrnlType = "AR_DN"; jrnlRemarks = "AR Debit Note " + note.getDocNo(); break;
            case "AR_CN": jrnlType = "AR_CN"; jrnlRemarks = "AR Credit Note " + note.getDocNo(); break;
            case "AP_DN": jrnlType = "AP_DN"; jrnlRemarks = "AP Debit Note " + note.getDocNo(); break;
            case "AP_CN": jrnlType = "AP_CN"; jrnlRemarks = "AP Credit Note " + note.getDocNo(); break;
            default: throw new BusinessException("Tipe note tidak dikenal: " + note.getNoteType());
        }

        int deleted = journalService.deleteByTypeAndRemarks(note.getTenantId(), note.getCompanyId(), jrnlType, jrnlRemarks);
        if (deleted == 0)
            throw new BusinessException("Jurnal posting tidak ditemukan. Note tidak diubah.");

        note.setStatus("DRAFT");
        repo.save(note);
    }

    /** Hapus note — hanya yang masih DRAFT. */
    @Transactional
    public void delete(Long id) {
        Note note = repo.findByIdAndTenantIdAndCompanyId(id, context.tenantId(), context.companyId()).orElseThrow(() -> new BusinessException("Note tidak ditemukan"));
        if (!"DRAFT".equals(note.getStatus()))
            throw new BusinessException("Hanya Note berstatus DRAFT yang dapat dihapus. Unpost dulu jika sudah diposting.");
        repo.delete(note);
    }
}