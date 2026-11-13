<template>
  <div>
    <div class="page-title">流程配置</div>
    <el-alert type="info" :closable="false" style="margin-bottom:16px"
      title="时间窗以服务器时间为准。「切换到该阶段」会把该阶段设成现在开始、4 小时后结束，并自动关闭本轮其他阶段；同一时间只允许一个阶段开放。" />
    <el-alert v-if="!roundOpen" type="warning" :closable="false" style="margin-bottom:16px"
      :title="`第 ${store.round} 轮已结束，只能查看；要调阶段请先在右上角切到进行中的轮次。`" />
    <div class="card">
      <el-table :data="stages" size="small">
        <el-table-column prop="id" label="#" width="50" />
        <el-table-column prop="name" label="阶段" width="180" />
        <el-table-column prop="window" label="时间窗口" min-width="240" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.current" type="success">当前阶段</el-tag>
            <el-tag v-else-if="row.open" type="warning">开放中</el-tag>
            <el-tag v-else-if="isEnded(row)" type="info">已结束</el-tag>
            <el-tag v-else type="info">未开放</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <span v-if="row.current" class="muted">进行中</span>
            <span v-else-if="isEnded(row)" class="muted">已结束</span>
            <el-button v-else size="small" type="primary" plain :disabled="!roundOpen"
              @click="switchTo(row)">切换到该阶段</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="card">
      <div class="page-title">演示控制</div>
      <p class="muted">当前第 {{ store.round }} 轮。切换阶段会改动数据库里的时间窗，切换后刷新页面即可看到效果。</p>
      <el-button @click="refresh">刷新状态</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stages, store, loadPublic, switchStage } from '../../store'

const roundOpen = computed(() => store.rounds.find(r => r.roundNo === store.round)?.status === 'OPEN')

/** 当前阶段序号；没有进行中的阶段时为 null */
const currentNo = computed(() => stages.find(s => s.current)?.id ?? null)

/** 有当前阶段时按流程位置判断，避免"切换阶段"把后面的阶段也压成过去后误显示已结束 */
function isEnded(row) {
  if (currentNo.value != null) return row.id < currentNo.value
  return row.remaining === 0
}

async function switchTo(row) {
  try {
    await ElMessageBox.confirm(
      `确认把「${row.name}」设为现在开始、4 小时后结束？`,
      '切换阶段', { type: 'warning', confirmButtonText: '切换', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    const r = await switchStage(row.id)
    store.notifications.unshift({ time: '刚刚', text: `阶段已切换：${row.name}`, read: false })
    if (r && r.autoConfirmed != null) {
      ElMessage.success(
        `已切到「${row.name}」：自动确认 ${r.autoConfirmed} 人，${r.multiChoice} 人需自主选择去向`
      )
    } else {
      ElMessage.success(`已切换到「${row.name}」`)
    }
  } catch (e) {
    ElMessage.error(e.message || '切换失败')
  }
}

function refresh() {
  loadPublic().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => loadPublic().catch(e => ElMessage.error(e.message)))
</script>
