<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api/gallery'

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true,
  },
  editingProduct: {
    type: Object,
    default: null,
  },
  categories: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref()
const submitLoading = ref(false)
const uploadLoading = ref(false)
const videoUploadLoading = ref(false)
const uploadingCount = ref(0)
const uploadingVideoCount = ref(0)
const uploadFileList = ref([])
const videoFileList = ref([])

const form = reactive({
  id: null,
  title: '',
  enTitle: '',
  description: '',
  enDescription: '',
  categoryId: null,
  price: null,
  sku: '',
  tagsText: '',
  imageUrls: [],
  videoUrl: '',
  imageSizesKb: [],
  status: 1,
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  sku: [{ required: true, message: '请输入货号', trigger: 'blur' }],
}

const categoryOptions = computed(() => {
  const list = []
  const walk = (items, prefix) => {
    items.forEach((item) => {
      const label = prefix ? prefix + ' / ' + item.name : item.name
      list.push({ value: item.id, label })
      if (item.children && item.children.length) {
        walk(item.children, label)
      }
    })
  }
  walk(props.categories || [], '')
  return list
})

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const hasUploading = computed(() => uploadingCount.value > 0 || uploadingVideoCount.value > 0)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    if (props.editingProduct) {
      fillByProduct(props.editingProduct)
      return
    }
    resetForm()
  },
)

const isPersistableUrl = (url) => {
  if (!url || typeof url !== 'string') return false
  const value = url.trim()
  if (!value) return false
  if (value.startsWith('blob:')) return false
  if (value.includes('localhost:5173') || value.includes('localhost:8080')) return false
  return true
}

const extractFinalUrl = (file) => {
  const responseUrl = file && file.response ? file.response.url : ''
  if (isPersistableUrl(responseUrl)) return responseUrl
  const fileUrl = file ? file.url : ''
  if (isPersistableUrl(fileUrl)) return fileUrl
  return ''
}

const fillByProduct = (product) => {
  const sourceUrls = product.imageUrls || []
  const safeUrls = sourceUrls.filter(isPersistableUrl)
  if (safeUrls.length !== sourceUrls.length) {
    ElMessage.warning('检测到历史本地预览地址，请重新上传商品图后保存')
  }

  form.id = product.id
  form.title = product.title || ''
  form.enTitle = product.enTitle || ''
  form.description = product.description || ''
  form.enDescription = product.enDescription || ''
  form.categoryId = product.categoryId || null
  form.price = product.price
  form.sku = product.sku || ''
  form.tagsText = (product.tags || []).join(',')
  form.imageUrls = [...safeUrls]
  form.videoUrl = isPersistableUrl(product.videoUrl || '') ? product.videoUrl : ''
  form.imageSizesKb = safeUrls.map(() => 0)
  form.status = product.status == null ? 1 : product.status
  uploadFileList.value = form.imageUrls.map((url, index) => ({
    name: 'image-' + (index + 1),
    url,
    uid: String(index + 1),
  }))
  videoFileList.value = form.videoUrl
    ? [
        {
          name: 'video-1',
          url: form.videoUrl,
          uid: 'video-1',
        },
      ]
    : []
}

const resetForm = () => {
  form.id = null
  form.title = ''
  form.enTitle = ''
  form.description = ''
  form.enDescription = ''
  form.categoryId = null
  form.price = null
  form.sku = ''
  form.tagsText = ''
  form.imageUrls = []
  form.videoUrl = ''
  form.imageSizesKb = []
  form.status = 1
  uploadingCount.value = 0
  uploadingVideoCount.value = 0
  uploadFileList.value = []
  videoFileList.value = []
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

const syncImageFields = (fileList) => {
  const urls = []
  const sizes = []
  fileList.forEach((file) => {
    const url = extractFinalUrl(file)
    if (url) {
      urls.push(url)
      const size = file.size ? Math.ceil(file.size / 1024) : 0
      sizes.push(size)
    }
  })
  form.imageUrls = urls
  form.imageSizesKb = sizes
}

const syncVideoField = (fileList) => {
  const source = fileList || []
  for (const file of source) {
    const url = extractFinalUrl(file)
    if (url) {
      form.videoUrl = url
      return
    }
  }
  form.videoUrl = ''
}

const uploadSingle = async (option) => {
  uploadingCount.value += 1
  uploadLoading.value = true
  try {
    const data = await api.uploadImages([option.file])
    const url = data.urls && data.urls.length ? data.urls[0] : ''
    if (!url) {
      throw new Error('OSS返回地址为空')
    }
    option.file.url = url
    option.onSuccess({ url }, option.file)
  } catch (error) {
    option.onError(error)
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploadingCount.value = Math.max(0, uploadingCount.value - 1)
    uploadLoading.value = uploadingCount.value > 0
  }
}

const uploadVideoSingle = async (option) => {
  uploadingVideoCount.value += 1
  videoUploadLoading.value = true
  try {
    const data = await api.uploadVideos([option.file])
    const url = data.urls && data.urls.length ? data.urls[0] : ''
    if (!url) {
      throw new Error('OSS返回地址为空')
    }
    option.file.url = url
    option.onSuccess({ url }, option.file)
  } catch (error) {
    option.onError(error)
    ElMessage.error(error.message || '视频上传失败')
  } finally {
    uploadingVideoCount.value = Math.max(0, uploadingVideoCount.value - 1)
    videoUploadLoading.value = uploadingVideoCount.value > 0
  }
}

const handleUploadSuccess = (_response, _file, fileList) => {
  syncImageFields(fileList)
}

const handleUploadRemove = (_file, fileList) => {
  syncImageFields(fileList)
}

const handleVideoUploadSuccess = (_response, _file, fileList) => {
  syncVideoField(fileList)
}

const handleVideoUploadRemove = (_file, fileList) => {
  syncVideoField(fileList)
}

const handleVideoExceed = () => {
  ElMessage.warning('最多上传1个视频')
}

const handleSubmit = async () => {
  if (hasUploading.value || uploadingVideoCount.value > 0) {
    ElMessage.warning('文件上传中，请稍候再保存')
    return
  }

  syncImageFields(uploadFileList.value || [])
  syncVideoField(videoFileList.value || [])

  await formRef.value.validate()
  if (!form.imageUrls.length) {
    ElMessage.warning('请至少上传一张图片（需上传成功）')
    return
  }
  const invalidLocal = form.imageUrls.some((url) => !isPersistableUrl(url))
  if (invalidLocal) {
    ElMessage.warning('图片还未完成上传，请删除本地预览图并重新上传')
    return
  }
  if (form.videoUrl && !isPersistableUrl(form.videoUrl)) {
    ElMessage.warning('视频还未完成上传，请重新上传后保存')
    return
  }
  submitLoading.value = true
  try {
    const payload = {
      id: form.id || undefined,
      title: form.title,
      enTitle: form.enTitle || undefined,
      description: form.description,
      enDescription: form.enDescription || undefined,
      categoryId: form.categoryId,
      price: Number(form.price),
      sku: form.sku,
      tags: form.tagsText
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
      imageUrls: form.imageUrls,
      videoUrl: form.videoUrl || undefined,
      imageSizesKb: form.imageSizesKb,
      status: form.status,
    }
    if (form.id) {
      await api.updateProduct(payload)
      ElMessage.success('商品更新成功')
    } else {
      await api.publishProduct(payload)
      ElMessage.success('商品发布成功')
    }
    emit('saved')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="form.id ? '编辑商品' : '一键发布商品'"
    width="min(820px, 96vw)"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-row :gutter="14">
        <el-col :xs="24" :md="16">
          <el-form-item label="商品标题" prop="title">
            <el-input v-model="form.title" placeholder="例如：春夏新款高级感连衣裙" />
          </el-form-item>
        </el-col>
        <el-col :xs="12" :md="8">
          <el-form-item label="货号" prop="sku">
            <el-input v-model="form.sku" placeholder="SKU-001" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="14">
        <el-col :xs="24" :md="16">
          <el-form-item label="英文标题（可选）">
            <el-input v-model="form.enTitle" placeholder="English title for global audience" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="14">
        <el-col :xs="12" :md="8">
          <el-form-item label="价格" prop="price">
            <el-input-number v-model="form.price" :min="0" :precision="2" :step="10" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :xs="12" :md="8">
          <el-form-item label="状态">
            <el-select v-model="form.status" class="w-full">
              <el-option :value="1" label="上架" />
              <el-option :value="0" label="下架" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-form-item label="分类" prop="categoryId">
            <el-select v-model="form.categoryId" filterable class="w-full" placeholder="选择分类">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="标签">
        <el-input v-model="form.tagsText" placeholder="多个标签请用逗号分隔，例如：爆款,显瘦,高品质" />
      </el-form-item>

      <el-form-item label="详细描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="5" placeholder="支持长文本，建议写卖点、材质、尺码建议等信息" />
      </el-form-item>

      <el-form-item label="英文描述（可选）">
        <el-input v-model="form.enDescription" type="textarea" :rows="4" placeholder="English description (can be AI summarized + translated later)" />
      </el-form-item>

      <el-form-item label="商品高清图（多图）">
        <el-upload
          v-model:file-list="uploadFileList"
          list-type="picture-card"
          :http-request="uploadSingle"
          :on-success="handleUploadSuccess"
          :on-remove="handleUploadRemove"
          accept="image/*"
          multiple
        >
          <el-icon v-if="!uploadLoading"><Plus /></el-icon>
          <span v-else class="text-xs text-zinc-500">上传中...</span>
        </el-upload>
      </el-form-item>

      <el-form-item label="商品视频（可选）">
        <el-upload
          v-model:file-list="videoFileList"
          :http-request="uploadVideoSingle"
          :on-success="handleVideoUploadSuccess"
          :on-remove="handleVideoUploadRemove"
          :on-exceed="handleVideoExceed"
          accept="video/mp4,video/quicktime,video/webm,video/x-m4v"
          :limit="1"
        >
          <el-button :loading="videoUploadLoading">
            {{ videoUploadLoading ? '视频上传中...' : '上传视频' }}
          </el-button>
          <template #tip>
            <div class="mt-1 text-xs text-zinc-500">支持 MP4 / MOV / WEBM，最多 1 个视频</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
        {{ form.id ? '保存修改' : '立即发布' }}
      </el-button>
    </template>
  </el-dialog>
</template>
