# 同城物流系统（Spring Boot + Vue + SQLite）

## 🛠 技术栈
- Frontend: Vue 3 + TypeScript + Element Plus + Vite
- Backend: Spring Boot 3 + Spring Security + Spring Data JPA
- Database: SQLite（文件持久化）

## 快速启动

### 前置要求
- Docker Desktop 已安装并运行

### 启动步骤
0. 复制环境变量模板并配置 JWT 密钥（至少 32 位）：
   ```bash
   cp .env.example .env
   ```
   然后编辑 `.env`，例如：
   ```bash
   JWT_SECRET=replace-with-a-strong-secret-at-least-32-chars
   ```
1. 在项目根目录执行：
   ```bash
   docker compose up --build
   ```
2. 等待容器启动完成后访问页面。

## 服务
- 前端: http://localhost:3000
- 健康检查: http://localhost:8000/api/health

## 验证
1. 执行 `docker compose up --build`，确认后端与前端容器均启动。
2. 打开 `http://localhost:3000`，使用测试账号（如 `admin / password123`）登录。
3. 进入“运单查询”页面，确认可看到订单列表并支持关键字搜索。
4. 使用管理员或下单方账号进入“创建订单”，提交后应提示创建成功并生成运单号。
5. 使用调度员进入“运营看板”，确认不显示“创建运单”按钮；使用骑手进入“运单查询”，确认不显示“去创建订单”按钮。

## 🧪 测试账号
- 管理员: `admin` / `password123`
- 调度员: `dispatch` / `password123`
- 骑手: `rider1` / `password123`
- 下单方: `customer1` / `password123`

## 📸 功能介绍
- 中文系统界面、中文校验提示。
- 登录鉴权 + 角色访问控制（管理员/调度员/骑手/下单方）。
- 订单创建、运单查询、订单列表筛选、派单、状态流转、统计看板。
- 列表统一分页，表格超宽支持横向滚动条。
- 所有确认动作使用居中 Modal，Toast 自动消失。
- 支持桌面端与移动端响应式布局。

## 🛠 技术亮点
- 后端使用 JWT + Spring Security 实现接口级权限控制。
- 订单状态机严格校验合法流转，非法请求返回中文错误。
- SQLite 持久化存储，容器重启后数据保留。
- 启动自动注入 Seed 数据，避免空库。

## 项目结构
```text
.
├── backend/
├── frontend/
├── .env.example
├── docker-compose.yml
└── README.md
```
