const VISITOR_STORAGE_KEY = 'gallery_visitor_id_v1'
const SESSION_STORAGE_KEY = 'gallery_session_id_v1'
const MAX_STAY_SECONDS = 604800

const baseUrl = import.meta.env.VITE_API_BASE || ''

const randomId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

const getOrCreateStorageId = (storage, key) => {
  try {
    const current = storage.getItem(key)
    if (current) return current
    const created = randomId()
    storage.setItem(key, created)
    return created
  } catch (_error) {
    return randomId()
  }
}

const getVisitorId = () => getOrCreateStorageId(window.localStorage, VISITOR_STORAGE_KEY)
const getSessionId = () => getOrCreateStorageId(window.sessionStorage, SESSION_STORAGE_KEY)

const shouldTrackRoute = (route) => {
  if (!route || !route.path) return false
  return !String(route.path).startsWith('/admin')
}

const postEvent = (path, payload, preferBeacon = false) => {
  const url = `${baseUrl}${path}`
  const body = JSON.stringify(payload || {})

  if (preferBeacon && typeof navigator !== 'undefined' && navigator.sendBeacon) {
    const blob = new Blob([body], { type: 'application/json' })
    navigator.sendBeacon(url, blob)
    return
  }

  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    keepalive: true,
  }).catch(() => {})
}

const toRoutePath = (route) => {
  if (!route) return '/'
  if (route.path) return String(route.path)
  if (route.fullPath) return String(route.fullPath)
  return '/'
}

const buildPayload = (route, patch = {}) => {
  const screen = typeof window !== 'undefined' && window.screen ? window.screen : null
  return {
    pagePath: toRoutePath(route),
    pageTitle: typeof document !== 'undefined' ? document.title || '' : '',
    visitorId: getVisitorId(),
    sessionId: getSessionId(),
    lang: typeof navigator !== 'undefined' ? navigator.language || '' : '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
    screenWidth: screen ? screen.width : null,
    screenHeight: screen ? screen.height : null,
    occurredAt: Date.now(),
    ...patch,
  }
}

export const setupPageAnalytics = (router) => {
  if (typeof window === 'undefined' || !router) return

  let currentVisit = null

  const openVisit = (route, referrer) => {
    if (!shouldTrackRoute(route)) {
      currentVisit = null
      return
    }
    const payload = buildPayload(route, {
      referrer: referrer || (typeof document !== 'undefined' ? document.referrer || '' : ''),
    })
    postEvent('/api/analytics/page-view', payload, false)
    currentVisit = {
      route,
      startAt: Date.now(),
      closed: false,
    }
  }

  const closeVisit = (reason, preferBeacon) => {
    if (!currentVisit || currentVisit.closed) return
    const duration = Math.max(0, Math.min(MAX_STAY_SECONDS, Math.floor((Date.now() - currentVisit.startAt) / 1000)))
    currentVisit.closed = true
    postEvent('/api/analytics/page-leave', buildPayload(currentVisit.route, {
      staySeconds: duration,
      referrer: reason,
    }), preferBeacon)
  }

  router.afterEach((to, from) => {
    const toPath = to && to.path ? String(to.path) : ''
    const fromPath = from && from.path ? String(from.path) : ''
    if (toPath === fromPath) {
      return
    }
    if (currentVisit) {
      closeVisit('route-change', false)
    }
    const referrer = from && from.path ? String(from.path) : ''
    openVisit(to, referrer)
  })

  router.isReady().then(() => {
    openVisit(router.currentRoute.value, '')
  }).catch(() => {})

  window.addEventListener('pagehide', () => closeVisit('pagehide', true))
  window.addEventListener('beforeunload', () => closeVisit('beforeunload', true))
}
