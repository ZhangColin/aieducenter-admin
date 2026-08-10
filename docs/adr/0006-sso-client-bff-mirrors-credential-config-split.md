# ADR-0006：admin BFF 镜像 app-registry SsoClient 凭证/配置拆分；不在配置 PUT 自动建凭证（issue #33）

- **状态**：Proposed（设计已 grill 定稿，待实现）
- **日期**：2026-08-10
- **关联**：[issue #33](https://github.com/ZhangColin/aieducenter-admin/issues/33)；下游 [app-registry ADR-0005](../../aieducenter-app-registry/docs/adr/0005-sso-client-config-credential-separation.md)（凭证/配置职责分离，commit `d9e1444`）；[CONTEXT.md](../../CONTEXT.md)「SSO 凭证 vs 配置」「重置 vs 轮换」术语

## 背景

app-registry #22（commit `d9e1444`）删除了管理流单端点 `createOrRotate`（`POST /api/app-registry/apps/{appId}/sso-clients`——创建与轮换同一动作，每次改配置都连带重发 `client_id` + `client_secret` 明文），拆成三个独立原语：

- **凭证** `POST .../sso-clients/credentials`（无 body）：无则创建、有则仅重置 `client_secret`，`client_id` **终身稳定**，一次性返明文。
- **配置 PUT** `PUT .../sso-clients`：整份替换 `redirectUris`/`postLogoutRedirectUris`/`scopes`/`grants`（两 URI 列表 `@NotEmpty`），不动凭证、不动 status，无 SsoClient → 404。
- **启停用** `PUT .../sso-clients/{enable,disable}`。

admin 现有 BFF 端点 `POST /api/admin/apps/{id}/sso-client`（body `{redirectUris, scopes, grants}`，一把梭）调用的正是**已删除的下游** → 405，必须适配。顺带：新契约到处有 `postLogoutRedirectUris`（PUT 里 `@NotEmpty`、GET/credentials 响应都返），而 admin 全链路 6 个 DTO **都没有**这字段。

设计分叉：admin 是镜像下游拆分、还是收成单 facade 内部编排？

## 决策

**admin 北向用独立端点镜像下游、纯透传**，外加一条安全护栏。

1. **两端点 + 启停用，1:1 镜像下游**（路径沿用 admin 现有单数连字符风格 `sso-client`）：
   - `POST /api/admin/apps/{id}/sso-client/credentials`（无 body）→ create-or-reset `client_secret`，一次性返明文 + 全量 SsoClient 视图（含 `postLogoutRedirectUris`，create 时空）。
   - `PUT /api/admin/apps/{id}/sso-client`（body 配置四件套）→ 整份替换、返无 secret 视图。
   - `PUT /api/admin/apps/{id}/sso-client/{enable,disable}`（无 body）。
   - 旧 `POST /api/admin/apps/{id}/sso-client`（带 body）**删除**——对 admin-web 是破坏性契约变更。
2. **不在配置 PUT 时自动建凭证**（核心护栏）：一次性明文 `client_secret` 必须被操作员**有意识地**看到并捕获。配置 PUT 在 SsoClient 未建时返 404，admin 翻译为专属码 `ADMIN_SSO_CLIENT_NOT_PROVISIONED`，前端据此弹"请先点开通"。首次开通由前端编排（凭证 → 配置）。"凭证已建、配置未 PUT"的 ACTIVE+空配置中间态被接受（继承 app-registry ADR-0005）。
3. **`postLogoutRedirectUris` 加入 admin 全链路**：配置命令、wire 请求、两个 wire 响应、`SsoClientCreatedResponse`、`AppDetailResponse.SsoClientInfo` + 详情 region 3 编辑框。
4. **语言**：`client_id` 终身稳定；"重置 secret" = 凭证接口；弃用"轮换(rotate)"措辞（全链路无处使用，apiKey 同为 reset 语义）。
5. **零业务判断、零持久化**：admin 不加 `@NotEmpty` 等校验（透传 app-registry），无 DB 迁移（纯透传，admin 不存 SsoClient）。

### 否决

- **单 facade 端点**（admin 内部编排凭证+配置）：把"生成 secret"与"存配置"耦合——要么每次存配置都连换 secret（代价与改一行配置不匹配），要么加 mode 标志位把刚拆掉的语义扯回；且违背 CONTEXT.md「admin 不做业务判断」原则。
- **配置 PUT 自动建凭证**（catch 404 → 调 credentials → retry PUT）：silent lost-secret——操作员永远拿不到首张 `client_secret`，它在用户没看见的情况下生成并丢失。正是本 ADR 要拦的陷阱。
- **admin 加 create-vs-reset discriminator**：下游本就不区分、前端凭调用前 `detail.ssoClient == null` 已能判断，加字段冗余；且勿用"返回的 redirectUris 是否为空"推断（reset 一个从没 PUT 过的 client 也是空，脆弱启发式）。

## 结果

- ✅ 契约清晰：四端点各司其职，与详情页"三区块各独立保存"布局吻合。
- ✅ 一次性明文 secret 总被显式捕获（凭证接口返、前端即时报给操作员）；`client_id` 稳定不扰动 identity 缓存与下游写死的配置。
- ⚠️ 代价：admin-web 破坏性契约变更（旧单端点删），前端须迁四端点；中间态编排责任落在前端（app-registry ADR-0005 已接受，identity 自 guard 空配置）。

## 参见

- 下游决策：[app-registry ADR-0005](../../aieducenter-app-registry/docs/adr/0005-sso-client-config-credential-separation.md)。
- 术语：[CONTEXT.md](../../CONTEXT.md)「SSO 凭证 vs 配置」「重置 vs 轮换」。
- 参照：admin 现有 `POST /api/admin/apps/{id}/api-key` 同为 create-or-reset 语义（无则生成、有则重置、一次性返明文）。
