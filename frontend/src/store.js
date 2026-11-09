import { reactive, watch } from 'vue'
import { users, students, labs } from './mock/data'

const KEY = 'lab-selection-demo-v1'

function seed() {
  const applications = {}
  students.slice(0, 36).forEach((s, i) => {
    const a = [(i % 5) + 1, ((i + 2) % 5) + 1]
    const labIds = i % 3 === 0 ? [...a, ((i + 4) % 5) + 1].map(n => 'L' + n) : a.map(n => 'L' + n)
    applications[`${s.id}:1`] = { labIds, obeyInner: i % 4 !== 0, obeyCross: i % 3 !== 0, time: '11月2日 09:' + String(10 + (i % 50)).padStart(2, '0') }
  })
  return {
    user: null,
    currentStage: 3,
    round: 1,
    stageDeadline: Date.now() + 4 * 3600 * 1000,
    applications,
    scores: {},
    labLists: {},
    offers: {
      S2023001: [{ labId: 'L1', status: 'pending' }, { labId: 'L3', status: 'pending' }],
      S2023002: [{ labId: 'L2', status: 'confirmed' }]
    },
    finalResults: { S2023002: 'L2' },
    adjustLog: [],
    talkRecords: [],
    publicity: { published: false, start: null, ended: false },
    notifications: [
      { time: '11月2日 08:00', text: '意向填报已开启，截止 12:00，请尽快提交', read: false }
    ]
  }
}

let saved = null
try { saved = JSON.parse(localStorage.getItem(KEY)) } catch { /* 损坏的缓存直接重建 */ }

export const store = reactive(saved && saved.applications ? saved : seed())

watch(store, () => localStorage.setItem(KEY, JSON.stringify(store)), { deep: true })

export function resetDemo() {
  Object.assign(store, seed())
}

export function login(username, password, expectedRole) {
  const u = users.find(u => u.username === username && u.password === password)
  if (!u) return 'fail'
  if (expectedRole && u.role !== expectedRole) return 'mismatch'
  store.user = { ...u }
  delete store.user.password
  return 'ok'
}

export function logout() {
  store.user = null
}

export function labById(id) {
  return labs.find(l => l.id === id)
}

export function appliedCount(labId, round = 1) {
  return Object.entries(store.applications)
    .filter(([k, v]) => k.endsWith(':' + round) && v.labIds.includes(labId)).length
}

export function remainingQuota(labId) {
  const lab = labById(labId)
  const taken = Object.values(store.finalResults).filter(id => id === labId).length
  return lab.quota - taken
}

export function setStage(id) {
  store.currentStage = id
  store.stageDeadline = Date.now() + 4 * 3600 * 1000
  if (id === 6 && store.round < 3) store.round += 1
}

export function studentStatus(studentId) {
  if (store.publicity.ended && store.finalResults[studentId]) return '正式入组'
  if (store.finalResults[studentId]) return '已录取'
  const offers = store.offers[studentId] || []
  if (offers.some(o => o.status === 'confirmed')) return '已确认'
  if (offers.some(o => o.status === 'pending')) return offers.filter(o => o.status === 'pending').length > 1 ? '待确认（多意向）' : '拟录取'
  if (offers.length && offers.every(o => ['declined', 'expired'].includes(o.status))) return '已放弃'
  const app = store.applications[`${studentId}:${store.round}`]
  if (app) return store.currentStage >= 4 ? '面试完成' : '已填报'
  if (store.currentStage >= 6) return '补录中'
  return '未填报'
}