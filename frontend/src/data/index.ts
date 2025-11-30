import type { Tab } from '../types'

export const tabs: Tab[] = [
  { id: 'home', name: '主页', icon: 'fa-solid fa-house' },
  { id: 'course', name: '课程资料', icon: 'fa-solid fa-graduation-cap' },
  { id: 'tech', name: '技术文档', icon: 'fa-solid fa-code' },
  { id: 'info', name: '校园信息', icon: 'fa-solid fa-bullhorn' },
]

// Resources are now loaded from the backend API
// See src/api/index.ts for API calls
