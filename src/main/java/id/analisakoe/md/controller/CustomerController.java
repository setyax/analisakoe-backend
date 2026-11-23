package id.analisakoe.md.controller;

import id.analisakoe.common.PageResponse;
import id.analisakoe.md.dto.CustomerRequest;
import id.analisakoe.md.dto.CustomerResponse;
import id.analisakoe.md.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/md/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('md.customer.read') or hasRole('ADMIN')")
    public PageResponse<CustomerResponse> list(
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.list(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('md.customer.read') or hasRole('ADMIN')")
    public CustomerResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('md.customer.write') or hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> create(@RequestBody @Valid CustomerRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('md.customer.write') or hasRole('ADMIN')")
    public CustomerResponse update(@PathVariable Long id, @RequestBody @Valid CustomerRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('md.customer.write') or hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}