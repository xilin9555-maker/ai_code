import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { Button, ConfigProvider, Input, Layout, Menu, Popconfirm, Tag } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@/assets/styles.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
;[Button, ConfigProvider, Input, Layout, Menu, Popconfirm, Tag].forEach((component) =>
  app.use(component),
)

app.mount('#app')
