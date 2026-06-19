import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { showError } from '@/utils/message'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => resp,
  (error) => {
    const message = error.response?.data?.message ?? '请求失败，请稍后重试'
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout(false)
      router.push('/login')
    }
    showError(message)
    return Promise.reject(error)
  }
)

export default http
