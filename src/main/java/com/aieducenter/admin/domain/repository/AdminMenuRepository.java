package com.aieducenter.admin.domain.repository;

import java.util.List;
import java.util.Optional;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 菜单仓储接口。
 *
 * @since 0.1.0
 */
public interface AdminMenuRepository extends BaseRepository<AdminMenu, Long> {

    List<AdminMenu> findByParentId(Long parentId);

    boolean existsByParentId(Long parentId);
}
