import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './style.css'
import { ensureSession, loadPublic, loadRounds } from './store'

const app = createApp(App).use(router).use(ElementPlus, { locale: zhCn })

// 全局兜底：组件里没接住的异常，也不要让页面静默白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue error]', info, err)
  ElMessage.error('页面出错了：' + (err?.message || err) + '（详见控制台）')
}
window.addEventListener('unhandledrejection', (e) => {
  console.error('[unhandled promise]', e.reason)
})

async function bootstrap() {
  // 1. 如果本地有 token，先用 /auth/me 恢复登录态（刷新页面不掉登录）
  await ensureSession()
  // 2. 先定轮次（默认落在"进行中"的那一轮），再拉公开数据（实训室、阶段）
  await loadRounds(true).catch(() => {})
  await loadPublic().catch(() => {})
  app.mount('#app')
}

bootstrap()

