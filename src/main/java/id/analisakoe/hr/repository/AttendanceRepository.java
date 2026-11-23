package id.analisakoe.hr.repository;
import id.analisakoe.hr.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByAttDateBetween(LocalDate start, LocalDate end);
    List<Attendance> findByEmployeeIdAndAttDateBetween(Long empId, LocalDate start, LocalDate end);
}