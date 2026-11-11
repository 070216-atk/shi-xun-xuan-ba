/**
 * 全部后端接口（与 framework/4.api/字段定义.md 一一对应）
 * 页面只 import 这里，不要自己拼 URL。
 */
import { http, setToken, clearToken } from './http'

// ---------------- 认证 ----------------

export async function login(username, password, portal = 'web') {
  const data = await http.post('/auth/login', { username, password, portal })
  setToken(data.token)
  return data // { token, expiresIn, user }
}

export function logout() {
  clearToken()
  return Promise.resolve()
}

export const me = () => http.get('/auth/me')

// ---------------- 公开信息 ----------------

export const getStages = (round = 1) => http.get(`/stages?round=${round}`)
export const getLabs = (round = 1) => http.get(`/labs?round=${round}`)
export const getLab = (id, round = 1) => http.get(`/labs/${id}?round=${round}`)
/** 公示状态 + 最终名单（公开接口，不用登录；未公示时返回 null） */
export const getPublicPublicity = (round = 1) => http.get(`/publicity?round=${round}`)

// ---------------- 学生端 ----------------

export const getMyApplication = (round = 1) => http.get(`/student/application?round=${round}`)
/** body: { round, items: [{labId, mentorId}], adjustInLab, adjustCross } */
export const saveMyApplication = (body) => http.put('/student/application', body)
export const getMyStatus = (round = 1) => http.get(`/student/status?round=${round}`)
export const getMyOffers = (round = 1) => http.get(`/student/offers?round=${round}`)
export const confirmOffer = (offerId) => http.post(`/student/offers/${offerId}/confirm`)
export const getMyResult = (round = 1) => http.get(`/student/result?round=${round}`)

// ---------------- 导师端 ----------------

export const getApplicants = (round = 1) => http.get(`/teacher/applicants?round=${round}`)
/** body: { round, attitude, logic, coding, time, comment } */
export const saveScore = (studentId, body) => http.put(`/teacher/scores/${studentId}`, body)
export const getTeacherLists = (round = 1) => http.get(`/teacher/lists?round=${round}`)
/** body: { round, admitted: [id], waitlist: [id], adjustCandidates: [id] } */
export const saveTeacherLists = (body) => http.put('/teacher/lists', body)

// ---------------- 管理端 ----------------

export const getQuotas = (round = 1) => http.get(`/admin/quotas?round=${round}`)
export const publishQuotas = (round = 1) => http.post('/admin/quotas/publish', { round })
export const getAdminStages = (round = 1) => http.get(`/admin/stages?round=${round}`)
export const updateStage = (id, body) => http.put(`/admin/stages/${id}`, body)
export const switchStage = (body) => http.post('/admin/stages/switch', body)
export const getAdjustPool = (round = 1) => http.get(`/admin/adjust/pool?round=${round}`)
export const adjustInternal = (body) => http.post('/admin/adjust/internal', body)
export const adjustCross = (body) => http.post('/admin/adjust/cross', body)
export const adjustTalk = (body) => http.post('/admin/adjust/talk', body)
export const getStudents = () => http.get('/admin/students')
export const getResults = (round = 1) => http.get(`/admin/results?round=${round}`)
export const getPublicity = (round = 1) => http.get(`/admin/publicity?round=${round}`)
export const publishPublicity = (body) => http.post('/admin/publicity/publish', body)
export const endPublicity = (round = 1) => http.post(`/admin/publicity/end?round=${round}`)
export const addObjection = (body) => http.post('/admin/publicity/objections', body)
export const resolveObjections = (round = 1) => http.post(`/admin/publicity/objections/resolve?round=${round}`)
export const openRound = (round) => http.post('/admin/rounds', { round })
export const getDashboard = (round = 1) => http.get(`/admin/dashboard?round=${round}`)
