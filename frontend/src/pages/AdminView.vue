<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/gallery'
import ProductFormDialog from '../components/ProductFormDialog.vue'
import OrderVoucherAdminPanel from '../components/OrderVoucherAdminPanel.vue'
import WsAlbumAdminPanel from '../components/WsAlbumAdminPanel.vue'
import { clearAdminAuth, getAdminUsername } from '../utils/auth'
import { applyPageMeta } from '../utils/pageMeta'

const router = useRouter()
const activeTab = ref('products')

const stats = reactive({
  totalProducts: 0,
  totalCategories: 0,
  totalImages: 0,
  publishedToday: 0,
  imageStorageMb: 0,
})
const imageStorageGb = computed(() => (Number(stats.imageStorageMb || 0) / 1024).toFixed(2))
const analyticsLoading = ref(false)
const analyticsDays = ref(7)
const analyticsOverview = reactive({
  pv: 0,
  uv: 0,
  avgStaySeconds: 0,
  trend: [],
  topPages: [],
})

const products = ref([])
const categories = ref([])
const loadingProducts = ref(false)

const pager = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const query = reactive({
  keyword: '',
  sku: '',
  categoryId: null,
  status: null,
})

const dialogVisible = ref(false)
const editingProduct = ref(null)
const selectedProductIds = ref([])
const batchCategoryId = ref(null)
const batchUpdating = ref(false)
const orderVoucherPanelRef = ref(null)

const categoryDialogVisible = ref(false)
const categoryForm = reactive({
  id: null,
  parentId: 0,
  name: '',
  enName: '',
  sort: 0,
})
const categorySaving = ref(false)

const shopSaving = ref(false)
const shopUploading = reactive({
  logo: false,
  banner: false,
})
const menuPageBlockUploading = reactive({})

const shopForm = reactive({
  shopName: '',
  shopLogo: '',
  heroBanner: '',
  announcement: '',
  domain: '',
  languageLabel: 'EN',
  themeColor: '#10b981',
  blockSearchEngineCrawl: 0,
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

const llmSaving = ref(false)
const llmTesting = ref(false)
const llmTestResult = ref(null)
const llmForm = reactive({
  id: null,
  enabled: 0,
  provider: 'openai-compatible',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  apiKeyMasked: '',
  model: 'gpt-4o-mini',
  temperature: 0.3,
  maxTokens: 800,
  targetLang: 'en',
  strictMode: 0,
  systemPrompt: '',
  userPromptTemplate: '',
  lastTestTime: '',
  lastTestStatus: null,
  lastTestMessage: '',
})
const llmTestInput = reactive({
  title: '新款格纹公文包，支持商务和日常通勤。',
  description: '容量大，质感好，支持肩背和手提。',
})

const ossSaving = ref(false)
const ossTesting = ref(false)
const ossForm = reactive({
  id: null,
  enabled: 0,
  endpoint: '',
  bucketName: '',
  accessKeyId: '',
  accessKeySecret: '',
  accessKeySecretMasked: '',
  bucketDomain: '',
  lastTestTime: '',
  lastTestStatus: null,
  lastTestMessage: '',
})

const menuTargetOptions = [
  { value: 'GROUP', label: '目录分组' },
  { value: 'CATEGORY', label: '分类' },
  { value: 'TAG', label: '标签' },
  { value: 'CATEGORY_TAG', label: '分类+标签' },
  { value: 'CUSTOM_PAGE', label: '自定义页面' },
]

const menuPageBlockTypeOptions = [
  { value: 'IMAGE', label: '单图活动位' },
  { value: 'RICH_TEXT', label: '图文说明' },
  { value: 'NOTICE', label: '公告' },
  { value: 'COUNTDOWN', label: '倒计时活动' },
]

const menuPageLinkTypeOptions = [
  { value: 'NONE', label: '不跳转' },
  { value: 'INTERNAL', label: '站内链接' },
  { value: 'EXTERNAL', label: '站外链接' },
]

const contactTypeOptions = [
  { value: 'PERSON', label: '联系人' },
  { value: 'WECHAT', label: '微信' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'PHONE', label: '电话' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'CUSTOM', label: '自定义' },
]

const pageMetaOptions = [
  { value: 'home', label: '首页 / 商品列表' },
  { value: 'product', label: '商品详情页' },
  { value: 'voucher', label: '订单凭证页' },
  { value: 'admin-login', label: '后台登录页' },
  { value: 'admin', label: '后台管理页' },
]

const adminName = computed(() => getAdminUsername() || 'Admin')

const categoryOptions = computed(() => {
  const list = [{ value: 0, label: '顶级分类' }]
  const walk = (nodes, prefix) => {
    nodes.forEach((node) => {
      const label = prefix ? prefix + ' / ' + node.name : node.name
      list.push({ value: node.id, label })
      if (node.children?.length) {
        walk(node.children, label)
      }
    })
  }
  walk(categories.value || [], '')
  return list
})

const flatCategoryRows = computed(() => {
  const rows = []
  const walk = (nodes, level, parentName) => {
    nodes.forEach((node) => {
      rows.push({
        id: node.id,
        parentId: node.parentId || 0,
        name: node.name,
        level,
        sort: node.sort,
        parentName: parentName || '顶级分类',
      })
      if (node.children?.length) {
        walk(node.children, level + 1, node.name)
      }
    })
  }
  walk(categories.value || [], 1, '')
  return rows
})

const categoryDialogTitle = computed(() => (categoryForm.id ? '编辑分类' : '新增分类'))

const normalizeMenuPageBlockType = (raw) => {
  const value = String(raw || '').trim().toUpperCase()
  if (['IMAGE', 'RICH_TEXT', 'NOTICE', 'COUNTDOWN'].includes(value)) return value
  return 'IMAGE'
}

const normalizeMenuPageLinkType = (raw) => {
  const value = String(raw || '').trim().toUpperCase()
  if (['NONE', 'INTERNAL', 'EXTERNAL'].includes(value)) return value
  return 'NONE'
}

const menuPageMenuOptions = computed(() => {
  return (shopForm.menuItems || [])
    .filter((item) => ['CATEGORY', 'TAG', 'CATEGORY_TAG'].includes(String(item?.targetType || '').toUpperCase()))
    .map((item) => ({
      value: String(item.id || '').trim(),
      label: String(item.name || '').trim() || String(item.id || '').trim(),
    }))
    .filter((item) => item.value)
})

const normalizeContactType = (raw) => {
  const value = String(raw || '').trim().toUpperCase()
  if (['PERSON', 'WECHAT', 'WHATSAPP', 'PHONE', 'EMAIL', 'CUSTOM'].includes(value)) return value
  return 'CUSTOM'
}

const defaultContactLabel = (type) => {
  const value = normalizeContactType(type)
  if (value === 'PERSON') return '联系人'
  if (value === 'WECHAT') return '微信'
  if (value === 'WHATSAPP') return 'WhatsApp'
  if (value === 'PHONE') return '电话'
  if (value === 'EMAIL') return '邮箱'
  return '联系方式'
}

const contactValuePlaceholder = (type) => {
  const value = normalizeContactType(type)
  if (value === 'EMAIL') return '例如：sales@example.com'
  if (value === 'WECHAT') return '例如：Dudu-H2'
  if (value === 'WHATSAPP') return '例如：+86 13800000000'
  if (value === 'PHONE') return '例如：13800000000'
  if (value === 'PERSON') return '例如：Alice'
  return '请输入联系方式内容'
}

const fetchStats = async (includeImageStorage = false) => {
  try {
    const data = await api.dashboardStats({ includeImageStorage })
    const next = data || {}
    stats.totalProducts = next.totalProducts == null ? stats.totalProducts : next.totalProducts
    stats.totalCategories = next.totalCategories == null ? stats.totalCategories : next.totalCategories
    stats.totalImages = next.totalImages == null ? stats.totalImages : next.totalImages
    stats.publishedToday = next.publishedToday == null ? stats.publishedToday : next.publishedToday
    if (next.imageStorageMb != null) {
      stats.imageStorageMb = next.imageStorageMb
    }
  } catch (error) {
    ElMessage.error(error.message || '统计加载失败')
  }
}

const fetchAnalyticsOverview = async () => {
  analyticsLoading.value = true
  try {
    const data = await api.adminAnalyticsOverview({
      days: analyticsDays.value,
      topN: 10,
    })
    analyticsOverview.pv = Number(data?.pv || 0)
    analyticsOverview.uv = Number(data?.uv || 0)
    analyticsOverview.avgStaySeconds = Number(data?.avgStaySeconds || 0)
    analyticsOverview.trend = Array.isArray(data?.trend) ? data.trend : []
    analyticsOverview.topPages = Array.isArray(data?.topPages) ? data.topPages : []
  } catch (error) {
    ElMessage.error(error.message || '访问统计加载失败')
  } finally {
    analyticsLoading.value = false
  }
}

const formatStaySeconds = (seconds) => {
  const total = Number(seconds || 0)
  if (total <= 0) return '0s'
  if (total < 60) return `${total}s`
  const mins = Math.floor(total / 60)
  const secs = total % 60
  return `${mins}m ${secs}s`
}

const fetchCategories = async () => {
  try {
    categories.value = await api.categoriesTree()
  } catch (error) {
    ElMessage.error(error.message || '分类加载失败')
  }
}

const fetchShopProfile = async () => {
  try {
    const data = await api.shopProfile()
    Object.assign(shopForm, {
      shopName: '',
      shopLogo: '',
      heroBanner: '',
      announcement: '',
      domain: '',
      languageLabel: 'EN',
      themeColor: '#10b981',
      blockSearchEngineCrawl: 0,
      contactName: '',
      contactWechat: '',
      contactPhone: '',
      contacts: [],
      copyrightText: '',
      menuItems: [],
      menuPageConfigs: [],
      customPages: [],
      pageMetas: [],
    }, data || {})
    shopForm.menuItems = Array.isArray(shopForm.menuItems) ? shopForm.menuItems : []
    shopForm.menuItems = shopForm.menuItems.map((item, idx) => ({
      id: item && item.id ? String(item.id) : `menu-${Date.now()}-${idx}`,
      parentId: item && item.parentId ? String(item.parentId) : '',
      name: item && item.name ? item.name : '',
      targetType: item && item.targetType ? item.targetType : 'CATEGORY',
      categoryId: item && item.categoryId ? String(item.categoryId) : null,
      tagName: item && item.tagName ? item.tagName : '',
      customPageKey: item && item.customPageKey ? item.customPageKey : '',
      sort: item && item.sort != null ? item.sort : idx + 1,
      enabled: item && item.enabled === 0 ? 0 : 1,
    }))
    shopForm.menuPageConfigs = Array.isArray(shopForm.menuPageConfigs) ? shopForm.menuPageConfigs.map((config, idx) => ({
      menuId: config && config.menuId ? String(config.menuId) : '',
      enabled: config && config.enabled === 0 ? 0 : 1,
      blocks: Array.isArray(config && config.blocks) ? config.blocks.map((block, blockIdx) => ({
        id: block && block.id ? String(block.id) : `menu-block-${Date.now()}-${idx}-${blockIdx}`,
        type: normalizeMenuPageBlockType(block && block.type),
        title: block && block.title ? String(block.title) : '',
        subTitle: block && block.subTitle ? String(block.subTitle) : '',
        imageUrl: block && block.imageUrl ? String(block.imageUrl) : '',
        content: block && block.content ? String(block.content) : '',
        buttonText: block && block.buttonText ? String(block.buttonText) : '',
        linkType: normalizeMenuPageLinkType(block && block.linkType),
        linkValue: block && block.linkValue ? String(block.linkValue) : '',
        startTime: block && block.startTime ? String(block.startTime) : '',
        endTime: block && block.endTime ? String(block.endTime) : '',
        sort: block && block.sort != null ? Number(block.sort) : blockIdx + 1,
        enabled: block && block.enabled === 0 ? 0 : 1,
      })) : [],
    })) : []
    shopForm.customPages = Array.isArray(shopForm.customPages) ? shopForm.customPages : []
    shopForm.pageMetas = Array.isArray(shopForm.pageMetas) ? shopForm.pageMetas.map((item, idx) => ({
      id: item && item.id ? String(item.id) : `meta-${Date.now()}-${idx}`,
      pageKey: item && item.pageKey ? String(item.pageKey) : 'home',
      langCode: item && item.langCode ? String(item.langCode) : 'en-us',
      title: item && item.title ? String(item.title) : '',
      description: item && item.description ? String(item.description) : '',
    })) : []
    let contactRows = Array.isArray(shopForm.contacts) ? shopForm.contacts : []
    contactRows = contactRows.map((item, idx) => ({
      id: item && item.id ? String(item.id) : `contact-${Date.now()}-${idx}`,
      type: normalizeContactType(item && item.type ? item.type : 'CUSTOM'),
      label: item && item.label ? String(item.label) : '',
      value: item && item.value ? String(item.value) : '',
      copyValue: item && item.copyValue ? String(item.copyValue) : '',
      sort: item && item.sort != null ? Number(item.sort) : idx + 1,
      enabled: item && item.enabled === 0 ? 0 : 1,
    }))
    if (!contactRows.length) {
      if (shopForm.contactName) {
        contactRows.push({
          id: `legacy-person-${Date.now()}`,
          type: 'PERSON',
          label: defaultContactLabel('PERSON'),
          value: shopForm.contactName,
          copyValue: shopForm.contactName,
          sort: contactRows.length + 1,
          enabled: 1,
        })
      }
      if (shopForm.contactWechat) {
        contactRows.push({
          id: `legacy-wechat-${Date.now()}`,
          type: 'WECHAT',
          label: defaultContactLabel('WECHAT'),
          value: shopForm.contactWechat,
          copyValue: shopForm.contactWechat,
          sort: contactRows.length + 1,
          enabled: 1,
        })
      }
      if (shopForm.contactPhone) {
        contactRows.push({
          id: `legacy-phone-${Date.now()}`,
          type: 'PHONE',
          label: defaultContactLabel('PHONE'),
          value: shopForm.contactPhone,
          copyValue: shopForm.contactPhone,
          sort: contactRows.length + 1,
          enabled: 1,
        })
      }
    }
    shopForm.contacts = contactRows
    applyPageMeta({ route: router.currentRoute.value, profile: shopForm, lang: 'zh-cn' })
  } catch (error) {
    ElMessage.error(error.message || '店铺配置加载失败')
  }
}

const fetchLlmConfig = async () => {
  try {
    const data = await api.llmGetConfig()
    if (!data) return
    llmForm.id = data.id || null
    llmForm.enabled = data.enabled == null ? 0 : data.enabled
    llmForm.provider = data.provider || 'openai-compatible'
    llmForm.baseUrl = data.baseUrl || 'https://api.openai.com/v1'
    llmForm.apiKey = ''
    llmForm.apiKeyMasked = data.apiKeyMasked || ''
    llmForm.model = data.model || 'gpt-4o-mini'
    llmForm.temperature = data.temperature == null ? 0.3 : Number(data.temperature)
    llmForm.maxTokens = data.maxTokens == null ? 800 : Number(data.maxTokens)
    llmForm.targetLang = data.targetLang || 'en'
    llmForm.strictMode = data.strictMode == null ? 0 : data.strictMode
    llmForm.systemPrompt = data.systemPrompt || ''
    llmForm.userPromptTemplate = data.userPromptTemplate || ''
    llmForm.lastTestTime = data.lastTestTime || ''
    llmForm.lastTestStatus = data.lastTestStatus == null ? null : data.lastTestStatus
    llmForm.lastTestMessage = data.lastTestMessage || ''
  } catch (error) {
    ElMessage.error(error.message || 'LLM配置加载失败')
  }
}

const saveLlmConfig = async () => {
  llmSaving.value = true
  try {
    const payload = {
      id: llmForm.id || undefined,
      enabled: llmForm.enabled,
      provider: llmForm.provider,
      baseUrl: llmForm.baseUrl,
      apiKey: llmForm.apiKey ? llmForm.apiKey.trim() : undefined,
      model: llmForm.model,
      temperature: Number(llmForm.temperature),
      maxTokens: Number(llmForm.maxTokens),
      targetLang: llmForm.targetLang,
      strictMode: llmForm.strictMode,
      systemPrompt: llmForm.systemPrompt || undefined,
      userPromptTemplate: llmForm.userPromptTemplate || undefined,
    }
    const data = await api.llmSaveConfig(payload)
    llmForm.id = data && data.id ? data.id : llmForm.id
    llmForm.apiKey = ''
    llmForm.apiKeyMasked = data && data.apiKeyMasked ? data.apiKeyMasked : llmForm.apiKeyMasked
    ElMessage.success('LLM配置保存成功')
    await fetchLlmConfig()
  } catch (error) {
    ElMessage.error(error.message || 'LLM配置保存失败')
  } finally {
    llmSaving.value = false
  }
}

const testLlmConfig = async () => {
  llmTesting.value = true
  try {
    const payload = {
      provider: llmForm.provider,
      baseUrl: llmForm.baseUrl,
      apiKey: llmForm.apiKey ? llmForm.apiKey.trim() : undefined,
      model: llmForm.model,
      temperature: Number(llmForm.temperature),
      maxTokens: Number(llmForm.maxTokens),
      targetLang: llmForm.targetLang,
      systemPrompt: llmForm.systemPrompt || undefined,
      userPromptTemplate: llmForm.userPromptTemplate || undefined,
      title: llmTestInput.title || undefined,
      description: llmTestInput.description || undefined,
    }
    const data = await api.llmTestConfig(payload)
    llmTestResult.value = data || null
    ElMessage.success('LLM测试成功')
    await fetchLlmConfig()
  } catch (error) {
    llmTestResult.value = null
    ElMessage.error(error.message || 'LLM测试失败')
    await fetchLlmConfig()
  } finally {
    llmTesting.value = false
  }
}

const fetchOssConfig = async () => {
  try {
    const data = await api.ossGetConfig()
    if (!data) return
    ossForm.id = data.id || null
    ossForm.enabled = data.enabled == null ? 0 : data.enabled
    ossForm.endpoint = data.endpoint || ''
    ossForm.bucketName = data.bucketName || ''
    ossForm.accessKeyId = data.accessKeyId || ''
    ossForm.accessKeySecret = ''
    ossForm.accessKeySecretMasked = data.accessKeySecretMasked || ''
    ossForm.bucketDomain = data.bucketDomain || ''
    ossForm.lastTestTime = data.lastTestTime || ''
    ossForm.lastTestStatus = data.lastTestStatus == null ? null : data.lastTestStatus
    ossForm.lastTestMessage = data.lastTestMessage || ''
  } catch (error) {
    ElMessage.error(error.message || 'OSS配置加载失败')
  }
}

const saveOssConfig = async () => {
  ossSaving.value = true
  try {
    const payload = {
      id: ossForm.id || undefined,
      enabled: ossForm.enabled,
      endpoint: ossForm.endpoint || undefined,
      bucketName: ossForm.bucketName || undefined,
      accessKeyId: ossForm.accessKeyId || undefined,
      accessKeySecret: ossForm.accessKeySecret ? ossForm.accessKeySecret.trim() : undefined,
      bucketDomain: ossForm.bucketDomain || undefined,
    }
    const data = await api.ossSaveConfig(payload)
    ossForm.id = data && data.id ? data.id : ossForm.id
    ossForm.accessKeySecret = ''
    ossForm.accessKeySecretMasked = data && data.accessKeySecretMasked ? data.accessKeySecretMasked : ossForm.accessKeySecretMasked
    ElMessage.success('OSS配置保存成功')
    await fetchOssConfig()
  } catch (error) {
    ElMessage.error(error.message || 'OSS配置保存失败')
  } finally {
    ossSaving.value = false
  }
}

const testOssConfig = async () => {
  ossTesting.value = true
  try {
    const payload = {
      endpoint: ossForm.endpoint || undefined,
      bucketName: ossForm.bucketName || undefined,
      accessKeyId: ossForm.accessKeyId || undefined,
      accessKeySecret: ossForm.accessKeySecret ? ossForm.accessKeySecret.trim() : undefined,
    }
    const message = await api.ossTestConfig(payload)
    ElMessage.success(message || 'OSS测试成功')
    await fetchOssConfig()
  } catch (error) {
    ElMessage.error(error.message || 'OSS测试失败')
    await fetchOssConfig()
  } finally {
    ossTesting.value = false
  }
}

const fetchProducts = async () => {
  loadingProducts.value = true
  try {
    const data = await api.adminProducts({
      page: pager.page,
      size: pager.size,
      keyword: query.keyword || undefined,
      sku: query.sku || undefined,
      categoryId: query.categoryId || undefined,
      status: query.status === null ? undefined : query.status,
    })
    products.value = data.records || []
    pager.total = Number(data.total || 0)
    pager.page = Number(data.page || pager.page || 1)
    pager.size = Number(data.size || pager.size || 10)
    selectedProductIds.value = []
  } catch (error) {
    ElMessage.error(error.message || '商品加载失败')
  } finally {
    loadingProducts.value = false
  }
}

const onSearch = () => {
  pager.page = 1
  fetchProducts()
}

const openCreate = () => {
  editingProduct.value = null
  dialogVisible.value = true
}

const openEdit = async (row) => {
  try {
    editingProduct.value = await api.adminProductDetail(row.id)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '获取商品详情失败')
  }
}

const removeProduct = async (row) => {
  await ElMessageBox.confirm('确认删除该商品吗？', '提示', { type: 'warning' })
  await api.deleteProduct(row.id)
  ElMessage.success('删除成功')
  fetchProducts()
  fetchStats()
}

const onProductSelectionChange = (rows) => {
  selectedProductIds.value = (rows || []).map((item) => item.id)
}

const batchUpdateCategory = async () => {
  if (!selectedProductIds.value.length) {
    ElMessage.warning('请先勾选商品')
    return
  }
  if (!batchCategoryId.value) {
    ElMessage.warning('请选择目标分类')
    return
  }

  await ElMessageBox.confirm('确认将选中商品批量调整到该分类吗？', '批量分类维护', { type: 'warning' })
  batchUpdating.value = true
  try {
    await api.batchUpdateProductCategory({
      productIds: selectedProductIds.value,
      categoryId: batchCategoryId.value,
    })
    ElMessage.success('批量分类更新成功')
    await fetchProducts()
  } catch (error) {
    ElMessage.error(error.message || '批量更新失败')
  } finally {
    batchUpdating.value = false
  }
}

const resetCategoryForm = () => {
  categoryForm.id = null
  categoryForm.parentId = 0
  categoryForm.name = ''
  categoryForm.enName = ''
  categoryForm.sort = 0
}

const openCreateCategory = () => {
  resetCategoryForm()
  categoryDialogVisible.value = true
}

const openEditCategory = (row) => {
  categoryForm.id = row.id
  categoryForm.parentId = row.parentId || 0
  categoryForm.name = row.name
  categoryForm.enName = row.enName || ''
  categoryForm.sort = row.sort || 0
  categoryDialogVisible.value = true
}

const saveCategory = async () => {
  if (!categoryForm.name.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  categorySaving.value = true
  try {
    await api.saveCategory({
      id: categoryForm.id || undefined,
      parentId: categoryForm.parentId,
      name: categoryForm.name.trim(),
      enName: categoryForm.enName ? categoryForm.enName.trim() : undefined,
      sort: categoryForm.sort || 0,
    })
    ElMessage.success('分类保存成功')
    categoryDialogVisible.value = false
    resetCategoryForm()
    await fetchCategories()
    await fetchStats()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    categorySaving.value = false
  }
}

const removeCategory = async (row) => {
  await ElMessageBox.confirm('确认删除分类 "' + row.name + '" 吗？', '提示', { type: 'warning' })
  await api.deleteCategory(row.id)
  ElMessage.success('分类删除成功')
  await fetchCategories()
  await fetchStats()
}

const pageChange = (current) => {
  pager.page = current
  fetchProducts()
}

const pageSizeChange = (size) => {
  pager.size = Number(size || 10)
  pager.page = 1
  fetchProducts()
}

const statusText = (status) => (status === 1 ? '上架' : '下架')
const topStatusText = (isTop) => (Number(isTop) === 1 ? '已置顶' : '普通')
const formatTags = (tags) => (Array.isArray(tags) && tags.length ? tags.join(' / ') : '-')

const openProductPage = (row) => {
  if (!row || !row.id) return
  const routeData = router.resolve({ path: '/product/' + row.id })
  window.open(routeData.href, '_blank')
}

const toggleTopStatus = async (row) => {
  if (!row || !row.id) return
  const nextTop = Number(row.isTop) === 1 ? 0 : 1
  const actionLabel = nextTop === 1 ? '置顶' : '取消置顶'
  try {
    await ElMessageBox.confirm(`确认${actionLabel}该商品吗？`, '提示', { type: 'warning' })
    await api.updateProductTop(row.id, nextTop)
    ElMessage.success(nextTop === 1 ? '置顶成功' : '已取消置顶')
    await fetchProducts()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || `${actionLabel}失败`)
  }
}

const openCreateVoucherFromProduct = async (row) => {
  activeTab.value = 'orderVouchers'
  await nextTick()
  if (orderVoucherPanelRef.value && orderVoucherPanelRef.value.openCreateFromProduct) {
    orderVoucherPanelRef.value.openCreateFromProduct(row)
  }
}

const isBlank = (value) => !value || !String(value).trim()

const normalizeTagNames = (raw) => {
  if (isBlank(raw)) return ''
  const seen = new Set()
  const list = []
  String(raw)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .forEach((item) => {
      if (seen.has(item)) return
      seen.add(item)
      list.push(item)
    })
  return list.join(',')
}

const normalizePageKey = (raw) => {
  if (isBlank(raw)) return ''
  return String(raw)
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9-_]/g, '')
}

const buildCustomPagesPayload = () => {
  const pages = []
  const keySet = new Set()
  for (let i = 0; i < shopForm.customPages.length; i += 1) {
    const row = shopForm.customPages[i] || {}
    const key = normalizePageKey(row.key)
    const title = String(row.title || '').trim()
    const content = String(row.content || '')
    const hasValue = Boolean(key || title || content.trim())
    if (!hasValue) continue
    if (!key) {
      ElMessage.warning(`第 ${i + 1} 个自定义页面缺少页面Key`)
      return null
    }
    if (keySet.has(key)) {
      ElMessage.warning(`自定义页面Key重复：${key}`)
      return null
    }
    keySet.add(key)
    pages.push({
      key,
      title,
      content,
    })
  }
  return {
    pages,
    keySet,
  }
}

const normalizeMetaPageKey = (raw) => {
  const value = String(raw || '').trim().toLowerCase()
  if (!value) return ''
  if (value.startsWith('page:')) return 'page:' + normalizePageKey(value.slice(5))
  return normalizePageKey(value)
}

const buildPageMetasPayload = (pageKeySet) => {
  const metas = []
  const keySet = new Set()
  for (let i = 0; i < shopForm.pageMetas.length; i += 1) {
    const row = shopForm.pageMetas[i] || {}
    const pageKey = normalizeMetaPageKey(row.pageKey)
    const langCode = String(row.langCode || '').trim().replace(/_/g, '-').toLowerCase()
    const title = String(row.title || '').trim()
    const description = String(row.description || '').trim()
    const hasValue = Boolean(pageKey || langCode || title || description)
    if (!hasValue) continue
    if (!pageKey) {
      ElMessage.warning(`第 ${i + 1} 个页面SEO配置缺少页面`)
      return null
    }
    if (!langCode) {
      ElMessage.warning(`第 ${i + 1} 个页面SEO配置缺少语言`)
      return null
    }
    if (pageKey.startsWith('page:')) {
      const customKey = pageKey.slice(5)
      if (!pageKeySet.has(customKey)) {
        ElMessage.warning(`第 ${i + 1} 个页面SEO配置关联的自定义页面不存在：${customKey}`)
        return null
      }
    }
    const uniqueKey = `${pageKey}|${langCode}`
    if (keySet.has(uniqueKey)) {
      ElMessage.warning(`页面SEO配置重复：${pageKey} / ${langCode}`)
      return null
    }
    keySet.add(uniqueKey)
    metas.push({
      pageKey,
      langCode,
      title,
      description,
    })
  }
  return metas
}

const buildMenuItemsPayload = (pageKeySet) => {
  const menus = []
  for (let i = 0; i < shopForm.menuItems.length; i += 1) {
    const row = shopForm.menuItems[i] || {}
    const name = String(row.name || '').trim()
    const id = String(row.id || `menu-${Date.now()}-${i}`).trim()
    const parentId = String(row.parentId || '').trim()
    const targetType = String(row.targetType || 'CATEGORY').trim().toUpperCase()
    const categoryId = row.categoryId ? String(row.categoryId).trim() : ''
    const tagName = normalizeTagNames(row.tagName)
    const customPageKey = normalizePageKey(row.customPageKey)
    const hasValue = Boolean(name || categoryId || tagName || customPageKey)
    if (!hasValue) continue
    if (!name) {
      ElMessage.warning(`第 ${i + 1} 个菜单项缺少名称`)
      return null
    }
    if (id && parentId && id === parentId) {
      ElMessage.warning(`第 ${i + 1} 个菜单项父级不能是自己`)
      return null
    }
    if (!['GROUP', 'CATEGORY', 'TAG', 'CATEGORY_TAG', 'CUSTOM_PAGE'].includes(targetType)) {
      ElMessage.warning(`第 ${i + 1} 个菜单项目标类型不合法`)
      return null
    }
    if (targetType === 'CATEGORY' && !categoryId) {
      ElMessage.warning(`第 ${i + 1} 个菜单项需选择分类`)
      return null
    }
    if (targetType === 'TAG' && !tagName) {
      ElMessage.warning(`第 ${i + 1} 个菜单项需填写标签`)
      return null
    }
    if (targetType === 'CATEGORY_TAG' && (!categoryId || !tagName)) {
      ElMessage.warning(`第 ${i + 1} 个菜单项需同时配置分类和标签`)
      return null
    }
    if (targetType === 'CUSTOM_PAGE') {
      if (!customPageKey) {
        ElMessage.warning(`第 ${i + 1} 个菜单项需关联自定义页面`)
        return null
      }
      if (!pageKeySet.has(customPageKey)) {
        ElMessage.warning(`第 ${i + 1} 个菜单项关联的页面不存在：${customPageKey}`)
        return null
      }
    }

    menus.push({
      id,
      parentId,
      name,
      targetType,
      categoryId: targetType === 'CATEGORY' || targetType === 'CATEGORY_TAG' ? categoryId : null,
      tagName: targetType === 'TAG' || targetType === 'CATEGORY_TAG' ? tagName : '',
      customPageKey: targetType === 'CUSTOM_PAGE' ? customPageKey : '',
      sort: row.sort == null ? i + 1 : Number(row.sort),
      enabled: row.enabled === 0 ? 0 : 1,
    })
  }
  return menus
}

const buildMenuPageConfigsPayload = (menuPayload) => {
  const configs = []
  const menuMap = new Map((menuPayload || []).map((item) => [String(item.id || ''), item]))
  const seenMenuIds = new Set()
  for (let i = 0; i < shopForm.menuPageConfigs.length; i += 1) {
    const row = shopForm.menuPageConfigs[i] || {}
    const menuId = String(row.menuId || '').trim()
    const rawBlocks = Array.isArray(row.blocks) ? row.blocks : []
    const hasValue = Boolean(menuId || rawBlocks.length)
    if (!hasValue) continue
    if (!menuId) {
      ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位缺少关联菜单`)
      return null
    }
    if (seenMenuIds.has(menuId)) {
      ElMessage.warning('菜单顶部活动位重复关联同一个菜单')
      return null
    }
    const menuItem = menuMap.get(menuId)
    if (!menuItem) {
      ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位关联菜单不存在`)
      return null
    }
    if (!['CATEGORY', 'TAG', 'CATEGORY_TAG'].includes(String(menuItem.targetType || '').toUpperCase())) {
      ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位只能绑定商品列表类菜单`)
      return null
    }
    const blocks = []
    for (let j = 0; j < rawBlocks.length; j += 1) {
      const block = rawBlocks[j] || {}
      const type = normalizeMenuPageBlockType(block.type)
      const title = String(block.title || '').trim()
      const subTitle = String(block.subTitle || '').trim()
      const imageUrl = String(block.imageUrl || '').trim()
      const content = String(block.content || '').trim()
      const buttonText = String(block.buttonText || '').trim()
      const linkType = normalizeMenuPageLinkType(block.linkType)
      const linkValue = String(block.linkValue || '').trim()
      const startTime = String(block.startTime || '').trim()
      const endTime = String(block.endTime || '').trim()
      const hasBlockValue = Boolean(title || subTitle || imageUrl || content || buttonText || linkValue || startTime || endTime)
      if (!hasBlockValue) continue
      if (type === 'IMAGE' && !imageUrl) {
        ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位的第 ${j + 1} 个单图模块缺少图片`)
        return null
      }
      if (type === 'RICH_TEXT' && !title && !content && !imageUrl) {
        ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位的第 ${j + 1} 个图文模块内容为空`)
        return null
      }
      if (type === 'NOTICE' && !title && !content) {
        ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位的第 ${j + 1} 个公告模块内容为空`)
        return null
      }
      if (type === 'COUNTDOWN' && !endTime) {
        ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位的第 ${j + 1} 个倒计时模块缺少结束时间`)
        return null
      }
      if (linkType !== 'NONE' && !linkValue) {
        ElMessage.warning(`第 ${i + 1} 个菜单顶部活动位的第 ${j + 1} 个模块缺少跳转地址`)
        return null
      }
      blocks.push({
        id: String(block.id || `menu-block-${Date.now()}-${i}-${j}`),
        type,
        title,
        subTitle,
        imageUrl,
        content,
        buttonText,
        linkType,
        linkValue,
        startTime,
        endTime,
        sort: block.sort == null ? j + 1 : Number(block.sort),
        enabled: block.enabled === 0 ? 0 : 1,
      })
    }
    configs.push({
      menuId,
      enabled: row.enabled === 0 ? 0 : 1,
      blocks,
    })
    seenMenuIds.add(menuId)
  }
  return configs
}

const buildContactsPayload = () => {
  const contacts = []
  for (let i = 0; i < shopForm.contacts.length; i += 1) {
    const row = shopForm.contacts[i] || {}
    const type = normalizeContactType(row.type)
    const label = String(row.label || '').trim() || defaultContactLabel(type)
    const value = String(row.value || '').trim()
    const copyValue = String(row.copyValue || '').trim()
    const hasValue = Boolean(label || value || copyValue)
    if (!hasValue) continue
    if (!value) {
      ElMessage.warning(`第 ${i + 1} 个联系方式缺少内容`)
      return null
    }
    contacts.push({
      id: String(row.id || `contact-${Date.now()}-${i}`),
      type,
      label,
      value,
      copyValue: copyValue || value,
      sort: row.sort == null ? i + 1 : Number(row.sort),
      enabled: row.enabled === 0 ? 0 : 1,
    })
  }
  return contacts
}

const menuParentOptions = (currentId) => {
  const id = String(currentId || '').trim()
  return (shopForm.menuItems || [])
    .filter((item) => String(item.id || '').trim() && String(item.id || '').trim() !== id)
    .map((item) => ({
      value: String(item.id || '').trim(),
      label: String(item.name || '').trim() || String(item.id || '').trim(),
    }))
}

const saveShopProfile = async () => {
  const pageBuildResult = buildCustomPagesPayload()
  if (!pageBuildResult) return
  const pageMetasPayload = buildPageMetasPayload(pageBuildResult.keySet)
  if (!pageMetasPayload) return
  const menuPayload = buildMenuItemsPayload(pageBuildResult.keySet)
  if (!menuPayload) return
  const menuPageConfigsPayload = buildMenuPageConfigsPayload(menuPayload)
  if (!menuPageConfigsPayload) return
  const contactsPayload = buildContactsPayload()
  if (!contactsPayload) return

  shopSaving.value = true
  try {
    await api.saveShopProfile({
      ...shopForm,
      contacts: contactsPayload,
      menuItems: menuPayload,
      menuPageConfigs: menuPageConfigsPayload,
      customPages: pageBuildResult.pages,
      pageMetas: pageMetasPayload,
    })
    ElMessage.success('店铺配置已保存')
    await fetchShopProfile()
  } catch (error) {
    ElMessage.error(error.message || '店铺配置保存失败')
  } finally {
    shopSaving.value = false
  }
}

const addMenuItem = () => {
  shopForm.menuItems.push({
    id: 'menu-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    parentId: '',
    name: '',
    targetType: 'CATEGORY',
    categoryId: null,
    tagName: '',
    customPageKey: '',
    sort: shopForm.menuItems.length + 1,
    enabled: 1,
  })
}

const addMenuPageConfig = () => {
  shopForm.menuPageConfigs.push({
    menuId: '',
    enabled: 1,
    blocks: [],
  })
}

const removeMenuPageConfig = (index) => {
  shopForm.menuPageConfigs.splice(index, 1)
}

const addMenuPageBlock = (config) => {
  if (!config) return
  const blocks = Array.isArray(config.blocks) ? config.blocks : []
  blocks.push({
    id: 'menu-block-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    type: 'IMAGE',
    title: '',
    subTitle: '',
    imageUrl: '',
    content: '',
    buttonText: '',
    linkType: 'NONE',
    linkValue: '',
    startTime: '',
    endTime: '',
    sort: blocks.length + 1,
    enabled: 1,
  })
  config.blocks = blocks
}

const removeMenuPageBlock = (config, blockIndex) => {
  if (!config || !Array.isArray(config.blocks)) return
  config.blocks.splice(blockIndex, 1)
}

const uploadMenuPageBlockImage = async (uploadFile, block) => {
  if (!uploadFile || !uploadFile.raw || !block) return
  const blockId = String(block.id || Date.now())
  menuPageBlockUploading[blockId] = true
  try {
    const result = await api.uploadImages([uploadFile.raw])
    const urls = Array.isArray(result?.urls) ? result.urls : []
    if (!urls.length) {
      throw new Error('上传结果为空')
    }
    block.imageUrl = urls[0]
    ElMessage.success('活动图上传成功')
  } catch (error) {
    ElMessage.error(error.message || '活动图上传失败')
  } finally {
    menuPageBlockUploading[blockId] = false
  }
}

const addContactItem = () => {
  shopForm.contacts.push({
    id: 'contact-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    type: 'WECHAT',
    label: defaultContactLabel('WECHAT'),
    value: '',
    copyValue: '',
    sort: shopForm.contacts.length + 1,
    enabled: 1,
  })
}

const removeContactItem = (index) => {
  shopForm.contacts.splice(index, 1)
}

const onContactTypeChange = (item) => {
  if (!item) return
  item.type = normalizeContactType(item.type)
  if (!String(item.label || '').trim()) {
    item.label = defaultContactLabel(item.type)
  }
}

const removeMenuItem = (index) => {
  shopForm.menuItems.splice(index, 1)
}

const addCustomPage = () => {
  shopForm.customPages.push({
    key: '',
    title: '',
    content: '',
  })
}

const removeCustomPage = (index) => {
  shopForm.customPages.splice(index, 1)
}

const addPageMeta = () => {
  shopForm.pageMetas.push({
    id: 'meta-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    pageKey: 'home',
    langCode: 'en-us',
    title: '',
    description: '',
  })
}

const removePageMeta = (index) => {
  shopForm.pageMetas.splice(index, 1)
}

const uploadShopImage = async (uploadFile, field) => {
  if (!uploadFile || !uploadFile.raw) return
  shopUploading[field] = true
  try {
    const result = await api.uploadImages([uploadFile.raw])
    const urls = result.urls || []
    if (!urls.length) {
      throw new Error('上传结果为空')
    }
    const uploadedUrl = urls[0]
    if (field === 'logo') {
      shopForm.shopLogo = uploadedUrl
    } else {
      shopForm.heroBanner = uploadedUrl
    }

    await api.saveShopProfile(field === 'logo' ? { shopLogo: uploadedUrl } : { heroBanner: uploadedUrl })
    await fetchShopProfile()
    ElMessage.success('图片上传并保存成功')
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    shopUploading[field] = false
  }
}

const logout = async () => {
  try {
    await api.adminLogout()
  } catch (_error) {
  } finally {
    clearAdminAuth()
    router.replace('/admin/login')
  }
}

const refreshAll = async () => {
  await Promise.all([fetchStats(true), fetchCategories(), fetchProducts(), fetchShopProfile(), fetchLlmConfig(), fetchOssConfig(), fetchAnalyticsOverview()])
}

onMounted(refreshAll)
</script>

<template>
  <div class="min-h-screen bg-zinc-100 pb-10">
    <section class="mx-auto max-w-7xl px-3 py-4 md:px-6 md:py-8">
      <div class="rounded-3xl bg-gradient-to-r from-zinc-900 to-zinc-700 p-5 text-white md:p-7">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.25em] text-amber-300">Dashboard</p>
            <h1 class="mt-2 text-2xl font-black md:text-4xl">商品相册管理后台</h1>
            <p class="mt-2 text-sm text-zinc-300">商品一键发布 · 分类管理 · 图片空间统计</p>
          </div>
          <div class="flex items-center gap-2">
            <span class="hidden text-xs text-zinc-300 md:inline">当前登录：{{ adminName }}</span>
            <el-button type="warning" round @click="router.push('/')">前台预览</el-button>
            <el-button plain round @click="logout">退出</el-button>
          </div>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-2 gap-3 md:grid-cols-5">
        <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
          <p class="text-xs text-zinc-500">商品总数</p>
          <p class="mt-2 text-2xl font-black text-zinc-900">{{ stats.totalProducts }}</p>
        </div>
        <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
          <p class="text-xs text-zinc-500">分类总数</p>
          <p class="mt-2 text-2xl font-black text-zinc-900">{{ stats.totalCategories }}</p>
        </div>
        <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
          <p class="text-xs text-zinc-500">图片总数</p>
          <p class="mt-2 text-2xl font-black text-zinc-900">{{ stats.totalImages }}</p>
        </div>
        <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
          <p class="text-xs text-zinc-500">今日发布</p>
          <p class="mt-2 text-2xl font-black text-zinc-900">{{ stats.publishedToday }}</p>
        </div>
        <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
          <p class="text-xs text-zinc-500">图片空间 (GB)</p>
          <p class="mt-2 text-2xl font-black text-zinc-900">{{ imageStorageGb }}</p>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="mt-6">
        <el-tab-pane label="商品管理" name="products">
          <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-5">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-6">
              <el-input v-model="query.keyword" placeholder="关键词" clearable @keyup.enter="onSearch" />
              <el-input v-model="query.sku" placeholder="货号" clearable @keyup.enter="onSearch" />
              <el-select v-model="query.categoryId" clearable placeholder="分类">
                <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="query.status" clearable placeholder="状态">
                <el-option :value="1" label="上架" />
                <el-option :value="0" label="下架" />
              </el-select>
              <el-button type="primary" @click="onSearch">搜索</el-button>
              <el-button type="warning" @click="openCreate">一键发布</el-button>
            </div>

            <div class="mt-3 flex flex-wrap items-center gap-2 rounded-xl bg-zinc-50 p-3 ring-1 ring-zinc-100">
              <span class="text-sm text-zinc-500">批量分类维护</span>
              <el-select v-model="batchCategoryId" filterable clearable placeholder="选择目标分类" class="w-[260px] max-w-full">
                <el-option v-for="item in categoryOptions.filter((x) => x.value !== 0)" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button
                type="primary"
                :disabled="!selectedProductIds.length || !batchCategoryId"
                :loading="batchUpdating"
                @click="batchUpdateCategory"
              >
                应用到已选 {{ selectedProductIds.length }} 项
              </el-button>
            </div>

            <el-table :data="products" class="mt-4" stripe v-loading="loadingProducts" @selection-change="onProductSelectionChange">
              <el-table-column type="selection" width="52" />
              <el-table-column label="封面" width="100">
                <template #default="{ row }">
                  <img :src="row.coverImage" alt="" class="h-14 w-14 rounded-lg object-cover" />
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" min-width="240" />
              <el-table-column prop="categoryName" label="分类" min-width="140" />
              <el-table-column prop="sku" label="货号" width="120" />
              <el-table-column label="标签" min-width="280">
                <template #default="{ row }">
                  <div class="space-y-1 text-xs leading-5">
                    <div>
                      <span class="text-zinc-500">中：</span>
                      <span class="text-zinc-800">{{ formatTags(row.zhTags || row.tags) }}</span>
                    </div>
                    <div>
                      <span class="text-zinc-500">EN：</span>
                      <span class="text-zinc-800">{{ formatTags(row.enTags) }}</span>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="价格" width="110">
                <template #default="{ row }">¥ {{ row.price }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="置顶" width="100">
                <template #default="{ row }">
                  <el-tag :type="Number(row.isTop) === 1 ? 'danger' : 'info'">{{ topStatusText(row.isTop) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="320" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openCreateVoucherFromProduct(row)">创建凭证</el-button>
                  <el-button link type="success" @click="openProductPage(row)">查看页面</el-button>
                  <el-button link type="warning" @click="toggleTopStatus(row)">
                    {{ Number(row.isTop) === 1 ? '取消置顶' : '置顶' }}
                  </el-button>
                  <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeProduct(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="mt-4 flex justify-end">
              <el-pagination
                background
                layout="sizes, prev, pager, next, total"
                :current-page="pager.page"
                :page-size="pager.size"
                :page-sizes="[10, 20, 50]"
                :total="pager.total"
                @current-change="pageChange"
                @size-change="pageSizeChange"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="订单凭证" name="orderVouchers">
          <OrderVoucherAdminPanel ref="orderVoucherPanelRef" />
        </el-tab-pane>

        <el-tab-pane label="访问统计" name="analytics">
          <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-5" v-loading="analyticsLoading">
            <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
              <p class="text-sm text-zinc-500">仅统计前台页面浏览，IP 已脱敏保存</p>
              <div class="flex items-center gap-2">
                <el-select v-model="analyticsDays" class="w-[120px]">
                  <el-option :value="7" label="近7天" />
                  <el-option :value="30" label="近30天" />
                  <el-option :value="90" label="近90天" />
                </el-select>
                <el-button type="primary" @click="fetchAnalyticsOverview">刷新</el-button>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
              <div class="rounded-2xl bg-zinc-50 p-4 ring-1 ring-zinc-100">
                <p class="text-xs text-zinc-500">PV</p>
                <p class="mt-2 text-2xl font-black text-zinc-900">{{ analyticsOverview.pv }}</p>
              </div>
              <div class="rounded-2xl bg-zinc-50 p-4 ring-1 ring-zinc-100">
                <p class="text-xs text-zinc-500">UV</p>
                <p class="mt-2 text-2xl font-black text-zinc-900">{{ analyticsOverview.uv }}</p>
              </div>
              <div class="rounded-2xl bg-zinc-50 p-4 ring-1 ring-zinc-100">
                <p class="text-xs text-zinc-500">平均停留时长</p>
                <p class="mt-2 text-2xl font-black text-zinc-900">{{ formatStaySeconds(analyticsOverview.avgStaySeconds) }}</p>
              </div>
            </div>

            <div class="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
              <div>
                <p class="mb-2 text-sm font-semibold text-zinc-700">每日趋势</p>
                <el-table :data="analyticsOverview.trend" stripe size="small">
                  <el-table-column prop="statDate" label="日期" width="120" />
                  <el-table-column prop="pv" label="PV" width="90" />
                  <el-table-column prop="uv" label="UV" width="90" />
                  <el-table-column label="平均停留">
                    <template #default="{ row }">{{ formatStaySeconds(row.avgStaySeconds) }}</template>
                  </el-table-column>
                </el-table>
              </div>
              <div>
                <p class="mb-2 text-sm font-semibold text-zinc-700">页面 TOP10</p>
                <el-table :data="analyticsOverview.topPages" stripe size="small">
                  <el-table-column prop="pagePath" label="页面路径" min-width="180" />
                  <el-table-column prop="pv" label="PV" width="90" />
                  <el-table-column prop="uv" label="UV" width="90" />
                  <el-table-column label="平均停留" width="120">
                    <template #default="{ row }">{{ formatStaySeconds(row.avgStaySeconds) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="分类管理" name="categories">
          <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-5">
            <div class="mb-4 flex justify-between">
              <p class="text-sm text-zinc-500">支持多级分类，按层级展示</p>
              <el-button type="primary" @click="openCreateCategory">新增分类</el-button>
            </div>
            <el-table :data="flatCategoryRows" stripe>
              <el-table-column label="分类名称" min-width="260">
                <template #default="{ row }">
                  <span :style="{ paddingLeft: (row.level - 1) * 20 + 'px' }">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="parentName" label="父级分类" min-width="160" />
              <el-table-column prop="level" label="层级" width="80" />
              <el-table-column prop="sort" label="排序" width="80" />
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openEditCategory(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="店铺配置" name="shop">
          <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="mb-4 text-sm text-zinc-500">配置前台店铺信息：店名、图标、头图、域名、公告与联系方式</p>
            <el-form label-position="top">
              <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                <el-form-item label="店铺名称">
                  <el-input v-model="shopForm.shopName" placeholder="例如：白鲸•CELINE" />
                </el-form-item>

                <el-form-item label="语言入口标签">
                  <el-input v-model="shopForm.languageLabel" placeholder="例如：EN" />
                </el-form-item>

                <el-form-item label="域名">
                  <el-input v-model="shopForm.domain" placeholder="例如：shop.example.com" />
                </el-form-item>

                <el-form-item label="主题色">
                  <el-input v-model="shopForm.themeColor" placeholder="例如：#10b981" />
                </el-form-item>

                <el-form-item label="搜索引擎抓取">
                  <el-switch
                    v-model="shopForm.blockSearchEngineCrawl"
                    :active-value="1"
                    :inactive-value="0"
                    inline-prompt
                    active-text="禁止抓取"
                    inactive-text="允许抓取"
                  />
                </el-form-item>

                <el-form-item label="版权信息">
                  <el-input v-model="shopForm.copyrightText" placeholder="例如：© 2026 White Whale" />
                </el-form-item>
              </div>

              <div class="mt-3 rounded-2xl border border-zinc-200 bg-zinc-50 p-3">
                <div class="mb-2 flex items-center justify-between">
                  <p class="text-sm font-semibold text-zinc-700">联系方式配置（前台 Contact）</p>
                  <el-button type="primary" link @click="addContactItem">新增联系方式</el-button>
                </div>
                <div v-if="!shopForm.contacts.length" class="py-2 text-xs text-zinc-400">暂无联系方式，可新增邮箱/WhatsApp/微信等</div>
                <div v-for="(item, index) in shopForm.contacts" :key="item.id || index" class="mb-2 rounded-xl border border-zinc-200 bg-white p-3">
                  <div class="grid grid-cols-1 gap-2 md:grid-cols-6">
                    <el-select v-model="item.type" placeholder="类型" @change="() => onContactTypeChange(item)">
                      <el-option v-for="opt in contactTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                    <el-input v-model="item.label" placeholder="展示名称，例如：WhatsApp" />
                    <el-input v-model="item.value" :placeholder="contactValuePlaceholder(item.type)" />
                    <el-input v-model="item.copyValue" placeholder="复制内容（留空则复制联系方式内容）" />
                    <el-input-number v-model="item.sort" :min="1" :precision="0" class="w-full" />
                    <el-switch v-model="item.enabled" :active-value="1" :inactive-value="0" inline-prompt active-text="启用" inactive-text="停用" />
                  </div>
                  <div class="mt-2 flex justify-end">
                    <el-button link type="danger" @click="removeContactItem(index)">删除</el-button>
                  </div>
                </div>
              </div>

              <el-form-item label="店铺公告">
                <el-input v-model="shopForm.announcement" type="textarea" :rows="3" maxlength="255" show-word-limit />
              </el-form-item>

              <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                <el-form-item label="店铺图标（头像）">
                  <div class="flex items-center gap-3">
                    <img
                      v-if="shopForm.shopLogo"
                      :src="shopForm.shopLogo"
                      class="h-16 w-16 rounded-full border border-zinc-200 object-cover"
                    />
                    <div v-else class="grid h-16 w-16 place-items-center rounded-full border border-dashed border-zinc-300 text-xs text-zinc-400">
                      无图
                    </div>
                    <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" @change="(f) => uploadShopImage(f, 'logo')">
                      <el-button :loading="shopUploading.logo">上传图标</el-button>
                    </el-upload>
                  </div>
                </el-form-item>

                <el-form-item label="店铺大 Banner">
                  <div class="flex items-center gap-3">
                    <img
                      v-if="shopForm.heroBanner"
                      :src="shopForm.heroBanner"
                      class="h-16 w-28 rounded-lg border border-zinc-200 object-cover"
                    />
                    <div v-else class="grid h-16 w-28 place-items-center rounded-lg border border-dashed border-zinc-300 text-xs text-zinc-400">
                      无图
                    </div>
                    <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" @change="(f) => uploadShopImage(f, 'banner')">
                      <el-button :loading="shopUploading.banner">上传 Banner</el-button>
                    </el-upload>
                  </div>
                </el-form-item>
              </div>

              <div class="mt-3 rounded-2xl border border-zinc-200 bg-zinc-50 p-3">
                <div class="mb-2 flex items-center justify-between">
                  <p class="text-sm font-semibold text-zinc-700">前台展示菜单（与分类分离）</p>
                  <el-button type="primary" link @click="addMenuItem">新增菜单项</el-button>
                </div>
                <div v-if="!shopForm.menuItems.length" class="py-2 text-xs text-zinc-400">暂无菜单项，可新增</div>
                <div v-for="(item, index) in shopForm.menuItems" :key="item.id || index" class="mb-2 rounded-xl border border-zinc-200 bg-white p-3">
                  <div class="grid grid-cols-1 gap-2 md:grid-cols-7">
                    <el-input v-model="item.name" placeholder="菜单名称，例如：包包" />
                    <el-select v-model="item.parentId" clearable placeholder="父级菜单（可选）">
                      <el-option label="顶级菜单" value="" />
                      <el-option v-for="opt in menuParentOptions(item.id)" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                    <el-select v-model="item.targetType" placeholder="目标类型">
                      <el-option v-for="opt in menuTargetOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                    <el-select v-if="item.targetType === 'CATEGORY' || item.targetType === 'CATEGORY_TAG'" v-model="item.categoryId" filterable clearable placeholder="关联分类">
                      <el-option
                        v-for="opt in categoryOptions.filter((x) => x.value !== 0)"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                    <el-input
                      v-if="item.targetType === 'TAG' || item.targetType === 'CATEGORY_TAG'"
                      v-model="item.tagName"
                      placeholder="标签，多个用逗号分隔"
                    />
                    <el-select v-if="item.targetType === 'CUSTOM_PAGE'" v-model="item.customPageKey" clearable placeholder="关联自定义页面">
                      <el-option v-for="p in shopForm.customPages" :key="p.key || p.title" :label="p.title || p.key || '未命名页面'" :value="p.key" />
                    </el-select>
                    <el-input-number v-model="item.sort" :min="1" :precision="0" class="w-full" />
                    <el-switch v-model="item.enabled" :active-value="1" :inactive-value="0" inline-prompt active-text="启用" inactive-text="停用" />
                  </div>
                  <div class="mt-2 flex justify-end">
                    <el-button link type="danger" @click="removeMenuItem(index)">删除</el-button>
                  </div>
                </div>
              </div>

              <div class="mt-3 rounded-2xl border border-zinc-200 bg-zinc-50 p-3">
                <div class="mb-2 flex items-center justify-between">
                  <div>
                    <p class="text-sm font-semibold text-zinc-700">菜单顶部活动位</p>
                    <p class="text-xs text-zinc-400">配置后会展示在对应菜单商品页顶部，商品列表仍按原菜单逻辑展示</p>
                  </div>
                  <el-button type="primary" link @click="addMenuPageConfig">新增菜单活动位</el-button>
                </div>
                <div v-if="!shopForm.menuPageConfigs.length" class="py-2 text-xs text-zinc-400">暂无菜单顶部活动位配置，可新增</div>
                <div v-for="(config, configIndex) in shopForm.menuPageConfigs" :key="config.menuId || configIndex" class="mb-3 rounded-xl border border-zinc-200 bg-white p-3">
                  <div class="grid grid-cols-1 gap-2 md:grid-cols-[minmax(0,2fr)_120px_auto] md:items-center">
                    <el-select v-model="config.menuId" filterable clearable placeholder="选择绑定菜单">
                      <el-option v-for="opt in menuPageMenuOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                    <el-switch v-model="config.enabled" :active-value="1" :inactive-value="0" inline-prompt active-text="启用" inactive-text="停用" />
                    <div class="flex justify-end">
                      <el-button link type="danger" @click="removeMenuPageConfig(configIndex)">删除活动位</el-button>
                    </div>
                  </div>

                  <div class="mt-3 rounded-xl border border-dashed border-zinc-200 bg-zinc-50 p-3">
                    <div class="mb-2 flex items-center justify-between">
                      <p class="text-sm font-semibold text-zinc-700">模块列表</p>
                      <el-button type="primary" link @click="addMenuPageBlock(config)">新增模块</el-button>
                    </div>
                    <div v-if="!config.blocks || !config.blocks.length" class="py-2 text-xs text-zinc-400">暂无模块，可新增单图、图文、公告或倒计时活动</div>
                    <div v-for="(block, blockIndex) in config.blocks" :key="block.id || blockIndex" class="mb-3 rounded-xl border border-zinc-200 bg-white p-3">
                      <div class="grid grid-cols-1 gap-2 md:grid-cols-[180px_120px_120px_auto] md:items-center">
                        <el-select v-model="block.type" placeholder="模块类型">
                          <el-option v-for="opt in menuPageBlockTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                        </el-select>
                        <el-input-number v-model="block.sort" :min="1" :precision="0" class="w-full" />
                        <el-switch v-model="block.enabled" :active-value="1" :inactive-value="0" inline-prompt active-text="启用" inactive-text="停用" />
                        <div class="flex justify-end">
                          <el-button link type="danger" @click="removeMenuPageBlock(config, blockIndex)">删除模块</el-button>
                        </div>
                      </div>

                      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <el-input v-model="block.title" placeholder="主标题，例如：Father's Day Belt Event" />
                        <el-input v-model="block.subTitle" placeholder="副标题（可选）" />
                      </div>

                      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div>
                          <div class="mb-2 flex items-center gap-3">
                            <img
                              v-if="block.imageUrl"
                              :src="block.imageUrl"
                              class="h-16 w-28 rounded-lg border border-zinc-200 object-cover"
                            />
                            <div v-else class="grid h-16 w-28 place-items-center rounded-lg border border-dashed border-zinc-300 text-xs text-zinc-400">
                              无图
                            </div>
                            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" @change="(f) => uploadMenuPageBlockImage(f, block)">
                              <el-button :loading="!!menuPageBlockUploading[String(block.id || '')]">上传活动图</el-button>
                            </el-upload>
                          </div>
                          <el-input v-model="block.imageUrl" placeholder="图片地址（也可直接粘贴 URL）" />
                        </div>
                        <el-input v-model="block.buttonText" placeholder="按钮文字（可选，例如：Shop Now）" />
                      </div>

                      <div class="mt-3">
                        <el-input v-model="block.content" type="textarea" :rows="block.type === 'NOTICE' ? 2 : 4" placeholder="正文内容 / 公告内容" />
                      </div>

                      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-3">
                        <el-select v-model="block.linkType" placeholder="跳转方式">
                          <el-option v-for="opt in menuPageLinkTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                        </el-select>
                        <el-input
                          v-model="block.linkValue"
                          :placeholder="block.linkType === 'INTERNAL' ? '站内路径，例如 /product/123 或 /page/about' : '站外链接，例如 https://example.com'"
                        />
                        <el-date-picker
                          v-if="block.type === 'COUNTDOWN'"
                          v-model="block.endTime"
                          type="datetime"
                          format="YYYY-MM-DD HH:mm:ss"
                          value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="结束时间"
                          class="w-full"
                        />
                        <div v-else class="hidden md:block"></div>
                      </div>

                      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <el-date-picker
                          v-model="block.startTime"
                          type="datetime"
                          format="YYYY-MM-DD HH:mm:ss"
                          value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="开始时间（可选）"
                          class="w-full"
                        />
                        <el-date-picker
                          v-if="block.type !== 'COUNTDOWN'"
                          v-model="block.endTime"
                          type="datetime"
                          format="YYYY-MM-DD HH:mm:ss"
                          value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="结束时间（可选）"
                          class="w-full"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="mt-3 rounded-2xl border border-zinc-200 bg-zinc-50 p-3">
                <div class="mb-2 flex items-center justify-between">
                  <p class="text-sm font-semibold text-zinc-700">自定义页面</p>
                  <el-button type="primary" link @click="addCustomPage">新增页面</el-button>
                </div>
                <div v-if="!shopForm.customPages.length" class="py-2 text-xs text-zinc-400">暂无自定义页面，可新增</div>
                <div v-for="(page, index) in shopForm.customPages" :key="page.key || index" class="mb-2 rounded-xl border border-zinc-200 bg-white p-3">
                  <div class="grid grid-cols-1 gap-2 md:grid-cols-2">
                    <el-input v-model="page.key" placeholder="页面Key（路由标识，例如 about-us）" />
                    <el-input v-model="page.title" placeholder="页面标题" />
                  </div>
                  <div class="mt-2">
                    <el-input v-model="page.content" type="textarea" :rows="5" placeholder="页面内容，支持纯文本" />
                  </div>
                  <div class="mt-2 flex justify-end">
                    <el-button link type="danger" @click="removeCustomPage(index)">删除</el-button>
                  </div>
                </div>
              </div>

              <div class="mt-3 rounded-2xl border border-zinc-200 bg-zinc-50 p-3">
                <div class="mb-2 flex items-center justify-between">
                  <p class="text-sm font-semibold text-zinc-700">页面 Title / Description（支持多语言）</p>
                  <el-button type="primary" link @click="addPageMeta">新增页面SEO配置</el-button>
                </div>
                <div v-if="!shopForm.pageMetas.length" class="py-2 text-xs text-zinc-400">暂无页面SEO配置，可按页面和语言新增</div>
                <div v-for="(meta, index) in shopForm.pageMetas" :key="meta.id || index" class="mb-2 rounded-xl border border-zinc-200 bg-white p-3">
                  <div class="grid grid-cols-1 gap-2 md:grid-cols-4">
                    <el-select v-model="meta.pageKey" filterable allow-create default-first-option placeholder="页面">
                      <el-option v-for="opt in pageMetaOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                      <el-option v-for="p in shopForm.customPages" :key="'page:' + p.key" :label="'自定义页面：' + (p.title || p.key)" :value="'page:' + p.key" />
                    </el-select>
                    <el-input v-model="meta.langCode" placeholder="语言，例如 en-us / zh-cn" />
                    <el-input v-model="meta.title" placeholder="Title，支持 {{shopName}} / {{productTitle}} / {{pageTitle}}" />
                    <el-input v-model="meta.description" placeholder="Description" />
                  </div>
                  <div class="mt-2 flex justify-end">
                    <el-button link type="danger" @click="removePageMeta(index)">删除</el-button>
                  </div>
                </div>
              </div>
            </el-form>

            <div class="mt-4 flex justify-end">
              <el-button type="primary" :loading="shopSaving" @click="saveShopProfile">保存店铺配置</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="LLM配置" name="llm">
          <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="mb-4 text-sm text-zinc-500">
              配置后，商品发布和中间库导入正式库时，产品标题/描述会通过该大模型进行英文总结翻译。
            </p>
            <el-form label-position="top">
              <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                <el-form-item label="启用">
                  <el-switch v-model="llmForm.enabled" :active-value="1" :inactive-value="0" />
                </el-form-item>
                <el-form-item label="Provider">
                  <el-input v-model="llmForm.provider" placeholder="openai-compatible" />
                </el-form-item>
                <el-form-item label="Base URL">
                  <el-input v-model="llmForm.baseUrl" placeholder="https://api.openai.com/v1" />
                </el-form-item>
                <el-form-item label="Model">
                  <el-input v-model="llmForm.model" placeholder="gpt-4o-mini" />
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input v-model="llmForm.apiKey" type="password" show-password placeholder="留空则沿用已保存Key" />
                  <p v-if="llmForm.apiKeyMasked" class="mt-1 text-xs text-zinc-500">已保存Key：{{ llmForm.apiKeyMasked }}</p>
                </el-form-item>
                <el-form-item label="目标语言">
                  <el-input v-model="llmForm.targetLang" placeholder="en" />
                </el-form-item>
                <el-form-item label="Temperature">
                  <el-input-number v-model="llmForm.temperature" :min="0" :max="2" :step="0.1" class="w-full" />
                </el-form-item>
                <el-form-item label="Max Tokens">
                  <el-input-number v-model="llmForm.maxTokens" :min="64" :max="4096" :step="64" class="w-full" />
                </el-form-item>
                <el-form-item label="严格模式（失败则中断）">
                  <el-switch v-model="llmForm.strictMode" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </div>

              <el-form-item label="System Prompt（可选）">
                <el-input
                  v-model="llmForm.systemPrompt"
                  type="textarea"
                  :rows="3"
                  placeholder="可覆盖默认System Prompt"
                />
              </el-form-item>

            <el-form-item label="User Prompt Template（可选）">
              <el-input
                v-model="llmForm.userPromptTemplate"
                type="textarea"
                :rows="6"
                placeholder="支持占位符：{{title}} {{description}} {{targetLang}}"
              />
            </el-form-item>

            <div class="rounded-2xl border border-zinc-200 bg-zinc-50 p-3">
              <p class="mb-3 text-sm font-semibold text-zinc-700">测试文案</p>
              <el-form-item label="测试标题">
                <el-input v-model="llmTestInput.title" placeholder="输入测试标题" />
              </el-form-item>
              <el-form-item label="测试描述">
                <el-input
                  v-model="llmTestInput.description"
                  type="textarea"
                  :rows="3"
                  placeholder="输入测试描述"
                />
              </el-form-item>
            </div>
            </el-form>

            <div class="flex flex-wrap items-center gap-3">
              <el-button :loading="llmTesting" @click="testLlmConfig">测试连通</el-button>
              <el-button type="primary" :loading="llmSaving" @click="saveLlmConfig">保存LLM配置</el-button>
              <span v-if="llmForm.lastTestTime" class="text-xs text-zinc-500">
                最近测试：{{ llmForm.lastTestTime }} / {{ llmForm.lastTestStatus === 1 ? '成功' : '失败' }} {{ llmForm.lastTestMessage || '' }}
              </span>
            </div>

            <div v-if="llmTestResult" class="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
              <p class="mb-2 text-sm font-semibold text-emerald-700">测试返回</p>
              <p class="text-xs text-zinc-500">英文标题</p>
              <p class="mb-3 text-sm font-semibold text-zinc-800">{{ llmTestResult.enTitle || '-' }}</p>
              <p class="text-xs text-zinc-500">英文描述</p>
              <p class="whitespace-pre-line text-sm text-zinc-700">{{ llmTestResult.enDescription || '-' }}</p>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="OSS配置" name="oss">
          <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="mb-4 text-sm text-zinc-500">
              统一管理上传与导入使用的 OSS 参数。保存后会立即应用到运行中的上传/导入流程。
            </p>
            <el-form label-position="top">
              <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                <el-form-item label="启用">
                  <el-switch v-model="ossForm.enabled" :active-value="1" :inactive-value="0" />
                </el-form-item>
                <el-form-item label="Endpoint">
                  <el-input v-model="ossForm.endpoint" placeholder="https://oss-cn-hangzhou.aliyuncs.com" />
                </el-form-item>
                <el-form-item label="Bucket Name">
                  <el-input v-model="ossForm.bucketName" placeholder="bucket-name" />
                </el-form-item>
                <el-form-item label="Bucket Domain（可选）">
                  <el-input v-model="ossForm.bucketDomain" placeholder="cdn.example.com 或 https://cdn.example.com" />
                </el-form-item>
                <el-form-item label="AccessKey ID">
                  <el-input v-model="ossForm.accessKeyId" placeholder="LTAI..." />
                </el-form-item>
                <el-form-item label="AccessKey Secret">
                  <el-input v-model="ossForm.accessKeySecret" type="password" show-password placeholder="留空则沿用已保存Secret" />
                  <p v-if="ossForm.accessKeySecretMasked" class="mt-1 text-xs text-zinc-500">已保存Secret：{{ ossForm.accessKeySecretMasked }}</p>
                </el-form-item>
              </div>
            </el-form>

            <div class="flex flex-wrap items-center gap-3">
              <el-button :loading="ossTesting" @click="testOssConfig">测试连通</el-button>
              <el-button type="primary" :loading="ossSaving" @click="saveOssConfig">保存OSS配置</el-button>
              <span v-if="ossForm.lastTestTime" class="text-xs text-zinc-500">
                最近测试：{{ ossForm.lastTestTime }} / {{ ossForm.lastTestStatus === 1 ? '成功' : '失败' }} {{ ossForm.lastTestMessage || '' }}
              </span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="微商导入" name="wsAlbum">
          <WsAlbumAdminPanel :categories="categories" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <ProductFormDialog
      v-model="dialogVisible"
      :editing-product="editingProduct"
      :categories="categories"
      @saved="refreshAll"
    />

    <el-dialog v-model="categoryDialogVisible" :title="categoryDialogTitle" width="min(420px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="父级分类">
          <el-select v-model="categoryForm.parentId" class="w-full" :disabled="Boolean(categoryForm.id)">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类名称">
          <el-input v-model="categoryForm.name" />
        </el-form-item>
        <el-form-item label="英文分类名（可选）">
          <el-input v-model="categoryForm.enName" />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="categoryForm.sort" :min="0" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
