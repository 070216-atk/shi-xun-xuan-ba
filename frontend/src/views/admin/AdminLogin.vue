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

      <el-alert type="info" :closable="false" style="margin-top:16px"
        title="本入口仅限学院管理员；学生与导师请从统一登录入口进入。" />

      <div class="demo" @click="fill">
        演示管理员账号：admin（密码 123456）
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

function fill() { username.value = 'admin'; password.value = '123456' }

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
.demo {
  margin-top: 14px; font-size: 13px; color: #94a3b8; cursor: pointer;
  border-top: 1px dashed #334155; padding-top: 12px;
}
.demo:hover { color: #38bdf8; }
.foot { margin-top: 14px; text-align: center; font-size: 13px; }
.foot a { color: #64748b; text-decoration: none; }
.foot a:hover { color: #94a3b8; }
</style>
