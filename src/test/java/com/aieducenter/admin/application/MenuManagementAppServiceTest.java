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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.UpdateMenuCommand;
import com.aieducenter.admin.application.mapper.AdminMenuMapper;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.cartisan.core.exception.DomainException;

/**
 * MenuManagementAppService 测试（Soybean 路由生成器模型）。
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
        var command = cmd("用户管理", null);

        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(invocation -> {
            AdminMenu saved = invocation.getArgument(0);
            setId(saved, 1L);
            return saved;
        });

        Long menuId = menuManagementAppService.create(command);

        assertThat(menuId).isEqualTo(1L);
        verify(menuRepository).save(any(AdminMenu.class));
    }

    @Test
    void given_validParentId_when_createMenu_then_success() {
        Long parentId = 1L;
        var command = cmd("用户列表", parentId);

        AdminMenu parentMenu = menu("用户管理", null);
        when(menuRepository.findById(parentId)).thenReturn(Optional.of(parentMenu));
        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(invocation -> {
            AdminMenu saved = invocation.getArgument(0);
            setId(saved, 2L);
            return saved;
        });

        Long menuId = menuManagementAppService.create(command);

        assertThat(menuId).isEqualTo(2L);
    }

    @Test
    void given_nonExistentParentId_when_createMenu_then_throwException() {
        var command = cmd("用户列表", 999L);
        when(menuRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuManagementAppService.create(command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_maxDepthParent_when_createMenu_then_throwException() {
        // root(1) → level1(2) → level2(3)：level2 深度 2，再加子超过 MAX_DEPTH=3
        AdminMenu root = menu("根菜单", null);
        setId(root, 1L);
        AdminMenu level1 = menu("一级", 1L);
        setId(level1, 2L);
        AdminMenu level2 = menu("二级", 2L);
        setId(level2, 3L);

        when(menuRepository.findById(3L)).thenReturn(Optional.of(level2));
        when(menuRepository.findById(2L)).thenReturn(Optional.of(level1));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(root));

        var command = cmd("子菜单", 3L);

        assertThatThrownBy(() -> menuManagementAppService.create(command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuTypeDirectory_when_create_then_saved_menu_is_directory() {
        var command = new CreateMenuCommand("系统管理", "manage", "/manage", "layout.base", null, null,
                null, 2, MenuType.DIRECTORY, null, false, false, false, false, null, null, null, null, null);
        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(inv -> inv.getArgument(0));

        menuManagementAppService.create(command);

        ArgumentCaptor<AdminMenu> captor = ArgumentCaptor.forClass(AdminMenu.class);
        verify(menuRepository).save(captor.capture());
        assertThat(captor.getValue().getMenuType()).isEqualTo(MenuType.DIRECTORY);
        assertThat(captor.getValue().getRoutePath()).isEqualTo("/manage"); // directory 可带 path，不强制
    }

    @Test
    void given_validData_when_updateMenu_then_success() {
        Long menuId = 1L;
        var command = new UpdateMenuCommand("新名称", "manage_user", "/new", null, null, null, null, 2,
                MenuType.MENU, null, false, false, false, false, null, null, null, null, null);
        AdminMenu existing = menu("旧名称", null);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(existing));
        when(menuRepository.save(any(AdminMenu.class))).thenAnswer(inv -> inv.getArgument(0));

        menuManagementAppService.update(menuId, command);

        assertThat(existing.getMenuName()).isEqualTo("新名称");
        assertThat(existing.getRoutePath()).isEqualTo("/new");
        assertThat(existing.getSortOrder()).isEqualTo(2);
        verify(menuRepository).save(existing);
    }

    @Test
    void given_nonExistentMenu_when_update_then_throwException() {
        var command = updateCmd("新名称", null);
        when(menuRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuManagementAppService.update(999L, command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuAsOwnParent_when_update_then_throwException() {
        Long menuId = 1L;
        var command = new UpdateMenuCommand("名称", "route", "/path", null, null, null, menuId, 1,
                MenuType.MENU, null, false, false, false, false, null, null, null, null, null);
        AdminMenu existing = menu("名称", null);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> menuManagementAppService.update(menuId, command))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuWithChildren_when_delete_then_throwException() {
        AdminMenu existing = menu("父菜单", null);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> menuManagementAppService.delete(1L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_menuWithoutChildren_when_delete_then_success() {
        AdminMenu existing = menu("菜单", null);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepository.existsByParentId(1L)).thenReturn(false);

        menuManagementAppService.delete(1L);

        verify(menuRepository).delete(existing);
    }

    @Test
    void given_menuId_when_findById_then_returnMenu() {
        AdminMenu m = menu("用户管理", null);
        when(menuRepository.findById(1L)).thenReturn(Optional.of(m));
        when(adminMenuMapper.convert(m)).thenReturn(null);

        menuManagementAppService.findById(1L);

        verify(menuRepository).findById(1L);
        verify(adminMenuMapper).convert(m);
    }

    @Test
    void given_nonExistentMenuId_when_findById_then_throwException() {
        when(menuRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuManagementAppService.findById(999L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void given_noMenuFilter_when_findTree_then_returnAllMenus() {
        List<AdminMenu> allMenus = List.of(menu("用户管理", null), menu("用户列表", 1L));
        when(menuRepository.findAll()).thenReturn(allMenus);
        when(adminMenuMapper.convertList(any())).thenReturn(List.of());

        menuManagementAppService.findTree();

        verify(menuRepository).findAll();
        verify(adminMenuMapper).convertList(any());
    }

    @Test
    void given_menuFilter_when_findTree_then_returnFilteredMenus() {
        AdminMenu m1 = menu("用户管理", null);
        setId(m1, 1L);
        AdminMenu m2 = menu("用户列表", 1L);
        setId(m2, 2L);
        AdminMenu m3 = menu("角色管理", null);
        setId(m3, 3L);

        when(menuRepository.findAll()).thenReturn(List.of(m1, m2, m3));
        when(adminMenuMapper.convertList(any())).thenReturn(List.of());

        menuManagementAppService.findTree(java.util.Set.of(1L, 2L));

        verify(menuRepository).findAll();
        verify(adminMenuMapper).convertList(any());
    }

    // ========== helper ==========

    private static CreateMenuCommand cmd(String menuName, Long parentId) {
        return new CreateMenuCommand(menuName, "route_name", "/route", null, null, null,
                parentId, 1, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null);
    }

    private static UpdateMenuCommand updateCmd(String menuName, Long parentId) {
        return new UpdateMenuCommand(menuName, "route_name", "/route", null, null, null,
                parentId, 1, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null);
    }

    private static AdminMenu menu(String menuName, Long parentId) {
        return new AdminMenu(menuName, "route_name", "/route", null, null, null,
                parentId, 0, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null);
    }

    private static void setId(AdminMenu menu, Long id) {
        try {
            java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(menu, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
