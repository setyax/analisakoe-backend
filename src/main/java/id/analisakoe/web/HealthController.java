package id.analisakoe.web;

import id.analisakoe.core.entity.Company;
import id.analisakoe.core.repository.CompanyRepository;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class HealthController {

    private final CompanyRepository companyRepository;

    public HealthController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "app", "AnalisaKoe",
            "status", "OK",
            "time", OffsetDateTime.now().toString()
        );
    }

    @GetMapping("/companies")
    public List<Company> companies() {
        return companyRepository.findAll();
    }
}