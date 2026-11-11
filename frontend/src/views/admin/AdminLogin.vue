<template>
  <div class="admin-bg">
    <div class="admin-card">
      <div class="logo-row">
        <div class="logo">管</div>
        <div>
          <h2>学院管理后台</h2>
          <p class="muted">实训室进组选拔系统 · 管理员专用</p>
        </div>
      </div>

      <el-form label-position="top" @submit.prevent="onLogin" style="margin-top:22px">
        <el-form-item label="管理员工号">
          <el-input v-model="username" placeholder="请输入管理员工号" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password
            placeholder="请输入密码" size="large" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="onLogin">
          进入后台
        </el-button>
      </el-form>

      <el-alert type="info" :closable="false" style="margin-top:16px;margin-bottom:0"
        title="本入口仅限学院管理员；学生与导师请从统一登录入口进入。" />

      <div class="acct">
        <button type="button" class="acct-toggle" @click="open = !open">
          <span>已有账号</span>
          <span class="arrow" :class="{ open }">▾</span>
        </button>

        <el-collapse-transition>
          <div v-show="open">
            <div class="acct-list">
              <button type="button" class="acct-row" :disabled="loading" @click="quickLogin">
                <span class="pill">管理员</span>
                <span class="who">学院管理员</span>
                <span class="acc">admin</span>
                <span class="go">→</span>
              </button>
            </div>
            <p class="acct-note">演示环境，点击直接进入</p>
          </div>
        </el-collapse-transition>
      </div>

      <div class="foot">
        <router-link to="/login">← 返回统一登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, loadPublic } from '../../store'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const open = ref(false)

function quickLogin() {
  username.value = 'admin'
  password.value = '123456'
  onLogin()
}

async function onLogin() {
  if (!username.value.trim()) return ElMessage.warning('请输入管理员工号')
  if (!password.value) return ElMessage.warning('请输入密码')

  loading.value = true
  try {
    const r = await login(username.value.trim(), password.value, 'admin')
    if (r === 'fail') return ElMessage.error('账号或密码错误')
    if (r === 'forbidden') return ElMessage.error('该账号不是管理员，请从统一登录入口进入')
    await loadPublic()
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.admin-bg {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #0f172a; padding: 16px;
}
.admin-card {
  background: #1e293b; border-radius: 14px; padding: 30px 26px; width: 100%; max-width: 400px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, .45); border: 1px solid #334155;
}
.logo-row { display: flex; align-items: center; gap: 12px; }
.logo {
  width: 42px; height: 42px; border-radius: 10px; background: #1b6e5a; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700;
}
.admin-card h2 { color: #e2e8f0; font-size: 18px; margin: 0; }
.admin-card .muted { color: #94a3b8; font-size: 12px; margin: 4px 0 0; }
.admin-card :deep(.el-form-item__label) { color: #cbd5e1; }

.acct { margin-top: 14px; }
.acct-toggle {
  width: 100%; display: flex; justify-content: center; align-items: center; gap: 6px;
  border: none; background: transparent; color: #64748b; font-size: 13px;
  cursor: pointer; font-family: inherit; padding: 6px 0;
}
.acct-toggle:hover { color: #38bdf8; }
.arrow { display: inline-block; transition: transform .2s; font-size: 11px; }
.arrow.open { transform: rotate(180deg); }

.acct-list {
  margin-top: 6px; border: 1px solid #334155; border-radius: 10px;
  max-height: 196px; overflow-y: auto;
}
.acct-list::-webkit-scrollbar { width: 6px; }
.acct-list::-webkit-scrollbar-thumb { background: #475569; border-radius: 3px; }

.acct-row {
  width: 100%; display: flex; align-items: center; gap: 9px;
  padding: 10px 12px; background: #24334a; border: none;
  border-bottom: 1px solid #334155; cursor: pointer; font-family: inherit;
  text-align: left; transition: background .15s;
}
.acct-row:last-child { border-bottom: none; }
.acct-row:hover:not(:disabled) { background: #2b3d57; }
.acct-row:disabled { opacity: .6; cursor: not-allowed; }
.pill { font-size: 11px; padding: 1px 7px; border-radius: 10px; background: #134e4a; color: #5eead4; flex-shrink: 0; }
.who { flex: 1; font-size: 13px; color: #e2e8f0; }
.acc { font-size: 12px; color: #64748b; }
.go { font-size: 13px; color: #64748b; }
.acct-note { text-align: center; font-size: 11px; color: #64748b; margin: 8px 0 0; }

.foot { margin-top: 14px; text-align: center; font-size: 13px; }
.foot a { color: #64748b; text-decoration: none; }
.foot a:hover { color: #94a3b8; }
</style>
