import type { ApiResponse, LoginResponse, UserInfo } from '@/types'
import http from './http'

export interface LoginForm {
  username: string
  password: string
}

export async function login(payload: LoginForm): Promise<LoginResponse> {
  const { data } = await http.post<ApiResponse<LoginResponse>>('/auth/login', payload)
  return data.data
}

export async function fetchMe(): Promise<UserInfo> {
  const { data } = await http.get<ApiResponse<UserInfo>>('/auth/me')
  return data.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

export interface RiderOption {
  username: string
  displayName: string
}

export async function fetchRiders(): Promise<RiderOption[]> {
  const { data } = await http.get<ApiResponse<RiderOption[]>>('/auth/riders')
  return data.data
}
