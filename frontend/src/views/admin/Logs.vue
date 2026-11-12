<template>
  <div>
    <div class="page-title">操作日志</div>

    <div class="card">
      <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <span>动作类型：</span>
        <el-select v-model="action" clearable placeholder="全部" style="width:200px" @change="reload">
          <el-option v-for="a in actions" :key="a" :value="a" :label="a" />
        </el-select>
        <el-tag>共 {{ total }} 条</el-tag>
        <el-button size="small" @click="reload">刷新</el-button>
      </div>

      <el-table :data="rows" size="small" max-height="560">
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="operatorName" label="操作人" width="110">
          <template #default="{ row }">{{ row.operatorName || '系统' }}</template>
        </el-table-column>
        <el-table-column prop="operatorRole" label="角色" width="90" />
        <el-table-column prop="action" label="动作" width="150">
          <template #default="{ row }"><el-tag size="small">{{ row.action }}</el-tag></template>
        </el-table-column>
        <el-table-column label="对象" width="150">
          <template #default="{ row }">{{ row.targetType }} #{{ row.targetId }}</template>
        </el-table-column>
        <el-table-column label="状态变化" width="150">
          <template #default="{ row }">
            <span v-if="row.beforeState || row.afterState">
              {{ row.beforeState || '-' }} → {{ row.afterState || '-' }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!rows.length" description="还没有操作日志" :image-size="60" />

      <div style="margin-top:12px;text-align:right">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPage" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { loadLogs } from '../../store'

const rows = ref([])
const actions = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const action = ref('')

async function load() {
  try {
    const r = await loadLogs({ page: page.value, size: size.value, action: action.value || '' })
    rows.value = r.list || []
    total.value = r.total || 0
    if (r.actions) actions.value = r.actions
  } catch (e) {
    ElMessage.error(e.message)
  }
}
function reload() {
  page.value = 1
  load()
}
function onPage(p) {
  page.value = p
  load()
}

onMounted(load)
</script>
