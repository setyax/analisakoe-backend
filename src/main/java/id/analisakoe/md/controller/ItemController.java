package id.analisakoe.md.controller;

import id.analisakoe.common.PageResponse;
import id.analisakoe.md.dto.ItemRequest;
import id.analisakoe.md.dto.ItemResponse;
import id.analisakoe.md.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/md/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('md.item.read') or hasRole('ADMIN')")
    public PageResponse<ItemResponse> list(
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.list(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('md.item.read') or hasRole('ADMIN')")
    public ItemResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('md.item.write') or hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> create(@RequestBody @Valid ItemRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('md.item.write') or hasRole('ADMIN')")
    public ItemResponse update(@PathVariable Long id, @RequestBody @Valid ItemRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('md.item.write') or hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}