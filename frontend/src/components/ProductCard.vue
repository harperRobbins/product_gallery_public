<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CURRENCY_CNY, formatPriceByCurrency } from '../utils/currency'

const props = defineProps({
  product: {
    type: Object,
    required: true,
  },
  mode: {
    type: String,
    default: 'small',
  },
  currency: {
    type: String,
    default: CURRENCY_CNY,
  },
  rates: {
    type: Object,
    default: () => ({}),
  },
  sourceCurrency: {
    type: String,
    default: CURRENCY_CNY,
  },
})

const emit = defineEmits(['open-detail', 'preview-images', 'preview-video'])
const { t } = useI18n()

const priceDisplay = computed(() => formatPriceByCurrency(props.product.price, props.currency, props.rates, props.sourceCurrency))

const hasPrice = computed(() => {
  const val = Number(props.product.price)
  return Number.isFinite(val) && val > 0
})

const visibleTags = computed(() => {
  const source = props.product.tags || []
  if (!source.length) return []
  if (props.mode === 'small') return source.slice(0, 1)
  return source.slice(0, 3)
})

const removeOssProcessParam = (rawUrl) => {
  if (!rawUrl) return rawUrl
  try {
    const parsed = new URL(rawUrl)
    if (!parsed.searchParams.has('x-oss-process')) return rawUrl
    parsed.searchParams.delete('x-oss-process')
    const query = parsed.searchParams.toString()
    return `${parsed.origin}${parsed.pathname}${query ? `?${query}` : ''}${parsed.hash}`
  } catch (e) {
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

const allImageUrls = computed(() => {
  const list = Array.isArray(props.product.imageUrls) ? props.product.imageUrls.filter(Boolean) : []
  if (list.length) return list
  if (props.product.coverImage) return [props.product.coverImage]
  return []
})

const previewImageUrls = computed(() => allImageUrls.value.map((url) => removeOssProcessParam(url)))

const thumbnailImages = computed(() => {
  const list = allImageUrls.value
  if (list.length > 4) return list.slice(0, 4)
  return list
})

const isOverFourImages = computed(() => allImageUrls.value.length > 4)

const openDetail = () => {
  emit('open-detail', props.product)
}

const previewImage = (index) => {
  emit('preview-images', {
    product: props.product,
    urls: previewImageUrls.value,
    index,
  })
}

const previewVideo = () => {
  if (!props.product.videoUrl) return
  emit('preview-video', {
    product: props.product,
    url: props.product.videoUrl,
  })
}

const onThumbClick = (index) => {
  if (props.product.videoUrl) {
    previewVideo()
    return
  }
  previewImage(index)
}
</script>

<template>
  <article
    v-if="mode === 'multi'"
    class="flex items-start gap-3 rounded-2xl bg-white p-3 shadow-[0_8px_20px_rgba(0,0,0,0.08)] ring-1 ring-zinc-100"
  >
    <div class="relative w-[126px] shrink-0">
      <button
        v-if="product.videoUrl"
        class="absolute left-2 top-2 z-10 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-semibold text-white"
        @click.stop="previewVideo"
      >
        {{ t('gallery.tabs.video') }}
      </button>

      <div
        v-if="isOverFourImages"
        class="grid w-[126px] grid-cols-2 gap-1"
      >
        <button
          v-for="(url, index) in thumbnailImages"
          :key="url + String(index)"
          class="relative h-[61px] w-[61px] overflow-hidden rounded-lg bg-zinc-100"
          @click.stop="onThumbClick(index)"
        >
          <img :src="url" :alt="product.title" loading="lazy" decoding="async" class="h-full w-full object-cover" />
          <span
            v-if="index === 3"
            class="absolute inset-0 grid place-items-center bg-black/45 text-sm font-bold text-white"
          >
            +{{ allImageUrls.length - 4 }}
          </span>
        </button>
      </div>

      <button
        v-else-if="thumbnailImages.length <= 1"
        class="block h-[126px] w-[126px] overflow-hidden rounded-xl bg-zinc-100"
        @click.stop="onThumbClick(0)"
      >
        <img v-if="allImageUrls[0]" :src="allImageUrls[0]" :alt="product.title" loading="lazy" decoding="async" class="h-full w-full object-cover" />
      </button>

      <div
        v-else
        class="flex w-[126px] gap-1"
      >
        <button
          v-for="(url, index) in thumbnailImages"
          :key="url + String(index)"
          class="h-[126px] min-w-0 flex-1 overflow-hidden rounded-lg bg-zinc-100"
          @click.stop="onThumbClick(index)"
        >
          <img :src="url" :alt="product.title" loading="lazy" decoding="async" class="h-full w-full object-cover" />
        </button>
      </div>
    </div>

    <div class="min-w-0 flex-1 cursor-pointer" @click="openDetail">
      <h3 class="line-clamp-3 text-[17px] font-semibold leading-6 text-zinc-900">{{ product.title }}</h3>
      <div class="mt-2 flex items-center gap-2">
        <p v-if="hasPrice" class="text-xl font-black text-rose-600">{{ priceDisplay.symbol }} {{ priceDisplay.text }}</p>
        <span v-if="product.sku" class="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] text-zinc-500">
          {{ product.sku }}
        </span>
      </div>
      <div class="mt-2 flex flex-wrap items-center gap-1" v-if="visibleTags.length || product.videoUrl">
        <span
          v-for="tag in visibleTags"
          :key="tag"
          class="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] text-zinc-600"
        >
          {{ tag }}
        </span>
        <span v-if="product.videoUrl" class="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] text-emerald-700">
          {{ t('gallery.tabs.video') }}
        </span>
      </div>
    </div>
  </article>

  <article
    v-else
    class="group cursor-pointer overflow-hidden rounded-2xl bg-white shadow-[0_8px_20px_rgba(0,0,0,0.08)] transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_12px_24px_rgba(0,0,0,0.14)]"
    @click="openDetail"
  >
    <div class="relative overflow-hidden">
        <img
          :src="product.coverImage"
          :alt="product.title"
          loading="lazy"
          decoding="async"
          class="h-40 w-full object-cover transition-transform duration-500 group-hover:scale-105 md:h-48"
        />
      <div class="absolute left-2 top-2 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-semibold text-white">
        {{ t('gallery.tabs.photos') }} {{ product.imageCount || 0 }}
      </div>
      <div
        v-if="product.videoUrl"
        class="absolute right-2 top-2 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-semibold text-white"
      >
        {{ t('gallery.tabs.video') }}
      </div>
    </div>
    <div class="space-y-2 p-3">
      <h3 class="line-clamp-2 text-sm font-semibold text-zinc-900">{{ product.title }}</h3>
      <div class="flex items-center justify-between">
        <p v-if="hasPrice" class="text-base font-black text-emerald-600">{{ priceDisplay.symbol }} {{ priceDisplay.text }}</p>
        <p class="text-[11px] text-zinc-500">{{ product.sku }}</p>
      </div>
      <div class="flex flex-wrap gap-1" v-if="visibleTags.length">
        <span
          v-for="tag in visibleTags"
          :key="tag"
          class="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] text-zinc-600"
        >
          {{ tag }}
        </span>
      </div>
    </div>
  </article>
</template>
