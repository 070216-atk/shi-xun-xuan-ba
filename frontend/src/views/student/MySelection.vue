<template>
  <div>
    <div class="page-title">我的选拔</div>

    <div class="card status-card">
      <div class="muted">当前状态</div>
      <div class="status-text">{{ status }}</div>
      <div class="muted">当前阶段：{{ currentStage.name }} ｜ 第 {{ store.round }} 轮</div>
      <div v-if="needAction" style="margin-top:8px">
        下一步：<b>{{ nextAction }}</b>
        <span v-if="store.currentStage === 5" class="deadline">（剩余 {{ countdown }}）</span>
      </div>
    </div>

    <div v-if="offers.length" class="card">
      <div class="page-title">拟录取意向</div>
      <el-alert v-if="pendingOffers.length > 1" type="warning" :closable="false" style="margin-bottom:12px"
        title="你收到 2 个及以上拟录取意向，请在截止时间前自主选择唯一去向；逾期未确认视为放弃，名额按递补顺位补充。" />
      <el-alert v-else-if="pendingOffers.length === 1 && offers.length === 1" type="success" :closable="false" style="margin-bottom:12px"
        title="你仅收到 1 个拟录取意向，系统已自动确认。" />
      <div v-for="o in offers" :key="o.labId" class="offer-row">
        <div>
          <b>{{ labById(o.labId).name }}</b>
          <el-tag :type="offerTag(o.status).type" size="small" style="margin-left:8px">{{ offerTag(o.status).label }}</el-tag>
        </div>
        <div v-if="o.status === 'pending' && pendingOffers.length > 1 && store.currentStage === 5">
          <el-button type="primary" size="small" @click="confirmOffer(o)">选择该去向</el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="page-title">我的填报记录</div>
      <template v-if="app">
        <el-tag v-for="id in app.labIds" :key="id" style="margin-right:8px">{{ labById(id).name }}</el-tag>
        <p class="muted">第 {{ store.round }} 轮 ｜ {{ app.time }} ｜ 内部调剂：{{ app.obeyInner ? '服从' : '不服从' }} ｜ 跨室调剂：{{ app.obeyCross ? '服从' : '不服从' }}</p>
      </template>
      <el-empty v-else description="本轮尚未填报" :image-size="60">
        <el-button v-if="store.currentStage === 3 || store.currentStage === 6" type="primary" @click="$router.push('/apply')">去填报</el-button>
      </el-empty>
    </div>

    <div v-if="store.currentStage === 6 && !store.finalResults[store.user.id]" class="card" style="text-align:center">
      <p>你尚未被录取，可参加第 {{ store.round }} 轮补录。</p>
      <el-button type="warning" @click="$router.push('/apply')">补录填报入口</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stages } from '../../mock/data'
import { store, labById, studentStatus } from '../../store'

const currentStage = computed(() => stages.find(s => s.id === store.currentStage))
const status = computed(() => studentStatus(store.user.id))
const offers = computed(() => store.offers[store.user.id] || [])
const pendingOffers = computed(() => offers.value.filter(o => o.status === 'pending'))
const app = computed(() => store.applications[`${store.user.id}:${store.round}`])

const needAction = computed(() => {
  if (store.currentStage === 3 && !app.value) return true
  if (store.currentStage === 5 && pendingOffers.value.length > 1) return true
  if (store.currentStage === 6 && !store.finalResults[store.user.id]) return true
  return false
})
const nextAction = computed(() => {
  if (store.currentStage === 3) return '尽快完成意向填报'
  if (store.currentStage === 5) return '在拟录取意向中选择唯一去向'
  if (store.currentStage === 6) return '重新填报补录志愿'
  return '等待学院通知'
})

const now = ref(Date.now())
const timer = setInterval(() => (now.value = Date.now()), 1000)
onUnmounted(() => clearInterval(timer))
const countdown = computed(() => {
  const ms = Math.max(0, store.stageDeadline - now.value)
  return `${Math.floor(ms / 3600000)}时${String(Math.floor(ms % 3600000 / 60000)).padStart(2, '0')}分${String(Math.floor(ms % 60000 / 1000)).padStart(2, '0')}秒`
})

function offerTag(s) {
  return {
    pending: { type: 'warning', label: '待确认' },
    confirmed: { type: 'success', label: '已确认' },
    declined: { type: 'info', label: '未选择' },
    expired: { type: 'danger', label: '逾期放弃' }
  }[s]
}

async function confirmOffer(o) {
  try {
    await ElMessageBox.confirm(`确认选择「${labById(o.labId).name}」为唯一去向？其余意向将视为放弃，此操作不可撤销。`, '确认去向', {
      confirmButtonText: '确认', cancelButtonText: '再想想', type: 'warning'
    })
  } catch { return }
  offers.value.forEach(x => { x.status = x.labId === o.labId ? 'confirmed' : 'declined' })
  store.finalResults[store.user.id] = o.labId
  store.notifications.unshift({ time: '刚刚', text: `你已确认去向：${labById(o.labId).name}`, read: false })
  ElMessage.success('确认成功')
}
</script>

<style scoped>
.status-card { background: linear-gradient(135deg, #eff6ff, #ecfdf5); }
.status-text { font-size: 26px; font-weight: 700; color: #1b6e5a; margin: 6px 0; }
.offer-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px dashed #e5e9f0; flex-wrap: wrap; }
.offer-row:last-child { border-bottom: none; }
</style>