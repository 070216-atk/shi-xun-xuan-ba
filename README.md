# 学生实训室进组选拔系统

把学院「双向选择 · 分轮录取 · 统筹调剂」的 8 步进组选拔流程搬到线上。

**技术栈**：Vue 3 + Element Plus（前端）｜Spring Boot 3.3 + MyBatis（后端）｜MySQL 8

---

## 目录结构

```
shi-xun-xuan-ba/
├── assets/images/选拔流程图.jpg     业务流程原图
├── framework/                       设计与规范文档
│   ├── 1.pdr/选拔系统PRD.md          产品需求
│   ├── 2.architecture/架构设计.md    分层、技术选型、关键决策
│   ├── 3.database/                  建表与种子数据
│   │   ├── schema.sql               12 张表
│   │   ├── seed.sql                 演示数据
│   │   └── README.md                导入步骤、密码处理、演示时间
│   └── 4.api/
│       ├── 接口设计.md               接口清单
│       └── 字段定义.md               ← 每个接口的请求/响应字段（联调以此为准）
├── frontend/                        Vue 3 + Vite
└── backend/                         Spring Boot（骨架已建）
```

---

## 环境要求

| 组件 | 版本 | 本机情况 |
| --- | --- | --- |
| Node.js | ≥ 18 | v24.16.0 ✅ |
| JDK | **21** | 已装 `C:\Program Files\Java\jdk-21`，但 PATH 里默认是 26 ⚠️ |
| MySQL | 8.0 | 已装 ✅ |
| Maven | 3.9 | 不在 PATH，用 `E:\学校文件\大二上作业\商城\tools\apache-maven-3.9.16\bin\mvn.cmd` |

> ⚠️ **JDK 必须显式切到 21**，否则 Spring Boot 起不来。见 [backend/README.md](backend/README.md) 第 0 节。

---

## 快速开始

### 1. 数据库

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
& $mysql -u root -p --default-character-set=utf8mb4 -e "source framework/3.database/schema.sql"
& $mysql -u root -p --default-character-set=utf8mb4 -e "source framework/3.database/seed.sql"
```

详见 [framework/3.database/README.md](framework/3.database/README.md)（含密码占位符怎么处理）。

### 2. 后端

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cd backend
& "E:\学校文件\大二上作业\商城\tools\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run
```

启动前先在 `backend/src/main/resources/application.yml` 里填好 MySQL 密码。

### 3. 前端

```powershell
cd frontend
npm install
npm run dev      # http://localhost:5173
```

---

## 当前进度

| 模块 | 状态 |
| --- | --- |
| 业务流程梳理 | ✅ 完成 |
| PRD / 架构设计 / 接口设计 | ✅ 完成（v0.1，待评审） |
| 前端页面（含 mock 数据） | ✅ 完成，**尚未接后端** |
| 数据库表结构 + 种子数据 | ✅ 完成 |
| 后端接口实现 | ⬜ 未开始 |
| 前后端联调 | ⬜ 未开始 |
| 完整流程验收（PRD 10 条） | ⬜ 未开始 |

---

## 已知待办

- [ ] `vite.config.js` 缺 `/api` 代理配置，前端无法直连 8080
- [ ] `seed.sql` 的密码是占位符，需用 BCrypt 生成后替换（或后端启动时自建）
- [ ] 前端 mock 字段与 [字段定义.md](framework/4.api/字段定义.md) 有 5 处差异，需同步
- [ ] `framework/3.database/` 与 `4.api/` 的编号之间缺 `3.` 之外的中间层，若需要可补

---

## 协作约定

1. **接口字段以 `framework/4.api/字段定义.md` 为准**，改字段必须同步改文档并告知对方。
2. **一次提交只做一件事**，提交信息写清楚做了什么。
3. **每天开工先 `git pull`**，推之前先拉。
4. **不要提交**：`node_modules/`、`target/`、`.env`、数据库密码（已在 `.gitignore` 里）。
