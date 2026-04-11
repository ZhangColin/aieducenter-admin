package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.response.PermissionResponse;
import com.aieducenter.admin.application.mapper.PermissionMapper;
import com.cartisan.security.permission.Permission;
import com.cartisan.security.permission.PermissionScanner;

/**
 * PermissionScanAppService 测试。
 */
@ExtendWith(MockitoExtension.class)
class PermissionScanAppServiceTest {

    @Mock
    private PermissionScanner permissionScanner;

    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void given_permissionsInCode_when_scanAll_then_returnAllPermissions() {
        // Given
        PermissionScanAppService service = new PermissionScanAppService(permissionScanner, permissionMapper);

        List<Permission> expectedPermissions = List.of(
                createMockPermission("admin:user:read"),
                createMockPermission("admin:user:write")
        );

        when(permissionScanner.scanAll()).thenReturn(expectedPermissions);
        when(permissionMapper.convertList(expectedPermissions))
                .thenReturn(List.of(
                        new PermissionResponse("admin:user:read", "平台管理 / 用户管理 / 查看"),
                        new PermissionResponse("admin:user:write", "平台管理 / 用户管理 / 编辑")
                ));

        // When
        List<PermissionResponse> result = service.scanAll();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("admin:user:read");
        assertThat(result.get(0).name()).isEqualTo("平台管理 / 用户管理 / 查看");
        assertThat(result.get(1).code()).isEqualTo("admin:user:write");
        assertThat(result.get(1).name()).isEqualTo("平台管理 / 用户管理 / 编辑");
        verify(permissionScanner).scanAll();
    }

    @Test
    void given_noPermissionsInCode_when_scanAll_then_returnEmptyList() {
        // Given
        PermissionScanAppService service = new PermissionScanAppService(permissionScanner, permissionMapper);
        when(permissionScanner.scanAll()).thenReturn(List.of());

        // When
        List<PermissionResponse> result = service.scanAll();

        // Then
        assertThat(result).isEmpty();
        verify(permissionScanner).scanAll();
    }

    @Test
    void given_adminScopePermissions_when_scanByScope_then_returnAdminPermissions() {
        // Given
        PermissionScanAppService service = new PermissionScanAppService(permissionScanner, permissionMapper);

        List<Permission> adminPermissions = List.of(createMockPermission("admin:user:read"));

        when(permissionScanner.scanByScope("admin")).thenReturn(adminPermissions);
        when(permissionMapper.convertList(adminPermissions))
                .thenReturn(List.of(new PermissionResponse("admin:user:read", "平台管理 / 用户管理 / 查看")));

        // When
        List<PermissionResponse> result = service.scanByScope("admin");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("admin:user:read");
        assertThat(result.get(0).name()).isEqualTo("平台管理 / 用户管理 / 查看");
        verify(permissionScanner).scanByScope("admin");
    }

    private Permission createMockPermission(String code) {
        return org.mockito.Mockito.mock(Permission.class);
    }
}
