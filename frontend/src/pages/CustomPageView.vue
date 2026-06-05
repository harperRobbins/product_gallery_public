<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { api } from '../api/gallery'
import { applyPageMeta } from '../utils/pageMeta'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const loading = ref(false)
const profile = ref(null)

const pageData = computed(() => {
  const key = String(route.params.key || '')
  const pages = Array.isArray(profile.value && profile.value.customPages) ? profile.value.customPages : []
  return pages.find((item) => String(item.key || '') === key) || null
})

const fetchProfile = async () => {
  loading.value = true
  try {
    profile.value = await api.shopProfile()
    const page = pageData.value
    applyPageMeta({
      route,
      profile: profile.value,
      lang: locale.value,
      data: { pageTitle: page && page.title ? page.title : '' },
    })
  } catch (error) {
    ElMessage.error(error.message || t('common.loading'))
  } finally {
    loading.value = false
  }
}

onMounted(fetchProfile)
</script>

<template>
  <div class="min-h-screen bg-zinc-50">
    <div class="mx-auto max-w-4xl px-4 py-6 md:px-6 md:py-10">
      <el-button text @click="router.push('/')">{{ t('product.back') }}</el-button>
      <div v-if="loading" class="py-20 text-center text-zinc-400">{{ t('common.loading') }}</div>
      <div v-else-if="!pageData" class="py-20 text-center text-zinc-400">{{ t('common.noData') }}</div>
      <div v-else class="mt-4 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-zinc-100 md:p-8">
        <h1 class="text-2xl font-black text-zinc-900 md:text-3xl">{{ pageData.title || pageData.key }}</h1>
        <div class="mt-4 whitespace-pre-line text-[15px] leading-8 text-zinc-700">
          {{ pageData.content }}
        </div>
      </div>
    </div>
  </div>
</template>
