<template>
  <div class="all-courses-container">
    <header class="neu-header">
      <div class="header-inner">
        <div class="left-section">
          <h1 class="logo">Neuedu</h1>
          <nav class="nav-links">
            <a @click="$router.push('/home')">首页</a>
            <a href="#" class="active">课程学习</a>
            <a @click="$router.push('/personal-learning')">个性学习</a>
            <a @click="$router.push('/my-exams')">考试</a>
            <a href="#">素质活动</a>
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
          <div class="user-info">
            <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            <span class="username">{{ userInfo.realName || '同学' }}</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </div>
        </div>
      </div>
    </header>

    <main class="main-content">
      <div class="page-title">课堂学习</div>

      <el-card class="filter-panel">
        <div class="filter-row">
          <el-input
              v-model="keyword"
              placeholder="请输入课程名称/代码"
              :prefix-icon="Search"
              clearable
              style="width: 250px; margin-right: 20px;"
          />

          <el-select v-model="statusFilter" placeholder="课程状态" clearable style="width: 120px; margin-right: 20px;">
            <el-option label="进行中" value="进行中" />
            <el-option label="未开始" value="未开始" />
            <el-option label="已结束" value="已结束" />
          </el-select>

          <el-select v-model="semesterFilter" placeholder="学期" clearable style="width: 200px; margin-right: 20px;">
            <el-option label="2025-2026学年 第1学期" value="2025-1" />
            <el-option label="2024-2025学年 第2学期" value="2024-2" />
          </el-select>

          <el-button @click="resetFilters">重置</el-button>

          <el-button type="primary" style="margin-left: auto;">
            <el-icon><Plus /></el-icon> 加入课程
          </el-button>
        </div>
      </el-card>

      <el-skeleton :rows="5" animated v-if="loading" style="margin-top: 20px;" />

      <div class="course-list-grid" v-else>
        <div
            v-for="item in filteredCourses"
            :key="item.id"
            class="course-card"
            :class="getCourseCardClass(item)"
        >
          <span class="top-status-tag" :class="getStatusTagClass(item.status)">{{ item.status }}</span>

          <div class="card-content">
            <div v-if="item.status === '匹配失败'" class="error-content">
              <el-icon :size="40" color="#C0C4CC"><CloseBold /></el-icon>
              <p>匹配失败</p>
            </div>

            <template v-else>
              <h4 class="course-name">{{ item.name }}</h4>
              <div class="course-meta">
                <span>{{ item.semester }}</span>
                <span class="divider">|</span>
                <span>{{ item.teacher }}</span>
              </div>
              <p class="course-code">{{ item.code }}</p>
            </template>
          </div>

          <div class="card-footer">
            <span class="enter-text" v-if="item.isTop === 1">置顶</span>
            <span class="enter-text" v-else></span>
            <span class="start-btn" @click="goToCourse(item.id)">开始学习</span>
          </div>
        </div>
      </div>

      <el-empty v-if="filteredCourses.length === 0 && !loading" description="未找到符合条件的课程" />

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
  </div>
</template>

<script setup>
import { ref, onMounted, computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, MagicStick, Platform, Plus, Search, CloseBold, ChatDotRound, Check } from '@element-plus/icons-vue'

import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const userInfo = ref({ realName: '' })
const loading = ref(true)
const courseList = ref([])
const notificationList = ref([]) // ★★★ 修复：添加通知列表状态
const unreadCount = computed(() => notificationList.value.filter(n => !n.isRead).length); // ★★★ 修复：添加未读计数

// ★★★ 修复：通知详情相关状态 ★★★
const detailDialogVisible = ref(false)
const currentNotification = ref({})

// 筛选状态
const keyword = ref('')
const statusFilter = ref(null)
const semesterFilter = ref('2025-1')

onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch(e) {}
  }
  fetchCourses()
  fetchNotifications() // ★★★ 修复：加载页面时获取通知
})

const fetchCourses = async () => {
  loading.value = true
  try {
    const res = await request.get('/home/data')
    courseList.value = res.courses || []
  } catch (error) {
    console.error('加载课程失败', error)
  } finally {
    loading.value = false
  }
}

// ★★★ 修复：添加通知获取逻辑 ★★★
const fetchNotifications = async () => {
  try {
    const res = await request.get('/student/notifications')
    notificationList.value = (res || []).map(n => ({
      ...n,
      tempReply: '',
      submitting: false
    }))
  } catch(e) {
    console.error("获取通知失败", e)
  }
}

// ★★★ 修复：添加打开详情对话框逻辑 ★★★
const openDetailDialog = async (note) => {
  currentNotification.value = { ...note };
  currentNotification.value.tempReply = currentNotification.value.tempReply || '';

  detailDialogVisible.value = true;

  if (!note.isRead) {
    try {
      note.isRead = true;
      await request.post(`/student/notification/read/${note.id}`);
    } catch (e) {
      console.error("标记已读失败", e);
    }
  }
};

// ★★★ 修复：添加提交回执逻辑 ★★★
const submitReply = async (note) => {
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
    targetNote.userReply = note.tempReply;
    targetNote.isRead = true;
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

const resetFilters = () => {
  keyword.value = ''
  statusFilter.value = null
  semesterFilter.value = null
}

const filteredCourses = computed(() => {
  let list = courseList.value

  if (keyword.value) {
    const kw = keyword.value.toLowerCase()
    list = list.filter(item =>
        item.name.toLowerCase().includes(kw) ||
        item.code.toLowerCase().includes(kw)
    )
  }

  if (statusFilter.value) {
    list = list.filter(item => item.status === statusFilter.value)
  }

  if (semesterFilter.value) {
    list = list.filter(item => item.semester === semesterFilter.value)
  }

  if (list.length > 3) {
    list.splice(2, 0, { id: 'fail', name: '软件测试(II)', semester: '2025-2026学年 第1学期', teacher: '林欣茹', status: '匹配失败', color: 'grey', isTop: 0, code: '240422-015' });
  }

  return list
})

const goToCourse = (courseId) => {
  if (courseId !== 'fail') {
    router.push(`/course-study/${courseId}`)
  }
}

const getCourseCardClass = (item) => {
  if (item.status === '匹配失败') return 'bg-match-fail'
  return 'bg-' + (item.color || 'blue')
}

const getStatusTagClass = (status) => {
  if (status === '进行中') return 'status-green'
  if (status === '未开始') return 'status-purple'
  if (status === '已结束') return 'status-grey'
  if (status === '匹配失败') return 'status-grey'
  return 'status-blue'
}

</script>

<style scoped lang="scss">
/* 基础样式 (保留原样式) */
$content-width: 1200px;
$header-height: 60px;

.all-courses-container {
  min-height: 100vh;
  background-color: #F5F7FA;
  width: 100%;
}

.neu-header {
  background: #fff;
  height: $header-height;
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
      .logo { font-size: 24px; color: #0041AB; margin-right: 40px; font-weight: 900; letter-spacing: -0.5px; }
      .nav-links a {
        text-decoration: none; color: #606266; margin-right: 32px; font-size: 15px; font-weight: 500; cursor: pointer;
        &:hover, &.active { color: #245FE6; font-weight: 700; }
      }
    }
    .right-section {
      display: flex; align-items: center; gap: 20px;
      .ai-btn { background: linear-gradient(90deg, #5383FC 0%, #766DFF 100%); border: none; padding: 18px 22px; font-weight: 600; font-style: italic; border-radius: 20px; }
      .icon-wrap { cursor: pointer; display: flex; align-items: center; }
      .user-info { display: flex; align-items: center; gap: 10px; padding: 0 5px; border-radius: 4px; .username { font-size: 14px; color: #333; font-weight: 500; } }
    }
  }
}

/* ★★★ 修复：添加通知气泡样式 (从 Home.vue 复制) ★★★ */
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

    &.required { border-left: 3px solid #E6A23C; background-color: #fffaf0; }
    &.unread { background-color: #f0f5ff; .n-title { color: #409EFF; } }

    .n-title { font-size: 14px; font-weight: 500; color: #333; margin-bottom: 4px; display: flex; align-items: center; gap: 8px;}
    .n-desc { font-size: 12px; color: #666; line-height: 1.4; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;}
    .n-time { font-size: 11px; color: #999; text-align: right; }
  }
  .notify-empty { text-align: center; color: #999; padding: 20px 0; }

  /* 详情对话框样式 */
  .detail-content {
    .detail-meta { margin-top: 15px; font-size: 14px; color: #606266; p { margin: 5px 0; } }
    .reply-area-dialog { padding: 15px; border: 1px solid #c6e2ff; background: #e6f1fc; border-radius: 4px; }
    .replied-text-dialog { color: #67C23A; font-size: 15px; display: flex; align-items: center; gap: 5px; font-weight: 600; }
  }
}
/* ★★★ 修复结束 ★★★ */

.main-content {
  width: $content-width;
  margin: 20px auto;
}

.page-title {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 20px;
  border-left: 5px solid #409EFF;
  padding-left: 10px;
  line-height: 24px;
}

.filter-panel {
  padding: 15px;
  margin-bottom: 20px;
  .filter-row {
    display: flex;
    align-items: center;
  }
}

.course-list-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.course-card {
  background: #fff;
  border-radius: 8px;
  height: 200px;
  padding: 15px;
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

  .top-status-tag {
    position: absolute;
    top: 0;
    right: 0;
    background: #409EFF;
    color: #fff;
    padding: 2px 8px;
    border-radius: 0 8px 0 8px;
    font-size: 12px;
    font-weight: bold;
    &.status-green { background-color: #67C23A; }
    &.status-purple { background-color: #9370DB; }
    &.status-grey { background-color: #909399; }
    &.status-blue { background-color: #409EFF; }
  }

  .card-content {
    flex-grow: 1;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    padding-bottom: 10px;

    .error-content {
      text-align: center;
      color: #fff;
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      p { margin-top: 10px; font-weight: bold; }
    }

    .course-name { font-size: 18px; margin: 0 0 8px 0; font-weight: 700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;}
    .course-meta { font-size: 12px; opacity: 0.95; margin-bottom: 4px; .divider { margin: 0 5px; opacity: 0.6; } }
    .course-code { font-size: 12px; opacity: 0.75; font-family: monospace; margin: 0; }
  }

  .card-footer {
    display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(255,255,255,0.2); padding-top: 8px;
    .enter-text { font-size: 12px; opacity: 0.8; cursor: default; }
    .start-btn {
      font-size: 12px; background: #fff; color: #333; padding: 4px 12px; border-radius: 16px; font-weight: 500;
      cursor: pointer;
    }
  }

  &.bg-blue { background: linear-gradient(135deg, #6B8DD6 0%, #8E9EFC 100%); }
  &.bg-green { background: linear-gradient(135deg, #42e695 0%, #3bb2b8 100%); }
  &.bg-orange { background: linear-gradient(135deg, #FFC371 0%, #FF5F6D 100%); }
  &.bg-red { background: linear-gradient(135deg, #FF9A9E 0%, #FAD0C4 100%); }
  &.bg-purple { background: linear-gradient(135deg, #A18CD1 0%, #FBC2EB 100%); }
  &.bg-cyan { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
  &.bg-grey { background: linear-gradient(135deg, #868f96 0%, #596164 100%); }

  &.bg-match-fail {
    background: #fff;
    border: 1px dashed #C0C4CC;
    color: #909399;

    .top-status-tag {
      background-color: #909399;
    }

    .card-footer {
      border-top: 1px solid #EBEEF5;
      .start-btn {
        background: #DCDFE6;
        color: #909399;
        cursor: not-allowed;
      }
    }
    .course-meta, .course-code, .course-name { color: #909399; }
  }
}
</style>
