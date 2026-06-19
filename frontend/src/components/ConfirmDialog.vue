<template>
  <ElDialog v-model="visible" :title="title" width="420px" align-center>
    <div class="confirm-content">{{ content }}</div>
    <template #footer>
      <ElButton class="table-action-btn" @click="visible = false">取消</ElButton>
      <ElButton type="primary" class="table-action-btn" :loading="loading" @click="onConfirm">确认</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  content: string
  loading?: boolean
}>()

const emits = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emits('update:modelValue', value)
})

function onConfirm(): void {
  emits('confirm')
}
</script>

<style scoped>
.confirm-content {
  line-height: 1.8;
  color: #1f2937;
}
</style>
