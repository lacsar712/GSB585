# 订单服务安全分析报告

## 一、订单状态流转核心逻辑

### 1.1 状态枚举定义

订单共有 6 种状态，定义于 [OrderStatus.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/OrderStatus.java#L1-L10)：

- `CREATED` — 待接单（初始状态）
- `ASSIGNED` — 已派单
- `PICKED_UP` — 已取件
- `IN_TRANSIT` — 运输中
- `DELIVERED` — 已送达（终态）
- `CANCELLED` — 已取消（终态）

### 1.2 合法状态流转矩阵

状态机通过静态 `TRANSITIONS` 映射表约束，定义于 [OrderService.java#L42-L51](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L42-L51)：

| 当前状态    | 允许转换至                 |
|------------|---------------------------|
| CREATED    | ASSIGNED, CANCELLED       |
| ASSIGNED   | PICKED_UP, CANCELLED      |
| PICKED_UP  | IN_TRANSIT, CANCELLED     |
| IN_TRANSIT | DELIVERED, CANCELLED      |
| DELIVERED  | （终态，无出边）            |
| CANCELLED  | （终态，无出边）            |

**状态流转图：**

```
CREATED ──┬──► ASSIGNED ──► PICKED_UP ──► IN_TRANSIT ──► DELIVERED
          │        │             │              │
          └────────┴─────────────┴──────────────┴──► CANCELLED
```

### 1.3 各接口状态约束逻辑

| 接口方法 | 前置状态要求 | 代码位置 |
|---------|------------|---------|
| `create()` | 无（新建即 CREATED） | [OrderService.java#L53-L77](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L53-L77) |
| `update()` | 必须为 `CREATED` | [OrderService.java#L88-L90](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L88-L90) |
| `delete()` | 必须为 `CREATED` | [OrderService.java#L119-L121](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L119-L121) |
| `assign()` | 必须为 `CREATED` | [OrderService.java#L205-L207](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L205-L207) |
| `updateStatus()` | 禁止对 `DELIVERED`/`CANCELLED` 操作，且必须符合 TRANSITIONS 映射 | [OrderService.java#L226-L238](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L226-L238) |

---

## 二、各角色接口权限矩阵

角色枚举定义于 [RoleType.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/RoleType.java#L1-L8)，共 4 种角色：`ADMIN`（管理员）、`DISPATCHER`（调度员）、`RIDER`（骑手）、`CUSTOMER`（客户）。

### 2.1 方法级 `@PreAuthorize` 声明汇总

| Service 方法 | `@PreAuthorize` 表达式 | 允许角色 |
|-------------|----------------------|---------|
| `create()` | `hasAnyRole('CUSTOMER','ADMIN')` | CUSTOMER, ADMIN |
| `update()` | `hasAnyRole('CUSTOMER','ADMIN')` | CUSTOMER, ADMIN |
| `delete()` | `hasAnyRole('CUSTOMER','ADMIN')` | CUSTOMER, ADMIN |
| `list()` | `hasAnyRole('ADMIN','DISPATCHER','RIDER','CUSTOMER')` | 全部角色 |
| `getByTrackingNo()` | `hasAnyRole('ADMIN','DISPATCHER','RIDER','CUSTOMER')` | 全部角色 |
| `assign()` | `hasAnyRole('ADMIN','DISPATCHER')` | ADMIN, DISPATCHER |
| `updateStatus()` | `hasAnyRole('ADMIN','DISPATCHER','RIDER')` | ADMIN, DISPATCHER, RIDER |
| `stats()` | `hasAnyRole('ADMIN','DISPATCHER')` | ADMIN, DISPATCHER |

### 2.2 业务层数据隔离逻辑（`@PreAuthorize` 之外的二次校验）

`@PreAuthorize` 仅做粗粒度角色校验，细粒度的数据归属校验在方法体内以编程方式实现：

- **CUSTOMER 列表过滤**：`list()` 通过 `buildSpec()` 强制添加 `createdBy.id = currentUserId` 条件，只能看到自己创建的订单（[OrderService.java#L275-L276](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L275-L276)）。
- **RIDER 列表过滤**：`list()` 通过 `buildSpec()` 强制添加 `rider.id = currentUserId` 条件，只能看到分配给自己的订单（[OrderService.java#L277-L279](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L277-L279)）。
- **CUSTOMER 查看/编辑/删除校验**：非 ADMIN 的 CUSTOMER 只能操作 `createdBy.id == currentUserId` 的订单（[OrderService.java#L85-L87](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L85-L87)、[L116-L118](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L116-L118)、[L192-L194](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L192-L194)）。
- **RIDER 查看/更新状态校验**：RIDER 只能查看和更新 `rider.id == currentUserId` 的订单（[OrderService.java#L195-L197](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L195-L197)、[L230-L234](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L230-L234)）。
- **ADMIN/DISPATCHER**：无额外归属限制，可操作任意订单。

---

## 三、JWT 与 `@PreAuthorize` 协作机制

### 3.1 认证流程链路

```
客户端请求 → JwtAuthenticationFilter → SecurityContext → @PreAuthorize 校验 → 业务方法
```

**详细步骤：**

1. **登录签发 Token**：用户在 [AuthService.java#L31-L44](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/AuthService.java#L31-L44) 中通过用户名密码认证成功后，`JwtTokenProvider.createToken()` 将角色信息写入 JWT Claims 的 `role` 字段（[JwtTokenProvider.java#L45](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L45)），使用 HS256 签名。

2. **请求拦截与 Token 解析**：每个请求经过 [JwtAuthenticationFilter.java#L28-L48](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L28-L48)：
   - 从 `Authorization: Bearer <token>` 头中提取 JWT。
   - 调用 `jwtTokenProvider.validate()` 验签并校验有效期。
   - 通过 `jwtTokenProvider.getUsername()` 从 Claims 中取用户名。
   - 调用 `CustomUserDetailsService.loadUserByUsername()` 从数据库重新加载用户实体（**注意：角色信息来源于数据库，而非 JWT Claims 中的 role 字段**）。
   - 构建 `UsernamePasswordAuthenticationToken`，其中 `getAuthorities()` 返回 `ROLE_` + 角色名（如 `ROLE_ADMIN`），设置到 `SecurityContextHolder`。

3. **方法级鉴权**：`@EnableMethodSecurity`（[SecurityConfig.java#L21](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L21)）启用 Spring Security 方法安全拦截器。AOP 代理在调用带 `@PreAuthorize` 的方法前，解析 SpEL 表达式（如 `hasAnyRole('ADMIN','DISPATCHER')`），检查当前 `Authentication` 的 `GrantedAuthority` 集合是否包含匹配的 `ROLE_XXX`，不满足则抛出 `AccessDeniedException`。

4. **无状态会话**：Security 配置为 `SessionCreationPolicy.STATELESS`（[SecurityConfig.java#L33](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L33)），不依赖 HttpSession，每次请求均需携带 JWT。

### 3.2 JWT Claims 中的角色 vs 数据库角色

一个关键设计细节：JWT 中虽写入了 `role` claim（[JwtTokenProvider.java#L45](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L45)），但在过滤器链中该 claim **未被读取用于授权**。`JwtAuthenticationFilter` 每次都从数据库重新加载用户及其角色（[JwtAuthenticationFilter.java#L37](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L37)）。这意味着：
- **优点**：管理员修改用户角色后立即生效，无需等待 Token 过期。
- **代价**：每次请求增加一次数据库查询。
- **JWT 中的 role claim 当前处于冗余状态**，未被消费使用。

---

## 四、潜在安全风险分析

### 风险 1：DISPATCHER/ADMIN 可取消任意订单，但状态机未区分角色可触发的转换

**严重程度：中**

`updateStatus()` 的 `@PreAuthorize` 允许 `ADMIN`、`DISPATCHER`、`RIDER` 三种角色调用，且 `TRANSITIONS` 表未按角色做差异化约束（[OrderService.java#L221-L251](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L221-L251)）。

**问题表现：**
- 任意具有 `updateStatus()` 权限的角色均可触发 `CANCELLED` 转换：
  - `CREATED → CANCELLED`：调度员或管理员可取消客户刚创建的订单
  - `ASSIGNED → CANCELLED`：可取消已派单订单，导致已接单骑手白跑
  - `PICKED_UP → CANCELLED`：可取消骑手已取件的订单
  - `IN_TRANSIT → CANCELLED`：可取消运输中订单

- **骑手（RIDER）也能取消订单**，包括 `IN_TRANSIT → CANCELLED`，即骑手可在运输途中单方面取消订单，无需客户或调度员确认。
- **客户（CUSTOMER）无法取消自己的订单**（`updateStatus()` 的 `@PreAuthorize` 不含 CUSTOMER），这是业务上的不对称：客户可以创建/删除订单，但删除仅限 CREATED 状态，一旦派单后客户无取消入口。

**建议：** 为 CANCELLED 操作增加基于角色的前置条件，例如客户可取消自己 CREATED 状态的订单，骑手不可在 PICKED_UP/IN_TRANSIT 阶段自行取消，需调度员审批。

---

### 风险 2：ID 遍历与越权访问（直接对象引用不安全）

**严重程度：中**

`update()`、`delete()`、`assign()`、`updateStatus()` 均通过路径参数 `id`（Long 型自增主键）操作订单：
- `update()` 和 `delete()` 对 CUSTOMER 做了归属校验，但 **ADMIN 无任何限制**，这符合设计预期。
- `assign()` 仅校验 ADMIN/DISPATCHER 角色和状态为 CREATED，但 **未校验当前状态是否确实为无人接单**。虽然状态检查 `entity.getStatus() != OrderStatus.CREATED` 一定程度防止了重复派单，但并发场景下仍有竞态窗口（详见风险 5）。
- **`getByTrackingNo()` 通过运单号查询**，CUSTOMER 只能查自己创建的，RIDER 只能查分配给自己的，但运单号（trackingNo）由 `yyyyMMddHHmm + 4位随机数` 生成（[OrderService.java#L310-L314](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L310-L314)），4 位随机数仅 10000 种可能，在知道大致时间的情况下可被暴力枚举。

**建议：** 使用 UUID 或更长随机串作为运单号，或对查询接口增加速率限制。

---

### 风险 3：`@PreAuthorize` 标注在 Service 层而非 Controller 层，Controller 无注解

**严重程度：低（当前安全，但架构脆弱）**

[OrderController.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/OrderController.java) 中所有端点均无 `@PreAuthorize` 注解，权限注解全部写在 Service 方法上。

**潜在问题：**
- 如果未来新增 Controller 方法调用了不同的 Service 方法，或绕过 Service 直接调用 Repository，权限保护可能遗漏。
- 安全配置中 `anyRequest().authenticated()`（[SecurityConfig.java#L38](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L38)）仅保证"已认证"，不保证"有权限"。若 Service 方法遗漏注解，即构成"只要登录就能访问"的越权。

**现状评估：** 当前所有 Service 公开方法均已标注，暂无漏洞，但建议在 Controller 层也加注解做防御性编程。

---

### 风险 4：updateStatus 中实际费用（actualFee）缺少业务校验

**严重程度：中**

[OrderService.java#L240-L247](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L240-L247) 中：
1. 仅要求 `DELIVERED` 时 `actualFee != null`，但 **未校验金额的合理性**（例如负数、零值、与 estimatedFee 的偏差范围）。
2. 骑手可在 PICKED_UP 或 IN_TRANSIT 阶段提前设置 actualFee（`if (request.getActualFee() != null)` 无状态限制），甚至可以在非 DELIVERED 状态下反复修改 actualFee。
3. 任何具有 updateStatus 权限的角色（ADMIN/DISPATCHER/RIDER）均可设置 actualFee，不仅限送达时由骑手填写。

**建议：** 将 actualFee 的设置限定为仅在 DELIVERED 转换时允许，并增加金额非负、合理范围校验。

---

### 风险 5：状态更新与派单操作存在 TOCTOU 竞态条件

**严重程度：低**

`assign()` 和 `updateStatus()` 均采用"先读取状态 → 判断 → 修改 → 保存"的模式（[OrderService.java#L204-L217](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L204-L217)），在并发请求下存在时间差（Time-of-Check to Time-of-Use）漏洞：
- 两个调度员同时对同一 CREATED 订单派单给不同骑手，两次读取均为 CREATED，后提交的覆盖前者的派单结果。
- 骑手更新状态的同时，调度员取消订单，可能导致状态覆盖。

虽然 `@Transactional` 提供了事务边界，但默认隔离级别下 JPA 的乐观锁/悲观锁未启用，需依赖数据库的行级锁或添加 `@Version` 乐观锁字段。

**建议：** 在 OrderEntity 中添加 `@Version` 字段启用 JPA 乐观锁，防止并发覆盖。

---

### 风险 6：客户无法取消非 CREATED 状态订单，骑手却可取消任意中间状态

**严重程度：中（业务逻辑不对称）**

业务角色权限在取消操作上不合理：
- **CUSTOMER**：可 `delete()` 删除订单，但仅限 CREATED 状态；`updateStatus()` 无 CUSTOMER 权限，所以派单后客户无法主动取消。
- **RIDER**：在 ASSIGNED/PICKED_UP/IN_TRANSIT 均可调用 `updateStatus()` 将订单改为 CANCELLED，且 **无需任何审批或原因记录**。
- **DISPATCHER/ADMIN**：同样可取消任意中间状态订单。

这意味着：客户下单后，一旦骑手接单，客户自己无法取消（只能联系调度员），而骑手却可以随时单方面取消已取件甚至运输中的订单，可能造成客户物品丢失风险。

---

### 风险 7：JWT 无效/过期后 getByUsername 失败未被妥善处理

**严重程度：低**

[JwtAuthenticationFilter.java#L35-L41](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L35-L41) 中，若 JWT 签名有效但用户已被删除或禁用（`enabled = false`），`userDetailsService.loadUserByUsername()` 将抛出 `UsernameNotFoundException`，该异常未被 catch，会导致 500 错误而非 401。虽然 `CustomUserDetails.isEnabled()` 返回用户 enabled 状态，但 Filter 中并未在加载后检查 enabled 状态直接构造 Authentication。Spring Security 的 `DaoAuthenticationProvider` 会校验 enabled，但 Filter 绕过了该 Provider，直接构造 Token。

**建议：** 在 Filter 中增加 `if (!userDetails.isEnabled())` 判断，抛出自定义异常或直接返回 403。

---

### 风险 8：`getCurrentUserEntity()` 每次都查询数据库，存在 N+1 问题

**严重程度：低（性能问题，非安全漏洞）**

[AuthService.java#L51-L54](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/AuthService.java#L51-L54) 中，每次获取当前用户实体都直接查库，而 JWT Filter 已经查过一次。同一请求中 `list()` 先调用 `getCurrentUserEntity()`，后续 buildSpec 又使用，导致同一请求内多次查同一用户。虽然非安全问题，但会增加数据库负载。

---

### 风险 9：CORS 全局允许所有来源

**严重程度：低-中**

[CorsConfig.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/CorsConfig.java) 若配置为 `allowedOrigins("*")`（当前代码虽然未读取，但从全局 CORS 配置命名推测），在携带 Authorization Header 的场景下 `*` 不被浏览器允许，实际会回退到具体 Origin。但如配置为允许所有 Origin，则任何网站均可通过用户浏览器携带 JWT 发起跨域请求（CSRF 变种）。由于本系统使用 JWT Bearer Token 而非 Cookie，CSRF 风险较低，但仍建议限制允许的 Origin 列表。

---

### 风险 10：Delete 操作是物理删除，无审计日志

**严重程度：低**

[OrderService.java#L123](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L123) 调用 `orderRepository.delete()` 执行物理删除，ADMIN 可任意删除 CREATED 状态订单且无任何审计记录或软删除机制。若管理员账号泄露，订单数据可被彻底抹除。

---

## 五、风险汇总表

| 编号 | 风险描述 | 严重程度 | 影响角色 |
|-----|---------|---------|---------|
| 1 | 状态机 CANCELLED 转换无角色区分，骑手可取消运输中订单 | 中 | RIDER/DISPATCHER/ADMIN |
| 2 | 运单号可暴力枚举；ID 为自增长整型易被遍历 | 中 | ALL |
| 3 | @PreAuthorize 仅在 Service 层，Controller 无防御 | 低 | ALL |
| 4 | actualFee 缺少金额合理性、设置时机、设置角色的约束 | 中 | RIDER/DISPATCHER/ADMIN |
| 5 | 并发状态更新无乐观锁，存在 TOCTOU 竞态 | 低 | ALL |
| 6 | 客户无法在派单后取消订单，骑手却可任意取消 | 中 | CUSTOMER/RIDER |
| 7 | JWT Filter 未处理用户被禁用/删除场景，可能返回 500 | 低 | ALL |
| 8 | getCurrentUserEntity 重复查库（性能问题） | 低 | ALL |
| 9 | CORS 配置可能过宽（需确认 CorsConfig 具体内容） | 低-中 | ALL |
| 10 | 删除操作物理删除，无审计/软删除 | 低 | ADMIN/CUSTOMER |

---

## 六、核心结论

订单服务的权限控制采用了 **"URL 层认证（JWT Filter）→ 方法层角色鉴权（@PreAuthorize）→ 业务层数据归属校验"** 的三层防护架构，整体结构清晰，JWT 与 Spring Security 的集成使用了标准的无状态 Token 认证模式。

主要安全隐患集中在 **状态机对角色的不区分**（任何人可取消订单）、**金额字段缺少约束**、以及 **并发场景缺少乐观锁** 三个方面。建议优先修复风险 1、4、6 以保障订单数据的业务完整性。
