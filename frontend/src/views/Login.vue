<template>
  <div class="login-bg">
    <div class="login-card">
      <div class="brand-mark">实训室进组选拔</div>
      <h2>统一身份登录</h2>
      <p class="muted" style="text-align:center">学生 / 导师 使用同一入口，系统按账号身份自动分流</p>

      <el-form label-position="top" @submit.prevent="onLogin" style="margin-top:18px">
        <el-form-item label="学号 / 工号">
          <el-input v-model="username" placeholder="请输入学号或工号" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password
            placeholder="请输入密码" size="large" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="onLogin">
          登录
        </el-button>
      </el-form>

      <div class="demo">
        <div class="demo-head" @click="showDemo = !showDemo">
          演示账号 {{ showDemo ? '收起' : '展开' }}
        </div>
        <div v-if="showDemo" class="demo-body">
          <div v-for="d in demos" :key="d.u" class="demo-row">
            <span>{{ d.label }}</span>
            <el-button size="small" link type="primary" @click="fill(d)">{{ d.u }}</el-button>
          </div>
          <p class="muted" style="margin-top:6px">密码都是 123456</p>
        </div>
      </div>

      <div class="foot">
        <router-link to="/">先浏览公开信息 →</router-link>
        <span class="dot">·</span>
        <router-link to="/admin/login">管理后台</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, loadPublic } from '../store'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const showDemo = ref(false)

const demos = [
  { u: 'student01', label: '学生 李明' },
  { u: 'student02', label: '学生 王芳' },
  { u: 'teacher01', label: '导师 刘冠军（202 室）' },
  { u: 'teacher03', label: '导师 高璇（404 室）' }
]

function fill(d) { username.value = d.u; password.value = '123456' }

async function onLogin() {
  if (!username.value.trim()) return ElMessage.warning('请输入学号或工号')
  if (!password.value) return ElMessage.warning('请输入密码')

  loading.value = true
  try {
    const r = await login(username.value.trim(), password.value, 'web')
    if (r === 'fail') return ElMessage.error('账号或密码错误')
    if (r === 'forbidden') return ElMessage.error('管理员账号请从「管理后台」入口登录')
    await loadPublic()
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #dbeafe, #d1fae5); padding: 16px;
}
.login-card {
  background: #fff; border-radius: 14px; padding: 28px 24px; width: 100%; max-width: 400px;
  box-shadow: 0 8px 30px rgba(30, 58, 138, .15);
}
.brand-mark { text-align: center; font-size: 12px; letter-spacing: 3px; color: #1b6e5a; }
.login-card h2 { text-align: center; color: #1e3a8a; font-size: 19px; margin: 6px 0 2px; }
.demo { margin-top: 18px; border-top: 1px dashed #e5e9f0; padding-top: 12px; }
.demo-head { font-size: 13px; color: #64748b; cursor: pointer; user-select: none; }
.demo-body { margin-top: 8px; font-size: 13px; }
.demo-row { display: flex; justify-content: space-between; align-items: center; padding: 2px 0; color: #475569; }
.foot { margin-top: 16px; text-align: center; font-size: 13px; }
.foot a { color: #64748b; text-decoration: none; }
.foot a:hover { color: #1b6e5a; }
.dot { margin: 0 6px; color: #cbd5e1; }
</style>
