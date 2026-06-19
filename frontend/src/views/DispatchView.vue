<template>
  <div class="grid gap-4">
    <ElCard>
      <div class="text-lg font-semibold text-slate-900">调度中心</div>
      <div class="mt-1 text-sm text-slate-500">处理订单派单、状态推进与异常取消。</div>
    </ElCard>

    <OrderTable
      v-model:page="query.page"
      v-model:size="query.size"
      :rows="orders"
      :total="total"
      :loading="loading"
      @change="loadData"
    >
      <template #title>调度订单池</template>
      <template #toolbar>
        <div class="flex flex-wrap items-center gap-2">
          <ElInput
            v-model="query.keyword"
            clearable
            placeholder="搜索运单号/手机号/姓名/地址/物品"
            class="!w-[280px]"
            :prefix-icon="Search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <ElSelect v-model="query.status" clearable placeholder="状态筛选" class="!w-32" @change="onFilterChange">
            <ElOption v-for="s in statuses" :key="s.value" :value="s.value" :label="s.label" />
          </ElSelect>
        </div>
      </template>
      <template #actions="{ row }">
        <div class="action-row">
          <ElButton class="table-action-btn" @click="openAssign(row)" :disabled="row.status !== 'CREATED'">派单</ElButton>
          <ElButton class="table-action-btn" type="primary" @click="openStatus(row)" :disabled="isTerminal(row.status)">更新状态</ElButton>
        </div>
      </template>
    </OrderTable>

    <ConfirmDialog
      v-model="assignVisible"
      title="确认派单"
      :content="`确认将运单 ${current?.trackingNo || ''} 派给骑手 ${selectedRiderLabel} 吗？`"
      :loading="actionLoading"
      @confirm="confirmAssign"
    />

    <ElDialog v-model="statusVisible" title="更新订单状态" width="460px" align-center>
      <ElForm :model="statusForm" label-width="90px">
        <ElFormItem label="目标状态">
          <ElSelect v-model="statusForm.targetStatus" placeholder="请选择状态" style="width: 100%">
            <ElOption v-for="item in nextStatusOptions" :key="item" :value="item" :label="statusText(item)" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="实际费用" v-if="statusForm.targetStatus === 'DELIVERED'">
          <ElInputNumber v-model="statusForm.actualFee" :min="0" :precision="2" style="width: 100%" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton class="table-action-btn" @click="statusVisible = false">取消</ElButton>
        <ElButton class="table-action-btn" type="primary" :loading="actionLoading" @click="confirmStatus">确认</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="assignInputVisible" title="派单信息" width="420px" align-center>
      <ElForm :model="assignForm" label-width="90px">
        <ElFormItem label="骑手账号">
          <ElSelect
            v-model="assignForm.riderUsername"
            filterable
            clearable
            placeholder="请选择骑手账号"
            style="width: 100%"
            :loading="riderLoading"
          >
            <ElOption
              v-for="rider in riderOptions"
              :key="rider.username"
              :label="`${rider.username} | ${rider.displayName}`"
              :value="rider.username"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton class="table-action-btn" @click="assignInputVisible = false">取消</ElButton>
        <ElButton class="table-action-btn" type="primary" @click="goAssignConfirm">下一步</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import OrderTable from '@/components/OrderTable.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { assignOrder, getOrders, updateOrderStatus } from '@/api/orders'
import { fetchRiders } from '@/api/auth'
import type { RiderOption } from '@/api/auth'
import type { OrderItem, OrderStatus } from '@/types'
import { showError, showSuccess } from '@/utils/message'
import { createDebouncedFunction } from '@/utils/debounce'
import { Search } from '@element-plus/icons-vue'

const loading = ref(false)
const actionLoading = ref(false)
const orders = ref<OrderItem[]>([])
const total = ref(0)
const current = ref<OrderItem | null>(null)

const query = reactive<{ page: number; size: number; status?: OrderStatus; keyword?: string }>({
  page: 1,
  size: 10,
  status: undefined,
  keyword: ''
})
const statuses: Array<{ value: OrderStatus; label: string }> = [
  { value: 'CREATED', label: '待接单' },
  { value: 'ASSIGNED', label: '已派单' },
  { value: 'PICKED_UP', label: '已取件' },
  { value: 'IN_TRANSIT', label: '配送中' },
  { value: 'DELIVERED', label: '已送达' },
  { value: 'CANCELLED', label: '已取消' }
]

const assignInputVisible = ref(false)
const assignVisible = ref(false)
const statusVisible = ref(false)
const riderLoading = ref(false)
const riderOptions = ref<RiderOption[]>([])

const assignForm = reactive({ riderUsername: '' })
const statusForm = reactive<{ targetStatus?: OrderStatus; actualFee?: number }>({ targetStatus: undefined, actualFee: undefined })
const selectedRiderLabel = computed(() => {
  const selected = riderOptions.value.find((item) => item.username === assignForm.riderUsername)
  if (!selected) return assignForm.riderUsername || '-'
  return `${selected.username} | ${selected.displayName}`
})

const nextStatusOptions = computed<OrderStatus[]>(() => {
  if (!current.value) return []
  const map: Record<OrderStatus, OrderStatus[]> = {
    CREATED: ['ASSIGNED', 'CANCELLED'],
    ASSIGNED: ['PICKED_UP', 'CANCELLED'],
    PICKED_UP: ['IN_TRANSIT', 'CANCELLED'],
    IN_TRANSIT: ['DELIVERED', 'CANCELLED'],
    DELIVERED: [],
    CANCELLED: []
  }
  return map[current.value.status]
})

function onFilterChange(): void {
  query.page = 1
  loadData()
}

function onSearch(): void {
  query.page = 1
  loadData()
}

function clearFilters(): void {
  query.keyword = ''
  query.status = undefined
  query.page = 1
  loadData()
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await getOrders(query)
    orders.value = res.content
    total.value = res.totalElements
  } finally {
    loading.value = false
  }
}

async function loadRiders(): Promise<void> {
  riderLoading.value = true
  try {
    riderOptions.value = await fetchRiders()
  } finally {
    riderLoading.value = false
  }
}

function openAssign(row: OrderItem): void {
  current.value = row
  assignForm.riderUsername = ''
  if (!riderOptions.value.length) {
    loadRiders().catch(() => undefined)
  }
  assignInputVisible.value = true
}

function goAssignConfirm(): void {
  if (!assignForm.riderUsername.trim()) {
    showError('请选择骑手账号')
    return
  }
  assignInputVisible.value = false
  assignVisible.value = true
}

async function confirmAssign(): Promise<void> {
  if (!current.value) return
  actionLoading.value = true
  try {
    await assignOrder(current.value.id, assignForm.riderUsername.trim())
    showSuccess('派单成功')
    assignVisible.value = false
    await loadData()
  } finally {
    actionLoading.value = false
  }
}

function openStatus(row: OrderItem): void {
  if (isTerminal(row.status)) {
    showError('已完成或已取消订单禁止更新状态')
    return
  }
  current.value = row
  statusForm.targetStatus = undefined
  statusForm.actualFee = undefined
  statusVisible.value = true
}

async function confirmStatus(): Promise<void> {
  if (!current.value || !statusForm.targetStatus) {
    showError('请选择目标状态')
    return
  }
  if (statusForm.targetStatus === 'DELIVERED' && (statusForm.actualFee === undefined || statusForm.actualFee === null)) {
    showError('送达状态必须填写实际费用')
    return
  }
  actionLoading.value = true
  try {
    await updateOrderStatus(current.value.id, statusForm.targetStatus, statusForm.actualFee)
    showSuccess('状态更新成功')
    statusVisible.value = false
    await loadData()
  } finally {
    actionLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadData(), loadRiders()])
})

function statusText(status: OrderStatus): string {
  const map: Record<OrderStatus, string> = {
    CREATED: '待接单',
    ASSIGNED: '已派单',
    PICKED_UP: '已取件',
    IN_TRANSIT: '配送中',
    DELIVERED: '已送达',
    CANCELLED: '已取消'
  }
  return map[status]
}

function isTerminal(status: OrderStatus): boolean {
  return status === 'DELIVERED' || status === 'CANCELLED'
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
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.action-row {
  display: flex;
  gap: 8px;
}
</style>
