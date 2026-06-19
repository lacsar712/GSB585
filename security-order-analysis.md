# 城市跑腿系统 — 订单服务安全与权限分析报告

---

## 一、核心逻辑概述

### 1.1 角色模型

系统定义了四种角色，枚举位于 [RoleType.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/RoleType.java#L1-L8)：

| 角色 | Spring Security 前缀 | 业务含义 |
|------|---------------------|---------|
| `ADMIN` | `ROLE_ADMIN` | 管理员，最高权限 |
| `DISPATCHER` | `ROLE_DISPATCHER` | 调度员，负责派单 |
| `RIDER` | `ROLE_RIDER` | 骑手，负责配送 |
| `CUSTOMER` | `ROLE_CUSTOMER` | 客户，发件方 |

初始账号在 [DataInitializer.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/DataInitializer.java#L27-L30) 中创建（默认密码均为 `password123`）。

### 1.2 订单实体

订单实体 [OrderEntity.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/OrderEntity.java#L1-L77) 关键字段：

- `id`：自增主键（可枚举）
- `trackingNo`：运单号，格式 `CC` + `yyyyMMddHHmm` + 4位随机数
- `status`：当前状态，枚举见 [OrderStatus.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/OrderStatus.java#L1-L10)
- `createdBy`（ManyToOne → User）：发件客户
- `rider`（ManyToOne → User）：被指派的骑手
- `estimatedFee` / `actualFee`：预估费用 / 实际费用

---

## 二、订单状态流转详解

### 2.1 状态机定义

状态流转表在 [OrderService.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L42-L51) 中以 `EnumMap` 静态初始化：

```
CREATED    ──→ ASSIGNED     (派单，由 assign() 方法执行)
CREATED    ──→ CANCELLED    (取消)
ASSIGNED   ──→ PICKED_UP    (骑手取件)
ASSIGNED   ──→ CANCELLED    (取消)
PICKED_UP  ──→ IN_TRANSIT   (配送中)
PICKED_UP  ──→ CANCELLED    (取消)
IN_TRANSIT ──→ DELIVERED    (已送达，需 actualFee)
IN_TRANSIT ──→ CANCELLED    (取消)
DELIVERED  ──→ (终态，无出站转换)
CANCELLED  ──→ (终态，无出站转换)
```

### 2.2 状态流转校验逻辑

在 [OrderService.java:222-251](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L222-L251) 的 `updateStatus()` 方法中：

1. **终态保护**：若当前状态为 `DELIVERED` 或 `CANCELLED`，直接拒绝。
2. **骑手归属校验**：仅对 `RIDER` 角色检查订单是否属于本人（`rider.id == currentUser.id`）。
3. **合法转换校验**：通过 `TRANSITIONS.get(current).contains(target)` 查表判断。
4. **送达费用校验**：目标状态为 `DELIVERED` 时 `actualFee` 不可为空。
5. 若 `actualFee` 非空则一并写入（不限于 DELIVERED 状态）。

---

## 三、JWT 与 @PreAuthorize 协作机制

### 3.1 认证流程（JWT 签发）

登录入口位于 [AuthController.java:19-22](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/AuthController.java#L19-L22)，核心流程在 [AuthService.java:31-44](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/AuthService.java#L31-L44)：

1. 客户端提交用户名/密码至 `POST /api/auth/login`。
2. `AuthenticationManager` 通过 `DaoAuthenticationProvider` 调用 `CustomUserDetailsService.loadUserByUsername()` 从数据库加载用户，使用 `BCryptPasswordEncoder` 比对密码。
3. 认证成功后，[JwtTokenProvider.createToken()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L38-L50) 生成 JWT：
   - `sub`（subject）= username
   - `role` claim = 角色名（如 `ADMIN`）
   - 使用 HS256 对称签名，密钥来自环境变量 `JWT_SECRET`（长度≥32字符）
   - 默认有效期 7200 秒（2小时）

### 3.2 请求鉴权流程（JWT 校验 → @PreAuthorize）

```
HTTP 请求到达
    │
    ▼
[SecurityConfig] 配置
  - anyRequest().authenticated()       (除 login/health 外所有请求需认证)
  - SessionCreationPolicy.STATELESS    (无状态，不创建 HttpSession)
  - 添加 JwtAuthenticationFilter 于 UsernamePasswordAuthenticationFilter 之前
    │
    ▼
[JwtAuthenticationFilter.doFilterInternal()]
  1. 从 Authorization: Bearer <token> 提取 token
  2. jwtTokenProvider.validate(token)：验签 + 过期检查
  3. jwtTokenProvider.getUsername(token)：从 sub 取出 username
  4. userDetailsService.loadUserByUsername(username)：↓↓↓
     ★ 从数据库重新加载用户及角色，不使用 JWT 内的 role claim 做授权 ★
  5. 构造 UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
     - getAuthorities() 返回 [new SimpleGrantedAuthority("ROLE_" + role.name())]
  6. 放入 SecurityContextHolder.getContext()
    │
    ▼
Spring Security 过滤器链继续
    │
    ▼
[Controller 方法被调用] → 调用 Service 方法
    │
    ▼
[@EnableMethodSecurity 激活的 MethodSecurityInterceptor]
  - 拦截标注 @PreAuthorize 的 Service 方法
  - 解析 SpEL 表达式，如 hasAnyRole('ADMIN','DISPATCHER')
  - 通过 SecurityContextHolder 中 Authentication 对象的 getAuthorities() 比对
  - 匹配失败 → 抛出 AccessDeniedException → 403
    │
    ▼
[Service 方法内部手工业务校验]
  - 如：CUSTOMER 只能操作自己创建的订单
  - 如：RIDER 只能更新指派给自己的订单
```

### 3.3 分层安全模型总结

本系统采用**两层权限控制**：

| 层级 | 机制 | 作用 | 位置 |
|------|------|------|------|
| **第一层：URL 级认证** | `SecurityFilterChain` 的 `authorizeHttpRequests` | 确保请求已认证（非匿名） | [SecurityConfig.java:35-38](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L35-L38) |
| **第二层：方法级角色鉴权** | `@PreAuthorize` + `@EnableMethodSecurity` | 基于角色（Role）粗粒度控制接口准入 | Service 方法注解 |
| **第三层（手工）：业务归属校验** | `if (current.getRole() == ...)` 硬编码 | 基于资源所有权的细粒度校验 | Service 方法体内 |

> **关键设计决策**：JWT 中的 `role` claim 仅作数据携带，**不参与授权决策**。每次请求都从数据库重新加载用户及其角色（[JwtAuthenticationFilter.java:37](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L37)）。好处是角色变更实时生效；坏处是每次请求有一次数据库查询，且禁用状态未校验（见风险 R3）。

---

## 四、各角色接口权限矩阵

以下矩阵汇总了 [OrderService.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java) 各方法上的 `@PreAuthorize` 与方法内归属校验：

| 操作 | Controller 端点 | @PreAuthorize | ADMIN | DISPATCHER | RIDER | CUSTOMER | 业务归属校验 |
|------|----------------|---------------|:-----:|:----------:|:-----:|:--------:|------------|
| 创建订单 | `POST /api/orders` | CUSTOMER, ADMIN | ✅ | ❌ | ❌ | ✅ | createdBy = 当前用户（隐式） |
| 编辑订单 | `PUT /api/orders/{id}` | CUSTOMER, ADMIN | ✅ 任意订单 | ❌ | ❌ | ✅ 仅本人 | 仅 CREATED 状态可编辑 |
| 删除订单 | `DELETE /api/orders/{id}` | CUSTOMER, ADMIN | ✅ 任意订单 | ❌ | ❌ | ✅ 仅本人 | 仅 CREATED 状态可删除（物理删除） |
| 查询订单列表 | `GET /api/orders` | ADMIN, DISPATCHER, RIDER, CUSTOMER | ✅ 全部 | ✅ 全部 | ✅ 仅本人负责的 | ✅ 仅本人创建的 | 通过 JPA Specification 过滤 |
| 运单号查询 | `GET /api/orders/tracking/{trackingNo}` | ADMIN, DISPATCHER, RIDER, CUSTOMER | ✅ 全部 | ✅ 全部 | ✅ 仅本人负责的 | ✅ 仅本人创建的 | 方法内显式判断 |
| 派单 | `POST /api/orders/{id}/assign` | ADMIN, DISPATCHER | ✅ | ✅ | ❌ | ❌ | 仅 CREATED 状态可派单；验证被指派人为 RIDER |
| 更新订单状态 | `POST /api/orders/{id}/status` | ADMIN, DISPATCHER, RIDER | ✅ | ✅ | ✅ 仅本人订单 | ❌ | 按 TRANSITIONS 表校验；DELIVERED 需 actualFee |
| 统计面板 | `GET /api/orders/stats` | ADMIN, DISPATCHER | ✅ | ✅ | ❌ | ❌ | 无额外校验 |

---

## 五、安全风险与潜在问题分析

### 🔴 R1【严重】可绕过派单流程创建无骑手的"孤儿订单"

**位置**：[OrderService.java:202-251](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L202-L251)

**问题**：`assign()` 方法在将状态改为 `ASSIGNED` 时同时设置了 `rider` 字段，但 `updateStatus()` 方法允许 `CREATED → ASSIGNED` 的合法转换（`TRANSITIONS` 表定义了此转换），且 `updateStatus()` 完全不操作 `rider` 字段。

**攻击路径**：DISPATCHER 或 ADMIN 调用 `POST /api/orders/{id}/status`，传入 `{"targetStatus":"ASSIGNED"}`，订单进入 ASSIGNED 状态但 `rider = null`。
**后果**：
1. 该订单无骑手负责；
2. `assign()` 检查 `status != CREATED` 后无法再补派单；
3. RIDER 更新状态时 `entity.getRider() == null` 触发异常，永远无法推进；
4. 订单进入不可恢复的死锁状态。

### 🔴 R2【严重】DISPATCHER 可执行骑手专属物理操作（职责越权）

**位置**：[OrderService.java:221-251](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L221-L251)

**问题**：`updateStatus()` 的 `@PreAuthorize` 包含 `DISPATCHER`，方法内仅对 `RIDER` 做归属校验，**对 ADMIN/DISPATCHER 没有任何额外的操作类型限制**。这意味着调度员可以：
- 将任意订单标记为 `PICKED_UP`（伪装骑手取件）
- 将任意订单标记为 `IN_TRANSIT`
- 将任意订单标记为 `DELIVERED`（同时可设置任意金额的 `actualFee`）

**后果**：调度员可伪造配送全流程、任意篡改费用，缺乏职责分离。

### 🔴 R3【高】被禁用用户的 JWT 仍可继续使用至过期

**位置**：[JwtAuthenticationFilter.java:35-41](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L35-L41)

**问题**：过滤器在加载 `UserDetails` 后直接构造 Authentication 并放入 SecurityContext，**未检查 `userDetails.isEnabled()`**。

**后果**：管理员若禁用了某骑手/调度员账号，该用户持有的有效 JWT 在 2 小时内仍可调用所有接口，无法即时生效拉黑。

### 🔴 R4【高】RIDER 可在取件后取消订单（货物侵吞风险）

**位置**：[OrderService.java:44-51](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L44-L51)

**问题**：`TRANSITIONS` 表允许 `PICKED_UP → CANCELLED` 和 `IN_TRANSIT → CANCELLED`，且 `updateStatus()` 中对 RIDER 的校验仅检查归属，没有状态阶段的角色限制。

**后果**：骑手取件后（甚至配送途中）可单方面取消订单，可能导致货物丢失、客户投诉；无审批流、无理由记录、无通知机制。

### 🟠 R5【中】CUSTOMER 无取消已派单订单的能力，只能删除（物理删除）

**位置**：[OrderService.java:110-125](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L110-L125)

**问题**：
- CUSTOMER 角色不在 `updateStatus()` 的 @PreAuthorize 中，无法逻辑取消订单；
- `delete()` 使用 `orderRepository.delete()` 执行**物理删除**，且仅当 `status == CREATED`；
- 一旦订单被派单（ASSIGNED 及之后），客户无法主动取消。

**后果**：客户无法在骑手取件前取消已派单订单（如发件信息有误）；物理删除导致审计数据丢失。

### 🟠 R6【中】actualFee 可在非 DELIVERED 状态被设置或覆盖

**位置**：[OrderService.java:244-247](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L244-L247)

**问题**：
```java
entity.setStatus(request.getTargetStatus());
if (request.getActualFee() != null) {
    entity.setActualFee(request.getActualFee());
}
```
只要 `actualFee` 非空，任何状态转换（PICKED_UP、IN_TRANSIT、CANCELLED）都可以写入/覆盖 `actualFee`，仅有 `@DecimalMin("0.0")` 校验。

**后果**：
1. 骑手可在取件时就设置费用，送达时再传不同值覆盖；
2. 取消的订单仍可携带费用记录；
3. 没有上限校验，可设置畸高费用（如 999999.00）。

### 🟠 R7【中】退出登录端点形同虚设（JWT 无法主动失效）

**位置**：[AuthController.java:24-27](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/AuthController.java#L24-L27)

**问题**：`POST /api/auth/logout` 被配置为需认证（非 permitAll），但方法体直接返回成功，未做任何 token 失效操作。由于系统使用无状态 JWT，token 在过期前一直有效。

**后果**：用户以为已退出，token 仍然可用；在共用设备上存在 token 被窃取使用的风险。

### 🟠 R8【中】订单 ID 自增、运单号可预测（可枚举风险）

**位置**：[OrderEntity.java:18-20](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/OrderEntity.java#L18-L20)、[OrderService.java:310-314](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L310-L314)

**问题**：
- 主键 `id` 为数据库自增，可遍历；
- 运单号 = `CC` + `yyyyMMddHHmm` + 4位随机数（1000-9999，共 9000 种），按分钟可被暴力枚举出有效运单号。

**后果**：攻击者结合 `getByTrackingNo()` 接口（需 RIDER/CUSTOMER 身份）可枚举他人订单信息（CUSTOMER 有归属校验，但 RIDER 可枚举不属于自己的运单号？——归属校验已阻止）；但若配合其他角色泄露，信息暴露面较大。

### 🟡 R9【低】@PreAuthorize 注解放置层次不一致

**位置**：OrderService 全部注解在 Service 层；[AuthController.java:35](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/AuthController.java#L35) 的 `/api/auth/riders` 注解在 Controller 层；[OrderController.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/OrderController.java) 完全没有注解。

**后果**：维护风险——若后续直接在 Controller 中新增调用 Service 公共方法的端点，容易遗漏安全检查；建议统一在 Service 层做方法级权限控制。

### 🟡 R10【低】登录端点无暴力破解防护

**位置**：[AuthController.java:19-22](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/AuthController.java#L19-L22)

**问题**：无登录失败次数限制、无 CAPTCHA、无 IP 限流。

**后果**：在线暴力破解弱密码（如初始密码 `password123`）风险较高。

### 🟡 R11【低】ADMIN 编辑订单缺少操作审计

**位置**：[OrderService.java:81-108](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L81-L108)

**问题**：管理员可以编辑、删除任意 CREATED 订单，或通过 updateStatus 任意推进状态，但系统仅通过 `log.info` 记录，无持久化审计表，无法追溯"谁在什么时间改了什么字段"。

### 🟡 R12【低】无法重新派单

**位置**：[OrderService.java:203-218](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L203-L218)

**问题**：`assign()` 要求订单必须处于 `CREATED` 状态。若骑手无法接单、车辆故障等，调度员无法改派其他骑手（状态已为 `ASSIGNED` 时被拒绝）。此为业务缺陷而非直接安全漏洞，但在异常场景下可能迫使调度员通过越权方式操作。

---

## 六、改进建议

| 风险 | 建议措施 |
|------|---------|
| R1 | 在 `updateStatus()` 中显式禁止 `CREATED → ASSIGNED`（该转换只能由 `assign()` 执行）；或在状态机中移除该路径，改由 `assign()` 内部独立设置状态。 |
| R2 | 在 `updateStatus()` 中为非 RIDER 角色增加允许的目标状态白名单（如 DISPATCHER 仅能 `→ CANCELLED`）；更精细的做法是引入"角色×动作"映射表，区分调度操作与配送操作。 |
| R3 | 在 [JwtAuthenticationFilter.java:37](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L37) 加载 UserDetails 后增加 `if (!userDetails.isEnabled())` 判断，返回 401；同时考虑维护一份 token 黑名单（如 Redis）以支持主动失效。 |
| R4 | 限制 RIDER 只能正向推进状态（`→ PICKED_UP`、`→ IN_TRANSIT`、`→ DELIVERED`），取消操作需走客户/管理员审批流程；至少禁止 `PICKED_UP/IN_TRANSIT → CANCELLED` 对 RIDER 开放。 |
| R5 | 给 CUSTOMER 开放取消操作（可限制为 ASSIGNED 之前或加入审批）；改物理删除为逻辑删除（增加 `DELETED` 状态或 `deleted` 布尔字段）保留审计痕迹。 |
| R6 | 将 `actualFee` 的写入限制为仅目标状态为 `DELIVERED` 的分支；增加费用上限校验；可考虑与 `estimatedFee` 做合理性区间比对。 |
| R7 | 若需支持主动退出，可引入 JWT 黑名单（Redis/jti 记录）；若保持无状态，则应在前端清除 token 并在文档中明确服务端不主动失效；同时将 logout 端点加入 `permitAll()` 或返回 204 并删除客户端 cookie。 |
| R8 | 使用 UUID 或雪花算法代替自增 ID；增加运单号熵（如改用 ULID / NanoId）。 |
| R9 | 统一将 `@PreAuthorize` 注解放在 Service 层方法上（当前 OrderService 已如此），Controller 层仅做路由；考虑启用 `@EnableMethodSecurity(jsr250Enabled = true)` 或引入 `@Secured` 一致性风格。 |
| R10 | 增加登录失败计数、IP 限流（如 Bucket4j / Redis + Lua），或在反向代理层（Nginx / Gateway）配置限流。 |
| R11 | 引入审计日志（如 Spring Data JPA Auditing + `@CreatedBy`/`@LastModifiedBy`，或独立的 `order_audit_log` 表）记录关键字段变更。 |
| R12 | 新增 reassign（改派）端点，允许 DISPATCHER/ADMIN 在 `ASSIGNED/PICKED_UP` 阶段更换骑手，记入审计。 |

---

## 七、JWT 与 @PreAuthorize 协作机制总结（时序视图）

```
[客户端]                         [服务端]
   │                                │
   │  POST /api/auth/login          │
   │  {username, password}          │
   ├───────────────────────────────→│  AuthController.login()
   │                                │    → AuthService.login()
   │                                │      → AuthenticationManager.authenticate()
   │                                │        → DaoAuthenticationProvider
   │                                │          → CustomUserDetailsService.loadUserByUsername()
   │                                │          → BCryptPasswordEncoder.matches()
   │                                │      → JwtTokenProvider.createToken()
   │                                │        JWT: {sub: username, role: ADMIN, exp: now+2h}
   │  {token, userInfo}             │
   │←───────────────────────────────┤
   │                                │
   │  GET /api/orders               │
   │  Authorization: Bearer <token> │
   ├───────────────────────────────→│  SecurityFilterChain
   │                                │    JwtAuthenticationFilter.doFilterInternal()
   │                                │      1. extract Bearer token
   │                                │      2. jwtTokenProvider.validate(token)
   │                                │         ├─ verify HS256 signature
   │                                │         └─ check expiration
   │                                │      3. jwtTokenProvider.getUsername(token)
   │                                │      4. userDetailsService.loadUserByUsername(username)
   │                                │         ★ re-queries DB for current roles ★
   │                                │      5. new UsernamePasswordAuthenticationToken(
   │                                │           userDetails, null,
   │                                │           [SimpleGrantedAuthority("ROLE_ADMIN")])
   │                                │      6. SecurityContextHolder.getContext().setAuthentication(auth)
   │                                │    OrderController.list()
   │                                │      → OrderService.list()
   │                                │        @PreAuthorize("hasAnyRole(...)") evaluated by
   │                                │        MethodSecurityInterceptor using SecurityContext auth
   │                                │        → method body (business ownership checks)
   │  200 {data}                    │
   │←───────────────────────────────┤
```

**核心要点**：

1. **JWT 只承担"身份传递"职能，不承担"权限决策"职能。** 即使 JWT payload 中包含 `role` claim，授权时仍以数据库当前角色为准。
2. **无状态架构**：`SessionCreationPolicy.STATELESS` 意味着不依赖 HttpSession，每个请求独立认证。
3. **方法级安全由 Spring AOP 实现**：`@EnableMethodSecurity` 为每个标注 `@PreAuthorize` 的 Bean 创建代理，在方法调用前拦截并执行 SpEL 表达式。
4. **两层防御缺一不可**：URL 级 `authenticated()` 兜底防止匿名访问；方法级 `@PreAuthorize` 做角色准入；Service 体内手工 `if` 做资源级归属校验。三者协同，任何一环被突破仍有其他层级把关（当前 R1/R2/R3/R4 即为第二、三粒之间缝隙导致的问题）。
