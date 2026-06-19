export type RoleType = 'ADMIN' | 'DISPATCHER' | 'RIDER' | 'CUSTOMER'

export type OrderStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'CANCELLED'

export interface UserInfo {
  id: number
  username: string
  role: RoleType
}

export interface LoginResponse {
  token: string
  tokenType: string
  user: UserInfo
}

export interface OrderItem {
  id: number
  trackingNo: string
  senderName: string
  senderPhone: string
  senderAddress: string
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  itemName: string
  weightKg: number
  distanceKm: number
  estimatedFee: number
  actualFee: number | null
  status: OrderStatus
  createdBy: string
  rider: string | null
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface OrderStats {
  total: number
  created: number
  assigned: number
  pickedUp: number
  inTransit: number
  delivered: number
  cancelled: number
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}
