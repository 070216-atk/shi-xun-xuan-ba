<template>
  <div>
    <div class="page-title">统筹调剂工作台</div>
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      title="调剂优先级不可颠倒：① 实训室内部调剂 → ② 全院跨实训室调剂（仅限服从者）→ ③ 约谈未服从调剂学生并统筹安置。每一步操作自动留痕。" />

    <div class="card">
      <div class="page-title">调剂池（剩余名额）</div>
      <el-tag v-for="l in pool" :key="l.id" :type="l.remaining > 0 ? 'warning' : 'info'" style="margin:4px">
        {{ l.name }}：{{ l.remaining }}
      </el-tag>
      <p class="muted">待安置学生：{{ unassigned.length }} 人</p>
    </div>

    <div class="card">
      <div class="page-title">① 实训室内部调剂</div>
      <el-table :data="p1Rows" size="small">
        <el-table-column prop="name" label="学生" width="100" />
        <el-table-column prop="labName" label="候选实训室" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="remainingQuota(row.labId) <= 0" @click="assign(row.sid, row.labId, 1)">安排录取</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p1Rows.length" description="无内部调剂候选或已全部处理" :image-size="50" />
    </div>

    <div class="card">
      <div class="page-title">② 全院跨实训室调剂<el-tag v-if="p1Rows.length" type="info" size="small" style="margin-left:8px">须先完成①</el-tag></div>
      <el-table :data="p2Rows" size="small">
        <el-table-column prop="name" label="学生（服从跨室调剂）" min-width="160" />
        <el-table-column label="安排去向" min-width="240">
          <template #default="{ row }">
            <el-select v-model="p2Target[row.id]" :disabled="p1Rows.length > 0" size="small" placeholder="选择有剩余名额的实训室" style="width:100%">
              <el-option v-for="l in pool.filter(x => x.remaining > 0)" :key="l.id" :value="l.id" :label="l.name" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="p1Rows.length > 0 || !p2Target[row.id]" @click="assign(row.id, p2Target[row.id], 2)">安排录取</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p2Rows.length" description="无服从跨室调剂的待安置学生" :image-size="50" />
    </div>

    <div class="card">
      <div class="page-title">③ 约谈与最终统筹安置<el-tag v-if="p1Rows.length || p2Rows.length" type="info" size="small" style="margin-left:8px">须先完成①②</el-tag></div>
      <el-table :data="p3Rows" size="small">
        <el-table-column prop="name" label="学生（未服从调剂）" min-width="150" />
        <el-table-column label="约谈记录" min-width="200">
          <template #default="{ row }">
            <el-input v-model="talkText[row.id]" size="small" placeholder="记录线下约谈情况" />
          </template>
        </el-table-column>
        <el-table-column label="安置去向" min-width="200">
          <template #default="{ row }">
            <el-select v-model="p3Target[row.id]" size="small" placeholder="最终安置实训室" style="width:100%">
              <el-option v-for="l in pool" :key="l.id" :value="l.id" :label="`${l.name}（剩 ${l.remaining}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="warning" :disabled="p1Rows.length > 0 || p2Rows.length > 0 || !talkText[row.id] || !p3Target[row.id]" @click="assign(row.id, p3Target[row.id], 3, talkText[row.id])">安置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!p3Rows.length" description="无需约谈安置的学生" :image-size="50" />
    </div>

    <div class="card">
      <div class="page-title">操作留痕</div>
      <el-table :data="[...store.adjustLog].reverse()" size="small" max-height="300">
        <el-table-column prop="time" label="时间" width="170" />
        <el-table-column prop="level" label="优先级" width="90" />
        <el-table-column prop="text" label="操作内容" min-width="260" />
      </el-table>
      <el-empty v-if="!store.adjustLog.length" description="暂无调剂操作" :image-size="50" />
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { labs, students } from '../../mock/data'
import { store, labById, remainingQuota } from '../../store'

const pool = computed(() => labs.map(l => ({ id: l.id, name: l.name, remaining: remainingQuota(l.id) })))

function latestApp(sid) {
  for (const r of [3, 2, 1]) {
    const a = store.applications[`${sid}:${r}`]
    if (a) return a
  }
  return null
}
const unassigned = computed(() => students.filter(s => latestApp(s.id) && !store.finalResults[s.id]))

const p1Rows = computed(() => {
  const rows = []
  for (const lab of labs) {
    const list = store.labLists[lab.id]
    if (!list) continue
    for (const sid of list.adjust) {
      if (!store.finalResults[sid]) rows.push({ sid, name: students.find(x => x.id === sid)?.name || sid, labId: lab.id, labName: lab.name })
    }
  }
  return rows
})
const p2Rows = computed(() => unassigned.value
  .filter(s => latestApp(s.id)?.obeyCross && !p1Rows.value.some(r => r.sid === s.id)))
const p3Rows = computed(() => unassigned.value
  .filter(s => !latestApp(s.id)?.obeyCross && !p1Rows.value.some(r => r.sid === s.id)))

const p2Target = reactive({})
const p3Target = reactive({})
const talkText = reactive({})

function assign(sid, labId, level, talk) {
  const sname = students.find(x => x.id === sid)?.name || sid
  store.finalResults[sid] = labId
  if (talk) store.talkRecords.push({ sid, talk, time: new Date().toLocaleString('zh-CN') })
  store.adjustLog.push({
    time: new Date().toLocaleString('zh-CN'),
    level: ['', '①内部', '②跨室', '③约谈'][level],
    text: `${sname} → ${labById(labId).name}${talk ? `（约谈：${talk}）` : ''}`
  })
  store.notifications.unshift({ time: '刚刚', text: `${sname} 已通过统筹调剂安排至 ${labById(labId).name}`, read: false })
  ElMessage.success('已安排录取并留痕')
}
</script>