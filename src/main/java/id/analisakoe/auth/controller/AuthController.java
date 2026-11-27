package id.analisakoe.auth.controller;

import id.analisakoe.auth.dto.*;
import id.analisakoe.auth.service.AuthService;
import id.analisakoe.core.dto.ChangePasswordRequest;
import id.analisakoe.core.service.UserService;
import id.analisakoe.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getHeader("User-Agent"), clientIp(http));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request, http.getHeader("User-Agent"), clientIp(http));
    }

    /** Ganti company/cabang aktif untuk klien REST (JWT). */
    @PostMapping("/switch-company")
    public SwitchCompanyResponse switchCompany(
            Authentication authentication,
            @RequestBody @Valid SwitchCompanyRequest request,
            HttpServletRequest http) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return authService.switchCompany(
            principal.getUser().getId(),
            principal.getUser().getTenantId(),
            request.companyId(),
            request.refreshToken(),
            http.getHeader("User-Agent"),
            clientIp(http)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Set<String> authorities = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        Map<String, Object> result = new HashMap<>();
        result.put("id", principal.getUser().getId());
        result.put("tenantId", principal.getUser().getTenantId());
        result.put("companyId", principal.getUser().getDefaultCompanyId());
        result.put("username", principal.getUser().getUsername());
        result.put("email", principal.getUser().getEmail());
        result.put("fullName", principal.getUser().getFullName());
        result.put("mustChangePwd", principal.getUser().getMustChangePwd());
        result.put("roles", principal.getUser().getRoles().stream().map(r -> r.getCode()).toList());
        result.put("authorities", authorities);
        return result;
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @RequestBody @Valid ChangePasswordRequest req) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        userService.changePassword(principal.getUser().getId(), req);
        return ResponseEntity.noContent().build();
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
