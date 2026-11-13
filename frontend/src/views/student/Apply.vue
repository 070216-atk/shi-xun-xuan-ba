<template>
  <div>
    <div class="page-title">
      意向填报
      <el-tag v-if="store.round > 1 || store.currentStage === 6" type="warning" style="margin-left:8px">
        {{ store.round > 1 ? `第${store.round}轮` : '' }}补录
      </el-tag>
    </div>

    <el-alert v-if="!windowOpen && !admitted" type="info" :closable="false" show-title
      :title="`当前不在填报窗口期（当前阶段：${currentStageName}）。填报只在「意向填报」或「分轮补录」阶段开放。`"
      style="margin-bottom:16px" />

    <div class="card">
      <el-result v-if="admitted" icon="success"
        :title="`你已在第 ${admitted.round} 轮被录取`"
        :sub-title="`去向：${admitted.lab}。已被录取的同学不再参与后续轮次，无需再填报。`" />
      <template v-else>
        <el-alert type="warning" :closable="false" style="margin-bottom:16px"
          :title="`填报规则：${maxItems === 1 ? '补录阶段只能填报 1 个导师意向' : '最多 3 个平行导师意向，不分先后'}；每轮只能提交一次，提交后不可修改。`" />
        <el-alert v-if="locked" type="success" :closable="false" style="margin-bottom:16px"
          title="本轮意向已提交，内容不可修改。" />
        <div v-if="windowOpen && store.stageDeadline" style="margin-bottom:16px">
          距离截止：<span class="deadline">{{ countdown }}</span>
        </div>

        <el-form label-position="top">
          <el-form-item v-for="i in maxItems" :key="i" :label="`平行意向 ${i}${maxItems > 1 && i > 1 ? '（可不填）' : ''}`">
            <el-select v-model="selected[i - 1]" :disabled="!windowOpen || locked" clearable
              placeholder="选择实训室 / 导师" style="width:100%">
              <el-option-group v-for="lab in labs" :key="lab.id" :label="lab.name">
                <el-option v-for="m in lab.mentors" :key="m.id" :value="`${lab.id}:${m.id}`"
                  :label="`${m.name}（${m.title || '导师'}，名额 ${m.quota}）`"
                  :disabled="selected.some((v, idx) => idx !== i - 1 && v === `${lab.id}:${m.id}`)" />
              </el-option-group>
            </el-select>
          </el-form-item>

          <el-form-item label="调剂意愿（两项独立选择）">
            <div><el-checkbox v-model="obeyInner" :disabled="!windowOpen || locked">服从实训室内部调剂</el-checkbox></div>
            <div><el-checkbox v-model="obeyCross" :disabled="!windowOpen || locked">服从全院跨实训室调剂</el-checkbox></div>
          </el-form-item>

          <el-button type="primary" size="large" :disabled="!windowOpen || locked" :loading="saving"
            style="width:100%" @click="onSubmit">
            {{ locked ? '已提交，不可修改' : '提交意向' }}
          </el-button>
        </el-form>
      </template>
    </div>

    <div v-if="existing" class="card">
      <div class="page-title">已提交的意向</div>
      <el-tag v-for="(lid, i) in existing.labIds" :key="i" style="margin:0 8px 8px 0">
        {{ labName(lid) }} · {{ mentorName(lid, existing.mentorIds[i]) }}
      </el-tag>
      <p class="muted">
        提交时间：{{ existing.time }} ｜ 内部调剂：{{ existing.obeyInner ? '服从' : '不服从' }} ｜
        跨室调剂：{{ existing.obeyCross ? '服从' : '不服从' }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { labs, stages, store, loadStudent, submitApplication, labById } from '../../store'

const saving = ref(false)
const key = computed(() => `${store.user?.id}:${store.round}`)
const existing = computed(() => store.applications[key.value])
/** 每轮只能提交一次，已提交后表单锁定 */
const locked = computed(() => !!existing.value)
const windowOpen = computed(() => store.currentStage === 3 || store.currentStage === 6)
/** 补录阶段只允许报 1 个导师，正常意向填报阶段最多 3 个 */
const maxItems = computed(() => store.currentStage === 6 ? 1 : 3)
const currentStageName = computed(() => stages.find(s => s.id === store.currentStage)?.name || '非填报期')

/** 已被录取（含前几轮）：录取是终局状态，后续轮次不能再填报 */
const admitted = computed(() => {
  const st = store.studentStatus
  if (!st || !['ADMITTED', 'CONFIRMED'].includes(st.status)) return null
  return {
    round: st.admittedRound || store.round,
    lab: st.admittedLabName || labName(store.finalResults[store.user?.id])
  }
})

const selected = ref(['', '', ''])
const obeyInner = ref(true)
const obeyCross = ref(true)

watch(existing, (v) => {
  if (!v) return
  const keys = (v.labIds || []).map((lid, i) => `${lid}:${(v.mentorIds || [])[i]}`)
  selected.value = [keys[0] || '', keys[1] || '', keys[2] || '']
  obeyInner.value = !!v.obeyInner
  obeyCross.value = !!v.obeyCross
}, { immediate: true })

function labName(id) { return labById(id)?.name || `实训室 ${id}` }
function mentorName(labId, mentorId) {
  const m = (labById(labId)?.mentors || []).find(x => x.id === mentorId)
  return m ? m.name : ''
}

const now = ref(Date.now())
const timer = setInterval(() => (now.value = Date.now()), 1000)
onUnmounted(() => clearInterval(timer))
const countdown = computed(() => {
  const ms = Math.max(0, store.stageDeadline - now.value)
  const h = Math.floor(ms / 3600000), m = Math.floor(ms % 3600000 / 60000), s = Math.floor(ms % 60000 / 1000)
  return `${h}时${String(m).padStart(2, '0')}分${String(s).padStart(2, '0')}秒`
})

onMounted(() => {
  if (store.user?.role === 'student') loadStudent().catch(e => ElMessage.error(e.message))
})

async function onSubmit() {
  if (locked.value) return ElMessage.warning('本轮意向已提交，不能重复提交或修改')
  if (admitted.value) {
    return ElMessage.warning(`你已在第 ${admitted.value.round} 轮被录取，不能再填报意向`)
  }
  const keys = selected.value.slice(0, maxItems.value).filter(Boolean)
  if (!keys.length) return ElMessage.warning('请至少填报 1 个意向')
  if (new Set(keys).size !== keys.length) return ElMessage.warning('意向不能重复')

  const items = keys.map(k => {
    const [labId, mentorId] = k.split(':').map(Number)
    return { labId, mentorId }
  })

  try {
    await ElMessageBox.confirm(
      `确认提交 ${items.length} 个平行意向（不分先后）？内部调剂：${obeyInner.value ? '服从' : '不服从'}，跨室调剂：${obeyCross.value ? '服从' : '不服从'}`,
      '二次确认', { confirmButtonText: '确认提交', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch { return }

  saving.value = true
  try {
    await submitApplication(items, obeyInner.value, obeyCross.value)
    store.notifications.unshift({ time: '刚刚', text: `第${store.round}轮意向已提交成功`, read: false })
    ElMessage.success('提交成功')
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    saving.value = false
  }
}
</script>
