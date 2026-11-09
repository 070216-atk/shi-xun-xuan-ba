<template>
  <div v-if="lab">
    <div class="page-title">{{ lab.name }}</div>
    <div class="card">
      <el-descriptions :column="isMobile ? 1 : 2" border>
        <el-descriptions-item label="项目方向">{{ lab.direction }}</el-descriptions-item>
        <el-descriptions-item label="招录名额">{{ lab.quota }} 人（已报名 {{ appliedCount(lab.id, store.round) }} 人）</el-descriptions-item>
        <el-descriptions-item label="地点">{{ lab.location }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ lab.contact }}</el-descriptions-item>
        <el-descriptions-item label="开放日时间" :span="2">{{ lab.openDay }}</el-descriptions-item>
        <el-descriptions-item label="培养安排" :span="2">{{ lab.training }}</el-descriptions-item>
        <el-descriptions-item label="考核要求" :span="2">{{ lab.assessment }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <div class="card">
      <div class="page-title">导师介绍</div>
      <div v-for="m in lab.mentors" :key="m.name" style="margin-bottom:12px">
        <b>{{ m.name }}</b> <el-tag size="small" type="info">{{ m.title }}</el-tag>
        <p class="muted" style="margin:6px 0">{{ m.intro }}</p>
      </div>
    </div>
    <div style="display:flex;gap:12px">
      <el-button @click="$router.back()">返回</el-button>
      <el-button v-if="store.user?.role === 'student'" type="primary" @click="$router.push('/apply')">去填报意向</el-button>
    </div>
  </div>
  <el-empty v-else description="实训室不存在" />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { labById, appliedCount, store } from '../store'
const route = useRoute()
const lab = computed(() => labById(route.params.id))
const isMobile = computed(() => window.innerWidth < 600)
</script>