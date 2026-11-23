package id.analisakoe.core.menu;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/core/menus")
public class MenuController {

    private final MenuService service;

    public MenuController(MenuService service) { this.service = service; }

    /**
     * Menu tree sesuai permission user yang login.
     */
    @GetMapping
    public ResponseEntity<List<MenuTreeNode>> myMenus(Authentication auth) {
        Set<String> authorities = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return ResponseEntity.ok(service.buildTreeForUser(authorities));
    }
}