/**
 * 后端请求封装（不引入 axios，原生 fetch 足够）
 *
 * 约定：
 *   · 所有接口以 /api 开头，开发环境由 Vite 代理到 http://localhost:8080
 *   · 后端统一返回 { code, message, data }，code === 0 才算成功
 *   · token 存 localStorage，每次请求自动带上 Authorization
 */

const TOKEN_KEY = 'lab-selection-token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * @param {'GET'|'POST'|'PUT'|'DELETE'} method
 * @param {string} url  形如 '/labs' 或 '/student/offers/3/confirm'
 * @param {object} [body]
 */
export async function request(method, url, body) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token

  let res
  try {
    res = await fetch('/api' + url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch (e) {
    // 后端没启动、或代理没配好
    const err = new Error('连不上后端：请先双击「启动后端.cmd」，确认 8080 已在运行')
    err.code = 'NETWORK'
    throw err
  }

  let json
  try {
    json = await res.json()
  } catch (e) {
    throw new Error('后端返回的不是 JSON（HTTP ' + res.status + '）')
  }

  if (json.code !== 0) {
    const err = new Error(json.message || '请求失败')
    err.code = json.code
    if (json.code === 1001) clearToken() // 登录失效，让上层跳登录页
    throw err
  }
  return json.data
}

export const http = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body ?? {}),
  put: (url, body) => request('PUT', url, body),
  del: (url) => request('DELETE', url)
}
