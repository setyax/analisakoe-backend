package id.analisakoe.core.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.common.PageResponse;
import id.analisakoe.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.dto.*;
import id.analisakoe.core.entity.AppUser;
import id.analisakoe.core.entity.Role;
import id.analisakoe.core.repository.AppUserRepository;
import id.analisakoe.core.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import id.analisakoe.auth.token.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    @Autowired
    private ContextService context;

    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(AppUserRepository userRepo, RoleRepository roleRepo, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    private Long tenant() { return context.tenantId(); }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, String status, Pageable pageable) {
        Page<AppUser> page;
        if (status != null && !status.isBlank()) {
            page = userRepo.findByTenantIdAndStatus(tenant(), status, pageable);
        } else if (search != null && !search.isBlank()) {
            page = userRepo.findByTenantIdAndUsernameContainingIgnoreCase(tenant(), search, pageable);
        } else {
            page = userRepo.findByTenantId(tenant(), pageable);
        }
        return PageResponse.of(page.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        AppUser u = userRepo.findByIdAndTenantId(id, tenant())
            .orElseThrow(() -> new EntityNotFoundException("User tidak ditemukan: " + id));
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        if (userRepo.existsByTenantIdAndUsername(tenant(), req.username())) {
            throw new BusinessException("Username sudah dipakai: " + req.username());
        }
        if (req.email() != null && userRepo.findByEmail(req.email()).isPresent()) {
            throw new BusinessException("Email sudah dipakai: " + req.email());
        }

        AppUser u = new AppUser();
        u.setTenantId(tenant());
        u.setUsername(req.username().trim());
        u.setEmail(req.email());
        u.setFullName(req.fullName());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setStatus(req.status() == null ? "ACTIVE" : req.status());
        u.setMustChangePwd(Boolean.TRUE);
        u.setFailedAttempts((short) 0);
        u.setRoles(resolveRoles(req.roleCodes()));

        return UserResponse.from(userRepo.save(u));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest req) {
        AppUser u = userRepo.findByIdAndTenantId(id, tenant())
            .orElseThrow(() -> new EntityNotFoundException("User tidak ditemukan: " + id));

        if (req.email() != null && !req.email().equals(u.getEmail())) {
            userRepo.findByEmail(req.email()).ifPresent(other -> {
                if (!other.getId().equals(u.getId())) {
                    throw new BusinessException("Email sudah dipakai: " + req.email());
                }
            });
            u.setEmail(req.email());
        }
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.status() != null) u.setStatus(req.status());
        if (req.roleCodes() != null) u.setRoles(resolveRoles(req.roleCodes()));

        return UserResponse.from(userRepo.save(u));
    }

    @Transactional
    public void delete(Long id) {
        AppUser u = userRepo.findByIdAndTenantId(id, tenant())
            .orElseThrow(() -> new EntityNotFoundException("User tidak ditemukan: " + id));
        if ("admin".equals(u.getUsername())) {
            throw new BusinessException("User 'admin' tidak boleh dihapus");
        }
        // soft delete = DISABLED
        u.setStatus("DISABLED");
        userRepo.save(u);
        refreshTokenService.revokeAllForUser(u.getId(), "USER_DISABLED");
    }

    @Transactional
    public void resetPassword(Long id, PasswordResetRequest req) {
        AppUser u = userRepo.findByIdAndTenantId(id, tenant())
            .orElseThrow(() -> new EntityNotFoundException("User tidak ditemukan: " + id));
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setMustChangePwd(Boolean.TRUE);
        u.setFailedAttempts((short) 0);
        u.setLockedUntil(null);
        userRepo.save(u);
        refreshTokenService.revokeAllForUser(u.getId(), "PASSWORD_RESET");
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        AppUser u = userRepo.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User tidak ditemukan: " + userId));

        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new BusinessException("Password lama salah");
        }
        if (passwordEncoder.matches(req.newPassword(), u.getPasswordHash())) {
            throw new BusinessException("Password baru tidak boleh sama dengan password lama");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setMustChangePwd(Boolean.FALSE);
        userRepo.save(u);
        refreshTokenService.revokeAllForUser(u.getId(), "PASSWORD_CHANGED");
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        List<Role> roles = roleRepo.findByTenantIdAndCodeIn(tenant(), List.copyOf(codes));
        if (roles.size() != codes.size()) {
            Set<String> found = new HashSet<>();
            roles.forEach(r -> found.add(r.getCode()));
            Set<String> missing = new HashSet<>(codes);
            missing.removeAll(found);
            throw new BusinessException("Role tidak dikenal: " + missing);
        }
        return new HashSet<>(roles);
    }
}