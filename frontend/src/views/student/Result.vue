<template>
  <div>
    <div class="page-title">录取结果</div>

    <div class="card" style="text-align:center">
      <template v-if="finalLab">
        <el-result :icon="admission.icon" :title="admission.title"
          :sub-title="`你的去向：${finalLab.name}`">
          <template #extra>
            <p v-if="finalMentorName">导师：{{ finalMentorName }}</p>
            <p class="muted">地点：{{ finalLab.location }} ｜ 联系方式：{{ finalLab.contact }}</p>
            <p v-if="admission.hint" class="muted">{{ admission.hint }}</p>
          </template>
        </el-result>
      </template>
      <el-result v-else-if="latestAdmission" :icon="historyView.icon"
        :title="historyView.title"
        :sub-title="`你的去向：${latestAdmission.labName || labById(latestAdmission.labId)?.name || `实训室 ${latestAdmission.labId}`}`">
        <template #extra>
          <p v-if="latestAdmission.mentorName">导师：{{ latestAdmission.mentorName }}</p>
          <p class="muted">录取后不再参与后续轮次，无需再填报。</p>
          <p v-if="latestAdmission.publicity === 'PUBLISHED'" class="muted">
            公示中，如有异议请线下联系学院教务。
          </p>
          <p v-else-if="latestAdmission.publicity === 'ENDED'" class="muted">
            公示已结束，录取结果已生效。
          </p>
        </template>
      </el-result>
      <el-result v-else icon="info" title="本轮结果尚未产生"
        sub-title="请耐心等待各阶段选拔完成，最终结果将在此展示并公示 1 天。" />
    </div>

    <div v-if="history.length > 1" class="card">
      <div class="page-title">往轮录取记录</div>
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="h in history" :key="h.round" :label="`第 ${h.round} 轮`">
          {{ h.labName || labById(h.labId)?.name || `实训室 ${h.labId}` }}
          <span v-if="h.mentorName"> ｜ 导师：{{ h.mentorName }}</span>
          <el-tag v-if="h.publicity === 'PUBLISHED'" type="warning" size="small" style="margin-left:8px">公示中</el-tag>
          <el-tag v-else-if="h.publicity === 'ENDED'" type="success" size="small" style="margin-left:8px">已生效</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="card">
      <div class="page-title">我的意向与状态</div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="当前状态">{{ status || '未登录' }}</el-descriptions-item>
        <el-descriptions-item :label="`第 ${store.round} 轮填报`">
          {{ app ? `${app.labIds.length} 个意向（${app.time}）` : '尚未填报' }}
        </el-descriptions-item>
        <el-descriptions-item label="收到的意向数">{{ (store.offers[store.user?.id] || []).length }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-if="store.publicity.published" class="card">
      <div class="page-title">公示名单</div>
      <p class="muted">
        共 {{ store.publicityList.length }} 人。
        {{ store.publicity.ended ? '公示已结束，名单已生效。' : '公示期 1 天，如有异议请线下联系学院教务。' }}
      </p>
      <el-table :data="store.publicityList" size="small" max-height="400">
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column prop="labName" label="录取实训室" min-width="160" />
        <el-table-column prop="mentorName" label="导师" width="110" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { store, labById, studentStatus, loadStudent, loadPublicity, loadFinalHistory } from '../../store'

const history = ref([])
const latestAdmission = computed(() => history.value[history.value.length - 1] || null)

function refreshHistory() {
  loadFinalHistory().then((h) => (history.value = h)).catch(() => (history.value = []))
}

onMounted(async () => {
  if (store.user?.role === 'student') {
    // 先等状态接口回来；它不带公示起止时间，必须再让公开公示接口补上
    await loadStudent().catch(e => ElMessage.error(e.message))
  }
  await loadPublicity().catch(() => {})
  refreshHistory()
})

// 顶栏切轮次后，往轮记录也要跟着重算
watch(() => store.round, refreshHistory)

const finalLab = computed(() => {
  const lid = store.finalResults[store.user?.id]
  return lid ? labById(lid) : null
})
const finalMentorName = computed(() => {
  const offers = store.offers[store.user?.id] || []
  const hit = offers.find(o => o.status === 'confirmed' && o.mentorName)
  return hit ? hit.mentorName : ''
})

/** 公示三态：未发布 / 公示中 / 已结束，页面文案全部从这里取 */
const admission = computed(() => {
  if (store.publicity.ended) {
    return { icon: 'success', title: '正式入组', hint: '公示已结束，录取结果已生效。' }
  }
  if (store.publicity.published) {
    return {
      icon: 'warning',
      title: '公示中',
      hint: `你的录取结果正在公示${store.publicity.end ? `，公示期至 ${store.publicity.end}` : ''}；如有异议请线下联系学院教务。`
    }
  }
  return { icon: 'info', title: '已确认去向，待公示', hint: '学院尚未发布公示，发布后这里会显示「公示中」。' }
})

const historyView = computed(() => {
  const h = latestAdmission.value
  if (h?.publicity === 'ENDED') return { icon: 'success', title: `已正式入组（第 ${h.round} 轮）` }
  if (h?.publicity === 'PUBLISHED') return { icon: 'warning', title: `公示中（第 ${h.round} 轮）` }
  return { icon: 'success', title: `已录取（第 ${h?.round} 轮）` }
})
const status = computed(() =>
  store.studentStatus?.statusText || (store.user ? studentStatus(store.user.id) : ''))
const app = computed(() => store.applications[`${store.user?.id}:${store.round}`])
</script>
