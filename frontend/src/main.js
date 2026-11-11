import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './style.css'
import { loadPublic, restoreSession } from './store'

const app = createApp(App).use(router).use(ElementPlus, { locale: zhCn })

async function bootstrap() {
  // 1. 如果本地有 token，先用 /auth/me 恢复登录态（刷新页面不掉登录）
  await restoreSession().catch(() => {})
  // 2. 拉公开数据（实训室、阶段）；后端没启动也不阻塞渲染
  await loadPublic().catch(() => {})
  app.mount('#app')
}

bootstrap()
