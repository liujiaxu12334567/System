import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入 Admin 组件 (这一行你之前漏了或者没用上)
const Login = () => import('../views/Login.vue')
const Home = () => import('../views/Home.vue')
const Admin = () => import('../views/Admin.vue') // <--- 【新增】

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
    {
        path: '/admin',
        name: 'Admin',
        component: Admin, // <--- 【必须改这里】之前你写的是 Home，现在改成 Admin
        meta: { title: '管理员首页' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router