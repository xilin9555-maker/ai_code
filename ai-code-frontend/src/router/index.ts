import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    inMenu?: boolean
  }
}

export const routes = [
  { path: '/', name: 'home', component: HomePage, meta: { title: '创作空间', inMenu: true } },
  {
    path: '/drafts',
    name: 'drafts',
    component: () => import('@/pages/DraftsPage.vue'),
    meta: { title: '我的草稿', inMenu: true },
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('@/pages/AboutPage.vue'),
    meta: { title: '关于灵构', inMenu: true },
  },
  {
    path: '/user/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/pages/NotFoundPage.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 灵构`
})

export default router
