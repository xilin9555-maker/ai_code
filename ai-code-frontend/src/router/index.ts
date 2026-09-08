import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import ACCESS_ENUM, { type AccessValue } from '@/access/accessEnum'
import HomePage from '@/pages/HomePage.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    inMenu?: boolean
    hideInMenu?: boolean
    access?: AccessValue
  }
}

type AppRouteRecord = RouteRecordRaw & {
  meta: {
    title: string
    inMenu?: boolean
    hideInMenu?: boolean
    access?: AccessValue
  }
}

export const routes: AppRouteRecord[] = [
  { path: '/', name: 'home', component: HomePage, meta: { title: '创作空间', inMenu: true } },
  {
    path: '/drafts',
    name: 'drafts',
    component: () => import('@/pages/DraftsPage.vue'),
    meta: { title: '我的草稿', inMenu: true, access: ACCESS_ENUM.USER },
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
    meta: { title: '登录', hideInMenu: true },
  },
  {
    path: '/user/register',
    name: 'register',
    component: () => import('@/pages/RegisterPage.vue'),
    meta: { title: '注册', hideInMenu: true },
  },
  {
    path: '/admin/user-manage',
    name: 'user-manage',
    component: () => import('@/pages/UserManagePage.vue'),
    meta: { title: '用户管理', inMenu: true, access: ACCESS_ENUM.ADMIN },
  },
  {
    path: '/user/settings',
    name: 'user-settings',
    component: () => import('@/pages/UserSettingsPage.vue'),
    meta: { title: '个人设置', hideInMenu: true, access: ACCESS_ENUM.USER },
  },
  {
    path: '/no-auth',
    name: 'no-auth',
    component: () => import('@/pages/NoAuthPage.vue'),
    meta: { title: '无权访问', hideInMenu: true },
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
