<template>
  <div class="home-container">
    <header class="neu-header">
      <div class="header-inner">
        <div class="left-section">
          <h1 class="logo">Neuedu</h1>
          <nav class="nav-links">
            <a @click="$router.push('/home')" class="active">首页</a>
            <a @click="$router.push('/all-courses')">课程学习</a>
            <a @click="$router.push('/personal-learning')">个性学习</a>
            <a @click="$router.push('/my-exams')">考试</a>
            <a @click="$router.push('/student-quality')">素质活动</a>
            <a href="#">毕业设计</a>
          </nav>
        </div>
        <div class="right-section">

          <el-button
              type="primary"
              round
              class="ai-btn"
              @click="$router.push('/neu-ai')"
          >
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon> NEU AI
          </el-button>

          <el-popover
              placement="bottom"
              :width="300"
              trigger="click"
              popper-class="notification-popover"
          >
            <template #reference>
              <div class="icon-wrap">
                <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="badge-dot">
                  <el-icon :size="20" color="#606266"><Bell /></el-icon>
                </el-badge>
              </div>
            </template>

            <div class="notify-box">
              <div class="notify-header">
                <span>消息通知 ({{ notificationList.length }})</span>
                <el-button link type="primary" size="small" @click="fetchNotifications">刷新</el-button>
              </div>
              <div class="notify-list" v-if="notificationList.length > 0">
                <div
                    v-for="(note, index) in notificationList"
                    :key="index"
                    class="notify-item"
                    :class="{'unread': !note.isRead, 'required': note.isActionRequired && !note.userReply}"
                    @click="openDetailDialog(note)">
                  <div class="n-title">
                    {{ note.title }}
                    <el-tag v-if="note.isActionRequired" size="small" :type="note.userReply ? 'success' : 'warning'" effect="dark">
                      {{ note.userReply ? '已填报' : '需填报' }}
                    </el-tag>
                    <el-icon v-if="!note.isRead" color="#409EFF"><ChatDotRound /></el-icon>
                  </div>
                  <div class="n-desc">{{ note.message }}</div>
                  <div class="n-time">{{ formatTime(note.createTime) }}</div>
                </div>
              </div>
              <div v-else class="notify-empty">暂无新通知</div>
            </div>
          </el-popover>

          <el-dropdown trigger="click">
            <div class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="username">{{ userInfo.realName || '同学' }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openPasswordDialog">
                  <el-icon><Setting /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item divided @click="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="banner-section">
      <div class="banner-inner">
        <div class="greeting-box">
          <h2>Hello, {{ userInfo.realName || '同学' }}</h2>
          <p>欢迎登录东软智慧教育平台</p>
          <div class="semester-box">
            <el-select v-model="currentSemester" class="custom-select" :teleported="false">
              <el-option label="2025-2026学年 第1学期" value="2025-1" />
              <el-option label="2024-2025学年 第2学期" value="2024-2" />
            </el-select>
          </div>
        </div>
        <div class="banner-img">
          <el-icon :size="220" color="rgba(255,255,255,0.15)"><Platform /></el-icon>
        </div>
      </div>
    </div>

    <main class="main-content">
      <section class="content-block">
        <div class="block-header">
          <h3 class="title">我的课程 <span class="count">({{ courseList.length }})</span></h3>
          <el-link :underline="false" class="more-link">更多 <el-icon><ArrowRight /></el-icon></el-link>
        </div>

        <el-skeleton :rows="3" animated v-if="loading" />

        <div class="course-grid" v-else>
          <div class="course-card"
               v-for="item in courseList"
               :key="item.id"
               :class="'bg-' + (item.color || 'blue')">
            <div class="status-row">
              <span class="status-tag">{{ item.status }}</span>
            </div>

            <div class="card-info">
              <h4 class="course-name">{{ item.name }}</h4>
              <div class="course-meta">
                <span>{{ item.semester }}</span>
                <span class="divider">|</span>
                <span>{{ item.teacher }}</span>
              </div>
              <p class="course-code">{{ item.code }}</p>
            </div>

            <div class="card-action">
              <span class="enter-text" v-if="item.isTop === 1">置顶</span>
              <span class="enter-text" v-else></span>
              <span class="start-btn" @click="goToCourse(item.id)">开始学习</span>
              <el-button size="small" type="primary" plain @click="goToClassroom(item.id)" :loading="enteringClassroom === item.id">
                进入在线课堂
              </el-button>
            </div>
          </div>
        </div>
      </section>

      <div class="bottom-row">
        <section class="content-block half-block">
          <div class="block-header">
            <div class="header-left">
              <h3 class="title">我的待办 <span class="count">({{ pendingTasks.length }})</span></h3>
            </div>
          </div>

          <div class="task-list" v-if="pendingTasks.length > 0">
            <div class="task-item" v-for="(task, index) in pendingTasks" :key="index">
              <div class="item-left">
                <el-tag :type="task.type === '测验' ? 'warning' : 'success'" effect="dark" size="small" class="type-tag">
                  {{ task.type }}
                </el-tag>
                <div class="item-content">
                  <div class="task-title">
                    {{ task.title }}
                    <span class="course-badge">{{ task.courseName }}</span>
                  </div>
                  <div class="task-desc">截止时间：{{ task.deadline }}</div>
                </div>
              </div>
              <el-button
                  type="primary"
                  link
                  round
                  size="small"
                  :disabled="Boolean(task.expired)"
                  @click="$router.push({ name: 'QuizDetail', params: { courseId: task.courseId || 0, materialId: task.id }, query: { mode: 'take', type: task.type } })"
              >
                {{ task.expired ? '已过期' : '去完成' }}
              </el-button>
            </div>
          </div>
          <div class="empty-area small" v-else>
            <div class="custom-empty">
              <el-icon :size="50" color="#e0e0e0"><Document /></el-icon>
              <p>真棒！所有作业都已完成</p>
            </div>
          </div>
        </section>

        <section class="content-block half-block">
          <div class="block-header">
            <h3 class="title">我的考试</h3>
            <el-link :underline="false" class="more-link" @click="goToMyExams">更多 <el-icon><ArrowRight /></el-icon></el-link>
          </div>

          <div class="task-list" v-if="myExams.length > 0">
            <div class="task-item" v-for="(exam, index) in myExams" :key="exam.examId || index">
              <div class="item-left">
                <el-tag :type="exam.submitted ? 'success' : 'danger'" effect="dark" size="small" class="type-tag">
                  {{ exam.submitted ? '已答' : '未答' }}
                </el-tag>
                <div class="item-content">
                  <div class="task-title">
                    {{ exam.title }}
                    <span class="course-badge">{{ exam.courseName }}</span>
                  </div>
                  <div class="task-desc">截止时间：{{ exam.deadline || '无限制' }}</div>
                </div>
              </div>
              <el-button type="primary" link round size="small" @click="goToExam(exam.examId)">
                {{ exam.submitted ? '查看' : '去完成' }}
              </el-button>
            </div>
          </div>

          <div class="empty-area small" v-else>
            <div class="custom-empty">
              <el-icon :size="50" color="#e0e0e0"><Tickets /></el-icon>
              <p>暂无考试</p>
            </div>
          </div>
        </section>
      </div>

    </main>

    <el-dialog
        v-model="detailDialogVisible"
        :title="currentNotification.title"
        width="500px"
        destroy-on-close
    >
      <div class="detail-content">
        <el-alert type="info" :closable="false" style="margin-bottom: 15px;">
          <template #title>
            <strong>通知详情</strong>
          </template>
          <p>{{ currentNotification.message }}</p>
        </el-alert>

        <div class="detail-meta">
          <p><strong>发送者：</strong> {{ currentNotification.senderName || '系统/管理员' }}</p>
          <p><strong>发送时间：</strong> {{ currentNotification.createTime ? formatFullTime(currentNotification.createTime) : '未知' }}</p>
        </div>

        <el-divider v-if="currentNotification.isActionRequired">回执要求</el-divider>

        <div v-if="currentNotification.isActionRequired" class="reply-area-dialog">
          <div v-if="currentNotification.userReply" class="replied-text-dialog">
            <el-icon><Check /></el-icon> 您已填报信息：<strong>{{ currentNotification.userReply }}</strong>
          </div>
          <div v-else class="reply-input-box-dialog">
            <el-input
                v-model="currentNotification.tempReply"
                type="textarea"
                :rows="4"
                placeholder="请在此填写所需信息或回复..." />
            <el-button
                type="primary"
                style="margin-top: 10px;"
                @click="submitReply(currentNotification)"
                :loading="currentNotification.submitting"
            >
              提交回执
            </el-button>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settingsDialogVisible" title="账户设置 - 修改密码" width="400px">
      <el-form
          :model="passwordForm"
          ref="passwordFormRef"
          :rules="passwordRules"
          label-width="100px"
          size="default"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmNewPassword">
          <el-input v-model="passwordForm.confirmNewPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPasswordChange">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, MagicStick, Platform, ArrowRight, Box, Document, Tickets, ArrowDown, Setting, SwitchButton, Check, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const userInfo = ref({ realName: '' })
const currentSemester = ref('2025-1')
const loading = ref(true)
const courseList = ref([])
const notificationList = ref([])
const pendingTasks = ref([])
const myExams = ref([])
const enteringClassroom = ref(null) // 控制进入课堂的 loading

// 通知详情状态
const detailDialogVisible = ref(false)
const currentNotification = ref({})

// 弹窗和表单
const settingsDialogVisible = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmNewPassword: ''
})

const unreadCount = computed(() => notificationList.value.filter(n => !n.isRead).length);

const validatePass = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const passwordRules = reactive({
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码不能少于6位', trigger: 'blur' }
  ],
  confirmNewPassword: [{ required: true, validator: validatePass, trigger: 'blur' }]
})

onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (!localStorage.getItem('token')) {
    router.push('/login')
    return
  }
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch(e) {}
  }
  fetchHomeData()
})

const fetchHomeData = async () => {
  try {
    // 1. 获取基础首页数据
    const res = await request.get('/home/data')
    if (res) {
      if (res.realName) {
        userInfo.value.realName = res.realName
      }
      courseList.value = res.courses || []
    }

    // 2. 获取通知列表 (用于铃铛)
    await fetchNotifications()

    // 3. 获取待办任务 (用于底部列表)
    const tasksRes = await request.get('/student/pending-tasks')
    pendingTasks.value = tasksRes || []

    // 4. 获取我的考试 (含已答/未答)
    const examsRes = await request.get('/student/my-exams', { params: { limit: 3 } })
    myExams.value = examsRes || []

  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const goToMyExams = () => router.push('/my-exams')

const goToExam = (examId) => {
  if (!examId) return
  router.push({ name: 'ExamDetail', params: { examId } })
}

const fetchNotifications = async () => {
  try {
    const res = await request.get('/student/notifications')
    // 为每个通知添加临时状态和回复字段
    notificationList.value = (res || []).map(n => ({
      ...n,
      tempReply: '', // 用于 v-model 绑定用户的临时输入
      submitting: false // 用于控制提交按钮的 loading 状态
    }))
  } catch(e) {
    console.error("获取通知失败", e)
  }
}

const openDetailDialog = async (note) => {
  // Deep clone the note for binding to the dialog
  currentNotification.value = { ...note };
  // Ensure tempReply is initialized for the dialog
  currentNotification.value.tempReply = currentNotification.value.tempReply || '';

  detailDialogVisible.value = true;

  // 如果未读，调用后端接口标记为已读
  if (!note.isRead) {
    try {
      // 1. 更新本地状态 (立即响应)
      note.isRead = true;
      // 2. 调用后端接口持久化
      await request.post(`/student/notification/read/${note.id}`);
    } catch (e) {
      console.error("标记已读失败", e);
    }
  }
};

const submitReply = async (note) => {
  // Find the original note in the list to update its state directly
  const targetNote = notificationList.value.find(n => n.id === note.id) || note;

  if (!note.tempReply || targetNote.submitting) return ElMessage.warning('请填写回复内容');

  targetNote.submitting = true;

  try {
    const payload = {
      id: targetNote.id,
      reply: note.tempReply
    };

    await request.post('/student/notification/reply', payload);

    ElMessage.success('回执提交成功');
    // 本地更新状态
    targetNote.userReply = note.tempReply;
    targetNote.isRead = true; // 提交回执也视为已读

    // Update the dialog content too
    currentNotification.value.userReply = note.tempReply;

  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败');
  } finally {
    targetNote.submitting = false;
  }
}

const formatTime = (timeString) => {
  if (!timeString) return ''
  return dayjs(timeString).fromNow()
}

const formatFullTime = (timeString) => {
  if (!timeString) return ''
  return dayjs(timeString).format('YYYY-MM-DD HH:mm:ss')
}

const openPasswordDialog = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmNewPassword = ''

  if (passwordFormRef.value) {
    passwordFormRef.value.resetFields()
  }
  settingsDialogVisible.value = true
}

const submitPasswordChange = () => {
  passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await request.post('/auth/update-password', {
          oldPassword: passwordForm.oldPassword,
          newPassword: passwordForm.newPassword
        })
        ElMessage.success('密码修改成功，请重新登录')
        settingsDialogVisible.value = false
        logout()
      } catch (error) {
        console.error('密码修改失败', error)
      }
    } else {
      ElMessage.warning('请检查输入项')
      return false
    }
  })
}

const goToCourse = (courseId) => {
  router.push(`/course-study/${courseId}`)
}

// 【关键修改】进入课堂前的状态检查
const parseActive = (res) => Boolean(res?.active ?? res?.isActive)

// 课堂激活探测：先走学生接口，失败或未激活则兜底教师接口
const checkClassActive = async (courseId) => {
  try {
    const res = await request.get(`/student/checkin/status/${courseId}`)
    if (parseActive(res)) return true
  } catch (e) {
    console.warn('学生课堂状态接口失败，尝试教师接口', e)
  }
  try {
    const res = await request.get(`/teacher/checkin/status/${courseId}`)
    if (parseActive(res)) return true
  } catch (e) {
    console.warn('教师课堂状态接口也失败', e)
  }
  return false
}

const goToClassroom = async (courseId) => {
  enteringClassroom.value = courseId // 开启 loading
  try {
    const isActive = await checkClassActive(courseId)
    if (isActive) {
      router.push(`/student/classroom/${courseId}`)
    } else {
      ElMessage.info('老师尚未开启在线课堂，请稍候再试')
    }
  } catch (error) {
    ElMessage.error('无法获取课程状态，请检查网络')
  } finally {
    enteringClassroom.value = null // 关闭 loading
  }
}

const logout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped lang="scss">
$content-width: 90%;

.home-container {
  min-height: 100vh;
  background-color: #F5F7FA;
  width: 100%;
  display: flex;
  flex-direction: column;
}

/* Header */
.neu-header {
  height: 60px;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  box-shadow: 0 1px 0 rgba(0,0,0,0.05);

  .header-inner {
    width: $content-width;
    margin: 0 auto;
    height: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;

    .left-section {
      display: flex; align-items: center;
      .logo {
        font-size: 24px; color: #0041AB; margin-right: 40px;
        font-weight: 900; letter-spacing: -0.5px;
      }
      .nav-links a {
        text-decoration: none; color: #606266; margin-right: 32px; font-size: 15px; font-weight: 500;
        &:hover, &.active { color: #245FE6; font-weight: 700; }
      }
    }
    .right-section {
      display: flex; align-items: center; gap: 20px;
      .ai-btn {
        background: linear-gradient(90deg, #5383FC 0%, #766DFF 100%);
        border: none; padding: 18px 22px; font-weight: 600; font-style: italic; border-radius: 20px;
      }
      .icon-wrap { cursor: pointer; display: flex; align-items: center; }

      :deep(.el-dropdown) {
        cursor: pointer;
      }
      .user-info {
        display: flex; align-items: center; gap: 10px;
        padding: 0 5px;
        border-radius: 4px;
        &:hover { background-color: #f0f2f5; }
        .username { font-size: 14px; color: #333; font-weight: 500;}
      }
    }
  }
}

/* Banner */
.banner-section {
  width: 100%;
  height: 360px;
  background: linear-gradient(135deg, #2E5CF6 0%, #1593F8 100%);
  color: #fff;
  padding-top: 50px;

  .banner-inner {
    width: $content-width;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    position: relative;

    .greeting-box {
      h2 { font-size: 40px; margin: 0 0 10px 0; font-weight: 500; letter-spacing: 0.5px; }
      p { font-size: 16px; opacity: 0.8; margin-bottom: 30px; }

      :deep(.custom-select) {
        width: 280px;
        .el-input__wrapper {
          background-color: rgba(255,255,255,0.15);
          box-shadow: none;
          border: 1px solid rgba(255,255,255,0.3);
          border-radius: 4px;
          .el-input__inner { color: #fff; }
        }
      }
    }
    .banner-img { margin-right: 40px; margin-top: 10px; opacity: 0.9; }
  }
}

/* Main Content */
.main-content {
  width: $content-width;
  margin: -130px auto 40px;
  position: relative;
  z-index: 10;
  padding-bottom: 20px;
}

/* Content Block */
.content-block {
  margin-bottom: 24px;

  .block-header {
    display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;
    padding-left: 2px;
    .title {
      font-size: 16px; font-weight: 700; color: #303133; margin: 0;
      border-left: 4px solid #FFA500; padding-left: 10px; line-height: 16px;
      .count { color: #909399; font-weight: 400; font-size: 14px; margin-left: 5px; }
    }
    .more-link { font-size: 12px; color: #909399; &:hover { color: #245FE6; } }

    .header-left {
      display: flex; align-items: center; gap: 30px;
      .tabs {
        display: flex; gap: 20px; font-size: 14px;
        .tab { color: #909399; cursor: pointer; &.active { color: #245FE6; font-weight: bold; } }
      }
    }
  }
}

/* 课程卡片 */
.course-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.course-card {
  background: #909399;
  border-radius: 8px;
  height: 160px;
  padding: 18px 22px;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transition: transform 0.2s;

  &:hover { transform: translateY(-3px); }

  &.bg-pink { background: linear-gradient(135deg, #FF758C 0%, #FF7EB3 100%); }
  &.bg-blue { background: linear-gradient(135deg, #6B8DD6 0%, #8E9EFC 100%); }
  &.bg-purple { background: linear-gradient(135deg, #A18CD1 0%, #FBC2EB 100%); }
  &.bg-red { background: linear-gradient(135deg, #FF9A9E 0%, #FAD0C4 100%); }
  &.bg-orange { background: linear-gradient(135deg, #FFC371 0%, #FF5F6D 100%); }
  &.bg-green { background: linear-gradient(135deg, #42e695 0%, #3bb2b8 100%); }
  &.bg-cyan { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
  &.bg-grey { background: linear-gradient(135deg, #868f96 0%, #596164 100%); }
  &.bg-teal { background: linear-gradient(135deg, #1D976C 0%, #93F9B9 100%); }
  &.bg-indigo { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
  &.bg-black { background: linear-gradient(135deg, #434343 0%, #000000 100%); }

  .status-row { text-align: right; margin-bottom: 0; }
  .status-tag {
    background: rgba(255,255,255,0.25); font-size: 12px; padding: 2px 8px; border-radius: 4px;
  }

  .card-info {
    .course-name { font-size: 18px; margin: 0 0 8px 0; font-weight: 700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;}
    .course-meta { font-size: 12px; opacity: 0.95; margin-bottom: 4px; .divider { margin: 0 5px; opacity: 0.6; } }
    .course-code { font-size: 12px; opacity: 0.75; font-family: monospace; margin: 0; }
  }

  .card-action {
    display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(255,255,255,0.2); padding-top: 8px;
    .enter-text { font-size: 12px; opacity: 0.8; cursor: pointer; }
    .start-btn {
      font-size: 12px; background: #fff; color: #333; padding: 2px 10px; border-radius: 12px; font-weight: 500;
    }
    .el-button { margin-left: 8px; }
  }
}

.empty-area {
  background: #fff; border-radius: 8px; height: 180px;
  display: flex; justify-content: center; align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
  .custom-empty { text-align: center; color: #ccc; p { font-size: 13px; margin-top: 10px; } }
  &.small { height: 300px; }
}

.bottom-row { display: flex; gap: 20px; .half-block { flex: 1; } }

/* 任务列表 */
.task-list {
  background: #fff; border-radius: 8px; padding: 0 20px; min-height: 300px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
  .task-item {
    display: flex; justify-content: space-between; align-items: center; padding: 16px 0; border-bottom: 1px solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .item-left {
      display: flex; gap: 12px; align-items: flex-start;
      .type-tag { margin-top: 2px; }
      .item-content {
        .task-title { font-size: 14px; color: #333; margin-bottom: 4px; display:flex; align-items:center; gap:5px; font-weight: 500;}
        .task-desc { font-size: 12px; color: #999; .sep { margin-left: 10px; } }
      }
    }
    .action-btn { font-size: 12px; padding: 6px 15px; }
  }
}

/* 通知气泡 */
.notify-box {
  .notify-header {
    display: flex; justify-content: space-between; align-items: center;
    padding-bottom: 10px; border-bottom: 1px solid #eee; margin-bottom: 10px; font-weight: bold;
  }
  .notify-list { max-height: 300px; overflow-y: auto; }
  .notify-item {
    padding: 10px;
    border-bottom: 1px solid #f5f5f5;
    cursor: pointer;
    transition: background-color 0.2s;
    &:last-child { border-bottom: none; }
    &:hover { background-color: #f7f9fd; }

    // 需填报消息高亮
    &.required {
      border-left: 3px solid #E6A23C;
      background-color: #fffaf0;
    }
    // 未读消息
    &.unread {
      background-color: #f0f5ff;
      .n-title { color: #409EFF; }
    }

    .n-title {
      font-size: 14px;
      font-weight: 500;
      color: #333;
      margin-bottom: 4px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .n-desc { font-size: 12px; color: #666; line-height: 1.4; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;}
    .n-time { font-size: 11px; color: #999; text-align: right; }
  }
  .notify-empty { text-align: center; color: #999; padding: 20px 0; }

  /* 详情对话框样式 */
  .detail-content {
    .detail-meta {
      margin-top: 15px;
      font-size: 14px;
      color: #606266;
      p { margin: 5px 0; }
    }
    .reply-area-dialog {
      padding: 15px;
      border: 1px solid #c6e2ff;
      background: #e6f1fc;
      border-radius: 4px;
    }
    .replied-text-dialog {
      color: #67C23A;
      font-size: 15px;
      display: flex;
      align-items: center;
      gap: 5px;
      font-weight: 600;
    }
  }
}

/* 课程徽章 */
.course-badge {
  background: #f0f2f5; color: #909399; font-size: 11px; padding: 1px 5px; border-radius: 4px; margin-left: 8px; font-weight: normal;
}
</style>
