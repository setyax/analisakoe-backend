package id.analisakoe.core.controller;

import id.analisakoe.common.PageResponse;
import id.analisakoe.core.dto.*;
import id.analisakoe.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/core/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('core.user.read') or hasRole('ADMIN')")
    public PageResponse<UserResponse> list(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.list(search, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('core.user.read') or hasRole('ADMIN')")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('core.user.write') or hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@RequestBody @Valid UserCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('core.user.write') or hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long id, @RequestBody @Valid UserUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('core.user.write') or hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('core.user.write') or hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody @Valid PasswordResetRequest req) {
        service.resetPassword(id, req);
        return ResponseEntity.noContent().build();
    }
}