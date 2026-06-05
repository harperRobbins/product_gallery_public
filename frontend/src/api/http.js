import axios from 'axios'
import { clearAdminAuth, getAdminToken } from '../utils/auth'
import { getCurrentLang } from '../i18n/lang'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = getAdminToken()
  if (token && config.url && config.url.startsWith('/api/admin')) {
    config.headers = config.headers || {}
    config.headers.Authorization = 'Bearer ' + token
  }
  if (config.url && config.url.startsWith('/api') && !config.url.startsWith('/api/admin')) {
    const lang = getCurrentLang()
    const params = config.params || {}
    if (!params.lang) {
      config.params = { ...params, lang }
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code === 0) {
        return payload.data
      }
      const error = new Error(payload.message || '请求失败')
      error.code = payload.code
      return Promise.reject(error)
    }
    return payload
  },
  (error) => {
    const status = error && error.response ? error.response.status : null
    const code = error ? error.code : null
    if (status === 401 || code === 401) {
      clearAdminAuth()
      if (window.location.pathname.startsWith('/admin') && window.location.pathname !== '/admin/login') {
        window.location.href = '/admin/login'
      }
    }
    return Promise.reject(error)
  },
)

export default http
