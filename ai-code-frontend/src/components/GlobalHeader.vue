<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowUpOutlined,
  DownOutlined,
  LogoutOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { message, type MenuProps } from 'ant-design-vue'
import { routes } from '@/router'
import { userLogout } from '@/api/userController'
import { checkAccess } from '@/access/checkAccess'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
/**
 * 根据当前用户的真实权限生成导航菜单。
 * 菜单和路由守卫共用 checkAccess，因此看得到的入口一定可以进入；专门标记为隐藏的
 * 登录页、错误页等辅助页面不会出现在主导航中。
 */
const menuItems = computed(() =>
  routes
    .filter(
      (item) =>
        item.meta.inMenu &&
        !item.meta.hideInMenu &&
        checkAccess(loginUserStore.loginUser, item.meta.access),
    )
    .map((item) => ({ key: item.path, label: item.meta.title })),
)
const selectedKeys = computed(() => [route.path])
const displayName = computed(
  () => loginUserStore.loginUser.userName || loginUserStore.loginUser.userAccount || '无名',
)
const loggingOut = ref(false)
const onMenuClick: MenuProps['onClick'] = ({ key }) => router.push(String(key))

/**
 * 注销当前用户，并在服务端与前端同时清理登录状态。
 * 服务端成功删除 Session 中的用户信息后，Pinia 会立即恢复游客状态，
 * 最后使用 replace 返回主页，避免浏览器后退时再次回到注销前的页面。
 */
async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true

  try {
    const response = await userLogout()
    if (response.data.code === 0 && response.data.data) {
      loginUserStore.clearLoginUser()
      message.success('已退出登录')
      await router.replace('/')
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '退出登录失败，请稍后重试')
  } finally {
    loggingOut.value = false
  }
}
</script>
<template>
  <a-layout-header class="global-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand" aria-label="灵构首页">
        <img src="/logo.svg" width="36" height="36" alt="" />
        <span>灵构<span class="brand-en">LINGBUILD</span></span>
      </RouterLink>
      <nav aria-label="主导航">
        <a-menu
          mode="horizontal"
          :selected-keys="selectedKeys"
          :items="menuItems"
          @click="onMenuClick"
        />
      </nav>
      <a-dropdown
        v-if="loginUserStore.initialized && loginUserStore.loginUser.id"
        :trigger="['hover']"
        placement="bottomRight"
      >
        <button class="user-menu-trigger" type="button" :disabled="loggingOut">
          <a-avatar :src="loginUserStore.loginUser.userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span class="header-user-name">{{ displayName }}</span>
          <DownOutlined class="menu-arrow" />
        </button>
        <template #overlay>
          <a-menu class="user-dropdown-menu">
            <a-menu-item key="settings" @click="router.push('/user/settings')">
              <SettingOutlined />
              <span>个人设置</span>
            </a-menu-item>
            <a-menu-item key="logout" :disabled="loggingOut" @click="handleLogout">
              <LogoutOutlined />
              <span>{{ loggingOut ? '正在退出…' : '退出登录' }}</span>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
      <a-button
        v-else
        class="login-button"
        :loading="!loginUserStore.initialized"
        @click="router.push('/user/login')"
      >
        登录 <ArrowUpOutlined v-if="loginUserStore.initialized" class="diagonal-arrow" />
      </a-button>
    </div>
  </a-layout-header>
</template>

<style scoped>
.user-menu-trigger {
  flex-shrink: 0;
  max-width: 210px;
  padding: 4px 7px 4px 4px;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 22px;
  font-size: 13px;
  font-weight: 600;
  transition:
    background 0.2s,
    border-color 0.2s;
}

.user-menu-trigger:hover,
.user-menu-trigger:focus-visible {
  background: #eeece5;
  border-color: #dedcd2;
}

.user-menu-trigger:disabled {
  cursor: wait;
  opacity: 0.7;
}

.header-user-name {
  display: inline-block;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.menu-arrow {
  color: #96978d;
  font-size: 10px;
}

.user-dropdown-menu {
  min-width: 142px;
}

.user-dropdown-menu :deep(.ant-dropdown-menu-item) {
  gap: 8px;
}
</style>
