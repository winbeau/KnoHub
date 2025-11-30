<script setup lang="ts">
import { ref } from 'vue'

interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'danger' | 'warning' | 'info'
}

const visible = ref(false)
const options = ref<ConfirmOptions>({
  title: '',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  type: 'info'
})

let resolvePromise: ((value: boolean) => void) | null = null

const show = (opts: ConfirmOptions): Promise<boolean> => {
  options.value = {
    title: opts.title || '确认操作',
    message: opts.message,
    confirmText: opts.confirmText || '确定',
    cancelText: opts.cancelText || '取消',
    type: opts.type || 'info'
  }
  visible.value = true

  return new Promise((resolve) => {
    resolvePromise = resolve
  })
}

const handleConfirm = () => {
  visible.value = false
  resolvePromise?.(true)
  resolvePromise = null
}

const handleCancel = () => {
  visible.value = false
  resolvePromise?.(false)
  resolvePromise = null
}

const getIconClass = () => {
  switch (options.value.type) {
    case 'danger': return 'fa-trash text-red-500 bg-red-100'
    case 'warning': return 'fa-exclamation-triangle text-amber-500 bg-amber-100'
    default: return 'fa-question-circle text-sky-500 bg-sky-100'
  }
}

const getConfirmButtonClass = () => {
  switch (options.value.type) {
    case 'danger': return 'bg-red-600 hover:bg-red-700 focus:ring-red-500'
    case 'warning': return 'bg-amber-600 hover:bg-amber-700 focus:ring-amber-500'
    default: return 'bg-sky-600 hover:bg-sky-700 focus:ring-sky-500'
  }
}

defineExpose({
  show
})
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm">
      <div
        v-if="visible"
        class="fixed inset-0 z-[10001] flex items-center justify-center bg-black/40 backdrop-blur-sm"
        @click.self="handleCancel"
      >
        <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 overflow-hidden transform transition-all">
          <!-- Header -->
          <div class="px-6 pt-6 pb-4">
            <div class="flex items-start gap-4">
              <div :class="['w-12 h-12 rounded-full flex items-center justify-center flex-shrink-0', getIconClass().split(' ').slice(1).join(' ')]">
                <i :class="['fa-solid text-xl', getIconClass().split(' ')[0]]"></i>
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="text-lg font-semibold text-slate-900">{{ options.title }}</h3>
                <p class="mt-2 text-sm text-slate-600 leading-relaxed">{{ options.message }}</p>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="px-6 py-4 bg-slate-50 flex justify-end gap-3">
            <button
              @click="handleCancel"
              class="px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-slate-400 transition-colors"
            >
              {{ options.cancelText }}
            </button>
            <button
              @click="handleConfirm"
              :class="[
                'px-4 py-2 text-sm font-medium text-white rounded-lg focus:outline-none focus:ring-2 focus:ring-offset-2 transition-colors',
                getConfirmButtonClass()
              ]"
            >
              {{ options.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm-enter-active {
  animation: confirm-in 0.2s ease-out;
}

.confirm-leave-active {
  animation: confirm-out 0.15s ease-in forwards;
}

.confirm-enter-active > div {
  animation: confirm-scale-in 0.2s ease-out;
}

.confirm-leave-active > div {
  animation: confirm-scale-out 0.15s ease-in forwards;
}

@keyframes confirm-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes confirm-out {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

@keyframes confirm-scale-in {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes confirm-scale-out {
  from {
    opacity: 1;
    transform: scale(1);
  }
  to {
    opacity: 0;
    transform: scale(0.95);
  }
}
</style>
