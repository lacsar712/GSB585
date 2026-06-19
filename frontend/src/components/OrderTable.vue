<template>
  <ElCard class="rounded-2xl">
    <template #header>
      <div class="table-head">
        <slot name="title">订单列表</slot>
        <slot name="toolbar" />
      </div>
    </template>
    <ElTable :data="rows" stripe border table-layout="auto" class="order-table" v-loading="loading">
      <ElTableColumn prop="trackingNo" label="运单号" min-width="180" />
      <ElTableColumn label="状态" min-width="120">
        <template #default="scope">
          <ElTag :type="statusTagType(scope.row.status)" effect="light">{{ statusText(scope.row.status) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="senderName" label="发件人" min-width="120" />
      <ElTableColumn prop="senderPhone" label="发件电话" min-width="140" />
      <ElTableColumn prop="receiverName" label="收件人" min-width="120" />
      <ElTableColumn prop="receiverPhone" label="收件电话" min-width="140" />
      <ElTableColumn prop="itemName" label="物品" min-width="120" />
      <ElTableColumn prop="weightKg" label="重量(kg)" min-width="120" />
      <ElTableColumn label="预估费用" min-width="120">
        <template #default="scope">¥{{ Number(scope.row.estimatedFee || 0).toFixed(2) }}</template>
      </ElTableColumn>
      <ElTableColumn label="实际费用" min-width="120">
        <template #default="scope">
          {{ scope.row.actualFee === null ? '-' : `¥${Number(scope.row.actualFee).toFixed(2)}` }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="rider" label="骑手" min-width="120" />
      <ElTableColumn label="创建时间" min-width="180">
        <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="showActions" label="操作" min-width="180" fixed="right">
        <template #default="scope">
          <slot name="actions" :row="scope.row" />
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="pager">
      <ElPagination
        v-model:current-page="innerPage"
        v-model:page-size="innerSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        :layout="paginationLayout"
        :small="isMobile"
        prev-text="上一页"
        next-text="下一页"
        @size-change="trigger"
        @current-change="trigger"
      />
    </div>
  </ElCard>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { OrderItem } from '@/types'
import dayjs from 'dayjs'

const props = withDefaults(defineProps<{
  rows: OrderItem[]
  loading: boolean
  page: number
  size: number
  total: number
  showActions?: boolean
}>(), {
  showActions: true
})

const emits = defineEmits<{
  (e: 'update:page', v: number): void
  (e: 'update:size', v: number): void
  (e: 'change'): void
}>()

const innerPage = computed({
  get: () => props.page,
  set: (v: number) => emits('update:page', v)
})

const innerSize = computed({
  get: () => props.size,
  set: (v: number) => emits('update:size', v)
})

const windowWidth = ref(typeof window === 'undefined' ? 1024 : window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

const paginationLayout = computed(() => {
  if (windowWidth.value <= 480) {
    return 'sizes, prev, pager, next'
  }
  return 'total, sizes, prev, pager, next'
})

function handleResize(): void {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

function trigger(): void {
  emits('change')
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
</script>

<style scoped>
.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-weight: 600;
}

:deep(.el-card__body) {
  overflow: visible;
}

:deep(.order-table .el-scrollbar__wrap) {
  overflow-x: auto !important;
}

:deep(.order-table .el-scrollbar__bar.is-horizontal) {
  height: 12px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  padding: 0 4px 2px;
}

:deep(.pager .el-pagination) {
  width: 100%;
  justify-content: flex-end;
  gap: 8px 10px;
}

@media (max-width: 768px) {
  .pager {
    justify-content: flex-start;
  }

  :deep(.pager .el-pagination) {
    justify-content: flex-start;
    flex-wrap: wrap;
    row-gap: 10px;
  }
}
</style>
