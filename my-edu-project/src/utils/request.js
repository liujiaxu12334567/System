import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const service = axios.create({
    baseURL: '/api',
    timeout: 5000
})

// 请求拦截器
service.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器
service.interceptors.response.use(
    response => {
        const res = response.data

        // 【🔥 核心修复点 🔥】
        // 原逻辑：if (res.code && res.code !== 200)
        // 修改后：增加类型判断 typeof res.code === 'number'
        // 原因：防止业务数据中包含 'code' 字段（如课程代码、商品编码）且值为字符串时，被误判为接口错误。
        if (res.code && typeof res.code === 'number' && res.code !== 200) {
            ElMessage.error(res.msg || '系统错误')
            return Promise.reject(new Error(res.msg || 'Error'))
        } else {
            return res
        }
    },
    error => {
        console.log('err' + error) // for debug
        let message = error.message || '网络连接失败'

        if (error.response) {
            // 尝试读取后端返回的具体错误信息
            if (typeof error.response.data === 'string') {
                message = error.response.data
            } else if (error.response.data && error.response.data.message) {
                message = error.response.data.message
            }
        }

        ElMessage.error(message)
        return Promise.reject(error)
    }
)

export default service