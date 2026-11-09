<template>
  <div>
    <div class="page-title">录取结果</div>
    <div class="card" style="text-align:center">
      <template v-if="finalLab">
        <el-result :icon="store.publicity.ended ? 'success' : 'info'"
          :title="store.publicity.ended ? '正式入组' : '已录取（公示中/待公示）'"
          :sub-title="`你的去向：${finalLab.name}`">
          <template #extra>
            <p>导师：{{ finalLab.mentors.map(m => m.name).join('、') }}</p>
            <p class="muted">地点：{{ finalLab.location }} ｜ 联系方式：{{ finalLab.contact }}</p>
          </template>
        </el-result>
      </template>
      <el-result v-else icon="info" title="结果尚未产生" sub-title="请耐心等待各阶段选拔完成，最终结果将在此展示并公示 1 天。" />
    </div>
    <div v-if="store.publicity.published" class="card">
      <div class="page-title">公示名单</div>
      <p class="muted">公示开始：{{ store.publicity.start }} ｜ 公示期 1 天，如有异议请线下联系学院教务。</p>
      <el-table :data="publicityRows" size="small" max-height="400">
        <el-table-column prop="id" label="学号" width="110" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="lab" label="录取实训室" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { students } from '../../mock/data'
import { store, labById } from '../../store'
const finalLab = computed(() => store.finalResults[store.user.id] ? labById(store.finalResults[store.user.id]) : null)
const publicityRows = computed(() => Object.entries(store.finalResults).map(([sid, lid]) => {
  const s = students.find(x => x.id === sid)
  return { id: sid, name: s?.name || sid, lab: labById(lid).name }
}))
</script>