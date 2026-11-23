package id.analisakoe.sal.repository;
import id.analisakoe.sal.entity.SalesReturnHdr;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SalesReturnRepository extends JpaRepository<SalesReturnHdr, Long> {
    List<SalesReturnHdr> findAllByOrderByDocDateDesc();
}