package com.aieducenter.admin.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.UpdateMenuCommand;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.application.mapper.AdminMenuMapper;
import static com.cartisan.core.util.Assertions.requirePresent;

import com.cartisan.core.exception.DomainException;

/**
 * 菜单管理应用服务。
 */
@Service
public class MenuManagementAppService {

    private final AdminMenuRepository menuRepository;
    private final AdminMenuMapper adminMenuMapper;

    public MenuManagementAppService(AdminMenuRepository menuRepository,
                                     AdminMenuMapper adminMenuMapper) {
        this.menuRepository = menuRepository;
        this.adminMenuMapper = adminMenuMapper;
    }

    /**
     * 构建菜单树（支持按 menuIds 过滤）。
     *
     * @param menuIds 菜单 ID 集合，null 表示全部菜单
     * @return 菜单 DTO 列表
     */
    public List<MenuResponse> findTree(Set<Long> menuIds) {
        List<AdminMenu> allMenus = menuRepository.findAll();

        // 构建映射（如果 menuIds 为 null，则包含所有菜单）
        Map<Long, AdminMenu> menuMap = MapUtil.newHashMap();
        for (AdminMenu menu : allMenus) {
            if (menuIds == null || menuIds.contains(menu.getId())) {
                menuMap.put(menu.getId(), menu);
            }
        }

        // 建立父子关系
        List<AdminMenu> roots = CollUtil.newArrayList();
        for (AdminMenu menu : menuMap.values()) {
            if (menu.getParentId() == null) {
                roots.add(menu);
            } else {
                AdminMenu parent = menuMap.get(menu.getParentId());
                if (parent != null) {
                    parent.addChild(menu);
                }
            }
        }

        return adminMenuMapper.convertList(roots);
    }

    /**
     * 创建菜单。
     */
    @Transactional
    public Long create(CreateMenuCommand command) {
        // 验证父菜单
        if (command.parentId() != null) {
            requirePresent(
                    menuRepository.findById(command.parentId()),
                    AdminMessage.MENU_NOT_FOUND
            );

            // 检查层级（最多3级）- 通过计算父菜单的层级
            int parentDepth = calculateDepth(command.parentId());
            if (parentDepth >= AdminMenu.MAX_DEPTH - 1) {
                throw new DomainException(AdminMessage.MENU_DEPTH_EXCEEDED);
            }
        }

        AdminMenu menu = new AdminMenu(command.name(), command.path(), command.icon(), command.parentId(), command.sortOrder());
        AdminMenu saved = menuRepository.save(menu);
        return saved.getId();
    }

    /**
     * 修改菜单。
     */
    @Transactional
    public void update(Long id, UpdateMenuCommand command) {
        AdminMenu menu = requirePresent(
                menuRepository.findById(id),
                AdminMessage.MENU_NOT_FOUND
        );

        // 如果修改父菜单，验证新父菜单
        if (command.parentId() != null && !command.parentId().equals(menu.getParentId())) {
            // 不允许将菜单设置为自己的父级
            if (command.parentId().equals(id)) {
                throw new DomainException(AdminMessage.MENU_INVALID_PARENT);
            }

            AdminMenu parent = requirePresent(
                    menuRepository.findById(command.parentId()),
                    AdminMessage.MENU_NOT_FOUND
            );

            // 检查层级
            int parentDepth = calculateDepth(parent.getId());
            if (parentDepth >= AdminMenu.MAX_DEPTH - 1) {
                throw new DomainException(AdminMessage.MENU_DEPTH_EXCEEDED);
            }

            // 不允许将菜单设置为自己的后代
            if (isDescendant(menu.getId(), command.parentId())) {
                throw new DomainException(AdminMessage.MENU_INVALID_PARENT);
            }
        }

        menu.setName(command.name());
        menu.setPath(command.path());
        menu.setIcon(command.icon());
        menu.setParentId(command.parentId());
        menu.setSortOrder(command.sortOrder());
        menuRepository.save(menu);
    }

    /**
     * 删除菜单。
     */
    @Transactional
    public void delete(Long id) {
        AdminMenu menu = requirePresent(
                menuRepository.findById(id),
                AdminMessage.MENU_NOT_FOUND
        );

        // 有子菜单的不能删除
        if (hasChildren(id)) {
            throw new DomainException(AdminMessage.MENU_HAS_CHILDREN);
        }

        menuRepository.delete(menu);
    }

    /**
     * 检查菜单是否有子菜单。
     */
    private boolean hasChildren(Long menuId) {
        return menuRepository.existsByParentId(menuId);
    }

    /**
     * 计算菜单的深度（层级）。
     */
    private int calculateDepth(Long menuId) {
        int depth = 0;
        Long currentId = menuId;
        while (currentId != null && depth < AdminMenu.MAX_DEPTH) {
            var menu = menuRepository.findById(currentId);
            if (menu.isEmpty()) {
                break;
            }
            currentId = menu.get().getParentId();
            depth++;
        }
        return depth;
    }

    /**
     * 检查 targetId 是否是 sourceId 的后代节点。
     */
    private boolean isDescendant(Long sourceId, Long targetId) {
        List<AdminMenu> allMenus = menuRepository.findAll();
        return isDescendantRecursive(allMenus, sourceId, targetId);
    }

    private boolean isDescendantRecursive(List<AdminMenu> menus, Long currentId, Long targetId) {
        for (AdminMenu menu : menus) {
            if (targetId.equals(menu.getParentId())) {
                if (menu.getId().equals(currentId)) {
                    return true;
                }
                if (isDescendantRecursive(menus, currentId, menu.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========== DTO 返回方法 ==========

    /**
     * 获取所有菜单树。
     */
    public List<MenuResponse> findTree() {
        return findTree(null);
    }

    /**
     * 根据 ID 获取菜单详情。
     */
    public MenuResponse findById(Long id) {
        AdminMenu menu = requirePresent(
                menuRepository.findById(id),
                AdminMessage.MENU_NOT_FOUND
        );
        return adminMenuMapper.convert(menu);
    }
}
