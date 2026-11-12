<template>
  <div>
    <div class="page-title">报名学生 — {{ lab?.name || '' }}（第 {{ store.round }} 轮）</div>

    <div class="card">
      <div class="toolbar">
        <el-tag>报名 {{ rows.length }} 人</el-tag>
        <el-tag type="success">已入围 {{ invitedCount }} 人</el-tag>
        <el-tag :type="rows.length >= (lab?.quota || 0) ? 'danger' : 'warning'">
          本室名额 {{ lab?.quota }}
        </el-tag>
        <el-button size="small" @click="reload">刷新</el-button>
      </div>

      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="不是所有报名者都会面试：先在这里把合适的人「拉进面试」，只有入围的学生才会出现在「面试评价」和「名单管理」里。" />

      <div class="toolbar" style="margin-bottom:10px">
        <el-button type="primary" :disabled="!selected.length || !canInvite" :loading="busy" @click="bulkInvite">
          把选中的 {{ selected.length }} 人拉进面试
        </el-button>
        <span v-if="!canInvite" class="muted">当前不在面试阶段，不能调整入围名单</span>
      </div>

      <el-table :data="rows" size="small" @selection-change="s => (selected = s)">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column prop="mentorName" label="意向导师" width="110" />
        <el-table-column label="内部调剂" width="95">
          <template #default="{ row }">{{ row.adjustInLab ? '服从' : '不服从' }}</template>
        </el-table-column>
        <el-table-column label="跨室调剂" width="95">
          <template #default="{ row }">{{ row.adjustCross ? '服从' : '不服从' }}</template>
        </el-table-column>
        <el-table-column label="面试资格" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.invited" type="success" size="small">已入围</el-tag>
            <el-tag v-else type="info" size="small">未入围</el-tag>
            <el-tag v-if="row.invitedByMe" size="small" style="margin-left:4px">我拉的</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="90">
          <template #default="{ row }">
            <el-tag :type="row.scoreAttitude != null ? 'success' : 'info'" size="small">
              {{ row.scoreAttitude != null ? row.scoreTotal + ' 分' : '未评' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.invited" size="small" type="primary" plain :disabled="!canInvite"
              @click="oneInvite(row)">拉进面试</el-button>
            <el-button v-else size="small" type="danger" plain :disabled="!canInvite"
              @click="oneUninvite(row)">取消入围</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="本轮暂无报名学生" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { store, labById, loadTeacher, inviteStudents, uninviteStudent } from '../../store'

const lab = computed(() => labById(store.user?.labId))
const rows = computed(() => store.applicants || [])
const invitedCount = computed(() => rows.value.filter(r => r.invited).length)
const selected = ref([])
const busy = ref(false)
// 入围只在面试（4）或补录（6）阶段可操作，和后端保持一致
const canInvite = computed(() => [4, 6].includes(store.currentStage))

async function bulkInvite() {
  busy.value = true
  try {
    const codes = selected.value.filter(r => !r.invited).map(r => r.code)
    if (!codes.length) return ElMessage.warning('选中的学生都已经入围了')
    const r = await inviteStudents(codes)
    ElMessage.success(`已拉进面试 ${r.added} 人，本室面试名单共 ${r.total} 人`)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    busy.value = false
  }
}

async function oneInvite(row) {
  try {
    await inviteStudents([row.code])
    ElMessage.success(`已把 ${row.realName} 拉进面试`)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function oneUninvite(row) {
  try {
    await ElMessageBox.confirm(
      `把 ${row.realName} 移出面试名单？移出后他不会再出现在评分和名单里。`,
      '取消入围', { type: 'warning', confirmButtonText: '移出', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await uninviteStudent(row.code)
    ElMessage.success(`已把 ${row.realName} 移出面试名单`)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

function reload() {
  loadTeacher().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => loadTeacher().catch(e => ElMessage.error(e.message)))
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
</style>
