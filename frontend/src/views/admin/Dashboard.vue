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
          <span class="muted">{{ l.admitted }}/{{ l.quota }}（报名 {{ l.applied }}）</span>
        </div>
        <el-progress :percentage="Math.round(l.admitted / l.quota * 100)" :stroke-width="14" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { labs, students } from '../../mock/data'
import { store, appliedCount, remainingQuota } from '../../store'

const allOffers = computed(() => Object.values(store.offers).flat())
const stats = computed(() => [
  { label: '学生总数', value: students.length, color: '#1e3a8a' },
  { label: '已填报（本轮）', value: Object.keys(store.applications).filter(k => k.endsWith(':' + store.round)).length, color: '#1b6e5a' },
  { label: '面试已评价', value: Object.keys(store.scores).length, color: '#1e3a8a' },
  { label: '拟录取意向', value: allOffers.value.length, color: '#ea6a1f' },
  { label: '已确认', value: allOffers.value.filter(o => o.status === 'confirmed').length, color: '#1b6e5a' },
  { label: '放弃/未选择', value: allOffers.value.filter(o => ['declined', 'expired'].includes(o.status)).length, color: '#d03050' },
  { label: '调剂安排', value: store.adjustLog.length, color: '#ea6a1f' },
  { label: '已录取', value: Object.keys(store.finalResults).length, color: '#1b6e5a' }
])
const labProgress = computed(() => labs.map(l => ({
  name: l.name, quota: l.quota,
  admitted: l.quota - remainingQuota(l.id),
  applied: appliedCount(l.id, store.round)
})))
</script>

<style scoped>
.stat { text-align: center; }
.stat-num { font-size: 30px; font-weight: 700; }
</style>