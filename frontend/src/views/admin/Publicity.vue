<template>
  <div>
    <div class="page-title">公示管理</div>
    <div class="card">
      <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <el-tag :type="statusType">{{ statusText }}</el-tag>
        <span class="muted">最终录取 {{ rows.length }} 人</span>
        <span v-if="store.publicity.published" class="muted">公示开始：{{ store.publicity.start }}（公示期 1 天）</span>
      </div>
      <div style="display:flex;gap:12px;flex-wrap:wrap">
        <el-button type="primary" :disabled="store.publicity.published || !rows.length" @click="publish">发布公示</el-button>
        <el-button type="success" :disabled="!store.publicity.published || store.publicity.ended" @click="endPublicity">结束公示，正式入组</el-button>
      </div>
    </div>
    <div class="card">
      <div class="page-title">最终录取名单</div>
      <el-table :data="rows" size="small" max-height="480">
        <el-table-column prop="id" label="学号" width="110" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" width="100" />
        <el-table-column prop="lab" label="录取实训室" min-width="180" />
        <el-table-column label="异议" width="130">
          <template #default="{ row }">
            <el-tag v-if="objections[row.id]" type="danger" size="small">有异议</el-tag>
            <el-button v-else size="small" :disabled="!store.publicity.published || store.publicity.ended" @click="mark(row)">标记异议</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="muted">异议线下处理，系统仅作标记；公示无异议结束后学生状态变为「正式入组」。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { students } from '../../mock/data'
import { store, labById } from '../../store'

const objections = reactive(store.objections || (store.objections = {}))
const rows = computed(() => Object.entries(store.finalResults).map(([sid, lid]) => {
  const s = students.find(x => x.id === sid)
  return { id: sid, name: s?.name || sid, className: s?.className || '-', lab: labById(lid).name }
}))
const statusText = computed(() => store.publicity.ended ? '公示已结束' : store.publicity.published ? '公示中' : '未发布')
const statusType = computed(() => store.publicity.ended ? 'success' : store.publicity.published ? 'warning' : 'info')

async function publish() {
  const hasObjection = Object.values(objections).some(Boolean)
  if (hasObjection) return ElMessage.warning('存在异议标记，请先线下处理')
  try {
    await ElMessageBox.confirm(`确认发布公示？共 ${rows.value.length} 人，公示期 1 天，全体学生可见。`, '发布公示', { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' })
  } catch { return }
  store.publicity = { published: true, start: new Date().toLocaleString('zh-CN'), ended: false }
  store.notifications.unshift({ time: '刚刚', text: '最终录取名单已公示，公示期 1 天', read: false })
  ElMessage.success('公示已发布')
}
async function endPublicity() {
  if (Object.values(objections).some(Boolean)) return ElMessage.warning('存在未处理异议，不能结束公示')
  try {
    await ElMessageBox.confirm('确认结束公示？所有录取学生状态将变为「正式入组」。', '结束公示', { type: 'warning', confirmButtonText: '结束', cancelButtonText: '取消' })
  } catch { return }
  store.publicity.ended = true
  store.notifications.unshift({ time: '刚刚', text: '公示无异议已结束，欢迎正式加入实训室开展 AI+PBL 项目实训', read: false })
  ElMessage.success('公示已结束，学生正式入组')
}
function mark(row) {
  objections[row.id] = true
  ElMessage.info(`已标记 ${row.name} 的异议，请线下处理`)
}
</script>