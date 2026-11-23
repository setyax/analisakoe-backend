package id.analisakoe.fin.repository;

import id.analisakoe.fin.entity.JournalHdr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JournalRepository extends JpaRepository<JournalHdr, Long> {

    @Query("SELECT h FROM JournalHdr h ORDER BY h.docDate DESC, h.id DESC")
    List<JournalHdr> findAllLatest();

    List<JournalHdr> findByJournalTypeAndRemarks(String journalType, String remarks);

    @Query("SELECT d.accountId, SUM(d.debit), SUM(d.credit) " +
           "FROM JournalDtl d JOIN d.header h " +
           "WHERE h.docDate BETWEEN :start AND :end " +
           "GROUP BY d.accountId")
    List<Object[]> sumBalancesByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);
}