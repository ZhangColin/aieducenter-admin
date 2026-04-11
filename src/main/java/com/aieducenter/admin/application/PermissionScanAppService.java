package com.aieducenter.admin.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.aieducenter.admin.application.dto.response.PermissionResponse;
import com.aieducenter.admin.application.mapper.PermissionMapper;
import com.cartisan.security.permission.PermissionScanner;

/**
 * 权限扫描应用服务。
 */
@Service
public class PermissionScanAppService {

    private final PermissionScanner permissionScanner;
    private final PermissionMapper permissionMapper;

    public PermissionScanAppService(PermissionScanner permissionScanner,
            PermissionMapper permissionMapper) {
        this.permissionScanner = permissionScanner;
        this.permissionMapper = permissionMapper;
    }

    /**
     * 扫描指定 scope 的权限。
     */
    public List<PermissionResponse> scanByScope(String scope) {
        return permissionMapper.convertList(permissionScanner.scanByScope(scope));
    }

    /**
     * 扫描所有权限。
     */
    public List<PermissionResponse> scanAll() {
        return permissionMapper.convertList(permissionScanner.scanAll());
    }
}
