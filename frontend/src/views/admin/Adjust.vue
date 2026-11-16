<template>
  <div>
    <div class="page-title">统筹调剂工作台</div>
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      title="调剂优先级不可颠倒：① 实训室内部调剂 → ② 全院跨实训室调剂（仅限服从者）。后端会强制校验，跳级会返回错误。已录取学生对去向不满意时，由本人在「我的选拔」里申请跨室转组，目标导师约谈后接收，不再由管理员逐条约谈安排。" />
    <el-alert v-if="!adjustOpen" type="info" :closable="false" style="margin-bottom:16px"
      title="现在还没到「统筹调剂录取」环节：① ② 和指定安置都要等初选、拟录取和补录走完才能操作，现在只能看，不能安置人。"
      :description="adjustStage ? `本轮该环节的时间窗：${adjustStage.window}` : ''" />

    <div class="card" style="border:2px solid #1b6e5a">
      <div class="page-title">安置检查（确保每个人都有实训室）</div>
      <p class="muted">
        所有调剂做完后，点「校验」确认每个人都有一个实训室、导师没有超录；校验不通过时不能发布公示。
        最后一轮补录结束后，所有还没有去向的学生（包括从未报名、只在往轮报过名的）都会进入下面的未安置名单，可单独「指定安置」。
      </p>
      <div class="gap-row" v-if="gaps">
        <el-tag>已填报 {{ gaps.appliedCount }}</el-tag>
        <el-tag v-if="gaps.neverAppliedCount" type="info">从未报名 {{ gaps.neverAppliedCount }}</el-tag>
        <el-tag v-if="gaps.earlierOnlyCount" type="warning">仅往轮报名 {{ gaps.earlierOnlyCount }}</el-tag>
        <el-tag type="success">已确认 {{ gaps.confirmedCount }}</el-tag>
        <el-tag :type="gaps.unplacedCount ? 'danger' : 'info'">未安置 {{ gaps.unplacedCount }}</el-tag>
        <el-tag type="warning">剩余名额 {{ gaps.totalRemaining }}</el-tag>
        <el-tag :type="gaps.feasible ? 'success' : 'danger'">
          {{ gaps.feasible ? '名额够用' : '名额不足！' }}
        </el-tag>
      </div>
      <div v-if="gaps && gaps.unplaced.length" style="margin:8px 0">
        <span class="muted">未安置：</span>
        <el-tag v-for="u in gaps.unplaced" :key="u.studentId" :type="sourceTag(u.source)" style="margin:4px">
          {{ u.realName }}（{{ u.code }}）{{ sourceLabel(u.source) }}
        </el-tag>
      </div>
      <div style="display:flex;gap:10px;flex-wrap:wrap;margin-top:8px">
        <el-button type="primary" :loading="checking" @click="doVerify">校验</el-button>
        <el-button @click="refreshGaps">刷新缺口</el-button>
      </div>
      <div v-if="gaps && gaps.unplaced.length" style="margin-top:14px;padding-top:12px;border-top:1px dashed #ddd">
        <div class="page-title" style="font-size:14px">指定安置（约谈后同意去某个导师）</div>
        <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
          <el-select v-model="assignStudent" placeholder="选学生" style="width:180px" filterable :disabled="!adjustOpen">
            <el-option v-for="u in gaps.unplaced" :key="u.studentId" :value="u.studentId"
              :label="`${u.realName}（${u.code}）${sourceLabel(u.source)}`" />
          </el-select>
          <el-select v-model="assignMentor" placeholder="选导师" style="width:200px" filterable :disabled="!adjustOpen">
            <el-option v-for="m in gaps.availableMentors" :key="m.mentorId" :value="m.mentorId"
              :label="`${m.labCode} ${m.mentorName}（剩 ${m.remaining}）`" />
          </el-select>
          <el-input v-model="assignReason" placeholder="安置原因（必填，学生能看到，200 字以内）"
            style="width:260px" maxlength="200" show-word-limit />
          <el-button type="success" :loading="assigning" :disabled="!adjustOpen" @click="doAssign">指定安置</el-button>
        </div>
        <p class="muted" style="margin:6px 0 0">
          这条会直接变成「已确认」，备注写成「学院统筹安置：原因」，学生在「我的选拔」里能看到这句说明。
        </p>
      </div>
      <el-alert v-if="verifyResult" :type="verifyResult.ok ? 'success' : 'error'" :closable="false" style="margin-top:10px"
        :title="verifyResult.ok
          ? `校验通过：${verifyResult.confirmedCount}/${verifyResult.appliedCount} 人都有唯一去向`
          : `校验未通过：缺失 ${verifyResult.missing.length} 人、重复 ${verifyResult.duplicate.length} 人、超录 ${verifyResult.overQuota.length} 位导师`" />
    </div>
    <div class="card">
      <div class="page-title">剩余名额（按导师）</div>
      <el-tag v-for="v in vacancies" :key="v.mentorId" :type="v.remaining > 0 ? 'warning' : 'info'" style="margin:4px">
        {{ v.labCode }} {{ v.mentorName }}：{{ v.remaining }}
      </el-tag>
      <p class="muted">待安置学生：{{ candidates.length }} 人</p>
      <el-button size="small" style="margin-top:8px" @click="refresh">刷新</el-button>
    </div>

    <div class="card">
      <div class="page-title">① 实训室内部调剂</div>
      <p class="muted">
        对象：本轮报过名、还没有去向、且勾了「服从实训室内部调剂」的学生。
        只能把他安排到「本轮报过的实训室」里的其他导师那边；名额按导师算。
      </p>
      <el-table :data="p1Rows" size="small">
        <el-table-column prop="realName" label="学生" width="100" />
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column label="服从" width="150">
          <template #default="{ row }">
            <el-tag size="small" type="success">室内</el-tag>
            <el-tag v-if="row.adjustCross" size="small" type="warning" style="margin-left:4px">跨室</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="安排到导师" min-width="240">
          <template #default="{ row }">
            <el-select v-model="t1[row.code]" size="small" :disabled="!adjustOpen" placeholder="仅显示他报过且还有名额的导师" style="width:100%">
              <el-option v-for="v in vacanciesFor(row)" :key="v.mentorId"
                :value="v.mentorId" :label="`${v.labCode} ${v.mentorName}（剩 ${v.remaining}）`" />
            </el-select>
            <div v-if="!vacanciesFor(row).length" class="muted" style="font-size:12px;color:#b88230">
              {{ row.adjustCross
                ? '他报过的实训室都满了 → 到 ② 跨室调剂里安排他'
                : '他报过的实训室都满了，且没勾选服从跨室调剂 → 只能用顶部「指定安置」' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="!t1[row.code]"
              @click="assign(1, row.code, t1[row.code])">安排录取</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p1Rows.length" description="无内部调剂候选或已全部处理" :image-size="50" />
    </div>

    <div class="card">
      <div class="page-title">
        ② 全院跨实训室调剂
        <el-tag v-if="placeableInternal.length" type="info" size="small" style="margin-left:8px">
          须先完成①（还剩 {{ placeableInternal.length }} 人本室有空位）
        </el-tag>
      </div>
      <p class="muted">
        对象：勾了「服从全院跨实训室调剂」的学生，可以安排到任何有空位的导师。
        只要 ① 里还有学生在本室有空位，就必须先把他们安排完；剩下的（本室都满了）才在这里处理。
        「跨室」指的是<b>换到别的实训室</b>：他自己本轮报过的那些实训室不会再出现在下拉里（那属于 ①）。
      </p>
      <el-table :data="p2Rows" size="small">
        <el-table-column prop="realName" label="学生" width="100" />
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column label="情况" min-width="180">
          <template #default="{ row }">
            <span v-if="!row.adjustInLab" class="muted">只服从跨室调剂</span>
            <span v-else class="muted">服从室内，但报过的室已满</span>
          </template>
        </el-table-column>
        <el-table-column label="安排到导师" min-width="240">
          <template #default="{ row }">
            <el-select v-model="t2[row.code]" :disabled="!adjustOpen || placeableInternal.length > 0" size="small"
              placeholder="选择有剩余名额的导师" style="width:100%">
              <el-option v-for="v in vacanciesForCross(row)" :key="v.mentorId"
                :value="v.mentorId" :label="`${v.labCode} ${v.mentorName}（剩 ${v.remaining}）`" />
            </el-select>
            <div v-if="!vacanciesForCross(row).length" class="muted" style="font-size:12px;color:#b88230">
              除了他报过的实训室，暂时没有别的室有空位
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="!adjustOpen || placeableInternal.length > 0 || !t2[row.code]"
              @click="assign(2, row.code, t2[row.code])">安排录取</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p2Rows.length" description="无服从跨室调剂的待安置学生" :image-size="50" />

      <div style="margin-top:14px;padding-top:12px;border-top:1px dashed #ddd">
        <div class="page-title" style="font-size:14px">学生自己申请换到别的实训室（跨室转组）</div>
        <p class="muted">
          已经被录取、但对去向不满意的学生，可以在「我的选拔」里申请换到别的实训室；
          目标导师约谈后接收，原去向自动作废。这里只做展示，换不换由目标导师决定，管理员不直接改。
        </p>
        <el-table :data="transferRows" size="small">
          <el-table-column prop="studentName" label="学生" width="90" />
          <el-table-column prop="studentCode" label="学号" width="105" />
          <el-table-column label="原去向" min-width="170">
            <template #default="{ row }">{{ row.fromLabCode }} {{ row.fromMentorName }}</template>
          </el-table-column>
          <el-table-column label="申请转到" min-width="190">
            <template #default="{ row }">{{ row.targetLabCode }} {{ row.targetMentorName }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="transferStatus(row.status).type">{{ transferStatus(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ String(row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!transferRows.length" description="暂无学生申请换室" :image-size="40" />
      </div>
    </div>

    <div class="card">
      <div class="page-title">调剂记录（持久留痕，刷新后仍在）</div>
      <p class="muted">
        这里读的是服务端操作日志：① 实训室内部调剂、② 全院跨实训室调剂、顶部「指定安置」，
        以及学生申请 / 导师接收的「跨室转组」。每次安排都记一行「谁、在什么时候、把哪个学生放到了哪个导师」。
      </p>
      <el-button size="small" style="margin-bottom:8px" :loading="historyLoading" @click="loadHistory">刷新记录</el-button>
      <el-table :data="historyRows" size="small" max-height="360">
        <el-table-column prop="time" label="时间" width="170" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.levelType">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="text" label="操作内容" min-width="260" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!historyRows.length" description="还没有调剂记录" :image-size="50" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { store, stages, loadAdmin, loadLogs, doAdjust, loadPlacement, verifyPlacement, assignPlacement } from '../../store'

/** 统筹调剂是第 7 环节；没到这一步，①② 和指定安置都只能看不能动 */
const adjustStage = computed(() => stages.find((s) => s.id === 7))
const adjustOpen = computed(() => !!adjustStage.value?.open)

const vacancies = computed(() => (store.pool?.vacancies || []))
const candidates = computed(() => (store.pool?.candidates || []))

/** 未安置学生的来源：本轮未录取 / 从未报名 / 仅往轮报名未录取 */
const sourceLabel = (s) => ({ NEVER: ' · 未报名', EARLIER: ' · 往轮未录' }[s] || '')
const sourceTag = (s) => ({ NEVER: 'info', EARLIER: 'warning' }[s] || 'danger')

/** 学生自己发起的跨室转组（后端按"待接收优先"排序） */
const transferRows = computed(() => (store.transfers || []))
function transferStatus(s) {
  return {
    PENDING: { label: '待导师接收', type: 'warning' },
    ACCEPTED: { label: '已接收', type: 'success' },
    REJECTED: { label: '已拒绝', type: 'danger' },
    CANCELED: { label: '已撤销', type: 'info' }
  }[s] || { label: s, type: 'info' }
}

const p1Rows = computed(() => candidates.value.filter(c => c.adjustInLab))
/** ① 里还"在本室有空位可去"的人：只要还有这种人，②就必须等着 */
const placeableInternal = computed(() => p1Rows.value.filter(c => vacanciesFor(c).length > 0))
/** ② 的对象：服从跨室的人；已经在 ① 里还能安排的人不重复出现 */
const p2Rows = computed(() => candidates.value.filter(c =>
  c.adjustCross && (!c.adjustInLab || vacanciesFor(c).length === 0)))

/** ①室内调剂只能调到他"本轮报过"的实训室里的导师，先用 labIds 把下拉过滤一遍 */
function appliedLabIds(row) {
  return row.labIds ? String(row.labIds).split(',').filter(Boolean).map(Number) : []
}
function vacanciesFor(row) {
  const raw = appliedLabIds(row)
  return vacancies.value.filter(v => v.remaining > 0
    && (!raw.length || raw.includes(Number(v.labId))))
}
/** ② 是"换到别的实训室"：他报过的实训室不再出现，只留其他室还有空位的导师 */
function vacanciesForCross(row) {
  const raw = appliedLabIds(row)
  return vacancies.value.filter(v => v.remaining > 0 && !raw.includes(Number(v.labId)))
}

const t1 = reactive({})
const t2 = reactive({})

/** 调剂记录：从服务端 operation_log 读，刷新页面也不会丢 */
const LEVEL = {
  ADJUST_INTERNAL: { label: '① 室内调剂', type: 'success' },
  ADJUST_CROSS: { label: '② 跨室调剂', type: 'warning' },
  ASSIGN_PLACEMENT: { label: '指定安置', type: 'primary' },
  TRANSFER_APPLY: { label: '转组申请', type: 'info' },
  TRANSFER_ACCEPT: { label: '跨室转组', type: 'warning' }
}
const HISTORY_ACTIONS = 'ADJUST_INTERNAL,ADJUST_CROSS,ASSIGN_PLACEMENT,TRANSFER_APPLY,TRANSFER_ACCEPT'
const historyRows = ref([])
const historyLoading = ref(false)

async function loadHistory() {
  historyLoading.value = true
  try {
    const r = await loadLogs({ page: 1, size: 50, action: HISTORY_ACTIONS })
    historyRows.value = (r.list || []).map(x => {
      const lv = LEVEL[x.action] || { label: x.action, type: 'info' }
      return {
        id: x.id,
        time: String(x.createdAt || '').replace('T', ' ').slice(0, 19),
        level: lv.label,
        levelType: lv.type,
        operator: x.operatorName || '系统',
        text: x.remark || ''
      }
    })
  } catch (e) {
    ElMessage.error(e.message || '读取调剂记录失败')
  } finally {
    historyLoading.value = false
  }
}

async function assign(level, code, mentorId) {
  try {
    await doAdjust(level, mentorId, [code])
    ElMessage.success('已安排录取')
    await loadHistory()
  } catch (e) {
    ElMessage.error(e.message || '安排失败')
  }
}

const checking = ref(false)
const verifyResult = ref(null)
const gaps = computed(() => store.placement)

async function refreshGaps() {
  try { await loadPlacement() } catch (e) { ElMessage.error(e.message || '刷新失败') }
}

const assigning = ref(false)
const assignStudent = ref(null)
const assignMentor = ref(null)
const assignReason = ref('')

async function doAssign() {
  if (!assignStudent.value) return ElMessage.warning('请选择学生')
  if (!assignMentor.value) return ElMessage.warning('请选择导师')
  if (!assignReason.value.trim()) return ElMessage.warning('必须填写安置原因，学生要能看到')
  assigning.value = true
  try {
    const r = await assignPlacement(assignStudent.value, assignMentor.value, assignReason.value.trim())
    if (r.already) ElMessage.info('该学生已经在这个导师名下')
    else ElMessage.success(`已安置到 ${r.mentorName}，学生端会显示「${r.remark}」`)
    assignStudent.value = null
    assignMentor.value = null
    assignReason.value = ''
    verifyResult.value = await verifyPlacement()
    await loadHistory()
  } catch (e) {
    ElMessage.error(e.message || '安置失败')
  } finally {
    assigning.value = false
  }
}
async function doVerify() {
  checking.value = true
  try {
    verifyResult.value = await verifyPlacement()
    if (verifyResult.value.ok) ElMessage.success('校验通过')
    else ElMessage.warning('校验未通过，请查看详情')
  } catch (e) {
    ElMessage.error(e.message || '校验失败')
  } finally {
    checking.value = false
  }
}
function refresh() {
  loadAdmin().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => {
  loadAdmin().catch(e => ElMessage.error(e.message))
  loadPlacement().catch(() => {})
  loadHistory()
})
</script>
