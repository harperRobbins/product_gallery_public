<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/gallery'
import { saveAdminAuth } from '../utils/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const submit = async () => {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await api.adminLogin({
      username: form.username.trim(),
      password: form.password,
    })
    saveAdminAuth(data.token, data.username || form.username.trim())
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/admin'
    router.replace(String(redirect))
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="grid min-h-screen place-items-center bg-gradient-to-br from-zinc-100 via-white to-emerald-50 px-4">
    <section class="w-full max-w-[420px] rounded-3xl bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.1)] ring-1 ring-zinc-100">
      <h1 class="text-2xl font-black text-zinc-900">后台登录</h1>
      <p class="mt-1 text-sm text-zinc-500">登录后可进行商品与分类管理</p>

      <el-form class="mt-6" label-position="top">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入后台账号" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入后台密码" @keyup.enter="submit" />
        </el-form-item>
      </el-form>

      <el-button type="primary" class="w-full" :loading="loading" @click="submit">登录后台</el-button>
    </section>
  </div>
</template>
