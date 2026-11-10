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
      <div v-for="n in notices" :key="n.title" class="notice">
        <div class="notice-head"><b>{{ n.title }}</b><span class="muted">{{ n.date }}</span></div>
        <p class="muted">{{ n.content }}</p>
      </div>
    </div>

    <div class="card" style="text-align:center">
      <router-link to="/labs"><el-button type="primary" size="large">查看全部实训室 →</el-button></router-link>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { stages, store } from '../store'

const currentStage = computed(() => stages.find(s => s.id === store.currentStage))

// 公告暂未做接口，先用固定文案
const notices = [
  {
    title: '2026 年实训室进组选拔开始',
    date: '宣讲当天',
    content: '202 / 204 / 402 / 404 / 406 / 505 六个实训室同步招录，每室 64 人。请先参加集中宣讲与开放日，再在填报窗口内提交意向。'
  },
  {
    title: '填报规则说明',
    date: '填报前必读',
    content: '最多填报 3 位导师，不分先后顺序；须同时选择是否服从「实训室内部调剂」与「全院跨实训室调剂」。截止后锁定，不可修改。'
  }
]
</script>

<style scoped>
.hero { text-align: center; background: linear-gradient(135deg, #eff6ff, #ecfdf5); }
.hero h1 { color: #1e3a8a; margin: 8px 0; font-size: 26px; }
.hero p { color: #1b6e5a; font-weight: 600; }
.notice { padding: 10px 0; border-bottom: 1px dashed #e5e9f0; }
.notice:last-child { border-bottom: none; }
.notice-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
</style>
