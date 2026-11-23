package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/ui/finance/batch-process")
public class UiBatchProcessController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiBatchProcessController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String view(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Batch Process");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        return "ui/finance/batch_process";
    }

    @PostMapping("/run")
    @Transactional
    public String runBatchProcess(HttpSession session, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
        final Long compId = (user != null && user.getActiveCompanyId() != null) ? user.getActiveCompanyId() : 1L;
        this.batchTenantId = tenantId;
        this.batchCompId = compId;
        try {
            int countAR = 0, countAP = 0, countIN = 0, countOUT = 0, countFA = 0, countCA = 0;

            Long accPiutang    = getCoaIdByCode("1201");
            Long accPersediaan = getCoaIdByCode("1301");
            Long accHutang     = getCoaIdByCode("2101");
            Long accPPN        = getCoaIdByCode("2201");
            Long accPendapatan = getCoaIdByCode("4101");
            Long accHPP        = getCoaIdByCode("5000");
            Long accBebanDep   = getCoaIdByCode("6199");
            Long accAkumDep    = getCoaIdByCode("1491");
            Long accBankCA     = getCoaIdByCode("1102");

            // 1. AR
            List<Object[]> arList = em.createNativeQuery(
                "SELECT id, total_amount, tax_amount FROM sal.sales_invoice_hdr WHERE UPPER(TRIM(status)) = 'CONFIRMED'").getResultList();
            for (Object[] row : arList) {
                Long id = ((Number) row[0]).longValue();
                BigDecimal total = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                BigDecimal tax = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                BigDecimal dpp = total.subtract(tax);
                if (total.compareTo(BigDecimal.ZERO) <= 0) continue;

                Long hdrId = createJournalHeader("JRN-AR-" + id, "SALES", "Auto-Journal SI #" + id, "SALES_INVOICE", id);
                insertJournalLine(hdrId, accPiutang, total, BigDecimal.ZERO, "Piutang - SI #" + id);
                insertJournalLine(hdrId, accPendapatan, BigDecimal.ZERO, dpp, "Pendapatan - SI #" + id);
                if (tax.compareTo(BigDecimal.ZERO) > 0 && accPPN != null) insertJournalLine(hdrId, accPPN, BigDecimal.ZERO, tax, "PPN - SI #" + id);
                em.createNativeQuery("UPDATE sal.sales_invoice_hdr SET status = 'POSTED' WHERE id = :id").setParameter("id", id).executeUpdate();
                countAR++;
            }

            // 2. AP
            List<Object[]> apList = em.createNativeQuery(
                "SELECT id, total_amount FROM pur.purchase_invoice_hdr WHERE UPPER(TRIM(status)) = 'CONFIRMED'").getResultList();
            for (Object[] row : apList) {
                Long id = ((Number) row[0]).longValue();
                BigDecimal total = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                if (total.compareTo(BigDecimal.ZERO) <= 0) continue;
                Long hdrId = createJournalHeader("JRN-AP-" + id, "PURCHASE", "Auto-Journal PI #" + id, "PURCHASE_INVOICE", id);
                insertJournalLine(hdrId, accPersediaan, total, BigDecimal.ZERO, "Persediaan - PI #" + id);
                insertJournalLine(hdrId, accHutang, BigDecimal.ZERO, total, "Hutang - PI #" + id);
                em.createNativeQuery("UPDATE pur.purchase_invoice_hdr SET status = 'POSTED' WHERE id = :id").setParameter("id", id).executeUpdate();
                countAP++;
            }

            // 3. IN
            List<?> inList = em.createNativeQuery(
                "SELECT id FROM inv.transaction_header WHERE UPPER(TRIM(status)) = 'CONFIRMED' AND doc_type = 'IN'").getResultList();
            for (Object row : inList) {
                Long id = ((Number) row).longValue();
                em.createNativeQuery("UPDATE inv.transaction_header SET status = 'POSTED' WHERE id = :id").setParameter("id", id).executeUpdate();
                countIN++;
            }

                        // 4. OUT
            List<?> outList = em.createNativeQuery(
                "SELECT id FROM inv.transaction_header WHERE UPPER(TRIM(status)) = 'CONFIRMED' AND doc_type = 'OUT'").getResultList();
            for (Object row : outList) {
                Long id = ((Number) row).longValue();
                
                // Ambil total cost dari detail
                BigDecimal totalCost = BigDecimal.ZERO;
                List<Object[]> details = em.createNativeQuery(
                    "SELECT d.qty, COALESCE(i.standard_price, 0) " +
                    "FROM inv.transaction_detail d " +
                    "JOIN md.item i ON i.id = d.item_id " +
                    "WHERE d.header_id = :hid")
                    .setParameter("hid", id).getResultList();
                
                for (Object[] d : details) {
                    BigDecimal qty = d[0] != null ? new BigDecimal(d[0].toString()) : BigDecimal.ZERO;
                    BigDecimal price = d[1] != null ? new BigDecimal(d[1].toString()) : BigDecimal.ZERO;
                    totalCost = totalCost.add(qty.multiply(price));
                }
                
                if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                    Long hdrId = createJournalHeader("JRN-HPP-" + id, "INVENTORY", 
                        "Auto-Journal HPP from OUT #" + id, "INVENTORY_OUT", id);
                    insertJournalLine(hdrId, accHPP, totalCost, BigDecimal.ZERO, "HPP - OUT #" + id);
                    insertJournalLine(hdrId, accPersediaan, BigDecimal.ZERO, totalCost, "Persediaan - OUT #" + id);
                    countOUT++;
                }
                
                em.createNativeQuery("UPDATE inv.transaction_header SET status = 'POSTED' WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            }

            // 5. Fixed Asset (FA) - PAKAI UPPER(TRIM())
            if (accBebanDep != null && accAkumDep != null) {
                List<Object[]> faList = em.createNativeQuery(
                    "SELECT id, asset_id, depreciation_amount FROM fin.asset_depreciation_log WHERE UPPER(TRIM(status)) = 'CONFIRMED'").getResultList();
                for (Object[] row : faList) {
                    Long logId = ((Number) row[0]).longValue();
                    Long assetId = ((Number) row[1]).longValue();
                    BigDecimal depAmt = (BigDecimal) row[2];

                    Long hdrId = createJournalHeader("JRN-FA-" + logId, "DEPRECIATION", "Auto-Journal Depresiasi Asset #" + assetId, "FA_DEPRECIATION", logId);
                    insertJournalLine(hdrId, accBebanDep, depAmt, BigDecimal.ZERO, "Beban Depresiasi - Asset #" + assetId);
                    insertJournalLine(hdrId, accAkumDep, BigDecimal.ZERO, depAmt, "Akumulasi Depresiasi - Asset #" + assetId);

                    em.createNativeQuery("UPDATE fin.asset_master SET accumulated_depreciation = accumulated_depreciation + :amt, book_value = book_value - :amt WHERE id = :assetId")
                        .setParameter("amt", depAmt).setParameter("assetId", assetId).executeUpdate();

                    em.createNativeQuery("UPDATE fin.asset_depreciation_log SET status = 'POSTED' WHERE id = :logId").setParameter("logId", logId).executeUpdate();
                    countFA++;
                }
            }

            // 6. Kas/Bank (CA)
            if (accBankCA != null && accPiutang != null && accHutang != null) {
                List<Object[]> caList = em.createNativeQuery(
                    "SELECT id, amount, trx_type, remarks FROM fin.cash_bank_trx WHERE UPPER(TRIM(status)) = 'CONFIRMED'").getResultList();
                for (Object[] row : caList) {
                    Long id = ((Number) row[0]).longValue();
                    BigDecimal amt = (BigDecimal) row[1];
                    String type = (String) row[2];
                    String rem = (String) row[3];

                    Long hdrId = createJournalHeader("JRN-CA-" + id, "CASH", "Auto-Journal Cash/Bank", "CASH_BANK", id);
                    if ("IN".equals(type)) {
                        insertJournalLine(hdrId, accBankCA, amt, BigDecimal.ZERO, rem);
                        insertJournalLine(hdrId, accPiutang, BigDecimal.ZERO, amt, "Pelunasan Piutang");
                    } else {
                        insertJournalLine(hdrId, accHutang, amt, BigDecimal.ZERO, "Pelunasan Hutang");
                        insertJournalLine(hdrId, accBankCA, BigDecimal.ZERO, amt, rem);
                    }
                    em.createNativeQuery("UPDATE fin.cash_bank_trx SET status = 'POSTED' WHERE id = :id").setParameter("id", id).executeUpdate();
                    countCA++;
                }
            }

            String msg = String.format("Batch POST Selesai! AR: %d, AP: %d, IN: %d, OUT: %d, FA: %d, CA: %d", countAR, countAP, countIN, countOUT, countFA, countCA);
            ra.addFlashAttribute("success", msg);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Batch POST GAGAL: " + e.getMessage());
        }
        return "redirect:/ui/finance/batch-process";
    }

    private volatile Long batchTenantId = 1L;
    private volatile Long batchCompId = 1L;

    private Long createJournalHeader(String docPrefix, String jType, String remarks, String refType, Long refId) {
        String docNo = docPrefix + "-" + System.currentTimeMillis();
        return ((Number) em.createNativeQuery(
            "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
            "VALUES (:tenantId, :compId, :docNo, CURRENT_DATE, :jt, :rem, 'POSTED', :rt, :rid) RETURNING id")
            .setParameter("tenantId", batchTenantId).setParameter("compId", batchCompId)
            .setParameter("docNo", docNo).setParameter("jt", jType).setParameter("rem", remarks).setParameter("rt", refType).setParameter("rid", refId)
            .getSingleResult()).longValue();
    }

    private void insertJournalLine(Long hdrId, Long accId, BigDecimal dr, BigDecimal cr, String desc) {
        em.createNativeQuery("INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) VALUES (:hdr, :acc, :dr, :cr, :desc)")
            .setParameter("hdr", hdrId).setParameter("acc", accId).setParameter("dr", dr).setParameter("cr", cr).setParameter("desc", desc).executeUpdate();
    }

    private Long getCoaIdByCode(String code) {
        try { return ((Number) em.createNativeQuery("SELECT id FROM fin.chart_of_account WHERE code = :code").setParameter("code", code).getSingleResult()).longValue(); }
        catch (Exception e) { return null; }
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, RedirectAttributes ra) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        ra.addFlashAttribute("error", "Error: " + root.getMessage());
        return "redirect:/ui/finance/batch-process";
    }
	
	    @PostMapping("/unpost")
    @Transactional
    public String unpostBatchProcess(RedirectAttributes ra) {
        // 1. Hitung jurnal OTOMATIS POSTED (ref_type IS NOT NULL)
        Long totalAuto = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM fin.journal_hdr " +
            "WHERE status = 'POSTED' AND ref_type IS NOT NULL")
            .getSingleResult()).longValue();

        // 2. Hitung jurnal MANUAL POSTED (ref_type IS NULL)
        Long totalManual = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM fin.journal_hdr " +
            "WHERE status = 'POSTED' AND ref_type IS NULL")
            .getSingleResult()).longValue();

        if (totalAuto == 0) {
            String msg = "Tidak ada jurnal otomatis berstatus POSTED untuk di-unpost.";
            if (totalManual > 0) {
                msg += " (" + totalManual + " jurnal manual dipertahankan, tidak di-void)";
            }
            ra.addFlashAttribute("error", msg);
            return "redirect:/ui/finance/batch-process";
        }

        // 3. Void HANYA jurnal otomatis (ref_type IS NOT NULL)
        int voided = em.createNativeQuery(
            "UPDATE fin.journal_hdr SET status = 'VOID' " +
            "WHERE status = 'POSTED' AND ref_type IS NOT NULL")
            .executeUpdate();

        // 4. Reset status transaksi agar bisa di-generate ulang
        em.createNativeQuery(
            "UPDATE sal.sales_invoice_hdr SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();
        em.createNativeQuery(
            "UPDATE pur.purchase_invoice_hdr SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();
        em.createNativeQuery(
            "UPDATE fin.cash_bank_trx SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();
        em.createNativeQuery(
            "UPDATE inv.transaction_header SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();

        // 5. Notifikasi lengkap
        String msg = "Berhasil UNPOST " + voided + " jurnal otomatis.";
        if (totalManual > 0) {
            msg += " " + totalManual + " jurnal manual TETAP AKTIF (tidak di-void).";
        }
        ra.addFlashAttribute("success", msg);
        return "redirect:/ui/finance/batch-process";
    }
	
	    // ============================================================
    // DELETE ALL JOURNALS (HATI-HATI! Hanya untuk reset testing)
    // ============================================================
        @PostMapping("/delete-journals")
    @Transactional
    public String deleteAllJournals(RedirectAttributes ra) {
        // 1. Hitung jurnal otomatis vs manual
        Long totalAuto = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM fin.journal_hdr WHERE ref_type IS NOT NULL")
            .getSingleResult()).longValue();

        Long totalManual = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM fin.journal_hdr WHERE ref_type IS NULL")
            .getSingleResult()).longValue();

        if (totalAuto == 0) {
            String msg = "Tidak ada jurnal otomatis untuk dihapus.";
            if (totalManual > 0) {
                msg += " (" + totalManual + " jurnal manual dipertahankan)";
            }
            ra.addFlashAttribute("error", msg);
            return "redirect:/ui/finance/batch-process";
        }

        // 2. Hapus detail jurnal otomatis saja
        em.createNativeQuery(
            "DELETE FROM fin.journal_dtl WHERE header_id IN " +
            "(SELECT id FROM fin.journal_hdr WHERE ref_type IS NOT NULL)")
            .executeUpdate();

        // 3. Hapus header jurnal otomatis saja
        int deletedHdr = em.createNativeQuery(
            "DELETE FROM fin.journal_hdr WHERE ref_type IS NOT NULL")
            .executeUpdate();

        // 4. Reset status transaksi
        em.createNativeQuery(
            "UPDATE sal.sales_invoice_hdr SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();
        em.createNativeQuery(
            "UPDATE pur.purchase_invoice_hdr SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();
        em.createNativeQuery(
            "UPDATE fin.cash_bank_trx SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();
        em.createNativeQuery(
            "UPDATE inv.transaction_header SET status = 'CONFIRMED' WHERE UPPER(TRIM(status)) = 'POSTED'")
            .executeUpdate();

        String msg = "Berhasil hapus " + deletedHdr + " jurnal otomatis.";
        if (totalManual > 0) {
            msg += " " + totalManual + " jurnal manual DIPERTAHANKAN.";
        }
        ra.addFlashAttribute("success", msg);
        return "redirect:/ui/finance/batch-process";
    }
}
