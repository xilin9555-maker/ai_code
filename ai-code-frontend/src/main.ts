import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  Avatar,
  Button,
  ConfigProvider,
  Dropdown,
  Form,
  Input,
  Layout,
  Menu,
  Popconfirm,
  Space,
  Switch,
  Tag,
} from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@/assets/styles.css'

import App from './App.vue'
import router from './router'
import '@/access'

const app = createApp(App)

app.use(createPinia())
app.use(router)
;[
  Avatar,
  Button,
  ConfigProvider,
  Dropdown,
  Form,
  Input,
  Layout,
  Menu,
  Popconfirm,
  Space,
  Switch,
  Tag,
].forEach((component) => app.use(component))

app.mount('#app')
