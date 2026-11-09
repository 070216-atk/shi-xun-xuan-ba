<template>
  <div>
    <div class="page-title">名单管理 — {{ lab.name }}（第 {{ store.round }} 轮）</div>
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      :title="`拟录取名单不得超过本室名额（${lab.quota} 人）；递补名单按顺位排序，用于放弃后的自动递补；内部调剂候选用于统筹调剂第一优先级。`" />

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">拟录取名单（{{ admit.length }}/{{ lab.quota }}）</div>
          <el-select v-model="admit" multiple :multiple-limit="lab.quota" placeholder="从已面试学生中选择" style="width:100%">
            <el-option v-for="r in interviewed" :key="r.id" :value="r.id" :label="`${r.name}（总分 ${total(r.id)}）`" :disabled="waitlist.includes(r.id) || adjust.includes(r.id)" />
          </el-select>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="card">
          <div class="page-title">递补名单（按顺位）</div>
          <el-select v-model="waitlist" multiple placeholder="选择后按顺序定顺位" style="width:100%;margin-bottom:8px">
            <el-option v-for="r in interviewed" :key="r.id" :value="r.id" :label="`${r.name}（总分 ${total(r.id)}）`" :disabled="admit.includes(r.id) || adjust.includes(r.id)" />
          </el-select>
          <div v-for="(sid, i) in waitlist" :key="sid" class="rank-row">
            <span>顺位 {{ i + 1 }}：{{ nameOf(sid) }}</span>
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
            <el-option v-for="r in interviewed" :key="r.id" :value="r.id" :label="`${r.name}（总分 ${total(r.id)}）`" :disabled="admit.includes(r.id) || waitlist.includes(r.id)" />
          </el-select>
        </div>
      </el-col>
    </el-row>

    <div class="card" style="text-align:center">
      <el-button type="primary" size="large" @click="submit">提交三份名单并发出拟录取</el-button>
      <p class="muted">提交后系统向学生发出拟录取意向；仅收到 1 个意向的学生将自动确认。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { students } from '../../mock/data'
import { store, labById } from '../../store'

const lab = computed(() => labById(store.user.labId)).value
const saved = store.labLists[lab.id]
const admit = ref(saved ? [...saved.admit] : [])
const waitlist = ref(saved ? [...saved.waitlist] : [])
const adjust = ref(saved ? [...saved.adjust] : [])

const interviewed = computed(() => Object.entries(store.applications)
  .filter(([k, v]) => k.endsWith(':' + store.round) && v.labIds.includes(lab.id))
  .map(([k]) => students.find(x => x.id === k.split(':')[0]))
  .filter(s => store.scores[`${s.id}:${lab.id}`]))

function total(sid) {
  const s = store.scores[`${sid}:${lab.id}`]
  return s ? s.attitude + s.logic + s.coding + s.time : 0
}
function nameOf(sid) { return students.find(x => x.id === sid)?.name || sid }
function move(i, dir) {
  const arr = waitlist.value
  ;[arr[i], arr[i + dir]] = [arr[i + dir], arr[i]]
}

async function submit() {
  if (!admit.value.length) return ElMessage.warning('拟录取名单不能为空')
  if (admit.value.length > lab.quota) return ElMessage.error('拟录取人数超过名额')
  try {
    await ElMessageBox.confirm(`确认提交？将向 ${admit.value.length} 名学生发出拟录取意向。`, '二次确认', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' })
  } catch { return }
  store.labLists[lab.id] = { admit: [...admit.value], waitlist: [...waitlist.value], adjust: [...adjust.value] }
  admit.value.forEach(sid => {
    if (!store.offers[sid]) store.offers[sid] = []
    if (store.offers[sid].some(o => o.labId === lab.id)) return
    store.offers[sid].push({ labId: lab.id, status: 'pending' })
    if (store.offers[sid].length === 1) {
      store.offers[sid][0].status = 'confirmed'
      store.finalResults[sid] = lab.id
    }
    store.notifications.unshift({ time: '刚刚', text: `${nameOf(sid)} 收到 ${lab.name} 拟录取意向`, read: false })
  })
  ElMessage.success('名单已提交，拟录取意向已发出')
}
</script>

<style scoped>
.rank-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px dashed #eee; }
</style>