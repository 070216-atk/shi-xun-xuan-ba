import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './style.css'
import { loadPublic } from './store'

const app = createApp(App).use(router).use(ElementPlus, { locale: zhCn })

// 先把公开数据（实训室、阶段）拉下来；后端没启动也不阻塞页面渲染
loadPublic()
  .catch(() => {})
  .finally(() => app.mount('#app'))
