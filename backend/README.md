# 后端（Spring Boot 3.3 + MyBatis + MySQL 8）

> 目标：**任何一台装了 JDK 17+ 和 MySQL 8 的电脑，clone 下来都能跑**。
> 下面凡是写路径的地方，都标了「本机示例」，你换成自己电脑上的即可。

---

## 0. 三样东西是"每人自己配"的

| 要配什么 | 放哪 | 会不会被提交 |
| --- | --- | --- |
| JDK 路径 | 环境变量 `JAVA_HOME` | 不涉及文件 |
| MySQL 密码 | `backend/src/main/resources/application-local.yml` | ❌ 已被 `.gitignore` 排除 |
| JWT 密钥 | 同上 | ❌ |

**新建你自己的本机配置：**

```powershell
cd backend\src\main\resources
Copy-Item application-local.yml.example application-local.yml
```

然后打开 `application-local.yml`，把两处占位符换掉。

> ⚠️ 千万别把密码写进 `application.yml`——那个文件是要提交到 GitHub 的。

---

## 1. 选一个 JDK（17 或 21，不要 25+）

先看这台机器有哪些 JDK：

```powershell
Get-ChildItem "C:\Program Files\Java","C:\Program Files\Zulu" -Directory -ErrorAction SilentlyContinue |
  Select-Object FullName
```

然后**只在当前窗口**指定（不影响系统设置）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"    # ← 换成你自己的 JDK 目录
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java --version
```

**成功标志**：输出里出现 `17.x` 或 `21.x`。

> 注意是 `java --version`（两个横线）。某些环境下 `java -version`（一个横线）没有任何输出，不是坏了。

**为什么必须手动指定**：这台机器 PATH 里默认是 JDK 26，`JAVA_HOME` 指向 25，两个都跑不动 Spring Boot 3.3。你室友的机器可能又是另一种情况——所以每次都显式设一遍最稳。

---

## 2. Maven

Maven 没在 PATH 里也能跑，用你机器上的完整路径即可。

**找 Maven 的三种常见情况：**

| 情况 | 怎么找 |
| --- | --- |
| 装了 IDEA / 用 IDE 开发 | 直接用 IDE 右侧 Maven 面板的 `spring-boot:run`，不用命令 |
| 以前的项目里带过 Maven | 找 `mvn.cmd`，例如本机示例：`E:\...\商城\tools\apache-maven-3.9.16\bin\mvn.cmd` |
| 都没有 | 去 `https://maven.apache.org/download.cgi` 下载解压，用 `bin\mvn.cmd` |

**建议先配国内镜像**（否则第一次下载依赖可能几分钟甚至失败）。在 `C:\Users\<你>\.m2\settings.xml` 里加：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>aliyun</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

---

## 3. 启动

```powershell
$env:JAVA_HOME = "你的JDK目录"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

cd E:\学校文件\大二上作业\shi-xun-xuan-ba\backend
& "你的Maven目录\bin\mvn.cmd" spring-boot:run
```

**成功标志**：

```
Tomcat started on port 8080
Started SelectionApplication in 3.2 seconds
```

> 第一次跑要下载依赖，2–5 分钟属正常。

---

## 4. 目录约定

```
backend/src/main/
├── java/com/college/selection/
│   ├── SelectionApplication.java   ✅ 已有
│   ├── common/                     统一返回体 Result
│   ├── controller/                 接请求、校验参数、包装返回
│   ├── service/                    业务规则：时间窗、自动确认、递补、调剂优先级
│   ├── mapper/                     MyBatis 接口（@Mapper）
│   ├── entity/                     与表一一对应
│   ├── dto/                        请求/响应对象
│   └── config/                     跨域、异常处理、拦截器
└── resources/
    ├── application.yml             ✅ 已有（不含任何密码）
    ├── application-local.yml       ← 每人自己建，不提交
    └── mapper/                     SQL XML
```

**三条纪律**：

1. controller 里不写业务规则，判断全部放 service
2. 不把 entity 直接返回给前端，用 dto
3. 学生状态只能由 service 改（架构设计 §4.2）

---

## 5. 建议的实现顺序

| 顺序 | 做什么 | 验证方式 |
| --- | --- | --- |
| 1 | `GET /api/labs` | 浏览器直接访问 |
| 2 | `GET /api/labs/{id}` | |
| 3 | `POST /api/auth/login` | Postman / curl |
| 4 | `GET /api/stages` | 对比 `seed.sql` |
| 5 | `PUT /api/student/application` | 试填 4 个意向会不会被拦 |
| 6 | `POST /api/student/offers/{labId}/confirm` | 开两个窗口同时确认 |

做完第 1 步就能让前端接上，别等全部写完。

---

## 6. 两个人电脑不一致时怎么办

| 症状 | 原因 | 处理 |
| --- | --- | --- |
| `Unsupported class file major version` | JDK 太新（25/26） | 换 17 或 21 |
| `Access denied for user 'root'` | `application-local.yml` 没建或密码错 | 见第 0 节 |
| `Unknown database 'lab_selection'` | 没导入 `schema.sql` | 见 `framework/3.database/README.md` |
| `Communications link failure` | MySQL 服务没启动 | 服务里启动 MySQL80 |
| `WeakKeyException` | JWT 密钥少于 32 字符 | 换一串更长的 |
| 依赖下载卡住 | 没配国内镜像 | 见第 2 节 |
| `Port 8080 was already in use` | 端口被占 | 改 `application.yml` 的 `server.port` |
