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
  rounds: [],         // [{ roundNo, status }]，数据从 /api/rounds 来
  currentStage: 0,
  stageDeadline: 0,

  applications: {},   // `学号:轮次` -> { labIds, mentorIds, obeyInner, obeyCross, time }
  scores: {},         // `学号:实训室id` -> { attitude, logic, coding, time, comment, total }
  labLists: {},       // 实训室id -> { admit, waitlist, adjust }
  offers: {},         // 学号 -> [{ offerId, labId, mentorId, mentorName, status }]
  finalResults: {},   // 学号 -> 实训室id
  publicity: { published: false, start: null, end: null, ended: false },
  objections: [],     // 管理端：当前公示未处理的异议
  notifications: [],
  quotas: [],         // 管理端：/admin/quotas 的原样数据
  applicants: [],     // 导师端：本室报名学生（后端原样）
  interviewList: [],  // 导师端：本室面试名单（入围者的并集）
  adjustInbox: [],    // 导师端：别人指定调剂给我的学生
  transferInbox: [],  // 导师端：学生申请转到我的名下的转组申请
  transfers: [],      // 学生端：本人本轮的转组申请（含历史）
  pool: { vacancies: [], candidates: [] },
  results: [],        // 管理端：已确认的最终名单
  publicityList: [],  // 公示名单（公开接口）
  dashboard: {},
  placement: null   // 管理端：收口缺口清单
})

export const labs = reactive([])
export const stages = reactive([])
export const students = reactive([])
export const notices = reactive([])

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

/** 轮次状态的统一文案：进行中 / 已结束 / 未开启（后端只有这三种状态） */
export function roundText(status) {
  return { OPEN: '进行中', CLOSED: '已结束', PLANNING: '未开启' }[status] || status || '未知'
}

/** 当前选中轮次的状态 + 正在进行的轮次号（提示"切回第几轮"时要用） */
export function roundState() {
  const cur = store.rounds.find((r) => r.roundNo === store.round)
  const open = store.rounds.find((r) => r.status === 'OPEN')
  return { status: cur ? cur.status : '', openRound: open ? open.roundNo : 0 }
}

/**
 * 拉轮次列表。preferOpen=true 时（启动/刷新页面）自动落到"进行中"的那一轮，
 * 这样管理员开启第 2 轮后刷新页面，看到的仍然是第 2 轮，而不是又被拉回第 1 轮。
 */
export async function loadRounds(preferOpen = false) {
  const list = (await api.getRounds()) || []
  store.rounds = list
  if (preferOpen && list.length) {
    const open = list.find((r) => r.status === 'OPEN')
    store.round = open ? open.roundNo : list[list.length - 1].roundNo
  }
  return store.rounds
}

/** 手动切轮次：公开数据 + 当前角色自己的数据都重新按新一轮加载 */
export async function setRound(round) {
  if (round === store.round) return
  store.round = round
  await loadPublic()
  const role = store.user?.role
  if (role === 'student') await loadStudent()
  else if (role === 'teacher') await loadTeacher()
  else if (role === 'admin') await loadAdmin()
}

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

export async function loadLab(id) {  const d = await api.getLab(id, store.round)
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

/** 学号 -> 姓名（索引里没有就原样返回，绝不显示数字 id） */
export function nameByCode(code) {
  return codeToName[code] || code
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

/**
 * 整页刷新时只恢复一次登录态。
 * 路由守卫和 main.js 都等它，避免守卫先跑、user 还是 null，被踢回登录页。
 */
let bootPromise = null
export function ensureSession() {
  if (!bootPromise) bootPromise = restoreSession().catch(() => null)
  return bootPromise
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
  store.adjustInbox = []
  store.transferInbox = []
  store.transfers = []
  store.pool = { vacancies: [], candidates: [] }
}

// ---------------- 学生端 ----------------

export async function loadStudent() {
  const sid = store.user.id
  const [app, offers, st, transfers] = await Promise.all([
    api.getMyApplication(store.round),
    api.getMyOffers(store.round),
    api.getMyStatus(store.round).catch(() => null),
    api.getMyTransfers(store.round).catch(() => [])
  ])
  store.transfers = transfers || []
  // 后端的状态是唯一口径，页面直接用，不再在前端重算
  store.studentStatus = st
  if (st) {
    store.publicity = {
      published: st.publicity === 'PUBLISHED' || st.publicity === 'ENDED',
      start: null,
      end: null,
      ended: st.publicity === 'ENDED'
    }
  }
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
    status: offerStatus(o.status),
    remark: o.remark || '',
    confirmedAt: o.confirmedAt
  }))
  const conf = offers.find((o) => o.status === 'CONFIRMED')
  if (conf) store.finalResults[sid] = conf.labId
  else delete store.finalResults[sid]
}

/** 往轮录取记录（切换补录轮后，学生仍能看到前面几轮的去向） */
export async function loadFinalHistory() {
  const sid = store.user?.id
  if (!sid) return []
  const max = Math.max(store.round, 1)
  const out = []
  for (let r = 1; r <= max; r++) {
    const offers = await api.getMyOffers(r).catch(() => [])
    const conf = offers.find((o) => o.status === 'CONFIRMED')
    if (!conf) continue
    // 公示状态按"录取所在轮"查，否则学生切到下一轮时就看不到公示中了
    const pub = await api.getPublicPublicity(r).catch(() => null)
    out.push({
      round: r,
      labId: conf.labId,
      labName: conf.labName,
      mentorName: conf.mentorName,
      publicity: pub ? pub.status : null
    })
  }
  return out
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

/** 撤销确认：退回待确认，名额让给该导师递补第一名 */
export async function releaseOffer(offerId) {
  const r = await api.releaseOffer(offerId)
  await loadStudent()
  return r
}

export async function confirmOffer(offerId) {
  const r = await api.confirmOffer(offerId)
  await loadStudent()
  return r
}

// ---------------- 跨室转组（学生端发起） ----------------

/** 已录取但不满意：申请转到别的实训室的某位导师 */
export async function applyTransfer(targetLabId, targetMentorId, reason) {
  const r = await api.submitTransfer({ round: store.round, targetLabId, targetMentorId, reason })
  await loadStudent()
  return r
}

/** 撤销还没被导师处理的申请 */
export async function cancelTransfer(id) {
  await api.cancelTransfer(id)
  await loadStudent()
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
  const [list, lists, interviewList, inbox, transferInbox] = await Promise.all([
    api.getApplicants(store.round),
    api.getTeacherLists(store.round),
    api.getInterviewList(store.round).catch(() => []),
    api.getAdjustInbox(store.round).catch(() => []),
    api.getTransferInbox(store.round).catch(() => [])
  ])
  store.applicants = list
  // 面试名单（入围者的并集）：评分页和名单页只认这批人
  store.interviewList = interviewList || []
  // 别人指定调剂给我的学生：需要我接收或拒绝
  store.adjustInbox = inbox || []
  // 学生主动申请的跨室转组：约谈后接收或拒绝
  store.transferInbox = transferInbox || []
  // 面试名单 / 两个收件箱里的学生也要建 学号↔id 映射，
  // 否则名单页和撤销录取按学号查 id 会查不到（只索引报名列表是不够的）
  indexStudents(
    [...(list || []), ...store.interviewList, ...store.adjustInbox, ...store.transferInbox].map((a) => ({
      studentId: a.studentId,
      code: a.code || a.studentCode,
      realName: a.realName || a.studentName,
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
    // 名单里可能有"没报过本室"的学生（跨室转组接收、学院统筹安置），
    // 他们不在报名列表里，这里补建映射，否则页面只能显示学生数字 id
    indexStudents(
      [...(lists.admitted || []), ...(lists.waitlist || []), ...(lists.adjustCandidates || [])]
        .map((o) => ({
          studentId: o.studentId,
          code: o.studentCode || o.code,
          realName: o.studentName || o.realName,
          className: o.className
        }))
        .filter((x) => x.code)
    )
    // 记录每条拟录取的状态，导师端要据此显示"已确认/待确认"和撤销按钮
    const admitStatus = {}
    for (const o of lists.admitted || []) {
      admitStatus[idToCode[o.studentId] || String(o.studentId)] = o.status
    }
    // 内部调剂的目标导师：保存下来，重新打开名单页时下拉框才能回显
    const adjustTargets = {}
    for (const o of lists.adjustCandidates || []) {
      adjustTargets[idToCode[o.studentId] || String(o.studentId)] = o.targetMentorId || null
    }
    store.labLists[labId] = {
      admit: pick(lists.admitted),
      waitlist: pick(lists.waitlist),
      adjust: pick(lists.adjustCandidates),
      admitStatus,
      adjustTargets
    }
  }
}

/** body: { attitude, logic, coding, time, comment } */
export async function saveScore(studentCode, body) {
  const sid = codeToId[studentCode]
  await api.saveScore(sid, { round: store.round, ...body })
  await loadTeacher()
}

export async function submitLists({ admit, waitlist, adjust, adjustTargets }) {
  const map = (arr) => arr.map((c) => codeToId[c])
  const targets = adjustTargets || {}
  await api.saveTeacherLists({
    round: store.round,
    admitted: map(admit),
    waitlist: map(waitlist),
    // 内部调剂带上目标导师（新流程：由导师指定调给谁）
    adjustList: (adjust || []).map((c) => ({
      studentId: codeToId[c],
      targetMentorId: targets[c] || null
    }))
  })
  await loadTeacher()
}

// ---------------- 内部调剂（我作为接收方） ----------------

/** 接收调剂：直接变成我的拟录取 */
export async function acceptAdjust(offerId) {
  const r = await api.acceptAdjust(offerId, store.round)
  await loadTeacher()
  return r
}

/** 拒绝调剂：退回发起人 */
export async function rejectAdjust(offerId, reason) {
  const r = await api.rejectAdjust(offerId, { reason }, store.round)
  await loadTeacher()
  return r
}

// ---------------- 跨室转组（导师端处理） ----------------

/** 约谈后接收：原去向作废，学生直接成为我的已确认录取 */
export async function acceptTransfer(id, interviewNote) {
  const r = await api.acceptTransfer(id, { interviewNote }, store.round)
  await loadTeacher()
  return r
}

/** 拒绝转组：学生保留原去向 */
export async function rejectTransfer(id, reason, interviewNote) {
  const r = await api.rejectTransfer(id, { reason, interviewNote }, store.round)
  await loadTeacher()
  return r
}

// ---------------- 管理端 ----------------

/**
 * 选中的轮次必须还在进行中才能做会改动选拔结果的操作。
 * 顶栏可以切到已结束的轮次去查看历史，但那时不该再往里写数据。
 */
function assertRoundOpen() {
  const st = store.rounds.find((r) => r.roundNo === store.round)?.status
  if (st && st !== 'OPEN') {
    const open = store.rounds.find((r) => r.status === 'OPEN')
    const hint = open ? `请先在右上角切到进行中的第 ${open.roundNo} 轮` : '当前没有进行中的轮次'
    throw new Error(`第 ${store.round} 轮${roundText(st)}，不能在这轮上操作；${hint}`)
  }
}

export async function loadAdmin() {
  const [qs, list, pool, db, results, trs] = await Promise.all([
    api.getQuotas(store.round),
    api.getStudents(),
    api.getAdjustPool(store.round),
    api.getDashboard(store.round),
    api.getResults(store.round),
    api.getAdminTransfers(store.round).catch(() => [])
  ])
  store.quotas = qs
  store.results = results || []
  store.transfers = trs || []
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
    } else {
      // 换到还没公示的轮次时必须清掉上一轮的状态，否则切轮后仍显示「公示中」
      store.publicity = { published: false, start: null, end: null, ended: false }
    }
  } catch (e) {
    store.publicity = { published: false, start: null, end: null, ended: false }
  }
}

export async function doAdjust(level, mentorId, studentCodes) {
  assertRoundOpen()
  const studentIds = studentCodes.map((c) => codeToId[c])
  const body = { round: store.round, mentorId, studentIds }
  if (level === 1) await api.adjustInternal(body)
  else await api.adjustCross(body)
  await loadAdmin()
}

export async function switchStage(stageNo, hours = 4) {
  assertRoundOpen()
  // 后端会先把本轮其他阶段全部关掉，再打开目标阶段，避免多个阶段同时开放；
  // 切到「拟录取与学生确认」时后端还会顺便结算（1 个意向自动确认，多个等学生选）
  const r = await api.switchStage({ round: store.round, stageNo, hours })
  await loadPublic()
  return r
}

export async function openNextRound() {
  const next = store.round + 1
  await api.openRound(next)
  await loadRounds()
  store.round = next
  await loadPublic()
  await loadAdmin()
}

/** 手动结算拟录取（分轮补录没有「拟录取确认」阶段，导师发完名单后由管理员点一次） */
export async function settleRound() {
  assertRoundOpen()
  const r = await api.settleOffers(store.round)
  await loadAdmin()
  return r
}

export async function publishPublicity() {
  const p = await api.publishPublicity({ round: store.round, days: 1 })
  store.publicity = { published: true, start: fmt(p.startAt), ended: false }
  return p
}

/** 公布剩余名额（后端会写一条操作日志） */
export async function publishQuotas() {
  assertRoundOpen()
  const r = await api.publishQuotas(store.round)
  await loadAdmin()
  return r
}

/** 公示状态 + 名单（公开接口，学生也能看） */
export async function loadPublicity() {
  const p = await api.getPublicPublicity(store.round)
  if (p) {
    store.publicity = { published: true, start: fmt(p.startAt), end: fmt(p.endAt), ended: p.status === 'ENDED' }
    store.publicityList = p.list || []
  } else {
    // 换到还没公示的轮次时要把上一轮的"公示中"清掉，否则页面会一直挂着旧状态
    store.publicity = { published: false, start: null, end: null, ended: false }
    store.publicityList = []
  }
  return p
}

export async function endPublicity() {
  await api.endPublicity(store.round)
  store.publicity.ended = true
}

export async function markObjection(studentCode, reason) {
  await api.addObjection({ round: store.round, studentCode, reason })
}

/** 当前公示中还没处理的异议（后端持久化在操作日志里） */
export async function loadObjections() {
  const list = await api.getObjections(store.round)
  store.objections = list || []
  return store.objections
}

export async function resolveObjections() {
  await api.resolveObjections(store.round)
  await loadObjections()
}

// ---------------- 站内通知 ----------------

/** 拉自己的通知（登录后调用；后端最多返回 50 条） */
export async function loadNotifications() {
  if (!store.user) return []
  const list = await api.getMyNotifications().catch(() => [])
  store.notifications = (list || []).map((n) => ({
    id: n.id,
    time: fmt(n.createdAt),
    title: n.title || '',
    text: n.content || '',
    read: !!n.isRead
  }))
  return store.notifications
}

/** 打开通知面板时把全部标为已读 */
export async function markNotificationsRead() {
  store.notifications.forEach((n) => { n.read = true })
  await api.markNotificationsRead().catch(() => {})
}

export { fmt }


// ---------------- 安置检查 ----------------

/** 拉取缺口清单：谁还没着落、还有多少空位 */
export async function loadPlacement() {
  store.placement = await api.getPlacementGaps(store.round)
  return store.placement
}

/** 安置校验 */
export function verifyPlacement() {
  return api.verifyPlacement(store.round)
}
/** 管理员指定安置：明确指定导师 + 原因，学生端可见 */
export async function assignPlacement(studentId, mentorId, reason) {
  const r = await api.assignPlacement({ round: store.round, studentId, mentorId, reason })
  await loadAdmin()
  await loadPlacement()
  return r
}
// ---------------- 公告 ----------------

export async function loadNotices() {
  const list = await api.getNotices()
  notices.splice(
    0,
    notices.length,
    ...list.map((n) => ({
      id: n.id,
      title: n.title,
      content: n.content,
      date: fmt(n.createdAt).slice(0, 10),
      isTop: !!n.isTop,
      publisher: n.publisherName || ''
    }))
  )
  return notices
}

// ---------------- 操作日志 ----------------

export function loadLogs(params) {
  return api.getLogs(params)
}

// ---------------- 导师撤销录取 ----------------

export async function revokeAdmit(studentCode, reason) {
  const sid = codeToId[studentCode]
  if (!sid) throw new Error('找不到该学生')
  const r = await api.revokeAdmit({ round: store.round, studentId: sid, reason })
  await loadTeacher()
  return r
}

// ---------------- 面试入围 ----------------

/** 把若干学生拉进本室面试名单 */
export async function inviteStudents(codes) {
  const ids = codes.map((c) => codeToId[c]).filter(Boolean)
  if (!ids.length) throw new Error('没有可用的学生')
  const r = await api.inviteStudents({ round: store.round, studentIds: ids })
  await loadTeacher()
  return r
}

/** 取消某个学生的入围 */
export async function uninviteStudent(code) {
  const sid = codeToId[code]
  if (!sid) throw new Error('找不到该学生')
  const r = await api.uninviteStudent(sid, store.round)
  await loadTeacher()
  return r
}

