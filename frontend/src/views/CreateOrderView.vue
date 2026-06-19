
<template>
  <ElCard class="rounded-2xl">
    <template #header>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="title">创建订单</div>
          <div class="sub-title">录入发件与收件信息，系统自动生成运单号</div>
        </div>
        <RouterLink to="/track" class="link-action-btn">去运单查询</RouterLink>
      </div>
    </template>
    <OrderForm :loading="loading" @submit="onSubmit" @cancel="onReset" ref="formComp" />
  </ElCard>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { createOrder } from '@/api/orders'
import { showSuccess } from '@/utils/message'
import OrderForm from '@/components/OrderForm.vue'

const loading = ref(false)
const formComp = ref()

async function onSubmit(data: any): Promise<void> {
  loading.value = true
  try {
    const result = await createOrder(data)
    showSuccess(`订单创建成功，运单号：${result.trackingNo}`)
    formComp.value?.resetForm()
  } finally {
    loading.value = false
  }
}

function onReset(): void {
  formComp.value?.resetForm()
}
</script>

<style scoped>
.title {
  font-size: 20px;
  font-weight: 600;
}

.sub-title {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}
</style>
