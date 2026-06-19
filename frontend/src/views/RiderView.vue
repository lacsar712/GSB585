<template>
  <div class="grid gap-4">
    <ElCard>
      <div class="text-lg font-semibold text-slate-900">骑手工作台</div>
      <div class="mt-1 text-sm text-slate-500">仅展示当前骑手分配到的订单，可执行状态推进。</div>
    </ElCard>

    <OrderTable
      v-model:page="query.page"
      v-model:size="query.size"
      :rows="orders"
      :total="total"
      :loading="loading"
      @change="loadData"
    >
      <template #title>我的配送订单</template>
      <template #toolbar>
        <div class="flex items-center gap-2">
          <ElInput
            v-model="query.keyword"
            clearable
            placeholder="搜索运单号/手机号/姓名/地址/物品"
            class="!w-[280px]"
            :prefix-icon="Search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
        </div>
      </template>
      <template #actions="{ row }">
        <div class="action-row">
          <ElButton class="table-action-btn" type="primary" @click="openStatus(row)" :disabled="isTerminal(row.status)">推进状态</ElButton>
        </div>
      </template>
    </OrderTable>
  </div>

  <ElDialog v-model="visible" title="推进配送状态" width="460px" align-center>
    <ElForm :model="form" label-width="90px">
      <ElFormItem label="目标状态">
        <ElSelect v-model="form.targetStatus" placeholder="请选择状态" style="width: 100%">
          <ElOption v-for="item in nextOptions" :key="item" :label="statusText(item)" :value="item" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="实际费用" v-if="form.targetStatus === 'DELIVERED'">
        <ElInputNumber v-model="form.actualFee" :min="0" :precision="2" style="width: 100%" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton class="table-action-btn" @click="visible = false">取消</ElButton>
      <ElButton class="table-action-btn" type="primary" :loading="actionLoading" @click="submit">确认</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import OrderTable from '@/components/OrderTable.vue'
import { getOrders, updateOrderStatus } from '@/api/orders'
import type { OrderItem, OrderStatus } from '@/types'
import { showError, showSuccess } from '@/utils/message'
import { createDebouncedFunction } from '@/utils/debounce'
import { Search } from '@element-plus/icons-vue'

const loading = ref(false)
const actionLoading = ref(false)
const orders = ref<OrderItem[]>([])
const total = ref(0)
const current = ref<OrderItem | null>(null)
const visible = ref(false)

const query = reactive<{ page: number; size: number; keyword?: string }>({ page: 1, size: 10, keyword: '' })
const form = reactive<{ targetStatus?: OrderStatus; actualFee?: number }>({ targetStatus: undefined, actualFee: undefined })

const nextOptions = computed<OrderStatus[]>(() => {
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

function openStatus(row: OrderItem): void {
  if (isTerminal(row.status)) {
    showError('已完成或已取消订单禁止更新状态')
    return
  }
  current.value = row
  form.targetStatus = undefined
  form.actualFee = undefined
  visible.value = true
}

async function submit(): Promise<void> {
  if (!current.value || !form.targetStatus) {
    showError('请选择目标状态')
    return
  }
  if (form.targetStatus === 'DELIVERED' && (form.actualFee === undefined || form.actualFee === null)) {
    showError('送达状态必须填写实际费用')
    return
  }
  actionLoading.value = true
  try {
    await updateOrderStatus(current.value.id, form.targetStatus, form.actualFee)
    showSuccess('状态更新成功')
    visible.value = false
    await loadData()
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadData)

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

function onSearch(): void {
  query.page = 1
  loadData()
}

function clearFilters(): void {
  query.keyword = ''
  query.page = 1
  loadData()
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
