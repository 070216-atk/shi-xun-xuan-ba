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
        <el-button type="success" :disabled="!store.publicity.published || store.publicity.ended" @click="endPublicity">
          结束公示，正式入组
        </el-button>
        <el-button @click="refresh">刷新</el-button>
      </div>
    </div>

    <div class="card">
      <div class="page-title">最终录取名单</div>
      <el-table :data="rows" size="small" max-height="480">
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column prop="labName" label="录取实训室" min-width="180" />
        <el-table-column prop="mentorName" label="导师" width="110" />
        <el-table-column label="异议" width="160">
          <template #default="{ row }">
            <el-tag v-if="hasObjection(row.code)" type="danger" size="small">有异议</el-tag>
            <el-button v-else size="small" :disabled="!store.publicity.published || store.publicity.ended"
              @click="mark(row)">标记异议</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">
        <el-button size="small" :disabled="!objectionCount" @click="resolve">异议已处理完（清零）</el-button>
        <span class="muted" style="margin-left:8px">存在未处理异议时，后端会拒绝结束公示（刷新页面也不会丢）。</span>
      </div>
      <div v-if="objectionCount" class="objection-box">
        <div v-for="o in store.objections" :key="o.id" class="objection-row">
          <span class="muted">{{ o.createdAt }}</span>
          <b v-if="o.studentCode">{{ o.studentCode }}</b>
          <span>{{ o.reason }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  store, loadAdmin, publishPublicity, endPublicity as endApi,
  markObjection, resolveObjections, loadObjections
} from '../../store'

const rows = computed(() => store.results || [])
const objectionCount = computed(() => store.objections.length)

/** 这条录取记录是否已被登记异议（可按学号，也可不带学号的总体异议） */
function hasObjection(code) {
  return store.objections.some(o => !o.studentCode || o.studentCode === code)
}

const statusText = computed(() =>
  store.publicity.ended ? '公示已结束' : store.publicity.published ? '公示中' : '未发布'
)
const statusType = computed(() =>
  store.publicity.ended ? 'success' : store.publicity.published ? 'warning' : 'info'
)

async function publish() {
  try {
    await ElMessageBox.confirm(
      `确认发布公示？共 ${rows.value.length} 人，公示期 1 天，全体学生可见。`,
      '发布公示', { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await publishPublicity()
    await Promise.all([loadAdmin(), loadObjections()])
    ElMessage.success('公示已发布，已给全体学生推送站内通知')
  } catch (e) {
    ElMessage.error(e.message || '发布失败')
  }
}

async function endPublicity() {
  if (objectionCount.value) return ElMessage.warning('存在未处理异议，不能结束公示')
  try {
    await ElMessageBox.confirm('确认结束公示？所有录取学生状态变为「正式入组」。', '结束公示',
      { type: 'warning', confirmButtonText: '结束', cancelButtonText: '取消' })
  } catch { return }
  try {
    await endApi()
    ElMessage.success('公示已结束')
  } catch (e) {
    ElMessage.error(e.message || '结束失败')
  }
}

async function mark(row) {
  try {
    await markObjection(row.code, `对 ${row.realName}（${row.code}）的成绩有疑问`)
    await loadObjections()
    ElMessage.info(`已登记 ${row.realName} 的异议`)
  } catch (e) {
    ElMessage.error(e.message || '登记失败')
  }
}

async function resolve() {
  try {
    await resolveObjections()
    ElMessage.success('异议已清零，现在可以结束公示')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

function refresh() {
  Promise.all([loadAdmin(), loadObjections()])
    .then(() => ElMessage.success('已刷新'))
    .catch(e => ElMessage.error(e.message))
}
onMounted(() => {
  loadAdmin().catch(e => ElMessage.error(e.message))
  loadObjections().catch(() => {})
})
</script>

<style scoped>
.objection-box { margin-top: 10px; border-top: 1px dashed #e2e8f0; padding-top: 8px; }
.objection-row { display: flex; gap: 10px; font-size: 13px; padding: 4px 0; color: #475569; }
.objection-row b { color: #b91c1c; font-weight: 600; }
</style>
