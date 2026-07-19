import { createApp } from 'vue'
import { createPinia } from 'pinia'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'

import App from './App.vue'
import router from './router'

import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

import '@/access/index'
import '@/styles/tokens.css'
import '@/styles/global.css'

dayjs.locale('zh-cn')
dayjs.extend(relativeTime)

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')
