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
      <el-alert v-if="!roundOpen" type="info" :closable="false" style="margin-bottom:12px"
        :title="`第 ${store.round} 轮已结束，以下操作请先在右上角切换到进行中的轮次。`" />
      <div style="display:flex;gap:12px;flex-wrap:wrap">
        <el-button type="primary" :disabled="!roundOpen" @click="publish">公布剩余名额</el-button>
        <el-button :disabled="!roundOpen" @click="settle">结算拟录取</el-button>
        <el-button type="warning" :disabled="!roundOpen || store.round >= 3" @click="openRound">
          {{ store.round >= 3 ? '已完成三轮' : `开启第 ${store.round + 1} 轮补录` }}
        </el-button>
      </div>
      <p class="muted" style="margin-top:8px">
        「结算拟录取」用于分轮补录：导师发完名单后点一次，只有 1 个拟录取意向的学生自动确认，收到多个意向的学生需在本轮自行选择。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { store, loadAdmin, openNextRound, publishQuotas, settleRound } from '../../store'

const rows = computed(() => store.quotas || [])
const roundOpen = computed(() => store.rounds.find(r => r.roundNo === store.round)?.status === 'OPEN')

function refresh() {
  loadAdmin().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
async function publish() {
  try {
    const r = await publishQuotas()
    store.notifications.unshift({
      time: '刚刚',
      text: `学院已公布剩余名额，共 ${r.total} 个，未录取学生可重新填报`,
      read: false
    })
    ElMessage.success(`剩余名额已公布（共 ${r.total} 个），已写入操作日志`)
  } catch (e) {
    ElMessage.error(e.message || '公布失败')
  }
}
async function settle() {
  try {
    const r = await settleRound()
    ElMessage.success(`已结算：自动确认 ${r.autoConfirmed ?? 0} 人，${r.multiChoice ?? 0} 人需自主选择去向`)
  } catch (e) {
    ElMessage.error(e.message || '结算失败')
  }
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
