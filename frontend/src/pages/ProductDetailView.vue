<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { api } from '../api/gallery'
import { setCurrentLang } from '../i18n/lang'
import { applyPageMeta } from '../utils/pageMeta'
import {
  COMMON_CURRENCIES,
  formatPriceByCurrency,
  getCurrentCurrency,
  normalizeCurrency,
  setCurrentCurrency,
} from '../utils/currency'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const loading = ref(false)
const product = ref(null)
const profile = ref(null)
const detailCarouselIndex = ref(0)
const imagePreviewVisible = ref(false)
const imagePreviewUrls = ref([])
const imagePreviewIndex = ref(0)
const preloadedImageUrls = new Set()
const activeCurrency = ref(getCurrentCurrency('en-US'))
const currencyRates = ref({})
const currencySource = ref('CNY')
const currencyOptions = COMMON_CURRENCIES

const readRouteCurrency = () => {
  const raw = Array.isArray(route.query.currency) ? route.query.currency[0] : route.query.currency
  if (!raw) return ''
  return String(raw).trim().toUpperCase()
}

const applyCurrencyFromRoute = () => {
  const fromRoute = readRouteCurrency()
  const supported = new Set(currencyOptions.map((item) => item.code))
  const normalized = normalizeCurrency(fromRoute)
  if (supported.has(normalized)) {
    activeCurrency.value = setCurrentCurrency(normalized)
    return
  }
  activeCurrency.value = getCurrentCurrency(locale.value)
}

const showPrice = computed(() => {
  const val = Number(product.value && product.value.price)
  return Number.isFinite(val) && val > 0
})
const displayPrice = computed(() => formatPriceByCurrency(
  product.value && product.value.price,
  activeCurrency.value,
  currencyRates.value,
  currencySource.value,
))

const removeOssProcessParam = (rawUrl) => {
  if (!rawUrl) return rawUrl
  try {
    const parsed = new URL(rawUrl)
    if (!parsed.searchParams.has('x-oss-process')) return rawUrl
    parsed.searchParams.delete('x-oss-process')
    const query = parsed.searchParams.toString()
    return `${parsed.origin}${parsed.pathname}${query ? `?${query}` : ''}${parsed.hash}`
  } catch (error) {
    const hashIndex = rawUrl.indexOf('#')
    const fragment = hashIndex >= 0 ? rawUrl.substring(hashIndex) : ''
    const main = hashIndex >= 0 ? rawUrl.substring(0, hashIndex) : rawUrl
    const queryIndex = main.indexOf('?')
    if (queryIndex < 0) return rawUrl
    const base = main.substring(0, queryIndex)
    const query = main.substring(queryIndex + 1)
    if (!query) return base + fragment
    const parts = query
      .split('&')
      .filter((part) => part)
      .filter((part) => {
        const key = (part.split('=')[0] || '').trim().toLowerCase()
        return key !== 'x-oss-process'
      })
    if (!parts.length) return base + fragment
    return `${base}?${parts.join('&')}${fragment}`
  }
}

const normalizedImageUrls = computed(() => {
  const list = Array.isArray(product.value?.imageUrls) ? product.value.imageUrls.filter(Boolean) : []
  return list.map((url) => removeOssProcessParam(url))
})

const fetchDetail = async () => {
  loading.value = true
  try {
    product.value = await api.publicProductDetail(route.params.id)
  } catch (error) {
    ElMessage.error(error.message || t('product.notFound'))
  } finally {
    loading.value = false
  }
}

const fetchProfile = async () => {
  try {
    profile.value = await api.shopProfile()
  } catch (error) {
    console.warn('店铺配置加载失败', error)
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

const openImagePreview = (index = detailCarouselIndex.value) => {
  const list = normalizedImageUrls.value
  if (!list.length) return
  const targetIndex = Math.max(0, Math.min(Number(index || 0), list.length - 1))
  imagePreviewUrls.value = list
  imagePreviewIndex.value = targetIndex
  imagePreviewVisible.value = true
  preloadImageWindow(list, targetIndex)
}

const onCarouselChange = (index) => {
  detailCarouselIndex.value = Number(index || 0)
  preloadImageWindow(normalizedImageUrls.value, detailCarouselIndex.value)
}

const onImagePreviewSwitch = (index) => {
  imagePreviewIndex.value = Number(index || 0)
  preloadImageWindow(imagePreviewUrls.value, imagePreviewIndex.value)
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/')
}

const chooseCurrency = async (currency) => {
  const next = setCurrentCurrency(normalizeCurrency(currency))
  activeCurrency.value = next
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      currency: next,
    },
  })
}

onMounted(async () => {
  locale.value = setCurrentLang('en-US')
  applyCurrencyFromRoute()
  await Promise.all([fetchDetail(), fetchCurrencyRates(), fetchProfile()])
  applyPageMeta({
    route,
    profile: profile.value,
    lang: locale.value,
    data: { productTitle: product.value && product.value.title ? product.value.title : '' },
  })
})
watch(
  () => route.query.currency,
  () => {
    applyCurrencyFromRoute()
  },
)
watch(
  normalizedImageUrls,
  (list) => {
    if (!list.length) return
    detailCarouselIndex.value = 0
    preloadImageWindow(list, 0)
  },
  { immediate: true },
)
</script>

<template>
  <div class="min-h-screen bg-zinc-50">
    <div class="mx-auto max-w-5xl px-3 py-4 md:px-6 md:py-8">
      <el-button text @click="goBack">
        <el-icon class="mr-1"><ArrowLeft /></el-icon>
        {{ t('product.back') }}
      </el-button>
      <div v-if="loading" class="py-20 text-center text-zinc-400">{{ t('common.loading') }}</div>
      <div v-else-if="!product" class="py-20 text-center text-zinc-400">{{ t('product.notFound') }}</div>

      <div v-else class="mt-3 overflow-hidden rounded-3xl bg-white shadow-xl ring-1 ring-zinc-100">
        <div class="grid grid-cols-1 md:grid-cols-2">
          <div class="bg-zinc-100 p-3 md:p-6">
            <div v-if="product.videoUrl" class="mb-4 overflow-hidden rounded-2xl bg-black">
              <video
                :src="product.videoUrl"
                controls
                playsinline
                class="max-h-[420px] w-full object-contain"
              ></video>
            </div>
            <el-carousel
              v-if="normalizedImageUrls.length"
              height="420px"
              indicator-position="outside"
              :autoplay="false"
              @change="onCarouselChange"
            >
              <el-carousel-item v-for="(url, index) in normalizedImageUrls" :key="url + String(index)">
                <div class="flex h-full items-center justify-center rounded-2xl bg-white">
                  <button
                    type="button"
                    class="h-full w-full cursor-zoom-in"
                    @click="openImagePreview(index)"
                  >
                    <img :src="url" class="h-full w-full rounded-2xl object-contain" :alt="product.title" />
                  </button>
                </div>
              </el-carousel-item>
            </el-carousel>
          </div>
          <div class="space-y-5 p-5 md:p-8">
            <p class="text-xs uppercase tracking-[0.25em] text-amber-600">{{ product.categoryName }}</p>
            <h1 class="text-2xl font-black leading-tight text-zinc-900 md:text-3xl">{{ product.title }}</h1>
            <div class="flex flex-wrap items-center gap-3">
              <p v-if="showPrice" class="text-3xl font-black text-orange-600">{{ displayPrice.symbol }} {{ displayPrice.text }}</p>
              <span class="rounded-full bg-zinc-100 px-3 py-1 text-xs text-zinc-500">{{ t('product.sku') }}: {{ product.sku }}</span>
              <select
                :value="activeCurrency"
                class="h-8 min-w-[96px] rounded-full border border-zinc-200 bg-zinc-50 px-3 text-xs font-bold tracking-[0.08em] text-zinc-700 outline-none"
                @change="chooseCurrency($event.target.value)"
              >
                <option
                  v-for="item in currencyOptions"
                  :key="item.code"
                  :value="item.code"
                >
                  {{ item.label }}
                </option>
              </select>
            </div>
            <div class="flex flex-wrap gap-2" v-if="product.tags?.length">
              <span v-for="tag in product.tags" :key="tag" class="rounded-full bg-amber-50 px-3 py-1 text-xs text-amber-700">
                {{ tag }}
              </span>
            </div>
            <div class="rounded-2xl bg-zinc-50 p-4 text-sm leading-7 text-zinc-600">
              <p class="whitespace-pre-line">{{ product.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <teleport to="body">
      <el-image-viewer
        v-if="imagePreviewVisible"
        :url-list="imagePreviewUrls"
        :initial-index="imagePreviewIndex"
        @switch="onImagePreviewSwitch"
        @close="imagePreviewVisible = false"
      />
    </teleport>
  </div>
</template>
