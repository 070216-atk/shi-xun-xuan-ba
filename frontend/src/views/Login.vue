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

      <!-- 已有账号：默认收起，点开是一条一条的列表 -->
      <div class="acct">
        <button type="button" class="acct-toggle" @click="open = !open">
          <span>已有账号</span>
          <span class="arrow" :class="{ open }">▾</span>
        </button>

        <el-collapse-transition>
          <div v-show="open">
            <div class="acct-list">
              <button v-for="d in accounts" :key="d.u" type="button" class="acct-row"
                :disabled="loading" @click="quickLogin(d)">
                <span class="pill" :class="d.role === '导师' ? 'teacher' : 'student'">{{ d.role }}</span>
                <span class="who">{{ d.who }}</span>
                <span class="acc">{{ d.u }}</span>
                <span class="go">→</span>
              </button>
            </div>
            <p class="acct-note">演示环境，点击任一账号直接进入</p>
          </div>
        </el-collapse-transition>
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
const open = ref(false)

const accounts = [
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

.acct { margin-top: 16px; }
.acct-toggle {
  width: 100%; display: flex; justify-content: center; align-items: center; gap: 6px;
  border: none; background: transparent; color: #64748b; font-size: 13px;
  cursor: pointer; font-family: inherit; padding: 6px 0;
}
.acct-toggle:hover { color: #1b6e5a; }
.arrow { display: inline-block; transition: transform .2s; font-size: 11px; }
.arrow.open { transform: rotate(180deg); }

.acct-list {
  margin-top: 6px; border: 1px solid #e2e8f0; border-radius: 10px;
  max-height: 196px; overflow-y: auto;
}
.acct-list::-webkit-scrollbar { width: 6px; }
.acct-list::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }

.acct-row {
  width: 100%; display: flex; align-items: center; gap: 9px;
  padding: 10px 12px; background: #fff; border: none;
  border-bottom: 1px solid #f1f5f9; cursor: pointer; font-family: inherit;
  text-align: left; transition: background .15s;
}
.acct-row:last-child { border-bottom: none; }
.acct-row:hover:not(:disabled) { background: #f0fdf4; }
.acct-row:disabled { opacity: .6; cursor: not-allowed; }
.pill {
  font-size: 11px; padding: 1px 7px; border-radius: 10px; flex-shrink: 0;
}
.pill.student { background: #dbeafe; color: #1e3a8a; }
.pill.teacher { background: #dcfce7; color: #15803d; }
.who { flex: 1; font-size: 13px; color: #334155; }
.acc { font-size: 12px; color: #94a3b8; }
.go { font-size: 13px; color: #cbd5e1; }
.acct-note { text-align: center; font-size: 11px; color: #a0aec0; margin: 8px 0 0; }

.foot { margin-top: 16px; text-align: center; font-size: 13px; }
.foot a { color: #64748b; text-decoration: none; }
.foot a:hover { color: #1b6e5a; }
.dot { margin: 0 6px; color: #cbd5e1; }
</style>
