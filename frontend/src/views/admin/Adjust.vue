<template>
  <div>
    <div class="page-title">统筹调剂工作台</div>
    <el-alert type="warning" :closable="false" style="margin-bottom:16px"
      title="调剂优先级不可颠倒：① 实训室内部调剂 → ② 全院跨实训室调剂（仅限服从者）→ ③ 约谈未服从调剂学生并统筹安置。后端会强制校验，跳级会返回错误。" />

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
import { computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { store, loadAdmin, doAdjust } from '../../store'

const vacancies = computed(() => (store.pool?.vacancies || []))
const candidates = computed(() => (store.pool?.candidates || []))

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

function refresh() {
  loadAdmin().then(() => ElMessage.success('已刷新')).catch(e => ElMessage.error(e.message))
}
onMounted(() => loadAdmin().catch(e => ElMessage.error(e.message)))
</script>
