export const LANG_STORAGE_KEY = 'gallery_lang'

export const normalizeLang = (lang) => {
  const raw = String(lang || '').trim().replace('_', '-').toLowerCase()
  if (!raw) return 'en-US'
  if (raw.startsWith('zh')) return 'zh-CN'
  if (raw.startsWith('en')) return 'en-US'
  return 'en-US'
}

export const getCurrentLang = () => {
  // Language switching is temporarily disabled for storefront.
  // Keep frontend default in English.
  return 'en-US'
}

export const setCurrentLang = (lang) => {
  const normalized = normalizeLang(lang)
  try {
    localStorage.setItem(LANG_STORAGE_KEY, normalized)
  } catch (_error) {
  }
  return normalized
}
