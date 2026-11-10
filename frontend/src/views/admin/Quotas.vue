<template>
  <div>
    <div class="page-title">名额与补录管理</div>
    <div class="card">
      <el-table :data="rows" size="small">
        <el-table-column prop="code" label="编号" width="70" />
        <el-table-column prop="name" label="实训室" min-width="200" />
        <el-table-column prop="quota" label="总名额" width="80" />
        <el-table-column prop="applied" :label="`第${store.round}轮报名`" width="110" />
        <el-table-column prop="confirmed" label="已录取" width="80" />
        <el-table-column label="剩余名额" width="100">
          <template #default="{ row }">
            <el-tag :type="row.remaining > 0 ? 'warning' : 'success'">{{ row.remaining }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin-top:12px" @click="refresh">刷新</el-button>
    </div>
    <div class="card">
      <div class="page-title">补录操作</div>
      <p class="muted">
        当前第 {{ store.round }} 轮。开启下一轮会把上一轮置为结束，并复制一份阶段时间窗给新一轮（再去「流程配置」里改具体日期）。
      </p>
      <div style="display:flex;gap:12px;flex-wrap:wrap">
        <el-button type="primary" @click="publish">公布剩余名额</el-button>
        <el-button type="warning" :disabled="store.round >= 3" @click="openRound">
          {{ store.round >= 3 ? '已完成三轮' : `开启第 ${store.round + 1} 轮补录` }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { store, loadAdmin, openNextRound } from '../../store'

const rows = computed(() => store.quotas || [])

function refresh() {
  loadAdmin().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
function publish() {
  const total = rows.value.reduce((s, r) => s + (r.remaining || 0), 0)
  store.notifications.unshift({
    time: '刚刚',
    text: `学院已公布剩余名额，共 ${total} 个，未录取学生可重新填报`,
    read: false
  })
  ElMessage.success(`剩余名额已公布（共 ${total} 个）`)
}
async function openRound() {
  try {
    await ElMessageBox.confirm(
      `确认开启第 ${store.round + 1} 轮补录？上一轮会被置为结束。`,
      '开启补录', { type: 'warning', confirmButtonText: '开启', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await openNextRound()
    ElMessage.success(`已开启第 ${store.round} 轮补录`)
  } catch (e) {
    ElMessage.error(e.message || '开启失败')
  }
}

onMounted(() => loadAdmin().catch(e => ElMessage.error(e.message)))
</script>
