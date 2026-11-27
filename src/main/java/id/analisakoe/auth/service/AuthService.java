package id.analisakoe.auth.service;

import id.analisakoe.auth.dto.*;
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
    private final CompanyAccessService companyAccessService;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AppUserRepository userRepo,
                       CompanyAccessService companyAccessService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepo = userRepo;
        this.companyAccessService = companyAccessService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent, String ip) {
        var auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        Long companyId = requireCompany(principal.getUser().getId(), principal.getUser().getTenantId());
        String accessToken = jwtService.generateToken(principal, companyId);
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

        Long companyId = requireCompany(user.getId(), user.getTenantId());
        UserPrincipal principal = new UserPrincipal(user);
        user.setDefaultCompanyId(companyId);

        var issued = refreshTokenService.issue(
            user.getId(),
            current.getFamilyId(),
            userAgent,
            ip
        );
        refreshTokenService.rotate(current, issued.entity().getTokenHash());

        String newAccess = jwtService.generateToken(principal, companyId);

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

    /**
     * Ganti company/cabang aktif untuk user REST.
     *
     * <p>Perilaku:</p>
     * <ul>
     *   <li>Company divalidasi terhadap core.user_company + status ACTIVE.</li>
     *   <li>default_company_id di DB diperbarui supaya refresh tidak kembali ke company lama.</li>
     *   <li>Access token lama tetap berlaku sampai expired; client wajib pakai access token baru
     *       yang dikembalikan endpoint ini.</li>
     *   <li>Jika refreshToken dikirim, token lama di-rotate dan family-id dipertahankan.</li>
     * </ul>
     */
    @Transactional
    public SwitchCompanyResponse switchCompany(Long userId,
                                               Long tenantId,
                                               Long companyId,
                                               String refreshTokenPlain,
                                               String userAgent,
                                               String ip) {
        AppUser user = userRepo.findById(userId)
            .orElseThrow(() -> new BusinessException("User tidak ditemukan"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("User tidak aktif");
        }

        Long effectiveTenantId = tenantId != null ? tenantId : user.getTenantId();
        if (!companyAccessService.isAllowed(userId, effectiveTenantId, companyId)) {
            throw new BusinessException("Company/cabang ini tidak tersedia untuk user Anda");
        }

        companyAccessService.setDefaultCompany(userId, effectiveTenantId, companyId);
        user.setDefaultCompanyId(companyId);
        UserPrincipal principal = new UserPrincipal(user);

        RefreshTokenService.IssuedToken issued;
        if (refreshTokenPlain != null && !refreshTokenPlain.isBlank()) {
            RefreshToken current = refreshTokenService.validateAndTouch(refreshTokenPlain);
            if (current == null) {
                throw new BusinessException("Refresh token tidak valid atau sudah kedaluwarsa");
            }
            issued = refreshTokenService.issue(
                user.getId(), current.getFamilyId(), userAgent, ip
            );
            refreshTokenService.rotate(current, issued.entity().getTokenHash());
        } else {
            issued = refreshTokenService.issue(user.getId(), null, userAgent, ip);
        }

        String accessToken = jwtService.generateToken(principal, companyId);
        String companyName = companyAccessService.companyName(userId, effectiveTenantId, companyId);

        return new SwitchCompanyResponse(
            companyId,
            companyName,
            accessToken,
            issued.plaintext(),
            "Bearer",
            jwtService.getTtlSeconds(),
            refreshTokenService.getTtlSeconds()
        );
    }

    private Long requireCompany(Long userId, Long tenantId) {
        Long companyId = companyAccessService.resolveDefaultCompanyId(userId, tenantId);
        if (companyId == null) {
            throw new BusinessException(
                "Akun Anda belum diberikan akses ke company/cabang manapun. Hubungi Admin."
            );
        }
        return companyId;
    }
}
