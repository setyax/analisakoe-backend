package id.analisakoe.md.controller;

import id.analisakoe.common.PageResponse;
import id.analisakoe.md.dto.SupplierRequest;
import id.analisakoe.md.dto.SupplierResponse;
import id.analisakoe.md.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/md/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('md.supplier.read') or hasRole('ADMIN')")
    public PageResponse<SupplierResponse> list(
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.list(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('md.supplier.read') or hasRole('ADMIN')")
    public SupplierResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('md.supplier.write') or hasRole('ADMIN')")
    public ResponseEntity<SupplierResponse> create(@RequestBody @Valid SupplierRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('md.supplier.write') or hasRole('ADMIN')")
    public SupplierResponse update(@PathVariable Long id, @RequestBody @Valid SupplierRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('md.supplier.write') or hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}