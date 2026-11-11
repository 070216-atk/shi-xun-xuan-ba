/**
 * 数据层（已接后端）
 *
 * 设计原则：**保持和原来的 mock 一样的数据形状**，这样页面几乎不用改。
 *   · 人员一律用【学号】做 key（store.applications / offers / finalResults）
 *   · labs / stages / students 都是响应式数组，页面直接 v-for
 */
import { reactive } from 'vue'
import * as api from './api'
import { clearToken, getToken } from './api/http'

export const store = reactive({
  user: null,
  round: 1,
  currentStage: 0,
  stageDeadline: 0,

  applications: {},   // `学号:轮次` -> { labIds, mentorIds, obeyInner, obeyCross, time }
  scores: {},         // `学号:实训室id` -> { attitude, logic, coding, time, comment, total }
  labLists: {},       // 实训室id -> { admit, waitlist, adjust }
  offers: {},         // 学号 -> [{ offerId, labId, mentorId, mentorName, status }]
  finalResults: {},   // 学号 -> 实训室id
  adjustLog: [],
  talkRecords: [],
  publicity: { published: false, start: null, ended: false },
  notifications: [],
  quotas: [],         // 管理端：/admin/quotas 的原样数据
  applicants: [],     // 导师端：本室报名学生（后端原样）
  pool: { vacancies: [], candidates: [] },
  results: [],        // 管理端：已确认的最终名单
  dashboard: {}
})

export const labs = reactive([])
export const stages = reactive([])
export const students = reactive([])

// 学号 <-> 后端数字 id
const idToCode = {}
const codeToId = {}
const codeToName = {}

const pad = (n) => String(n).padStart(2, '0')
const fmt = (s) => (s ? String(s).slice(0, 16) : '')

/** 后端 'SUBMITTED' 这类枚举 -> 页面用的中文/简称 */
const offerStatus = (s) => ({
  ISSUED: 'pending',
  CONFIRMED: 'confirmed',
  DECLINED: 'declined',
  EXPIRED: 'expired',
  VOID: 'void'
}[s] || 'pending')

function indexStudents(list) {
  for (const s of list) {
    idToCode[s.studentId] = s.code
    codeToId[s.code] = s.studentId
    codeToName[s.code] = s.realName
    if (!students.find((x) => x.id === s.code)) {
      students.push({ id: s.code, sid: s.studentId, name: s.realName, className: s.className })
    }
  }
}

// ---------------- 公开数据 ----------------

export async function loadPublic() {
  const [ls, ss] = await Promise.all([api.getLabs(store.round), api.getStages(store.round)])
  labs.splice(0, labs.length, ...ls.map((l) => ({ ...l, mentors: l.mentors || [] })))
  stages.splice(
    0,
    stages.length,
    ...ss.map((s) => ({
      id: s.stageNo,
      dbId: s.id,
      name: s.name,
      window: `${fmt(s.startAt)} ~ ${fmt(s.endAt)}`,
      desc: s.description || '',
      open: s.open,
      current: s.isCurrent,
      remaining: s.remainingSeconds
    }))
  )
  const cur = stages.find((s) => s.current)
  store.currentStage = cur ? cur.id : 0
  store.stageDeadline = cur && cur.remaining != null ? Date.now() + cur.remaining * 1000 : 0
  return { labs, stages }
}

export async function loadLab(id) {
  const d = await api.getLab(id, store.round)
  const merged = { ...d, mentors: d.mentors || [] }
  const i = labs.findIndex((l) => String(l.id) === String(id))
  if (i >= 0) labs.splice(i, 1, merged)
  else labs.push(merged)
  return merged
}

export function labById(id) {
  return labs.find((l) => String(l.id) === String(id))
}

export function appliedCount(labId) {
  const l = labById(labId)
  return l ? l.applied || 0 : 0
}

export function remainingQuota(labId) {
  const q = store.quotas.find((x) => String(x.labId) === String(labId))
  if (q) return q.remaining
  const l = labById(labId)
  return l ? l.quota || 0 : 0
}

// ---------------- 登录 ----------------

function toUser(u) {
  return {
    id: u.code,          // 页面用学号当 key
    code: u.code,
    name: u.realName,
    role: (u.role || '').toLowerCase(),
    username: u.username,
    dbId: u.id,
    labId: u.labId,
    mentorId: u.mentorId,
    className: u.className
  }
}

/**
 * 登录。portal = 'web'（学生/导师统一入口）或 'admin'（管理后台）
 * 返回：'ok' | 'fail'（账号密码错）| 'forbidden'（走错入口）
 * 网络错误（后端没启动等）会直接抛出，由页面显示具体原因，不要伪装成密码错误
 */
export async function login(username, password, portal = 'web') {
  let data
  try {
    data = await api.login(username, password, portal)
  } catch (e) {
    if (e.code === 1003) return 'forbidden'
    if (e.code === 1002) return 'fail'
    throw e
  }
  store.user = toUser(data.user)
  return 'ok'
}

/** 刷新页面后用本地 token 恢复登录态 */
export async function restoreSession() {
  if (!getToken()) return null
  try {
    const u = await api.me()
    store.user = toUser(u)
    return store.user
  } catch (e) {
    clearToken()
    return null
  }
}

export function logout() {
  clearToken()
  store.user = null
  store.applications = {}
  store.offers = {}
  store.scores = {}
  store.labLists = {}
  store.finalResults = {}
  store.quotas = []
  store.applicants = []
  store.results = []
  store.pool = { vacancies: [], candidates: [] }
}

// ---------------- 学生端 ----------------

export async function loadStudent() {
  const sid = store.user.id
  const [app, offers] = await Promise.all([
    api.getMyApplication(store.round),
    api.getMyOffers(store.round)
  ])
  const key = `${sid}:${store.round}`
  if (app) {
    store.applications[key] = {
      labIds: (app.items || []).map((i) => i.labId),
      mentorIds: (app.items || []).map((i) => i.mentorId),
      obeyInner: !!app.adjustInLab,
      obeyCross: !!app.adjustCross,
      time: fmt(app.submittedAt)
    }
  } else {
    delete store.applications[key]
  }
  store.offers[sid] = offers.map((o) => ({
    offerId: o.id,
    labId: o.labId,
    mentorId: o.mentorId,
    mentorName: o.mentorName,
    labName: o.labName,
    type: o.type,
    status: offerStatus(o.status)
  }))
  const conf = offers.find((o) => o.status === 'CONFIRMED')
  if (conf) store.finalResults[sid] = conf.labId
  else delete store.finalResults[sid]
}

/** items: [{ labId, mentorId }] */
export async function submitApplication(items, adjustInLab, adjustCross) {
  await api.saveMyApplication({
    round: store.round,
    items,
    adjustInLab,
    adjustCross
  })
  await Promise.all([loadStudent(), loadPublic()])
}

export async function confirmOffer(offerId) {
  const r = await api.confirmOffer(offerId)
  await loadStudent()
  return r
}

export function studentStatus(studentId) {
  if (store.publicity.ended && store.finalResults[studentId]) return '正式入组'
  if (store.finalResults[studentId]) return '已录取'
  const os = store.offers[studentId] || []
  if (os.some((o) => o.status === 'confirmed')) return '已确认'
  if (os.some((o) => o.status === 'pending')) {
    return os.filter((o) => o.status === 'pending').length > 1 ? '待确认（多意向）' : '拟录取'
  }
  if (os.length && os.every((o) => ['declined', 'expired'].includes(o.status))) return '已放弃'
  if (store.applications[`${studentId}:${store.round}`]) return '已填报'
  return '未填报'
}

// ---------------- 导师端 ----------------

export async function loadTeacher() {
  const [list, lists] = await Promise.all([
    api.getApplicants(store.round),
    api.getTeacherLists(store.round)
  ])
  store.applicants = list
  indexStudents(
    list.map((a) => ({
      studentId: a.studentId,
      code: a.code,
      realName: a.realName,
      className: a.className
    }))
  )

  const labId = store.user.labId
  for (const a of list) {
    const code = a.code
    const key = `${code}:${labId}`
    if (a.scoreAttitude != null) {
      store.scores[key] = {
        attitude: a.scoreAttitude,
        logic: a.scoreLogic,
        coding: a.scoreCoding,
        time: a.scoreTime,
        comment: a.scoreComment || '',
        total: a.scoreTotal
      }
    }
    store.applications[`${code}:${store.round}`] = {
      labIds: [labId],
      mentorIds: [a.mentorId],
      obeyInner: !!a.adjustInLab,
      obeyCross: !!a.adjustCross,
      time: ''
    }
  }

  const pick = (arr) => (arr || []).map((o) => idToCode[o.studentId] || String(o.studentId))
  const mine = lists.mentorId
  if (mine) {
    store.labLists[labId] = {
      admit: pick(lists.admitted),
      waitlist: pick(lists.waitlist),
      adjust: pick(lists.adjustCandidates)
    }
  }
}

/** body: { attitude, logic, coding, time, comment } */
export async function saveScore(studentCode, body) {
  const sid = codeToId[studentCode]
  await api.saveScore(sid, { round: store.round, ...body })
  await loadTeacher()
}

export async function submitLists({ admit, waitlist, adjust }) {
  const map = (arr) => arr.map((c) => codeToId[c])
  await api.saveTeacherLists({
    round: store.round,
    admitted: map(admit),
    waitlist: map(waitlist),
    adjustCandidates: map(adjust)
  })
  await loadTeacher()
}

// ---------------- 管理端 ----------------

export async function loadAdmin() {
  const [qs, list, pool, db, results] = await Promise.all([
    api.getQuotas(store.round),
    api.getStudents(),
    api.getAdjustPool(store.round),
    api.getDashboard(store.round),
    api.getResults(store.round)
  ])
  store.quotas = qs
  store.results = results || []
  indexStudents(list.map((s) => ({ studentId: s.studentId, code: s.code, realName: s.realName, className: s.className })))
  store.dashboard = db
  store.pool = pool

  // 把调剂池里的学生也补进 applications / offers，供页面复用
  for (const c of pool.candidates) {
    store.applications[`${c.code}:${store.round}`] = store.applications[`${c.code}:${store.round}`] || {
      labIds: [],
      mentorIds: [],
      obeyInner: !!c.adjustInLab,
      obeyCross: !!c.adjustCross,
      time: ''
    }
    store.applications[`${c.code}:${store.round}`].obeyInner = !!c.adjustInLab
    store.applications[`${c.code}:${store.round}`].obeyCross = !!c.adjustCross
    if (c.offerType && c.offerStatus) {
      store.offers[c.code] = [
        {
          offerId: null,
          labId: null,
          type: c.offerType,
          status: offerStatus(c.offerStatus),
          rankNo: c.rankNo
        }
      ]
    }
  }

  // 公示状态
  try {
    const p = await api.getPublicity(store.round)
    if (p) {
      store.publicity = {
        published: p.status === 'PUBLISHED' || p.status === 'ENDED',
        start: fmt(p.startAt),
        ended: p.status === 'ENDED'
      }
    }
  } catch (e) {
    /* 没有公示记录就保持默认 */
  }
}

export async function doAdjust(level, mentorId, studentCodes, conclusion) {
  const studentIds = studentCodes.map((c) => codeToId[c])
  const body = { round: store.round, mentorId, studentIds, conclusion }
  if (level === 1) await api.adjustInternal(body)
  else if (level === 2) await api.adjustCross(body)
  else await api.adjustTalk(body)
  await loadAdmin()
}

export async function switchStage(stageNo) {
  const st = stages.find((s) => s.id === stageNo)
  if (!st || !st.dbId) return
  const now = new Date()
  const start = new Date(now.getTime() - 3600 * 1000)
  const end = new Date(now.getTime() + 4 * 3600 * 1000)
  const f = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
  await api.updateStage(st.dbId, { startAt: f(start), endAt: f(end) })
  await loadPublic()
}

export async function openNextRound() {
  await api.openRound(store.round + 1)
  store.round += 1
  await loadPublic()
}

export async function publishPublicity() {
  const p = await api.publishPublicity({ round: store.round, days: 1 })
  store.publicity = { published: true, start: fmt(p.startAt), ended: false }
  return p
}

export async function endPublicity() {
  await api.endPublicity(store.round)
  store.publicity.ended = true
}

export async function markObjection(reason) {
  await api.addObjection({ round: store.round, reason })
}

export async function resolveObjections() {
  await api.resolveObjections(store.round)
}

export { fmt }
