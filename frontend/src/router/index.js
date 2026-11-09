import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { store } from '../store'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
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
      { path: 'admin/dashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { role: 'admin' } }
    ]
  }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(to => {
  if (!to.meta.role) return true
  if (!store.user) return '/login'
  if (store.user.role !== to.meta.role) {
    ElMessage.warning('当前角色无权访问该页面')
    return '/'
  }
  return true
})

export default router