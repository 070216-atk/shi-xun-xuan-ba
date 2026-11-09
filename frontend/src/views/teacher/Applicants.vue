<template>
  <div>
    <div class="page-title">报名学生 — {{ lab.name }}</div>
    <div class="card">
      <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;flex-wrap:wrap">
        <span>轮次：</span>
        <el-select v-model="round" style="width:140px">
          <el-option v-for="r in [1, 2, 3]" :key="r" :value="r" :label="`第 ${r} 轮`" />
        </el-select>
        <el-tag>报名 {{ rows.length }} 人</el-tag>
        <el-tag :type="rows.length >= lab.quota ? 'danger' : 'warning'">名额 {{ lab.quota }}（{{ rows.length >= lab.quota ? '已满额' : '未满额' }}）</el-tag>
      </div>
      <el-alert type="info" :closable="false" title="无论报名是否满额，所有报名学生均须参加实训室统一面试。" style="margin-bottom:12px" />
      <el-table :data="rows" size="small">
        <el-table-column prop="id" label="学号" width="110" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" width="100" />
        <el-table-column label="平行意向" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="id in row.labIds" :key="id" size="small" :type="id === lab.id ? 'success' : 'info'" style="margin:2px">{{ labById(id).name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内部调剂" width="85">
          <template #default="{ row }">{{ row.obeyInner ? '服从' : '不服从' }}</template>
        </el-table-column>
        <el-table-column label="跨室调剂" width="85">
          <template #default="{ row }">{{ row.obeyCross ? '服从' : '不服从' }}</template>
        </el-table-column>
        <el-table-column label="面试评价" width="90">
          <template #default="{ row }">
            <el-tag :type="store.scores[`${row.id}:${lab.id}`] ? 'success' : 'info'" size="small">
              {{ store.scores[`${row.id}:${lab.id}`] ? '已评' : '未评' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { students } from '../../mock/data'
import { store, labById } from '../../store'

const lab = computed(() => labById(store.user.labId)).value
const round = ref(store.round)
const rows = computed(() => Object.entries(store.applications)
  .filter(([k, v]) => k.endsWith(':' + round.value) && v.labIds.includes(lab.id))
  .map(([k, v]) => {
    const sid = k.split(':')[0]
    const s = students.find(x => x.id === sid)
    return { ...s, ...v }
  }))
</script>