import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入 Admin 组件 (这一行你之前漏了或者没用上)
const Login = () => import('../views/Login.vue')
const Home = () => import('../views/Home.vue')
const Admin = () => import('../views/Admin.vue') // <--- 【新增】
const Teacher = () => import('../views/Teacher.vue')
const Leader = () => import('../views/Leader.vue')
const CourseStudy = () => import('../views/CourseStudy.vue')
const QuizDetail = () => import('../views/QuizDetail.vue')
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
    },
    {
        path: '/teacher',
        name: 'Teacher',
        component: Teacher,
        meta: { title: '教师工作台' }
    },
    {
        path: '/leader',
        name: 'Leader',
        component: Leader,
        meta: { title: '课题组长工作台' }
    },
    {
        path: '/course-study/:id',
        name: 'CourseStudy',
        component: CourseStudy,
        meta: { title: '课程学习' }
    },
    {
        path: '/quiz/:courseId/:materialId',
        name: 'QuizDetail', // 这个名字必须和 CourseStudy.vue 里的 router.push({ name: 'QuizDetail' }) 一致
        component: QuizDetail,
        meta: { title: '在线测验' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router