<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/gallery'
import { applyPageMeta } from '../utils/pageMeta'

const route = useRoute()
const loading = ref(false)
const errorText = ref('')
const voucher = ref(null)
const profile = ref(null)
const showAllItems = ref(false)
const isMobileViewport = ref(false)

const bankDialogVisible = ref(false)
const activeBankMethod = ref(null)

const MOBILE_COLLAPSE_COUNT = 4
const DESKTOP_COLLAPSE_COUNT = 8

const currencySymbol = (code) => {
  const normalized = String(code || 'CNY').trim().toUpperCase()
  if (normalized === 'USD') return '$'
  if (normalized === 'EUR') return '€'
  if (normalized === 'GBP') return '£'
  return '¥'
}

const formatMoney = (value, code) => `${currencySymbol(code)} ${Number(value || 0).toFixed(2)}`

const paymentStatusLabel = (value) => {
  if (value === 'PAID') return 'Paid'
  if (value === 'PARTIAL') return 'Partially Paid'
  return 'Unpaid'
}

const itemCount = computed(() => Array.isArray(voucher.value?.items) ? voucher.value.items.length : 0)
const canShowContacts = computed(() => Array.isArray(voucher.value?.contacts) && voucher.value.contacts.length > 0)
const paymentMethods = computed(() => Array.isArray(voucher.value?.paymentMethods) ? voucher.value.paymentMethods : [])
const canShowPaymentMethods = computed(() => paymentMethods.value.length > 0)
const hasShippingAddress = computed(() => String(voucher.value?.shippingAddressSnapshot || '').trim().length > 0)
const collapsedItemCount = computed(() => (isMobileViewport.value ? MOBILE_COLLAPSE_COUNT : DESKTOP_COLLAPSE_COUNT))
const shouldCollapseItems = computed(() => itemCount.value > collapsedItemCount.value)
const displayedItems = computed(() => {
  const items = Array.isArray(voucher.value?.items) ? voucher.value.items : []
  if (!shouldCollapseItems.value || showAllItems.value) {
    return items
  }
  return items.slice(0, collapsedItemCount.value)
})
const hiddenItemCount = computed(() => Math.max(0, itemCount.value - displayedItems.value.length))

const updateViewport = () => {
  if (typeof window === 'undefined') return
  isMobileViewport.value = window.innerWidth < 768
}

const hasText = (value) => String(value || '').trim().length > 0

const copyText = async (text) => {
  const value = String(text || '').trim()
  if (!value) return
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(value)
      ElMessage.success('Copied')
      return
    }
  } catch (_error) {
  }
  const input = document.createElement('textarea')
  input.value = value
  document.body.appendChild(input)
  input.select()
  document.execCommand('copy')
  document.body.removeChild(input)
  ElMessage.success('Copied')
}

const openBankDialog = (method) => {
  activeBankMethod.value = method || null
  bankDialogVisible.value = true
}

const paymentActionText = (method) => {
  if (!method) return ''
  if (method.type === 'PAYPAL_TRANSFER') return 'Copy account'
  if (method.type === 'PAYPAL_BILL') return 'Copy bill link'
  if (method.type === 'CREDIT_CARD_LINK') return 'Copy pay link'
  if (method.type === 'BANK_TRANSFER') return 'View bank details'
  return 'Copy'
}

const handlePaymentAction = (method) => {
  if (!method) return
  if (method.type === 'BANK_TRANSFER') {
    openBankDialog(method)
    return
  }
  if (method.type === 'PAYPAL_TRANSFER') {
    copyText(method.accountValue || '')
    return
  }
  if (method.type === 'PAYPAL_BILL' || method.type === 'CREDIT_CARD_LINK') {
    copyText(method.payUrl || '')
  }
}

const canShowPaymentAction = (method) => {
  if (!method) return false
  if (method.type === 'BANK_TRANSFER') {
    return Array.isArray(method.bankFields) && method.bankFields.length > 0
  }
  if (method.type === 'PAYPAL_TRANSFER') {
    return hasText(method.accountValue)
  }
  if (method.type === 'PAYPAL_BILL' || method.type === 'CREDIT_CARD_LINK') {
    return hasText(method.payUrl)
  }
  return false
}

const canCopyContact = (contact) => hasText(contact?.copyValue)
const canCopyBankField = (field) => hasText(field?.copyValue)

const loadVoucher = async () => {
  const code = route.params.code
  if (!code) return
  loading.value = true
  errorText.value = ''
  voucher.value = null
  showAllItems.value = false
  try {
    voucher.value = await api.publicOrderVoucherDetail(code)
    applyPageMeta({
      route,
      profile: profile.value,
      lang: 'en-us',
      data: { voucherNo: voucher.value && voucher.value.voucherNo ? voucher.value.voucherNo : '' },
    })
  } catch (error) {
    errorText.value = error.message || 'Voucher not found'
  } finally {
    loading.value = false
  }
}

const loadProfile = async () => {
  try {
    profile.value = await api.shopProfile()
  } catch (error) {
    console.warn('店铺配置加载失败', error)
  }
}

watch(() => route.params.code, loadVoucher)

onMounted(async () => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  await loadProfile()
  loadVoucher()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
})
</script>

<template>
  <div class="min-h-screen bg-[linear-gradient(180deg,#faf7f2_0%,#f4efe8_42%,#ffffff_100%)] px-3 py-4 text-zinc-900 md:px-8 md:py-6">
    <div class="mx-auto max-w-5xl">
      <div class="hidden rounded-[32px] bg-zinc-900 px-6 py-8 text-white shadow-[0_30px_90px_rgba(24,24,27,0.22)] md:block">
        <div class="flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
          <div class="flex items-center gap-4">
            <img
              v-if="voucher?.shopLogo"
              :src="voucher.shopLogo"
              alt="shop-logo"
              class="h-16 w-16 rounded-full border border-white/20 object-cover"
            />
            <div>
              <p class="text-xs uppercase tracking-[0.3em] text-amber-300">Order Voucher</p>
              <h1 class="mt-2 text-3xl font-black">{{ voucher?.shopName || 'Product Gallery' }}</h1>
            </div>
          </div>
          <div class="rounded-2xl border border-white/10 bg-white/10 px-4 py-3 text-sm backdrop-blur">
            <p class="text-zinc-300">Voucher No.</p>
            <p class="mt-1 text-lg font-bold">{{ voucher?.voucherNo || '-' }}</p>
            <p class="mt-2 text-zinc-300">Status</p>
            <p class="mt-1 font-semibold">{{ paymentStatusLabel(voucher?.paymentStatus) }}</p>
          </div>
        </div>
      </div>

      <div v-if="loading" class="mt-6 rounded-3xl bg-white p-10 text-center text-zinc-500 shadow-sm ring-1 ring-zinc-100">
        Loading voucher...
      </div>

      <div v-else-if="errorText" class="mt-6 rounded-3xl border border-rose-200 bg-rose-50 p-10 text-center text-rose-600 shadow-sm">
        {{ errorText }}
      </div>

      <div v-else-if="voucher" class="mt-4 grid gap-4 lg:mt-6 lg:grid-cols-[1.5fr_0.9fr] lg:gap-6">
        <section class="rounded-[28px] bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
          <div data-testid="voucher-mobile-summary" class="rounded-[24px] bg-zinc-900 p-4 text-white md:hidden">
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <p class="text-[11px] uppercase tracking-[0.22em] text-amber-300">Order Voucher</p>
                <p class="mt-2 text-lg font-black">{{ voucher.voucherNo || '-' }}</p>
                <p class="mt-2 text-sm text-zinc-300">{{ paymentStatusLabel(voucher.paymentStatus) }}</p>
              </div>
              <div class="shrink-0 text-right">
                <p class="text-[11px] uppercase tracking-[0.18em] text-zinc-400">Due</p>
                <p class="mt-2 text-xl font-black text-amber-300">{{ formatMoney(voucher.balanceAmount, voucher.currencyCode) }}</p>
              </div>
            </div>
          </div>

          <div class="mt-4 flex flex-col gap-3 border-b border-zinc-100 pb-4 md:mt-0 md:flex-row md:items-end md:justify-between">
            <div class="min-w-0">
              <p class="text-xs uppercase tracking-[0.26em] text-zinc-400">Customer</p>
              <h2 class="mt-2 text-lg font-black text-zinc-900 md:text-2xl">{{ voucher.customerName || 'Guest Customer' }}</h2>
              <p class="mt-2 text-[11px] font-medium uppercase tracking-[0.18em] text-zinc-400 md:text-xs">
                {{ itemCount }} {{ itemCount === 1 ? 'item' : 'items' }}
              </p>
            </div>
            <div class="hidden rounded-2xl bg-amber-50 px-4 py-3 text-right md:block">
              <p class="text-xs uppercase tracking-[0.22em] text-amber-700">Amount Due</p>
              <p class="mt-1 text-2xl font-black text-amber-900">{{ formatMoney(voucher.balanceAmount, voucher.currencyCode) }}</p>
            </div>
          </div>

          <div class="mt-4 space-y-2 md:mt-5 md:space-y-3">
            <div
              v-for="item in displayedItems"
              :key="item.id || item.sort"
              data-testid="voucher-item-card"
              class="rounded-2xl border border-zinc-100 bg-zinc-50 p-2.5 md:rounded-3xl md:p-4"
            >
              <div class="flex items-center gap-3 md:hidden">
                <div class="h-14 w-14 shrink-0 overflow-hidden rounded-md bg-white">
                  <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="h-full w-full object-cover" />
                  <div v-else class="grid h-full place-items-center text-[10px] text-zinc-400">No Image</div>
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="voucher-item-title text-[15px] font-black text-zinc-900">{{ item.title }}</h3>
                  <div class="mt-1 flex items-end gap-2">
                    <p class="text-[15px] font-black leading-none text-zinc-900">{{ formatMoney(item.lineAmount, voucher.currencyCode) }}</p>
                    <p class="text-[11px] leading-none text-zinc-500">Unit {{ formatMoney(item.unitPrice, voucher.currencyCode) }}</p>
                  </div>
                </div>
                <div class="shrink-0 pr-1">
                  <span class="inline-flex rounded-full bg-zinc-100 px-2.5 py-1 text-base font-semibold leading-none text-zinc-700">
                    X{{ item.quantity }}
                  </span>
                </div>
              </div>

              <div class="hidden md:grid md:grid-cols-[5.5rem_minmax(0,1fr)] md:gap-4">
                <div class="h-[5.5rem] w-[5.5rem] shrink-0 overflow-hidden rounded-2xl bg-white">
                  <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="h-full w-full object-cover" />
                  <div v-else class="grid h-full place-items-center text-xs text-zinc-400">No Image</div>
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0 pr-1">
                      <h3 class="voucher-item-title text-lg font-bold text-zinc-900">{{ item.title }}</h3>
                      <p v-if="item.sku" class="mt-1 text-sm text-zinc-400">
                        SKU: {{ item.sku }}
                      </p>
                      <p v-if="item.remark" class="mt-2 text-sm text-zinc-600">{{ item.remark }}</p>
                    </div>
                    <div class="shrink-0 text-right">
                      <div class="inline-flex rounded-full bg-white px-2.5 py-1 text-[11px] font-semibold text-zinc-600 ring-1 ring-zinc-200">
                        x{{ item.quantity }}
                      </div>
                      <p class="mt-2 text-xl font-black text-zinc-900">{{ formatMoney(item.lineAmount, voucher.currencyCode) }}</p>
                      <p class="mt-1 text-sm text-zinc-500">{{ formatMoney(item.unitPrice, voucher.currencyCode) }} each</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="shouldCollapseItems" class="mt-3">
            <button
              data-testid="voucher-items-toggle"
              class="w-full rounded-2xl border border-zinc-200 bg-white px-4 py-3 text-sm font-semibold text-zinc-700 transition hover:border-zinc-300 hover:bg-zinc-50"
              @click="showAllItems = !showAllItems"
            >
              {{ showAllItems ? 'Collapse items' : `Show remaining ${hiddenItemCount} ${hiddenItemCount === 1 ? 'item' : 'items'}` }}
            </button>
          </div>

          <div v-if="voucher.remark" class="mt-5 rounded-3xl border border-zinc-200 bg-zinc-50 p-4">
            <p class="text-xs uppercase tracking-[0.24em] text-zinc-400">Remark</p>
            <p class="mt-2 whitespace-pre-line text-sm text-zinc-700">{{ voucher.remark }}</p>
          </div>
        </section>

        <aside class="space-y-4 md:space-y-6">
          <section class="rounded-[28px] bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="text-xs uppercase tracking-[0.24em] text-zinc-400">Summary</p>
            <div class="mt-4 space-y-3 text-sm text-zinc-600">
              <div class="flex items-center justify-between">
                <span>Subtotal</span>
                <span>{{ formatMoney(voucher.subtotalAmount, voucher.currencyCode) }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span>Shipping</span>
                <span>{{ formatMoney(voucher.shippingFee, voucher.currencyCode) }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span>Discount</span>
                <span>- {{ formatMoney(voucher.discountAmount, voucher.currencyCode) }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span>Paid</span>
                <span>{{ formatMoney(voucher.paidAmount, voucher.currencyCode) }}</span>
              </div>
              <div class="border-t border-zinc-100 pt-3">
                <div class="flex items-center justify-between text-base font-bold text-zinc-900">
                  <span>Total</span>
                  <span>{{ formatMoney(voucher.totalAmount, voucher.currencyCode) }}</span>
                </div>
                <div class="mt-2 flex items-center justify-between text-base font-bold text-amber-700">
                  <span>Balance</span>
                  <span>{{ formatMoney(voucher.balanceAmount, voucher.currencyCode) }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-if="hasShippingAddress" class="rounded-[28px] bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="text-xs uppercase tracking-[0.24em] text-zinc-400">Shipping Address</p>
            <p class="mt-3 whitespace-pre-line text-sm text-zinc-700">{{ voucher.shippingAddressSnapshot }}</p>
          </section>

          <section v-if="canShowPaymentMethods" class="rounded-[28px] bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="text-xs uppercase tracking-[0.24em] text-zinc-400">Payment Methods</p>
            <div class="mt-4 space-y-3">
              <div
                v-for="method in paymentMethods"
                :key="`${method.methodId}-${method.type}`"
                class="rounded-2xl border border-zinc-100 bg-zinc-50 px-3 py-3 md:px-4"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="text-sm font-semibold text-zinc-800">{{ method.name }}</p>
                    <p v-if="method.description" class="mt-1 text-xs text-zinc-500">{{ method.description }}</p>
                    <p v-if="method.type === 'PAYPAL_TRANSFER'" class="mt-2 break-all text-xs text-zinc-700">{{ method.accountValue }}</p>
                  </div>
                  <button
                    v-if="canShowPaymentAction(method)"
                    class="shrink-0 text-xs font-semibold text-emerald-700"
                    @click="handlePaymentAction(method)"
                  >
                    {{ paymentActionText(method) }}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section v-if="canShowContacts" class="rounded-[28px] bg-white p-4 shadow-sm ring-1 ring-zinc-100 md:p-6">
            <p class="text-xs uppercase tracking-[0.24em] text-zinc-400">Contact</p>
            <div class="mt-4 space-y-3">
              <div
                v-for="contact in voucher.contacts"
                :key="contact.id"
                class="flex items-center justify-between gap-3 rounded-2xl border border-zinc-100 bg-zinc-50 px-3 py-3 md:px-4"
              >
                <div class="min-w-0">
                  <p class="text-sm font-semibold text-zinc-800">{{ contact.label }}</p>
                  <p class="truncate text-xs text-zinc-500">{{ contact.value }}</p>
                </div>
                <button
                  v-if="canCopyContact(contact)"
                  class="shrink-0 text-xs font-semibold text-emerald-700"
                  @click="copyText(contact.copyValue)"
                >
                  Copy
                </button>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </div>

  <el-dialog v-model="bankDialogVisible" title="Bank Transfer Details" width="min(720px, 94vw)">
    <div v-if="activeBankMethod" class="space-y-2">
      <p class="text-sm font-semibold text-zinc-800">{{ activeBankMethod.name }}</p>
      <div
        v-for="(field, index) in activeBankMethod.bankFields || []"
        :key="`${field.label}-${index}`"
        class="grid grid-cols-[150px_minmax(0,1fr)_auto] items-center gap-2 rounded-md border border-zinc-200 bg-zinc-50 px-2.5 py-1.5"
      >
        <p class="truncate text-[11px] text-zinc-500">{{ field.label }}</p>
        <p class="break-all text-xs font-semibold text-zinc-800">{{ field.value }}</p>
        <button
          v-if="canCopyBankField(field)"
          class="shrink-0 text-[11px] font-semibold text-emerald-700"
          @click="copyText(field.copyValue)"
        >
          Copy
        </button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.voucher-item-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

@media (min-width: 768px) {
  .voucher-item-title {
    display: block;
    -webkit-line-clamp: unset;
    overflow: visible;
  }
}
</style>
