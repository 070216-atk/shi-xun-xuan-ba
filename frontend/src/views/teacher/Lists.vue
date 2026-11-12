<template>
  <div>
    <div class="page-title">
      名单管理 — {{ lab?.name || '' }}（第 {{ store.round }} 轮）
      <el-tag v-if="store.currentStage === 5" type="success" style="margin-left:8px">拟录取与学生确认</el-tag>
      <el-tag v-else-if="store.currentStage === 6" type="warning" style="margin-left:8px">分轮补录</el-tag>
    </div>
    <el-alert v-if="!canSubmit" type="info" :closable="false" style="margin-bottom:16px"
      title="名单只能在「导师初选与面试考核」「拟录取与学生确认」或「分轮补录」阶段提交；现在不在开放时间内，可以先看名单，等阶段切换后再提交。" />
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      :title="`拟录取不得超过你个人的名额（${quota} 人）；递补按顺位排序，用于放弃后的自动递补；内部调剂候选用于统筹调剂第一优先级。`" />

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">拟录取名单（{{ admit.length }}/{{ quota }}）</div>
          <el-select v-model="admit" multiple :multiple-limit="quota" placeholder="从已面试学生中选择" style="width:100%">
            <el-option v-for="r in candidates" :key="r.code" :value="r.code"
              :label="`${r.realName}（均分 ${r.scoreTotal ?? '—'}）`"
              :disabled="waitlist.includes(r.code) || adjust.includes(r.code)" />
          </el-select>          <div v-for="(code, i) in admit" :key="'a' + code" class="rank-row">
            <span>第 {{ i + 1 }} 顺位：{{ nameOf(code) }}</span>
            <span>
              <el-button size="small" :disabled="i === 0" @click="moveIn('admit', i, -1)">↑</el-button>
              <el-button size="small" :disabled="i === admit.length - 1" @click="moveIn('admit', i, 1)">↓</el-button>
            </span>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">递补名单（按顺位）</div>
          <el-select v-model="waitlist" multiple placeholder="选择后按顺序定顺位" style="width:100%;margin-bottom:8px">
            <el-option v-for="r in candidates" :key="r.code" :value="r.code"
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
            <el-option v-for="r in candidates" :key="r.code" :value="r.code"
              :label="`${r.realName}（均分 ${r.scoreTotal ?? '—'}）`"
              :disabled="admit.includes(r.code) || waitlist.includes(r.code)" />
          </el-select>
        </div>
      </el-col>
    </el-row>

    <div class="card" style="text-align:center">
      <el-button type="primary" size="large" :loading="saving" :disabled="!canSubmit" @click="submit">
        提交三份名单并发出拟录取
      </el-button>
      <p class="muted">
        {{ store.currentStage === 4
          ? '提交后系统向学生发出拟录取意向；进入「拟录取与学生确认」阶段时，只有 1 个意向的学生会自动确认。'
          : '当前已进入「拟录取与学生确认」或补录阶段，提交后只有 1 个拟录取意向的学生会立即自动确认；收到多个意向的学生自行选择唯一去向。' }}
      </p>
    </div>

    <div class="card">
      <div class="page-title">自动录取（可选）</div>
      <p class="muted">
        默认是你手动挑选。如果嫌麻烦，可以让系统按顺序录满你的名额（{{ quota }} 人），剩下的人自动进递补。
      </p>
      <div style="display:flex;gap:10px;flex-wrap:wrap">
        <el-button :loading="autoing" @click="doAuto('score')">按面试评分自动录取</el-button>
        <el-button :loading="autoing" @click="doAuto('manual')">按我当前的排序自动录取</el-button>
      </div>
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
import { store, labById, loadTeacher, submitLists, revokeAdmit, autoAdmit } from '../../store'

const lab = computed(() => labById(store.user?.labId))
const quota = computed(() => {
  const m = (lab.value?.mentors || []).find(x => x.id === store.user?.mentorId)
  return m ? m.quota : (lab.value?.quota || 0)
})

const admit = ref([])
const waitlist = ref([])
const adjust = ref([])
const saving = ref(false)
/** 提交名单的窗口：面试考核（4）、拟录取与学生确认（5）、分轮补录（6） */
const canSubmit = computed(() => [4, 5, 6].includes(store.currentStage))

const interviewed = computed(() =>
  (store.applicants || []).filter(r => r.scoreAttitude != null)
)

// 补录/确认阶段允许直接录取尚未评分的报名学生，否则新轮次一个都选不了
const candidates = computed(() =>
  [5, 6].includes(store.currentStage) ? (store.applicants || []) : interviewed.value
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
function moveIn(which, i, dir) {
  const arr = which === 'admit' ? admit.value : waitlist.value
  ;[arr[i], arr[i + dir]] = [arr[i + dir], arr[i]]
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

const autoing = ref(false)

async function doAuto(sortBy) {
  const label = sortBy === 'score' ? '面试评分' : '你当前的排序'
  try {
    await ElMessageBox.confirm(
      `按${label}自动录取？会覆盖你当前的名单，录满名额（${quota.value} 人）为止，其余进递补。`,
      '自动录取', { type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消' }
    )
  } catch { return }
  autoing.value = true
  try {
    const r = await autoAdmit(sortBy)
    ElMessage.success(`自动录取完成：拟录取 ${r.admitted} 人，递补 ${r.waitlist} 人`)
    await loadLists()
  } catch (e) {
    ElMessage.error(e.message || '自动录取失败')
  } finally {
    autoing.value = false
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
