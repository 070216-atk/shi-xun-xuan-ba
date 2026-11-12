<template>
  <div>
    <div class="page-title">面试评价 — {{ lab?.name || '' }}（第 {{ store.round }} 轮）</div>
    <div class="card">
      <el-alert type="info" :closable="false"
        title="四个维度各 1–5 分：学习态度、逻辑思维、编程基础、时间投入。这里只显示已入围本室面试名单的学生；评分保存后可修改。"
        style="margin-bottom:12px" />
      <el-table :data="rows" size="small">
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column v-for="d in dims" :key="d.key" :label="d.label" width="150">
          <template #default="{ row }">
            <el-rate v-model="draft(row.code)[d.key]" :max="5" />
          </template>
        </el-table-column>
        <el-table-column label="学生展示（项目/作品/经历）" min-width="200">
          <template #default="{ row }">
            <el-input v-model="draft(row.code).showcase" size="small" type="textarea" :rows="2"
              placeholder="学生现场展示的内容" />
          </template>
        </el-table-column>
        <el-table-column label="评语" min-width="180">
          <template #default="{ row }">
            <el-input v-model="draft(row.code).comment" size="small" placeholder="面试评语" />
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
import { computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { store, labById, loadTeacher, saveScore } from '../../store'

const lab = computed(() => labById(store.user?.labId))
const rows = computed(() => store.interviewList || [])
const dims = [
  { key: 'attitude', label: '学习态度' },
  { key: 'logic', label: '逻辑思维' },
  { key: 'coding', label: '编程基础' },
  { key: 'time', label: '时间投入' }
]

const drafts = reactive({})
function draft(code) {
  if (!drafts[code]) {
    const a = rows.value.find(r => r.code === code) || {}
    drafts[code] = {
      attitude: a.scoreAttitude || 0,
      logic: a.scoreLogic || 0,
      coding: a.scoreCoding || 0,
      time: a.scoreTime || 0,
      comment: a.scoreComment || '',
      showcase: a.showcase || ''
    }
  }
  return drafts[code]
}

async function save(row) {
  const d = draft(row.code)
  if (dims.some(x => !d[x.key])) return ElMessage.warning('请完成四个维度打分')
  try {
    await saveScore(row.code, {
      attitude: d.attitude,
      logic: d.logic,
      coding: d.coding,
      time: d.time,
      comment: d.comment,
      showcase: d.showcase
    })
    delete drafts[row.code]
    ElMessage.success(`已保存 ${row.realName} 的面试评价`)
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

onMounted(() => loadTeacher().catch(e => ElMessage.error(e.message)))
</script>
