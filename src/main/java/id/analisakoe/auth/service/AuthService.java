package id.analisakoe.auth.service;

import id.analisakoe.auth.dto.LoginRequest;
import id.analisakoe.auth.dto.LoginResponse;
import id.analisakoe.auth.dto.RefreshRequest;
import id.analisakoe.auth.dto.TokenResponse;
import id.analisakoe.auth.jwt.JwtService;
import id.analisakoe.auth.token.RefreshToken;
import id.analisakoe.auth.token.RefreshTokenService;
import id.analisakoe.common.BusinessException;
import id.analisakoe.core.entity.AppUser;
import id.analisakoe.core.repository.AppUserRepository;
import id.analisakoe.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AppUserRepository userRepo;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AppUserRepository userRepo) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepo = userRepo;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent, String ip) {
        var auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String accessToken = jwtService.generateToken(principal);
        var issued = refreshTokenService.issue(principal.getUser().getId(), null, userAgent, ip);

        Set<String> roles = principal.getUser().getRoles().stream()
            .map(r -> r.getCode())
            .collect(Collectors.toSet());

        Set<String> permissions = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> !a.startsWith("ROLE_"))
            .collect(Collectors.toSet());

        var user = new LoginResponse.UserInfo(
            principal.getUser().getId(),
            principal.getUser().getTenantId(),
            principal.getUser().getUsername(),
            principal.getUser().getEmail(),
            principal.getUser().getFullName(),
            roles,
            permissions
        );

        return new LoginResponse(
            accessToken,
            issued.plaintext(),
            "Bearer",
            jwtService.getTtlSeconds(),
            refreshTokenService.getTtlSeconds(),
            user
        );
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, String userAgent, String ip) {
        RefreshToken current = refreshTokenService.validateAndTouch(request.refreshToken());
        if (current == null) {
            throw new BusinessException("Refresh token tidak valid atau sudah kedaluwarsa");
        }

        AppUser user = userRepo.findById(current.getUserId())
            .orElseThrow(() -> new BusinessException("User tidak ditemukan untuk token ini"));
        if (!"ACTIVE".equals(user.getStatus())) {
            refreshTokenService.revokeAllForUser(user.getId(), "USER_NOT_ACTIVE");
            throw new BusinessException("User tidak aktif");
        }

        UserPrincipal principal = new UserPrincipal(user);

        var issued = refreshTokenService.issue(
            user.getId(),
            current.getFamilyId(),
            userAgent,
            ip
        );
        refreshTokenService.rotate(current, issued.entity().getTokenHash());

        String newAccess = jwtService.generateToken(principal);

        return new TokenResponse(
            newAccess,
            issued.plaintext(),
            "Bearer",
            jwtService.getTtlSeconds(),
            refreshTokenService.getTtlSeconds()
        );
    }

    @Transactional
    public void logout(String refreshTokenPlaintext) {
        refreshTokenService.revoke(refreshTokenPlaintext, "LOGOUT");
    }
}