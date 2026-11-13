<template>
  <div>
    <header class="topbar">
      <div class="topbar-inner">
        <router-link to="/" class="brand">
          {{ store.user?.role === 'admin' ? '实训室进组选拔系统 · 管理后台' : '实训室进组选拔系统' }}
        </router-link>
        <div class="user-area">
          <template v-if="store.user">
            <el-select v-if="store.rounds.length > 1" :model-value="store.round" size="small"
              class="round-select" @change="onRoundChange">
              <el-option v-for="r in store.rounds" :key="r.roundNo" :value="r.roundNo"
                :label="`第 ${r.roundNo} 轮 · ${roundText(r.status)}`" />
            </el-select>
            <el-badge :is-dot="unread > 0" class="bell">
              <el-button size="small" text style="color:#fff" @click="openNotif">通知</el-button>
            </el-badge>
            <span class="uname">{{ store.user.name }}（{{ roleName }}）</span>
            <el-button size="small" @click="onLogout">退出</el-button>
          </template>
          <router-link v-else :to="isAdminArea ? '/admin/login' : '/login'">
            <el-button size="small" type="warning">登录</el-button>
          </router-link>
        </div>
      </div>
      <nav class="nav">
        <router-link v-for="m in menus" :key="m.path" :to="m.path" class="nav-item"
          active-class="active" exact-active-class="active">{{ m.label }}</router-link>
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { store, logout, setRound } from '../store'

const router = useRouter()
const route = useRoute()
const showNotif = ref(false)
const unread = computed(() => store.notifications.filter(n => !n.read).length)

function openNotif() {
  showNotif.value = true
  store.notifications.forEach(n => { n.read = true })
}
const roleName = computed(() => ({ student: '学生', teacher: '导师', admin: '学院管理员' }[store.user?.role] || ''))
const roundText = (s) => ({ OPEN: '进行中', CLOSED: '已结束', PLANNING: '未开启' }[s] || s)

async function onRoundChange(r) {
  try {
    await setRound(r)
    ElMessage.success(`已切换到第 ${r} 轮`)
  } catch (e) {
    ElMessage.error(e.message || '切换轮次失败')
  }
}
const isAdminArea = computed(() => route.path.startsWith('/admin'))

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
    { path: '/admin/dashboard', label: '数据看板' },
    { path: '/admin/notices', label: '公告管理' },
    { path: '/admin/logs', label: '操作日志' }
  ]
}
const menus = computed(() => {
  const role = store.user?.role
  // 管理员只看到后台菜单，学生/导师看到公开 + 自己的菜单
  return role === 'admin' ? roleMenus.admin : [...publicMenus, ...(roleMenus[role] || [])]
})

function onLogout() {
  const wasAdmin = store.user?.role === 'admin'
  logout()
  router.push(wasAdmin ? '/admin/login' : '/login')
}
</script>

<style scoped>
.topbar { background: linear-gradient(135deg, #1e3a8a, #1b6e5a); color: #fff; }
.topbar-inner { max-width: 1100px; margin: 0 auto; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.brand { color: #fff; font-size: 18px; font-weight: 700; text-decoration: none; }
.user-area { display: flex; align-items: center; gap: 10px; }
.uname { font-size: 13px; }
.round-select { width: 148px; }
.nav { max-width: 1100px; margin: 0 auto; padding: 0 8px 8px; display: flex; gap: 4px; overflow-x: auto; white-space: nowrap; }
.nav-item { color: rgba(255,255,255,.85); text-decoration: none; padding: 6px 12px; border-radius: 16px; font-size: 14px; flex-shrink: 0; }
.nav-item.active { background: rgba(255,255,255,.22); color: #fff; font-weight: 600; }
</style>
