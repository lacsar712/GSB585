# OrderService 订单状态流转与权限安全分析

> 分析对象：`backend/src/main/java/com/citycourier`
> 主要文件：[OrderService.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java)、[OrderController.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/OrderController.java)、[SecurityConfig.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java)、[JwtAuthenticationFilter.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java)、[JwtTokenProvider.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java)

---

## 一、核心逻辑解析

### 1.1 角色与状态枚举

- 角色 `RoleType`：`CUSTOMER`、`RIDER`、`DISPATCHER`、`ADMIN`。
- 订单状态 `OrderStatus`：`CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`，以及终态 `CANCELLED`。

### 1.2 状态机定义

[OrderService.java#L42-L51](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L42-L51) 通过 `EnumMap<OrderStatus, Set<OrderStatus>>` 静态声明合法转移：

| 当前状态 | 可转入的状态 |
| --- | --- |
| `CREATED` | `ASSIGNED`、`CANCELLED` |
| `ASSIGNED` | `PICKED_UP`、`CANCELLED` |
| `PICKED_UP` | `IN_TRANSIT`、`CANCELLED` |
| `IN_TRANSIT` | `DELIVERED`、`CANCELLED` |
| `DELIVERED` | （终态，空集） |
| `CANCELLED` | （终态，空集） |

`updateStatus`（[L220-L251](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L220-L251)）核心校验：

1. 若当前为 `DELIVERED` 或 `CANCELLED` 直接拒绝；
2. 若调用者为 `RIDER`，须为订单当前所属骑手；
3. 通过 `TRANSITIONS.get(...)` 校验目标状态合法性；
4. 进入 `DELIVERED` 时强制要求 `actualFee` 不为空。

### 1.3 接口权限矩阵（基于 `@PreAuthorize`）

| 服务方法 | 注解角色限制 | 业务侧附加校验 |
| --- | --- | --- |
| `create` [L54](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L54) | `CUSTOMER`,`ADMIN` | 收发件人/电话/地址不可重复 |
| `update` [L80](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L80) | `CUSTOMER`,`ADMIN` | CUSTOMER 仅本人；状态须为 `CREATED` |
| `delete` [L111](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L111) | `CUSTOMER`,`ADMIN` | CUSTOMER 仅本人；状态须为 `CREATED` |
| `list` [L174](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L174) | `ADMIN`,`DISPATCHER`,`RIDER`,`CUSTOMER` | `buildSpec` 中按角色注入 `createdBy`/`rider` 过滤 |
| `getByTrackingNo` [L186](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L186) | 同上 | CUSTOMER 仅本人创建；RIDER 仅自身订单 |
| `assign` [L202](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L202) | `ADMIN`,`DISPATCHER` | 仅 `CREATED` 状态可派单 |
| `updateStatus` [L221](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L221) | `ADMIN`,`DISPATCHER`,`RIDER` | RIDER 必须为该单骑手；检查转移合法性 |
| `stats` [L254](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L254) | `ADMIN`,`DISPATCHER` | — |

### 1.4 列表数据隔离（`buildSpec`）

[OrderService.java#L271-L301](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L271-L301) 中：

- `CUSTOMER` 强制 `createdBy.id = current.id`；
- `RIDER` 强制 `rider.id = current.id`；
- `DISPATCHER` / `ADMIN` 不附加用户级限制；
- `keyword` 走 JPA Criteria 参数化绑定，不存在 SQL 注入。

---

## 二、潜在风险与越权点

### 2.1 `updateStatus` 缺乏「角色—目标状态」绑定（关键）

[L220-L251](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L220-L251) 中，`@PreAuthorize` 允许 `ADMIN`、`DISPATCHER`、`RIDER` 三类角色调用，但状态机本身并未把动作绑定到具体角色。后果：

- **`DISPATCHER` 越权完成配送**：派单员在合法转移表内可直接将订单从 `ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED` 推进，绕过骑手的实际作业；甚至可在结单时随意填写 `actualFee` 改写收入数据。
- **`RIDER` 越权取消订单**：状态机允许任何中间态转入 `CANCELLED`。当前代码未禁止 `RIDER` 调用 `CANCELLED`，骑手可单方取消已派给自己的订单，对客户履约形成业务风险。
- **`CUSTOMER` 无法取消自己的订单**：业务通常允许客户在 `CREATED` 取消，但 `updateStatus` 的注解未含 `CUSTOMER`，相关取消能力只能由调度员/管理员执行，与权限矩阵存在不一致。

**建议**：用「角色 × (源态→目标态)」白名单替代单一全局 `TRANSITIONS`；例如 `RIDER` 仅可推进 `PICKED_UP / IN_TRANSIT / DELIVERED`；`DISPATCHER` 仅可派单与取消；`CUSTOMER` 仅可在 `CREATED` 取消。

### 2.2 `assign` 未校验骑手账号 `enabled`

[L209-L211](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L209-L211) 仅过滤 `role == RIDER`，未校验 `enabled`。被禁用骑手仍可被指派，造成派单异常。

### 2.3 `getByTrackingNo` 中 `DISPATCHER` 未做范围限制

[L186-L199](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L186-L199) 对 CUSTOMER、RIDER 强制做了所属过滤，但 `DISPATCHER`/`ADMIN` 可枚举任何运单号。`trackingNo` 由 `CC + yyyyMMddHHmm + 4位随机数`（[L310-L314](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L310-L314)）构成，熵较低，调度员/管理员视角下属于业务范围内可接受，但若未来开放该接口给低权限端，会成为 IDOR 入口。

### 2.4 `updateStatus` 对 `DISPATCHER`/`ADMIN` 未做归属限制

[L230-L234](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L230-L234) 仅约束 `RIDER` 必须为本单骑手；`DISPATCHER` 可对任何订单的状态进行推进，组合 §2.1 即可绕过实际配送流程伪造结单。

### 2.5 `actualFee` 缺少范围与数值校验

[L240-L247](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L240-L247) 仅判断非空，未校验非负、上限或与 `estimatedFee` 的偏差。若上游 DTO 校验缺失，骑手可填写负数或异常值。

### 2.6 `CANCELLED` 不要求附加理由/审批

终态转入 `CANCELLED` 没有原因与审计字段，对应业务侧无法溯源恶意取消行为；属业务层面缺陷，安全影响为弱审计能力。

### 2.7 `update` 仅按 `CREATED` 限制，非创建者也可被 `ADMIN` 修改

[L80-L108](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L80-L108) 中 `ADMIN` 不做归属校验，可改写任意运单的发收件人信息，而修改不会触发审计日志（仅 `log.info` 记录 id/操作人）。建议补充审计字段或敏感字段冻结。

### 2.8 异常信息泄露

[L237](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L237) 在错误信息中拼接当前/目标状态，调用方可借此枚举枚举值；非高危，但建议返回标准错误码。

### 2.9 越权操作不会返回 403

业务侧违规通过抛 `BizException`（见 [L86](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L86)、[L117](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L117)、[L193](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L193) 等），由 `GlobalExceptionHandler` 转为业务错误，HTTP 通常仍是 200/400，而非语义化的 403。这与 `@PreAuthorize` 抛出的 `AccessDeniedException` 不一致，可能影响前端拦截策略。

---

## 三、JWT 与 `@PreAuthorize` 的协作机制

### 3.1 全链路调用顺序

1. **登录获取 Token**：`AuthService.login`（[AuthService.java#L31-L44](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/AuthService.java#L31-L44)）经 `AuthenticationManager` + `DaoAuthenticationProvider`（[SecurityConfig.java#L47-L52](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L47-L52)）完成密码校验后，由 `JwtTokenProvider.createToken`（[JwtTokenProvider.java#L38-L50](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L38-L50)）签发 HS256 JWT，`subject=username`、`claim.role=角色枚举`。

2. **每次请求过滤**：`SecurityConfig` 中 `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`（[L41](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L41)）将 `JwtAuthenticationFilter` 插入过滤器链。Filter 取出 `Authorization: Bearer <token>`，调用 `JwtTokenProvider.validate` 校验签名/过期，再用 `UserDetailsService.loadUserByUsername` 重新加载用户（保证角色以 DB 为准），构造 `UsernamePasswordAuthenticationToken` 写入 `SecurityContextHolder`（[JwtAuthenticationFilter.java#L32-L45](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L32-L45)）。

3. **角色注入**：`CustomUserDetails.getAuthorities` 返回 `ROLE_<RoleType>`（[CustomUserDetails.java#L31-L33](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/CustomUserDetails.java#L31-L33)）。这是 `hasAnyRole(...)` 能匹配的关键——`hasAnyRole('ADMIN')` 会被 Spring 内部前缀化为 `ROLE_ADMIN` 与之比较。

4. **方法级鉴权**：`SecurityConfig` 上 `@EnableMethodSecurity`（[L21](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L21)）启用 AOP 代理；`OrderController` 调用到 `OrderService` 的 `@PreAuthorize` 方法时，Spring AOP 切面读取 `SecurityContextHolder.getContext().getAuthentication()`，将其权限集合与 SpEL 表达式进行匹配，匹配失败抛 `AccessDeniedException`，由 `JwtAuthenticationEntryPoint`/`AccessDeniedHandler` 链返回 401/403。

5. **业务双重校验**：`OrderService` 内部还会通过 `authService.getCurrentUserEntity()`（[AuthService.java#L51-L54](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/AuthService.java#L51-L54)）从 SecurityContext 中重读用户实体，做行级（owner/rider）数据归属校验，与方法级注解形成「角色门槛 + 资源归属」双层防御。

### 3.2 信任来源与一致性

- **角色信息以 DB 为准**：尽管 token 中含 `role` claim，`JwtAuthenticationFilter` 仅以 `subject` 为索引重新查询 DB，`@PreAuthorize` 实际比较的是 `userDetails.getAuthorities()` 而非 token claim，因此用户被改角色或禁用后，新签发的请求立即失效（前提是禁用账户在 `loadUserByUsername` 中被处理；当前 `enabled` 字段作为 `UserDetails.isEnabled()` 由 Spring Security 强制校验）。
- **签名与有效期**：`JwtTokenProvider.init`（[L27-L36](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L27-L36)）强制 HS256 + 至少 32 字节密钥，规避了 `alg=none` 与弱密钥攻击；`validate` 通过 `parseClaimsJws` 自动校验 `exp`。
- **无状态会话**：`SessionCreationPolicy.STATELESS` + `csrf.disable()`（[SecurityConfig.java#L32-L33](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L32-L33)）适配纯 JWT 场景。

### 3.3 协作上的薄弱点

1. **未校验 `iss`/`aud`**：[JwtTokenProvider.java#L65-L67](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L65-L67) 仅校验签名与 `exp`，缺少 issuer/audience 约束。如果未来同密钥被多服务复用，可能被跨服务重放。
2. **缺少 Token 注销/黑名单**：登出/改密后旧 token 仍在 `expireSeconds` 内有效，结合 §3.2 的 DB 重查仅能拦截「角色变更/禁用」，不能拦截「正常用户但需强制下线」场景。
3. **`Authorization` 解析未做大小写/重复 header 容错**：[JwtAuthenticationFilter.java#L32-L34](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L32-L34) 仅识别 `Bearer ` 前缀大小写敏感格式，属规范实现但需注意客户端一致性。
4. **Token 失效仅 `log.warn`**：无效 Token 时未打断请求，转而以匿名身份继续走链路。由于 `SecurityConfig` 中 `anyRequest().authenticated()`，最终仍会被 `AuthenticationEntryPoint` 拦截为 401，但若未来某条路径被加入 `permitAll`，则可能允许伪造 token 通过。

---

## 四、风险汇总（可执行修复方向）

| 等级 | 位置 | 问题 | 修复建议 |
| --- | --- | --- | --- |
| 高 | `OrderService.updateStatus` | `DISPATCHER` 可绕过骑手推进至 `DELIVERED` 并写入 `actualFee` | 引入「角色 × 状态转移」白名单，结单仅允许 `RIDER`（且为该单骑手）或 `ADMIN` |
| 高 | `OrderService.updateStatus` | `RIDER` 可单方将订单转为 `CANCELLED` | 取消动作仅放给 `DISPATCHER`/`ADMIN`，或要求 `CREATED` 状态下的客户取消 |
| 中 | `OrderService.update`/`delete` | `ADMIN` 修改/删除任意订单无审计 | 增加操作日志表与敏感字段冻结策略 |
| 中 | `OrderService.assign` | 未校验骑手 `enabled` | `findByUsername` 后追加 `u.getEnabled()` 校验 |
| 中 | `OrderService.updateStatus` | `actualFee` 缺范围校验 | DTO 注解 `@DecimalMin("0")` + 上限校验 |
| 低 | `JwtTokenProvider` | 未校验 `iss`/`aud`、无黑名单 | 增加 issuer/audience 与登出黑名单或短 TTL + Refresh Token |
| 低 | `OrderService` 错误信息 | 暴露状态枚举 | 统一错误码，避免外露内部状态 |

---

> 结论：状态流转表与 `@PreAuthorize` 共同构筑了基础权限框架，JWT 过滤器与方法级注解之间通过 `SecurityContextHolder` + `ROLE_` 前缀完成衔接；但 `updateStatus` 缺少「角色—动作」细粒度绑定，是当前最关键的越权风险面，建议优先修复。
