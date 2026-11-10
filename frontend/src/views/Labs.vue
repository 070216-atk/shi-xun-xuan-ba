<template>
  <div>
    <div class="page-title">实训室一览</div>
    <el-row :gutter="16">
      <el-col v-for="lab in labs" :key="lab.id" :xs="24" :sm="12" :md="8">
        <div class="card lab-card">
          <h3>{{ lab.name }}</h3>
          <p class="muted">{{ lab.direction }}</p>
          <div class="lab-meta">
            <el-tag>名额 {{ lab.quota }}</el-tag>
            <el-tag type="info">已报名 {{ appliedCount(lab.id) }}</el-tag>
            <el-tag type="success">{{ (lab.mentors || []).length }} 位导师</el-tag>
          </div>
          <p class="muted">导师：{{ (lab.mentors || []).map(m => m.name).join('、') || '—' }}</p>
          <router-link :to="`/labs/${lab.id}`"><el-button type="primary" plain style="width:100%">查看详情</el-button></router-link>
        </div>
      </el-col>
    </el-row>
    <el-empty v-if="!labs.length" description="正在加载实训室…" :image-size="60" />
  </div>
</template>

<script setup>
import { labs, appliedCount } from '../store'
</script>

<style scoped>
.lab-card h3 { color: #1b6e5a; margin: 4px 0 8px; }
.lab-meta { display: flex; gap: 8px; margin: 8px 0; flex-wrap: wrap; }
</style>
