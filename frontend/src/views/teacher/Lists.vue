<template>
  <div>
    <div class="page-title">
      名单管理 — {{ lab?.name || '' }}（第 {{ store.round }} 轮）
      <el-tag v-if="store.currentStage === 5" type="success" style="margin-left:8px">拟录取与学生确认</el-tag>
      <el-tag v-else-if="store.currentStage === 6" type="warning" style="margin-left:8px">分轮补录</el-tag>
      <el-tag v-else-if="store.currentStage === 7" type="info" style="margin-left:8px">统筹调剂</el-tag>
    </div>
    <el-alert v-if="!canSubmit" type="info" :closable="false" style="margin-bottom:16px"
      title="名单只能在「导师初选与面试考核」「拟录取与学生确认」或「分轮补录」阶段提交；现在不在开放时间内，可以先看名单，等阶段切换后再提交。" />
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      :title="`拟录取不得超过你个人的名额（${quota} 人）；递补按顺位排序，用于放弃后的自动递补；内部调剂候选用于统筹调剂第一优先级。三份名单都只能从已完成面试评分的学生中选择。`" />
    <el-alert v-if="canSubmit && !candidates.length" type="info" :closable="false" style="margin-bottom:16px"
      title="本室本轮还没有已评分的学生：请先到「报名学生」把学生拉进面试名单，再到「面试评价」打分，评分后才能写入名单。" />

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">拟录取名单（{{ admit.length }}/{{ quota }}）</div>
          <el-select v-model="admit" multiple :multiple-limit="quota" placeholder="从已面试学生中选择" style="width:100%">
            <el-option v-for="r in candidates" :key="r.code" :value="r.code"
              :label="optionLabel(r)"
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
              :label="optionLabel(r)"
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
          <el-select v-model="adjust" multiple placeholder="选择调剂候选" style="width:100%;margin-bottom:8px">
            <el-option v-for="r in candidates" :key="r.code" :value="r.code"
              :label="optionLabel(r)"
              :disabled="admit.includes(r.code) || waitlist.includes(r.code)" />
          </el-select>
          <p class="muted" style="margin:0 0 6px">调剂给谁由你指定，对方导师接收后即成为他的拟录取（不再二次面试）。</p>
          <div v-for="code in adjust" :key="'j' + code" class="rank-row">
            <span>{{ nameOf(code) }} →</span>
            <el-select v-model="adjustTargets[code]" size="small" placeholder="选择接收导师" style="width:150px">
              <el-option v-for="m in otherMentors" :key="m.id" :value="m.id" :label="`${m.name}（剩 ${m.quota}）`" />
            </el-select>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="card" style="text-align:center">
      <el-button type="primary" size="large" :loading="saving" :disabled="!canSubmit" @click="submit">
        提交三份名单并发出拟录取
      </el-button>
      <p class="muted">
        {{ [3, 4].includes(store.currentStage)
          ? '提交后系统向学生发出拟录取意向；进入「拟录取与学生确认」阶段时，只有 1 个意向的学生会自动确认。'
          : '当前已进入「拟录取与学生确认」或补录阶段，提交后只有 1 个拟录取意向的学生会立即自动确认；收到多个意向的学生自行选择唯一去向。' }}
      </p>
    </div>

    <div v-if="store.adjustInbox.length" class="card">
      <div class="page-title">别人调剂给我的学生</div>
      <p class="muted">接收后直接成为你的拟录取（占用你的名额），不再二次面试。</p>
      <div v-for="o in store.adjustInbox" :key="o.id" class="rank-row">
        <span>
          <b>{{ o.studentName }}</b>（{{ o.studentCode }}）
          <span class="muted">← 来自 {{ o.mentorName }}</span>
          <el-tag v-if="o.scoreTotal != null" size="small" style="margin-left:6px">均分 {{ o.scoreTotal }}</el-tag>
        </span>
        <span>
          <el-button size="small" type="primary" :disabled="![4, 5, 6].includes(store.currentStage)" @click="doAccept(o)">接收</el-button>
          <el-button size="small" type="danger" plain :disabled="![4, 5, 6].includes(store.currentStage)" @click="doReject(o)">拒绝</el-button>
        </span>
      </div>
    </div>
    <div v-if="store.transferInbox.length || store.currentStage === 7" class="card">
      <div class="page-title">学生转组申请（约谈后处理）</div>
      <p class="muted">
        已录取的学生对去向不满意时会申请转到你这里。线下约谈后决定是否接收：
        接收后他原来的录取自动解除，直接变成你的已确认录取（占用你一个名额）。
      </p>
      <el-empty v-if="!store.transferInbox.length" description="暂时没有待处理的转组申请" :image-size="56" />
      <div v-for="t in store.transferInbox" :key="t.id" class="rank-row" style="align-items:flex-start;flex-wrap:wrap;gap:8px">
        <div style="flex:1;min-width:240px">
          <b>{{ t.studentName }}</b>（{{ t.studentCode }}）
          <span class="muted">原去向 {{ t.fromLabName }} · {{ t.fromMentorName }} → 申请转到 {{ t.targetLabName }}</span>
          <div class="muted" style="font-size:12px;margin-top:4px">申请理由：{{ t.reason }}</div>
          <el-input v-model="transferNotes[t.id]" size="small" maxlength="200" show-word-limit
            placeholder="约谈记录（选填，学生能看到）" style="margin-top:6px;max-width:420px" />
        </div>
        <span style="display:flex;gap:8px">
          <el-button size="small" type="primary" :disabled="store.currentStage !== 7" @click="doAcceptTransfer(t)">接收</el-button>
          <el-button size="small" type="danger" plain :disabled="store.currentStage !== 7" @click="doRejectTransfer(t)">拒绝</el-button>
        </span>
      </div>
    </div>
    <div v-if="confirmed.length" class="card">
      <div class="page-title">已确认名单（可撤销）</div>
      <p class="muted">撤销后名额立即释放，并按递补顺位自动补位；每次撤销都会写入操作日志。</p>
      <div v-for="c in confirmed" :key="c" class="rank-row">
        <span>{{ nameOf(c) }} <el-tag type="success" size="small">已确认</el-tag></span>
        <el-button size="small" type="danger" plain :disabled="![5, 6].includes(store.currentStage)" @click="revoke(c)">撤销录取</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { store, labById, nameByCode, loadTeacher, submitLists, revokeAdmit, acceptAdjust, rejectAdjust, acceptTransfer, rejectTransfer } from '../../store'

const lab = computed(() => labById(store.user?.labId))
const quota = computed(() => {
  const m = (lab.value?.mentors || []).find(x => x.id === store.user?.mentorId)
  return m ? m.quota : (lab.value?.quota || 0)
})

const admit = ref([])
const waitlist = ref([])
const adjust = ref([])
const adjustTargets = reactive({})
// 只能调给本室的其它导师
const otherMentors = computed(() =>
  ((lab.value?.mentors) || []).filter(m => m.id !== store.user?.mentorId)
)
const saving = ref(false)
/** 提交名单的窗口：面试考核（4）、拟录取与学生确认（5）、分轮补录（6） */
const canSubmit = computed(() => [4, 5, 6].includes(store.currentStage))

// 面试阶段（4）只显示本室面试名单里的人：名单成员必须已入围，否则后端会拒绝；
// 阶段 5/6 允许对整个报名池（已评分者）补交修正
const interviewed = computed(() => {
  const pool = store.currentStage === 4 ? (store.interviewList || []) : (store.applicants || [])
  return pool.filter(r => r.scoreTotal != null)
})

// 三份名单都只能从已完成面试评分的学生里选（补录阶段也一样，不能跳过面试）；
// 但跨室转组接收、学院统筹安置进来的学生没报过本室，也要能在名单里正常显示，否则只剩数字
const candidates = computed(() => {
  const base = interviewed.value
  const have = new Set(base.map((r) => r.code))
  const saved = store.labLists[store.user?.labId] || {}
  const extra = [...new Set([...(saved.admit || []), ...(saved.waitlist || []), ...(saved.adjust || [])])]
    .filter((code) => !have.has(code))
  return [...base, ...extra.map((code) => ({ code, realName: nameOf(code), scoreTotal: null }))]
})

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
        inputPlaceholder: '例如：学生主动放弃 / 经沟通调整到其他实训室（200 字以内）',
        inputValidator: (v) => {
          if (!v || !v.trim()) return '必须填写撤销原因'
          return v.trim().length > 200 ? '原因最多 200 字' : true
        }
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

function optionLabel(r) {
  return r.scoreTotal == null ? r.realName : `${r.realName}（均分 ${r.scoreTotal}）`
}
function nameOf(code) {
  const r = (store.applicants || []).find(x => x.code === code)
  return r ? r.realName : nameByCode(code)
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
    // 内部调剂的目标导师也要恢复，否则刷新后下拉框全空、重新提交会丢掉目标
    Object.keys(adjustTargets).forEach(k => delete adjustTargets[k])
    Object.assign(adjustTargets, saved.adjustTargets || {})
  }
}

// 顶栏切换轮次后重新按新一轮加载，避免把上一轮的名单带着提交
watch(() => store.round, () => {
  admit.value = []
  waitlist.value = []
  adjust.value = []
  Object.keys(adjustTargets).forEach(k => delete adjustTargets[k])
  loadLists().catch(e => ElMessage.error(e.message))
})

async function doAccept(o) {
  try {
    await ElMessageBox.confirm(
      `接收 ${o.studentName}？会占用你一个名额，直接成为你的拟录取。`,
      '接收调剂', { type: 'warning', confirmButtonText: '接收', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await acceptAdjust(o.id)
    ElMessage.success(`已接收 ${o.studentName}`)
  } catch (e) {
    ElMessage.error(e.message || '接收失败')
  }
}

async function doReject(o) {
  try {
    await ElMessageBox.confirm(`拒绝 ${o.studentName} 的调剂？他会退回发起导师。`,
      '拒绝调剂', { type: 'warning', confirmButtonText: '拒绝', cancelButtonText: '取消' })
  } catch { return }
  try {
    await rejectAdjust(o.id, '接收方导师拒绝')
    ElMessage.success(`已拒绝 ${o.studentName}`)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}
// ---------------- 跨室转组：学生申请转到我的名下 ----------------

const transferNotes = reactive({})

async function doAcceptTransfer(t) {
  try {
    await ElMessageBox.confirm(
      `接收 ${t.studentName}？会占用你一个名额；他原来在「${t.fromLabName} · ${t.fromMentorName}」的录取会立即解除。`,
      '接收转组', { type: 'warning', confirmButtonText: '接收', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await acceptTransfer(t.id, (transferNotes[t.id] || '').trim())
    delete transferNotes[t.id]
    ElMessage.success(`已接收 ${t.studentName}`)
  } catch (e) {
    ElMessage.error(e.message || '接收失败')
  }
}

async function doRejectTransfer(t) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt(`拒绝 ${t.studentName} 的转组申请？请填写原因，学生能看到。`,
      '拒绝转组', {
        type: 'warning',
        confirmButtonText: '拒绝',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：本方向名额已满 / 面试后认为方向不匹配（200 字以内）',
        inputValidator: (v) => {
          if (!v || !v.trim()) return '必须填写拒绝原因'
          return v.trim().length > 200 ? '原因最多 200 字' : true
        }
      })
    reason = r.value.trim()
  } catch { return }
  try {
    await rejectTransfer(t.id, reason, (transferNotes[t.id] || '').trim())
    delete transferNotes[t.id]
    ElMessage.success(`已拒绝 ${t.studentName}`)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
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
    await submitLists({ admit: admit.value, waitlist: waitlist.value, adjust: adjust.value, adjustTargets })
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
