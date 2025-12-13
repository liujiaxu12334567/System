<template>
  <div class="classroom">
    <header class="cls-header">
      <div class="header-left">
        <el-button link class="back-btn" @click="goBack">
          <el-icon :size="20"><ArrowLeft /></el-icon>
        </el-button>
        <div class="course-info">
          <h2>{{ courseTitle }}</h2>
          <div class="status-badge">
            <span class="dot" :class="wsStatus ? 'online' : 'offline'"></span>
            {{ wsStatus ? '实时连线中' : '连接中断' }} · 模式：{{ currentQuestion?.mode || 'broadcast' }}
          </div>
        </div>
      </div>
    </header>

    <main class="cls-body">
      <div class="left-column">
        <section class="panel question-panel">
          <div class="panel-header">
            <span class="title">在线问题</span>
            <el-tag size="small" effect="plain" round>{{ questions.length }}</el-tag>
          </div>
          <div class="panel-content">
            <el-empty v-if="questions.length === 0" description="暂无提问" :image-size="60" />
            <el-scrollbar v-else>
              <div
                  v-for="q in questions"
                  :key="q.id"
                  class="q-item"
                  :class="{'active': currentQuestion && currentQuestion.id === q.id}"
                  @click="selectQuestion(q)"
              >
                <div class="q-main">
                  <div class="q-title">{{ q.title }}</div>
                  <div class="q-desc" v-if="q.description">{{ q.description }}</div>
                </div>
                <div class="q-side">
                  <el-tag size="small" :type="getModeTag(q.mode)">{{ q.mode }}</el-tag>
                  <span class="q-time">{{ formatTimeShort(q.createTime) }}</span>
                </div>
              </div>
            </el-scrollbar>
          </div>
        </section>

        <section class="panel interaction-panel">
          <div class="panel-header">
            <span class="title">互动操作</span>
          </div>
          <div class="action-area">
            <div class="btn-group">
              <el-button type="primary" class="action-btn" @click="handRaise" :disabled="!currentQuestion">
                <el-icon><Pointer /></el-icon> 举手
              </el-button>
              <el-button type="warning" class="action-btn" @click="raceAnswer" :disabled="!currentQuestion">
                <el-icon><Timer /></el-icon> 抢答
              </el-button>
              <el-button type="success" class="action-btn" @click="openAnswerDialog" :disabled="!currentQuestion">
                <el-icon><EditPen /></el-icon> 回答
              </el-button>
            </div>
          </div>
          <div class="feed-list">
            <div class="sub-title">实时动态</div>
            <el-scrollbar>
              <div v-if="answers.length === 0" class="empty-feed">暂无动态</div>
              <div v-for="a in answers" :key="a.id" class="feed-item">
                <div class="feed-icon">
                  <el-icon v-if="a.type==='hand'" color="#409EFF"><Pointer /></el-icon>
                  <el-icon v-else-if="a.type==='race'" color="#E6A23C"><Timer /></el-icon>
                  <el-icon v-else color="#67C23A"><ChatDotRound /></el-icon>
                </div>
                <div class="feed-content">
                  <span class="feed-user">学生 {{ a.studentId }}</span>
                  <span class="feed-text">{{ a.text }}</span>
                </div>
                <span class="feed-time">{{ formatTime(a.createTime) }}</span>
              </div>
            </el-scrollbar>
          </div>
        </section>
      </div>

      <section class="panel chat-panel">
        <div class="panel-header">
          <span class="title">课堂群聊</span>
          <el-tag type="info" round size="small">{{ chats.length }}</el-tag>
        </div>

        <div class="chat-container">
          <div class="chat-messages" ref="chatScrollRef">
            <div v-if="chats.length === 0" class="chat-empty">
              <p>暂无消息，打个招呼吧~</p>
            </div>

            <div
                v-for="c in chats"
                :key="c.id"
                class="message-row"
                :class="{ 'me': isMe(c.senderId) }"
            >
              <div class="avatar-wrapper">
                <div class="avatar" :style="{backgroundColor: avatarColor(c.role)}">
                  {{ avatarInitial(c.senderName || c.senderId) }}
                </div>
              </div>

              <div class="content-wrapper">
                <div class="meta-info">
                  <el-tag v-if="c.role === 'teacher'" size="small" type="danger" effect="dark" class="role-badge">教师</el-tag>
                  <span class="sender-name">{{ c.senderName || '用户' + c.senderId }}</span>
                  <span class="msg-time">{{ formatTime(c.createTime) }}</span>
                </div>
                <div class="bubble">
                  {{ c.content }}
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input-area">
            <el-input
                v-model="chatInput"
                type="textarea"
                :rows="3"
                placeholder="输入消息，按 Enter 发送"
                @keyup.enter.prevent="sendChat"
                resize="none"
            />
            <div class="input-toolbar">
              <el-tooltip content="发送 (Enter)" placement="top">
                <el-button type="primary" circle @click="sendChat">
                  <el-icon><Promotion /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </section>
    </main>

    <el-dialog v-model="answerDialogVisible" title="提交回答" width="400px" align-center>
      <el-input v-model="answerText" type="textarea" :rows="4" placeholder="请输入您的回答..." />
      <template #footer>
        <el-button @click="answerDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="submitAnswer">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Pointer, Timer, EditPen, Promotion, ChatDotRound } from '@element-plus/icons-vue'
import request from '@/utils/request'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const courseId = Number(route.params.courseId)
const courseTitle = ref(`课程 ${courseId}`)
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

const questions = ref([])
const currentQuestion = ref(null)
const answers = ref([])
const answerDialogVisible = ref(false)
const answerText = ref('')
const chats = ref([])
const chatInput = ref('')
const chatScrollRef = ref(null)
const wsStatus = ref(false)

let timer = null
let ws = null
const parseActive = (res) => Boolean(res?.active ?? res?.isActive)

const checkClassActive = async () => {
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

const ensureClassStarted = async () => {
  try {
    const active = await checkClassActive()
    if (!active) {
      ElMessage.info('老师尚未开启课堂，请稍后从课程入口进入')
      router.push('/home')
      return false
    }
    return true
  } catch (e) {
    ElMessage.error('无法确认课堂状态，请稍后重试')
    router.push('/home')
    return false
  }
}

onMounted(async () => {
  if (!localStorage.getItem('token')) {
    router.push('/login')
    return
  }
  const canEnter = await ensureClassStarted()
  if (!canEnter) return
  await fetchQuestions()
  startPolling()
  connectWs()
  await loadChat()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (ws) ws.close()
})

// === Helper Functions ===
const formatTime = (t) => t ? dayjs(t).format('HH:mm') : ''
const formatTimeShort = (t) => t ? dayjs(t).format('MM-DD HH:mm') : ''
// 判断消息是否由当前用户发送
const isMe = (senderId) => senderId == userInfo.id
const getModeTag = (mode) => {
  const map = { broadcast: 'info', hand: 'warning', race: 'danger', assign: 'success' }
  return map[mode] || 'info'
}

const avatarInitial = (name) => {
  if (!name) return 'U'
  return name.toString().trim().substring(0, 1).toUpperCase()
}
const avatarColor = (role) => role === 'teacher' ? '#F56C6C' : '#409EFF'

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
  })
}

// === Data Fetching ===
const fetchQuestions = async () => {
  try {
    questions.value = await request.get('/student/online-questions', { params: { courseId } }) || []
    if (questions.value.length && !currentQuestion.value) {
      await selectQuestion(questions.value[0])
    }
  } catch (e) { console.error(e) }
}

const selectQuestion = async (q) => {
  currentQuestion.value = q
  await loadAnswers()
}

const loadAnswers = async () => {
  if (!currentQuestion.value) return
  answers.value = await request.get(`/student/classroom/question/${currentQuestion.value.id}/answers`) || []
}

const loadChat = async () => {
  const res = await request.get(`/student/classroom/${courseId}/chat`, { params: { limit: 200 } }) || []
  chats.value = res
  scrollToBottom()
}

const sendChat = async () => {
  if (!chatInput.value.trim()) return
  try {
    await request.post(`/student/classroom/${courseId}/chat`, { content: chatInput.value.trim() })
    chatInput.value = ''
    await loadChat()
  } catch (e) { ElMessage.error('发送失败') }
}

// === Interaction Actions ===
const handRaise = async () => {
  if (!currentQuestion.value) return
  await request.post(`/student/classroom/question/${currentQuestion.value.id}/hand`)
  ElMessage.success('已举手')
  await loadAnswers()
}

const raceAnswer = async () => {
  if (!currentQuestion.value) return
  await request.post(`/student/classroom/question/${currentQuestion.value.id}/race`)
  ElMessage.success('抢答已提交')
  await loadAnswers()
}

const openAnswerDialog = () => {
  if (!currentQuestion.value) return
  answerDialogVisible.value = true
}

const submitAnswer = async () => {
  if (!currentQuestion.value) return
  if (!answerText.value.trim()) return ElMessage.warning('请输入内容')
  try {
    await request.post(`/student/online-question/${currentQuestion.value.id}/answer`, { answerText: answerText.value })
    ElMessage.success('回答已提交')
    answerDialogVisible.value = false
    answerText.value = ''
    await loadAnswers()
  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败')
  }
}

// === WebSocket & Polling ===
const startPolling = () => {
  timer = setInterval(() => {
    if (currentQuestion.value) loadAnswers()
    else fetchQuestions()
  }, 5000)
}

const connectWs = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${window.location.host}/ws/classroom?courseId=${courseId}`
  ws = new WebSocket(url)

  ws.onopen = () => { wsStatus.value = true }

  ws.onmessage = (evt) => {
    try {
      const msg = JSON.parse(evt.data)
      if (msg.courseId !== courseId) return
      switch (msg.type) {
        case 'question': fetchQuestions(); break;
        case 'hand': case 'race': case 'answer': case 'call': loadAnswers(); loadChat(); break;
        case 'chat': loadChat(); break;
      }
    } catch (e) {}
  }

  ws.onerror = () => { wsStatus.value = false }

  ws.onclose = () => {
    wsStatus.value = false
    setTimeout(connectWs, 5000)
  }
}

const goBack = () => router.push('/home')
</script>

<style scoped lang="scss">
$primary: #409EFF;
$bg-color: #f0f2f5;
$panel-bg: #ffffff;
$border-color: #e4e7ed;

.classroom {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
  font-family: 'PingFang SC', sans-serif;
}

/* Header */
.cls-header {
  height: 60px;
  background: $panel-bg;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  z-index: 10;

  .header-left {
    display: flex; align-items: center; gap: 16px;
    .back-btn {
      color: #606266; font-size: 18px;
      &:hover { color: $primary; }
    }
    .course-info {
      h2 { margin: 0; font-size: 18px; color: #303133; font-weight: 600; }
      .status-badge {
        font-size: 12px; color: #909399; display: flex; align-items: center; gap: 6px;
        .dot { width: 6px; height: 6px; border-radius: 50%; background: #F56C6C; &.online { background: #67C23A; } }
      }
    }
  }
}

/* Body Layout */
.cls-body {
  flex: 1;
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
  padding: 20px;
  overflow: hidden;
}

.left-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.panel {
  background: $panel-bg;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  border: 1px solid $border-color;

  .panel-header {
    height: 50px;
    padding: 0 16px;
    border-bottom: 1px solid $border-color;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #fafafa;
    .title { font-weight: 600; font-size: 15px; color: #303133; }
  }
}

/* Question Panel */
.question-panel {
  height: 45%;
  .panel-content { flex: 1; overflow: hidden; padding: 0; }
  .q-item {
    padding: 12px 16px; border-bottom: 1px solid #f5f7fa; cursor: pointer; display: flex; justify-content: space-between; transition: all 0.2s;
    &:hover { background: #f9fafc; }
    &.active { background: #ecf5ff; border-left: 3px solid $primary; }
    .q-main {
      flex: 1; min-width: 0; margin-right: 10px;
      .q-title { font-size: 14px; font-weight: 500; color: #303133; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .q-desc { font-size: 12px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    }
    .q-side { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; .q-time { font-size: 11px; color: #C0C4CC; } }
  }
}

/* Interaction Panel */
.interaction-panel {
  flex: 1;
  .action-area { padding: 16px; border-bottom: 1px dashed $border-color; }
  .btn-group {
    display: flex; gap: 10px;
    .action-btn { flex: 1; }
  }
  .feed-list {
    flex: 1; overflow: hidden; padding: 12px 16px; display: flex; flex-direction: column;
    .sub-title { font-size: 12px; color: #909399; margin-bottom: 8px; font-weight: 600; }
    .empty-feed { text-align: center; color: #ccc; margin-top: 20px; font-size: 13px; }
    .feed-item {
      display: flex; align-items: center; padding: 8px 0; border-bottom: 1px solid #f8f8f8;
      .feed-icon { margin-right: 10px; display:flex; align-items:center; }
      .feed-content { flex: 1; display: flex; flex-direction: column;
        .feed-user { font-size: 12px; color: #666; }
        .feed-text { font-size: 13px; color: #333; }
      }
      .feed-time { font-size: 11px; color: #ccc; }
    }
  }
}

/* Chat Panel (重做核心部分) */
.chat-panel {
  height: 100%;
  .chat-container { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: #f9f9f9; }

  .chat-messages {
    flex: 1; overflow-y: auto; padding: 20px;
    .chat-empty { text-align: center; color: #C0C4CC; margin-top: 100px; p { margin-top: 10px; font-size: 14px; } }

    .message-row {
      display: flex; margin-bottom: 20px;

      /* 自己的消息样式 */
      &.me {
        flex-direction: row-reverse;
        .message-body { align-items: flex-end; }
        .avatar-wrapper { margin-left: 12px; margin-right: 0; }
        .content-wrapper {
          align-items: flex-end;
          .meta-info { flex-direction: row-reverse; }
          /* 自己的气泡：绿色或品牌色 */
          .bubble { background: #95ec69; color: #303133; border-radius: 8px 0 8px 8px; }
        }
      }

      .avatar-wrapper { margin-right: 12px; flex-shrink: 0; }
      .avatar {
        width: 40px; height: 40px; border-radius: 6px;
        display: flex; align-items: center; justify-content: center;
        font-weight: bold; color: #fff; font-size: 16px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      }

      .content-wrapper {
        display: flex; flex-direction: column; max-width: 70%;
        .meta-info {
          display: flex; align-items: center; gap: 6px; margin-bottom: 4px; font-size: 12px; color: #909399;
          .role-badge { height: 18px; padding: 0 4px; font-size: 10px; line-height: 16px; border: none;}
        }
        /* 他人的气泡：白色 */
        .bubble {
          padding: 10px 14px;
          background: #fff;
          border-radius: 0 8px 8px 8px;
          box-shadow: 0 1px 2px rgba(0,0,0,0.05);
          font-size: 14px;
          line-height: 1.5;
          word-wrap: break-word;
          white-space: pre-wrap;
          color: #303133;
          border: 1px solid #f0f0f0;
        }
      }
    }
  }

  .chat-input-area {
    padding: 16px;
    background: #fff;
    border-top: 1px solid $border-color;
    position: relative;

    .input-toolbar {
      position: absolute; bottom: 24px; right: 24px;
    }
    :deep(.el-textarea__inner) {
      padding-right: 50px; border: none; background: #f5f7fa; border-radius: 8px;
      &:focus { background: #fff; box-shadow: 0 0 0 1px $primary inset; }
    }
  }
}
</style>
