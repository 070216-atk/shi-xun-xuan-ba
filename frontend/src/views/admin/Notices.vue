<template>
  <div>
    <div class="page-title">通知公告管理</div>

    <div class="card">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="openNew">新增公告</el-button>
        <el-button @click="refresh">刷新</el-button>
      </div>
      <el-table :data="rows" size="small">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.isTop" type="danger" size="small" style="margin-right:6px">置顶</el-tag>
            {{ row.title }}
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="publisherName" label="发布人" width="110" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : '已下线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="edit(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain
              @click="toggle(row)">{{ row.status === 1 ? '下线' : '发布' }}</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="暂无公告" :image-size="60" />
    </div>

    <el-dialog v-model="showDialog" :title="form.id ? '编辑公告' : '新增公告'" width="92%" style="max-width:520px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例如：2026 年实训室进组选拔开始" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="公告正文" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.isTop">置顶</el-checkbox>
          <el-checkbox v-model="form.published">已发布（取消勾选即下线）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminNotices, saveNotice, deleteNotice } from '../../api'

const rows = ref([])
const showDialog = ref(false)
const saving = ref(false)
const form = reactive({ id: null, title: '', content: '', isTop: false, published: true })

async function load() {
  try {
    rows.value = await getAdminNotices()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  Object.assign(form, { id: null, title: '', content: '', isTop: false, published: true })
  showDialog.value = true
}
function edit(row) {
  Object.assign(form, {
    id: row.id, title: row.title, content: row.content,
    isTop: !!row.isTop, published: row.status === 1
  })
  showDialog.value = true
}

async function save() {
  if (!form.title.trim()) return ElMessage.warning('标题不能为空')
  saving.value = true
  try {
    await saveNotice({
      id: form.id,
      title: form.title.trim(),
      content: form.content,
      isTop: form.isTop,
      status: form.published ? 1 : 0
    })
    showDialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  try {
    await saveNotice({
      id: row.id, title: row.title, content: row.content,
      isTop: !!row.isTop, status: row.status === 1 ? 0 : 1
    })
    ElMessage.success(row.status === 1 ? '已下线' : '已发布')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteNotice(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

function refresh() {
  load().then(() => ElMessage.success('已刷新'))
}
onMounted(load)
</script>
