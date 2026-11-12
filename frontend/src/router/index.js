import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { store, ensureSession } from '../store'

const routes = [
  // ---- 前台：统一登录（学生 / 导师）----
  { path: '/login', component: () => import('../views/Login.vue') },
  // ---- 管理后台：独立入口，独立登录页 ----
  { path: '/admin/login', component: () => import('../views/admin/AdminLogin.vue') },

  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', component: () => import('../views/Home.vue') },
      { path: 'labs', component: () => import('../views/Labs.vue') },
      { path: 'labs/:id', component: () => import('../views/LabDetail.vue') },
      { path: 'apply', component: () => import('../views/student/Apply.vue'), meta: { role: 'student' } },
      { path: 'my', component: () => import('../views/student/MySelection.vue'), meta: { role: 'student' } },
      { path: 'result', component: () => import('../views/student/Result.vue'), meta: { role: 'student' } },
      { path: 'teacher/applicants', component: () => import('../views/teacher/Applicants.vue'), meta: { role: 'teacher' } },
      { path: 'teacher/interview', component: () => import('../views/teacher/Interview.vue'), meta: { role: 'teacher' } },
      { path: 'teacher/lists', component: () => import('../views/teacher/Lists.vue'), meta: { role: 'teacher' } },
      { path: 'admin/stages', component: () => import('../views/admin/Stages.vue'), meta: { role: 'admin' } },
      { path: 'admin/quotas', component: () => import('../views/admin/Quotas.vue'), meta: { role: 'admin' } },
      { path: 'admin/adjust', component: () => import('../views/admin/Adjust.vue'), meta: { role: 'admin' } },
      { path: 'admin/publicity', component: () => import('../views/admin/Publicity.vue'), meta: { role: 'admin' } },
      { path: 'admin/dashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { role: 'admin' } },
      { path: 'admin/notices', component: () => import('../views/admin/Notices.vue'), meta: { role: 'admin' } },
      { path: 'admin/logs', component: () => import('../views/admin/Logs.vue'), meta: { role: 'admin' } }
    ]
  },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(async (to) => {
  if (!to.meta.role) return true

  // 整页刷新时先等登录态恢复完，再判断权限（否则会被误判成未登录）
  await ensureSession()

  // 没登录：管理员路由回管理后台登录页，其余回统一登录页
  if (!store.user) {
    return to.meta.role === 'admin' ? '/admin/login' : '/login'
  }
  if (store.user.role !== to.meta.role) {
    ElMessage.warning('当前账号无权访问该页面')
    return '/'
  }
  return true
})

export default router
