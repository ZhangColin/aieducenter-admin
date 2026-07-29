# 菜单类型特性（T1+T2+T3）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:test-driven-development 逐任务实现。步骤用 `- [ ]` 复选框跟踪。

**Goal:** 让统一后台菜单对前端正确暴露节点 `type`（MENU/GROUP/DIVIDER）、CRUD 强制 type↔path 不变量、消费侧菜单树排序+祖先链补全+裁剪空分组与悬空分隔线。

**Architecture:** 领域字段与枚举（`AdminMenu.type` / `MenuType`）已存在、DB `type` 列与种子值（=1 MENU）已就位。本特性纯应用/读出层补全，**无 schema 变更、无 Flyway 新迁移**。三个工单形成链：T1（读侧暴露）→ T2（写侧 CRUD+不变量）→ T3（消费侧树修正，其集成测试依赖 T2 的 CRUD 建 GROUP/DIVIDER）。type↔path 不变量落在**菜单聚合内**（对齐 `AdminUser` 的 `require()` 码风）。树组装逻辑（排序/祖先补全/裁剪）抽成纯函数 `MenuTreeAssembler` 便于单测。

**Tech Stack:** Java 21 records / Spring Boot 3.4 / MapStruct / cartisan-web（全局 `BaseEnumSerializer`+`BaseEnumDeserializer` 经 `JacksonConfiguration` 自动注册）/ JUnit5 + AssertJ + Mockito / PostgreSQL（集成测试真实库）。

## Global Constraints

- DDD 六边形：不变量在聚合内强制；应用层不绕过。
- 枚举实现 `BaseEnum<Integer>`，整数 code 存储/传输；出入站经全局 BaseEnum（反）序列化器，**无需逐字段配置**。
- DTO 用 record；构造函数注入；集合用 hutool（`CollUtil`）。
- 错误码格式 `(httpStatus, "ADMIN_NNN[_M]", msg)`，实现 `CodeMessage`。
- 测试命名 `given_{条件}_when_{操作}_then_{预期}`；断言用 AssertJ。
- 领域层零外部依赖；`require` 来自 `static import com.cartisan.core.util.Assertions.require`。
- commit 信息中文，按工单分 3 个 commit。

## 已确认的关键事实（探索结论）

1. `AdminMenu` 已有 `type` 字段（`MenuType type = MenuType.MENU`，`@Column(name="type", nullable=false)`），有显式 `setType`（null→MENU）；**构造函数不接收 type、无 path↔type 不变量**（T2 缺口）。
2. `MenuType` 枚举完备：`MENU(1)/GROUP(2)/DIVIDER(3)`，`implements BaseEnum<MenuType>`，带 `JpaConverter`。
3. `MenuResponse`（record）**无 type 字段**（T1 缺口）；`AdminMenuMapper` 是 MapStruct `DomainMapper<AdminMenu,MenuResponse>`——加同名字段自动映射。
4. `findTree(Set<Long> menuIds)`（`MenuManagementAppService:45`）两 bug：① HashMap 不排序；② 角色过滤时若叶子父 GROUP 未在 menuIds 中 → `menuMap.get(parentId)=null` → 叶子既不入 roots 也不挂父 → **静默丢弃**（T3 缺口）。
5. `CreateMenuCommand` / `UpdateMenuCommand` 均**无 type 字段**（T2 缺口）；`update` 用零散 setter（含 `menu.setPath(...)`），是唯一 `setPath` 调用点。
6. 错误码 `AdminMessage` 枚举；菜单族已有 `ADMIN_014`(MENU_HAS_CHILDREN,403) / `ADMIN_014_1`(DEPTH,403) / `ADMIN_014_2`(INVALID_PARENT,403)；新增 `ADMIN_014_3`。
7. 全局（反）序列化器字节码已验证：`BaseEnumDeserializer.deserialize` 对非 null token 调 `getValueAsInt()`→`BaseEnum.parseByCode`，异常/null→null（由聚合归一为 MENU）。故 command 的 `type` 用 `MenuType` 类型即可，请求 `"type":1` 正常反序列化。
8. V1 迁移 `sys_admin_menus.type INTEGER NOT NULL`；V2 种子菜单全部 `type=1`（MENU）。
9. `AdminUserPermissionAppService.getMenus`：超管→`findTree(null)`（全量）；其他→按角色 menuIds→`findTree(menuIds)`（过滤）。`/menus` 管理视图→`findTree()`→`findTree(null)`（全量）。
10. **无现成 BaseEnum→整数序列化的 HTTP 测试**（控制器测试均 standaloneSetup）；整数序列化是框架保证，T1 用 @SpringBootTest 注入 `ObjectMapper` 补一条断言。

## File Structure

**Create:**
- `src/main/java/com/aieducenter/admin/application/MenuTreeAssembler.java` — 纯函数树组装（排序 / 祖先链补全 / 裁剪空 GROUP 与悬空 DIVIDER）。状态无关，静态方法。
- `src/test/java/com/aieducenter/admin/application/MenuTreeAssemblerTest.java` — 纯单测：排序、祖先补全、裁剪（空 GROUP、悬空 DIVIDER、级联）、管理视图不裁。

**Modify:**
- `domain/dto/response/MenuResponse.java` — 加 `type`（MenuType）字段 + 7 参次级构造函数（默认 MENU，保持现有 8 处调用点兼容）。
- `domain/aggregate/AdminMenu.java` — 构造函数接收 type 并在聚合内强制 type↔path 不变量；新增 `updateDetails(...)`；`setType` 改为不变量感知；去掉 path 的 `@Setter`（关闭旁路）。
- `application/dto/command/CreateMenuCommand.java` / `UpdateMenuCommand.java` — 加 `type`（MenuType，可选）+ 旧参数次级构造函数。
- `application/MenuManagementAppService.java` — `create`/`update` 传 type；`update` 改用 `updateDetails`；`findTree` 委托 `MenuTreeAssembler.assemble`。
- `domain/error/AdminMessage.java` — 新增 `MENU_TYPE_PATH_MISMATCH(400, "ADMIN_014_3", "MENU 类型必须有路径")`。

**Test updates（随行为变化更新，非破坏性）：**
- `endpoints/controller/AdminMenuControllerTest.java` — 3 处 `new MenuResponse(...)` → 补 type（用次级构造函数后**无需改**；仅当用规范构造时改）。
- `application/AdminUserPermissionAppServiceTest.java`(4)、`endpoints/controller/AdminAuthControllerTest.java`(1) — 同上。
- `domain/aggregate/AdminMenuTest.java` — 新增不变量测试（MENU 缺 path 抛 ADMIN_014_3；GROUP/DIVIDER path 归 null）。
- `boundary/MenuBoundaryTest.java` — `given_path_boundary` 更新为反映新不变量。
- `integration/AdminMenuTreeTest.java` — 重写孤儿断言为「祖先链补全」；新增裁剪/排序/序列化断言。

> **兼容策略**：AdminMenu / CreateMenuCommand / UpdateMenuCommand / MenuResponse 均采用「规范构造函数（含 type）+ 次级构造函数（旧 arity，type 缺省）」。这样绝大多数现有 5 参 `new AdminMenu(...)`、7 参 `new MenuResponse(...)` 调用点**零改动**即可编译；只有真正受不变量影响的测试（`given_path_boundary`、孤儿集成测试）需重写。

---

## Task 1（T1 / #4）：读侧暴露 `type`（纯读路径，无行为变化）

**Files:** Modify `MenuResponse.java`; Verify `AdminMenuMapper`（无需改）+ `AdminMenuTreeTest`（加断言）。

**Interfaces:** Produces `MenuResponse.type() : MenuType`（后续 T2/T3 与前端依赖）。

- [ ] **Step 1: 写失败测试**（整数序列化 + type 透传）

在 `AdminMenuTreeTest` 注入 `ObjectMapper`，新增：
```java
@Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

@Test
void given_menuResponse_when_serialize_then_type_is_integer_code() throws Exception {
    MenuResponse menu  = new MenuResponse(1L, "用户管理", "/users", "user", null, 1, MenuType.MENU,  null);
    MenuResponse group = new MenuResponse(2L, "分组",    null,      null, null, 2, MenuType.GROUP, null);
    MenuResponse divider = new MenuResponse(3L, "--",    null,      null, null, 3, MenuType.DIVIDER, null);
    assertThat(objectMapper.writeValueAsString(menu)).contains("\"type\":1");
    assertThat(objectMapper.writeValueAsString(group)).contains("\"type\":2");
    assertThat(objectMapper.writeValueAsString(divider)).contains("\"type\":3");
}
```
并在既有 `given_menu_hierarchy_when_query_tree_then_return_correct_structure` 末尾加：`assertThat(level1.type()).isEqualTo(MenuType.MENU);`（type 与种子/默认语义一致）。

- [ ] **Step 2: 运行验证失败** — `mvn test -Dtest=AdminMenuTreeTest` → 编译失败（`MenuResponse` 无 8 参构造 / 无 `type()`）。

- [ ] **Step 3: 实现** — 给 `MenuResponse` 加 `type` 字段 + 次级构造函数：
```java
package com.aieducenter.admin.application.dto.response;

import java.util.List;
import com.aieducenter.admin.domain.enums.MenuType;

public record MenuResponse(
        Long id, String name, String path, String icon, Long parentId, Integer sortOrder,
        MenuType type,
        List<MenuResponse> children
) {
    /** 旧 7 参构造（type 缺省 MENU），保持现有调用点兼容。 */
    public MenuResponse(Long id, String name, String path, String icon, Long parentId, Integer sortOrder, List<MenuResponse> children) {
        this(id, name, path, icon, parentId, sortOrder, MenuType.MENU, children);
    }
}
```
MapStruct 自动映射 `AdminMenu.type`→`MenuResponse.type`（同名同类型），无需改 mapper。

- [ ] **Step 4: 运行验证通过** — `mvn test -Dtest=AdminMenuTreeTest` → PASS（含整数序列化断言）。

- [ ] **Step 5: 全量编译 + 受影响测试** — `mvn test -Dtest=AdminMenuControllerTest,AdminUserPermissionAppServiceTest,AdminAuthControllerTest,AdminMenuTreeTest` → PASS（次级构造函数使旧 7 参调用点零改动）。

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/aieducenter/admin/application/dto/response/MenuResponse.java \
        src/test/java/com/aieducenter/admin/integration/AdminMenuTreeTest.java
git commit -m "feat(admin): T1 菜单响应暴露 type 字段（GROUP/MENU/DIVIDER 整数 code）

MenuResponse 增 type（MenuType），经全局 BaseEnumSerializer 序列化为整数 code。
纯读路径：领域字段/种子值已就位，无 schema 变更、不改树的形状/顺序/过滤行为。
7 参次级构造函数保持既有调用点兼容。

Closes #4"
```

---

## Task 2（T2 / #5）：CRUD 接受 type + 聚合内强制 type↔path 不变量

**Files:** Modify `AdminMessage.java`, `AdminMenu.java`, `CreateMenuCommand.java`, `UpdateMenuCommand.java`, `MenuManagementAppService.java`; Test `AdminMenuTest.java`, `MenuBoundaryTest.java`, `MenuManagementAppServiceTest.java`, `AdminMenuTreeTest.java`。

**Interfaces:** Consumes T1 的 `MenuResponse.type`；Produces `AdminMessage.MENU_TYPE_PATH_MISMATCH`、`AdminMenu.updateDetails(...)`、command `.type()`。

**错误码 HTTP 状态决策**：`ADMIN_014_3` 用 **400**（type/path 组合不一致属于请求体校验错误，类比 `USERNAME_INVALID`(400)）；菜单族既有 `_1/_2` 是树结构业务限制（403），语义不同。如评审倾向 403 可一行改。

- [ ] **Step 1: 写失败测试（领域不变量）** — 在 `AdminMenuTest` 新增：
```java
@Test
void given_menuType_without_path_when_construct_then_throw_ADMIN_014_3() {
    assertThatThrownBy(() -> new AdminMenu("用户管理", null, "user", null, 1, MenuType.MENU))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("ADMIN_014_3");
    assertThatThrownBy(() -> new AdminMenu("用户管理", "  ", "user", null, 1, MenuType.MENU))
        .isInstanceOf(DomainException.class);
}

@Test
void given_group_or_divider_when_construct_then_path_normalized_to_null() {
    AdminMenu group = new AdminMenu("分组", "/ignored", null, null, 1, MenuType.GROUP);
    assertThat(group.getType()).isEqualTo(MenuType.GROUP);
    assertThat(group.getPath()).isNull();
    AdminMenu divider = new AdminMenu("--", null, null, null, 2, MenuType.DIVIDER);
    assertThat(divider.getType()).isEqualTo(MenuType.DIVIDER);
    assertThat(divider.getPath()).isNull();
}

@Test
void given_default_type_when_construct_5arg_then_menu_with_path() {
    AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1); // 旧 5 参
    assertThat(menu.getType()).isEqualTo(MenuType.MENU);
    assertThat(menu.getPath()).isEqualTo("/users");
}

@Test
void given_updateDetails_to_menu_without_path_when_update_then_throw() {
    AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);
    assertThatThrownBy(() -> menu.updateDetails("用户管理", null, "user", null, 1, MenuType.MENU))
        .isInstanceOf(DomainException.class);
}
```

- [ ] **Step 2: 运行验证失败** — `mvn test -Dtest=AdminMenuTest` → 编译失败（无 6 参构造 / 无 `updateDetails`）。

- [ ] **Step 3: 新增错误码** — `AdminMessage.java` 在 `MENU_INVALID_PARENT` 后加：
```java
/**
 * MENU 类型菜单必须有非空路径（type↔path 不变量）。
 */
MENU_TYPE_PATH_MISMATCH(400, "ADMIN_014_3", "MENU 类型菜单必须有路径"),
```
（同步更新类头注释的菜单限制分组说明。）

- [ ] **Step 4: 改 AdminMenu 聚合** — 引入 `require`，重构构造与 mutator：
```java
import static com.cartisan.core.util.Assertions.require;
import com.aieducenter.admin.domain.error.AdminMessage;
// ...
// path 字段：去掉 @Setter，仅 @Getter（关闭不变量旁路）
@Getter @Column(name = "path", length = 255) private String path;

/** 创建菜单（含类型，强制 type↔path 不变量）。 */
public AdminMenu(String name, String path, String icon, Long parentId, Integer sortOrder, MenuType type) {
    this.name = name;
    this.icon = icon;
    this.parentId = parentId;
    this.sortOrder = sortOrder != null ? sortOrder : 0;
    applyTypeAndPath(type, path);
}

/** 旧 5 参构造（type 缺省 → MENU），保持现有调用点兼容。 */
public AdminMenu(String name, String path, String icon, Long parentId, Integer sortOrder) {
    this(name, path, icon, parentId, sortOrder, null);
}

/** 整体更新（应用层调用；强制 type↔path 不变量）。 */
public void updateDetails(String name, String path, String icon, Long parentId, Integer sortOrder, MenuType type) {
    this.name = name;
    this.icon = icon;
    this.parentId = parentId;
    this.sortOrder = sortOrder != null ? sortOrder : 0;
    applyTypeAndPath(type, path);
}

/** setType 改为不变量感知（对当前 path 校验）；保持既有 setType 测试通过。 */
public void setType(MenuType type) {
    applyTypeAndPath(type, this.path);
}

private void applyTypeAndPath(MenuType type, String path) {
    MenuType resolved = type != null ? type : MenuType.MENU;
    if (resolved == MenuType.MENU) {
        require(path != null && !path.isBlank(), AdminMessage.MENU_TYPE_PATH_MISMATCH);
        this.path = path;
    } else {
        this.path = null; // GROUP / DIVIDER：path 无意义，归一为 null
    }
    this.type = resolved;
}
```
> `setType` 在已有 `new AdminMenu(...,"/test",...)`（path 非空）上 `setType(GROUP)` → path 归 null、断言 type==GROUP → 既有 `AdminMenuTest`/`MenuBoundaryTest` 的 setType 用例不变量仍 PASS。

- [ ] **Step 5: 运行 AdminMenuTest** → `mvn test -Dtest=AdminMenuTest` PASS。

- [ ] **Step 6: 写 command 失败测试** — 在 `MenuManagementAppServiceTest` 的 create/update 用例补 type 透传断言（如 `given_menuTypeGroup_when_create_then_typeSet`），并新增 `given_menuTypeMenuWithoutPath_when_create_then_throw`（mock repo，断言 DomainException ADMIN_014_3）。

- [ ] **Step 7: 给 command 加 type** —
```java
// CreateMenuCommand.java
import com.aieducenter.admin.domain.enums.MenuType;
public record CreateMenuCommand(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 255) String path,
        @Size(max = 50) String icon,
        Long parentId,
        Integer sortOrder,
        MenuType type            // 可选；缺省由聚合归一为 MENU
) {
    public CreateMenuCommand(String name, String path, String icon, Long parentId, Integer sortOrder) {
        this(name, path, icon, parentId, sortOrder, null);
    }
}
```
`UpdateMenuCommand` 同理加 `MenuType type` 字段 + 5 参次级构造。

- [ ] **Step 8: 改 MenuManagementAppService create/update 传 type** —
```java
// create:
AdminMenu menu = new AdminMenu(command.name(), command.path(), command.icon(),
        command.parentId(), command.sortOrder(), command.type());
// update（替换原零散 setter，含移除 menu.setPath(...)）：
menu.updateDetails(command.name(), command.path(), command.icon(),
        command.parentId(), command.sortOrder(), command.type());
```

- [ ] **Step 9: 更新 `MenuBoundaryTest.given_path_boundary`** — 反映新不变量：MENU + 空 path 抛 ADMIN_014_3；GROUP/DIVIDER 接受任意 path 并归 null。其余边界用例（path="/test"）不受影响，无需改。

- [ ] **Step 10: 运行受影响测试** — `mvn test -Dtest=AdminMenuTest,MenuBoundaryTest,MenuManagementAppServiceTest,AdminMenuTreeTest,RoleManagementAppServiceTest,AdminPerformanceTest` → 全 PASS。

- [ ] **Step 11: Commit**
```bash
git add -A
git commit -m "feat(admin): T2 菜单 CRUD 支持 type + 聚合内 type↔path 不变量

CreateMenuCommand/UpdateMenuCommand 接受 type（可选，缺省 MENU）。
AdminMenu 构造/updateDetails/setType 在聚合内强制不变量：
- MENU 必须有非空 path，否则 ADMIN_014_3(400)；
- GROUP/DIVIDER 的 path 归一为 null。
去掉 path 的 @Setter 关闭旁路。新增错误码 ADMIN_014_3 (MENU_TYPE_PATH_MISMATCH)。

Closes #5"
```

---

## Task 3（T3 / #6）：消费侧菜单树排序 + 祖先链补全 + 裁剪

**Files:** Create `MenuTreeAssembler.java` + `MenuTreeAssemblerTest.java`; Modify `MenuManagementAppService.findTree`; Rewrite `AdminMenuTreeTest` 孤儿断言 + 新增裁剪集成测试。

**Interfaces:** Consumes T2 的 CRUD（集成测试用它建 GROUP/DIVIDER）；Produces `MenuTreeAssembler.assemble(List<AdminMenu>, Set<Long>)`。

**算法（核心）：**
- `menuIds == null`（管理视图/超管）：全量 → 仅排序（按 sortOrder 再按 id）→ **不裁剪**。
- `menuIds != null`（角色过滤消费侧）：
  1. **祖先链补全**：keptIds = menuIds ∪ {每个 kept 节点的全部祖先}（沿 parentId 上溯）。消除孤儿。
  2. 组装树、排序。
  3. **裁剪**（自底向上一次扫描）：
     - MENU 恒保留；
     - GROUP 仅当 ≥1 存活子节点才保留（空 GROUP 裁）；
     - DIVIDER 仅当存在存活的内容兄弟（MENU 或非空 GROUP）才保留（悬空 DIVIDER 裁）。
     级联：子节点被裁会使父 GROUP 变空→父裁；内容兄弟被裁会使 DIVIDER 悬空→DIVIDER 裁。先深递归裁子、再按规则过滤本层，一步收敛。

- [ ] **Step 1: 写失败测试（纯单测 `MenuTreeAssemblerTest`）** — 覆盖：
  - `given_unsorted_when_assemble_full_then_sorted_by_sortOrder_then_id`
  - `given_filtered_leaf_without_parent_when_assemble_then_ancestor_chain_completed`（menuIds={leaf} → 返回 root→group→leaf，不孤儿）
  - `given_empty_group_assigned_when_assemble_filtered_then_trimmed`（menuIds 含 GROUP 但无可见子 → 被裁）
  - `given_dangling_divider_when_assemble_filtered_then_trimmed`（menuIds={divider} 无内容兄弟 → 被裁；menuIds={menuA,divider,menuB} → divider 保留）
  - `given_admin_view_when_assemble_then_no_trim`（menuIds=null → 空 GROUP/DIVIDER 保留）
  - 用例直接 `new AdminMenu(...)` 构造（反射设 id），断言树形。
  示例：
```java
@Test
void given_filtered_leaf_without_parent_when_assemble_then_ancestor_chain_completed() {
    AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
    AdminMenu group = menu("g", null, MenuType.GROUP, 1L, 0, 2L);
    AdminMenu leaf = menu("leaf", "/x", MenuType.MENU, 2L, 0, 3L);
    List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, group, leaf), Set.of(3L));
    assertThat(roots).hasSize(1);
    assertThat(roots.get(0).getId()).isEqualTo(1L);          // root 自动补回
    assertThat(roots.get(0).getChildren()).hasSize(1);       // group
    assertThat(roots.get(0).getChildren().get(0).getChildren()).hasSize(1); // leaf
}
```

- [ ] **Step 2: 运行验证失败** — `mvn test -Dtest=MenuTreeAssemblerTest` → 编译失败（类不存在）。

- [ ] **Step 3: 实现 `MenuTreeAssembler`** —
```java
package com.aieducenter.admin.application;

import java.util.*;
import cn.hutool.core.collection.CollUtil;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;

/** 菜单树组装（纯函数）：排序 + （过滤视图下）祖先链补全 + 裁剪空 GROUP / 悬空 DIVIDER。 */
public final class MenuTreeAssembler {
    private MenuTreeAssembler() {}

    /**
     * @param allMenus 全部菜单（未组装父子）
     * @param menuIds  null=管理视图（全量、仅排序、不裁）；非 null=角色过滤（祖先补全+排序+裁剪）
     */
    public static List<AdminMenu> assemble(List<AdminMenu> allMenus, Set<Long> menuIds) {
        Map<Long, Long> parentOf = new HashMap<>();
        for (AdminMenu m : allMenus) parentOf.put(m.getId(), m.getParentId());

        Set<Long> keptIds = (menuIds == null) ? null : expandAncestors(menuIds, parentOf);

        Map<Long, AdminMenu> menuMap = new LinkedHashMap<>();
        for (AdminMenu m : allMenus) {
            if (keptIds == null || keptIds.contains(m.getId())) {
                m.setChildren(CollUtil.newArrayList()); // 防御性重置，避免跨调用累积
                menuMap.put(m.getId(), m);
            }
        }
        List<AdminMenu> roots = CollUtil.newArrayList();
        for (AdminMenu m : menuMap.values()) {
            Long pid = m.getParentId();
            if (pid == null || !menuMap.containsKey(pid)) roots.add(m);
            else menuMap.get(pid).addChild(m);
        }
        sortTree(roots);
        return (menuIds == null) ? roots : prune(roots);
    }

    private static Set<Long> expandAncestors(Set<Long> menuIds, Map<Long, Long> parentOf) {
        Set<Long> kept = new HashSet<>(menuIds);
        for (Long id : menuIds) {
            Long p = parentOf.get(id);
            while (p != null && kept.add(p)) p = parentOf.get(p);
        }
        return kept;
    }

    private static void sortTree(List<AdminMenu> nodes) {
        nodes.sort(Comparator.comparingInt(AdminMenu::getSortOrder).thenComparing(AdminMenu::getId));
        for (AdminMenu n : nodes) sortTree(n.getChildren());
    }

    /** 自底向上裁剪：MENU 保留；GROUP 需有存活子；DIVIDER 需有存活内容兄弟。 */
    private static List<AdminMenu> prune(List<AdminMenu> nodes) {
        for (AdminMenu n : nodes) n.setChildren(prune(n.getChildren()));
        boolean hasContentSibling = nodes.stream().anyMatch(
            n -> n.getType() == MenuType.MENU
              || (n.getType() == MenuType.GROUP && !n.getChildren().isEmpty()));
        List<AdminMenu> surviving = CollUtil.newArrayList();
        for (AdminMenu n : nodes) {
            switch (n.getType()) {
                case MENU -> surviving.add(n);
                case GROUP -> { if (!n.getChildren().isEmpty()) surviving.add(n); }
                case DIVIDER -> { if (hasContentSibling) surviving.add(n); }
            }
        }
        return surviving;
    }
}
```

- [ ] **Step 4: 运行单测** — `mvn test -Dtest=MenuTreeAssemblerTest` PASS。

- [ ] **Step 5: 改 `MenuManagementAppService.findTree` 委托 assembler** —
```java
public List<MenuResponse> findTree(Set<Long> menuIds) {
    List<AdminMenu> roots = MenuTreeAssembler.assemble(menuRepository.findAll(), menuIds);
    return adminMenuMapper.convertList(roots);
}
```
（移除原手写 HashMap 组装逻辑。`findTree()`/`findTree(null)` 语义不变。）

- [ ] **Step 6: 重写集成测试孤儿断言** — `AdminMenuTreeTest.given_menu_tree_when_filter_exclude_parent_but_include_child_then_child_not_appear` 改名为 `..._then_ancestor_chain_completed`，断言改为：过滤 `{menu2Id}`（仅子）→ 返回 `[menu1(含 menu2 子)]`，子不再丢弃、父自动补全。同时新增集成用例：
  - `given_emptyGroupOnly_assigned_when_filter_then_group_trimmed`
  - `given_divider_between_menus_when_filter_then_kept` / `given_dangling_divider_when_filter_then_trimmed`
  - `given_admin_view_when_findTree_then_emptyGroupAndDivider_kept`（menuIds=null → /menus 不裁）
  这些用例用 T2 的 `create(new CreateMenuCommand(..., type))` 建 GROUP/DIVIDER（故 T3 依赖 T2）。

- [ ] **Step 7: 运行集成 + 全量** — `mvn test -Dtest=AdminMenuTreeTest,MenuTreeAssemblerTest,MenuManagementAppServiceTest` → PASS。最后 `mvn verify`（含 ArchUnit 架构守护）→ PASS。

- [ ] **Step 8: Commit**
```bash
git add -A
git commit -m "fix(admin): T3 消费侧菜单树排序 + 祖先链补全 + 裁剪

findTree 抽出纯函数 MenuTreeAssembler：
- 两端点均按 sortOrder(再按 id) 排序（修 HashMap 乱序）；
- /auth/current（角色过滤）：祖先分组链补全（被分配叶子不孤儿）+ 裁空 GROUP + 裁悬空 DIVIDER；
- /menus（管理视图）：全量、不裁剪（管理员可编辑空分组/分隔线）。
重写原'只过滤子→空'集成断言为'祖先链补全'。

Closes #6"
```

---

## Self-Review

**1. 工单 AC 覆盖：**
- T1：type 字段 ✅(Task1 Step3)；整数 code ✅(序列化断言)；种子=MENU ✅(探索#8 + 断言)；不改树行为 ✅(T1 不动 findTree)。
- T2：command 接受 type 可选 ✅(Step7)；MENU 缺 path→ADMIN_014_3 ✅(Step3/4)；GROUP/DIVIDER path 归 null ✅(applyTypeAndPath)；聚合内强制 ✅(构造/updateDetails/setType)；新错误码 ✅(Step3)。
- T3：两端点排序 ✅；祖先链补全 ✅；空 GROUP 裁 ✅；悬空 DIVIDER 裁 ✅；管理视图不裁 ✅；重写孤儿断言 ✅(Step6)。全部命中。

**2. 占位符扫描：** 无 TBD/TODO；关键代码（聚合不变量、Assembler 算法、错误码、测试）均给出完整代码。

**3. 类型一致性：** `MenuResponse.type()`、`command.type()`、`AdminMenu.updateDetails(..., MenuType)`、`MenuTreeAssembler.assemble(List,Set)` 在各任务间签名一致；`AdminMessage.MENU_TYPE_PATH_MISMATCH` 名称统一。

**4. 风险/注意：**
- 次级构造函数策略使 ~40 处旧 arity 调用点零改动，仅 `given_path_boundary` 与孤儿集成测试需重写（已列入步骤）。
- `AdminPerformanceTest`（100 菜单，path 合法）默认 MENU 不受影响。
- 去掉 path `@Setter` 后确认唯一调用点 `MenuManagementAppService:131` 已在 Step8 移除。
- 整数序列化是框架保证；T1 用注入 ObjectMapper 的 @SpringBootTest 断言兜底验证。
