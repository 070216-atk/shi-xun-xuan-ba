<template>
  <div>
    <div class="page-title">统筹调剂工作台</div>
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      title="调剂优先级不可颠倒：① 实训室内部调剂 → ② 全院跨实训室调剂（仅限服从者）→ ③ 约谈未服从调剂学生并统筹安置。后端会强制校验，跳级会返回错误。" />

    <div class="card" style="border:2px solid #1b6e5a">
      <div class="page-title">收口安置（确保每个人都有实训室）</div>
      <p class="muted">
        所有调剂做完后，点「一键收口」：有待确认拟录取的按顺位定案，没有的按「同室 → 跨室 → 经约谈统筹」依次落到有空位的导师。
        最后一轮补录结束后，所有还没有去向的学生（包括从未报名、只在往轮报过名的）都会进入下面的未安置名单，可单独「指定安置」或跟着「一键收口」一起兜底安置。
        收口校验不通过时不能发布公示。
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
        <el-button type="primary" :loading="finalizing" :disabled="!gaps || !gaps.unplacedCount" @click="doFinalize">
          一键收口（{{ gaps ? gaps.unplacedCount : 0 }} 人）
        </el-button>
        <el-button :loading="checking" @click="doVerify">校验</el-button>
        <el-button @click="refreshGaps">刷新缺口</el-button>
      </div>
      <div v-if="gaps && gaps.unplaced.length" style="margin-top:14px;padding-top:12px;border-top:1px dashed #ddd">
        <div class="page-title" style="font-size:14px">指定安置（约谈后同意去某个导师）</div>
        <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
          <el-select v-model="assignStudent" placeholder="选学生" style="width:180px" filterable>
            <el-option v-for="u in gaps.unplaced" :key="u.studentId" :value="u.studentId"
              :label="`${u.realName}（${u.code}）${sourceLabel(u.source)}`" />
          </el-select>
          <el-select v-model="assignMentor" placeholder="选导师" style="width:200px" filterable>
            <el-option v-for="m in gaps.availableMentors" :key="m.mentorId" :value="m.mentorId"
              :label="`${m.labCode} ${m.mentorName}（剩 ${m.remaining}）`" />
          </el-select>
          <el-input v-model="assignReason" placeholder="安置原因（必填，学生能看到）" style="width:260px" />
          <el-button type="success" :loading="assigning" @click="doAssign">指定安置</el-button>
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
      <el-table :data="p1Rows" size="small">
        <el-table-column prop="realName" label="学生" width="100" />
        <el-table-column prop="code" label="学号" width="110" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column label="安排到导师" min-width="260">
          <template #default="{ row }">
            <el-select v-model="t1[row.code]" size="small" placeholder="选择有剩余名额的导师" style="width:100%">
              <el-option v-for="v in vacancies.filter(x => x.remaining > 0)" :key="v.mentorId"
                :value="v.mentorId" :label="`${v.labCode} ${v.mentorName}（剩 ${v.remaining}）`" />
            </el-select>
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
        <el-tag v-if="p1Rows.length" type="info" size="small" style="margin-left:8px">须先完成①</el-tag>
      </div>
      <el-table :data="p2Rows" size="small">
        <el-table-column prop="realName" label="学生（服从跨室调剂）" min-width="160" />
        <el-table-column label="安排到导师" min-width="260">
          <template #default="{ row }">
            <el-select v-model="t2[row.code]" :disabled="p1Rows.length > 0" size="small"
              placeholder="选择有剩余名额的导师" style="width:100%">
              <el-option v-for="v in vacancies.filter(x => x.remaining > 0)" :key="v.mentorId"
                :value="v.mentorId" :label="`${v.labCode} ${v.mentorName}（剩 ${v.remaining}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="p1Rows.length > 0 || !t2[row.code]"
              @click="assign(2, row.code, t2[row.code])">安排录取</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p2Rows.length" description="无服从跨室调剂的待安置学生" :image-size="50" />
    </div>

    <div class="card">
      <div class="page-title">
        ③ 约谈与最终统筹安置
        <el-tag v-if="p1Rows.length || p2Rows.length" type="info" size="small" style="margin-left:8px">须先完成①②</el-tag>
      </div>
      <el-table :data="p3Rows" size="small">
        <el-table-column prop="realName" label="学生（未服从调剂）" min-width="150" />
        <el-table-column label="约谈记录" min-width="200">
          <template #default="{ row }">
            <el-input v-model="talk[row.code]" size="small" placeholder="记录线下约谈情况" />
          </template>
        </el-table-column>
        <el-table-column label="安置去向" min-width="220">
          <template #default="{ row }">
            <el-select v-model="t3[row.code]" size="small" placeholder="最终安置导师" style="width:100%">
              <el-option v-for="v in vacancies" :key="v.mentorId" :value="v.mentorId"
                :label="`${v.labCode} ${v.mentorName}（剩 ${v.remaining}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="warning"
              :disabled="p1Rows.length > 0 || p2Rows.length > 0 || !talk[row.code] || !t3[row.code]"
              @click="assign(3, row.code, t3[row.code], talk[row.code])">安置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p3Rows.length" description="无需约谈安置的学生" :image-size="50" />
    </div>

    <div class="card">
      <div class="page-title">操作留痕</div>
      <p class="muted">每次调剂都会写入 offer.remark 和 operation_log，可在数据库里追溯。</p>
      <el-table :data="store.adjustLog" size="small" max-height="300">
        <el-table-column prop="time" label="时间" width="170" />
        <el-table-column prop="level" label="优先级" width="90" />
        <el-table-column prop="text" label="操作内容" min-width="260" />
      </el-table>
      <el-empty v-if="!store.adjustLog.length" description="暂无本页调剂操作记录" :image-size="50" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { store, loadAdmin, doAdjust, loadPlacement, finalizePlacement, verifyPlacement, assignPlacement } from '../../store'

const vacancies = computed(() => (store.pool?.vacancies || []))
const candidates = computed(() => (store.pool?.candidates || []))

/** 未安置学生的来源：本轮未录取 / 从未报名 / 仅往轮报名未录取 */
const sourceLabel = (s) => ({ NEVER: ' · 未报名', EARLIER: ' · 往轮未录' }[s] || '')
const sourceTag = (s) => ({ NEVER: 'info', EARLIER: 'warning' }[s] || 'danger')

const p1Rows = computed(() => candidates.value.filter(c => c.adjustInLab))
const p2Rows = computed(() => candidates.value.filter(c => !c.adjustInLab && c.adjustCross))
const p3Rows = computed(() => candidates.value.filter(c => !c.adjustInLab && !c.adjustCross))

const t1 = reactive({})
const t2 = reactive({})
const t3 = reactive({})
const talk = reactive({})

async function assign(level, code, mentorId, conclusion) {
  try {
    await doAdjust(level, mentorId, [code], conclusion)
    const v = vacancies.value.find(x => x.mentorId === mentorId)
    store.adjustLog.push({
      time: new Date().toLocaleString('zh-CN'),
      level: ['', '①内部', '②跨室', '③约谈'][level],
      text: `${code} → ${v ? v.labCode + ' ' + v.mentorName : mentorId}${conclusion ? '（约谈：' + conclusion + '）' : ''}`
    })
    ElMessage.success('已安排录取')
  } catch (e) {
    ElMessage.error(e.message || '安排失败')
  }
}

const finalizing = ref(false)
const checking = ref(false)
const verifyResult = ref(null)
const gaps = computed(() => store.placement)

async function refreshGaps() {
  try { await loadPlacement() } catch (e) { ElMessage.error(e.message || '刷新失败') }
}

async function doFinalize() {
  try {
    await ElMessageBox.confirm(
      `确认收口？会把没着落的学生按「同室 → 跨室 → 经约谈统筹」依次安置，不服从调剂的人也会被安排。`,
      '一键收口', { type: 'warning', confirmButtonText: '开始收口', cancelButtonText: '取消' }
    )
  } catch { return }
  finalizing.value = true
  try {
    const r = await finalizePlacement(true)
    verifyResult.value = r.verify
    ElMessage.success(
      `收口完成：同室 ${r.assignedInLab} 人、跨室 ${r.assignedCross} 人、强制 ${r.forced} 人，仍未安置 ${r.stillUnplacedCount} 人`
    )
  } catch (e) {
    ElMessage.error(e.message || '收口失败')
  } finally {
    finalizing.value = false
  }
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
})
</script>
