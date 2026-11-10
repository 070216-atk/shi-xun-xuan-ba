<template>
  <div class="login-bg">
    <div class="login-card">
      <h2>学生实训室进组选拔系统</h2>
      <p class="muted" style="text-align:center">双向选择 · 分轮录取 · 统筹调剂</p>

      <div class="role-tabs">
        <button v-for="r in roles" :key="r.key" type="button"
          class="role-tab" :class="{ active: role === r.key }" @click="switchRole(r.key)">
          {{ r.label }}
        </button>
      </div>

      <el-form label-position="top" @submit.prevent="onLogin">
        <el-form-item :label="currentRole.accountLabel">
          <el-input v-model="username" :placeholder="`请输入${currentRole.accountLabel}`" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password placeholder="请输入密码" size="large" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="onLogin">
          以{{ currentRole.label }}身份登录
        </el-button>
      </el-form>

      <el-divider>演示账号（一键填入）</el-divider>
      <div class="demo-btns">
        <el-button v-for="d in currentRole.demos" :key="d.u" size="small" @click="fill(d)">{{ d.label }}</el-button>
      </div>
      <p class="muted" style="margin-top:12px">
        演示密码均为 123456。账号身份须与所选页签一致，学生账号不能登录导师/管理员入口，反之亦然。
      </p>
      <p class="muted" style="margin-top:4px;font-size:12px">
        需要先启动后端（8080），否则登录会提示「连不上后端」。
      </p>
      <div style="text-align:center;margin-top:8px">
        <router-link to="/" class="muted">不登录，先浏览公开信息 →</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, loadPublic } from '../store'

const router = useRouter()
const role = ref('student')
const username = ref('')
const password = ref('')
const loading = ref(false)

const roles = [
  {
    key: 'student', label: '学生', accountLabel: '学号 / 账号',
    demos: [{ u: 'student01', label: '李明' }, { u: 'student02', label: '王芳' }]
  },
  {
    key: 'teacher', label: '导师', accountLabel: '教师工号',
    demos: [{ u: 'teacher01', label: '刘冠军（202 室）' }, { u: 'teacher03', label: '高璇（404 室）' }]
  },
  {
    key: 'admin', label: '学院管理员', accountLabel: '管理员工号',
    demos: [{ u: 'admin', label: '学院管理员' }]
  }
]
const currentRole = computed(() => roles.find(r => r.key === role.value))

function switchRole(key) {
  role.value = key
  username.value = ''
  password.value = ''
}
function fill(d) { username.value = d.u; password.value = '123456' }

async function onLogin() {
  if (!username.value.trim()) return ElMessage.warning('请输入账号')
  if (!password.value) return ElMessage.warning('请输入密码')
  loading.value = true
  try {
    const result = await login(username.value.trim(), password.value, role.value)
    if (result === 'fail') return ElMessage.error('账号或密码错误')
    if (result === 'mismatch') {
      return ElMessage.error(`该账号不是${currentRole.value.label}账号，请切换到正确的身份页签登录`)
    }
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
.login-bg { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #dbeafe, #d1fae5); padding: 16px; }
.login-card { background: #fff; border-radius: 14px; padding: 28px 24px; width: 100%; max-width: 400px; box-shadow: 0 8px 30px rgba(30,58,138,.15); }
.login-card h2 { text-align: center; color: #1e3a8a; font-size: 20px; margin: 0 0 4px; }
.role-tabs { display: flex; gap: 4px; background: #f0f4f8; border-radius: 10px; padding: 4px; margin: 16px 0; }
.role-tab { flex: 1; border: none; background: transparent; padding: 10px 4px; border-radius: 8px; font-size: 14px; color: #5a6b7f; cursor: pointer; font-family: inherit; }
.role-tab.active { background: #1b6e5a; color: #fff; font-weight: 600; }
.demo-btns { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.demo-btns .el-button { margin-left: 0; }
</style>
