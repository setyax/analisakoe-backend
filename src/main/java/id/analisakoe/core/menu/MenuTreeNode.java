package id.analisakoe.core.menu;

import java.util.List;

public record MenuTreeNode(
    Long id,
    String code,
    String name,
    String path,
    String icon,
    Integer sortOrder,
    Boolean isHeader,
    List<MenuTreeNode> children
) {
    public static MenuTreeNode from(Menu m, List<MenuTreeNode> children) {
        return new MenuTreeNode(
            m.getId(),
            m.getCode(),
            m.getName(),
            m.getPath(),
            m.getIcon(),
            m.getSortOrder(),
            m.getIsHeader(),
            children
        );
    }
}