import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import MainLayout from '@/layouts/MainLayout.vue'
import DashboardView from '@/views/DashboardView.vue'
import CreateOrderView from '@/views/CreateOrderView.vue'
import DispatchView from '@/views/DispatchView.vue'
import RiderView from '@/views/RiderView.vue'
import TrackView from '@/views/TrackView.vue'
import { useAuthStore } from '@/stores/auth'
import type { RoleType } from '@/types'

const routes = [
  { path: '/login', component: LoginView, meta: { title: '登录' } },
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', redirect: '/dashboard' },
      { path: '/dashboard', component: DashboardView, meta: { title: '工作台', roles: ['ADMIN', 'DISPATCHER'] } },
      { path: '/create', component: CreateOrderView, meta: { title: '创建订单', roles: ['ADMIN', 'CUSTOMER'] } },
      { path: '/dispatch', component: DispatchView, meta: { title: '调度中心', roles: ['ADMIN', 'DISPATCHER'] } },
      { path: '/rider', component: RiderView, meta: { title: '骑手工作台', roles: ['RIDER'] } },
      { path: '/track', component: TrackView, meta: { title: '运单查询', roles: ['ADMIN', 'DISPATCHER', 'RIDER', 'CUSTOMER'] } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function getDefaultRouteByRole(role?: RoleType | null): string {
  if (role === 'ADMIN' || role === 'DISPATCHER') return '/dashboard'
  if (role === 'RIDER') return '/rider'
  return '/track'
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (auth.token && !auth.user) {
    await auth.initUser()
  }

  if (to.path === '/login' && auth.isLogin) {
    return getDefaultRouteByRole(auth.role)
  }

  if (!auth.isLogin && to.path !== '/login') {
    return '/login'
  }

  if (to.path === '/' && auth.isLogin) {
    return getDefaultRouteByRole(auth.role)
  }

  const roles = to.meta.roles as RoleType[] | undefined
  if (roles && auth.role && !roles.includes(auth.role)) {
    return getDefaultRouteByRole(auth.role)
  }

  return true
})

export default router
