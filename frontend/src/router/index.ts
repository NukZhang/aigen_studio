import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/requirements'
  },
  {
    path: '/requirements',
    name: 'Requirements',
    component: () => import('@/views/Requirements.vue')
  },
  {
    path: '/requirements/:id',
    name: 'RequirementDetail',
    component: () => import('@/views/RequirementDetail.vue')
  },
  {
    path: '/jobs',
    name: 'Jobs',
    component: () => import('@/views/Jobs.vue')
  },
  {
    path: '/jobs/:id',
    name: 'JobDetail',
    component: () => import('@/views/JobDetail.vue')
  },
  {
    path: '/artifacts',
    name: 'Artifacts',
    component: () => import('@/views/Artifacts.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router