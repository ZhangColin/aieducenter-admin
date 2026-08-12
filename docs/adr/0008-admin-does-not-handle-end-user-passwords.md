# admin 不处理终端用户密码（仅管账号状态 + 会话）

identity（[spec #66](https://github.com/ZhangColin/aieducenter-identity/issues/66)，对接见 admin [issue #49](https://github.com/ZhangColin/aieducenter-admin/issues/49)）为终端用户账号提供了三个密码端点：`POST /reset-password {newPassword}`（**admin 传入**新密码，即 admin 持有明文、可凭它登录该账号）、`POST /clear-password`、`POST /force-change-password {enable}`。

**决策**：admin-console **一个密码端点都不暴露**。账号管理 BFF（`account` 子包 / `AccountClient`）只消费 identity 的**账号状态 + 会话**类端点——搜索、管理详情、封号(disable)、解封(activate)、解锁(unlock)、强制下线(sessions/revoke)，共 6 个。密码生命周期（重置/清除/强制改密）**完全归 identity**（用户自务 + 验证流程）。

**为什么**：管理后台处理终端用户密码是**不规范的**（合规/安全）——运营人员持有或干预用户凭据本身就是风险面。与其逐个端点辨别"哪个密码操作安全"（如 `force-change-password` 并不让 admin 持明文），不如划一条干净界限：**密码生命周期整体归 identity，admin 只到"账号状态 + 会话"为止**。

**取舍**：放弃了 helpdesk 便利（用户被锁后、运营当场设临时密码口头告知）——该便利不值合规代价；正当的重置路径是用户经 identity 的验证流程自助完成。

**后果**：`AccountClient` / 账号控制器只接 identity 9 端点中的 6 个，未来工程师看到 identity 提供密码端点、**勿当遗漏去"补"进 admin**——要接须另立 ADR 推翻本条。identity 仍可按 #66 为其他消费方实现这三个密码端点；本 ADR 仅约束 admin 的消费边界。
