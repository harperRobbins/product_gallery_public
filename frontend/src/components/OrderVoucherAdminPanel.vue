<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/gallery'

const PAYMENT_TYPE_OPTIONS = [
  { value: 'PAYPAL_TRANSFER', label: 'PayPal转账' },
  { value: 'PAYPAL_BILL', label: 'PayPal账单' },
  { value: 'CREDIT_CARD_LINK', label: '信用卡链接' },
  { value: 'BANK_TRANSFER', label: '银行转账' },
]

const voucherList = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const productPickerVisible = ref(false)
const productPickerLoading = ref(false)
const productPickerKeyword = ref('')
const productPickerProducts = ref([])
const convertCurrencyLoading = ref(false)
const convertTargetCurrency = ref('USD')

const shippingAddressList = ref([])
const paymentMethodList = ref([])
const configLoading = ref(false)

const shippingDialogVisible = ref(false)
const shippingDialogSaving = ref(false)
const shippingListLoading = ref(false)
const editingShippingId = ref(null)
const shippingCreateFromVoucher = ref(false)
const shippingAddressKeyword = ref('')

const paymentDialogVisible = ref(false)
const paymentDialogSaving = ref(false)
const paymentListLoading = ref(false)
const editingPaymentMethodId = ref(null)

const shareLoadingId = ref(null)
const detailLoadingId = ref(null)
const voidLoadingId = ref(null)

const pager = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const query = reactive({
  keyword: '',
  status: '',
  paymentStatus: '',
})

const form = reactive({
  id: null,
  status: 'ACTIVE',
  customerName: '',
  customerContactType: 'WECHAT',
  customerContactValue: '',
  shippingAddressId: null,
  paymentMethods: [],
  currencyCode: 'CNY',
  shippingFee: 0,
  discountAmount: 0,
  paidAmount: 0,
  remark: '',
  internalNote: '',
  expireTime: '',
  items: [],
})

const shippingForm = reactive({
  label: '',
  receiverName: '',
  receiverPhone: '',
  country: '',
  state: '',
  city: '',
  addressLine1: '',
  addressLine2: '',
  postalCode: '',
  remark: '',
  enabled: 1,
  sort: 0,
})

const paymentMethodForm = reactive({
  name: '',
  type: 'PAYPAL_TRANSFER',
  description: '',
  accountValue: '',
  bankFields: [],
  enabled: 1,
  sort: 0,
})

const contactTypeOptions = [
  { value: 'PERSON', label: '联系人' },
  { value: 'WECHAT', label: '微信' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'PHONE', label: '电话' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'CUSTOM', label: '自定义' },
]

const statusOptions = [
  { value: 'ACTIVE', label: '启用' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'VOID', label: '作废' },
]

const paymentStatusOptions = [
  { value: 'UNPAID', label: '未付款' },
  { value: 'PARTIAL', label: '部分付款' },
  { value: 'PAID', label: '已付清' },
]

const dialogTitle = computed(() => (form.id ? '编辑订单凭证' : '创建订单凭证'))
const shippingDialogTitle = computed(() => (editingShippingId.value ? '编辑收货地址' : '新增收货地址'))
const paymentDialogTitle = computed(() => (editingPaymentMethodId.value ? '编辑支付方式' : '新增支付方式'))

const subtotalAmount = computed(() => {
  return form.items.reduce((sum, item) => {
    return sum + toNumber(item.unitPrice) * toNumber(item.quantity || 0)
  }, 0)
})

const totalAmount = computed(() => {
  return Math.max(0, subtotalAmount.value + toNumber(form.shippingFee) - toNumber(form.discountAmount))
})

const balanceAmount = computed(() => {
  return Math.max(0, totalAmount.value - toNumber(form.paidAmount))
})

const paymentStatusText = computed(() => {
  const paid = toNumber(form.paidAmount)
  if (paid <= 0) return '未付款'
  if (paid >= totalAmount.value) return '已付清'
  return '部分付款'
})

const canConvertCurrency = computed(() => {
  return Boolean(form.id) && paymentStatusText.value === '未付款'
})

const selectedPaymentMethodIds = computed({
  get: () => form.paymentMethods.map((item) => item.methodId),
  set: (ids) => {
    const map = new Map(form.paymentMethods.map((item) => [item.methodId, item]))
    form.paymentMethods = ids.map((methodId) => ({
      methodId,
      payUrl: map.get(methodId)?.payUrl || '',
    }))
  },
})

const selectedLinkPaymentMethods = computed(() => {
  const selectedIds = new Set(selectedPaymentMethodIds.value)
  return paymentMethodList.value.filter((item) => {
    if (!selectedIds.has(item.id)) return false
    return item.type === 'PAYPAL_BILL' || item.type === 'CREDIT_CARD_LINK'
  })
})

const filteredShippingAddressList = computed(() => {
  const keyword = String(shippingAddressKeyword.value || '').trim().toLowerCase()
  const rows = Array.isArray(shippingAddressList.value) ? shippingAddressList.value : []
  if (!keyword) return rows
  return rows.filter((item) => {
    const haystack = [
      item?.label,
      item?.receiverName,
      item?.receiverPhone,
      item?.displayText,
    ].map((v) => String(v || '').toLowerCase()).join(' ')
    return haystack.includes(keyword)
  })
})

const hasShippingAddressMatch = computed(() => filteredShippingAddressList.value.length > 0)
const selectedShippingAddress = computed(() => {
  const id = form.shippingAddressId
  if (!id) return null
  return shippingAddressList.value.find((item) => item.id === id) || null
})

const isBankTransferType = computed(() => paymentMethodForm.type === 'BANK_TRANSFER')
const isPaypalTransferType = computed(() => paymentMethodForm.type === 'PAYPAL_TRANSFER')
const selectedPaymentMethodNames = computed(() => {
  const map = new Map(paymentMethodList.value.map((item) => [item.id, item.name]))
  return selectedPaymentMethodIds.value.map((id) => map.get(id)).filter((name) => Boolean(name))
})

const createEmptyItem = (patch = {}) => ({
  localId: 'voucher-item-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
  productId: null,
  title: '',
  sku: '',
  imageUrl: '',
  unitPrice: 0,
  quantity: 1,
  remark: '',
  uploading: false,
  ...patch,
})

const createEmptyBankField = (patch = {}) => ({
  label: '',
  value: '',
  copyValue: '',
  ...patch,
})

const draggingBankFieldIndex = ref(-1)

const resetForm = () => {
  form.id = null
  form.status = 'ACTIVE'
  form.customerName = ''
  form.customerContactType = 'WECHAT'
  form.customerContactValue = ''
  form.shippingAddressId = null
  form.paymentMethods = []
  form.currencyCode = 'CNY'
  form.shippingFee = 0
  form.discountAmount = 0
  form.paidAmount = 0
  form.remark = ''
  form.internalNote = ''
  form.expireTime = ''
  form.items = [createEmptyItem()]
  shippingAddressKeyword.value = ''
  convertTargetCurrency.value = 'USD'
}

const resetShippingForm = () => {
  editingShippingId.value = null
  shippingForm.label = ''
  shippingForm.receiverName = ''
  shippingForm.receiverPhone = ''
  shippingForm.country = ''
  shippingForm.state = ''
  shippingForm.city = ''
  shippingForm.addressLine1 = ''
  shippingForm.addressLine2 = ''
  shippingForm.postalCode = ''
  shippingForm.remark = ''
  shippingForm.enabled = 1
  shippingForm.sort = 0
}

const resetPaymentMethodForm = () => {
  editingPaymentMethodId.value = null
  paymentMethodForm.name = ''
  paymentMethodForm.type = 'PAYPAL_TRANSFER'
  paymentMethodForm.description = ''
  paymentMethodForm.accountValue = ''
  paymentMethodForm.bankFields = []
  paymentMethodForm.enabled = 1
  paymentMethodForm.sort = 0
}

const statusLabel = (value) => {
  const hit = statusOptions.find((item) => item.value === value)
  return hit ? hit.label : value || '-'
}

const paymentStatusLabel = (value) => {
  const hit = paymentStatusOptions.find((item) => item.value === value)
  return hit ? hit.label : value || '-'
}

const paymentMethodTypeLabel = (type) => {
  const hit = PAYMENT_TYPE_OPTIONS.find((item) => item.value === type)
  return hit ? hit.label : type || '-'
}

const shippingOptionLabel = (item) => {
  if (!item) return ''
  const receiver = String(item.receiverName || '').trim()
  const label = String(item.label || '').trim()
  if (receiver && label) {
    return `${receiver} - ${label}`
  }
  return receiver || label || '-'
}

const currencySymbol = (code) => {
  const normalized = String(code || 'CNY').trim().toUpperCase()
  if (normalized === 'USD') return '$'
  if (normalized === 'EUR') return '€'
  if (normalized === 'GBP') return '£'
  return '¥'
}

const formatMoney = (value, currencyCode = 'CNY') => `${currencySymbol(currencyCode)} ${Number(value || 0).toFixed(2)}`

const formatDateTime = (value) => {
  if (!value) return '-'
  const normalized = String(value).replace('T', ' ')
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized
}

const buildVoucherUrl = (publicCode) => `/voucher/${publicCode}`
const buildPosterUrl = (publicCode) => `${import.meta.env.VITE_API_BASE || ''}/api/order-vouchers/poster/${publicCode}`

const onShippingAddressFilter = (keyword) => {
  shippingAddressKeyword.value = String(keyword || '')
}

const getSelectedPaymentMethod = (methodId) => {
  return form.paymentMethods.find((item) => item.methodId === methodId)
}

const fetchVouchers = async () => {
  loading.value = true
  try {
    const data = await api.adminOrderVouchers({
      page: pager.page,
      size: pager.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      paymentStatus: query.paymentStatus || undefined,
    })
    voucherList.value = data.records || []
    pager.total = Number(data.total || 0)
  } catch (error) {
    ElMessage.error(error.message || '订单凭证加载失败')
  } finally {
    loading.value = false
  }
}

const fetchVoucherConfigs = async () => {
  configLoading.value = true
  try {
    const [addresses, methods] = await Promise.all([
      api.listOrderVoucherShippingAddresses(),
      api.listOrderVoucherPaymentMethods(),
    ])
    shippingAddressList.value = Array.isArray(addresses) ? addresses : []
    paymentMethodList.value = Array.isArray(methods) ? methods : []
  } catch (error) {
    ElMessage.error(error.message || '账单配置加载失败')
  } finally {
    configLoading.value = false
  }
}

const fetchShippingAddressList = async () => {
  shippingListLoading.value = true
  try {
    const rows = await api.listOrderVoucherShippingAddresses()
    shippingAddressList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    ElMessage.error(error.message || '收货地址加载失败')
  } finally {
    shippingListLoading.value = false
  }
}

const fetchPaymentMethodList = async () => {
  paymentListLoading.value = true
  try {
    const rows = await api.listOrderVoucherPaymentMethods()
    paymentMethodList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    ElMessage.error(error.message || '支付方式加载失败')
  } finally {
    paymentListLoading.value = false
  }
}

const fetchPickerProducts = async () => {
  productPickerLoading.value = true
  try {
    const data = await api.adminProducts({
      page: 1,
      size: 12,
      keyword: productPickerKeyword.value || undefined,
      status: 1,
    })
    productPickerProducts.value = data.records || []
  } catch (error) {
    ElMessage.error(error.message || '商品加载失败')
  } finally {
    productPickerLoading.value = false
  }
}

const openCreate = async () => {
  if (!shippingAddressList.value.length || !paymentMethodList.value.length) {
    await fetchVoucherConfigs()
  }
  resetForm()
  dialogVisible.value = true
}

const openCreateFromProduct = async (product) => {
  if (!shippingAddressList.value.length || !paymentMethodList.value.length) {
    await fetchVoucherConfigs()
  }
  resetForm()
  if (product) {
    form.items = [
      createEmptyItem({
        productId: product.id || null,
        title: product.title || '',
        sku: product.sku || '',
        imageUrl: product.coverImage || '',
        unitPrice: toNumber(product.price),
      }),
    ]
  }
  dialogVisible.value = true
}

const openEdit = async (row) => {
  if (!row || !row.id) return
  detailLoadingId.value = row.id
  try {
    if (!shippingAddressList.value.length || !paymentMethodList.value.length) {
      await fetchVoucherConfigs()
    }
    const data = await api.adminOrderVoucherDetail(row.id)
    applyVoucherDetail(data)
    convertTargetCurrency.value = (data.currencyCode || 'CNY') === 'USD' ? 'EUR' : 'USD'
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '凭证详情加载失败')
  } finally {
    detailLoadingId.value = null
  }
}

const openShippingDialog = async (fromVoucher = false, preset = null) => {
  shippingCreateFromVoucher.value = fromVoucher
  shippingDialogVisible.value = true
  resetShippingForm()
  if (preset && typeof preset === 'object') {
    if (preset.receiverName) {
      shippingForm.receiverName = String(preset.receiverName)
    }
    if (preset.label) {
      shippingForm.label = String(preset.label)
    }
  }
  await fetchShippingAddressList()
}

const createShippingAddressFromSearch = async () => {
  const keyword = String(shippingAddressKeyword.value || '').trim()
  await openShippingDialog(true, {
    receiverName: keyword,
    label: keyword ? `${keyword} 地址` : '',
  })
}

const openPaymentDialog = async () => {
  paymentDialogVisible.value = true
  resetPaymentMethodForm()
  await fetchPaymentMethodList()
}

const editShippingAddress = (row) => {
  editingShippingId.value = row.id
  shippingForm.label = row.label || ''
  shippingForm.receiverName = row.receiverName || ''
  shippingForm.receiverPhone = row.receiverPhone || ''
  shippingForm.country = row.country || ''
  shippingForm.state = row.state || ''
  shippingForm.city = row.city || ''
  shippingForm.addressLine1 = row.addressLine1 || ''
  shippingForm.addressLine2 = row.addressLine2 || ''
  shippingForm.postalCode = row.postalCode || ''
  shippingForm.remark = row.remark || ''
  shippingForm.enabled = row.enabled === 0 ? 0 : 1
  shippingForm.sort = Number(row.sort || 0)
}

const saveShippingAddress = async () => {
  const payload = {
    label: String(shippingForm.label || '').trim() || undefined,
    receiverName: String(shippingForm.receiverName || '').trim() || undefined,
    receiverPhone: String(shippingForm.receiverPhone || '').trim() || undefined,
    country: String(shippingForm.country || '').trim() || undefined,
    state: String(shippingForm.state || '').trim() || undefined,
    city: String(shippingForm.city || '').trim() || undefined,
    addressLine1: String(shippingForm.addressLine1 || '').trim() || undefined,
    addressLine2: String(shippingForm.addressLine2 || '').trim() || undefined,
    postalCode: String(shippingForm.postalCode || '').trim() || undefined,
    remark: String(shippingForm.remark || '').trim() || undefined,
    enabled: shippingForm.enabled === 0 ? 0 : 1,
    sort: Number(shippingForm.sort || 0),
  }
  if (!payload.label) {
    ElMessage.warning('请填写收货地址名称')
    return
  }
  shippingDialogSaving.value = true
  try {
    let createdId = null
    if (editingShippingId.value) {
      await api.updateOrderVoucherShippingAddress(editingShippingId.value, payload)
      ElMessage.success('收货地址已更新')
    } else {
      createdId = await api.createOrderVoucherShippingAddress(payload)
      ElMessage.success('收货地址已创建')
    }
    resetShippingForm()
    await fetchShippingAddressList()
    if (shippingCreateFromVoucher.value && createdId) {
      form.shippingAddressId = createdId
      shippingDialogVisible.value = false
      shippingCreateFromVoucher.value = false
    }
  } catch (error) {
    ElMessage.error(error.message || '收货地址保存失败')
  } finally {
    shippingDialogSaving.value = false
  }
}

const deleteShippingAddress = async (row) => {
  await ElMessageBox.confirm(`确认删除收货地址 ${row.label || ''} 吗？`, '提示', { type: 'warning' })
  try {
    await api.deleteOrderVoucherShippingAddress(row.id)
    ElMessage.success('收货地址已删除')
    await fetchShippingAddressList()
    if (form.shippingAddressId === row.id) {
      form.shippingAddressId = null
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '删除失败')
  }
}

const addBankField = () => {
  paymentMethodForm.bankFields.push(createEmptyBankField())
}

const removeBankField = (index) => {
  paymentMethodForm.bankFields.splice(index, 1)
}

const onBankFieldDragStart = (index) => {
  draggingBankFieldIndex.value = index
}

const onBankFieldDrop = (targetIndex) => {
  const from = draggingBankFieldIndex.value
  draggingBankFieldIndex.value = -1
  if (from < 0 || from === targetIndex) return
  const rows = paymentMethodForm.bankFields || []
  if (from >= rows.length || targetIndex >= rows.length) return
  const moved = rows.splice(from, 1)[0]
  rows.splice(targetIndex, 0, moved)
}

const onBankFieldDragEnd = () => {
  draggingBankFieldIndex.value = -1
}

const editPaymentMethod = (row) => {
  editingPaymentMethodId.value = row.id
  paymentMethodForm.name = row.name || ''
  paymentMethodForm.type = row.type || 'PAYPAL_TRANSFER'
  paymentMethodForm.description = row.description || ''
  paymentMethodForm.accountValue = row.accountValue || ''
  paymentMethodForm.enabled = row.enabled === 0 ? 0 : 1
  paymentMethodForm.sort = Number(row.sort || 0)
  paymentMethodForm.bankFields = Array.isArray(row.bankFields)
    ? row.bankFields.map((item) => createEmptyBankField({
        label: item.label || '',
        value: item.value || '',
        copyValue: item.copyValue || '',
      }))
    : []
}

const savePaymentMethod = async () => {
  const payload = {
    name: String(paymentMethodForm.name || '').trim() || undefined,
    type: paymentMethodForm.type,
    description: String(paymentMethodForm.description || '').trim() || undefined,
    accountValue: String(paymentMethodForm.accountValue || '').trim() || undefined,
    bankFields: (paymentMethodForm.bankFields || [])
      .map((item) => ({
        label: String(item.label || '').trim(),
        value: String(item.value || '').trim(),
        copyValue: String(item.copyValue || '').trim(),
      }))
      .filter((item) => item.label || item.value || item.copyValue),
    enabled: paymentMethodForm.enabled === 0 ? 0 : 1,
    sort: Number(paymentMethodForm.sort || 0),
  }
  if (!payload.name) {
    ElMessage.warning('请填写支付方式名称')
    return
  }
  if (payload.type === 'PAYPAL_TRANSFER' && !payload.accountValue) {
    ElMessage.warning('PayPal转账请填写收款账号')
    return
  }
  if (payload.type === 'BANK_TRANSFER' && !payload.bankFields.length) {
    ElMessage.warning('银行转账至少填写一项收款信息')
    return
  }
  paymentDialogSaving.value = true
  try {
    if (editingPaymentMethodId.value) {
      await api.updateOrderVoucherPaymentMethod(editingPaymentMethodId.value, payload)
      ElMessage.success('支付方式已更新')
    } else {
      await api.createOrderVoucherPaymentMethod(payload)
      ElMessage.success('支付方式已创建')
    }
    resetPaymentMethodForm()
    await fetchPaymentMethodList()
  } catch (error) {
    ElMessage.error(error.message || '支付方式保存失败')
  } finally {
    paymentDialogSaving.value = false
  }
}

const deletePaymentMethod = async (row) => {
  await ElMessageBox.confirm(`确认删除支付方式 ${row.name || ''} 吗？`, '提示', { type: 'warning' })
  try {
    await api.deleteOrderVoucherPaymentMethod(row.id)
    ElMessage.success('支付方式已删除')
    await fetchPaymentMethodList()
    selectedPaymentMethodIds.value = selectedPaymentMethodIds.value.filter((id) => id !== row.id)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '删除失败')
  }
}

const addManualItem = () => {
  form.items.push(createEmptyItem())
}

const removeItem = (index) => {
  form.items.splice(index, 1)
  if (!form.items.length) {
    form.items.push(createEmptyItem())
  }
}

const openProductPicker = async () => {
  productPickerVisible.value = true
  await fetchPickerProducts()
}

const addProductToVoucher = (product) => {
  form.items.push(createEmptyItem({
    productId: product.id || null,
    title: product.title || '',
    sku: product.sku || '',
    imageUrl: product.coverImage || '',
    unitPrice: toNumber(product.price),
  }))
  ElMessage.success('已加入账单明细')
}

const buildPayload = () => {
  const items = []
  for (let i = 0; i < form.items.length; i += 1) {
    const row = form.items[i] || {}
    const title = String(row.title || '').trim()
    const hasAnyValue = Boolean(title || row.productId || row.imageUrl || toNumber(row.unitPrice) > 0)
    if (!hasAnyValue) continue
    if (!title && !row.productId) {
      ElMessage.warning(`第 ${i + 1} 个商品缺少名称`)
      return null
    }
    const quantity = Number(row.quantity || 0)
    if (!quantity || quantity < 1) {
      ElMessage.warning(`第 ${i + 1} 个商品数量至少为 1`)
      return null
    }
    const unitPrice = toNumber(row.unitPrice)
    if (unitPrice < 0) {
      ElMessage.warning(`第 ${i + 1} 个商品单价不能为负`)
      return null
    }
    items.push({
      productId: row.productId || undefined,
      title: title || undefined,
      sku: String(row.sku || '').trim() || undefined,
      imageUrl: String(row.imageUrl || '').trim() || undefined,
      unitPrice,
      quantity,
      remark: String(row.remark || '').trim() || undefined,
      sort: i + 1,
    })
  }
  if (!items.length) {
    ElMessage.warning('至少添加1个商品')
    return null
  }

  for (const method of selectedLinkPaymentMethods.value) {
    const selected = getSelectedPaymentMethod(method.id)
    if (!selected || !String(selected.payUrl || '').trim()) {
      ElMessage.warning(`请填写 ${method.name} 的支付链接`)
      return null
    }
  }

  return {
    status: form.status,
    customerName: String(form.customerName || '').trim() || undefined,
    customerContactType: form.customerContactType || undefined,
    customerContactValue: String(form.customerContactValue || '').trim() || undefined,
    shippingAddressId: form.shippingAddressId || undefined,
    paymentMethods: form.paymentMethods.map((item) => ({
      methodId: item.methodId,
      payUrl: String(item.payUrl || '').trim() || undefined,
    })),
    currencyCode: String(form.currencyCode || 'CNY').trim().toUpperCase(),
    shippingFee: toNumber(form.shippingFee),
    discountAmount: toNumber(form.discountAmount),
    paidAmount: toNumber(form.paidAmount),
    remark: String(form.remark || '').trim() || undefined,
    internalNote: String(form.internalNote || '').trim() || undefined,
    expireTime: form.expireTime || undefined,
    items,
  }
}

const saveVoucher = async () => {
  const payload = buildPayload()
  if (!payload) return
  saving.value = true
  try {
    if (form.id) {
      await api.updateOrderVoucher(form.id, payload)
      ElMessage.success('订单凭证已更新')
      await fetchVouchers()
    } else {
      const createdId = await api.createOrderVoucher(payload)
      ElMessage.success('订单凭证已创建')
      pager.page = 1
      query.status = ''
      query.paymentStatus = ''
      query.keyword = ''
      if (createdId) {
        try {
          const createdVoucher = await api.adminOrderVoucherDetail(createdId)
          query.keyword = createdVoucher.voucherNo || ''
        } catch (_error) {
        }
      }
      await fetchVouchers()
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const applyVoucherDetail = (data) => {
  form.id = data.id || null
  form.status = data.status || 'ACTIVE'
  form.customerName = data.customerName || ''
  form.customerContactType = data.customerContactType || 'WECHAT'
  form.customerContactValue = data.customerContactValue || ''
  form.shippingAddressId = data.shippingAddressId || null
  form.paymentMethods = Array.isArray(data.paymentMethods)
    ? data.paymentMethods
      .filter((item) => item && item.methodId)
      .map((item) => ({
        methodId: item.methodId,
        payUrl: item.payUrl || '',
      }))
    : []
  form.currencyCode = data.currencyCode || 'CNY'
  form.shippingFee = toNumber(data.shippingFee)
  form.discountAmount = toNumber(data.discountAmount)
  form.paidAmount = toNumber(data.paidAmount)
  form.remark = data.remark || ''
  form.internalNote = data.internalNote || ''
  form.expireTime = data.expireTime || ''
  shippingAddressKeyword.value = ''
  form.items = Array.isArray(data.items) && data.items.length
    ? data.items.map((item) => createEmptyItem({
      localId: 'voucher-item-' + item.id,
      productId: item.productId || null,
      title: item.title || '',
      sku: item.sku || '',
      imageUrl: item.imageUrl || '',
      unitPrice: toNumber(item.unitPrice),
      quantity: Number(item.quantity || 1),
      remark: item.remark || '',
    }))
    : [createEmptyItem()]
}

const convertCurrency = async () => {
  if (!form.id) return
  if (String(convertTargetCurrency.value || '').trim().toUpperCase() === String(form.currencyCode || '').trim().toUpperCase()) {
    ElMessage.warning('目标币种与当前币种相同')
    return
  }
  convertCurrencyLoading.value = true
  try {
    const data = await api.convertOrderVoucherCurrency(form.id, {
      targetCurrency: convertTargetCurrency.value,
    })
    applyVoucherDetail(data)
    ElMessage.success('账单币种已转换')
    await fetchVouchers()
  } catch (error) {
    ElMessage.error(error.message || '币种转换失败')
  } finally {
    convertCurrencyLoading.value = false
  }
}

const voidVoucher = async (row) => {
  if (!row || !row.id) return
  await ElMessageBox.confirm(`确认作废凭证 ${row.voucherNo || ''} 吗？`, '提示', { type: 'warning' })
  voidLoadingId.value = row.id
  try {
    await api.voidOrderVoucher(row.id)
    ElMessage.success('订单凭证已作废')
    await fetchVouchers()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '作废失败')
  } finally {
    voidLoadingId.value = null
  }
}

const copyToClipboard = async (text) => {
  const value = String(text || '').trim()
  if (!value) {
    ElMessage.warning('没有可复制内容')
    return false
  }
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch (_error) {
  }
  const input = document.createElement('textarea')
  input.value = value
  document.body.appendChild(input)
  input.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(input)
  return ok
}

const shareVoucher = async (row) => {
  if (!row || !row.id) return
  shareLoadingId.value = row.id
  try {
    const data = await api.shareOrderVoucher(row.id)
    const copied = await copyToClipboard(data.copyText)
    ElMessage.success(copied ? '发送文案已复制，可直接发给客户' : '已生成发送文案')
    await fetchVouchers()
  } catch (error) {
    ElMessage.error(error.message || '生成发送文案失败')
  } finally {
    shareLoadingId.value = null
  }
}

const previewVoucher = (row) => {
  if (!row || !row.publicCode) return
  window.open(buildVoucherUrl(row.publicCode), '_blank')
}

const downloadPoster = (row) => {
  if (!row || !row.publicCode) return
  window.open(buildPosterUrl(row.publicCode), '_blank')
}

const uploadItemImage = async (option, item) => {
  if (!item) return
  item.uploading = true
  try {
    const data = await api.uploadImages([option.file])
    const url = data.urls && data.urls.length ? data.urls[0] : ''
    if (!url) {
      throw new Error('上传结果为空')
    }
    item.imageUrl = url
    option.onSuccess({ url }, option.file)
    ElMessage.success('图片上传成功')
  } catch (error) {
    option.onError(error)
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    item.uploading = false
  }
}

const pageChange = (page) => {
  pager.page = Number(page || 1)
  fetchVouchers()
}

const pageSizeChange = (size) => {
  pager.size = Number(size || 10)
  pager.page = 1
  fetchVouchers()
}

const search = () => {
  pager.page = 1
  fetchVouchers()
}

const toNumber = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

defineExpose({
  openCreate,
  openCreateFromProduct,
})

onMounted(async () => {
  resetForm()
  await Promise.all([fetchVouchers(), fetchVoucherConfigs()])
})
</script>

<template>
  <div class="rounded-3xl bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-5">
    <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <div>
        <p class="text-sm font-semibold text-zinc-800">订单凭证</p>
        <p class="text-sm text-zinc-500">支持管理收货地址、支付方式，并可在账单中勾选多个支付渠道。</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <el-button @click="openShippingDialog">收货地址管理</el-button>
        <el-button @click="openPaymentDialog">支付方式管理</el-button>
        <el-button type="primary" @click="openCreate">新建订单凭证</el-button>
      </div>
    </div>

    <div class="mt-4 grid grid-cols-1 gap-3 md:grid-cols-5">
      <el-input v-model="query.keyword" placeholder="凭证号 / 客户 / 联系方式" clearable @keyup.enter="search" />
      <el-select v-model="query.status" clearable placeholder="凭证状态">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.paymentStatus" clearable placeholder="支付状态">
        <el-option v-for="item in paymentStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="openCreate">创建</el-button>
    </div>

    <el-table :data="voucherList" class="mt-4" stripe v-loading="loading">
      <el-table-column prop="voucherNo" label="凭证号" min-width="160" />
      <el-table-column label="客户" min-width="170">
        <template #default="{ row }">
          <div class="text-sm text-zinc-800">{{ row.customerName || '-' }}</div>
          <div class="text-xs text-zinc-500">{{ row.customerContactValue || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="itemSummary" label="商品摘要" min-width="260" />
      <el-table-column label="金额" min-width="130">
        <template #default="{ row }">
          <div class="font-semibold text-zinc-800">{{ formatMoney(row.totalAmount, row.currencyCode) }}</div>
          <div class="text-xs text-zinc-500">待付 {{ formatMoney(row.balanceAmount, row.currencyCode) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="支付状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.paymentStatus === 'PAID' ? 'success' : row.paymentStatus === 'PARTIAL' ? 'warning' : 'info'">
            {{ paymentStatusLabel(row.paymentStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'VOID' ? 'danger' : 'info'">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分享/查看" width="120">
        <template #default="{ row }">
          <div>{{ row.shareCount || 0 }} / {{ row.viewCount || 0 }}</div>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :loading="shareLoadingId === row.id" @click="shareVoucher(row)">复制发送文案</el-button>
          <el-button link type="primary" :loading="detailLoadingId === row.id" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="previewVoucher(row)">预览</el-button>
          <el-button link type="warning" @click="downloadPoster(row)">凭证图</el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status === 'VOID'"
            :loading="voidLoadingId === row.id"
            @click="voidVoucher(row)"
          >
            作废
          </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(1180px, 96vw)">
      <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
        <el-form-item label="凭证状态">
          <el-select v-model="form.status">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户姓名">
          <el-input v-model="form.customerName" placeholder="例如：Alice" />
        </el-form-item>
        <el-form-item label="客户联系方式">
          <div class="flex w-full gap-2">
            <el-select v-model="form.customerContactType" class="w-[160px]">
              <el-option v-for="item in contactTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="form.customerContactValue" placeholder="例如：+44 7000 000000" />
          </div>
        </el-form-item>
        <el-form-item label="收货地址">
          <div class="w-full">
            <div class="flex w-full gap-2">
              <el-select
                v-model="form.shippingAddressId"
                clearable
                filterable
                :filter-method="onShippingAddressFilter"
                placeholder="输入收货人姓名搜索"
                class="flex-1"
              >
                <el-option
                  v-for="item in filteredShippingAddressList"
                  :key="item.id"
                  :label="shippingOptionLabel(item)"
                  :value="item.id"
                />
                <template #empty>
                  <div class="px-2 py-1.5 text-xs text-zinc-500">
                    <span v-if="shippingAddressKeyword">未找到匹配地址</span>
                    <span v-else>暂无地址</span>
                    <el-button
                      v-if="shippingAddressKeyword && !hasShippingAddressMatch"
                      class="ml-2"
                      link
                      type="primary"
                      @click.stop="createShippingAddressFromSearch"
                    >
                      新增该收货人地址
                    </el-button>
                  </div>
                </template>
              </el-select>
              <el-button @click="openShippingDialog(true)">新增地址</el-button>
            </div>
            <div v-if="selectedShippingAddress" class="mt-2 rounded-lg border border-zinc-200 bg-zinc-50 px-2.5 py-2">
              <p class="text-xs font-semibold text-zinc-700">{{ shippingOptionLabel(selectedShippingAddress) }}</p>
              <p class="mt-1 whitespace-pre-line text-[11px] text-zinc-500">{{ selectedShippingAddress.displayText }}</p>
            </div>
            <div v-else class="mt-2 text-[11px] text-zinc-400">
              未选择收货地址
            </div>
          </div>
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="form.currencyCode">
            <el-option label="CNY" value="CNY" />
            <el-option label="USD" value="USD" />
            <el-option label="EUR" value="EUR" />
            <el-option label="GBP" value="GBP" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示不过期"
            class="w-full"
          />
        </el-form-item>
      </div>

      <div class="mt-2 rounded-2xl border border-zinc-200 bg-zinc-50 p-4" v-loading="configLoading">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="text-sm font-semibold text-zinc-800">支付方式</p>
            <p class="text-xs text-zinc-500">可勾选多个支付方式；PayPal账单和信用卡链接需填写本账单专属支付地址。</p>
          </div>
          <el-button size="small" @click="openPaymentDialog">管理支付方式</el-button>
        </div>
        <el-select v-model="selectedPaymentMethodIds" multiple placeholder="请选择支付方式" class="w-full">
          <el-option
            v-for="item in paymentMethodList"
            :key="item.id"
            :label="`${item.name}（${paymentMethodTypeLabel(item.type)}）`"
            :value="item.id"
            :disabled="item.enabled === 0"
          />
        </el-select>
        <p v-if="selectedPaymentMethodNames.length" class="mt-2 text-xs text-zinc-600">
          已选择：{{ selectedPaymentMethodNames.join('、') }}
        </p>
        <div v-if="selectedLinkPaymentMethods.length" class="mt-3 space-y-3">
          <div v-for="method in selectedLinkPaymentMethods" :key="method.id" class="rounded-xl border border-zinc-200 bg-white p-3">
            <p class="text-xs font-semibold text-zinc-700">{{ method.name }} 支付链接</p>
            <el-input
              v-model="getSelectedPaymentMethod(method.id).payUrl"
              placeholder="https://..."
              class="mt-2"
            />
          </div>
        </div>
      </div>

      <div class="mt-2 rounded-2xl border border-zinc-200 bg-zinc-50 p-4">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="text-sm font-semibold text-zinc-800">商品明细</p>
            <p class="text-xs text-zinc-500">可直接手工录入商品，也可从系统商品库快速带入。</p>
          </div>
          <div class="flex gap-2">
            <el-button @click="addManualItem">新增手工商品</el-button>
            <el-button type="primary" @click="openProductPicker">从商品库添加</el-button>
          </div>
        </div>

        <div v-for="(item, index) in form.items" :key="item.localId" class="mb-3 rounded-2xl border border-zinc-200 bg-white p-4">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-semibold text-zinc-700">商品 {{ index + 1 }}</span>
            <el-button link type="danger" @click="removeItem(index)">删除</el-button>
          </div>
          <div class="grid grid-cols-1 gap-3 md:grid-cols-[140px_repeat(4,minmax(0,1fr))]">
            <div>
              <div class="mb-2 h-28 overflow-hidden rounded-xl border border-zinc-200 bg-zinc-50">
                <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="h-full w-full object-cover" />
                <div v-else class="grid h-full place-items-center text-xs text-zinc-400">未设置图片</div>
              </div>
              <el-upload
                :show-file-list="false"
                accept="image/*"
                :http-request="(option) => uploadItemImage(option, item)"
              >
                <el-button class="w-full" size="small" :loading="item.uploading">上传图片</el-button>
              </el-upload>
            </div>
            <el-input v-model="item.title" placeholder="商品名称" />
            <el-input v-model="item.sku" placeholder="货号 / 备注编码" />
            <el-input-number v-model="item.unitPrice" :min="0" :precision="2" :step="10" class="w-full" />
            <el-input-number v-model="item.quantity" :min="1" :step="1" class="w-full" />
            <div class="rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm text-zinc-700">
              小计：{{ formatMoney(toNumber(item.unitPrice) * toNumber(item.quantity), form.currencyCode) }}
            </div>
          </div>
          <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
            <el-input v-model="item.imageUrl" placeholder="图片地址（可直接粘贴 URL）" />
            <el-input v-model="item.remark" placeholder="商品备注（可选）" />
          </div>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-4 md:grid-cols-4">
        <el-form-item label="运费">
          <el-input-number v-model="form.shippingFee" :min="0" :precision="2" :step="10" class="w-full" />
        </el-form-item>
        <el-form-item label="优惠金额">
          <el-input-number v-model="form.discountAmount" :min="0" :precision="2" :step="10" class="w-full" />
        </el-form-item>
        <el-form-item label="已付金额">
          <el-input-number v-model="form.paidAmount" :min="0" :precision="2" :step="10" class="w-full" />
        </el-form-item>
        <div class="rounded-2xl border border-emerald-200 bg-emerald-50 p-3">
          <p class="text-xs text-zinc-500">账单汇总</p>
          <p class="mt-1 text-sm text-zinc-700">商品小计：{{ formatMoney(subtotalAmount, form.currencyCode) }}</p>
          <p class="text-sm text-zinc-700">总金额：{{ formatMoney(totalAmount, form.currencyCode) }}</p>
          <p class="text-sm text-zinc-700">待支付：{{ formatMoney(balanceAmount, form.currencyCode) }}</p>
          <p class="mt-1 text-sm font-semibold text-emerald-700">{{ paymentStatusText }}</p>
        </div>
      </div>

      <div v-if="canConvertCurrency" class="mt-4 rounded-2xl border border-sky-200 bg-sky-50 p-4">
        <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p class="text-sm font-semibold text-sky-900">未付款账单币种转换</p>
            <p class="text-xs text-sky-700">将整张账单按当前汇率转换为其他币种，并同步更新明细单价与汇总金额。</p>
          </div>
          <div class="flex gap-2">
            <el-select v-model="convertTargetCurrency" class="w-[120px]">
              <el-option label="CNY" value="CNY" />
              <el-option label="USD" value="USD" />
              <el-option label="EUR" value="EUR" />
              <el-option label="GBP" value="GBP" />
              <el-option label="JPY" value="JPY" />
            </el-select>
            <el-button type="primary" :loading="convertCurrencyLoading" @click="convertCurrency">转换币种</el-button>
          </div>
        </div>
      </div>

      <div class="mt-2 grid grid-cols-1 gap-4 md:grid-cols-2">
        <el-form-item label="客户可见备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="例如：请在 24 小时内完成付款" />
        </el-form-item>
        <el-form-item label="内部备注">
          <el-input v-model="form.internalNote" type="textarea" :rows="3" placeholder="仅后台可见" />
        </el-form-item>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveVoucher">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="shippingDialogVisible" :title="shippingDialogTitle" width="min(980px, 96vw)">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
        <el-input v-model="shippingForm.label" placeholder="地址名称（例如：UK仓）" />
        <el-input v-model="shippingForm.receiverName" placeholder="收件人" />
        <el-input v-model="shippingForm.receiverPhone" placeholder="电话" />
        <el-input v-model="shippingForm.country" placeholder="国家" />
        <el-input v-model="shippingForm.state" placeholder="省/州" />
        <el-input v-model="shippingForm.city" placeholder="城市" />
        <el-input v-model="shippingForm.addressLine1" placeholder="地址1" />
        <el-input v-model="shippingForm.addressLine2" placeholder="地址2（可选）" />
        <el-input v-model="shippingForm.postalCode" placeholder="邮编" />
        <el-input v-model="shippingForm.remark" placeholder="备注（可选）" />
        <el-input-number v-model="shippingForm.sort" :step="1" class="w-full" placeholder="排序" />
        <el-select v-model="shippingForm.enabled">
          <el-option :value="1" label="启用" />
          <el-option :value="0" label="停用" />
        </el-select>
      </div>
      <div class="mt-3 flex justify-end gap-2">
        <el-button @click="resetShippingForm">重置</el-button>
        <el-button type="primary" :loading="shippingDialogSaving" @click="saveShippingAddress">保存地址</el-button>
      </div>

      <el-table class="mt-4" :data="shippingAddressList" stripe v-loading="shippingListLoading">
        <el-table-column prop="label" label="名称" min-width="120" />
        <el-table-column label="地址信息" min-width="280">
          <template #default="{ row }">
            <div class="whitespace-pre-line text-xs text-zinc-700">{{ row.displayText }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 0 ? 'info' : 'success'">{{ row.enabled === 0 ? '停用' : '启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">{{ row.sort || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="editShippingAddress(row)">编辑</el-button>
            <el-button link type="danger" @click="deleteShippingAddress(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="paymentDialogVisible" :title="paymentDialogTitle" width="min(1040px, 96vw)">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
        <el-input v-model="paymentMethodForm.name" placeholder="支付方式名称" />
        <el-select v-model="paymentMethodForm.type">
          <el-option v-for="item in PAYMENT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="paymentMethodForm.enabled">
          <el-option :value="1" label="启用" />
          <el-option :value="0" label="停用" />
        </el-select>
        <el-input v-model="paymentMethodForm.description" class="md:col-span-2" placeholder="描述（展示给客户）" />
        <el-input-number v-model="paymentMethodForm.sort" :step="1" class="w-full" placeholder="排序" />
        <el-input v-if="isPaypalTransferType" v-model="paymentMethodForm.accountValue" class="md:col-span-3" placeholder="PayPal收款账号" />
      </div>

      <div v-if="isBankTransferType" class="mt-3 rounded-xl border border-zinc-200 bg-zinc-50 p-3">
        <div class="mb-2 flex items-center justify-between">
          <p class="text-sm font-semibold text-zinc-800">银行转账字段</p>
          <el-button size="small" @click="addBankField">新增字段</el-button>
        </div>
        <div v-if="!paymentMethodForm.bankFields.length" class="text-xs text-zinc-500">未配置字段</div>
        <div
          v-for="(field, index) in paymentMethodForm.bankFields"
          :key="index"
          class="mb-2 grid grid-cols-1 gap-2 rounded-lg border border-zinc-200 bg-white p-2 md:grid-cols-[56px_180px_1fr_1fr_auto]"
          draggable="true"
          @dragstart="onBankFieldDragStart(index)"
          @dragend="onBankFieldDragEnd"
          @dragover.prevent
          @drop.prevent="onBankFieldDrop(index)"
        >
          <div class="grid place-items-center text-xs font-semibold text-zinc-400">拖拽</div>
          <el-input v-model="field.label" placeholder="字段名（如 Bank Name）" />
          <el-input v-model="field.value" placeholder="展示内容" />
          <el-input v-model="field.copyValue" placeholder="复制内容（可空，不填则不显示复制按钮）" />
          <el-button link type="danger" @click="removeBankField(index)">删除</el-button>
        </div>
      </div>

      <div class="mt-3 flex justify-end gap-2">
        <el-button @click="resetPaymentMethodForm">重置</el-button>
        <el-button type="primary" :loading="paymentDialogSaving" @click="savePaymentMethod">保存支付方式</el-button>
      </div>

      <el-table class="mt-4" :data="paymentMethodList" stripe v-loading="paymentListLoading">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            {{ paymentMethodTypeLabel(row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" />
        <el-table-column label="附加信息" min-width="220">
          <template #default="{ row }">
            <div v-if="row.type === 'PAYPAL_TRANSFER'" class="text-xs text-zinc-700 break-all">
              {{ row.accountValue || '-' }}
            </div>
            <div v-else-if="row.type === 'BANK_TRANSFER'" class="text-xs text-zinc-700">
              {{ Array.isArray(row.bankFields) ? row.bankFields.length : 0 }} 项银行字段
            </div>
            <div v-else class="text-xs text-zinc-500">账单中手动填写链接</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 0 ? 'info' : 'success'">{{ row.enabled === 0 ? '停用' : '启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="editPaymentMethod(row)">编辑</el-button>
            <el-button link type="danger" @click="deletePaymentMethod(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="productPickerVisible" title="从商品库添加" width="min(980px, 94vw)">
      <div class="mb-4 flex gap-2">
        <el-input v-model="productPickerKeyword" placeholder="搜索商品标题 / 货号" clearable @keyup.enter="fetchPickerProducts" />
        <el-button type="primary" :loading="productPickerLoading" @click="fetchPickerProducts">搜索</el-button>
      </div>
      <el-table :data="productPickerProducts" stripe v-loading="productPickerLoading">
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <img :src="row.coverImage" alt="" class="h-14 w-14 rounded-lg object-cover" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column prop="sku" label="货号" width="120" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.price, 'CNY') }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="addProductToVoucher(row)">加入账单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
