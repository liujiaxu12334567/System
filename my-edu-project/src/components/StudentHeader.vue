<template>
  <header class="neu-header">
    <div class="header-inner">
      <div class="left-section">
        <h1 class="logo" @click="router.push('/home')">Neuedu</h1>
        <nav class="nav-links">
          <a :class="{ active: isActive('/home') }" @click="router.push('/home')">首页</a>
          <a :class="{ active: isActive('/all-courses') }" @click="router.push('/all-courses')">课程学习</a>
          <a :class="{ active: isActive('/personal-learning') }" @click="router.push('/personal-learning')">个性学习</a>
          <a :class="{ active: isActive('/my-exams') }" @click="router.push('/my-exams')">考试</a>
          <a :class="{ active: isActive('/student-quality') }" @click="router.push('/student-quality')">素质活动</a>
          <a :class="{ active: isActive('/course-schedule') }" @click="router.push('/course-schedule')">课程安排</a>
        </nav>
      </div>

      <div class="right-section">
        <el-button
          type="primary"
          round
          class="ai-btn"
          @click="router.push('/neu-ai')"
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
            <div class="icon-wrap" @click.stop>
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
                :class="{ unread: !note.isRead, required: note.isActionRequired && !note.userReply }"
                @click="openDetailDialog(note)"
              >
                <div class="n-title">
                  {{ note.title }}
                  <el-tag
                    v-if="note.isActionRequired"
                    size="small"
                    :type="note.userReply ? 'success' : 'warning'"
                    effect="dark"
                  >
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
              <el-dropdown-item @click="router.push('/course-schedule')">
                <el-icon><Calendar /></el-icon>
                查看课程表
              </el-dropdown-item>
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
            <el-icon><Check /></el-icon> 你已填报信息 <strong>{{ currentNotification.userReply }}</strong>
          </div>
          <div v-else class="reply-input-box-dialog">
            <el-input
              v-model="currentNotification.tempReply"
              type="textarea"
              :rows="4"
              placeholder="请在此填写所需信息或回复"
            />
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
  </header>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

import {
  ArrowDown,
  Bell,
  Calendar,
  Check,
  ChatDotRound,
  MagicStick,
  Setting,
  SwitchButton
} from '@element-plus/icons-vue'

const router = useRouter()
const userInfo = ref({ realName: '' })

const notificationList = ref([])
const unreadCount = computed(() => notificationList.value.filter(n => !n.isRead).length)
let notificationTimer = null

const detailDialogVisible = ref(false)
const currentNotification = ref({})

const settingsDialogVisible = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmNewPassword: ''
})

const validatePass = (rule, value, callback) => {
  if (value === '') callback(new Error('请再次输入新密码'))
  else if (value !== passwordForm.newPassword) callback(new Error('两次输入密码不一致'))
  else callback()
}

const passwordRules = reactive({
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码不能少于6位', trigger: 'blur' }
  ],
  confirmNewPassword: [{ required: true, validator: validatePass, trigger: 'blur' }]
})

const isActive = (path) => router.currentRoute.value.path === path

const fetchNotifications = async () => {
  try {
    const res = await request.get('/student/notifications')
    notificationList.value = (res || []).map(n => ({
      ...n,
      tempReply: '',
      submitting: false
    }))
  } catch (e) {
    // 静默失败，避免影响主 UI
  }
}

const openDetailDialog = async (note) => {
  currentNotification.value = { ...note }
  currentNotification.value.tempReply = currentNotification.value.tempReply || ''
  detailDialogVisible.value = true

  if (!note.isRead) {
    try {
      note.isRead = true
      await request.post(`/student/notification/read/${note.id}`)
    } catch (e) {}
  }
}

const submitReply = async (note) => {
  const targetNote = notificationList.value.find(n => n.id === note.id) || note
  if (!note.tempReply || targetNote.submitting) return ElMessage.warning('请填写回复内容')

  targetNote.submitting = true
  try {
    await request.post('/student/notification/reply', { id: targetNote.id, reply: note.tempReply })
    ElMessage.success('回执提交成功')
    targetNote.userReply = note.tempReply
    targetNote.isRead = true
    currentNotification.value.userReply = note.tempReply
  } catch (e) {
    ElMessage.error(e?.response?.data || '提交失败')
  } finally {
    targetNote.submitting = false
  }
}

const formatTime = (timeString) => (timeString ? dayjs(timeString).fromNow() : '')
const formatFullTime = (timeString) => (timeString ? dayjs(timeString).format('YYYY-MM-DD HH:mm:ss') : '')

const openPasswordDialog = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmNewPassword = ''
  if (passwordFormRef.value) passwordFormRef.value.resetFields()
  settingsDialogVisible.value = true
}

const submitPasswordChange = () => {
  if (!passwordFormRef.value) return
  passwordFormRef.value.validate(async (valid) => {
    if (!valid) return ElMessage.warning('请检查输入项')
    try {
      await request.post('/auth/update-password', {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      settingsDialogVisible.value = false
      logout()
    } catch (error) {
      ElMessage.error(error?.response?.data || '密码修改失败')
    }
  })
}

const logout = () => {
  localStorage.clear()
  router.push('/login')
}

onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try {
      userInfo.value = JSON.parse(storedUser)
    } catch (e) {}
  }
  fetchNotifications()
  if (!notificationTimer) notificationTimer = setInterval(fetchNotifications, 60 * 1000)
})

onBeforeUnmount(() => {
  if (notificationTimer) clearInterval(notificationTimer)
})
</script>

<style scoped lang="scss">
.neu-header {
  height: 60px;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.05);
}

.header-inner {
  width: 90%;
  margin: 0 auto;
  height: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left-section {
  display: flex;
  align-items: center;
}

.logo {
  font-size: 24px;
  color: #0041ab;
  margin-right: 40px;
  font-weight: 900;
  letter-spacing: -0.5px;
  cursor: pointer;
}

.nav-links a {
  text-decoration: none;
  color: #606266;
  margin-right: 32px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.nav-links a:hover,
.nav-links a.active {
  color: #245fe6;
  font-weight: 700;
}

.right-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.ai-btn {
  background: linear-gradient(90deg, #5383fc 0%, #766dff 100%);
  border: none;
  padding: 18px 22px;
  font-weight: 600;
  font-style: italic;
  border-radius: 20px;
}

.icon-wrap {
  cursor: pointer;
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 5px;
  border-radius: 4px;
}

.user-info:hover {
  background-color: #f0f2f5;
}

.username {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.notify-box .notify-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
  margin-bottom: 10px;
  font-weight: bold;
}

.notify-box .notify-list {
  max-height: 300px;
  overflow-y: auto;
}

.notify-item {
  padding: 10px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background-color 0.2s;
}
.notify-item:last-child { border-bottom: none; }
.notify-item:hover { background-color: #f7f9fd; }

.notify-item.required {
  border-left: 3px solid #e6a23c;
  background-color: #fffaf0;
}

.notify-item.unread {
  background-color: #f0f5ff;
}

.notify-item.unread .n-title { color: #409eff; }

.n-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.n-desc {
  font-size: 12px;
  color: #666;
  line-height: 1.4;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.n-time {
  font-size: 11px;
  color: #999;
  text-align: right;
}

.notify-empty {
  text-align: center;
  color: #999;
  padding: 20px 0;
}

.detail-content .detail-meta {
  margin-top: 15px;
  font-size: 14px;
  color: #606266;
}
.detail-content .detail-meta p { margin: 5px 0; }

.reply-area-dialog {
  padding: 15px;
  border: 1px solid #c6e2ff;
  background: #e6f1fc;
  border-radius: 4px;
}

.replied-text-dialog {
  color: #67c23a;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-weight: 600;
}
</style>
