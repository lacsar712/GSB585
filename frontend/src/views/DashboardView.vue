<template>
  <div class="dashboard-shell space-y-5">
    <section class="hero-panel rounded-3xl p-6">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p class="hero-kicker">OPERATIONS</p>
          <h2 class="hero-title">同城履约运营看板</h2>
          <p class="mt-2 max-w-2xl text-sm text-cyan-100/90">
            聚焦核心数据：待处理、在途、已完成，支持快速派单与运单跟踪。
          </p>
        </div>
        <div class="flex flex-wrap gap-3">
          <ElButton v-if="canCreateOrder" type="primary" @click="$router.push('/create')">创建运单</ElButton>
          <ElButton class="btn-ghost-on-dark" @click="$router.push('/dispatch')">进入调度</ElButton>
        </div>
      </div>
    </section>

    <section class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <article v-for="item in statCards" :key="item.label" class="metric-card">
        <p class="metric-label">{{ item.label }}</p>
        <p class="metric-value">{{ item.value }}</p>
        <p class="metric-desc">{{ item.desc }}</p>
      </article>
    </section>

    <section class="rounded-3xl border border-slate-200/80 bg-white p-5 sm:p-6">
      <div class="mb-4 flex items-center justify-between gap-3">
        <div>
          <h3 class="panel-title">履约流程概览</h3>
          <p class="panel-sub">五个核心节点的订单占比</p>
        </div>
      </div>

      <div class="space-y-3">
        <div v-for="step in flowSteps" :key="step.label" class="funnel-row">
          <div class="flex min-w-0 items-center gap-3">
            <span class="funnel-index">{{ step.index }}</span>
            <p class="truncate text-sm font-semibold text-slate-800">{{ step.label }} · {{ step.count }} 单</p>
          </div>
          <div class="funnel-progress">
            <div class="funnel-progress-value" :style="{ width: `${step.percent}%` }"></div>
          </div>
          <div class="w-16 text-right text-sm font-semibold text-slate-700">{{ step.percent }}%</div>
        </div>
      </div>
    </section>

    <section class="rounded-3xl border border-slate-200/80 bg-white overflow-hidden">
      <div class="border-b border-slate-100 px-5 py-5 sm:px-6">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="panel-title">实时订单池</h3>
            <p class="panel-sub">按关键字和状态快速筛选</p>
          </div>
          <div class="flex flex-wrap gap-2">
            <ElInput
              v-model="query.keyword"
              clearable
              placeholder="搜索运单号/手机号/姓名"
              class="!w-[260px]"
              :prefix-icon="Search"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
            <ElSelect v-model="query.status" clearable placeholder="状态筛选" class="!w-34" @change="onFilterChange">
              <ElOption v-for="s in statuses" :key="s.value" :value="s.value" :label="s.label" />
            </ElSelect>
          </div>
        </div>
      </div>

      <OrderTable
        v-model:page="query.page"
        v-model:size="query.size"
        :rows="orders"
        :total="total"
        :loading="loading"
        :show-actions="false"
        @change="loadData"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getOrders, getStats } from '@/api/orders'
import type { OrderItem, OrderStats, OrderStatus } from '@/types'
import { useAuthStore } from '@/stores/auth'
import OrderTable from '@/components/OrderTable.vue'
import { createDebouncedFunction } from '@/utils/debounce'
import { Search } from '@element-plus/icons-vue'

const auth = useAuthStore()

const loading = ref(false)
const orders = ref<OrderItem[]>([])
const total = ref(0)
const stats = ref<OrderStats>({ total: 0, created: 0, assigned: 0, pickedUp: 0, inTransit: 0, delivered: 0, cancelled: 0 })

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

const completionRate = computed(() => pct(stats.value.delivered, Math.max(stats.value.total, 1)))
const canCreateOrder = computed(() => auth.role === 'ADMIN' || auth.role === 'CUSTOMER')

const statCards = computed(() => [
  { label: '订单总量', value: stats.value.total, desc: '当前系统订单规模' },
  { label: '待处理', value: stats.value.created, desc: '待接单与待派发' },
  { label: '配送中', value: stats.value.inTransit, desc: '在途履约订单' },
  { label: '完成率', value: `${completionRate.value}%`, desc: `${stats.value.delivered} 单已签收` }
])

const flowSteps = computed(() => {
  const totalCount = Math.max(stats.value.total, 1)
  return [
    { index: '01', label: '创建', count: stats.value.created, percent: pct(stats.value.created, totalCount) },
    { index: '02', label: '派单', count: stats.value.assigned, percent: pct(stats.value.assigned, totalCount) },
    { index: '03', label: '取件', count: stats.value.pickedUp, percent: pct(stats.value.pickedUp, totalCount) },
    { index: '04', label: '在途', count: stats.value.inTransit, percent: pct(stats.value.inTransit, totalCount) },
    { index: '05', label: '签收', count: stats.value.delivered, percent: pct(stats.value.delivered, totalCount) }
  ]
})

function pct(numerator: number, denominator: number): number {
  return Math.min(100, Math.round((numerator / denominator) * 100))
}

function onFilterChange(): void {
  query.page = 1
  loadData()
}

function onSearch(): void {
  query.page = 1
  loadData()
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const [pageData, statData] = await Promise.all([getOrders(query), getStats()])
    orders.value = pageData.content
    total.value = pageData.totalElements
    stats.value = statData
  } finally {
    loading.value = false
  }
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
.hero-panel {
  position: relative;
  overflow: hidden;
  color: #f8fafc;
  background:
    radial-gradient(circle at 20% 15%, rgba(34, 211, 238, 0.35) 0, transparent 42%),
    radial-gradient(circle at 90% 85%, rgba(59, 130, 246, 0.42) 0, transparent 40%),
    linear-gradient(120deg, #0f172a 0%, #1e293b 52%, #0f172a 100%);
}

.hero-kicker {
  letter-spacing: 0.18em;
  font-size: 11px;
  font-weight: 800;
  color: rgba(186, 230, 253, 0.95);
}

.hero-title {
  margin-top: 6px;
  margin-bottom: 0;
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.05;
  font-weight: 900;
}

.metric-card {
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 18px;
  background: linear-gradient(160deg, #ffffff 0%, #f8fafc 100%);
}

.metric-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-value {
  margin-top: 8px;
  margin-bottom: 4px;
  color: #0f172a;
  font-size: 38px;
  line-height: 1;
  font-weight: 900;
}

.metric-desc {
  color: #475569;
  font-size: 13px;
}

.panel-title {
  margin: 0;
  font-size: 19px;
  color: #0f172a;
  font-weight: 800;
}

.panel-sub {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.funnel-row {
  display: grid;
  align-items: center;
  gap: 12px;
  grid-template-columns: minmax(140px, 1fr) minmax(120px, 2fr) 62px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px 12px;
}

.funnel-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 800;
}

.funnel-progress {
  height: 9px;
  background: #e2e8f0;
  border-radius: 999px;
  overflow: hidden;
}

.funnel-progress-value {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0ea5e9 0%, #3b82f6 100%);
}

@media (max-width: 880px) {
  .funnel-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
