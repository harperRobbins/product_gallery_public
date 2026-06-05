<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/gallery'

const props = defineProps({
  categories: {
    type: Array,
    default: () => [],
  },
})

const configLoading = ref(false)
const configForm = reactive({
  id: null,
  configName: '默认配置',
  token: '',
  enabled: 1,
  defaultTransLang: 'en',
  defaultXWgLang: 'zh',
  defaultMaxPages: 20,
  remark: '',
  tokenMasked: '',
  lastVerifyTime: '',
  lastSuccessTime: '',
})

const testAlbumId = ref('')

const shopsLoading = ref(false)
const shops = ref([])
const shopPager = reactive({
  page: 1,
  size: 20,
  total: 0,
})
const shopKeyword = ref('')
const shopStatus = ref(null)
const addAlbumId = ref('')
const shopPriceStrategyDialogVisible = ref(false)
const editingShopId = ref('')
const priceRegexText = ref('')
const priceDetectForm = reactive({
  sourcePriority: 'ITEM_FIRST',
  matchMode: 'FIRST_HIT',
  priceTransformType: 'NONE',
  transformDigits: 4,
  detectInTitle: true,
  detectInSubTitle: true,
  stripTokens: true,
})

const crawlStarting = ref(false)
const crawlForm = reactive({
  shopId: '',
  crawlMode: 'FULL',
  startTimestamp: '',
  startDate: '',
  endDate: '',
  tagGroupId: '',
  tagIdsText: '',
  searchValue: '',
  itemIdsText: '',
  mergeItemIds: 0,
  maxPages: 20,
  enableTimestampUpdate: 1,
  remark: '',
})

const normalizeTimestampInput = () => {
  const raw = crawlForm.startTimestamp == null ? '' : String(crawlForm.startTimestamp)
  crawlForm.startTimestamp = raw.replace(/[^\d]/g, '')
}

const parseItemIdsInput = () => {
  const raw = String(crawlForm.itemIdsText || '').trim()
  if (!raw) return []
  const tokens = raw
    .split(/[\n,，;；\s]+/g)
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  const unique = []
  const seen = new Set()
  for (const token of tokens) {
    if (seen.has(token)) continue
    seen.add(token)
    unique.push(token)
    if (unique.length >= 200) break
  }
  return unique
}

const parseTagIdsInput = () => {
  const raw = String(crawlForm.tagIdsText || '').trim()
  if (!raw) {
    return { ids: [], invalidTokens: [] }
  }
  const tokens = raw
    .split(/[\n,，;；\s]+/g)
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  const unique = []
  const seen = new Set()
  const invalidTokens = []
  for (const token of tokens) {
    if (!/^\d+$/.test(token)) {
      invalidTokens.push(token)
      continue
    }
    const id = Number(token)
    if (!Number.isSafeInteger(id) || id <= 0) {
      invalidTokens.push(token)
      continue
    }
    if (seen.has(id)) continue
    seen.add(id)
    unique.push(id)
    if (unique.length >= 200) break
  }
  return { ids: unique, invalidTokens }
}

const crawlLogsLoading = ref(false)
const crawlLogs = ref([])
const requestLogsVisible = ref(false)
const requestLogsLoading = ref(false)
const requestLogs = ref([])
const requestLogsBatchNo = ref('')
const activeDataTab = ref('crawl')
const crawlStatusSnapshot = ref({})
let crawlPollTimer = null

const importLoading = ref(false)
const importRows = ref([])
const importPager = reactive({
  page: 1,
  size: 20,
  total: 0,
})
const importQuery = reactive({
  shopId: '',
  keyword: '',
  importStatus: null,
  hasVideo: null,
  isAbnormal: null,
})
const selectedImportIds = ref([])

const importDialogVisible = ref(false)
const importSubmitting = ref(false)
const importAllMode = ref(false)
const importForm = reactive({
  categoryId: null,
  importAllShopId: '',
  targetTagsText: '',
  priceStrategyType: 'NONE',
  priceStrategyValue: null,
  allowRepeatImport: 0,
  defaultTitleTemplate: '',
})

const importLogsLoading = ref(false)
const importLogs = ref([])
const importRunningBatchNo = ref('')
let importPollTimer = null

const clampInteger = (value, fallback, min, max) => {
  const raw = Number(value)
  if (!Number.isFinite(raw)) return fallback
  let normalized = Math.floor(raw)
  if (normalized < min) normalized = min
  if (normalized > max) normalized = max
  return normalized
}

const defaultMaxPagesModel = computed({
  get() {
    return clampInteger(configForm.defaultMaxPages, 20, 1, 200)
  },
  set(value) {
    configForm.defaultMaxPages = clampInteger(value, 20, 1, 200)
  },
})

const crawlMaxPagesModel = computed({
  get() {
    return clampInteger(crawlForm.maxPages, 20, 1, 500)
  },
  set(value) {
    crawlForm.maxPages = clampInteger(value, 20, 1, 500)
  },
})

const categoryOptions = () => {
  const list = []
  const walk = (nodes) => {
    ;(nodes || []).forEach((node) => {
      list.push({ value: node.id, label: node.name })
      if (node.children?.length) {
        walk(node.children)
      }
    })
  }
  walk(props.categories)
  return list
}

const loadConfig = async () => {
  configLoading.value = true
  try {
    const data = await api.wsAlbumGetConfig()
    if (!data) {
      return
    }
    Object.assign(configForm, {
      id: data.id,
      configName: data.configName || '默认配置',
      token: '',
      enabled: data.enabled ?? 1,
      defaultTransLang: data.defaultTransLang || 'en',
      defaultXWgLang: data.defaultXWgLang || 'zh',
      defaultMaxPages: clampInteger(data.defaultMaxPages, 20, 1, 200),
      remark: data.remark || '',
      tokenMasked: data.tokenMasked || '',
      lastVerifyTime: data.lastVerifyTime || '',
      lastSuccessTime: data.lastSuccessTime || '',
    })
    crawlForm.maxPages = clampInteger(data.defaultMaxPages, 20, 1, 500)
  } catch (error) {
    ElMessage.error(error.message || '加载抓取配置失败')
  } finally {
    configLoading.value = false
  }
}

const saveConfig = async () => {
  if (!configForm.token.trim()) {
    ElMessage.warning('请输入 token')
    return
  }
  configLoading.value = true
  try {
    const data = await api.wsAlbumSaveConfig({
      id: configForm.id,
      configName: configForm.configName,
      token: configForm.token,
      enabled: configForm.enabled,
      defaultTransLang: configForm.defaultTransLang,
      defaultXWgLang: configForm.defaultXWgLang,
      defaultMaxPages: clampInteger(configForm.defaultMaxPages, 20, 1, 200),
      remark: configForm.remark,
    })
    ElMessage.success('抓取配置已保存')
    Object.assign(configForm, {
      id: data.id,
      token: '',
      tokenMasked: data.tokenMasked,
      lastVerifyTime: data.lastVerifyTime,
      lastSuccessTime: data.lastSuccessTime,
    })
  } catch (error) {
    ElMessage.error(error.message || '保存配置失败')
  } finally {
    configLoading.value = false
  }
}

const testConfig = async () => {
  if (!testAlbumId.value.trim()) {
    ElMessage.warning('请输入用于测试的 albumId')
    return
  }
  try {
    const msg = await api.wsAlbumTestConfig({ albumId: testAlbumId.value.trim() })
    ElMessage.success(msg || '测试成功')
    await loadConfig()
  } catch (error) {
    ElMessage.error(error.message || '测试失败')
  }
}

const loadShops = async () => {
  shopsLoading.value = true
  try {
    const data = await api.wsAlbumShopList({
      page: shopPager.page,
      size: shopPager.size,
      keyword: shopKeyword.value || undefined,
      status: shopStatus.value,
    })
    shops.value = data.records || []
    shopPager.total = Number(data.total || 0)
    if (!crawlForm.shopId && shops.value.length) {
      crawlForm.shopId = shops.value[0].shopId
    }
  } catch (error) {
    ElMessage.error(error.message || '加载来源店铺失败')
  } finally {
    shopsLoading.value = false
  }
}

const addShop = async () => {
  if (!addAlbumId.value.trim()) {
    ElMessage.warning('请输入 albumId')
    return
  }
  try {
    await api.wsAlbumShopAdd({ albumId: addAlbumId.value.trim() })
    ElMessage.success('店铺已添加')
    addAlbumId.value = ''
    await loadShops()
  } catch (error) {
    ElMessage.error(error.message || '添加店铺失败')
  }
}

const refreshShop = async (row) => {
  try {
    await api.wsAlbumShopRefresh({ shopId: row.shopId })
    ElMessage.success('店铺信息已刷新')
    await loadShops()
  } catch (error) {
    ElMessage.error(error.message || '刷新失败')
  }
}

const toggleShopStatus = async (row) => {
  try {
    await api.wsAlbumShopUpdate({
      shopId: row.shopId,
      status: row.status === 1 ? 0 : 1,
      remark: row.remark,
    })
    ElMessage.success('状态已更新')
    await loadShops()
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
  }
}

const openShopPriceStrategyDialog = (row) => {
  editingShopId.value = row.shopId
  Object.assign(priceDetectForm, {
    sourcePriority: 'ITEM_FIRST',
    matchMode: 'FIRST_HIT',
    priceTransformType: 'NONE',
    transformDigits: 4,
    detectInTitle: true,
    detectInSubTitle: true,
    stripTokens: true,
  })
  priceRegexText.value = ''

  const raw = String(row.priceDetectConfigJson || '').trim()
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      priceDetectForm.sourcePriority = String(parsed.sourcePriority || 'ITEM_FIRST').toUpperCase()
      priceDetectForm.matchMode = String(parsed.matchMode || 'FIRST_HIT').toUpperCase()
      priceDetectForm.priceTransformType = String(parsed.priceTransformType || 'NONE').toUpperCase()
      priceDetectForm.transformDigits = Number(parsed.transformDigits || 4) || 4
      priceDetectForm.detectInTitle = parsed.detectInTitle !== false
      priceDetectForm.detectInSubTitle = parsed.detectInSubTitle !== false
      priceDetectForm.stripTokens = parsed.stripTokens !== false
      if (Array.isArray(parsed.priceRegexList) && parsed.priceRegexList.length) {
        priceRegexText.value = parsed.priceRegexList.map((item) => String(item || '').trim()).filter(Boolean).join('\n')
      }
    } catch (error) {
      ElMessage.warning('该店铺现有策略JSON解析失败，已按默认值打开')
    }
  }
  shopPriceStrategyDialogVisible.value = true
}

const buildPriceRegexList = () => {
  const raw = String(priceRegexText.value || '').trim()
  if (!raw) return []
  const list = raw
    .split('\n')
    .map((line) => String(line || '').trim())
    .filter(Boolean)
  return Array.from(new Set(list))
}

const saveShopPriceStrategy = async () => {
  if (!editingShopId.value) {
    ElMessage.warning('缺少店铺ID')
    return
  }
  if (!priceDetectForm.detectInTitle && !priceDetectForm.detectInSubTitle) {
    ElMessage.warning('至少选择一个文案来源（标题或副标题）')
    return
  }
  const regexList = buildPriceRegexList()
  const payload = {
    sourcePriority: String(priceDetectForm.sourcePriority || 'ITEM_FIRST').toUpperCase(),
    matchMode: String(priceDetectForm.matchMode || 'FIRST_HIT').toUpperCase(),
    priceTransformType: String(priceDetectForm.priceTransformType || 'NONE').toUpperCase(),
    transformDigits: Math.max(1, Math.min(16, Number(priceDetectForm.transformDigits || 4))),
    detectInTitle: !!priceDetectForm.detectInTitle,
    detectInSubTitle: !!priceDetectForm.detectInSubTitle,
    stripTokens: !!priceDetectForm.stripTokens,
    priceRegexList: regexList,
  }
  try {
    await api.wsAlbumShopUpdate({
      shopId: editingShopId.value,
      priceDetectConfigJson: JSON.stringify(payload),
    })
    ElMessage.success('店铺价格识别策略已更新')
    shopPriceStrategyDialogVisible.value = false
    await loadShops()
  } catch (error) {
    ElMessage.error(error.message || '更新店铺价格识别策略失败')
  }
}

const clearShopPriceStrategy = async () => {
  if (!editingShopId.value) {
    ElMessage.warning('缺少店铺ID')
    return
  }
  try {
    await ElMessageBox.confirm('确认清空该店铺的价格识别策略并回退到系统默认吗？', '提示', {
      type: 'warning',
    })
    await api.wsAlbumShopUpdate({
      shopId: editingShopId.value,
      priceDetectConfigJson: '',
    })
    ElMessage.success('已清空，后续将使用默认识别策略')
    shopPriceStrategyDialogVisible.value = false
    await loadShops()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '清空策略失败')
    }
  }
}

const startCrawl = async () => {
  if (!crawlForm.shopId) {
    ElMessage.warning('请先选择来源店铺')
    return
  }
  crawlStarting.value = true
  try {
    const startTimestampVal = crawlForm.startTimestamp == null || String(crawlForm.startTimestamp).trim() === ''
      ? undefined
      : Number(crawlForm.startTimestamp)
    if (startTimestampVal !== undefined && (!Number.isFinite(startTimestampVal) || startTimestampVal <= 0)) {
      ElMessage.warning('起始时间戳格式不正确，请输入纯数字')
      return
    }
    const itemIds = parseItemIdsInput()
    const tagIdsResult = parseTagIdsInput()
    if (tagIdsResult.invalidTokens.length) {
      ElMessage.warning('tagId 格式不正确: ' + tagIdsResult.invalidTokens.slice(0, 3).join(', '))
      return
    }
    if (itemIds.length > 0 && !crawlForm.shopId) {
      ElMessage.warning('指定图文ID抓取时，请先选择来源店铺')
      return
    }
    const payload = {
      shopId: crawlForm.shopId,
      crawlMode: crawlForm.crawlMode,
      startTimestamp: startTimestampVal,
      startDate: crawlForm.startDate || '',
      endDate: crawlForm.endDate || '',
      tagList: tagIdsResult.ids,
      tagGroupId: crawlForm.tagGroupId || '',
      searchValue: crawlForm.searchValue || '',
      itemIds,
      mergeItemIds: itemIds.length > 1 ? crawlForm.mergeItemIds : 0,
      maxPages: clampInteger(crawlForm.maxPages, 20, 1, 500),
      enableTimestampUpdate: crawlForm.enableTimestampUpdate,
      remark: crawlForm.remark || '',
    }
    const data = await api.wsAlbumCrawlStart(payload)
    ElMessage.success('抓取任务已启动: ' + data.crawlBatchNo)
    await loadCrawlLogs()
  } catch (error) {
    ElMessage.error(error.message || '启动抓取失败')
  } finally {
    crawlStarting.value = false
  }
}

const syncCrawlStatus = (rows) => {
  const previous = crawlStatusSnapshot.value || {}
  const next = {}
  ;(rows || []).forEach((row) => {
    next[row.crawlBatchNo] = row.status
    const oldStatus = previous[row.crawlBatchNo]
    if (oldStatus === 0 && row.status === 1) {
      ElMessage.success(`抓取任务已完成: ${row.crawlBatchNo}`)
    } else if (oldStatus === 0 && row.status === 2) {
      ElMessage.error(`抓取任务失败: ${row.crawlBatchNo}`)
    } else if (oldStatus === 0 && row.status === 3) {
      ElMessage.warning(`抓取任务已停止: ${row.crawlBatchNo}`)
    }
  })
  crawlStatusSnapshot.value = next
}

const loadCrawlLogs = async (silent = false) => {
  if (!silent) {
    crawlLogsLoading.value = true
  }
  try {
    const data = await api.wsAlbumCrawlLogList({ page: 1, size: 20 })
    crawlLogs.value = data.records || []
    syncCrawlStatus(crawlLogs.value)
  } catch (error) {
    if (!silent) {
      ElMessage.error(error.message || '抓取日志加载失败')
    }
  } finally {
    if (!silent) {
      crawlLogsLoading.value = false
    }
  }
}

const startCrawlPolling = () => {
  if (crawlPollTimer) {
    clearInterval(crawlPollTimer)
  }
  crawlPollTimer = setInterval(() => {
    if (activeDataTab.value !== 'crawl') {
      return
    }
    loadCrawlLogs(true)
  }, 5000)
}

const stopCrawlPolling = () => {
  if (!crawlPollTimer) {
    return
  }
  clearInterval(crawlPollTimer)
  crawlPollTimer = null
}

const retryCrawl = async (row) => {
  try {
    await api.wsAlbumCrawlRetry({ crawlBatchNo: row.crawlBatchNo })
    ElMessage.success('重试任务已启动')
    await loadCrawlLogs()
  } catch (error) {
    ElMessage.error(error.message || '重试失败')
  }
}

const stopCrawl = async (row) => {
  try {
    await ElMessageBox.confirm(`确认停止抓取任务 ${row.crawlBatchNo} 吗？`, '停止任务', { type: 'warning' })
    await api.wsAlbumCrawlStop({ crawlBatchNo: row.crawlBatchNo })
    ElMessage.success('停止指令已下发')
    await loadCrawlLogs()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '停止任务失败')
    }
  }
}

const openRequestLogs = async (row) => {
  requestLogsBatchNo.value = row.crawlBatchNo
  requestLogsVisible.value = true
  await loadRequestLogs()
}

const loadRequestLogs = async () => {
  if (!requestLogsBatchNo.value) {
    requestLogs.value = []
    return
  }
  requestLogsLoading.value = true
  try {
    const data = await api.wsAlbumCrawlRequestLogList({
      crawlBatchNo: requestLogsBatchNo.value,
      limit: 500,
    })
    requestLogs.value = data || []
  } catch (error) {
    ElMessage.error(error.message || '调用记录加载失败')
  } finally {
    requestLogsLoading.value = false
  }
}

const loadImportProducts = async () => {
  importLoading.value = true
  try {
    const data = await api.wsAlbumImportProductList({
      page: importPager.page,
      size: importPager.size,
      shopId: importQuery.shopId || undefined,
      keyword: importQuery.keyword || undefined,
      importStatus: importQuery.importStatus,
      hasVideo: importQuery.hasVideo,
      isAbnormal: importQuery.isAbnormal,
    })
    importRows.value = data.records || []
    importPager.total = Number(data.total || 0)
    selectedImportIds.value = []
  } catch (error) {
    ElMessage.error(error.message || '中间库商品加载失败')
  } finally {
    importLoading.value = false
  }
}

const searchImportProducts = () => {
  importPager.page = 1
  loadImportProducts()
}

const onImportSelectionChange = (rows) => {
  selectedImportIds.value = (rows || []).map((row) => row.id)
}

const markAbnormal = async () => {
  if (!selectedImportIds.value.length) {
    ElMessage.warning('请至少选择一个商品')
    return
  }
  await api.wsAlbumImportProductMarkAbnormal({
    ids: selectedImportIds.value,
    isAbnormal: 1,
    abnormalReason: '人工标记异常',
  })
  ElMessage.success('已标记异常')
  await loadImportProducts()
}

const hideImports = async () => {
  if (!selectedImportIds.value.length) {
    ElMessage.warning('请至少选择一个商品')
    return
  }
  await ElMessageBox.confirm('确认隐藏选中的中间库商品吗？', '提示', { type: 'warning' })
  await api.wsAlbumImportProductDelete({ ids: selectedImportIds.value })
  ElMessage.success('已隐藏')
  await loadImportProducts()
}

const openImportDialog = () => {
  if (!selectedImportIds.value.length) {
    ElMessage.warning('请先勾选需要导入的商品')
    return
  }
  importAllMode.value = false
  importForm.importAllShopId = ''
  importDialogVisible.value = true
}

const openImportDialogAll = () => {
  importAllMode.value = true
  importForm.importAllShopId = importQuery.shopId || ''
  importDialogVisible.value = true
}

const submitImport = async () => {
  if (!importAllMode.value && !selectedImportIds.value.length) {
    ElMessage.warning('请先勾选需要导入的商品')
    return
  }
  if (!importForm.categoryId) {
    ElMessage.warning('请选择目标分类')
    return
  }
  importSubmitting.value = true
  try {
    const targetTags = importForm.targetTagsText
      .split(',')
      .map((x) => x.trim())
      .filter(Boolean)
    const result = await api.wsAlbumImportFormal({
      ids: importAllMode.value ? [] : selectedImportIds.value,
      importAll: importAllMode.value,
      shopId: importAllMode.value ? (importForm.importAllShopId || undefined) : undefined,
      categoryId: importForm.categoryId,
      targetTags,
      priceStrategyType: importForm.priceStrategyType,
      priceStrategyValue: importForm.priceStrategyValue,
      allowRepeatImport: importForm.allowRepeatImport,
      defaultTitleTemplate: importForm.defaultTitleTemplate,
    })
    const modeText = importAllMode.value ? '（全量）' : ''
    ElMessage.success('导入任务已启动' + modeText + ': ' + result.importBatchNo + '，共 ' + (result.selectedCount || 0) + ' 条')
    importDialogVisible.value = false
    await Promise.all([loadImportProducts(), loadImportLogs()])
    startImportPolling(result.importBatchNo)
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    importSubmitting.value = false
  }
}

const loadImportLogs = async (silent = false) => {
  if (!silent) {
    importLogsLoading.value = true
  }
  try {
    const data = await api.wsAlbumImportLogList({ page: 1, size: 20 })
    importLogs.value = data.records || []
    if (!importRunningBatchNo.value) {
      const running = importLogs.value.find((item) => item.status === 0)
      if (running?.importBatchNo) {
        startImportPolling(running.importBatchNo)
      }
    }
  } catch (error) {
    if (!silent) {
      ElMessage.error(error.message || '导入日志加载失败')
    }
  } finally {
    if (!silent) {
      importLogsLoading.value = false
    }
  }
}

const stopImportPolling = () => {
  if (importPollTimer) {
    clearInterval(importPollTimer)
    importPollTimer = null
  }
  importRunningBatchNo.value = ''
}

const pollImportStatus = async (batchNo, silent = true) => {
  if (!batchNo) return
  try {
    const detail = await api.wsAlbumImportLogDetail({ importBatchNo: batchNo })
    if (!detail) {
      stopImportPolling()
      return
    }
    if (detail.status === 0) {
      await loadImportLogs(true)
      return
    }

    stopImportPolling()
    await Promise.all([loadImportProducts(), loadImportLogs(true)])
    const selected = Number(detail.selectedCount || 0)
    const success = Number(detail.successCount || 0)
    const failed = Number(detail.failedCount || 0)
    const skipped = Math.max(0, selected - success - failed)
    if (detail.status === 1) {
      ElMessage.success(`导入完成: 成功 ${success} / 失败 ${failed} / 略过 ${skipped}`)
    } else {
      ElMessage.error(`导入结束(含失败): 成功 ${success} / 失败 ${failed} / 略过 ${skipped}`)
    }
  } catch (error) {
    if (!silent) {
      ElMessage.error(error.message || '导入任务状态查询失败')
    }
  }
}

const startImportPolling = (batchNo) => {
  if (!batchNo) return
  importRunningBatchNo.value = batchNo
  if (importPollTimer) {
    clearInterval(importPollTimer)
  }
  importPollTimer = setInterval(() => {
    if (!importRunningBatchNo.value) {
      return
    }
    pollImportStatus(importRunningBatchNo.value, true)
  }, 3000)
  pollImportStatus(batchNo, true)
}

const importPageChange = (page) => {
  importPager.page = page
  loadImportProducts()
}

const statusText = (value) => {
  if (value === 1) return '成功'
  if (value === 2) return '失败'
  if (value === 3) return '已停止'
  return '执行中'
}

const requestStatusText = (value) => {
  if (value === 1) return '成功'
  if (value === 2) return '失败'
  return '未知'
}

const productImportStatusText = (value) => {
  if (value === 1) return '已导入'
  if (value === 2) return '导入失败'
  if (value === 3) return '已忽略'
  return '未导入'
}

const importDialogTitle = computed(() => (importAllMode.value ? '一键导入全部中间库' : '批量导入正式库'))

const initialize = async () => {
  await Promise.all([loadConfig(), loadShops(), loadCrawlLogs(), loadImportLogs()])
  await loadImportProducts()
}

onMounted(initialize)
onMounted(startCrawlPolling)
onUnmounted(() => {
  stopCrawlPolling()
  stopImportPolling()
})

watch(activeDataTab, (tab) => {
  if (tab === 'crawl') {
    loadCrawlLogs(true)
  }
})
</script>

<template>
  <div class="space-y-4">
    <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
      <p class="mb-3 text-sm font-semibold text-zinc-800">抓取配置管理</p>
      <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
        <el-input v-model="configForm.configName" placeholder="配置名" />
        <el-input v-model="configForm.defaultTransLang" placeholder="transLang，例如 en" />
        <el-input v-model="configForm.defaultXWgLang" placeholder="x-wg-language，例如 zh" />
        <el-input-number
          v-model="defaultMaxPagesModel"
          :min="1"
          :max="200"
          :step="1"
          :step-strictly="true"
          :precision="0"
          controls-position="right"
          class="w-full"
        />
      </div>
      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
        <el-input
          v-model="configForm.token"
          type="textarea"
          :rows="3"
          placeholder="粘贴微商相册 token（必填）"
        />
        <el-input v-model="configForm.remark" type="textarea" :rows="3" placeholder="备注" />
      </div>
      <div class="mt-2 text-xs text-zinc-500">
        当前掩码 token：{{ configForm.tokenMasked || '未配置' }}
      </div>
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <el-input v-model="testAlbumId" placeholder="测试 albumId" class="w-64" />
        <el-button :loading="configLoading" type="primary" @click="saveConfig">保存配置</el-button>
        <el-button :loading="configLoading" @click="testConfig">测试连接</el-button>
        <span class="text-xs text-zinc-500">最近验证：{{ configForm.lastVerifyTime || '-' }}</span>
        <span class="text-xs text-zinc-500">最近抓取成功：{{ configForm.lastSuccessTime || '-' }}</span>
      </div>
    </div>

    <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="text-sm font-semibold text-zinc-800">来源店铺管理</p>
        <div class="flex items-center gap-2">
          <el-input v-model="shopKeyword" placeholder="店铺名/ID" class="w-44" @keyup.enter="loadShops" />
          <el-select v-model="shopStatus" clearable placeholder="状态" class="w-28">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
          <el-button @click="loadShops">查询</el-button>
        </div>
      </div>
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <el-input v-model="addAlbumId" placeholder="新增店铺 albumId" class="w-64" />
        <el-button type="primary" @click="addShop">新增店铺</el-button>
      </div>
      <el-table :data="shops" class="mt-3" v-loading="shopsLoading" size="small">
        <el-table-column prop="shopId" label="店铺ID" min-width="220" />
        <el-table-column prop="shopName" label="店铺名称" min-width="220" />
        <el-table-column label="识别策略" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.priceDetectConfigJson ? 'warning' : 'info'">
              {{ row.priceDetectConfigJson ? '店铺自定义' : '系统默认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastCrawlTime" label="最近抓取" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="refreshShop(row)">刷新信息</el-button>
            <el-button link type="warning" @click="openShopPriceStrategyDialog(row)">价格识别策略</el-button>
            <el-button link @click="toggleShopStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
      <el-tabs v-model="activeDataTab">
        <el-tab-pane label="抓取任务" name="crawl" />
        <el-tab-pane label="中间库商品" name="imports" />
        <el-tab-pane label="导入日志" name="importLogs" />
      </el-tabs>
    </div>

    <div v-show="activeDataTab === 'crawl'" class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
      <p class="text-sm font-semibold text-zinc-800">抓取任务</p>
      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-4">
        <el-select v-model="crawlForm.shopId" filterable placeholder="选择店铺">
          <el-option v-for="shop in shops" :key="shop.shopId" :value="shop.shopId" :label="shop.shopName + ' (' + shop.shopId + ')'" />
        </el-select>
        <el-select v-model="crawlForm.crawlMode">
          <el-option value="FULL" label="全量抓取" />
          <el-option value="INCREMENTAL" label="增量抓取" />
          <el-option value="SYNC" label="同步产品（遇重复即停）" />
          <el-option value="CUSTOM" label="自定义时间抓取" />
        </el-select>
        <el-input v-model="crawlForm.searchValue" placeholder="关键词 searchValue" />
        <el-input-number
          v-model="crawlMaxPagesModel"
          :min="1"
          :max="500"
          :step="1"
          :step-strictly="true"
          :precision="0"
          controls-position="right"
          class="w-full"
        />
      </div>
      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-4">
        <el-input
          v-model="crawlForm.startTimestamp"
          placeholder="起始时间戳（增量可填）"
          clearable
          @input="normalizeTimestampInput"
        />
        <el-input v-model="crawlForm.startDate" placeholder="startDate" />
        <el-input v-model="crawlForm.endDate" placeholder="endDate" />
        <el-input v-model="crawlForm.tagGroupId" placeholder="tagGroupId" />
      </div>
      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
        <el-input
          v-model="crawlForm.tagIdsText"
          type="textarea"
          :rows="2"
          placeholder="可选：按 tagId 过滤（支持多个，换行/逗号分隔）"
        />
      </div>
      <div class="mt-3 grid grid-cols-1 gap-3">
        <el-input
          v-model="crawlForm.itemIdsText"
          type="textarea"
          :rows="3"
          placeholder="可选：指定图文ID抓取（支持多个，换行/逗号分隔）"
        />
      </div>
      <div class="mt-3 flex items-center gap-3">
        <el-switch v-model="crawlForm.enableTimestampUpdate" :active-value="1" :inactive-value="0" inline-prompt active-text="时间戳更新" inactive-text="只新增" />
        <el-switch
          v-model="crawlForm.mergeItemIds"
          :active-value="1"
          :inactive-value="0"
          inline-prompt
          active-text="多ID合并为1条"
          inactive-text="多ID各自入库"
        />
        <el-button type="primary" :loading="crawlStarting" @click="startCrawl">开始抓取</el-button>
      </div>
      <el-table :data="crawlLogs" class="mt-3" size="small" v-loading="crawlLogsLoading">
        <el-table-column prop="crawlBatchNo" label="批次号" min-width="200" />
        <el-table-column prop="shopName" label="店铺" min-width="180" />
        <el-table-column prop="crawlMode" label="模式" width="110" />
        <el-table-column prop="fetchedCount" label="抓取" width="90" />
        <el-table-column prop="insertedCount" label="新增" width="90" />
        <el-table-column prop="updatedCount" label="更新" width="90" />
        <el-table-column prop="duplicateCount" label="重复" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : row.status === 3 ? 'info' : 'warning'">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRequestLogs(row)">调用记录</el-button>
            <el-button v-if="row.status === 2" link type="danger" @click="retryCrawl(row)">重试</el-button>
            <el-button v-if="row.status === 0" link type="warning" @click="stopCrawl(row)">停止</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-show="activeDataTab === 'imports'" class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="text-sm font-semibold text-zinc-800">中间库商品</p>
        <div class="flex items-center gap-2">
          <el-button type="warning" :disabled="!selectedImportIds.length" @click="markAbnormal">批量标异常</el-button>
          <el-button type="danger" :disabled="!selectedImportIds.length" @click="hideImports">批量隐藏</el-button>
          <el-button type="primary" :disabled="!selectedImportIds.length" @click="openImportDialog">批量导入正式库</el-button>
          <el-button type="success" @click="openImportDialogAll">一键全部导入</el-button>
        </div>
      </div>
      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-5">
        <el-select v-model="importQuery.shopId" filterable clearable placeholder="店铺">
          <el-option v-for="shop in shops" :key="shop.shopId" :value="shop.shopId" :label="shop.shopName" />
        </el-select>
        <el-input v-model="importQuery.keyword" placeholder="标题关键词" @keyup.enter="searchImportProducts" />
        <el-select v-model="importQuery.importStatus" clearable placeholder="导入状态">
          <el-option :value="0" label="未导入" />
          <el-option :value="1" label="已导入" />
          <el-option :value="2" label="导入失败" />
        </el-select>
        <el-select v-model="importQuery.hasVideo" clearable placeholder="视频">
          <el-option :value="1" label="有视频" />
          <el-option :value="0" label="无视频" />
        </el-select>
        <el-button @click="searchImportProducts">查询</el-button>
      </div>
      <el-table :data="importRows" class="mt-3" size="small" v-loading="importLoading" @selection-change="onImportSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column label="主图" width="92">
          <template #default="{ row }">
            <img v-if="row.mainImageUrl" :src="row.mainImageUrl" class="h-14 w-14 rounded object-cover" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column prop="goodsId" label="goods_id" min-width="220" />
        <el-table-column prop="itemPrice" label="价格" width="100" />
        <el-table-column prop="mediaCount" label="图片数" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.importStatus === 1 ? 'success' : row.importStatus === 2 ? 'danger' : 'info'">
              {{ productImportStatusText(row.importStatus) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-3 flex justify-end">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="importPager.page"
          :page-size="importPager.size"
          :total="importPager.total"
          @current-change="importPageChange"
        />
      </div>
    </div>

    <div v-show="activeDataTab === 'importLogs'" class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-zinc-100">
      <p class="text-sm font-semibold text-zinc-800">导入日志</p>
      <el-table :data="importLogs" class="mt-3" size="small" v-loading="importLogsLoading">
        <el-table-column prop="importBatchNo" label="导入批次" min-width="220" />
        <el-table-column prop="shopId" label="店铺ID" min-width="220" />
        <el-table-column prop="selectedCount" label="选中" width="80" />
        <el-table-column prop="successCount" label="成功" width="80" />
        <el-table-column prop="failedCount" label="失败" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </div>

    <el-dialog v-model="requestLogsVisible" title="微商接口调用记录" width="min(1200px, 96vw)">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <el-input v-model="requestLogsBatchNo" placeholder="抓取批次号" class="w-96" />
        <el-button @click="loadRequestLogs">刷新</el-button>
      </div>
      <el-table :data="requestLogs" size="small" v-loading="requestLogsLoading" max-height="65vh">
        <el-table-column prop="pageNo" label="页" width="60" />
        <el-table-column prop="httpStatus" label="HTTP" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ requestStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestPageTimestamp" label="请求游标" width="140" />
        <el-table-column prop="responsePageTimestamp" label="返回游标" width="140" />
        <el-table-column prop="responseIsLoadMore" label="isLoadMore" width="110" />
        <el-table-column prop="fetchedCount" label="条数" width="80" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="请求URL" min-width="420">
          <template #default="{ row }">
            <div class="max-h-20 overflow-auto break-all text-xs text-zinc-700">
              {{ row.requestUrl }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="请求参数" min-width="420">
          <template #default="{ row }">
            <div class="max-h-24 overflow-auto break-all rounded bg-zinc-50 p-2 text-xs text-zinc-700">
              {{ row.requestParamsJson }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="220" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" :title="importDialogTitle" width="min(560px, 92vw)">
      <el-form label-position="top">
        <div v-if="importAllMode" class="mb-2 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
          当前模式支持按店铺导入“未导入 + 导入失败”的中间库商品；不选店铺则默认全店铺。
        </div>
        <el-form-item v-if="importAllMode" label="导入店铺（可选）">
          <el-select v-model="importForm.importAllShopId" filterable clearable class="w-full" placeholder="不选则导入全店铺">
            <el-option v-for="shop in shops" :key="shop.shopId" :value="shop.shopId" :label="shop.shopName + ' (' + shop.shopId + ')'" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标分类">
          <el-select v-model="importForm.categoryId" filterable class="w-full" placeholder="请选择分类">
            <el-option v-for="item in categoryOptions()" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标标签（逗号分隔）">
          <el-input v-model="importForm.targetTagsText" placeholder="例如：爆款,公文包,新款" />
          <div class="mt-1 text-xs text-zinc-500">导入时会自动追加该商品在中间库的来源标签，并自动去重。</div>
        </el-form-item>
        <el-form-item label="价格策略">
          <el-select v-model="importForm.priceStrategyType" class="w-full">
            <el-option value="NONE" label="不处理" />
            <el-option value="PERCENT" label="百分比加价" />
            <el-option value="FIXED_ADD" label="固定金额加价" />
            <el-option value="FIXED_OVERRIDE" label="固定价格覆盖" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略值（可选）">
          <el-input-number v-model="importForm.priceStrategyValue" :min="0" :precision="2" class="w-full" />
        </el-form-item>
        <el-form-item label="默认标题模板（空标题商品使用）">
          <el-input v-model="importForm.defaultTitleTemplate" placeholder="例如：微商导入-{goodsId}" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="importForm.allowRepeatImport" :active-value="1" :inactive-value="0" inline-prompt active-text="允许重复导入" inactive-text="禁止重复导入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importSubmitting" @click="submitImport">确认导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shopPriceStrategyDialogVisible" title="店铺价格识别策略" width="min(680px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="价格来源优先级">
          <el-select v-model="priceDetectForm.sourcePriority" class="w-full">
            <el-option value="ITEM_FIRST" label="优先 itemPrice，缺失时回退文案识别价" />
            <el-option value="TEXT_FIRST" label="优先文案识别价，缺失时回退 itemPrice" />
            <el-option value="TEXT_ONLY" label="仅使用文案识别价" />
            <el-option value="ITEM_ONLY" label="仅使用 itemPrice" />
          </el-select>
        </el-form-item>
        <el-form-item label="多命中处理">
          <el-select v-model="priceDetectForm.matchMode" class="w-full">
            <el-option value="FIRST_HIT" label="按规则顺序取第一个命中" />
            <el-option value="MAX" label="取命中价格最大值" />
            <el-option value="MIN" label="取命中价格最小值" />
          </el-select>
        </el-form-item>
        <el-form-item label="识别值转换">
          <el-select v-model="priceDetectForm.priceTransformType" class="w-full">
            <el-option value="NONE" label="不转换（直接作为价格）" />
            <el-option value="REVERSE_LAST_N" label="取末尾N位并倒序（店铺编码转价格）" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="priceDetectForm.priceTransformType === 'REVERSE_LAST_N'" label="转换位数 N">
          <el-input-number v-model="priceDetectForm.transformDigits" :min="1" :max="16" :step="1" :step-strictly="true" :precision="0" class="w-full" />
        </el-form-item>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
          <el-switch v-model="priceDetectForm.detectInTitle" inline-prompt active-text="标题识别" inactive-text="标题关闭" />
          <el-switch v-model="priceDetectForm.detectInSubTitle" inline-prompt active-text="副标题识别" inactive-text="副标题关闭" />
          <el-switch v-model="priceDetectForm.stripTokens" inline-prompt active-text="剥离价格词" inactive-text="保留原文案" />
        </div>
        <el-form-item class="mt-3" label="自定义识别正则（每行一条，可留空使用系统默认）">
          <el-input
            v-model="priceRegexText"
            type="textarea"
            :rows="7"
            placeholder="示例：(?i)\\bP\\s*([1-9]\\d{1,5}(?:\\.\\d{1,2})?)\\b"
          />
          <div class="mt-1 text-xs text-zinc-500">
            每条正则必须包含第1个捕获组（括号）作为价格数字，例如 `([0-9]+(?:\\.[0-9]{1,2})?)`。
          </div>
          <div class="mt-1 text-xs text-zinc-500">
            编码转价格示例：若选择“取末尾N位并倒序”，可配 `(?<!\\d)(\\d{8})(?!\\d)` + `N=4`，`25250871 -> 1780`。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clearShopPriceStrategy">清空为默认</el-button>
        <el-button @click="shopPriceStrategyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveShopPriceStrategy">保存策略</el-button>
      </template>
    </el-dialog>
  </div>
</template>
