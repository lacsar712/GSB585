<template>
  <div class="grid gap-4">
    <ElCard class="rounded-2xl">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="text-lg font-semibold text-slate-900">运单查询</div>
            <div class="mt-1 text-sm text-slate-500">查看当前用户名下订单，支持关键字搜索与分页。</div>
          </div>
          <RouterLink v-if="canCreateOrder" to="/create" class="link-action-btn">去创建订单</RouterLink>
        </div>
      </template>

      <OrderTable
        class="card-flat"
        v-model:page="query.page"
        v-model:size="query.size"
        :rows="orders"
        :total="total"
        :loading="loading"
        @change="loadData"
      >
        <template #title>我的订单列表</template>
        <template #toolbar>
          <div class="flex items-center gap-2">
            <ElInput
              v-model="query.keyword"
              clearable
              placeholder="搜索运单号/手机号/姓名/地址/物品"
              class="!w-[320px]"
              :prefix-icon="Search"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
          </div>
        </template>
        <template #actions="{ row }">
          <div class="flex items-center gap-3">
            <ElButton class="table-action-btn" :disabled="row.status !== 'CREATED'" @click="openEdit(row)">编辑</ElButton>
            <ElButton class="table-action-btn" type="danger" :disabled="row.status !== 'CREATED'" @click="openDelete(row)">删除</ElButton>
            <ElButton class="table-action-btn" @click="openDetail(row)">详情</ElButton>
          </div>
        </template>
      </OrderTable>
    </ElCard>

    <!-- Detail Dialog -->
    <ElDialog
      v-model="detailVisible"
      title="运单详情"
      width="600px"
      align-center
      append-to-body
      class="premium-dialog"
    >
      <div v-if="current" class="p-4">
        <div class="mb-6 flex items-center justify-between rounded-xl bg-slate-50 p-4 border border-slate-100">
          <div>
            <div class="text-sm text-slate-500">运单号</div>
            <div class="text-xl font-bold text-slate-900 tracking-wide font-mono mt-1">{{ current.trackingNo }}</div>
          </div>
          <ElTag :type="statusTagType(current.status)" effect="dark" size="large" class="!rounded-lg !px-4 !font-bold">
            {{ statusText(current.status) }}
          </ElTag>
        </div>

        <div class="relative pl-6 space-y-8 before:absolute before:left-[7px] before:top-2 before:bottom-2 before:w-[2px] before:bg-slate-200">
          <!-- Sender -->
          <div class="relative">
             <div class="absolute -left-[29px] top-1 h-4 w-4 rounded-full border-2 border-slate-300 bg-white"></div>
             <div class="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">发件信息</div>
             <div class="text-base font-semibold text-slate-900">{{ current.senderName }} <span class="text-slate-400 font-normal mx-1">|</span> {{ current.senderPhone }}</div>
             <div class="text-sm text-slate-600 mt-1 leading-relaxed">{{ current.senderAddress }}</div>
          </div>

          <!-- Receiver -->
           <div class="relative">
             <div class="absolute -left-[29px] top-1 h-4 w-4 rounded-full border-2 border-indigo-500 bg-indigo-500 shadow-sm shadow-indigo-500/30"></div>
             <div class="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">收件信息</div>
             <div class="text-base font-semibold text-slate-900">{{ current.receiverName }} <span class="text-slate-400 font-normal mx-1">|</span> {{ current.receiverPhone }}</div>
             <div class="text-sm text-slate-600 mt-1 leading-relaxed">{{ current.receiverAddress }}</div>
          </div>
        </div>

        <div class="mt-8 grid grid-cols-1 gap-4 rounded-xl border border-slate-100 bg-white p-4 shadow-sm sm:grid-cols-2">
          <div>
             <div class="text-xs text-slate-400">物品信息</div>
             <div class="text-sm font-medium text-slate-900 mt-1">{{ current.itemName }} ({{ current.weightKg }}kg)</div>
          </div>
          <div>
             <div class="text-xs text-slate-400">负责骑手</div>
             <div class="text-sm font-medium text-slate-900 mt-1">{{ current.rider || '待分配' }}</div>
          </div>
          <div>
             <div class="text-xs text-slate-400">预估费用</div>
             <div class="text-sm font-medium text-slate-900 mt-1">¥{{ Number(current.estimatedFee || 0).toFixed(2) }}</div>
          </div>
          <div>
             <div class="text-xs text-slate-400">下单时间</div>
             <div class="text-sm font-medium text-slate-900 mt-1">{{ formatTime(current.createdAt) }}</div>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="px-6 py-4">
          <ElButton type="primary" class="table-action-btn !w-full" @click="detailVisible = false">关闭</ElButton>
        </div>
      </template>
    </ElDialog>

    <ElDialog
      v-model="editVisible"
      title="编辑订单"
      width="920px"
      align-center
      append-to-body
    >
      <OrderForm
        v-if="editingOrder"
        :initial-data="editingOrder"
        :loading="editLoading"
        @submit="onEditSubmit"
        @cancel="editVisible = false"
      />
    </ElDialog>

    <ConfirmDialog
      v-model="deleteVisible"
      title="确认删除订单"
      :content="`确认删除运单 ${deletingOrder?.trackingNo || ''} 吗？删除后不可恢复。`"
      :loading="deleteLoading"
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { deleteOrder, getOrders, updateOrder } from '@/api/orders'
import { useAuthStore } from '@/stores/auth'
import type { OrderItem } from '@/types'
import { showSuccess } from '@/utils/message'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import OrderTable from '@/components/OrderTable.vue'
import OrderForm from '@/components/OrderForm.vue'
import { createDebouncedFunction } from '@/utils/debounce'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const auth = useAuthStore()
const loading = ref(false)
const orders = ref<OrderItem[]>([])
const total = ref(0)
const current = ref<OrderItem | null>(null)
const detailVisible = ref(false)
const editVisible = ref(false)
const editLoading = ref(false)
const editingOrder = ref<OrderItem | null>(null)
const deleteVisible = ref(false)
const deleteLoading = ref(false)
const deletingOrder = ref<OrderItem | null>(null)
const canCreateOrder = computed(() => auth.role === 'ADMIN' || auth.role === 'CUSTOMER')

const query = reactive<{ page: number; size: number; keyword?: string }>({
  page: 1,
  size: 10,
  keyword: ''
})

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await getOrders({ page: query.page, size: query.size, keyword: query.keyword || undefined })
    orders.value = res.content
    total.value = res.totalElements
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  loadData()
}

function openDetail(row: OrderItem): void {
  current.value = row
  detailVisible.value = true
}

function openEdit(row: OrderItem): void {
  if (row.status !== 'CREATED') {
    return
  }
  editingOrder.value = { ...row }
  editVisible.value = true
}

async function onEditSubmit(data: Record<string, unknown>): Promise<void> {
  if (!editingOrder.value) return
  editLoading.value = true
  try {
    await updateOrder(editingOrder.value.id, data)
    showSuccess('订单编辑成功')
    editVisible.value = false
    await loadData()
  } finally {
    editLoading.value = false
  }
}

function openDelete(row: OrderItem): void {
  if (row.status !== 'CREATED') {
    return
  }
  deletingOrder.value = row
  deleteVisible.value = true
}

async function confirmDelete(): Promise<void> {
  if (!deletingOrder.value) return
  deleteLoading.value = true
  try {
    await deleteOrder(deletingOrder.value.id)
    showSuccess('订单删除成功')
    deleteVisible.value = false
    await loadData()
  } finally {
    deleteLoading.value = false
  }
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    CREATED: '待接单',
    ASSIGNED: '已派单',
    PICKED_UP: '已取件',
    IN_TRANSIT: '配送中',
    DELIVERED: '已送达',
    CANCELLED: '已取消'
  }
  return map[status] ?? status
}

function statusTagType(status: string): 'info' | 'success' | 'warning' | 'danger' | 'primary' {
  const map: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = {
    CREATED: 'info',
    ASSIGNED: 'primary',
    PICKED_UP: 'warning',
    IN_TRANSIT: 'warning',
    DELIVERED: 'success',
    CANCELLED: 'danger'
  }
  return map[status] ?? 'info'
}

function formatTime(value: string): string {
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

const debouncedSearch = createDebouncedFunction(() => {
  query.page = 1
  loadData()
}, 450)

watch(() => query.keyword, () => {
  debouncedSearch()
})

onBeforeUnmount(() => {
  debouncedSearch.cancel()
})

onMounted(loadData)
</script>

<style scoped>
/* Scoped styles replaced by Tailwind */
</style>
