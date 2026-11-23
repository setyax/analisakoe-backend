package id.analisakoe.security;

import id.analisakoe.core.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UserPrincipal implements UserDetails {

    private final AppUser user;

    public UserPrincipal(AppUser user) { this.user = user; }

    public AppUser getUser() { return user; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            role.getPermissions().forEach(p ->
                authorities.add(new SimpleGrantedAuthority(p.getCode()))
            );
        });
        return authorities;
    }

    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      {
        return user.getLockedUntil() == null
            || user.getLockedUntil().isBefore(java.time.OffsetDateTime.now());
    }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return "ACTIVE".equals(user.getStatus()); }
}