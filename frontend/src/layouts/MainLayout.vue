<template>
  <div>
    <header class="topbar">
      <div class="topbar-inner">
        <router-link to="/" class="brand">实训室进组选拔系统</router-link>
        <div class="user-area">
          <template v-if="store.user">
            <el-badge :is-dot="unread > 0" class="bell">
              <el-button size="small" text style="color:#fff" @click="showNotif = true">通知</el-button>
            </el-badge>
            <span class="uname">{{ store.user.name }}（{{ roleName }}）</span>
            <el-button size="small" @click="onLogout">退出</el-button>
          </template>
          <router-link v-else to="/login"><el-button size="small" type="warning">登录</el-button></router-link>
        </div>
      </div>
      <nav class="nav">
        <router-link v-for="m in menus" :key="m.path" :to="m.path" class="nav-item" active-class="active" exact-active-class="active">{{ m.label }}</router-link>
      </nav>
    </header>
    <main class="page">
      <router-view />
    </main>
    <el-dialog v-model="showNotif" title="站内通知" width="90%" style="max-width:480px">
      <div v-for="(n, i) in store.notifications" :key="i" style="padding:8px 0;border-bottom:1px solid #eee">
        <div class="muted">{{ n.time }}</div>
        <div>{{ n.text }}</div>
      </div>
      <el-empty v-if="!store.notifications.length" description="暂无通知" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { store, logout } from '../store'

const router = useRouter()
const showNotif = ref(false)
const unread = computed(() => store.notifications.filter(n => !n.read).length)
const roleName = computed(() => ({ student: '学生', teacher: '导师', admin: '学院管理员' }[store.user?.role] || ''))

const publicMenus = [
  { path: '/', label: '首页' },
  { path: '/labs', label: '实训室' }
]
const roleMenus = {
  student: [
    { path: '/apply', label: '意向填报' },
    { path: '/my', label: '我的选拔' },
    { path: '/result', label: '录取结果' }
  ],
  teacher: [
    { path: '/teacher/applicants', label: '报名学生' },
    { path: '/teacher/interview', label: '面试评价' },
    { path: '/teacher/lists', label: '名单管理' }
  ],
  admin: [
    { path: '/admin/stages', label: '流程配置' },
    { path: '/admin/quotas', label: '名额与补录' },
    { path: '/admin/adjust', label: '统筹调剂' },
    { path: '/admin/publicity', label: '公示管理' },
    { path: '/admin/dashboard', label: '数据看板' }
  ]
}
const menus = computed(() => [...publicMenus, ...(roleMenus[store.user?.role] || [])])

function onLogout() {
  logout()
  router.push('/login')
}
</script>

<style scoped>
.topbar { background: linear-gradient(135deg, #1e3a8a, #1b6e5a); color: #fff; }
.topbar-inner { max-width: 1100px; margin: 0 auto; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.brand { color: #fff; font-size: 18px; font-weight: 700; text-decoration: none; }
.user-area { display: flex; align-items: center; gap: 10px; }
.uname { font-size: 13px; }
.nav { max-width: 1100px; margin: 0 auto; padding: 0 8px 8px; display: flex; gap: 4px; overflow-x: auto; white-space: nowrap; }
.nav-item { color: rgba(255,255,255,.85); text-decoration: none; padding: 6px 12px; border-radius: 16px; font-size: 14px; flex-shrink: 0; }
.nav-item.active { background: rgba(255,255,255,.22); color: #fff; font-weight: 600; }
</style>