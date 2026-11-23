package id.analisakoe.core.menu;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final MenuRepository repo;

    public MenuService(MenuRepository repo) { this.repo = repo; }

    /**
     * Bangun tree menu untuk user dengan authorities tertentu.
     * Aturan:
     *  - Leaf dengan requiredPermission dan user tidak punya → hide
     *  - Header tanpa anak yang visible → hide
     *  - requiredPermission == null → semua user login bisa lihat (asalkan leaf)
     */
    @Transactional(readOnly = true)
    public List<MenuTreeNode> buildTreeForUser(Collection<String> authorities) {
        Set<String> auths = authorities == null ? Set.of() : new HashSet<>(authorities);

        List<Menu> all = repo.findAllActiveOrdered();

        // group children by parent_id
        Map<Long, List<Menu>> byParent = all.stream()
            .filter(m -> m.getParentId() != null)
            .collect(Collectors.groupingBy(Menu::getParentId, LinkedHashMap::new, Collectors.toList()));

        List<MenuTreeNode> roots = new ArrayList<>();
        for (Menu m : all) {
            if (m.getParentId() == null) {
                MenuTreeNode node = buildNode(m, byParent, auths);
                if (node != null) roots.add(node);
            }
        }
        return roots;
    }

    private MenuTreeNode buildNode(Menu m, Map<Long, List<Menu>> byParent, Set<String> auths) {
        List<MenuTreeNode> children = new ArrayList<>();
        List<Menu> childMenus = byParent.getOrDefault(m.getId(), List.of());
        for (Menu child : childMenus) {
            MenuTreeNode cn = buildNode(child, byParent, auths);
            if (cn != null) children.add(cn);
        }

        // apakah user boleh lihat menu ini sendiri?
        boolean allowed = m.getRequiredPermission() == null
            || auths.contains(m.getRequiredPermission())
            || auths.contains("ROLE_ADMIN");  // admin selalu lihat semua

        // header tanpa anak visible → hide
        if (Boolean.TRUE.equals(m.getIsHeader()) && children.isEmpty()) {
            return null;
        }

        // leaf (tidak punya anak) → harus allowed
        if (children.isEmpty() && !allowed) {
            return null;
        }

        // header dengan anak, tapi header-nya sendiri tidak allowed → hide (kasus langka)
        if (!children.isEmpty() && !allowed && !Boolean.TRUE.equals(m.getIsHeader())) {
            return null;
        }

        // header selalu allowed kalau punya anak visible; leaf harus allowed
        if (!children.isEmpty() || allowed) {
            return MenuTreeNode.from(m, children);
        }
        return null;
    }
}