# 后端（Spring Boot 3.3 + MyBatis + MySQL 8）

## 0. 先解决 Java 版本问题（必读）

本机有 **三个** Java：

| 位置 | 版本 | 说明 |
| --- | --- | --- |
| PATH 里的 `java` | **26** | 太新，Spring Boot 3.3 起不来 |
| `JAVA_HOME` 当前指向 | 25（Zulu） | 也偏新 |
| `C:\Program Files\Java\jdk-21` | **21 LTS** | ✅ 用这个 |

**所以每次开新终端，先执行这一行**（只影响当前窗口）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java --version    # 应显示 21.0.9
```

> 注意是 `java --version`（两个横线）。这个环境里 `java -version`（一个横线）没有任何输出，别被它骗到，不是坏。

Maven 不在 PATH 里，用项目外那个已经装好的：

```powershell
$mvn = "E:\学校文件\大二上作业\商城\tools\apache-maven-3.9.16\bin\mvn.cmd"
```

## 1. 启动

```powershell
cd E:\学校文件\大二上作业\shi-xun-xuan-ba\backend
& $mvn spring-boot:run
```

成功的标志：控制台出现 `Tomcat started on port 8080` 和 `Started SelectionApplication`。

浏览器访问 `http://localhost:8080/api/labs` 应该有返回（前提是你已经写了这个接口）。

## 2. 目录约定（照这个建，别自己发明）

```
backend/src/main/
├── java/com/college/selection/
│   ├── SelectionApplication.java   ✅ 已有
│   ├── controller/                 接请求、校验参数、包装返回
│   ├── service/                    业务规则：时间窗、自动确认、递补、调剂优先级
│   ├── mapper/                     MyBatis 接口（加 @Mapper）
│   ├── entity/                     与表一一对应
│   ├── dto/                        请求/响应对象
│   └── config/                     跨域、异常处理、拦截器
└── resources/
    ├── application.yml             ✅ 已有（记得填数据库密码）
    └── mapper/                     SQL XML
```

**三条纪律**（写错了后期很难改）：

1. **controller 里不许写业务规则**。判断时间窗、算名额、改状态，全部放 service。
2. **不把 entity 直接返回给前端**。用 dto，否则表结构一改前端就崩。
3. **学生状态只能由 service 改**。这是架构设计 §4.2 的硬要求。

## 3. 建议的实现顺序

按这个顺序做，每一步都能立刻验证：

| 顺序 | 做什么 | 验证方式 |
| --- | --- | --- |
| 1 | `GET /api/labs`（最简单，只有一张表） | 浏览器直接访问 |
| 2 | `POST /api/auth/login`（先返回假 token 也行） | Postman / curl |
| 3 | `GET /api/stages`（时间窗） | 对比 `seed.sql` 里的数据 |
| 4 | `PUT /api/student/application`（最复杂的校验） | 试试填 4 个意向会不会被拦 |
| 5 | `GET /api/student/offers` + `confirm`（并发在这里） | 开两个窗口同时确认 |

做完第 1 步就可以让前端接上，**别等全部写完再联调**。

## 4. 两个必须先问清楚的点

1. **`vite.config.js` 还没配代理**（架构设计说 `/api` → 8080，实际文件里没有）。
   前端现在直接打 `/api/...` 会 404。这个要跟前端说，让他加：
   ```js
   server: { port: 5173, host: true,
     proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } }
   ```
2. **登录时的密码比对**：`seed.sql` 里的 `password_hash` 是占位符，见
   `framework/3.database/README.md` 第 2 节。建议写个 `CommandLineRunner` 启动时建账号。
