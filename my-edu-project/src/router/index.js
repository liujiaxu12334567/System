import { createRouter, createWebHistory } from 'vue-router'
// 懒加载引入页面
const Login = () => import('../views/Login.vue')
const Home = () => import('../views/Home.vue')

const routes = [
    {
        path: '/',
        redirect: '/login' // 默认跳到登录页
    },
    {
        path: '/login',
        name: 'Login',
        component: Login
    },
    {
        path: '/home',
        name: 'Home',
        component: Home
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router