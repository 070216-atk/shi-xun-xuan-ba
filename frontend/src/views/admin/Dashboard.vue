<template>
  <div>
    <div class="page-title">数据看板</div>
    <el-row :gutter="12">
      <el-col v-for="s in stats" :key="s.label" :xs="12" :sm="8" :md="6">
        <div class="card stat">
          <div class="stat-num" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="muted">{{ s.label }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="card">
      <div class="page-title">各实训室录取进度</div>
      <div v-for="l in labProgress" :key="l.name" style="margin-bottom:14px">
        <div style="display:flex;justify-content:space-between;margin-bottom:4px">
          <span>{{ l.name }}</span>
          <span class="muted">{{ l.confirmed }}/{{ l.quota }}（报名 {{ l.applied }}）</span>
        </div>
        <el-progress :percentage="l.quota ? Math.round(l.confirmed / l.quota * 100) : 0" :stroke-width="14" />
      </div>
      <el-button size="small" style="margin-top:8px" @click="refresh">刷新</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { store, students, loadAdmin } from '../../store'

const d = computed(() => store.dashboard || {})

const stats = computed(() => [
  { label: '学生总数', value: students.length, color: '#1e3a8a' },
  { label: '已填报（本轮）', value: d.value.applied ?? 0, color: '#1b6e5a' },
  { label: '面试已评价', value: d.value.interviewed ?? 0, color: '#1e3a8a' },
  { label: '拟录取待确认', value: d.value.issued ?? 0, color: '#ea6a1f' },
  { label: '已确认', value: d.value.confirmed ?? 0, color: '#1b6e5a' },
  { label: '递补人数', value: d.value.waitlist ?? 0, color: '#d03050' },
  { label: '调剂候选', value: d.value.adjustCandidates ?? 0, color: '#ea6a1f' },
  { label: '待安置（室内/跨室）', value: `${d.value.pendingInternal ?? 0}/${d.value.pendingCross ?? 0}`, color: '#1e3a8a' }
])

const labProgress = computed(() => (store.quotas || []).map(q => ({
  name: q.name,
  quota: q.quota,
  confirmed: q.confirmed,
  applied: q.applied
})))

function refresh() {
  loadAdmin().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => loadAdmin().catch(e => ElMessage.error(e.message)))
</script>

<style scoped>
.stat { text-align: center; }
.stat-num { font-size: 30px; font-weight: 700; }
</style>
