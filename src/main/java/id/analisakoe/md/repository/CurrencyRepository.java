package id.analisakoe.md.repository;

import id.analisakoe.md.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
    List<Currency> findAllByOrderByCode();
}