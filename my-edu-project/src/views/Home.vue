<template>
  <div class="home-container">
    <header class="neu-header">
      <div class="header-inner">
        <div class="left-section">
          <h1 class="logo">Neuedu</h1>
          <nav class="nav-links">
            <a href="#" class="active">首页</a>
            <a href="#">课程学习</a>
            <a href="#">个性学习</a>
            <a href="#">考试</a>
            <a href="#">素质活动</a>
            <a href="#">毕业设计</a>
          </nav>
        </div>
        <div class="right-section">
          <el-button type="primary" round class="ai-btn">
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon> NEU AI
          </el-button>
          <div class="icon-wrap">
            <el-badge is-dot class="badge-dot">
              <el-icon :size="20" color="#606266"><Bell /></el-icon>
            </el-badge>
          </div>

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
            </div>
          </div>
        </div>
      </section>

      <section class="content-block">
        <div class="block-header">
          <h3 class="title">我的项目 <span class="count">(0)</span></h3>
          <el-link :underline="false" class="more-link">更多 <el-icon><ArrowRight /></el-icon></el-link>
        </div>
        <div class="empty-area">
          <div class="custom-empty">
            <el-icon :size="60" color="#e0e0e0"><Box /></el-icon>
            <p>暂无项目</p>
          </div>
        </div>
      </section>

      <div class="bottom-row">
        <section class="content-block half-block">
          <div class="block-header">
            <div class="header-left">
              <h3 class="title">我的学习任务</h3>
              <div class="tabs">
                <span class="tab active">未作答</span>
                <span class="tab">作答中</span>
              </div>
            </div>
          </div>

          <div class="task-list" v-if="taskList.length > 0">
            <div class="task-item" v-for="task in taskList" :key="task.id">
              <div class="item-left">
                <el-tag type="warning" effect="plain" size="small" class="type-tag">测验</el-tag>
                <div class="item-content">
                  <div class="task-title">{{ task.name }}</div>
                  <div class="task-desc">截止时间：{{ task.deadline }} <span class="sep">| {{ task.courseName }}</span></div>
                </div>
              </div>
              <el-button type="primary" plain round size="small" class="action-btn">
                {{ task.status === '未作答' ? '去作答' : task.status }}
              </el-button>
            </div>
          </div>
          <div class="empty-area small" v-else>
            <div class="custom-empty">
              <el-icon :size="50" color="#e0e0e0"><Document /></el-icon>
              <p>暂无任务</p>
            </div>
          </div>
        </section>

        <section class="content-block half-block">
          <div class="block-header">
            <h3 class="title">我的考试</h3>
            <el-link :underline="false" class="more-link">更多 <el-icon><ArrowRight /></el-icon></el-link>
          </div>
          <div class="empty-area small">
            <div class="custom-empty">
              <el-icon :size="50" color="#e0e0e0"><Tickets /></el-icon>
              <p>暂无考试</p>
            </div>
          </div>
        </section>
      </div>

    </main>

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
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, MagicStick, Platform, ArrowRight, Box, Document, Tickets, ArrowDown, Setting, SwitchButton } from '@element-plus/icons-vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import request from '@/utils/request' // 使用自定义 request 实例

const router = useRouter()
const userInfo = ref({ realName: '' })
const currentSemester = ref('2025-1')
const loading = ref(true)
const courseList = ref([])
const taskList = ref([])

// 弹窗和表单
const settingsDialogVisible = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmNewPassword: ''
})

// 自定义校验规则：检查两次输入的新密码是否一致
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
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch(e) {}
  }
  fetchHomeData()
})

const fetchHomeData = async () => {
  try {
    const res = await request.get('/home/data')

    if (res) {
      if (res.realName) {
        userInfo.value.realName = res.realName
      }
      courseList.value = res.courses || []
      taskList.value = res.tasks || []
    }
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 1. 点击头像菜单中的“修改密码”
const openPasswordDialog = () => {
  // 重置表单状态
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmNewPassword = ''

  // 清除校验结果
  if (passwordFormRef.value) {
    passwordFormRef.value.resetFields()
  }
  settingsDialogVisible.value = true
}

// 2. 提交密码修改请求
const submitPasswordChange = () => {
  // 触发表单校验
  passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        // 调用后端的修改密码接口
        await request.post('/auth/update-password', {
          oldPassword: passwordForm.oldPassword,
          newPassword: passwordForm.newPassword
        })

        ElMessage.success('密码修改成功，请重新登录')
        settingsDialogVisible.value = false
        logout() // 修改密码后强制用户重新登录

      } catch (error) {
        console.error('密码修改失败', error)
      }
    } else {
      ElMessage.warning('请检查输入项')
      return false
    }
  })
}

// 【新增功能】跳转到课程学习详情页
const goToCourse = (courseId) => {
  router.push(`/course-study/${courseId}`)
}

// 退出登录
const logout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped lang="scss">
/* 90% 宽度，配合你之前的全局样式 style.css */
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
  background: #fff;
  height: 60px;
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

      // Dropdown and User Info Styles
      :deep(.el-dropdown) {
        cursor: pointer;
      }
      .user-info {
        display: flex; align-items: center; gap: 10px;
        padding: 0 5px;
        border-radius: 4px;
        &:hover {
          background-color: #f0f2f5;
        }
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

/* 通用块 */
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
  background: #fff;
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

  /* 颜色 */
  &.bg-pink { background: linear-gradient(135deg, #FF758C 0%, #FF7EB3 100%); }
  &.bg-blue { background: linear-gradient(135deg, #6B8DD6 0%, #8E9EFC 100%); }
  &.bg-purple { background: linear-gradient(135deg, #A18CD1 0%, #FBC2EB 100%); }
  &.bg-red { background: linear-gradient(135deg, #FF9A9E 0%, #FAD0C4 100%); }

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
</style>