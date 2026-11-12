<template>
  <div>
    <div class="page-title">名单管理 — {{ lab?.name || '' }}（第 {{ store.round }} 轮）</div>
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      :title="`拟录取不得超过你个人的名额（${quota} 人）；递补按顺位排序，用于放弃后的自动递补；内部调剂候选用于统筹调剂第一优先级。`" />

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">拟录取名单（{{ admit.length }}/{{ quota }}）</div>
          <el-select v-model="admit" multiple :multiple-limit="quota" placeholder="从已面试学生中选择" style="width:100%">
            <el-option v-for="r in interviewed" :key="r.code" :value="r.code"
              :label="`${r.realName}（均分 ${r.scoreTotal ?? '—'}）`"
              :disabled="waitlist.includes(r.code) || adjust.includes(r.code)" />
          </el-select>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">递补名单（按顺位）</div>
          <el-select v-model="waitlist" multiple placeholder="选择后按顺序定顺位" style="width:100%;margin-bottom:8px">
            <el-option v-for="r in interviewed" :key="r.code" :value="r.code"
              :label="`${r.realName}（均分 ${r.scoreTotal ?? '—'}）`"
              :disabled="admit.includes(r.code) || adjust.includes(r.code)" />
          </el-select>
          <div v-for="(code, i) in waitlist" :key="code" class="rank-row">
            <span>顺位 {{ i + 1 }}：{{ nameOf(code) }}</span>
            <span>
              <el-button size="small" :disabled="i === 0" @click="move(i, -1)">↑</el-button>
              <el-button size="small" :disabled="i === waitlist.length - 1" @click="move(i, 1)">↓</el-button>
            </span>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">内部调剂候选名单</div>
          <el-select v-model="adjust" multiple placeholder="选择调剂候选" style="width:100%">
            <el-option v-for="r in interviewed" :key="r.code" :value="r.code"
              :label="`${r.realName}（均分 ${r.scoreTotal ?? '—'}）`"
              :disabled="admit.includes(r.code) || waitlist.includes(r.code)" />
          </el-select>
        </div>
      </el-col>
    </el-row>

    <div class="card" style="text-align:center">
      <el-button type="primary" size="large" :loading="saving" @click="submit">提交三份名单并发出拟录取</el-button>
      <p class="muted">提交后系统向学生发出拟录取意向；进入「拟录取与学生确认」阶段时，只有 1 个意向的学生会自动确认。</p>
    </div>

    <div v-if="confirmed.length" class="card">
      <div class="page-title">已确认名单（可撤销）</div>
      <p class="muted">撤销后名额立即释放，并按递补顺位自动补位；每次撤销都会写入操作日志。</p>
      <div v-for="c in confirmed" :key="c" class="rank-row">
        <span>{{ nameOf(c) }} <el-tag type="success" size="small">已确认</el-tag></span>
        <el-button size="small" type="danger" plain @click="revoke(c)">撤销录取</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { store, labById, loadTeacher, submitLists, revokeAdmit } from '../../store'

const lab = computed(() => labById(store.user?.labId))
const quota = computed(() => {
  const m = (lab.value?.mentors || []).find(x => x.id === store.user?.mentorId)
  return m ? m.quota : (lab.value?.quota || 0)
})

const admit = ref([])
const waitlist = ref([])
const adjust = ref([])
const saving = ref(false)

const interviewed = computed(() =>
  (store.applicants || []).filter(r => r.scoreAttitude != null)
)

// 已经确认了本导师的学生（可以撤销）
const confirmed = computed(() => {
  const saved = store.labLists[store.user?.labId]
  if (!saved || !saved.admitStatus) return []
  return saved.admit.filter(code => saved.admitStatus[code] === 'CONFIRMED')
})

async function revoke(code) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt(
      `确认撤销 ${nameOf(code)} 的录取？撤销后名额立即释放，并按递补顺位自动补位。请填写原因：`,
      '撤销录取',
      {
        type: 'warning',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：学生主动放弃 / 经沟通调整到其他实训室',
        inputValidator: (v) => (v && v.trim() ? true : '必须填写撤销原因')
      }
    )
    reason = r.value.trim()
  } catch { return }

  try {
    const res = await revokeAdmit(code, reason)
    ElMessage.success(
      res.promotedName
        ? `已撤销 ${nameOf(code)}，并自动递补：${res.promotedName}`
        : `已撤销 ${nameOf(code)}（暂无递补人选）`
    )
  } catch (e) {
    ElMessage.error(e.message || '撤销失败')
  }
}

function nameOf(code) {
  const r = (store.applicants || []).find(x => x.code === code)
  return r ? r.realName : code
}
function move(i, dir) {
  const arr = waitlist.value
  ;[arr[i], arr[i + dir]] = [arr[i + dir], arr[i]]
}

async function loadLists() {
  await loadTeacher()
  const saved = store.labLists[store.user.labId]
  if (saved) {
    admit.value = [...saved.admit]
    waitlist.value = [...saved.waitlist]
    adjust.value = [...saved.adjust]
  }
}

async function submit() {
  if (!admit.value.length) return ElMessage.warning('拟录取名单不能为空')
  if (admit.value.length > quota.value) return ElMessage.error('拟录取人数超过你自己的名额')
  try {
    await ElMessageBox.confirm(
      `确认提交？将向 ${admit.value.length} 名学生发出拟录取意向。`,
      '二次确认', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' }
    )
  } catch { return }

  saving.value = true
  try {
    await submitLists({ admit: admit.value, waitlist: waitlist.value, adjust: adjust.value })
    ElMessage.success('名单已提交，拟录取意向已发出')
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => loadLists().catch(e => ElMessage.error(e.message)))
</script>

<style scoped>
.rank-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px dashed #eee; }
</style>
