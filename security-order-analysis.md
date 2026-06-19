# CityCourier 订单服务安全分析报告

## 一、订单状态流转核心逻辑

### 1.1 状态定义

订单状态枚举定义在 [OrderStatus.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/OrderStatus.java#L1-L10)，共六种状态：

| 状态 | 含义 |
|------|------|
| `CREATED` | 待接单（客户刚创建） |
| `ASSIGNED` | 已派单（调度员分配骑手） |
| `PICKED_UP` | 已取件 |
| `IN_TRANSIT` | 配送中 |
| `DELIVERED` | 已送达（终态） |
| `CANCELLED` | 已取消（终态） |

### 1.2 状态流转规则

状态机定义在 [OrderService.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L42-L51) 的静态 `TRANSITIONS` 映射表中：

```
CREATED   → { ASSIGNED, CANCELLED }
ASSIGNED  → { PICKED_UP, CANCELLED }
PICKED_UP → { IN_TRANSIT, CANCELLED }
IN_TRANSIT→ { DELIVERED, CANCELLED }
DELIVERED → { }   (终态，禁止任何变更)
CANCELLED → { }   (终态，禁止任何变更)
```

状态流转校验在 [updateStatus()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L222-L251) 方法第 236-238 行执行，通过 `TRANSITIONS.get(currentStatus).contains(targetStatus)` 判断目标状态是否合法。

### 1.3 角色定义

四种角色定义在 [RoleType.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/RoleType.java#L1-L8)：

- **ADMIN**：管理员，拥有最高权限
- **DISPATCHER**：调度员，负责派单和调度
- **RIDER**：骑手，负责取件和配送
- **CUSTOMER**：客户，创建和管理自己的订单

---

## 二、各角色接口权限矩阵

`@PreAuthorize` 注解全部位于 Service 层（而非 Controller 层），Controller 仅做薄转发。权限注解通过 Spring Security 方法级安全（`@EnableMethodSecurity`）在 Service bean 方法调用时拦截。

### 2.1 接口权限一览

| 接口路径 | HTTP 方法 | Service 方法 | @PreAuthorize | 额外数据级校验 |
|----------|-----------|-------------|---------------|----------------|
| `/api/orders` | GET | `list()` | ADMIN/DISPATCHER/RIDER/CUSTOMER | CUSTOMER 只能看自己创建的；RIDER 只能看分配给自己的；ADMIN/DISPATCHER 看全部 |
| `/api/orders/tracking/{trackingNo}` | GET | `getByTrackingNo()` | ADMIN/DISPATCHER/RIDER/CUSTOMER | CUSTOMER 只能查自己创建的；RIDER 只能查分配给自己的 |
| `/api/orders` | POST | `create()` | CUSTOMER/ADMIN | 创建人自动绑定为当前用户 |
| `/api/orders/{id}` | PUT | `update()` | CUSTOMER/ADMIN | CUSTOMER 只能编辑自己的订单；仅 CREATED 状态可编辑 |
| `/api/orders/{id}` | DELETE | `delete()` | CUSTOMER/ADMIN | CUSTOMER 只能删除自己的订单；仅 CREATED 状态可删除 |
| `/api/orders/{id}/assign` | POST | `assign()` | ADMIN/DISPATCHER | 仅 CREATED 状态可派单；必须指定 RIDER 角色用户 |
| `/api/orders/{id}/status` | POST | `updateStatus()` | ADMIN/DISPATCHER/RIDER | RIDER 只能操作分配给自己的订单；终态不可变更；状态流转需合法 |
| `/api/orders/stats` | GET | `stats()` | ADMIN/DISPATCHER | 无额外校验 |

### 2.2 数据级权限实现细节

Service 层通过 `authService.getCurrentUserEntity()` 获取当前登录用户实体，再手动判断数据归属：

- **list 方法**：在 [buildSpec()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L271-L301) 中使用 JPA Specification 动态拼接过滤条件，CUSTOMER 过滤 `createdBy.id = currentUserId`，RIDER 过滤 `rider.id = currentUserId`。
- **update/delete 方法**：CUSTOMER 角色检查 `entity.getCreatedBy().getId().equals(current.getId())`，ADMIN 不做归属检查。
- **updateStatus 方法**：RIDER 角色检查 `entity.getRider().getId().equals(current.getId())`，ADMIN/DISPATCHER 不做归属检查。
- **getByTrackingNo 方法**：CUSTOMER 和 RIDER 均做归属检查。

---

## 三、JWT 与 @PreAuthorize 协作机制

### 3.1 认证授权整体架构

```
客户端                          服务器
  │                               │
  │  POST /api/auth/login         │
  │  (username + password)        │
  │ ─────────────────────────────>│
  │                               │  AuthenticationManager.authenticate()
  │                               │  → DaoAuthenticationProvider
  │                               │  → CustomUserDetailsService.loadUserByUsername()
  │                               │  → BCryptPasswordEncoder.matches()
  │                               │
  │                               │  JwtTokenProvider.createToken()
  │                               │  载荷: { sub: username, role: ROLE_NAME, exp }
  │                               │  签名: HS256(secretKey)
  │  { token, userInfo }          │
  │ <─────────────────────────────│
  │                               │
  │  GET /api/orders              │
  │  Authorization: Bearer <token>│
  │ ─────────────────────────────>│
  │                               │  JwtAuthenticationFilter.doFilterInternal()
  │                               │  1. 提取 Bearer Token
  │                               │  2. jwtTokenProvider.validate(token)
  │                               │  3. jwtTokenProvider.getUsername(token)
  │                               │  4. userDetailsService.loadUserByUsername(username)
  │                               │  5. 构造 UsernamePasswordAuthenticationToken
  │                               │  6. 设置到 SecurityContextHolder
  │                               │
  │                               │  SecurityFilterChain: anyRequest().authenticated()
  │                               │  → 检查 SecurityContext 中是否存在 Authentication
  │                               │
  │                               │  调用 OrderController 方法
  │                               │  → 调用 OrderService 方法
  │                               │  → @PreAuthorize 拦截器（MethodSecurityInterceptor）
  │                               │    检查当前 Authentication.getAuthorities() 是否满足 SpEL 表达式
  │                               │
  │  { data }                     │
  │ <─────────────────────────────│
```

### 3.2 JWT 生成细节

[JwtTokenProvider.createToken()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtTokenProvider.java#L38-L50)：

- 使用 HMAC-SHA256 签名（HS256）
- 密钥从配置 `security.jwt.secret` 读取（环境变量 `JWT_SECRET`），要求至少 32 字符
- Token 载荷：
  - `sub`（subject）：用户名（username）
  - `role`：角色名称（如 `ADMIN`），自定义 claim
  - `iat`（issued at）：签发时间
  - `exp`（expiration）：过期时间（默认 7200 秒 = 2 小时）
- 注意：Token 中**未存储用户 ID**，仅存储用户名，每次请求需重新查库。

### 3.3 请求认证流程

[JwtAuthenticationFilter](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L28-L48) 是一个 `OncePerRequestFilter`，注册在 `UsernamePasswordAuthenticationFilter` 之前：

1. 从 `Authorization` 请求头提取 `Bearer <token>` 部分
2. 调用 `jwtTokenProvider.validate(token)` 验证签名和过期时间
3. 解析出 username，调用 `CustomUserDetailsService.loadUserByUsername(username)` 从数据库加载用户
4. 构造 `UsernamePasswordAuthenticationToken`，传入 userDetails 和 authorities
5. 将 Authentication 放入 `SecurityContextHolder.getContext()`

### 3.4 角色权限映射

[CustomUserDetails.getAuthorities()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/CustomUserDetails.java#L30-L33) 将数据库中的 `RoleType` 枚举转换为 Spring Security 的 `GrantedAuthority`：

```java
return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
```

例如数据库角色 `ADMIN` 会映射为权限字符串 `ROLE_ADMIN`。这与 `@PreAuthorize("hasAnyRole('ADMIN',...)")` 中的角色名对应——Spring Security 的 `hasRole()` 会自动添加 `ROLE_` 前缀。

### 3.5 @PreAuthorize 执行点

[SecurityConfig](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/SecurityConfig.java#L20-L44) 通过 `@EnableMethodSecurity` 开启方法级安全。关键配置：

- CSRF 禁用（REST API + JWT 无状态架构）
- Session 策略：`STATELESS`（不创建 HttpSession）
- URL 级别：除 `/api/auth/login`（POST）和 `/api/health`（GET）外，全部要求 authenticated
- 方法级别：`@PreAuthorize` 在 Service bean 方法调用时通过 AOP 代理拦截，基于 `SecurityContextHolder` 中的 Authentication 进行 SpEL 表达式求值

**协作关键点**：JWT 过滤器负责**认证（Authentication）**——证明"你是谁"；`@PreAuthorize` 负责**授权（Authorization）**——判断"你能做什么"。两者通过 `SecurityContextHolder` 这个线程级存储桥梁衔接：过滤器将认证结果写入 SecurityContext，方法拦截器从 SecurityContext 读取权限信息进行判断。

---

## 四、潜在安全风险分析

### 4.1 严重风险

#### 风险 1：DISPATCHER/ADMIN 可越权操作所有订单状态（水平 + 垂直越权）

**位置**：[OrderService.updateStatus()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L222-L251)

**问题描述**：方法仅对 RIDER 角色做了订单归属检查（第 230-234 行），对 ADMIN 和 DISPATCHER 没有任何归属或业务范围限制。这意味着：

- DISPATCHER 可以将任意订单直接标记为 `PICKED_UP`、`IN_TRANSIT`、`DELIVERED`，代替骑手完成配送流程操作
- DISPATCHER 可以取消任意骑手正在配送中的订单（IN_TRANSIT → CANCELLED）
- ADMIN 同样不受限制，可以操作所有订单

**业务影响**：调度员的职责应限于派单（CREATED → ASSIGNED），不应能代替骑手取件或确认送达。当前实现中，派单操作 `assign()` 正确限制了起始状态为 CREATED，但 `updateStatus()` 对 DISPATCHER 没有细分可操作的目标状态。

**风险等级**：高

---

#### 风险 2：CUSTOMER 无法取消已派单订单，但 RIDER/DISPATCHER 可在任意阶段取消

**位置**：[OrderService.updateStatus()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L222-L251) 与 [OrderService.delete()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L112-L125)

**问题描述**：

- `delete()` 仅允许在 CREATED 状态下物理删除订单（相当于未派单时取消），且仅限 CUSTOMER/ADMIN
- `updateStatus()` 允许 CANCELLED 作为 ASSIGNED/PICKED_UP/IN_TRANSIT 的后继状态，但该方法的 `@PreAuthorize` 仅包含 ADMIN/DISPATCHER/RIDER，**CUSTOMER 被完全排除在外**
- 没有记录取消原因、取消人、取消时间

**业务影响**：客户下单后想取消订单，必须在派单前通过删除操作完成。一旦派单，客户无法主动取消，反而骑手和调度员可以随意取消。这不符合业务常理。此外，骑手在 PICKED_UP 和 IN_TRANSIT 阶段也能直接取消订单（已取件甚至配送中可取消），存在配送员恶意取消的风险。

**风险等级**：高

---

#### 风险 3：actualFee 可在任意状态变更时被任意角色设置（费用篡改风险）

**位置**：[OrderService.updateStatus()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L244-L247)

```java
entity.setStatus(request.getTargetStatus());
if (request.getActualFee() != null) {
    entity.setActualFee(request.getActualFee());
}
```

**问题描述**：代码仅在第 240-242 行校验了"DELIVERED 时必须填写 actualFee"，但没有校验"只有 DELIVERED 时才能设置 actualFee"。只要请求体中携带 `actualFee` 字段，无论目标状态是 ASSIGNED、PICKED_UP、IN_TRANSIT 还是 CANCELLED，都会更新费用金额。同时也没有限制谁可以设置费用。

**攻击场景**：
- DISPATCHER 在派单时就将 actualFee 设为 0 或异常值
- RIDER 在取件时就修改实际费用
- 取消订单时仍可篡改 actualFee（虽然已取消的订单不再结算，但数据被污染）

**风险等级**：高

---

### 4.2 中等风险

#### 风险 4：运单号可预测，存在信息泄露风险（暴力枚举）

**位置**：[getByTrackingNo()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L187-L199) 与 [generateTrackingNo()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L310-L314)

**问题描述**：运单号生成规则为 `CC + yyyyMMddHHmm + 4位随机数（1000-9999）`，即每分钟只有 9000 种可能值。虽然 CUSTOMER 和 RIDER 查询时做了归属检查，但 ADMIN 和 DISPATCHER 查询时没有任何限制。即使是 CUSTOMER 角色，攻击者如果知道自己创建订单的时间，也可以尝试遍历运单号查询其他客户的订单（只是会被归属检查拦截，但仍可通过响应差异推断运单号是否存在）。

更重要的是，`getByTrackingNo()` 对 ADMIN/DISPATCHER 完全开放，结合可预测的运单号，可能导致批量订单信息泄露。

**风险等级**：中

---

#### 风险 5：JWT 无法主动失效，用户禁用/权限变更后旧 Token 仍可使用

**位置**：[JwtAuthenticationFilter.doFilterInternal()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/JwtAuthenticationFilter.java#L28-L48)

**问题描述**：JWT 是无状态的，Filter 验证 Token 时只检查签名和过期时间，然后通过 username 从数据库加载用户。虽然每次都会重新查库加载最新的用户状态（`enabled` 字段和角色），但注意：[CustomUserDetailsService.loadUserByUsername()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/security/CustomUserDetailsService.java#L17-L21) 没有检查用户的 `enabled` 状态！

```java
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return userRepository.findByUsername(username)
            .map(CustomUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
}
```

`userRepository.findByUsername()` 没有过滤 `enabled = true`，这意味着：
- 管理员将某用户禁用（`enabled = false`）后，该用户在 Token 过期前仍可正常访问所有接口
- 用户被禁用后其已颁发的 Token 在 `expire-seconds`（2小时）内完全有效

此外，JWT 中未存储用户 ID，每次请求需要查一次数据库，存在不必要的数据库压力，但也意味着角色变更可以实时生效（因为每次都重新加载用户）。不过 `enabled` 字段的校验确实缺失。

**风险等级**：中

---

#### 风险 6：派单时未校验骑手账号是否启用

**位置**：[OrderService.assign()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L203-L218)

```java
User rider = userRepository.findByUsername(request.getRiderUsername())
        .filter(u -> u.getRole() == RoleType.RIDER)
        .orElseThrow(() -> new BizException("骑手不存在或角色错误"));
```

**问题描述**：只过滤了角色为 RIDER，没有检查 `u.getEnabled()`。已被禁用的骑手账号仍可被派单。

**风险等级**：中

---

#### 风险 7：删除订单为物理删除，不可恢复

**位置**：[OrderService.delete()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L112-L125)

**问题描述**：`orderRepository.delete(entity)` 直接从数据库删除记录，非软删除。CREATED 状态的订单被删除后完全消失，无法追溯历史。如果 ADMIN 误删，没有恢复机制。

**风险等级**：中

---

### 4.3 低风险 / 设计建议

#### 风险 8：状态变更无操作人记录和审计日志

**问题描述**：订单状态更新、派单、取消等操作没有记录操作人 ID、操作时间、操作原因。[OrderEntity](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/entity/OrderEntity.java#L1-L77) 中仅有 `createdAt` 和 `updatedAt`，没有 `statusChangedBy`、`cancelledBy`、`cancelledReason`、`assignedBy` 等审计字段。发生纠纷时无法追溯。

**风险等级**：低（审计缺失）

---

#### 风险 9：@PreAuthorize 写在 Service 层，Controller 层无保护（架构问题）

**位置**：[OrderController.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/controller/OrderController.java#L1-L62)

**问题描述**：Controller 层所有方法均没有添加任何权限注解，完全依赖 Service 层的 `@PreAuthorize`。这在当前代码结构下是可行的（Spring 方法安全对同一 application context 中的 Service bean 调用生效），但如果后续有人绕过 Service 直接在 Controller 中写业务逻辑，或通过其他 Service 内部调用（注意：Spring AOP 不拦截同一个类中 this 调用），可能导致权限绕过。更安全的做法是在 Controller 层也加上 URL 级别的权限控制作为防御纵深。

另外，SecurityConfig 中 URL 级别权限过于宽泛：`.anyRequest().authenticated()` 只要求登录，不区分角色。虽然方法级安全做了第二层防护，但若方法注解遗漏，接口将暴露给所有已认证用户。

**风险等级**：低（依赖单一防护层）

---

#### 风险 10：初始化数据硬编码默认密码

**位置**：[DataInitializer.java](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/config/DataInitializer.java#L27-L30)

```java
User admin = createUser("admin", "password123", RoleType.ADMIN);
User dispatcher = createUser("dispatch", "password123", RoleType.DISPATCHER);
User rider = createUser("rider1", "password123", RoleType.RIDER);
User customer = createUser("customer1", "password123", RoleType.CUSTOMER);
```

**问题描述**：所有初始用户使用弱密码 `password123`。虽然仅在数据库为空时初始化，但如果生产环境部署后未及时修改，或数据库被重置，将导致严重安全问题。

**风险等级**：低（需生产环境注意）

---

#### 风险 11：订单编辑/删除时 ADMIN 不受状态限制以外的业务约束

**位置**：[OrderService.update()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L81-L108) 和 [delete()](file:///d:/document/code/modelX/GSB0616/585/GSB585/backend/src/main/java/com/citycourier/service/OrderService.java#L112-L125)

**问题描述**：ADMIN 可以编辑/删除任意 CREATED 状态的订单，包括修改客户信息、费用计算参数（weightKg、distanceKm）等，从而改变 estimatedFee。虽然 ADMIN 应有较高权限，但修改订单没有审计记录。此为设计取舍，风险较低。

**风险等级**：低

---

### 4.4 风险汇总表

| 编号 | 风险描述 | 位置 | 等级 |
|------|---------|------|------|
| 1 | DISPATCHER/ADMIN 可越权操作任意订单的所有状态流转（代替骑手取件/送达） | updateStatus() | 高 |
| 2 | CUSTOMER 无法取消已派单订单，RIDER 可在取件/配送中取消 | updateStatus()/delete() | 高 |
| 3 | actualFee 可在任意状态变更时被设置，非仅送达时 | updateStatus() 第 244-247 行 | 高 |
| 4 | 运单号可预测，存在批量信息枚举风险 | generateTrackingNo()/getByTrackingNo() | 中 |
| 5 | 用户禁用后旧 JWT 仍有效（loadUserByUsername 未检查 enabled） | CustomUserDetailsService | 中 |
| 6 | 派单时未校验骑手是否启用 | assign() | 中 |
| 7 | 订单物理删除，不可恢复 | delete() | 中 |
| 8 | 状态变更无操作人/原因审计字段 | OrderEntity | 低 |
| 9 | Controller 层无权限注解，URL 级安全配置过于宽泛 | SecurityConfig/OrderController | 低 |
| 10 | 初始化数据使用弱密码 | DataInitializer | 低 |
| 11 | ADMIN 编辑订单无审计 | update() | 低 |

---

## 五、修复建议

1. **细化 updateStatus 角色-状态权限**：为不同角色定义允许触发的目标状态集合。例如：
   - CUSTOMER：仅允许 `{CANCELLED}`（在 ASSIGNED 之前，或在 ASSIGNED 后一定时间内）
   - DISPATCHER：仅允许 `{ASSIGNED, CANCELLED}`，且 CANCELLED 仅在 ASSIGNED 阶段
   - RIDER：仅允许 `{PICKED_UP, IN_TRANSIT, DELIVERED}`，以及在 ASSIGNED 阶段可拒绝（CANCELLED）
   - ADMIN：全部状态（作为超级管理员兜底）

2. **修复 actualFee 写入逻辑**：将 actualFee 设置严格限定在 DELIVERED 状态，且由完成配送的骑手或管理员设置：
   ```java
   if (request.getTargetStatus() == OrderStatus.DELIVERED) {
       if (request.getActualFee() == null) {
           throw new BizException("订单送达时必须填写实际费用");
       }
       entity.setActualFee(request.getActualFee());
   } else if (request.getActualFee() != null) {
       throw new BizException("仅送达状态可设置实际费用");
   }
   ```

3. **修复 loadUserByUsername 启用检查**：
   ```java
   return userRepository.findByUsernameAndEnabledTrue(username)
           .map(CustomUserDetails::new)
           .orElseThrow(() -> new UsernameNotFoundException("用户不存在或已禁用"));
   ```

4. **派单时校验骑手启用状态**：在 assign() 的 filter 链中增加 `.filter(u -> u.getEnabled())`。

5. **为订单增加审计字段**：`assignedBy`、`cancelledBy`、`cancelledReason`、`statusUpdatedBy`、`statusUpdatedAt`。

6. **物理删除改为软删除**：在 OrderEntity 中增加 `deleted` 布尔字段或 `deletedAt` 时间字段，删除时标记而非直接删除记录。

7. **运单号增加随机性**：使用 UUID 或更长的随机串，或在查询运单接口增加频率限制。

8. **Controller 层增加角色注解作为纵深防御**：在 Controller 方法上也添加对应的 `@PreAuthorize`，与 Service 层形成双重保护。
