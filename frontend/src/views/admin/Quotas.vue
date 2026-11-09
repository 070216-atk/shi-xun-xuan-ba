<template>
  <div>
    <div class="page-title">名额与补录管理</div>
    <div class="card">
      <el-table :data="rows" size="small">
        <el-table-column prop="name" label="实训室" min-width="180" />
        <el-table-column prop="quota" label="总名额" width="80" />
        <el-table-column prop="applied" :label="`第${store.round}轮报名`" width="110" />
        <el-table-column prop="admitted" label="已录取" width="80" />
        <el-table-column label="剩余名额" width="100">
          <template #default="{ row }">
            <el-tag :type="row.remaining > 0 ? 'warning' : 'success'">{{ row.remaining }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="card">
      <div class="page-title">补录操作</div>
      <p class="muted">当前第 {{ store.round }} 轮。发布剩余名额后，未录取学生可在补录窗口重新填报。</p>
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
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { labs } from '../../mock/data'
import { store, appliedCount, remainingQuota, setStage } from '../../store'

const rows = computed(() => labs.map(l => ({
  name: l.name, quota: l.quota,
  applied: appliedCount(l.id, store.round),
  admitted: l.quota - remainingQuota(l.id),
  remaining: remainingQuota(l.id)
})))

function publish() {
  const total = rows.value.reduce((s, r) => s + r.remaining, 0)
  store.notifications.unshift({ time: '刚刚', text: `学院已公布剩余名额，共 ${total} 个，未录取学生可重新填报`, read: false })
  ElMessage.success(`剩余名额已公布（共 ${total} 个）`)
}
async function openRound() {
  try {
    await ElMessageBox.confirm(`确认开启第 ${store.round + 1} 轮补录？将进入「分轮补录」阶段并开放重新填报。`, '开启补录', { type: 'warning', confirmButtonText: '开启', cancelButtonText: '取消' })
  } catch { return }
  setStage(6)
  store.notifications.unshift({ time: '刚刚', text: `第 ${store.round} 轮补录已开启，请未录取学生重新填报志愿`, read: false })
  ElMessage.success(`第 ${store.round} 轮补录已开启`)
}
</script>