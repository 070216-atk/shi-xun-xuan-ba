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
            <el-tag v-if="objections[row.code]" type="danger" size="small">有异议</el-tag>
            <el-button v-else size="small" :disabled="!store.publicity.published || store.publicity.ended"
              @click="mark(row)">标记异议</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">
        <el-button size="small" :disabled="!objectionCount" @click="resolve">异议已处理完（清零）</el-button>
        <span class="muted" style="margin-left:8px">存在未处理异议时，后端会拒绝结束公示。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  store, loadAdmin, publishPublicity, endPublicity as endApi,
  markObjection, resolveObjections
} from '../../store'

const objections = reactive({})
const rows = computed(() => store.results || [])
const objectionCount = computed(() => Object.values(objections).filter(Boolean).length)

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
    store.notifications.unshift({ time: '刚刚', text: '最终录取名单已公示，公示期 1 天', read: false })
    ElMessage.success('公示已发布')
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
    store.notifications.unshift({ time: '刚刚', text: '公示已结束，学生正式入组', read: false })
    ElMessage.success('公示已结束')
  } catch (e) {
    ElMessage.error(e.message || '结束失败')
  }
}

async function mark(row) {
  try {
    await markObjection(`对 ${row.realName}（${row.code}）的成绩有疑问`)
    objections[row.code] = true
    ElMessage.info(`已登记 ${row.realName} 的异议`)
  } catch (e) {
    ElMessage.error(e.message || '登记失败')
  }
}

async function resolve() {
  try {
    await resolveObjections()
    Object.keys(objections).forEach(k => delete objections[k])
    ElMessage.success('异议已清零，现在可以结束公示')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

function refresh() {
  loadAdmin().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => loadAdmin().catch(e => ElMessage.error(e.message)))
</script>
