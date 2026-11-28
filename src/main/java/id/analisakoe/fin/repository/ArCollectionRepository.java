package id.analisakoe.fin.repository;
import id.analisakoe.fin.entity.ArCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ArCollectionRepository extends JpaRepository<ArCollection, Long> {
    List<ArCollection> findAllByOrderByDocDateDesc();
    List<ArCollection> findByCompanyIdOrderByDocDateDesc(Long companyId);
}