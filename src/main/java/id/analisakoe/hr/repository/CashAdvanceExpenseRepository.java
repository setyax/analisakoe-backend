package id.analisakoe.hr.repository;

import id.analisakoe.hr.entity.CashAdvanceExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashAdvanceExpenseRepository extends JpaRepository<CashAdvanceExpense, Long> {
    List<CashAdvanceExpense> findByCashAdvanceId(Long cashAdvanceId);
}