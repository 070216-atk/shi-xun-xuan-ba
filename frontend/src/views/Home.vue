<template>
  <div>
    <div class="card hero">
      <h1>学生实训室进组选拔</h1>
      <p>双向选择 · 分轮录取 · 统筹调剂</p>
      <el-tag v-if="currentStage" type="warning" size="large">
        当前阶段：{{ currentStage.name }}（{{ currentStage.window }}）
      </el-tag>
      <el-tag v-else type="info" size="large">当前不在任何阶段的开放时间内</el-tag>
    </div>

    <div class="card">
      <div class="page-title">选拔流程</div>
      <el-steps :active="Math.max(0, store.currentStage - 1)" direction="vertical" style="min-height:520px">
        <el-step v-for="s in stages" :key="s.id" :title="`${s.id}. ${s.name}`" :description="`${s.window} ｜ ${s.desc}`" />
      </el-steps>
    </div>

    <div class="card">
      <div class="page-title">通知公告</div>
      <div v-for="n in notices" :key="n.id" class="notice">
        <div class="notice-head">
          <b>
            <el-tag v-if="n.isTop" type="danger" size="small" style="margin-right:6px">置顶</el-tag>
            {{ n.title }}
          </b>
          <span class="muted">{{ n.date }}</span>
        </div>
        <p class="muted">{{ n.content }}</p>
      </div>
      <el-empty v-if="!notices.length" description="暂无公告" :image-size="60" />
    </div>

    <div class="card" style="text-align:center">
      <router-link to="/labs"><el-button type="primary" size="large">查看全部实训室 →</el-button></router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { stages, notices, store, loadNotices } from '../store'

const currentStage = computed(() => stages.find(s => s.id === store.currentStage))

onMounted(() => {
  loadNotices().catch(() => {})
})
</script>

<style scoped>
.hero { text-align: center; background: linear-gradient(135deg, #eff6ff, #ecfdf5); }
.hero h1 { color: #1e3a8a; margin: 8px 0; font-size: 26px; }
.hero p { color: #1b6e5a; font-weight: 600; }
.notice { padding: 10px 0; border-bottom: 1px dashed #e5e9f0; }
.notice:last-child { border-bottom: none; }
.notice-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
</style>
