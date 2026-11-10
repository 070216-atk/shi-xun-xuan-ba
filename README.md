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
│   │   ├── README.md                导入步骤、密码处理、演示时间`r`n│   │   └── 数据核对说明.md          名额核对、转录错误修正、规则复核
│   └── 4.api/
│       ├── 接口设计.md               接口清单
│       └── 字段定义.md               ← 每个接口的请求/响应字段（联调以此为准）
├── frontend/                        Vue 3 + Vite
└── backend/                         Spring Boot
```

---

## 环境要求

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| JDK | **17 或 21** | 不要用 25 及以上，Spring Boot 3.3 不支持 |
| Maven | 3.6+ | 不在 PATH 也行，用完整路径调用；IDE 自带的也能用 |
| MySQL | 8.0 | 字符集必须 utf8mb4 |
| Node.js | 18+ | 前端用 |

> 两个人的电脑环境可能不同，所以 **JDK 路径、Maven 路径、数据库密码都不写死在仓库里**，各自在本地配。

---

## 新人上手（四步）

### 第 1 步：克隆

```powershell
git clone git@github.com:070216-atk/shi-xun-xuan-ba.git
cd shi-xun-xuan-ba
```

### 第 2 步：建自己的本机配置（不提交）

```powershell
cd backend\src\main\resources
Copy-Item application-local.yml.example application-local.yml
```

打开 `application-local.yml`，填 MySQL 密码和 JWT 密钥。
**这两项每人不同，绝不能写进 `application.yml`。**

### 第 3 步：导入数据库

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"   # ← 你的路径
& $mysql -u root -p --default-character-set=utf8mb4 -e "source framework/3.database/schema.sql"
& $mysql -u root -p --default-character-set=utf8mb4 -e "source framework/3.database/seed.sql"
```

详见 [framework/3.database/README.md](framework/3.database/README.md)。

### 第 4 步：启动

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"    # ← 你的 JDK 目录
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

cd backend
& "你的Maven目录\bin\mvn.cmd" spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev      # http://localhost:5173
```

详细排错见 [backend/README.md](backend/README.md)。

---

## 当前进度

| 模块 | 状态 |
| --- | --- |
| 业务流程梳理 | ✅ 完成 |
| PRD / 架构设计 / 接口设计 / 字段定义 | ✅ 完成（v0.1，待评审） |
| 前端页面（含 mock 数据） | ✅ 完成，**尚未接后端** |
| 数据库表结构 + 种子数据 | ✅ 完成 |
| 后端接口实现 | ✅ 学生 / 导师 / 管理端核心接口（补录轮次与异议提交未做） |
| 前后端联调 | ⬜ 未开始 |
| 完整流程验收（PRD 10 条） | ⬜ 未开始 |

---

## 已知待办

- [ ] `vite.config.js` 缺 `/api` 代理配置，前端无法直连 8080
- [ ] `seed.sql` 的密码是占位符，需用 BCrypt 生成后替换（或后端启动时自建账号）
- [ ] 前端 mock 字段与 [字段定义.md](framework/4.api/字段定义.md) 有 5 处差异，需同步
- [x] 种子数据已按《实训室选报宣讲纪要》替换为真实数据，名额统一 64/室，详见 [数据核对说明.md](framework/3.database/数据核对说明.md)
- [ ] `POST /admin/rounds` 补录轮次开启未实现（需要为第 2/3 轮生成阶段时间窗）
- [ ] 公示异议的提交/处理接口未实现（当前只能在库里改 objection_count）
- [ ] 前端仍是 mock 数据，尚未接后端

---

## 协作约定

1. **接口字段以 [字段定义.md](framework/4.api/字段定义.md) 为准**，改字段必须同步改文档并告知对方。
2. **一次提交只做一件事**，提交信息写清楚做了什么。
3. **每天开工先 `git pull`**，推之前先拉。
4. **绝不提交**：`node_modules/`、`target/`、`application-local.yml`、`.env`、任何密码（`.gitignore` 已覆盖）。
5. 代码文件统一 LF 换行，`.gitattributes` 已配好，别手动改。

---

## 一键启动（推荐）

双击项目根目录的 **`启动后端.cmd`**。

脚本会自动完成：找 JDK（17~24，优先 21）→ 找 Maven → 打包 → 检查 8080 端口 → 启动。
找不到 JDK 或 Maven 时会给出明确提示，不会一闪而过。

> ⚠️ **不要用 `mvn spring-boot:run`**：在含中文的路径下，Maven 分叉出的 JVM 会丢失 classpath，
> 报 `ClassNotFoundException: com.college.selection.SelectionApplication`。
> 用 `mvn package` + `java -jar`，或者直接用上面的 .cmd。

启动成功后：
- 接口地址：`http://localhost:8080/api/labs`
- 演示账号：`admin` / `teacher01` / `student01`，密码统一 `123456`
### 一键初始化数据库

双击根目录的 **`初始化数据库.cmd`**。

脚本会：找 `mysql.exe` → 读 `application-local.yml` 里的密码 → 确认 → 建表 → 导数据 → 打印校验结果 → 可选地把演示阶段设成现在开放。

> ⚠️ **git 只同步文件，不同步数据库**。同事 `git pull` 之后，代码和 `schema.sql`/`seed.sql` 都更新了，
> 但他电脑上的 MySQL 还是旧结构，**必须自己再跑一次这个脚本**，否则会报 `Unknown column 'quota'`。

**判断自己的库是新是旧**：访问 `http://localhost:8080/api/labs`，
看到 `"code": "202"` 是新库，看到 `"code": "L1"` 就是旧库，要重导。
---

## 四个一键脚本（根目录）

| 脚本 | 什么时候用 |
| --- | --- |
| **`初始化数据库.cmd`** | 第一次运行、或别人更新了 `schema.sql`/`seed.sql` 之后。会重建 `lab_selection` 库 |
| **`启动后端.cmd`** | 单独启动后端（8080），自动找 JDK/Maven、打包、启动 |
| **`启动前端.cmd`** | 单独启动前端（5173），缺依赖会自动 `npm install` |
| **`启动全部.cmd`** | **推荐**。依次拉起后端和前端，各自等就绪，最后自动打开浏览器 |

**第一次使用顺序**：`初始化数据库.cmd` → `启动全部.cmd`（会自动打开浏览器，不用敲网址）

> 所有脚本都会在出错时停在窗口里显示原因，不会一闪而过。
> 如果提示"没有 Maven"，说明你机器上没装 Maven，但只要有已经打好的 jar 也能启动。