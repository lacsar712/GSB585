import { defineStore } from 'pinia'
import type { RoleType, UserInfo } from '@/types'
import { fetchMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import { showSuccess } from '@/utils/message'

interface AuthState {
  token: string
  user: UserInfo | null
}

const TOKEN_KEY = 'city-courier-token'

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: null
  }),
  getters: {
    isLogin: (state) => Boolean(state.token),
    role: (state): RoleType | null => state.user?.role ?? null
  },
  actions: {
    async login(username: string, password: string): Promise<void> {
      const data = await loginApi({ username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      showSuccess('登录成功')
    },
    async initUser(): Promise<void> {
      if (!this.token) return
      try {
        this.user = await fetchMe()
      } catch {
        this.logout(false)
      }
    },
    async logout(withNotify = true): Promise<void> {
      if (this.token) {
        try {
          await logoutApi()
        } catch {
          // 忽略退出失败
        }
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      if (withNotify) {
        showSuccess('已退出登录')
      }
    }
  }
})
