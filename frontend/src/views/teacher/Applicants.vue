<template>
  <div>
    <div class="page-title">报名学生 — {{ lab?.name || '' }}</div>
    <div class="card">
      <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;flex-wrap:wrap">
        <el-tag>第 {{ store.round }} 轮</el-tag>
        <el-tag>报名 {{ rows.length }} 人</el-tag>
        <el-tag :type="rows.length >= (lab?.quota || 0) ? 'danger' : 'warning'">
          本室名额 {{ lab?.quota }}（{{ rows.length >= (lab?.quota || 0) ? '已满额' : '未满额' }}）
        </el-tag>
        <el-button size="small" @click="reload">刷新</el-button>
      </div>
      <el-alert type="info" :closable="false"
        title="无论报名是否满额，所有报名学生均须参加实训室统一面试。" style="margin-bottom:12px" />
      <el-table :data="rows" size="small">
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column prop="mentorName" label="意向导师" width="110" />
        <el-table-column label="内部调剂" width="95">
          <template #default="{ row }">{{ row.adjustInLab ? '服从' : '不服从' }}</template>
        </el-table-column>
        <el-table-column label="跨室调剂" width="95">
          <template #default="{ row }">{{ row.adjustCross ? '服从' : '不服从' }}</template>
        </el-table-column>
        <el-table-column label="面试评价" width="100">
          <template #default="{ row }">
            <el-tag :type="row.scoreAttitude != null ? 'success' : 'info'" size="small">
              {{ row.scoreAttitude != null ? '已评' : '未评' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合分" width="90">
          <template #default="{ row }">{{ row.scoreTotal != null ? row.scoreTotal : '—' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="本轮暂无报名学生" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { store, labById, loadTeacher } from '../../store'

const lab = computed(() => labById(store.user?.labId))
const rows = computed(() => store.applicants || [])

function reload() {
  loadTeacher().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => loadTeacher().catch(e => ElMessage.error(e.message)))
</script>
