<template>
  <div>
    <div class="page-title">录取结果</div>

    <div class="card" style="text-align:center">
      <template v-if="finalLab">
        <el-result :icon="store.publicity.ended ? 'success' : 'info'"
          :title="store.publicity.ended ? '正式入组' : '已录取（公示中 / 待公示）'"
          :sub-title="`你的去向：${finalLab.name}`">
          <template #extra>
            <p v-if="finalMentorName">导师：{{ finalMentorName }}</p>
            <p class="muted">地点：{{ finalLab.location }} ｜ 联系方式：{{ finalLab.contact }}</p>
          </template>
        </el-result>
      </template>
      <el-result v-else icon="info" title="结果尚未产生"
        sub-title="请耐心等待各阶段选拔完成，最终结果将在此展示并公示 1 天。" />
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
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { store, labById, studentStatus, loadStudent, loadPublicity } from '../../store'

onMounted(() => {
  if (store.user?.role === 'student') {
    loadStudent().catch(e => ElMessage.error(e.message))
  }
  loadPublicity().catch(() => {})
})

const finalLab = computed(() => {
  const lid = store.finalResults[store.user?.id]
  return lid ? labById(lid) : null
})
const finalMentorName = computed(() => {
  const offers = store.offers[store.user?.id] || []
  const hit = offers.find(o => o.status === 'confirmed' && o.mentorName)
  return hit ? hit.mentorName : ''
})
const status = computed(() =>
  store.studentStatus?.statusText || (store.user ? studentStatus(store.user.id) : ''))
const app = computed(() => store.applications[`${store.user?.id}:${store.round}`])
</script>
