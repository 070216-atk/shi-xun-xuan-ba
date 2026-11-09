<template>
  <div>
    <div class="page-title">流程配置</div>
    <el-alert type="info" :closable="false" style="margin-bottom:16px"
      title="预览版说明：阶段切换为手动模拟（正式版由服务器时间窗口自动开关）。切换后对应操作入口自动开放/关闭，演示截止时间重置为 4 小时后。" />
    <div class="card">
      <el-table :data="stages" size="small">
        <el-table-column prop="id" label="#" width="50" />
        <el-table-column prop="name" label="阶段" width="180" />
        <el-table-column prop="window" label="时间窗口" width="220" />
        <el-table-column prop="desc" label="说明" min-width="260" />
        <el-table-column label="状态" width="200" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.id === store.currentStage" type="success">当前阶段</el-tag>
            <el-button v-else size="small" type="primary" plain @click="switchTo(row)">切换到该阶段</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="card">
      <div class="page-title">演示控制</div>
      <p class="muted">当前第 {{ store.round }} 轮。切换到「分轮补录」会自动进入下一轮（最多第三轮）。</p>
      <el-popconfirm title="将清空所有演示数据并恢复初始状态，确定？" @confirm="onReset">
        <template #reference><el-button type="danger" plain>重置演示数据</el-button></template>
      </el-popconfirm>
    </div>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { stages } from '../../mock/data'
import { store, setStage, resetDemo } from '../../store'

async function switchTo(row) {
  try {
    await ElMessageBox.confirm(`确认将当前阶段切换为「${row.name}」？学生端与管理端的可操作入口将随之变化。`, '切换阶段', { type: 'warning', confirmButtonText: '切换', cancelButtonText: '取消' })
  } catch { return }
  setStage(row.id)
  store.notifications.unshift({ time: '刚刚', text: `阶段已切换：${row.name}`, read: false })
  ElMessage.success(`已切换到「${row.name}」`)
}
function onReset() {
  resetDemo()
  ElMessage.success('演示数据已重置')
}
</script>