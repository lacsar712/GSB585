<template>
  <ElForm ref="formRef" :model="form" :rules="rules" label-width="110px" class="form-area" size="large">
    <div class="form-grid">
      <ElFormItem label="发件人姓名" prop="senderName"><ElInput v-model="form.senderName" placeholder="请输入姓名" /></ElFormItem>
      <ElFormItem label="发件人电话" prop="senderPhone"><ElInput v-model="form.senderPhone" placeholder="请输入电话" /></ElFormItem>
      <ElFormItem label="发件地址" prop="senderAddress"><ElInput v-model="form.senderAddress" placeholder="请输入详细地址" /></ElFormItem>
      <ElFormItem label="收件人姓名" prop="receiverName"><ElInput v-model="form.receiverName" placeholder="请输入姓名" /></ElFormItem>
      <ElFormItem label="收件人电话" prop="receiverPhone"><ElInput v-model="form.receiverPhone" placeholder="请输入电话" /></ElFormItem>
      <ElFormItem label="收件地址" prop="receiverAddress"><ElInput v-model="form.receiverAddress" placeholder="请输入详细地址" /></ElFormItem>
      <ElFormItem label="物品名称" prop="itemName"><ElInput v-model="form.itemName" placeholder="例如：文件、电子产品" /></ElFormItem>
      <ElFormItem label="重量(kg)" prop="weightKg">
        <ElInputNumber v-model="form.weightKg" :min="0.1" :precision="2" class="!w-full" controls-position="right" placeholder="请输入重量" />
      </ElFormItem>
    </div>
    <div class="actions pt-4 border-t border-slate-100 flex justify-end gap-3 mt-6">
      <ElButton class="table-action-btn" @click="onCancel">取消</ElButton>
      <ElButton type="primary" class="table-action-btn" :loading="loading" @click="submit">提交</ElButton>
    </div>
  </ElForm>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { showError } from '@/utils/message'

const props = defineProps<{
  initialData?: any
  loading: boolean
}>()

const emits = defineEmits<{
  (e: 'submit', data: any): void
  (e: 'cancel'): void
}>()

const formRef = ref<FormInstance>()
const form = reactive({
  senderName: '',
  senderPhone: '',
  senderAddress: '',
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  itemName: '',
  weightKg: 1,
  distanceKm: 3
})

function normalizeText(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLowerCase()
}

function normalizePhone(value: string): string {
  return value.replace(/\D/g, '')
}

const validateDifferentNames = (_: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value?.trim()) {
    callback()
    return
  }
  if (normalizeText(value) === normalizeText(form.receiverName)) {
    callback(new Error('发件人姓名与收件人姓名不能相同'))
    return
  }
  callback()
}

const validateDifferentReceiverNames = (_: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value?.trim()) {
    callback()
    return
  }
  if (normalizeText(value) === normalizeText(form.senderName)) {
    callback(new Error('收件人姓名与发件人姓名不能相同'))
    return
  }
  callback()
}

const validateDifferentPhones = (_: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value?.trim()) {
    callback()
    return
  }
  if (normalizePhone(value) === normalizePhone(form.receiverPhone)) {
    callback(new Error('发件人电话与收件人电话不能相同'))
    return
  }
  callback()
}

const validateDifferentReceiverPhones = (_: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value?.trim()) {
    callback()
    return
  }
  if (normalizePhone(value) === normalizePhone(form.senderPhone)) {
    callback(new Error('收件人电话与发件人电话不能相同'))
    return
  }
  callback()
}

const validateDifferentAddresses = (_: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value?.trim()) {
    callback()
    return
  }
  if (normalizeText(value) === normalizeText(form.receiverAddress)) {
    callback(new Error('发件地址与收件地址不能相同'))
    return
  }
  callback()
}

const validateDifferentReceiverAddresses = (_: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value?.trim()) {
    callback()
    return
  }
  if (normalizeText(value) === normalizeText(form.senderAddress)) {
    callback(new Error('收件地址与发件地址不能相同'))
    return
  }
  callback()
}

const rules: FormRules = {
  senderName: [
    { required: true, message: '请输入发件人姓名', trigger: 'blur' },
    { min: 2, max: 32, message: '发件人姓名长度需在2-32字符', trigger: 'blur' },
    { validator: validateDifferentNames, trigger: ['blur', 'change'] }
  ],
  senderPhone: [
    { required: true, message: '请输入发件人电话', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入11位中国大陆手机号', trigger: 'blur' },
    { validator: validateDifferentPhones, trigger: ['blur', 'change'] }
  ],
  senderAddress: [
    { required: true, message: '请输入发件地址', trigger: 'blur' },
    { min: 5, max: 255, message: '发件地址长度需在5-255字符', trigger: 'blur' },
    { validator: validateDifferentAddresses, trigger: ['blur', 'change'] }
  ],
  receiverName: [
    { required: true, message: '请输入收件人姓名', trigger: 'blur' },
    { min: 2, max: 32, message: '收件人姓名长度需在2-32字符', trigger: 'blur' },
    { validator: validateDifferentReceiverNames, trigger: ['blur', 'change'] }
  ],
  receiverPhone: [
    { required: true, message: '请输入收件人电话', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入11位中国大陆手机号', trigger: 'blur' },
    { validator: validateDifferentReceiverPhones, trigger: ['blur', 'change'] }
  ],
  receiverAddress: [
    { required: true, message: '请输入收件地址', trigger: 'blur' },
    { min: 5, max: 255, message: '收件地址长度需在5-255字符', trigger: 'blur' },
    { validator: validateDifferentReceiverAddresses, trigger: ['blur', 'change'] }
  ],
  itemName: [
    { required: true, message: '请输入物品名称', trigger: 'blur' },
    { min: 2, max: 64, message: '物品名称长度需在2-64字符', trigger: 'blur' }
  ],
  weightKg: [
    { required: true, message: '请输入重量', trigger: 'change' },
    { type: 'number', min: 0.1, max: 50, message: '重量需在0.1kg-50kg之间', trigger: 'change' }
  ]
}

watch(() => props.initialData, (newVal) => {
  if (newVal) {
    Object.assign(form, {
      senderName: newVal.senderName,
      senderPhone: newVal.senderPhone,
      senderAddress: newVal.senderAddress,
      receiverName: newVal.receiverName,
      receiverPhone: newVal.receiverPhone,
      receiverAddress: newVal.receiverAddress,
      itemName: newVal.itemName,
      weightKg: newVal.weightKg || 1,
      distanceKm: newVal.distanceKm || 3
    })
  } else {
    resetForm()
  }
}, { immediate: true })

watch([() => form.senderName, () => form.receiverName], () => {
  formRef.value?.validateField(['senderName', 'receiverName']).catch(() => undefined)
})

watch([() => form.senderPhone, () => form.receiverPhone], () => {
  formRef.value?.validateField(['senderPhone', 'receiverPhone']).catch(() => undefined)
})

watch([() => form.senderAddress, () => form.receiverAddress], () => {
  formRef.value?.validateField(['senderAddress', 'receiverAddress']).catch(() => undefined)
})

async function submit(): Promise<void> {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) {
    showError('请先修正表单项')
    return
  }
  emits('submit', {
    ...form,
    senderName: form.senderName.trim(),
    receiverName: form.receiverName.trim(),
    senderPhone: normalizePhone(form.senderPhone),
    receiverPhone: normalizePhone(form.receiverPhone),
    senderAddress: form.senderAddress.trim(),
    receiverAddress: form.receiverAddress.trim(),
    itemName: form.itemName.trim()
  })
}

function onCancel(): void {
  emits('cancel')
}

function resetForm(): void {
  formRef.value?.resetFields()
  // Reset reactive object manually if needed, but resetFields handles prop-bound fields
  form.weightKg = 1
  form.distanceKm = 3
}

defineExpose({
  resetForm
})
</script>

<style scoped>
.form-area {
  margin-top: 6px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 18px;
}

.actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 992px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
