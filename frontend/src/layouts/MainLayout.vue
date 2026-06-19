<template>
  <div class="flex min-h-screen bg-slate-50 md:grid md:grid-cols-[280px_1fr]">
    <!-- Mobile Sidebar Overlay -->
    <div 
      v-if="isSidebarOpen" 
      class="fixed inset-0 z-20 bg-black/50 backdrop-blur-sm transition-opacity md:hidden"
      @click="isSidebarOpen = false"
    ></div>

    <!-- Premium Sidebar -->
    <aside 
      class="sidebar-panel fixed inset-y-0 left-0 z-30 flex w-[280px] flex-col text-white shadow-2xl transition-transform duration-300 md:static md:translate-x-0"
      :class="isSidebarOpen ? 'translate-x-0' : '-translate-x-full'"
    >
      <div class="brand-zone flex items-center gap-4 px-6 py-8">
        <div class="brand-badge flex h-12 w-12 items-center justify-center rounded-xl">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-7 h-7 text-white">
            <path d="M11.644 1.59a.75.75 0 01.712 0l9.75 5.25a.75.75 0 010 1.32l-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25a.75.75 0 010-1.32l9.75-5.25z" />
            <path fill-rule="evenodd" d="M3.25 9.55l9.06 4.88 9.06-4.88 1.41.76-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25 1.41-.76z" clip-rule="evenodd" />
            <path fill-rule="evenodd" d="M3.25 14.28l9.06 4.88 9.06-4.88 1.41.76-9.75 5.25a.75.75 0 01-.712 0l-9.75-5.25 1.41-.76z" clip-rule="evenodd" />
          </svg>
        </div>
        <div>
          <div class="text-xl font-bold tracking-tight text-white">同城快送</div>
          <div class="text-xs font-medium text-indigo-400">高端物流</div>
        </div>
      </div>

      <div class="nav-zone px-6 py-5">
        <nav class="flex flex-col gap-2">
          <RouterLink
            v-for="item in menus"
            :key="item.path"
            :to="item.path"
            class="nav-link group flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium transition-all duration-200"
            active-class="nav-link-active text-white"
            @click="isSidebarOpen = false"
          >
            <el-icon :size="20" class="nav-icon text-slate-400 transition-colors group-hover:text-white">
              <component :is="item.icon" />
            </el-icon>
            <span class="nav-text text-slate-300 transition-colors group-hover:text-white">{{ item.label }}</span>
          </RouterLink>
        </nav>
      </div>

      <div class="mt-auto p-6 pt-5 account-zone">
        <div class="rounded-2xl bg-slate-800/55 p-4 border border-slate-700/60">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-slate-700 text-sm font-bold text-white">
              {{ avatarText }}
            </div>
            <div class="overflow-hidden">
              <div class="truncate text-sm font-medium text-white">{{ auth.user?.username || '用户' }}</div>
              <div class="truncate text-xs text-slate-400">{{ roleText }}</div>
            </div>
          </div>
          <button 
            @click="doLogout"
            class="mt-3 flex w-full items-center justify-center gap-2 rounded-lg bg-slate-700/50 py-2 text-xs font-semibold text-slate-300 transition hover:bg-slate-700 hover:text-white"
          >
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </button>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="relative flex h-screen flex-col overflow-hidden bg-[#f8fafc]">
      <button
        class="fixed left-4 top-4 z-20 flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-500 shadow-sm transition-all hover:border-indigo-200 hover:text-indigo-600 md:hidden"
        @click="isSidebarOpen = !isSidebarOpen"
      >
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
        </svg>
      </button>

      <!-- Page Content -->
      <div class="flex-1 overflow-auto p-4 pt-16 md:p-8 md:pt-8">
        <div class="mx-auto max-w-7xl animate-fade-in-up">
          <RouterView />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Document, Coin, CollectionTag, Grid, Search, SwitchButton } from '@element-plus/icons-vue'

const auth = useAuthStore()
const router = useRouter()

const isSidebarOpen = ref(false)

const roleText = computed(() => {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    DISPATCHER: '调度员',
    RIDER: '骑手',
    CUSTOMER: '客户'
  }
  return auth.role ? map[auth.role] : '-'
})

const avatarText = computed(() => {
  const username = auth.user?.username ?? ''
  return username ? username.slice(0, 1).toUpperCase() : '用'
})

const menus = computed(() => {
  const all = [
    { path: '/dashboard', label: '运营看板', icon: Grid, roles: ['ADMIN', 'DISPATCHER'] },
    { path: '/create', label: '创建订单', icon: Document, roles: ['ADMIN', 'CUSTOMER'] },
    { path: '/dispatch', label: '调度中心', icon: CollectionTag, roles: ['ADMIN', 'DISPATCHER'] },
    { path: '/rider', label: '骑手工作台', icon: Coin, roles: ['RIDER'] },
    { path: '/track', label: '运单查询', icon: Search, roles: ['ADMIN', 'DISPATCHER', 'RIDER', 'CUSTOMER'] },
  ]
  return all.filter((m) => auth.role && m.roles.includes(auth.role))
})

async function doLogout(): Promise<void> {
  await auth.logout(true)
  await router.push('/login')
}
</script>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in-up {
  animation: fadeInUp 0.5s ease-out forwards;
}

/* Custom scrollbar for sidebar nav if needed */
aside nav {
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.sidebar-panel {
  background:
    radial-gradient(circle at 12% 12%, rgba(14, 165, 233, 0.16) 0, transparent 45%),
    radial-gradient(circle at 100% 88%, rgba(56, 189, 248, 0.14) 0, transparent 42%),
    linear-gradient(180deg, #0b1730 0%, #0c1b39 54%, #0b1730 100%);
}

.brand-badge {
  background: linear-gradient(145deg, #2563eb 0%, #0ea5e9 100%);
  box-shadow: 0 14px 24px -14px rgba(14, 116, 255, 0.85);
}

.brand-zone {
  position: relative;
}

.brand-zone::after {
  content: '';
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(71, 85, 105, 0) 0%, rgba(14, 165, 233, 0.45) 50%, rgba(71, 85, 105, 0) 100%);
}

.nav-zone {
  position: relative;
  background: linear-gradient(180deg, rgba(12, 27, 57, 0) 0%, rgba(8, 45, 78, 0.2) 100%);
}

.account-zone {
  position: relative;
}

.account-zone::before {
  content: '';
  position: absolute;
  left: 24px;
  right: 24px;
  top: 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(71, 85, 105, 0) 0%, rgba(56, 189, 248, 0.35) 50%, rgba(71, 85, 105, 0) 100%);
}

.nav-link {
  color: #c7d2e5;
}

.nav-link:hover {
  background: rgba(51, 65, 85, 0.38);
}

.nav-link-active {
  background: linear-gradient(120deg, #2563eb 0%, #0284c7 100%);
  box-shadow: 0 14px 24px -18px rgba(14, 116, 255, 0.95);
}

.nav-link-active .nav-icon,
.nav-link-active .nav-text {
  color: #ffffff !important;
}
</style>
