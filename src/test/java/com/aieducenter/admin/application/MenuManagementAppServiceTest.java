package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.UpdateMenuCommand;
import com.aieducenter.admin.application.mapper.AdminMenuMapper;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.cartisan.core.exception.DomainException;

/**
 * MenuManagementAppService 测试。
 */
@ExtendWith(MockitoExtension.class)
class MenuManagementAppServiceTest {

    @Mock
    private AdminMenuRepository menuRepository;

    @Mock
    private AdminMenuMapper adminMenuMapper;

    private MenuManagementAppService menuManagementAppService;

    @BeforeEach
    void setUp() {
        menuManagementAppService = new MenuManagementAppService(menuRepository, adminMenuMapper);
    }

    @Test
    void given_noParentId_when_createMenu_then_success() {
        // Given
        var command = new CreateMenuCommand("用户管理", "/users", "user", null, 1);

        // 使用 thenAnswer 返回带 ID 的菜单
        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(invocation -> {
            AdminMenu saved = new AdminMenu("用户管理", "/users", "user", null, 1);
            saved.setChildren(List.of());
            // 使用反射设置 ID（模拟 @PrePersist）
            try {
                java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(saved, 1L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return saved;
        });

        // When
        Long menuId = menuManagementAppService.create(command);

        // Then
        assertThat(menuId).isNotNull();
        assertThat(menuId).isEqualTo(1L);
        verify(menuRepository).save(any(AdminMenu.class));
    }

    @Test
    void given_validParentId_when_createMenu_then_success() {
        // Given
        Long parentId = 1L;
        var command = new CreateMenuCommand("用户列表", "/users/list", "list", parentId, 1);

        AdminMenu parentMenu = new AdminMenu("用户管理", "/users", "user", null, 0);
        parentMenu.setChildren(List.of());

        when(menuRepository.findById(parentId)).thenReturn(Optional.of(parentMenu));
        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(invocation -> {
            AdminMenu saved = new AdminMenu("用户列表", "/users/list", "list", parentId, 1);
            saved.setChildren(List.of());
            // 使用反射设置 ID
            try {
                java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(saved, 2L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return saved;
        });

        // When
        Long menuId = menuManagementAppService.create(command);

        // Then
        assertThat(menuId).isNotNull();
        assertThat(menuId).isEqualTo(2L);
        verify(menuRepository).save(any(AdminMenu.class));
    }

    @Test
    void given_nonExistentParentId_when_createMenu_then_throwException() {
        // Given
        Long parentId = 999L;
        var command = new CreateMenuCommand("用户列表", "/users/list", "list", parentId, 1);

        when(menuRepository.findById(parentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> menuManagementAppService.create(command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_maxDepthParent_when_createMenu_then_throwException() {
        // Given
        // 创建 3 层菜单链：root -> level1 -> level2 (target)
        // 添加 level3 会超过 MAX_DEPTH=3
        Long level2Id = 3L;
        var command = new CreateMenuCommand("子菜单", "/sub", "sub", level2Id, 1);

        // 设置菜单链：level2 (parentId=2) -> level1 (parentId=1) -> root (parentId=null)
        AdminMenu rootMenu = new AdminMenu("根菜单", "/root", "root", null, 0);
        AdminMenu level1Menu = new AdminMenu("一级菜单", "/level1", "l1", 1L, 1);
        AdminMenu level2Menu = new AdminMenu("二级菜单", "/level2", "l2", 2L, 1);

        // 设置 ID
        try {
            java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(rootMenu, 1L);
            idField.set(level1Menu, 2L);
            idField.set(level2Menu, 3L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 设置 findById 返回值
        when(menuRepository.findById(level2Id)).thenReturn(Optional.of(level2Menu));
        when(menuRepository.findById(2L)).thenReturn(Optional.of(level1Menu));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(rootMenu));

        // When & Then - level2 的深度是 2，添加子菜单会达到深度 3，正好是 MAX_DEPTH
        // 所以这个测试需要调整：让 level2 的深度已经是 MAX_DEPTH - 1 = 2
        // 实际上 calculateDepth(level2Id) = 2 (level2->level1->root)
        // 所以添加 level3 会超过限制
        assertThatThrownBy(() -> menuManagementAppService.create(command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_validData_when_updateMenu_then_success() {
        // Given
        Long menuId = 1L;
        var command = new UpdateMenuCommand("新名称", "/new/path", "newIcon", null, 2);

        AdminMenu existingMenu = new AdminMenu("旧名称", "/old/path", "oldIcon", null, 1);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        menuManagementAppService.update(menuId, command);

        // Then
        assertThat(existingMenu.getName()).isEqualTo("新名称");
        assertThat(existingMenu.getPath()).isEqualTo("/new/path");
        assertThat(existingMenu.getIcon()).isEqualTo("newIcon");
        assertThat(existingMenu.getSortOrder()).isEqualTo(2);
        verify(menuRepository).save(existingMenu);
    }

    @Test
    void given_nonExistentMenu_when_update_then_throwException() {
        // Given
        Long menuId = 999L;
        var command = new UpdateMenuCommand("新名称", "/new/path", "newIcon", null, 1);

        when(menuRepository.findById(menuId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> menuManagementAppService.update(menuId, command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuAsOwnParent_when_update_then_throwException() {
        // Given
        Long menuId = 1L;
        var command = new UpdateMenuCommand("名称", "/path", "icon", menuId, 1); // 设置自己为父级

        AdminMenu existingMenu = new AdminMenu("名称", "/path", "icon", null, 1);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));

        // When & Then
        assertThatThrownBy(() -> menuManagementAppService.update(menuId, command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuWithChildren_when_delete_then_throwException() {
        // Given
        Long menuId = 1L;
        AdminMenu existingMenu = new AdminMenu("父菜单", "/parent", "parent", null, 1);

        when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
        when(menuRepository.existsByParentId(menuId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> menuManagementAppService.delete(menuId))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuWithoutChildren_when_delete_then_success() {
        // Given
        Long menuId = 1L;
        AdminMenu existingMenu = new AdminMenu("菜单", "/menu", "menu", null, 1);

        when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
        when(menuRepository.existsByParentId(menuId)).thenReturn(false);

        // When
        menuManagementAppService.delete(menuId);

        // Then
        verify(menuRepository).delete(existingMenu);
    }

    @Test
    void given_menuId_when_findById_then_returnMenu() {
        // Given
        Long menuId = 1L;
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));
        when(adminMenuMapper.convert(menu)).thenReturn(null);

        // When
        menuManagementAppService.findById(menuId);

        // Then
        verify(menuRepository).findById(menuId);
        verify(adminMenuMapper).convert(menu);
    }

    @Test
    void given_nonExistentMenuId_when_findById_then_throwException() {
        // Given
        Long menuId = 999L;
        when(menuRepository.findById(menuId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> menuManagementAppService.findById(menuId))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_noMenuFilter_when_findTree_then_returnAllMenus() {
        // Given
        List<AdminMenu> allMenus = List.of(
                new AdminMenu("用户管理", "/users", "user", null, 1),
                new AdminMenu("用户列表", "/users/list", "list", 1L, 1)
        );

        when(menuRepository.findAll()).thenReturn(allMenus);
        when(adminMenuMapper.convertList(any())).thenReturn(List.of());

        // When
        menuManagementAppService.findTree();

        // Then
        verify(menuRepository).findAll();
        verify(adminMenuMapper).convertList(any());
    }

    @Test
    void given_menuFilter_when_findTree_then_returnFilteredMenus() {
        // Given
        AdminMenu menu1 = new AdminMenu("用户管理", "/users", "user", null, 1);
        AdminMenu menu2 = new AdminMenu("用户列表", "/users/list", "list", 1L, 1);
        AdminMenu menu3 = new AdminMenu("角色管理", "/roles", "role", null, 2);

        // 使用反射设置 ID
        try {
            java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(menu1, 1L);
            idField.set(menu2, 2L);
            idField.set(menu3, 3L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<AdminMenu> allMenus = List.of(menu1, menu2, menu3);

        when(menuRepository.findAll()).thenReturn(allMenus);
        when(adminMenuMapper.convertList(any())).thenReturn(List.of());

        // When - 只获取用户管理相关的菜单
        menuManagementAppService.findTree(java.util.Set.of(1L, 2L));

        // Then
        verify(menuRepository).findAll();
        verify(adminMenuMapper).convertList(any());
    }
}
