import { createRouter, createWebHistory } from 'vue-router'

const Login = () => import('../views/LoginStyled.vue')
const Home = () => import('../views/Home.vue')
const Admin = () => import('../views/Admin.vue')
const Teacher = () => import('../views/Teacher.vue')
const Leader = () => import('../views/Leader.vue')
const CourseStudy = () => import('../views/CourseStudy.vue')
const QuizDetail = () => import('../views/QuizDetail.vue')
const AllCourses = () => import('../views/AllCourses.vue') // 【新增引入】
const ExamDetail = () => import('../views/ExamDetail.vue')
const MyExams = () => import('../views/MyExams.vue')
const PersonalLearning = () => import('../views/PersonalLearning.vue')
const CourseSchedule = () => import('../views/CourseSchedule.vue')
const QualityTeacher = () => import('../views/QualityTeacher.vue')
const StudentQuality = () => import('../views/StudentQuality.vue')
const TeacherClassroom = () => import('../views/TeacherClassroom.vue')
const StudentClassroom = () => import('../views/StudentClassroom.vue')
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
    // 【新增路由】
    {
        path: '/all-courses',
        name: 'AllCourses',
        component: AllCourses,
        meta: { title: '我的课程学习' }
    },
    {
        path: '/admin',
        name: 'Admin',
        component: Admin,
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
        name: 'QuizDetail',
        component: QuizDetail,
        meta: { title: '在线测验' }
    },{
        path: '/exam/:examId', // 只需要 examId
        name: 'ExamDetail',
        component: ExamDetail,
        meta: { title: '在线考试' }
    },
    {
        path: '/my-exams',
        name: 'MyExams',
        component: MyExams,
        meta: { title: '我的考试' }
    },
    {
        path: '/personal-learning',
        name: 'PersonalLearning',
        component: PersonalLearning,
        meta: { title: '个性学习' }
    },
    {
        path: '/course-schedule',
        name: 'CourseSchedule',
        component: CourseSchedule,
        meta: { title: '课程表' }
    },
    {
        path: '/teacher/classroom/:courseId',
        name: 'TeacherClassroom',
        component: TeacherClassroom,
        meta: { title: '在线课堂（教师）' }
    },
    {
        path: '/student/classroom/:courseId',
        name: 'StudentClassroom',
        component: StudentClassroom,
        meta: { title: '在线课堂（学生）' }
    },
    // 在 routes 数组中添加以下对象
    {
        path: '/neu-ai',
        name: 'NeuAi',
        component: () => import('../views/NeuAi.vue'),
        meta: { title: 'NEU AI 智能助手' }
    },{
        path: '/quality-teacher',
        name: 'QualityTeacher',
        component: QualityTeacher,
        meta: { title: '素质教师工作台' }
    },
    {
        path: '/student-quality',
        name: 'StudentQuality',
        component: StudentQuality,
        meta: { title: '素质活动与请假' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router
