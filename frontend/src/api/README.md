# 前端接后端 —— 迁移说明

> 现状：`src/store.js` 和 `src/mock/data.js` 是**纯前端演示版**，数据存在 localStorage 里，刷新不丢但没有后端。
> 目标：把页面逐个迁到真实接口，**不要一次性全改**，改一个页面验证一个。

---

## 一、已经准备好的东西（不需要你写）

| 文件 | 作用 |
| --- | --- |
| `src/api/http.js` | fetch 封装：自动带 token、统一处理 `{code,message,data}`、1001 自动清 token |
| `src/api/index.js` | **26 个接口的函数**，名字和参数跟 `framework/4.api/字段定义.md` 一一对应 |
| `vite.config.js` | 已配 `/api` → `http://localhost:8080` 代理 |

**跑起来的前提**：后端在 8080 启动（双击根目录 `启动后端.cmd`），然后 `npm run dev`。

---

## 二、迁移一个页面的标准步骤

以「实训室列表」为例，**三步**：

### 第 1 步：把 import 从 mock 换成 api

```diff
- import { labs } from '../mock/data'
+ import { ref, onMounted } from 'vue'
+ import { getLabs } from '../api'
```

### 第 2 步：页面数据改成 ref + onMounted 拉取

```js
const labs = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    labs.value = await getLabs()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
})
```

### 第 3 步：模板里的字段名对齐

后端返回的字段和 mock **不完全一样**，主要差异：

| mock 里 | 后端返回 | 说明 |
| --- | --- | --- |
| `lab.id: 'L1'` | `id: 1` + `code: '202'` | **主键是数字**，`L1` 这种编号在 `code` 里 |
| `lab.applied` 需要自己算 | `applied` 直接给 | |
| `stage.window: '11月2日 08:00–12:00'` | `startAt` / `endAt` / `remainingSeconds` | 展示文案要自己格式化 |
| `mentors[].name` | 多了 `id` / `quota` | 名额在**导师**身上 |
| `role: 'student'` | `'STUDENT'` | 枚举大写 |

---

## 三、按这个顺序迁（风险从低到高）

| 顺序 | 页面 | 用到的接口 | 注意 |
| --- | --- | --- | --- |
| 1 | `Login.vue` | `login()` | 登录成功要存 token；演示账号改成 `student01` / `teacher01` / `admin` |
| 2 | `Labs.vue` / `LabDetail.vue` | `getLabs` / `getLab` | 公开接口，不用登录 |
| 3 | `Home.vue` | `getStages` | 时间轴改用工整的时间格式 |
| 4 | `student/Apply.vue` | `getMyApplication` / `saveMyApplication` | 提交前要处理 `2001 不在时间窗口` |
| 5 | `student/MySelection.vue` | `getMyStatus` / `getMyOffers` | 状态文案用后端返回的 `statusText` |
| 6 | `student/Result.vue` | `getMyResult` | |
| 7 | `teacher/Applicants.vue` | `getApplicants` | |
| 8 | `teacher/Interview.vue` | `saveScore` | 四维 1–5 分 |
| 9 | `teacher/Lists.vue` | `getTeacherLists` / `saveTeacherLists` | 递补顺序即数组顺序 |
| 10 | `admin/*` | 各自对应 | 调剂三级要按顺序调用 |

---

## 四、两个坑

1. **别直接删 `mock/data.js`**。等最后一个页面迁完再删，否则中间状态会有页面白屏。
2. **`store.js` 里的 `studentStatus()` 可以整体换成后端的 `status` + `statusText`**，后端的推导和 PRD 一致，别在前端再写一遍判断。

---

## 五、错误码速查

| code | 含义 | 前端怎么处理 |
| --- | --- | --- |
| 0 | 成功 | — |
| 1001 | 未登录 / 过期 | 清 token，跳 `/login` |
| 1002 | 账号或密码错误 | 提示 |
| 1003 | 无权限 | 提示"当前角色不能做这个操作" |
| 2001 | 不在时间窗口 | 提示后端返回的 message（里面有具体时间段） |
| 2002 | 志愿数超限 / 重复 | 提示 |
| 2003 | 名额已满 | 提示 |
| 2004 | 调剂优先级顺序错误 | 提示"请先完成上一级调剂" |
| 2005 | 存在未处理异议 | 提示 |
