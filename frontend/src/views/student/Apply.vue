<template>
  <div>
    <div class="page-title">意向填报<el-tag v-if="store.round > 1" type="warning" style="margin-left:8px">第{{ store.round }}轮补录</el-tag></div>

    <el-alert v-if="!windowOpen" type="info" :closable="false" show-title
      :title="`当前不在填报窗口期（当前阶段：${currentStageName}）。填报在「意向填报」或「分轮补录」阶段开放。`" style="margin-bottom:16px" />

    <div class="card">
      <el-alert type="warning" :closable="false" style="margin-bottom:16px"
        title="填报规则：最多 3 个平行导师意向，不分先后；截止前可反复修改，截止后锁定不可修改。" />
      <div v-if="windowOpen" style="margin-bottom:16px">
        距离截止：<span class="deadline">{{ countdown }}</span>
      </div>

      <el-form label-position="top">
        <el-form-item v-for="i in 3" :key="i" :label="`平行意向 ${i}${i > 1 ? '（可不填）' : ''}`">
          <el-select v-model="selected[i - 1]" :disabled="!windowOpen" clearable placeholder="选择实训室 / 导师" style="width:100%">
            <el-option v-for="lab in labs" :key="lab.id" :value="lab.id"
              :label="`${lab.name}（${lab.mentors[0].name} 等，名额 ${lab.quota}）`"
              :disabled="selected.some((v, idx) => idx !== i - 1 && v === lab.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="调剂意愿（两项独立选择）">
          <div>
            <el-checkbox v-model="obeyInner" :disabled="!windowOpen">服从实训室内部调剂</el-checkbox>
          </div>
          <div>
            <el-checkbox v-model="obeyCross" :disabled="!windowOpen">服从全院跨实训室调剂</el-checkbox>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" :disabled="!windowOpen" style="width:100%" @click="onSubmit">
          {{ existing ? '修改并提交' : '提交意向' }}
        </el-button>
      </el-form>
    </div>

    <div v-if="existing" class="card">
      <div class="page-title">已提交的意向</div>
      <el-tag v-for="id in existing.labIds" :key="id" style="margin-right:8px">{{ labById(id).name }}</el-tag>
      <p class="muted">提交时间：{{ existing.time }} ｜ 内部调剂：{{ existing.obeyInner ? '服从' : '不服从' }} ｜ 跨室调剂：{{ existing.obeyCross ? '服从' : '不服从' }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { labs, stages } from '../../mock/data'
import { store, labById } from '../../store'

const key = computed(() => `${store.user.id}:${store.round}`)
const existing = computed(() => store.applications[key.value])
const windowOpen = computed(() => store.currentStage === 3 || store.currentStage === 6)
const currentStageName = computed(() => stages.find(s => s.id === store.currentStage)?.name)

const selected = ref(existing.value ? [...existing.value.labIds, '', '', ''].slice(0, 3) : ['', '', ''])
const obeyInner = ref(existing.value?.obeyInner ?? true)
const obeyCross = ref(existing.value?.obeyCross ?? true)

const now = ref(Date.now())
const timer = setInterval(() => (now.value = Date.now()), 1000)
onUnmounted(() => clearInterval(timer))
const countdown = computed(() => {
  let ms = Math.max(0, store.stageDeadline - now.value)
  const h = Math.floor(ms / 3600000), m = Math.floor(ms % 3600000 / 60000), s = Math.floor(ms % 60000 / 1000)
  return `${h}时${String(m).padStart(2, '0')}分${String(s).padStart(2, '0')}秒`
})

async function onSubmit() {
  const labIds = selected.value.filter(Boolean)
  if (!labIds.length) return ElMessage.warning('请至少填报 1 个意向')
  if (new Set(labIds).size !== labIds.length) return ElMessage.warning('意向不能重复')
  try {
    await ElMessageBox.confirm(
      `确认提交 ${labIds.length} 个平行意向（不分先后）？内部调剂：${obeyInner.value ? '服从' : '不服从'}，跨室调剂：${obeyCross.value ? '服从' : '不服从'}`,
      '二次确认', { confirmButtonText: '确认提交', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch { return }
  store.applications[key.value] = {
    labIds, obeyInner: obeyInner.value, obeyCross: obeyCross.value,
    time: new Date().toLocaleString('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  }
  store.notifications.unshift({ time: '刚刚', text: `第${store.round}轮意向已提交成功`, read: false })
  ElMessage.success('提交成功')
}
</script>