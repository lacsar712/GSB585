import { ElMessage } from 'element-plus'

const defaultDuration = 2500

export function showSuccess(message: string): void {
  ElMessage({ type: 'success', message, duration: defaultDuration, showClose: true })
}

export function showError(message: string): void {
  ElMessage({ type: 'error', message, duration: defaultDuration, showClose: true })
}
