import { createRouter, createWebHistory } from 'vue-router'

// 懒加载引入页面
const Login = () => import('../views/Login.vue')
const Home = () => import('../views/Home.vue')

// ★★★ 关键：必须添加这一行！★★★
const Admin = () => import('../views/Admin.vue')

const routes = [
    {
        path: '/',
        redirect: '/login'
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
    },
    // 新增的管理员路由
    {
        path: '/admin',
        name: 'Admin',
        component: Admin // 这里用到的 Admin，必须在上面定义过
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router