<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Search, Operation, Filter, Close, ArrowUp, ArrowRight } from '@element-plus/icons-vue'
import { api } from '../api/gallery'
import ProductCard from '../components/ProductCard.vue'
import { setCurrentLang } from '../i18n/lang'
import { COMMON_CURRENCIES, getCurrentCurrency, normalizeCurrency, setCurrentCurrency } from '../utils/currency'
import { applyPageMeta } from '../utils/pageMeta'

const router = useRouter()
const route = useRoute()
const { t, locale } = useI18n()

const products = ref([])
const categories = ref([])
const loading = ref(false)
const booting = ref(true)
const searchText = ref('')
const displayMode = ref('multi')
const activeTab = ref('all')
const activeTopId = ref(null)
const activeMenuId = ref('__all__')
const menuCategoryId = ref(null)
const menuTag = ref('')

const shopProfile = reactive({
  shopName: '',
  shopLogo: '',
  heroBanner: '',
  announcement: '',
  domain: '',
  languageLabel: 'EN',
  themeColor: '#10b981',
  contactName: '',
  contactWechat: '',
  contactPhone: '',
  contacts: [],
  copyrightText: '',
  menuItems: [],
  menuPageConfigs: [],
  customPages: [],
  pageMetas: [],
})

const viewSheetVisible = ref(false)
const filterSheetVisible = ref(false)
const menuDialogVisible = ref(false)
const contactDialogVisible = ref(false)
const activeMenuL1Id = ref('')
const activeMenuL2Id = ref('')
const selectedQuick = ref('')
const imagePreviewVisible = ref(false)
const imagePreviewUrls = ref([])
const imagePreviewIndex = ref(0)
const preloadedImageUrls = new Set()
const videoPreviewVisible = ref(false)
const videoPreviewUrl = ref('')
const videoPreviewTitle = ref('')
const menuPreviewMap = reactive({})
const routeReady = ref(false)
const showBackToTop = ref(false)
const currentTimeMs = ref(Date.now())
let countdownTimer = null
const managedQueryKeys = ['keyword', 'categoryId', 'menuId', 'menuCategoryId', 'menuTag', 'startDate', 'endDate', 'share', 'tab', 'view']
const validTabs = new Set(['all', 'new', 'video', 'photos'])
const validViews = new Set(['small', 'multi'])
const validShares = new Set(['all', 'never', 'shared'])
const galleryStateStorageKey = 'gallery_view_state_v2'

const tabs = computed(() => [
  { key: 'all', label: t('gallery.tabs.all') },
  { key: 'new', label: t('gallery.tabs.new') },
  { key: 'video', label: t('gallery.tabs.video') },
  { key: 'photos', label: t('gallery.tabs.photos') },
])

const viewModes = computed(() => [
  { key: 'small', label: t('gallery.viewModes.small') },
  { key: 'multi', label: t('gallery.viewModes.multi') },
])

const quickDateOptions = computed(() => [
  { key: 'today', label: t('gallery.quickDate.today') },
  { key: 'yesterday', label: t('gallery.quickDate.yesterday') },
  { key: 'thisMonth', label: t('gallery.quickDate.thisMonth') },
  { key: 'lastMonth', label: t('gallery.quickDate.lastMonth') },
  { key: 'thisYear', label: t('gallery.quickDate.thisYear') },
  { key: 'lastYear', label: t('gallery.quickDate.lastYear') },
])

const currencyOptions = COMMON_CURRENCIES
const currencyRates = ref({})
const currencySource = ref('CNY')
const activeCurrency = ref(getCurrentCurrency('en-US'))
const currencyLabelMap = currencyOptions.reduce((acc, row) => {
  acc[row.code] = row.label
  return acc
}, {})
const activeCurrencyLabel = computed(() => currencyLabelMap[activeCurrency.value] || activeCurrency.value)

const normalizeContactType = (raw) => {
  const value = String(raw || '').trim().toUpperCase()
  if (['PERSON', 'WECHAT', 'WHATSAPP', 'PHONE', 'EMAIL', 'CUSTOM'].includes(value)) return value
  return 'CUSTOM'
}

const defaultContactLabel = (type) => {
  const value = normalizeContactType(type)
  if (value === 'PERSON') return t('gallery.contactFields.person')
  if (value === 'WECHAT') return t('gallery.contactFields.wechat')
  if (value === 'WHATSAPP') return t('gallery.contactFields.whatsapp')
  if (value === 'PHONE') return t('gallery.contactFields.phone')
  if (value === 'EMAIL') return t('gallery.contactFields.email')
  return t('gallery.contactFields.custom')
}

const contactItems = computed(() => {
  const rows = []
  const append = (row, idx) => {
    const type = normalizeContactType(row && row.type)
    const label = String((row && row.label) || '').trim() || defaultContactLabel(type)
    const value = String((row && row.value) || '').trim()
    const copyValue = String((row && row.copyValue) || '').trim() || value
    if (!value) return
    rows.push({
      id: String((row && row.id) || `contact-${idx}`),
      type,
      label,
      value,
      copyValue,
      sort: row && row.sort != null ? Number(row.sort) : idx + 1,
      enabled: row && row.enabled === 0 ? 0 : 1,
    })
  }

  const explicit = Array.isArray(shopProfile.contacts) ? shopProfile.contacts : []
  explicit.forEach((item, idx) => append(item, idx))
  if (rows.length) {
    return rows
      .filter((item) => item.enabled !== 0)
      .sort((a, b) => Number(a.sort || 0) - Number(b.sort || 0))
  }

  if (shopProfile.contactName) {
    append({ id: 'legacy-person', type: 'PERSON', value: shopProfile.contactName, label: defaultContactLabel('PERSON') }, 0)
  }
  if (shopProfile.contactWechat) {
    append({ id: 'legacy-wechat', type: 'WECHAT', value: shopProfile.contactWechat, label: defaultContactLabel('WECHAT') }, 1)
  }
  if (shopProfile.contactPhone) {
    append({ id: 'legacy-phone', type: 'PHONE', value: shopProfile.contactPhone, label: defaultContactLabel('PHONE') }, 2)
  }
  return rows
    .filter((item) => item.enabled !== 0)
    .sort((a, b) => Number(a.sort || 0) - Number(b.sort || 0))
})

const filters = reactive({
  categoryId: null,
  startDate: '',
  endDate: '',
  share: 'all',
})

const draftFilters = reactive({
  categoryId: null,
  startDate: '',
  endDate: '',
  share: 'all',
})

const pager = reactive({
  page: 1,
  size: 12,
  total: 0,
})

const summary = reactive({
  newCount: 0,
  totalCount: 0,
})

const topCategories = computed(() => categories.value || [])
const menuKeyOf = (item) => String((item && (item.id || item.name)) || '')

const displayMenus = computed(() => {
  const list = Array.isArray(shopProfile.menuItems) ? shopProfile.menuItems : []
  return list
    .filter((item) => item && item.enabled !== 0)
    .map((item, index) => ({
      ...item,
      id: menuKeyOf(item) || `menu-${index}`,
      parentId: item && item.parentId ? String(item.parentId) : '',
      name: item && item.name ? item.name : `${t('gallery.defaultMenuName')}${index + 1}`,
    }))
    .sort((a, b) => Number(a.sort || 0) - Number(b.sort || 0))
})

const menuTree = computed(() => {
  const list = displayMenus.value.map((item) => ({ ...item, children: [] }))
  const map = new Map()
  list.forEach((item) => map.set(String(item.id), item))

  const roots = []
  list.forEach((item) => {
    const parentId = String(item.parentId || '')
    if (!parentId || !map.has(parentId) || parentId === String(item.id)) {
      roots.push(item)
      return
    }
    map.get(parentId).children.push(item)
  })

  const sortNodes = (nodes) => {
    nodes.sort((a, b) => Number(a.sort || 0) - Number(b.sort || 0))
    nodes.forEach((node) => sortNodes(node.children || []))
  }
  sortNodes(roots)
  return roots
})

function findMenuNodeById(id) {
  const target = String(id || '')
  if (!target) return null
  const queue = [...menuTree.value]
  while (queue.length) {
    const node = queue.shift()
    if (!node) continue
    if (String(node.id) === target) return node
    if (Array.isArray(node.children) && node.children.length) {
      queue.push(...node.children)
    }
  }
  return null
}

function findMenuPathById(id, nodes = menuTree.value, path = []) {
  const target = String(id || '')
  if (!target || !Array.isArray(nodes)) return []
  for (const node of nodes) {
    const nextPath = [...path, node]
    if (String(node.id) === target) return nextPath
    if (Array.isArray(node.children) && node.children.length) {
      const result = findMenuPathById(target, node.children, nextPath)
      if (result.length) return result
    }
  }
  return []
}

const level1Menus = computed(() => menuTree.value || [])

const level2Menus = computed(() => {
  if (!activeMenuL1Id.value) return []
  const selected = findMenuNodeById(activeMenuL1Id.value)
  if (!selected) return []
  if (Array.isArray(selected.children) && selected.children.length) {
    const viewAll = {
      ...selected,
      id: `${selected.id}__view_all`,
      children: [],
      _viewAll: true,
      name: t('gallery.menu.viewAll'),
    }
    return [viewAll, ...selected.children]
  }
  return [selected]
})

const level3Menus = computed(() => {
  if (!activeMenuL2Id.value) return []
  const selected = findMenuNodeById(activeMenuL2Id.value)
  if (!selected) return []
  if (Array.isArray(selected.children) && selected.children.length) return selected.children
  return []
})

const drawerLevel1Node = computed(() => {
  if (!activeMenuL1Id.value) return null
  return findMenuNodeById(activeMenuL1Id.value)
})

const drawerLevel2Items = computed(() => {
  const current = drawerLevel1Node.value
  if (!current || !Array.isArray(current.children)) return []
  return current.children
})

const drawerActiveLevel2Node = computed(() => {
  const currentL1 = drawerLevel1Node.value
  if (!currentL1 || !currentL1.id) return null
  const active = activeMenuNode.value
  const parent = activeMenuParentNode.value

  if (active && String(active.parentId || '') === String(currentL1.id)) {
    return active
  }
  if (parent && String(parent.parentId || '') === String(currentL1.id)) {
    return parent
  }
  const selected = findMenuNodeById(activeMenuL2Id.value)
  if (selected && String(selected.parentId || '') === String(currentL1.id)) {
    return selected
  }
  return drawerLevel2Items.value[0] || null
})

const drawerLevel3Items = computed(() => {
  const current = drawerActiveLevel2Node.value
  if (!current || !Array.isArray(current.children)) return []
  return current.children
})

const activeMenuLabel = computed(() => {
  if (activeMenuId.value === '__all__') return t('common.all')
  const current = displayMenus.value.find((item) => String(item.id) === String(activeMenuId.value))
  return current && current.name ? current.name : t('gallery.defaultMenuName')
})

const activeMenuPageConfig = computed(() => {
  if (activeMenuId.value === '__all__') return null
  const list = Array.isArray(shopProfile.menuPageConfigs) ? shopProfile.menuPageConfigs : []
  const match = list.find((item) => item && item.enabled !== 0 && String(item.menuId || '') === String(activeMenuId.value))
  return match || null
})

const activeMenuNode = computed(() => {
  if (activeMenuId.value === '__all__') return null
  return findMenuNodeById(activeMenuId.value)
})

const activeMenuParentNode = computed(() => {
  const current = activeMenuNode.value
  if (!current || !current.parentId) return null
  return findMenuNodeById(current.parentId)
})

const activeMenuFamilyItems = computed(() => {
  const current = activeMenuNode.value
  if (!current) return []

  if (Array.isArray(current.children) && current.children.length) {
    return [current, ...current.children]
  }

  const parent = activeMenuParentNode.value
  if (parent && Array.isArray(parent.children) && parent.children.length) {
    return [parent, ...parent.children]
  }
  return []
})

const visibleMenuPageBlocks = computed(() => {
  const config = activeMenuPageConfig.value
  const blocks = Array.isArray(config && config.blocks) ? config.blocks : []
  return blocks
    .filter((block) => block && block.enabled !== 0 && isMenuPageBlockVisible(block))
    .sort((a, b) => Number(a?.sort || 0) - Number(b?.sort || 0))
})

const hasActiveCategoryFilter = computed(() => {
  if (filters.categoryId) return true
  if (menuCategoryId.value) return true
  if (menuTag.value) return true
  return activeMenuId.value !== '__all__'
})

const menuTypeLabel = (item) => {
  if (!item) return ''
  if (item.targetType === 'GROUP') return t('gallery.menuTypes.group')
  if (item.targetType === 'CUSTOM_PAGE') return t('gallery.menuTypes.page')
  if (item.targetType === 'CATEGORY_TAG') return t('gallery.menuTypes.categoryTag')
  if (item.targetType === 'CATEGORY') return t('gallery.menuTypes.category')
  if (item.targetType === 'TAG') return t('gallery.menuTypes.tag')
  return ''
}

const parseMenuPageDateTime = (value) => {
  const raw = String(value || '').trim()
  if (!raw) return null
  const normalized = raw.includes('T') ? raw : raw.replace(' ', 'T')
  const date = new Date(normalized)
  if (!Number.isFinite(date.getTime())) return null
  return date
}

const isMenuPageBlockVisible = (block) => {
  if (!block) return false
  const start = parseMenuPageDateTime(block.startTime)
  const end = parseMenuPageDateTime(block.endTime)
  const now = currentTimeMs.value
  if (start && now < start.getTime()) return false
  if (end && now > end.getTime()) return false
  return true
}

const countdownText = (block) => {
  const end = parseMenuPageDateTime(block && block.endTime)
  if (!end) return ''
  const diff = end.getTime() - currentTimeMs.value
  if (diff <= 0) return t('gallery.menuPage.ended')
  const totalSeconds = Math.floor(diff / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (days > 0) return `${days}d ${hours}h ${minutes}m`
  if (hours > 0) return `${hours}h ${minutes}m ${seconds}s`
  return `${minutes}m ${seconds}s`
}

const isActiveMenuFamilyItem = (item) => {
  if (!item) return false
  return String(item.id || '') === String(activeMenuId.value || '')
}

const isDrawerActiveLevel2Item = (item) => {
  if (!item) return false
  return String(item.id || '') === String(drawerActiveLevel2Node.value?.id || '')
}

const openInlineMenuItem = (item) => {
  if (!item) return
  applyMenuItem(item)
}

const menuFallbackLabel = (item) => {
  const name = String(item?.name || '').trim()
  if (!name) return t('gallery.menu.defaultIcon')
  return name.slice(0, 1).toUpperCase()
}

const openMenuPageBlockLink = (block) => {
  if (!block) return
  const linkType = String(block.linkType || 'NONE').trim().toUpperCase()
  const linkValue = String(block.linkValue || '').trim()
  if (!linkValue || linkType === 'NONE') return
  if (linkType === 'INTERNAL') {
    router.push(linkValue)
    return
  }
  window.open(linkValue, '_blank', 'noopener')
}

const getMenuFilterPayload = (item) => {
  if (!item) return {}
  if (item.targetType === 'CATEGORY') {
    return { categoryId: item.categoryId || undefined }
  }
  if (item.targetType === 'TAG') {
    return { tag: item.tagName || undefined }
  }
  if (item.targetType === 'CATEGORY_TAG') {
    return { categoryId: item.categoryId || undefined, tag: item.tagName || undefined }
  }
  return {}
}

const loadMenuPreview = async (items) => {
  const list = Array.isArray(items) ? items : []
  const tasks = []
  for (const item of list) {
    if (!item || !item.id) continue
    if (item.targetType === 'GROUP' || item.targetType === 'CUSTOM_PAGE') continue
    if (menuPreviewMap[item.id] !== undefined) continue
    const payload = getMenuFilterPayload(item)
    if (!payload.categoryId && !payload.tag) {
      menuPreviewMap[item.id] = ''
      continue
    }
    tasks.push(
      api.publicProducts({ page: 1, size: 1, ...payload })
        .then((data) => {
          const first = data && data.records && data.records.length ? data.records[0] : null
          menuPreviewMap[item.id] = first && first.coverImage ? first.coverImage : ''
        })
        .catch(() => {
          menuPreviewMap[item.id] = ''
        }),
    )
  }
  if (tasks.length) {
    await Promise.all(tasks)
  }
}

const findTopByCategoryId = (categoryId) => {
  if (!categoryId) return null
  for (const top of topCategories.value) {
    if (String(top.id) === String(categoryId)) return top
    if ((top.children || []).some((item) => String(item.id) === String(categoryId))) return top
  }
  return null
}

const selectedTopCategory = computed(() => {
  if (activeTopId.value) {
    return topCategories.value.find((item) => String(item.id) === String(activeTopId.value)) || null
  }
  return findTopByCategoryId(filters.categoryId)
})

const secondLevelCategories = computed(() => {
  if (!selectedTopCategory.value) return []
  return selectedTopCategory.value.children || []
})

const heroCover = computed(() => {
  if (shopProfile.heroBanner) return shopProfile.heroBanner
  if (products.value.length > 0 && products.value[0].coverImage) return products.value[0].coverImage
  return ''
})

const shopTitle = computed(() => {
  if (shopProfile.shopName) return shopProfile.shopName
  const first = products.value[0]
  if (first && first.tags && first.tags.length) {
    return '白鲸•' + first.tags[0]
  }
  return 'SZWego Gallery'
})

const shopLogo = computed(() => {
  if (shopProfile.shopLogo) return shopProfile.shopLogo
  return heroCover.value || 'https://static.szwego.com/etc/miniapp/add_cart_default_cover.png'
})

const hasMore = computed(() => products.value.length < pager.total)
const showTopTitle = computed(() => filteredProducts.value.some((item) => Number(item && item.isTop ? item.isTop : 0) === 1))

const announcement = computed(() => {
  if (shopProfile.announcement && shopProfile.announcement.trim()) return shopProfile.announcement.trim()
  return ''
})

const summaryText = computed(() => t('gallery.newTotal', {
  newCount: summary.newCount,
  totalCount: summary.totalCount,
}))

const parseDate = (dateStr, endOfDay = false) => {
  if (!dateStr) return null
  if (endOfDay) return new Date(dateStr + 'T23:59:59')
  return new Date(dateStr + 'T00:00:00')
}

const normalizeEpochMs = (value) => {
  const ts = Number(value || 0)
  if (!Number.isFinite(ts) || ts <= 0) return 0
  if (ts < 1000000000000) return Math.floor(ts * 1000)
  return Math.floor(ts)
}

const resolveProductTimeMs = (item) => {
  if (!item) return 0
  const sourceTs = normalizeEpochMs(item.sourceTimestamp)
  if (sourceTs > 0) return sourceTs
  const fallbackTs = normalizeEpochMs(item.timeStamp)
  if (fallbackTs > 0) return fallbackTs
  if (item.updateTime) {
    const updateMs = new Date(item.updateTime).getTime()
    if (Number.isFinite(updateMs) && updateMs > 0) return updateMs
  }
  return 0
}

const compareProducts = (a, b) => {
  const aTop = Number(a && a.isTop ? a.isTop : 0) === 1 ? 1 : 0
  const bTop = Number(b && b.isTop ? b.isTop : 0) === 1 ? 1 : 0
  if (aTop !== bTop) {
    return bTop - aTop
  }
  const aTs = resolveProductTimeMs(a)
  const bTs = resolveProductTimeMs(b)
  if (aTs !== bTs) {
    return bTs - aTs
  }
  const aId = String(a && a.id ? a.id : '')
  const bId = String(b && b.id ? b.id : '')
  if (!aId && !bId) return 0
  if (!aId) return 1
  if (!bId) return -1
  if (/^\d+$/.test(aId) && /^\d+$/.test(bId)) {
    if (aId.length !== bId.length) return bId.length - aId.length
    return bId.localeCompare(aId)
  }
  return bId.localeCompare(aId)
}

const applyClientFilters = (sourceList) => {
  let list = Array.isArray(sourceList) ? [...sourceList] : []

  if (filters.startDate) {
    const start = parseDate(filters.startDate)
    const startMs = start ? start.getTime() : 0
    list = list.filter((item) => {
      const ts = resolveProductTimeMs(item)
      return ts > 0 && ts >= startMs
    })
  }

  if (filters.endDate) {
    const end = parseDate(filters.endDate, true)
    const endMs = end ? end.getTime() : 0
    list = list.filter((item) => {
      const ts = resolveProductTimeMs(item)
      return ts > 0 && ts <= endMs
    })
  }

  if (activeTab.value === 'video') {
    list = list.filter((item) => Boolean(item.videoUrl))
  } else if (activeTab.value === 'photos') {
    list = list.filter((item) => !item.videoUrl)
  } else if (activeTab.value === 'new') {
    const now = Date.now()
    const sevenDays = 7 * 24 * 60 * 60 * 1000
    list = list.filter((item) => {
      const ts = resolveProductTimeMs(item)
      return ts > 0 && now - ts <= sevenDays
    })
  }

  return list.sort(compareProducts)
}

const filteredProducts = computed(() => applyClientFilters(products.value))

const resolveProductDate = (item) => {
  const sourceTs = resolveProductTimeMs(item)
  if (!sourceTs) {
    return null
  }
  const date = new Date(sourceTs)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const key = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  const label = locale.value === 'zh-CN'
    ? `${day} ${month}月`
    : `${date.toLocaleString('en-US', { month: 'short' })} ${day}`
  return { key, label }
}

const multiModeRows = computed(() => {
  const rows = []
  let currentKey = ''
  ;(filteredProducts.value || []).forEach((item) => {
    const dateInfo = resolveProductDate(item)
    if (dateInfo && dateInfo.key !== currentKey) {
      currentKey = dateInfo.key
      rows.push({
        type: 'date',
        key: `date-${currentKey}`,
        label: dateInfo.label,
      })
    }
    rows.push({
      type: 'item',
      key: `item-${item.id}`,
      item,
    })
  })
  return rows
})

const formatDate = (date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return y + '-' + m + '-' + d
}

const getQuickRange = (key) => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()

  if (key === 'today') {
    const day = formatDate(now)
    return { startDate: day, endDate: day }
  }

  if (key === 'yesterday') {
    const y = new Date(now)
    y.setDate(now.getDate() - 1)
    const day = formatDate(y)
    return { startDate: day, endDate: day }
  }

  if (key === 'thisMonth') {
    return {
      startDate: formatDate(new Date(year, month, 1)),
      endDate: formatDate(now),
    }
  }

  if (key === 'lastMonth') {
    const start = new Date(year, month - 1, 1)
    const end = new Date(year, month, 0)
    return { startDate: formatDate(start), endDate: formatDate(end) }
  }

  if (key === 'thisYear') {
    return {
      startDate: formatDate(new Date(year, 0, 1)),
      endDate: formatDate(now),
    }
  }

  const start = new Date(year - 1, 0, 1)
  const end = new Date(year - 1, 11, 31)
  return { startDate: formatDate(start), endDate: formatDate(end) }
}

const pickQuickDate = (key) => {
  selectedQuick.value = key
  const range = getQuickRange(key)
  draftFilters.startDate = range.startDate
  draftFilters.endDate = range.endDate
}

const fetchCategories = async () => {
  try {
    categories.value = await api.categoriesTree()
  } catch (error) {
    ElMessage.error(error.message || t('gallery.errors.categoryLoad'))
  }
}

const fetchShopProfile = async () => {
  try {
    const data = await api.shopProfile()
    Object.assign(shopProfile, data || {})
    shopProfile.menuItems = Array.isArray(shopProfile.menuItems)
      ? shopProfile.menuItems.map((item, idx) => ({
        ...item,
        id: item && item.id ? String(item.id) : `menu-${idx}`,
        parentId: item && item.parentId ? String(item.parentId) : '',
      }))
      : []
    shopProfile.menuPageConfigs = Array.isArray(shopProfile.menuPageConfigs)
      ? shopProfile.menuPageConfigs.map((config, idx) => ({
        ...config,
        menuId: config && config.menuId ? String(config.menuId) : '',
        enabled: config && config.enabled === 0 ? 0 : 1,
        blocks: Array.isArray(config && config.blocks)
          ? config.blocks.map((block, blockIdx) => ({
            ...block,
            id: block && block.id ? String(block.id) : `menu-block-${idx}-${blockIdx}`,
            type: block && block.type ? String(block.type).toUpperCase() : 'IMAGE',
            linkType: block && block.linkType ? String(block.linkType).toUpperCase() : 'NONE',
            sort: block && block.sort != null ? Number(block.sort) : blockIdx + 1,
            enabled: block && block.enabled === 0 ? 0 : 1,
          }))
          : [],
      }))
      : []
    shopProfile.customPages = Array.isArray(shopProfile.customPages) ? shopProfile.customPages : []
    shopProfile.contacts = Array.isArray(shopProfile.contacts) ? shopProfile.contacts : []
    shopProfile.pageMetas = Array.isArray(shopProfile.pageMetas) ? shopProfile.pageMetas : []
    applyPageMeta({ route, profile: shopProfile, lang: locale.value })
  } catch (error) {
    console.warn('店铺配置加载失败', error)
  }
}

const buildProductsQuery = (targetPage) => {
  const keyword = searchText.value.trim()
  const useMenuFilter = activeMenuId.value !== '__all__'
  const effectiveCategoryId = useMenuFilter
    ? (menuCategoryId.value || undefined)
    : (filters.categoryId || undefined)
  const effectiveTag = useMenuFilter ? (menuTag.value || undefined) : undefined
  return {
    page: targetPage,
    size: pager.size,
    keyword: keyword || undefined,
    categoryId: effectiveCategoryId,
    tag: effectiveTag,
  }
}

const fetchProducts = async (reset = false) => {
  loading.value = true
  try {
    const targetPage = reset ? 1 : pager.page + 1
    const query = buildProductsQuery(targetPage)
    const data = await api.publicProducts(query)
    const records = Array.isArray(data && data.records) ? data.records : []
    pager.total = Number(data && data.total ? data.total : 0)
    if (reset) {
      products.value = records
      pager.page = 1
    } else {
      products.value = products.value.concat(records)
      pager.page = targetPage
    }
  } catch (error) {
    ElMessage.error(error.message || t('gallery.errors.productLoad'))
  } finally {
    loading.value = false
  }
}

const fetchSummary = async () => {
  try {
    const data = await api.publicProductSummary()
    summary.newCount = Number(data && data.newCount ? data.newCount : 0)
    summary.totalCount = Number(data && data.totalCount ? data.totalCount : 0)
  } catch (error) {
    console.warn('统计加载失败', error)
  }
}

const fetchCurrencyRates = async () => {
  try {
    const data = await api.currencyRates()
    const rates = data && data.rates ? data.rates : {}
    currencyRates.value = rates
    currencySource.value = normalizeCurrency(data && data.baseCurrency ? data.baseCurrency : 'CNY')
    const supported = Array.isArray(data && data.supportedCurrencies) ? data.supportedCurrencies.map((code) => normalizeCurrency(code)) : []
    if (supported.length && !supported.includes(activeCurrency.value)) {
      activeCurrency.value = setCurrentCurrency(supported[0])
    }
  } catch (error) {
    console.warn('汇率加载失败，使用本地兜底汇率', error)
  }
}

const readGalleryStateCache = () => {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.sessionStorage.getItem(galleryStateStorageKey)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data || typeof data !== 'object') return null
    return data
  } catch (error) {
    return null
  }
}

const writeGalleryStateCache = () => {
  if (typeof window === 'undefined') return
  try {
    const payload = {
      fullPath: route.fullPath,
      savedAt: Date.now(),
      scrollY: window.scrollY || 0,
      products: products.value,
      pager: {
        page: pager.page,
        size: pager.size,
        total: pager.total,
      },
    }
    window.sessionStorage.setItem(galleryStateStorageKey, JSON.stringify(payload))
  } catch (error) {
    // ignore cache write failures
  }
}

const restoreGalleryStateCache = () => {
  const cache = readGalleryStateCache()
  if (!cache) return { restored: false, scrollY: 0 }
  if (cache.fullPath !== route.fullPath) return { restored: false, scrollY: 0 }
  if (!Array.isArray(cache.products) || !cache.products.length) return { restored: false, scrollY: 0 }
  products.value = cache.products
  pager.page = Number(cache.pager && cache.pager.page ? cache.pager.page : 1)
  pager.size = Number(cache.pager && cache.pager.size ? cache.pager.size : pager.size)
  pager.total = Number(cache.pager && cache.pager.total ? cache.pager.total : 0)
  return {
    restored: true,
    scrollY: Number(cache.scrollY || 0),
  }
}

const firstQueryValue = (value) => (Array.isArray(value) ? value[0] : value)

const readQueryString = (value) => {
  const first = firstQueryValue(value)
  if (first === undefined || first === null) return ''
  return String(first).trim()
}

const applyStateFromRouteQuery = (query) => {
  const keyword = readQueryString(query.keyword)
  const tab = readQueryString(query.tab)
  const view = readQueryString(query.view)
  const share = readQueryString(query.share)
  const categoryId = readQueryString(query.categoryId)
  const menuId = readQueryString(query.menuId)
  const menuCategory = readQueryString(query.menuCategoryId)
  const menuTagText = readQueryString(query.menuTag)

  searchText.value = keyword
  activeTab.value = validTabs.has(tab) ? tab : 'all'
  displayMode.value = validViews.has(view) ? view : 'multi'
  filters.categoryId = categoryId || null
  filters.startDate = readQueryString(query.startDate)
  filters.endDate = readQueryString(query.endDate)
  filters.share = validShares.has(share) ? share : 'all'

  if (menuId && menuId !== '__all__') {
    activeMenuId.value = menuId
    menuCategoryId.value = menuCategory || null
    menuTag.value = menuTagText
  } else {
    activeMenuId.value = '__all__'
    menuCategoryId.value = null
    menuTag.value = ''
  }
  activeTopId.value = (findTopByCategoryId(filters.categoryId) || {}).id || null
}

const buildRouteQueryFromState = () => {
  const query = {}
  const keyword = searchText.value.trim()
  const menuId = String(activeMenuId.value || '').trim()
  const categoryId = filters.categoryId ? String(filters.categoryId).trim() : ''
  const menuCategory = menuCategoryId.value ? String(menuCategoryId.value).trim() : ''
  const menuTagText = String(menuTag.value || '').trim()

  if (keyword) query.keyword = keyword
  if (categoryId) query.categoryId = categoryId
  if (filters.startDate) query.startDate = filters.startDate
  if (filters.endDate) query.endDate = filters.endDate
  if (filters.share && filters.share !== 'all') query.share = filters.share
  if (activeTab.value !== 'all') query.tab = activeTab.value
  if (displayMode.value !== 'multi') query.view = displayMode.value

  if (menuId && menuId !== '__all__') {
    query.menuId = menuId
    if (menuCategory) query.menuCategoryId = menuCategory
    if (menuTagText) query.menuTag = menuTagText
  }
  return query
}

const pickUnmanagedQuery = (query) => {
  const result = {}
  Object.keys(query || {}).forEach((key) => {
    if (!managedQueryKeys.includes(key)) {
      result[key] = query[key]
    }
  })
  return result
}

const comparableQuery = (query) => {
  const normalized = {}
  Object.keys(query || {})
    .sort()
    .forEach((key) => {
      const raw = firstQueryValue(query[key])
      if (raw === undefined || raw === null) return
      const value = String(raw)
      if (!value) return
      normalized[key] = value
    })
  return JSON.stringify(normalized)
}

const syncRouteQueryFromState = async () => {
  if (!routeReady.value) return
  const nextQuery = {
    ...pickUnmanagedQuery(route.query),
    ...buildRouteQueryFromState(),
  }
  if (comparableQuery(route.query) === comparableQuery(nextQuery)) return false
  await router.replace({
    path: route.path,
    query: nextQuery,
  })
  return true
}

const refreshByState = async () => {
  const queryChanged = await syncRouteQueryFromState()
  if (!queryChanged) {
    await fetchProducts(true)
  }
}

const onSearch = () => {
  refreshByState()
}

const onSearchInput = () => {
  if (searchText.value !== '') return
  refreshByState()
}

const clearSearch = () => {
  if (!searchText.value) return
  searchText.value = ''
  refreshByState()
}

const chooseCategory = (id, topId) => {
  filters.categoryId = id
  activeTopId.value = topId || (findTopByCategoryId(id) || {}).id || null
  refreshByState()
}

const clearMenuFilter = () => {
  activeMenuId.value = '__all__'
  menuCategoryId.value = null
  menuTag.value = ''
}

const clearCategoryFilter = () => {
  filters.categoryId = null
  activeTopId.value = null
  clearMenuFilter()
  refreshByState()
}

const initMenuChooser = () => {
  const roots = level1Menus.value
  if (!roots.length) {
    activeMenuL1Id.value = ''
    activeMenuL2Id.value = ''
    return
  }

  if (activeMenuId.value && activeMenuId.value !== '__all__') {
    const path = findMenuPathById(activeMenuId.value, roots, [])
    if (path.length) {
      activeMenuL1Id.value = String(path[0].id || '')
      if (path.length >= 2) {
        activeMenuL2Id.value = String(path[1].id || '')
      } else if (path[0].children && path[0].children.length) {
        activeMenuL2Id.value = String(path[0].children[0].id || '')
      } else {
        activeMenuL2Id.value = ''
      }
      return
    }
  }

  const first = roots[0]
  activeMenuL1Id.value = String(first.id || '')
  if (first.children && first.children.length) {
    activeMenuL2Id.value = String(first.children[0].id || '')
  } else {
    activeMenuL2Id.value = ''
  }
}

const openMenuDialog = () => {
  initMenuChooser()
  menuDialogVisible.value = true
  if (drawerLevel2Items.value.length) {
    loadMenuPreview(drawerLevel2Items.value)
  }
  if (drawerLevel3Items.value.length) {
    loadMenuPreview(drawerLevel3Items.value)
  }
}

const openContact = () => {
  if (!contactItems.value.length) {
    ElMessage.info(t('gallery.contactMissing'))
    return
  }
  contactDialogVisible.value = true
}

const copyToClipboard = async (text) => {
  const value = String(text || '').trim()
  if (!value) return false
  if (navigator?.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return true
  }
  const temp = document.createElement('textarea')
  temp.value = value
  temp.setAttribute('readonly', 'readonly')
  temp.style.position = 'fixed'
  temp.style.opacity = '0'
  document.body.appendChild(temp)
  temp.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } finally {
    document.body.removeChild(temp)
  }
  return ok
}

const copyContactItem = async (item) => {
  if (!item || !item.copyValue) {
    ElMessage.warning(t('gallery.contactCopyFailed'))
    return
  }
  try {
    const ok = await copyToClipboard(item.copyValue)
    if (!ok) {
      ElMessage.warning(t('gallery.contactCopyFailed'))
      return
    }
    ElMessage.success(t('gallery.contactCopied'))
  } catch (_error) {
    ElMessage.warning(t('gallery.contactCopyFailed'))
  }
}

const openBatchForward = () => {
  ElMessage.info(t('gallery.batchBuilding'))
}

const onCurrencyChange = (value) => {
  activeCurrency.value = setCurrentCurrency(normalizeCurrency(value))
}

const chooseLevel1 = (item) => {
  if (!item) return
  activeMenuL1Id.value = String(item.id || '')
  if (item.children && item.children.length) {
    activeMenuL2Id.value = String(item.children[0].id || '')
    loadMenuPreview(item.children)
  } else {
    activeMenuL2Id.value = ''
  }
}

const chooseLevel2 = (item) => {
  if (!item) return
  activeMenuL2Id.value = String(item.id || '')
  if (item.children && item.children.length) {
    loadMenuPreview(item.children)
  }
  applyMenuItem(item)
}

const openLevel1Page = (item) => {
  if (!item || String(item.targetType || '').toUpperCase() === 'GROUP') return
  applyMenuItem(item)
}

const applyMenuItem = (item) => {
  if (!item) {
    clearMenuFilter()
    menuDialogVisible.value = false
    refreshByState()
    return
  }
  if (item.targetType === 'GROUP') {
    if (Array.isArray(item.children) && item.children.length > 0) {
      return
    }
    clearMenuFilter()
    menuDialogVisible.value = false
    refreshByState()
    return
  }
  if (item.targetType === 'CUSTOM_PAGE' && item.customPageKey) {
    menuDialogVisible.value = false
    router.push('/page/' + item.customPageKey)
    return
  }

  activeMenuId.value = String(item.id || item.name || Date.now())
  if (item.targetType === 'CATEGORY') {
    menuCategoryId.value = item.categoryId || null
    menuTag.value = ''
  } else if (item.targetType === 'TAG') {
    menuCategoryId.value = null
    menuTag.value = item.tagName || ''
  } else if (item.targetType === 'CATEGORY_TAG') {
    menuCategoryId.value = item.categoryId || null
    menuTag.value = item.tagName || ''
  } else {
    menuCategoryId.value = null
    menuTag.value = ''
  }
  menuDialogVisible.value = false
  refreshByState()
}

const loadMore = async () => {
  if (!hasMore.value || loading.value) return
  await fetchProducts(false)
}

const ensureAutoLoadIfNeeded = async () => {
  if (typeof window === 'undefined') return
  if (!hasMore.value || loading.value) return
  const docHeight = document.documentElement.scrollHeight || document.body.scrollHeight || 0
  const scrollBottom = (window.scrollY || 0) + window.innerHeight
  if (docHeight > 0 && docHeight - scrollBottom <= 280) {
    await loadMore()
  }
}

const handleScroll = () => {
  if (typeof window !== 'undefined') {
    showBackToTop.value = (window.scrollY || 0) > 520
  }
  void ensureAutoLoadIfNeeded()
}

const scrollToTop = () => {
  if (typeof window === 'undefined') return
  window.scrollTo({
    top: 0,
    left: 0,
    behavior: 'smooth',
  })
}

const goDetail = (item) => {
  writeGalleryStateCache()
  router.push({
    path: '/product/' + item.id,
    query: { currency: activeCurrency.value },
  })
}

const preloadImageWindow = (urls, centerIndex, maxNeighbors = 2) => {
  if (typeof window === 'undefined') return
  const list = Array.isArray(urls) ? urls : []
  if (!list.length) return
  const idx = Math.max(0, Math.min(Number(centerIndex || 0), list.length - 1))
  const candidates = []
  for (let offset = 1; candidates.length < maxNeighbors; offset += 1) {
    const nextIndex = idx + offset
    if (nextIndex < list.length) {
      candidates.push(nextIndex)
      if (candidates.length >= maxNeighbors) break
    }
    const prevIndex = idx - offset
    if (prevIndex >= 0) {
      candidates.push(prevIndex)
    }
    if (nextIndex >= list.length && prevIndex < 0) break
  }
  for (const candidateIndex of candidates) {
    const src = list[candidateIndex]
    if (!src) continue
    if (preloadedImageUrls.has(src)) continue
    preloadedImageUrls.add(src)
    const img = new Image()
    img.src = src
  }
}

const openImagePreview = (payload) => {
  const urls = payload && Array.isArray(payload.urls) ? payload.urls.filter(Boolean) : []
  if (!urls.length) return
  const targetIndex = Math.max(0, Math.min(Number(payload.index || 0), urls.length - 1))
  imagePreviewUrls.value = urls
  imagePreviewIndex.value = targetIndex
  imagePreviewVisible.value = true
  preloadImageWindow(urls, targetIndex)
}

const onImagePreviewSwitch = (index) => {
  imagePreviewIndex.value = Number(index || 0)
  preloadImageWindow(imagePreviewUrls.value, imagePreviewIndex.value)
}

const openVideoPreview = (payload) => {
  if (!payload || !payload.url) return
  videoPreviewUrl.value = payload.url
  videoPreviewTitle.value = payload.product && payload.product.title ? payload.product.title : t('gallery.videoPreviewTitle')
  videoPreviewVisible.value = true
}

const openFilterSheet = () => {
  draftFilters.categoryId = filters.categoryId
  draftFilters.startDate = filters.startDate
  draftFilters.endDate = filters.endDate
  draftFilters.share = filters.share
  selectedQuick.value = ''
  filterSheetVisible.value = true
}

const resetDraftFilter = () => {
  draftFilters.categoryId = null
  draftFilters.startDate = ''
  draftFilters.endDate = ''
  draftFilters.share = 'all'
  selectedQuick.value = ''
}

const confirmFilter = async () => {
  filters.categoryId = draftFilters.categoryId
  filters.startDate = draftFilters.startDate
  filters.endDate = draftFilters.endDate
  filters.share = draftFilters.share
  filterSheetVisible.value = false

  activeTopId.value = (findTopByCategoryId(filters.categoryId) || {}).id || null
  await refreshByState()
}

onMounted(async () => {
  let restoredScrollY = 0
  let restoredProducts = false
  try {
    locale.value = setCurrentLang('en-US')
    const restored = restoreGalleryStateCache()
    restoredProducts = restored.restored
    restoredScrollY = restored.scrollY
    applyStateFromRouteQuery(route.query)
    void fetchCategories().then(() => {
      activeTopId.value = (findTopByCategoryId(filters.categoryId) || {}).id || null
    })
    void fetchShopProfile()
    void fetchSummary()
    void fetchCurrencyRates()
    routeReady.value = true
    void syncRouteQueryFromState()
    if (!restoredProducts) {
      void fetchProducts(true)
    }
  } finally {
    booting.value = false
  }
  await nextTick()
  if (restoredProducts && restoredScrollY > 0 && typeof window !== 'undefined') {
    window.scrollTo({
      top: restoredScrollY,
      left: 0,
      behavior: 'auto',
    })
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('scroll', handleScroll, { passive: true })
    showBackToTop.value = (window.scrollY || 0) > 520
  }
  countdownTimer = window.setInterval(() => {
    currentTimeMs.value = Date.now()
  }, 1000)
  await ensureAutoLoadIfNeeded()
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('scroll', handleScroll)
  }
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
})

watch(drawerLevel2Items, (list) => {
  if (!menuDialogVisible.value) return
  loadMenuPreview(list)
})

watch(drawerLevel3Items, (list) => {
  if (!menuDialogVisible.value) return
  loadMenuPreview(list)
})

watch(
  () => route.query,
  async (query) => {
    if (!routeReady.value) return
    applyStateFromRouteQuery(query)
    await fetchProducts(true)
  },
)

watch(
  [activeTab, displayMode],
  () => {
    if (!routeReady.value) return
    syncRouteQueryFromState()
  },
)
</script>

<template>
  <div class="min-h-screen bg-[#ebebee] px-2 py-3 pb-20 text-left md:px-6 md:py-6 md:pb-24">
    <section
      v-if="booting"
      class="mx-auto max-w-[920px] overflow-hidden rounded-[26px] bg-[#f6f6f7] shadow-[0_20px_60px_rgba(0,0,0,0.14)]"
    >
      <div class="h-[260px] animate-pulse bg-zinc-300 md:h-[320px]"></div>
      <div class="space-y-3 px-4 py-4 md:px-6">
        <div class="h-12 animate-pulse rounded-xl bg-zinc-200"></div>
        <div class="h-8 w-56 animate-pulse rounded-lg bg-zinc-200"></div>
        <div class="grid grid-cols-2 gap-3 md:grid-cols-3">
          <div v-for="i in 6" :key="i" class="h-36 animate-pulse rounded-2xl bg-zinc-200"></div>
        </div>
      </div>
    </section>

    <section
      v-else
      class="mx-auto max-w-[920px] overflow-hidden rounded-[26px] bg-[#f6f6f7] shadow-[0_20px_60px_rgba(0,0,0,0.14)]"
    >
      <div class="relative h-[260px] md:h-[320px]">
        <img v-if="heroCover" :src="heroCover" alt="cover" class="h-full w-full object-cover" />
        <div v-else class="hero-fallback h-full w-full"></div>
        <div class="absolute inset-0 bg-black/35"></div>

        <div class="absolute left-4 right-4 top-4 flex items-center gap-2 md:left-8 md:right-8 md:top-6">
          <div
            class="flex h-11 flex-1 items-center gap-2 rounded-full bg-white/16 px-4 text-white backdrop-blur-[2px] ring-1 ring-white/18"
          >
            <el-icon><Search /></el-icon>
            <input
              v-model="searchText"
              type="text"
              :placeholder="t('common.search')"
              class="w-full bg-transparent text-sm outline-none placeholder:text-white/80"
              @input="onSearchInput"
              @keyup.enter="onSearch"
            />
            <button v-if="searchText" type="button" class="grid h-6 w-6 place-items-center text-white/85" @click="clearSearch">
              <el-icon><Close /></el-icon>
            </button>
          </div>
          <button
            class="grid h-11 w-11 place-items-center rounded-full bg-white/16 text-white ring-1 ring-white/20"
            :title="t('common.search')"
            @click="onSearch"
          >
            <el-icon><Search /></el-icon>
          </button>
          <label
            class="flex h-11 items-center gap-2 rounded-full bg-white/16 px-2 text-white ring-1 ring-white/20"
            :title="t('common.currency')"
          >
            <span class="px-1 text-[10px] font-bold tracking-[0.12em] text-white/85">FX</span>
            <select
              :value="activeCurrency"
              class="h-8 min-w-[84px] appearance-none rounded-full border border-white/30 bg-white/95 px-3 text-xs font-bold tracking-[0.08em] text-zinc-900 shadow-sm outline-none"
              @change="onCurrencyChange($event.target.value)"
            >
              <option
                v-for="item in currencyOptions"
                :key="item.code"
                :value="item.code"
                class="text-zinc-900"
              >
                {{ item.label }}
              </option>
            </select>
          </label>
        </div>

        <div class="absolute bottom-4 left-4 right-4 md:bottom-5 md:left-8 md:right-8">
          <div class="flex items-end justify-between gap-3">
            <div class="flex items-center gap-3 text-white">
              <img class="h-12 w-12 rounded-full border-2 border-white/40 object-cover" :src="shopLogo" alt="avatar" />
              <div>
                <h1 class="text-2xl font-black italic text-white drop-shadow">{{ shopTitle }}</h1>
                <p class="text-sm text-white/85">{{ summaryText }}</p>
                <p class="text-xs text-white/75">FX: {{ activeCurrencyLabel }}</p>
                <p v-if="shopProfile.domain" class="text-xs text-white/75">{{ shopProfile.domain }}</p>
              </div>
            </div>
            <button
              class="hidden rounded-full bg-white/16 px-4 py-2 text-xs font-semibold text-white ring-1 ring-white/20 md:block"
              @click="router.push('/admin')"
            >
              {{ t('gallery.previewDashboard') }}
            </button>
          </div>
        </div>
      </div>

      <div class="px-4 pb-6 pt-4 md:px-6 md:pb-8">
        <div v-if="announcement" class="rounded-xl bg-[#f4e7d8] px-4 py-3 text-[18px] font-semibold text-[#d86f1f]">
          {{ announcement }}
        </div>

        <div class="mt-4 flex items-center justify-between gap-3">
          <div class="no-scrollbar flex min-w-0 flex-1 items-center gap-4 overflow-x-auto pr-1 md:gap-6">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="relative shrink-0 whitespace-nowrap text-[17px] md:text-xl"
              :class="activeTab === tab.key ? 'font-black text-zinc-900' : 'font-semibold text-zinc-500'"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
              <span
                v-if="activeTab === tab.key"
                class="absolute -bottom-2 left-0 h-1.5 w-6 rounded-full bg-emerald-500"
              ></span>
            </button>
          </div>

          <div class="flex shrink-0 items-center gap-2">
            <button
              class="grid h-10 w-10 place-items-center rounded-full bg-white text-zinc-700 shadow-sm ring-1 ring-zinc-200"
              @click="viewSheetVisible = true"
            >
              <el-icon><Operation /></el-icon>
            </button>
            <button
              class="grid h-10 w-10 place-items-center rounded-full bg-white text-zinc-700 shadow-sm ring-1 ring-zinc-200"
              @click="openFilterSheet"
            >
              <el-icon><Filter /></el-icon>
            </button>
          </div>
        </div>

        <div class="mt-3 flex items-center gap-2 text-xs text-zinc-500">
          <span>
            {{ t('gallery.currentMenu') }}：<span class="font-semibold text-zinc-800">{{ activeMenuLabel }}</span>
          </span>
          <button
            v-if="hasActiveCategoryFilter"
            class="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] font-semibold text-zinc-600 ring-1 ring-zinc-200"
            @click="clearCategoryFilter"
          >
            {{ t('common.clear') }}
          </button>
        </div>

        <div v-if="activeMenuFamilyItems.length > 1" class="mt-3 overflow-hidden rounded-2xl border border-zinc-200 bg-white/90 p-2 shadow-sm">
          <div class="no-scrollbar flex items-center gap-2 overflow-x-auto">
            <button
              v-for="item in activeMenuFamilyItems"
              :key="item.id"
              type="button"
              class="shrink-0 rounded-full px-4 py-2 text-sm font-semibold transition"
              :class="isActiveMenuFamilyItem(item) ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-700 hover:bg-zinc-200'"
              @click="openInlineMenuItem(item)"
            >
              {{ item.name }}
            </button>
          </div>
        </div>

        <div v-if="visibleMenuPageBlocks.length" class="mt-4 space-y-3">
          <div
            v-for="block in visibleMenuPageBlocks"
            :key="block.id"
            class="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm"
          >
            <button
              v-if="block.type === 'IMAGE'"
              type="button"
              class="group block w-full text-left"
              :class="block.linkType && block.linkType !== 'NONE' ? 'cursor-pointer' : 'cursor-default'"
              @click="openMenuPageBlockLink(block)"
            >
              <div class="relative aspect-[16/7] min-h-[180px] overflow-hidden bg-zinc-100 md:min-h-[220px]">
                <img
                  v-if="block.imageUrl"
                  :src="block.imageUrl"
                  :alt="block.title || activeMenuLabel"
                  class="h-full w-full object-cover transition duration-500 group-hover:scale-[1.02]"
                />
                <div v-else class="grid h-full w-full place-items-center text-sm text-zinc-400">{{ block.title || activeMenuLabel }}</div>
                <div class="absolute inset-0 bg-gradient-to-t from-black/55 via-black/10 to-transparent"></div>
                <div class="absolute inset-x-0 bottom-0 p-4 md:p-6">
                  <div class="max-w-[560px]">
                    <p class="text-[11px] font-semibold uppercase tracking-[0.22em] text-white/75">{{ activeMenuLabel }}</p>
                    <p v-if="block.title" class="mt-2 text-xl font-black text-white md:text-3xl">{{ block.title }}</p>
                    <p v-if="block.subTitle" class="mt-2 text-sm font-medium text-white/85 md:text-base">{{ block.subTitle }}</p>
                    <div v-if="block.linkType && block.linkType !== 'NONE' && block.buttonText" class="mt-4">
                      <span class="inline-flex rounded-full bg-white px-4 py-2 text-sm font-bold text-zinc-900">
                        {{ block.buttonText }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </button>

            <div v-else-if="block.type === 'NOTICE'" class="bg-amber-50 px-4 py-3 text-left">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div class="min-w-0">
                  <p v-if="block.title" class="text-sm font-bold uppercase tracking-[0.12em] text-amber-700">{{ block.title }}</p>
                  <p v-if="block.content" class="mt-1 whitespace-pre-line text-sm leading-6 text-amber-900">{{ block.content }}</p>
                </div>
                <button
                  v-if="block.linkType && block.linkType !== 'NONE' && block.buttonText"
                  type="button"
                  class="rounded-full bg-amber-500 px-4 py-2 text-xs font-bold text-white"
                  @click="openMenuPageBlockLink(block)"
                >
                  {{ block.buttonText }}
                </button>
              </div>
            </div>

            <div v-else-if="block.type === 'COUNTDOWN'" class="bg-zinc-900 px-4 py-4 text-white">
              <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div class="min-w-0">
                  <p v-if="block.title" class="text-lg font-black">{{ block.title }}</p>
                  <p v-if="block.subTitle" class="mt-1 text-sm text-white/75">{{ block.subTitle }}</p>
                  <p v-if="block.content" class="mt-2 whitespace-pre-line text-sm leading-6 text-white/85">{{ block.content }}</p>
                </div>
                <div class="flex items-center gap-3">
                  <div class="rounded-2xl bg-white/10 px-4 py-3 text-center ring-1 ring-white/15">
                    <p class="text-[10px] font-semibold uppercase tracking-[0.2em] text-white/65">{{ t('gallery.menuPage.endsIn') }}</p>
                    <p class="mt-1 text-xl font-black">{{ countdownText(block) }}</p>
                  </div>
                  <button
                    v-if="block.linkType && block.linkType !== 'NONE' && block.buttonText"
                    type="button"
                    class="rounded-full bg-emerald-500 px-4 py-2 text-sm font-bold text-white"
                    @click="openMenuPageBlockLink(block)"
                  >
                    {{ block.buttonText }}
                  </button>
                </div>
              </div>
            </div>

            <div v-else class="grid gap-4 p-4 md:grid-cols-[160px_1fr] md:items-center">
              <div v-if="block.imageUrl" class="overflow-hidden rounded-xl">
                <img :src="block.imageUrl" :alt="block.title || activeMenuLabel" class="h-32 w-full object-cover md:h-28" />
              </div>
              <div class="min-w-0">
                <p v-if="block.title" class="text-lg font-black text-zinc-900">{{ block.title }}</p>
                <p v-if="block.subTitle" class="mt-1 text-sm font-medium text-zinc-500">{{ block.subTitle }}</p>
                <p v-if="block.content" class="mt-3 whitespace-pre-line text-sm leading-6 text-zinc-700">{{ block.content }}</p>
                <div v-if="block.linkType && block.linkType !== 'NONE' && block.buttonText" class="mt-4">
                  <button
                    type="button"
                    class="rounded-full bg-zinc-900 px-4 py-2 text-sm font-bold text-white"
                    @click="openMenuPageBlockLink(block)"
                  >
                    {{ block.buttonText }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <h2 v-if="showTopTitle" class="mt-4 text-3xl font-black text-zinc-900 md:text-3xl">{{ t('gallery.title') }}</h2>

        <div v-if="displayMode === 'small'" class="mt-3 grid grid-cols-2 gap-3 md:grid-cols-3">
          <ProductCard
            v-for="item in filteredProducts"
            :key="item.id"
            :product="item"
            :currency="activeCurrency"
            :rates="currencyRates"
            :source-currency="currencySource"
            mode="small"
            @open-detail="goDetail"
          />
        </div>

        <div v-else class="mt-3 space-y-3">
          <template v-for="row in multiModeRows" :key="row.key">
            <div
              v-if="row.type === 'date'"
              class="px-1 pt-1 text-[26px] font-extrabold leading-none tracking-tight text-zinc-800 md:text-[30px]"
            >
              {{ row.label }}
            </div>
            <ProductCard
              v-else
              :product="row.item"
              :currency="activeCurrency"
              :rates="currencyRates"
              :source-currency="currencySource"
              mode="multi"
              @open-detail="goDetail"
              @preview-images="openImagePreview"
              @preview-video="openVideoPreview"
            />
          </template>
        </div>

        <div v-if="filteredProducts.length === 0 && !loading" class="py-20 text-center text-zinc-500">{{ t('gallery.noProducts') }}</div>

        <div class="mt-7 flex flex-col items-center justify-center gap-2">
          <p v-if="loading && hasMore" class="text-xs text-zinc-400">{{ t('gallery.loadingMore') }}</p>
          <p v-else-if="!hasMore && filteredProducts.length" class="text-xs text-zinc-400">{{ t('gallery.reachedBottom') }}</p>
        </div>
      </div>
    </section>

    <el-drawer v-model="viewSheetVisible" direction="btt" size="46%" :with-header="false" class="view-sheet">
      <div class="px-5 pb-6 pt-4">
        <h3 class="text-center text-xl font-black text-zinc-900">{{ t('gallery.switchView') }}</h3>
        <div class="mt-5 grid grid-cols-2 gap-3">
          <button
            v-for="item in viewModes"
            :key="item.key"
            class="rounded-2xl border p-2"
            :class="
              displayMode === item.key
                ? 'border-emerald-400 bg-emerald-50 shadow-[0_4px_20px_rgba(16,185,129,0.18)]'
                : 'border-zinc-200 bg-zinc-100'
            "
            @click="displayMode = item.key; viewSheetVisible = false"
          >
            <div class="mode-preview">
              <div class="mode-preview-row"></div>
              <div class="mode-preview-row"></div>
              <div class="mode-preview-row short"></div>
            </div>
            <p class="mt-2 text-sm font-semibold text-zinc-700">{{ item.label }}</p>
          </button>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="filterSheetVisible" direction="btt" size="72%" :with-header="false" class="filter-sheet">
      <div class="relative px-5 pb-6 pt-4">
        <button class="absolute left-4 top-4 text-zinc-500" @click="filterSheetVisible = false">
          <el-icon size="22"><Close /></el-icon>
        </button>
        <h3 class="text-center text-xl font-black text-zinc-900">{{ t('gallery.filter') }}</h3>
        <button class="absolute right-4 top-4 text-sm font-semibold text-zinc-400" @click="resetDraftFilter">{{ t('common.clear') }}</button>

        <div class="mt-5">
          <p class="text-sm font-semibold text-zinc-700">{{ t('gallery.timeFrame') }}</p>
          <div class="mt-2 grid grid-cols-[1fr_auto_1fr] items-center gap-2">
            <el-date-picker
              v-model="draftFilters.startDate"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :placeholder="t('common.startDate')"
              class="w-full"
            />
            <span class="text-zinc-500">-</span>
            <el-date-picker
              v-model="draftFilters.endDate"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :placeholder="t('common.endDate')"
              class="w-full"
            />
          </div>

          <div class="mt-3 grid grid-cols-3 gap-2">
            <button
              v-for="item in quickDateOptions"
              :key="item.key"
              class="rounded-lg px-2 py-2 text-sm"
              :class="
                selectedQuick === item.key
                  ? 'bg-emerald-500 text-white'
                  : 'bg-zinc-100 text-zinc-700 ring-1 ring-zinc-200'
              "
              @click="pickQuickDate(item.key)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="mt-5">
          <p class="text-sm font-semibold text-zinc-700">{{ t('gallery.share.title') }}</p>
          <div class="mt-2 grid grid-cols-3 gap-2">
            <button
              class="rounded-lg px-2 py-2 text-sm"
              :class="draftFilters.share === 'all' ? 'bg-emerald-500 text-white' : 'bg-zinc-100 text-zinc-700 ring-1 ring-zinc-200'"
              @click="draftFilters.share = 'all'"
            >
              {{ t('gallery.share.all') }}
            </button>
            <button
              class="rounded-lg px-2 py-2 text-sm"
              :class="
                draftFilters.share === 'never' ? 'bg-emerald-500 text-white' : 'bg-zinc-100 text-zinc-700 ring-1 ring-zinc-200'
              "
              @click="draftFilters.share = 'never'"
            >
              {{ t('gallery.share.never') }}
            </button>
            <button
              class="rounded-lg px-2 py-2 text-sm"
              :class="
                draftFilters.share === 'shared' ? 'bg-emerald-500 text-white' : 'bg-zinc-100 text-zinc-700 ring-1 ring-zinc-200'
              "
              @click="draftFilters.share = 'shared'"
            >
              {{ t('gallery.share.shared') }}
            </button>
          </div>
        </div>

        <div class="mt-5">
          <p class="text-sm font-semibold text-zinc-700">{{ t('gallery.category') }}</p>
          <el-select v-model="draftFilters.categoryId" class="mt-2 w-full" clearable :placeholder="t('common.selectCategory')">
            <el-option :value="null" :label="t('gallery.allCategory')" />
            <el-option v-for="cat in topCategories" :key="String(cat.id)" :label="cat.name" :value="cat.id" />
          </el-select>
        </div>

        <div class="mt-8 grid grid-cols-2 gap-3">
          <button class="rounded-xl bg-zinc-100 py-3 text-lg font-semibold text-emerald-600" @click="resetDraftFilter">{{ t('common.reset') }}</button>
          <button class="rounded-xl bg-emerald-500 py-3 text-lg font-semibold text-white" @click="confirmFilter">{{ t('common.confirm') }}</button>
        </div>
      </div>
    </el-drawer>

    <el-drawer
      v-model="menuDialogVisible"
      direction="ltr"
      size="min(360px, 88vw)"
      :with-header="false"
      :close-on-click-modal="true"
      :lock-scroll="false"
      class="menu-sheet"
    >
      <div class="h-full px-3 pb-5 pt-3 md:px-4">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-lg font-bold text-zinc-900">{{ t('gallery.productCategory') }}</h3>
          <button
            class="rounded-full bg-zinc-900 px-3 py-1.5 text-xs font-semibold text-white"
            @click="applyMenuItem(null)"
          >
            {{ t('gallery.menu.all') }}
          </button>
        </div>

        <div v-if="menuTree.length">
          <div class="grid h-[72vh] min-h-[460px] grid-cols-[112px_1fr] gap-3">
            <aside class="overflow-y-auto border-r border-zinc-200 pr-2">
              <button
                v-for="item in level1Menus"
                :key="item.id"
                class="mb-2 w-full rounded-xl px-3 py-3 text-left text-sm leading-5 transition"
                :class="String(activeMenuL1Id) === String(item.id) ? 'bg-zinc-900 font-semibold text-white shadow-sm' : 'text-zinc-700 hover:bg-zinc-100'"
                @click="chooseLevel1(item)"
              >
                {{ item.name }}
              </button>
            </aside>

            <div class="flex min-h-0 flex-col overflow-hidden">
              <div v-if="drawerLevel1Node" class="flex items-center justify-between rounded-2xl border border-zinc-200 bg-zinc-50 px-3 py-2">
                <p class="min-w-0 truncate text-sm font-black text-zinc-900">{{ drawerLevel1Node.name }}</p>
                <button
                  v-if="String(drawerLevel1Node.targetType || '').toUpperCase() !== 'GROUP'"
                  type="button"
                  class="inline-flex items-center gap-1 rounded-full bg-white px-3 py-1.5 text-xs font-bold text-zinc-700 ring-1 ring-zinc-200"
                  @click="openLevel1Page(drawerLevel1Node)"
                >
                  <span>{{ t('gallery.menu.viewAll') }}</span>
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>

              <div v-if="drawerLevel3Items.length" class="pt-3">
                <div class="no-scrollbar flex gap-2 overflow-x-auto">
                  <button
                    v-for="item in drawerLevel3Items"
                    :key="item.id"
                    class="shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold"
                    :class="isActiveMenuFamilyItem(item) ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-700'"
                    @click="openInlineMenuItem(item)"
                  >
                    {{ item.name }}
                  </button>
                </div>
              </div>

              <div class="flex-1 overflow-y-auto pt-3">
                <div v-if="drawerLevel2Items.length" class="grid grid-cols-3 gap-2">
                  <button
                    v-for="item in drawerLevel2Items"
                    :key="item.id"
                    class="rounded-2xl border border-zinc-200 bg-white p-1.5 text-center transition hover:-translate-y-0.5 hover:shadow-sm"
                    @click="chooseLevel2(item)"
                  >
                    <div
                      class="aspect-square overflow-hidden rounded-xl shadow-sm"
                      :class="isDrawerActiveLevel2Item(item) ? 'ring-2 ring-zinc-900 ring-offset-1' : ''"
                    >
                      <img
                        v-if="menuPreviewMap[item.id]"
                        :src="menuPreviewMap[item.id]"
                        :alt="item.name"
                        class="h-full w-full object-cover"
                      />
                      <div v-else class="grid h-full w-full place-items-center bg-gradient-to-br from-[#f3ead9] via-[#f7f2e7] to-[#ece6f7] text-lg font-black text-zinc-500">
                        {{ menuFallbackLabel(item) }}
                      </div>
                    </div>
                    <p class="mt-2 line-clamp-2 text-[12px] font-semibold leading-4 text-zinc-900">{{ item.name }}</p>
                    <p class="mt-1 text-[10px] text-zinc-400">{{ item.children && item.children.length ? t('gallery.menu.hasChildren') : menuTypeLabel(item) }}</p>
                  </button>
                </div>
                <div v-else class="grid h-full place-items-center rounded-lg border border-dashed border-zinc-200 bg-zinc-50 text-sm text-zinc-400">
                  {{ t('gallery.menu.noChildren') }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="rounded-lg bg-zinc-50 px-3 py-10 text-center text-sm text-zinc-400">
          {{ t('gallery.menu.noConfig') }}
        </div>
      </div>
    </el-drawer>

    <div
      class="fixed bottom-0 left-0 right-0 z-40 border-t border-zinc-200 bg-white/95 backdrop-blur md:left-1/2 md:w-[920px] md:-translate-x-1/2"
    >
      <div class="mx-auto grid max-w-[920px] grid-cols-3">
        <button class="py-3 text-sm font-semibold text-zinc-800" @click="openMenuDialog">☰ {{ t('gallery.productCategory') }}</button>
        <button class="py-3 text-sm font-semibold text-zinc-800" @click="openContact">{{ t('gallery.contact') }}</button>
        <button class="py-3 text-sm font-semibold text-zinc-800" @click="openBatchForward">{{ t('gallery.batchForward') }}</button>
      </div>
    </div>

    <el-dialog v-model="contactDialogVisible" :title="t('gallery.contact')" width="min(560px, 94vw)">
      <div v-if="contactItems.length" class="space-y-2">
        <div
          v-for="item in contactItems"
          :key="item.id"
          class="flex items-center justify-between rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-2"
        >
          <div class="min-w-0">
            <p class="text-xs font-semibold uppercase tracking-wide text-zinc-500">{{ item.label }}</p>
            <p class="truncate text-sm font-medium text-zinc-900">{{ item.value }}</p>
          </div>
          <el-button type="primary" plain size="small" @click="copyContactItem(item)">{{ t('common.copy') }}</el-button>
        </div>
      </div>
      <div v-else class="py-8 text-center text-sm text-zinc-500">
        {{ t('gallery.contactMissing') }}
      </div>
      <template #footer>
        <span class="text-xs text-zinc-400">{{ t('gallery.contactCopyHint') }}</span>
      </template>
    </el-dialog>

    <button
      v-if="showBackToTop"
      type="button"
      class="fixed bottom-20 right-4 z-40 flex h-11 w-11 items-center justify-center rounded-full bg-zinc-900 text-white shadow-[0_10px_30px_rgba(0,0,0,0.22)] transition hover:-translate-y-0.5 hover:bg-zinc-700 md:bottom-6 md:right-6"
      @click="scrollToTop"
    >
      <el-icon :size="18"><ArrowUp /></el-icon>
    </button>

    <teleport to="body">
      <el-image-viewer
        v-if="imagePreviewVisible"
        :url-list="imagePreviewUrls"
        :initial-index="imagePreviewIndex"
        @switch="onImagePreviewSwitch"
        @close="imagePreviewVisible = false"
      />
    </teleport>

    <el-dialog v-model="videoPreviewVisible" :title="videoPreviewTitle" width="min(860px, 96vw)" destroy-on-close>
      <video
        v-if="videoPreviewUrl"
        :src="videoPreviewUrl"
        controls
        autoplay
        playsinline
        class="max-h-[72vh] w-full rounded-lg bg-black"
      ></video>
    </el-dialog>
  </div>
</template>

<style scoped>
.hero-fallback {
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.22), transparent 40%),
    linear-gradient(120deg, #3f454c 0%, #68727d 46%, #2f343a 100%);
}

.mode-preview {
  height: 56px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.65);
  padding: 7px;
}

.mode-preview-row {
  height: 10px;
  border-radius: 4px;
  background: #d4d4d8;
  margin-bottom: 5px;
}

.mode-preview-row.short {
  width: 65%;
}

:deep(.view-sheet),
:deep(.filter-sheet),
:deep(.view-sheet .el-drawer),
:deep(.filter-sheet .el-drawer) {
  width: min(920px, calc(100vw - 12px)) !important;
  max-width: min(920px, calc(100vw - 12px)) !important;
  margin: 0 auto;
  border-radius: 24px 24px 0 0;
  overflow: hidden;
}

:deep(.menu-sheet) {
  border-radius: 0 24px 24px 0;
  width: min(360px, 88vw) !important;
}

/* On desktop: position drawer at content area left edge.
   The drawer slides in from the left via Element Plus's built-in transform transition. */
@media (min-width: 768px) {
  :deep(.menu-sheet.ltr) {
    left: calc(50vw - 460px) !important;
  }
}

.no-scrollbar {
  scrollbar-width: none;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}
</style>
