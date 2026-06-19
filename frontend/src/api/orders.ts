import type { ApiResponse, OrderItem, OrderStats, OrderStatus, PageResponse } from '@/types'
import http from './http'

export interface ListParams {
  page: number
  size: number
  status?: OrderStatus
  keyword?: string
}

export async function getOrders(params: ListParams): Promise<PageResponse<OrderItem>> {
  const { data } = await http.get<ApiResponse<PageResponse<OrderItem>>>('/orders', { params })
  return data.data
}

export async function getOrderByTracking(trackingNo: string): Promise<OrderItem> {
  const { data } = await http.get<ApiResponse<OrderItem>>(`/orders/tracking/${trackingNo}`)
  return data.data
}

export async function createOrder(payload: Record<string, unknown>): Promise<OrderItem> {
  const { data } = await http.post<ApiResponse<OrderItem>>('/orders', payload)
  return data.data
}

export async function updateOrder(id: number, payload: Record<string, unknown>): Promise<OrderItem> {
  const { data } = await http.put<ApiResponse<OrderItem>>(`/orders/${id}`, payload)
  return data.data
}

export async function deleteOrder(id: number): Promise<void> {
  await http.delete(`/orders/${id}`)
}


export async function assignOrder(id: number, riderUsername: string): Promise<OrderItem> {
  const { data } = await http.post<ApiResponse<OrderItem>>(`/orders/${id}/assign`, { riderUsername })
  return data.data
}

export async function updateOrderStatus(id: number, targetStatus: OrderStatus, actualFee?: number): Promise<OrderItem> {
  const { data } = await http.post<ApiResponse<OrderItem>>(`/orders/${id}/status`, {
    targetStatus,
    actualFee
  })
  return data.data
}

export async function getStats(): Promise<OrderStats> {
  const { data } = await http.get<ApiResponse<OrderStats>>('/orders/stats')
  return data.data
}

export async function getHealth(): Promise<{ status: string; time: string }> {
  const { data } = await http.get<ApiResponse<{ status: string; time: string }>>('/health')
  return data.data
}
