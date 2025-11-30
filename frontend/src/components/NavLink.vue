<script setup lang="ts">
defineProps<{
  active: boolean
  icon: string
}>()
</script>

<template>
  <a
    href="#"
    class="inline-flex items-center px-2 pt-1 border-b-2 text-xs sm:text-sm font-medium transition-colors duration-200"
    :class="
      active
        ? 'border-sky-500 text-sky-900'
        : 'border-transparent text-slate-500 hover:border-sky-200 hover:text-sky-700'
    "
  >
    <i :class="icon" class="mr-1.5"></i>
    <slot></slot>
  </a>
</template>
