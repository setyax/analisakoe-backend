package id.analisakoe.md.controller;

import id.analisakoe.common.PageResponse;
import id.analisakoe.md.dto.WarehouseRequest;
import id.analisakoe.md.dto.WarehouseResponse;
import id.analisakoe.md.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/md/warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('md.item.read') or hasRole('ADMIN')")
    public PageResponse<WarehouseResponse> list(
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.list(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('md.item.read') or hasRole('ADMIN')")
    public WarehouseResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('md.item.write') or hasRole('ADMIN')")
    public ResponseEntity<WarehouseResponse> create(@RequestBody @Valid WarehouseRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('md.item.write') or hasRole('ADMIN')")
    public WarehouseResponse update(@PathVariable Long id, @RequestBody @Valid WarehouseRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('md.item.write') or hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}