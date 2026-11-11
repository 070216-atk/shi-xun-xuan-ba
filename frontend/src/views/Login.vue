<template>
  <div class="login-bg">
    <div class="login-card">
      <div class="brand-mark">实训室进组选拔</div>
      <h2>统一身份登录</h2>

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

      <div class="quick">
        <div class="quick-title"><span>快捷登录</span></div>
        <div class="quick-grid">
          <button v-for="d in quicks" :key="d.u" type="button" class="quick-btn"
            :disabled="loading" @click="quickLogin(d)">
            <span class="role">{{ d.role }}</span>
            <span class="who">{{ d.who }}</span>
          </button>
        </div>
        <p class="quick-note">演示环境，一键以对应身份进入</p>
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

// 快捷登录：演示环境用，点一下直接以该身份进入
const quicks = [
  { u: 'student01', role: '学生', who: '李明' },
  { u: 'student02', role: '学生', who: '王芳' },
  { u: 'teacher01', role: '导师', who: '刘冠军 · 202 室' },
  { u: 'teacher03', role: '导师', who: '高璇 · 404 室' }
]

async function quickLogin(d) {
  username.value = d.u
  password.value = '123456'
  await onLogin()
}

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
.quick { margin-top: 20px; }
.quick-title {
  display: flex; align-items: center; gap: 10px; color: #94a3b8; font-size: 12px;
  margin-bottom: 12px;
}
.quick-title::before, .quick-title::after {
  content: ''; flex: 1; height: 1px; background: #e5e9f0;
}
.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.quick-btn {
  border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 9px; padding: 9px 10px;
  cursor: pointer; text-align: left; font-family: inherit; transition: .15s;
}
.quick-btn:hover:not(:disabled) { border-color: #1b6e5a; background: #ecfdf5; }
.quick-btn:disabled { opacity: .6; cursor: not-allowed; }
.quick-btn .role { display: block; font-size: 11px; color: #1b6e5a; font-weight: 600; }
.quick-btn .who { display: block; font-size: 12px; color: #475569; margin-top: 2px; }
.quick-note { text-align: center; font-size: 11px; color: #a0aec0; margin: 10px 0 0; }
.foot { margin-top: 16px; text-align: center; font-size: 13px; }
.foot a { color: #64748b; text-decoration: none; }
.foot a:hover { color: #1b6e5a; }
.dot { margin: 0 6px; color: #cbd5e1; }
</style>
