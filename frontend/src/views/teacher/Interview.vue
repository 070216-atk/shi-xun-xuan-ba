<template>
  <div>
    <div class="page-title">面试评价 — {{ lab.name }}（第 {{ store.round }} 轮）</div>
    <div class="card">
      <el-alert type="info" :closable="false" title="四个维度各 1–5 分：学习态度、逻辑思维、编程基础、时间投入。评分保存后可修改。" style="margin-bottom:12px" />
      <el-table :data="rows" size="small">
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="id" label="学号" width="110" />
        <el-table-column v-for="d in dims" :key="d.key" :label="d.label" width="150">
          <template #default="{ row }">
            <el-rate v-model="getScore(row.id)[d.key]" :max="5" />
          </template>
        </el-table-column>
        <el-table-column label="评语" min-width="180">
          <template #default="{ row }">
            <el-input v-model="getScore(row.id).comment" size="small" placeholder="面试评语" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="本轮暂无报名学生" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { students } from '../../mock/data'
import { store, labById } from '../../store'

const lab = computed(() => labById(store.user.labId)).value
const dims = [
  { key: 'attitude', label: '学习态度' },
  { key: 'logic', label: '逻辑思维' },
  { key: 'coding', label: '编程基础' },
  { key: 'time', label: '时间投入' }
]
const rows = computed(() => Object.entries(store.applications)
  .filter(([k, v]) => k.endsWith(':' + store.round) && v.labIds.includes(lab.id))
  .map(([k]) => students.find(x => x.id === k.split(':')[0])))

const drafts = reactive({})
function getScore(sid) {
  const key = `${sid}:${lab.id}`
  if (!drafts[key]) drafts[key] = { attitude: 0, logic: 0, coding: 0, time: 0, comment: '', ...(store.scores[key] || {}) }
  return drafts[key]
}
function save(row) {
  const s = getScore(row.id)
  if (dims.some(d => !s[d.key])) return ElMessage.warning('请完成四个维度打分')
  store.scores[`${row.id}:${lab.id}`] = { ...s }
  ElMessage.success(`已保存 ${row.name} 的面试评价`)
}
</script>