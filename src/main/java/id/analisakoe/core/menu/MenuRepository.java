package id.analisakoe.core.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    @Query("SELECT m FROM Menu m WHERE m.status = 'ACTIVE' ORDER BY m.sortOrder, m.id")
    List<Menu> findAllActiveOrdered();
}