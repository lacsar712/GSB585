<template>
  <div class="relative grid min-h-screen grid-cols-1 overflow-hidden lg:grid-cols-2">
    <!-- Left Side: Visual -->
    <div class="relative hidden flex-col justify-between bg-[#0f172a] p-12 text-white lg:flex">
      <div class="z-10 flex items-center gap-3">
        <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6">
            <path d="M11.644 1.59a.75.75 0 01.712 0l9.75 5.25a.75.75 0 010 1.32l-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25a.75.75 0 010-1.32l9.75-5.25z" />
            <path fill-rule="evenodd" d="M3.25 9.55l9.06 4.88 9.06-4.88 1.41.76-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25 1.41-.76z" clip-rule="evenodd" />
            <path fill-rule="evenodd" d="M3.25 14.28l9.06 4.88 9.06-4.88 1.41.76-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25 1.41-.76z" clip-rule="evenodd" />
          </svg>
        </div>
        <span class="text-xl font-bold tracking-tight">同城快送</span>
      </div>

      <div class="z-10 space-y-6">
        <h1 class="text-5xl font-bold leading-tight tracking-tight">
          重新定义 <br />
          <span class="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-cyan-400">同城极速配送</span>
        </h1>
        <p class="max-w-md text-lg text-slate-400">
          高效、智能、安全的同城物流解决方案。
          实时追踪，极速送达，为您连接城市的每一个角落。
        </p>
      </div>

      <div class="z-10 flex items-center gap-4 text-sm text-slate-500">
        <span>© 2024 同城快送有限公司</span>
      </div>

      <!-- Background Patterns -->
      <div class="absolute inset-0 z-0 bg-[url('https://images.unsplash.com/photo-1616401784845-18088629ca8e?q=80&w=2560&auto=format&fit=crop')] bg-cover bg-center opacity-20 mix-blend-overlay"></div>
      <div class="absolute inset-0 bg-gradient-to-t from-[#0f172a] via-[#0f172a]/80 to-transparent"></div>
    </div>

    <!-- Right Side: Login Form -->
    <div class="flex items-center justify-center bg-white p-8 lg:p-12">
      <div class="w-full max-w-[420px] space-y-8">
        <div class="lg:hidden flex items-center gap-3 mb-8">
          <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600">
             <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6 text-white">
                <path d="M11.644 1.59a.75.75 0 01.712 0l9.75 5.25a.75.75 0 010 1.32l-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25a.75.75 0 010-1.32l9.75-5.25z" />
                <path fill-rule="evenodd" d="M3.25 9.55l9.06 4.88 9.06-4.88 1.41.76-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25 1.41-.76z" clip-rule="evenodd" />
             </svg>
          </div>
          <span class="text-xl font-bold text-slate-900">同城快送</span>
        </div>

        <div>
          <h2 class="text-3xl font-bold tracking-tight text-slate-900">欢迎回来</h2>
          <p class="mt-2 text-sm text-slate-500">请输入您的账号信息以登录系统</p>
        </div>

        <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent>
          <ElFormItem label="用户名" prop="username">
            <ElInput v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
          </ElFormItem>
          <ElFormItem label="密码" prop="password">
            <ElInput v-model="form.password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
          </ElFormItem>
          
          <!-- Remember Me and Forgot Password removed -->

          <ElButton type="primary" size="large" class="w-full" :loading="loading" @click="onSubmit">
            立即登录
          </ElButton>
        </ElForm>

        <!--
        <div class="relative">
          <div class="absolute inset-0 flex items-center">
            <div class="w-full border-t border-slate-200"></div>
          </div>
          <div class="relative flex justify-center text-sm">
            <span class="bg-white px-2 text-slate-500">测试账号</span>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3 text-xs text-slate-500 text-center">
          <div class="bg-slate-50 p-2 rounded-lg border border-slate-100">
             <span class="font-bold text-slate-700">管理员</span><br>admin / password123
          </div>
          <div class="bg-slate-50 p-2 rounded-lg border border-slate-100">
             <span class="font-bold text-slate-700">调度员</span><br>dispatch / password123
          </div>
          <div class="bg-slate-50 p-2 rounded-lg border border-slate-100">
             <span class="font-bold text-slate-700">骑手</span><br>rider1 / password123
          </div>
          <div class="bg-slate-50 p-2 rounded-lg border border-slate-100">
             <span class="font-bold text-slate-700">客户</span><br>customer1 / password123
          </div>
        </div>
        -->
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { showError } from '@/utils/message'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const auth = useAuthStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit(): Promise<void> {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) {
    showError('请先修正表单错误')
    return
  }

  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push('/track')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* Additional custom styles if needed */
</style>
