package id.analisakoe.hr.repository;

import id.analisakoe.hr.entity.EmployeeSalary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeSalaryRepository extends JpaRepository<EmployeeSalary, Long> {
}