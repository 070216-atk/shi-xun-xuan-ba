<template>
  <div>
    <div class="page-title">我的选拔</div>

    <div class="card status-card">
      <div class="muted">当前状态</div>
      <div class="status-text">
        {{ status }}
        <el-tag v-if="inPublicity" type="warning" size="small" style="margin-left:8px;vertical-align:middle">公示中</el-tag>
      </div>
      <div class="muted">
        当前阶段：{{ currentStage?.name || '未开放' }} ｜ 第 {{ store.round }} 轮
      </div>
      <div v-if="needAction" style="margin-top:8px">
        下一步：<b>{{ nextAction }}</b>
        <span v-if="store.stageDeadline && store.currentStage === 5" class="deadline">（剩余 {{ countdown }}）</span>
      </div>
    </div>

    <div v-if="offers.length" class="card">
      <div class="page-title">录取意向</div>
      <el-alert v-if="pendingAdmitted.length > 1" type="warning" :closable="false" style="margin-bottom:12px"
        title="你收到 2 个及以上拟录取意向，请在截止时间前自主选择唯一去向；逾期未确认视为放弃，名额按递补顺位补充。" />
      <el-alert v-else-if="pendingAdmitted.length === 1" type="success" :closable="false" style="margin-bottom:12px"
        title="你收到 1 个拟录取意向，进入「拟录取与学生确认」阶段后系统会自动确认；也可以直接点下方按钮确认。" />

      <div v-for="o in offers" :key="o.offerId || o.labId" class="offer-row">
        <div>
          <b>{{ o.labName || labName(o.labId) }}</b>
          <span v-if="o.mentorName" class="muted"> · 导师 {{ o.mentorName }}</span>
          <el-tag :type="offerTag(o.status).type" size="small" style="margin-left:8px">{{ offerTag(o.status).label }}</el-tag>
          <el-tag v-if="o.type === 'WAITLIST'" type="info" size="small" style="margin-left:4px">递补</el-tag>
          <el-tag v-if="o.type === 'ADJUST_CANDIDATE'" type="info" size="small" style="margin-left:4px">调剂候选</el-tag>
        </div>
        <div v-if="o.status === 'pending' && o.type === 'ADMITTED'">
          <el-button type="primary" size="small" :loading="confirming" @click="doConfirm(o)">
            {{ pendingAdmitted.length > 1 ? '选择该去向' : '确认该去向' }}
          </el-button>
        </div>
        <div v-else-if="o.status === 'confirmed' && o.type === 'ADMITTED'">
          <el-button size="small" type="warning" plain :loading="releasing" @click="doRelease(o)">撤销确认</el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="page-title">我的填报记录</div>
      <template v-if="app">
        <el-tag v-for="(lid, i) in app.labIds" :key="i" style="margin:0 8px 8px 0">
          {{ labName(lid) }}<span v-if="mentorName(lid, app.mentorIds?.[i])"> · {{ mentorName(lid, app.mentorIds[i]) }}</span>
        </el-tag>
        <p class="muted">
          第 {{ store.round }} 轮 ｜ {{ app.time }} ｜ 内部调剂：{{ app.obeyInner ? '服从' : '不服从' }} ｜
          跨室调剂：{{ app.obeyCross ? '服从' : '不服从' }}
        </p>
      </template>
      <el-empty v-else :description="alreadyAdmitted ? '你已被录取，后续轮次无需填报' : '本轮尚未填报'" :image-size="60">
        <el-button v-if="store.currentStage === 3 && !alreadyAdmitted" type="primary" @click="$router.push('/apply')">去填报</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stages, store, labById, studentStatus, loadStudent, confirmOffer, releaseOffer } from '../../store'

onMounted(() => {
  if (store.user?.role === 'student') loadStudent().catch(e => ElMessage.error(e.message))
})

const currentStage = computed(() => stages.find(s => s.id === store.currentStage))
const status = computed(() =>
  store.studentStatus?.statusText || (store.user ? studentStatus(store.user.id) : ''))
/** 后端状态文案在公示期间是"…公示中"，这里据此给状态加个醒目标记 */
const inPublicity = computed(() => (status.value || '').includes('公示中'))
const offers = computed(() => store.offers[store.user?.id] || [])
/** 待确认的拟录取（递补/调剂候选不算，它们不需要学生操作） */
const pendingAdmitted = computed(() =>
  offers.value.filter(o => o.status === 'pending' && o.type === 'ADMITTED'))
const app = computed(() => store.applications[`${store.user?.id}:${store.round}`])
const alreadyAdmitted = computed(() => ['ADMITTED', 'CONFIRMED'].includes(store.studentStatus?.status))

const needAction = computed(() => {
  if (alreadyAdmitted.value) return false
  if (store.currentStage === 3 && !app.value) return true
  // 只要还有待确认的拟录取，就需要学生操作（1 个也要确认，不能只靠自动结算）
  if (pendingAdmitted.value.length > 0) return true
  return false
})
const nextAction = computed(() => {
  if (store.currentStage === 3 && !app.value) return '尽快完成意向填报'
  if (pendingAdmitted.value.length > 1) return '在拟录取意向中选择唯一去向'
  if (pendingAdmitted.value.length === 1) return '确认你的录取去向'
  return '等待学院通知'
})

const now = ref(Date.now())
const timer = setInterval(() => (now.value = Date.now()), 1000)
onUnmounted(() => clearInterval(timer))
const countdown = computed(() => {
  const ms = Math.max(0, store.stageDeadline - now.value)
  return `${Math.floor(ms / 3600000)}时${String(Math.floor(ms % 3600000 / 60000)).padStart(2, '0')}分${String(Math.floor(ms % 60000 / 1000)).padStart(2, '0')}秒`
})

function labName(id) { return labById(id)?.name || (id ? `实训室 ${id}` : '—') }
function mentorName(labId, mentorId) {
  const m = (labById(labId)?.mentors || []).find(x => x.id === mentorId)
  return m ? m.name : ''
}

function offerTag(s) {
  return {
    pending: { type: 'warning', label: '待确认' },
    confirmed: { type: 'success', label: '已确认' },
    declined: { type: 'info', label: '未选择' },
    expired: { type: 'danger', label: '逾期放弃' },
    void: { type: 'info', label: '已作废' }
  }[s] || { type: 'info', label: s }
}

const confirming = ref(false)
const releasing = ref(false)

async function doRelease(o) {
  try {
    await ElMessageBox.confirm(
      `撤销对「${o.labName || labName(o.labId)}」的确认？该名额会立刻让给这位导师的递补第一名，你可以在本阶段重新选择。`,
      '撤销确认', { confirmButtonText: '撤销', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch { return }
  releasing.value = true
  try {
    const r = await releaseOffer(o.offerId)
    ElMessage.success(r.promotedStudentId ? '已撤销，名额已让给递补同学' : '已撤销确认')
  } catch (e) {
    ElMessage.error(e.message || '撤销失败')
  } finally {
    releasing.value = false
  }
}
async function doConfirm(o) {
  try {
    await ElMessageBox.confirm(
      `确认选择「${o.labName || labName(o.labId)}」为去向？确认后仍可「撤销确认」改选，但名额会立刻让给该导师的递补第一名。`,
      '确认去向', { confirmButtonText: '确认', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch { return }

  confirming.value = true
  try {
    await confirmOffer(o.offerId)
    store.notifications.unshift({ time: '刚刚', text: `你已确认去向：${o.labName || labName(o.labId)}`, read: false })
    ElMessage.success('确认成功')
  } catch (e) {
    ElMessage.error(e.message || '确认失败')
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.status-card { background: linear-gradient(135deg, #eff6ff, #ecfdf5); }
.status-text { font-size: 26px; font-weight: 700; color: #1b6e5a; margin: 6px 0; }
.offer-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px dashed #e5e9f0; flex-wrap: wrap; }
.offer-row:last-child { border-bottom: none; }
</style>
