const defaultTitle = 'Product Gallery'
const defaultTitleTemplates = {
  home: '{{shopName}}',
  product: '{{productTitle}} - {{shopName}}',
  voucher: 'Order Voucher {{voucherNo}} - {{shopName}}',
  admin: '商品相册管理后台',
  'admin-login': '后台登录 - {{shopName}}',
  page: '{{pageTitle}} - {{shopName}}',
}

const routeMetaKey = (route) => {
  const name = route && route.name ? String(route.name) : ''
  if (name === 'gallery') return 'home'
  if (name === 'product-detail') return 'product'
  if (name === 'voucher-detail') return 'voucher'
  if (name === 'admin') return 'admin'
  if (name === 'admin-login') return 'admin-login'
  if (name === 'custom-page') return `page:${String(route.params?.key || '').trim().toLowerCase()}`
  return name || 'home'
}

const normalizeLang = (lang) => String(lang || 'en-us').trim().replace('_', '-').toLowerCase()

const renderTemplate = (value, data = {}) => String(value || '').replace(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g, (_match, key) => {
  const replacement = data[key]
  return replacement == null ? '' : String(replacement)
})

const findMeta = (metas, key, lang) => {
  const rows = Array.isArray(metas) ? metas : []
  const normalizedKey = String(key || '').trim().toLowerCase()
  const normalizedLang = normalizeLang(lang)
  return rows.find((item) => String(item.pageKey || '').toLowerCase() === normalizedKey && normalizeLang(item.langCode) === normalizedLang)
    || rows.find((item) => String(item.pageKey || '').toLowerCase() === normalizedKey && normalizeLang(item.langCode).split('-')[0] === normalizedLang.split('-')[0])
    || rows.find((item) => String(item.pageKey || '').toLowerCase() === normalizedKey && normalizeLang(item.langCode) === 'default')
    || null
}

const defaultTitleTemplate = (key) => {
  const normalizedKey = String(key || '').trim().toLowerCase()
  if (normalizedKey.startsWith('page:')) return defaultTitleTemplates.page
  return defaultTitleTemplates[normalizedKey] || defaultTitleTemplates.home
}

const setDescription = (description) => {
  if (typeof document === 'undefined') return
  const content = String(description || '').trim()
  let tag = document.querySelector('meta[name="description"]')
  if (!tag) {
    tag = document.createElement('meta')
    tag.setAttribute('name', 'description')
    document.head.appendChild(tag)
  }
  tag.setAttribute('content', content)
}

const setRobots = (blockSearchEngineCrawl) => {
  if (typeof document === 'undefined') return
  let tag = document.querySelector('meta[name="robots"]')
  if (!tag) {
    tag = document.createElement('meta')
    tag.setAttribute('name', 'robots')
    document.head.appendChild(tag)
  }
  const blocked = Number(blockSearchEngineCrawl || 0) === 1
  tag.setAttribute('content', blocked ? 'noindex, nofollow, noarchive, nosnippet' : 'index, follow')
}

export const applyPageMeta = ({ route, profile, lang, data }) => {
  if (typeof document === 'undefined') return
  const key = routeMetaKey(route)
  const shopName = profile && profile.shopName ? profile.shopName : defaultTitle
  const meta = findMeta(profile && profile.pageMetas, key, lang)
  const templateData = {
    shopName,
    pageTitle: data && data.pageTitle ? data.pageTitle : '',
    productTitle: data && data.productTitle ? data.productTitle : '',
    voucherNo: data && data.voucherNo ? data.voucherNo : '',
  }
  const fallbackTitle = renderTemplate(defaultTitleTemplate(key), templateData).trim()
    || templateData.productTitle
    || templateData.pageTitle
    || shopName
    || defaultTitle
  const title = meta && meta.title ? renderTemplate(meta.title, templateData).trim() : fallbackTitle
  const description = meta && meta.description ? renderTemplate(meta.description, templateData).trim() : (profile && profile.announcement ? profile.announcement : '')
  document.title = title || defaultTitle
  setDescription(description)
  setRobots(profile && profile.blockSearchEngineCrawl)
}
