<script setup lang="ts">
import { ref } from 'vue'

export interface ToastMessage {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
  duration?: number
}

const messages = ref<ToastMessage[]>([])
let idCounter = 0

const show = (type: ToastMessage['type'], message: string, duration = 3000) => {
  const id = ++idCounter
  messages.value.push({ id, type, message, duration })

  if (duration > 0) {
    setTimeout(() => {
      remove(id)
    }, duration)
  }

  return id
}

const remove = (id: number) => {
  const index = messages.value.findIndex(m => m.id === id)
  if (index > -1) {
    messages.value.splice(index, 1)
  }
}

const success = (message: string, duration?: number) => show('success', message, duration)
const error = (message: string, duration?: number) => show('error', message, duration ?? 5000)
const warning = (message: string, duration?: number) => show('warning', message, duration)
const info = (message: string, duration?: number) => show('info', message, duration)

const getIcon = (type: ToastMessage['type']) => {
  switch (type) {
    case 'success': return 'fa-check-circle'
    case 'error': return 'fa-exclamation-circle'
    case 'warning': return 'fa-exclamation-triangle'
    case 'info': return 'fa-info-circle'
  }
}

const getColorClass = (type: ToastMessage['type']) => {
  switch (type) {
    case 'success': return 'bg-emerald-50 border-emerald-200 text-emerald-800'
    case 'error': return 'bg-red-50 border-red-200 text-red-800'
    case 'warning': return 'bg-amber-50 border-amber-200 text-amber-800'
    case 'info': return 'bg-sky-50 border-sky-200 text-sky-800'
  }
}

const getIconColor = (type: ToastMessage['type']) => {
  switch (type) {
    case 'success': return 'text-emerald-500'
    case 'error': return 'text-red-500'
    case 'warning': return 'text-amber-500'
    case 'info': return 'text-sky-500'
  }
}

defineExpose({
  show,
  success,
  error,
  warning,
  info,
  remove
})
</script>

<template>
  <Teleport to="body">
    <div class="fixed bottom-4 right-4 z-[10000] flex flex-col gap-2 max-w-sm">
      <TransitionGroup name="toast">
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="[
            'flex items-start gap-3 px-4 py-3 rounded-lg shadow-lg border backdrop-blur-sm',
            getColorClass(msg.type)
          ]"
        >
          <i :class="['fa-solid text-lg mt-0.5', getIcon(msg.type), getIconColor(msg.type)]"></i>
          <p class="flex-1 text-sm font-medium">{{ msg.message }}</p>
          <button
            @click="remove(msg.id)"
            class="text-current opacity-50 hover:opacity-100 transition-opacity"
          >
            <i class="fa-solid fa-xmark"></i>
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active {
  animation: toast-in 0.3s ease-out;
}

.toast-leave-active {
  animation: toast-out 0.2s ease-in forwards;
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateX(100%);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes toast-out {
  from {
    opacity: 1;
    transform: translateX(0);
  }
  to {
    opacity: 0;
    transform: translateX(100%);
  }
}
</style>
