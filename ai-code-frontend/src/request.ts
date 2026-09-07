import axios from 'axios'
import { message } from 'ant-design-vue'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
  withCredentials: true,
})

let redirecting = false
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      if (
        body.code === 40100 &&
        !response.config.url?.includes('/user/get/login') &&
        window.location.pathname !== '/user/login' &&
        !redirecting
      ) {
        redirecting = true
        message.warning('请先登录')
        const redirect = window.location.pathname + window.location.search + window.location.hash
        window.location.assign(`/user/login?redirect=${encodeURIComponent(redirect)}`)
      }
      return Promise.reject(new Error(body.message || '请求未成功，请稍后重试'))
    }
    return response
  },
  (error: unknown) => Promise.reject(error),
)
export default request
