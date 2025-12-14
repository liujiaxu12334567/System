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
            <span class="dot online"></span> 在线课堂 · 模式：{{ currentQuestion?.mode || 'broadcast' }}
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button type="warning" plain round style="margin-right: 8px" @click="resetClassroom">
          <el-icon style="margin-right: 4px"><Refresh /></el-icon> 开启新课堂
        </el-button>
        <el-button type="primary" round @click="publishQuestion">
          <el-icon style="margin-right: 4px"><Promotion /></el-icon> 发布提问
        </el-button>
      </div>
    </header>

    <main class="cls-body">
      <div class="left-column">
        <section class="panel control-panel">
          <div class="panel-header">
            <span class="title">提问控制</span>
          </div>
          <div class="form-container">
            <el-form label-position="top" size="small">
              <el-form-item label="互动模式">
                <el-radio-group v-model="questionForm.mode" size="small">
                  <el-radio-button v-for="m in modeOptions" :key="m.value" :label="m.value">{{ m.label }}</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="问题标题">
                <el-input v-model="questionForm.title" placeholder="请输入问题标题" />
              </el-form-item>
              <el-form-item label="补充描述">
                <el-input v-model="questionForm.content" type="textarea" :rows="2" placeholder="规则说明或补充..." resize="none" />
              </el-form-item>
            </el-form>
          </div>
        </section>

        <section class="panel list-panel">
          <el-tabs v-model="activeTab" class="custom-tabs">
            <el-tab-pane label="历史提问" name="questions">
              <el-scrollbar height="300px">
                <div v-for="q in questions" :key="q.id" class="list-item" :class="{'active': currentQuestion && currentQuestion.id === q.id}" @click="selectQuestion(q)">
                  <div class="item-title">{{ q.title }}</div>
                  <div class="item-meta">
                    <span class="time">{{ formatTimeShort(q.createTime) }}</span>
                    <el-tag size="small" effect="plain">{{ q.mode }}</el-tag>
                  </div>
                </div>
              </el-scrollbar>
            </el-tab-pane>
            <el-tab-pane label="举手/抢答" name="queue">
              <el-table :data="queue" size="small" :show-header="false" style="width: 100%" empty-text="暂无排队">
                <el-table-column width="80">
                  <template #default="{row}"><el-tag size="small">{{ row.type }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="studentId" label="学号">
                  <template #default="{row}">S-{{ row.studentId }}</template>
                </el-table-column>
                <el-table-column width="80" align="right">
                  <template #default="{row}">
                    <el-button v-if="row.state !== 'called'" size="small" type="primary" link @click="callAnswer(row.id)">点名</el-button>
                    <el-tag v-else type="success" size="small">已点</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>

      <div class="middle-column">
        <section class="panel feed-panel">
          <div class="panel-header">
            <span class="title">学生发言流</span>
            <el-button type="text" @click="loadAnswers"><el-icon><Refresh /></el-icon></el-button>
          </div>
          <div class="feed-content">
            <el-empty v-if="answers.length === 0" description="等待学生互动..." :image-size="80" />
            <el-scrollbar v-else>
              <div v-for="a in answers" :key="a.id" class="feed-card">
                <div class="feed-header">
                  <div class="user">
                    <el-avatar :size="24" style="background:#409EFF">{{ avatarInitial('S'+a.studentId) }}</el-avatar>
                    <span class="name">学生 {{ a.studentId }}</span>
                  </div>
                  <span class="time">{{ formatTime(a.createTime) }}</span>
                </div>
                <div class="feed-body">
                  <el-tag size="small" type="warning" class="type-badge">{{ a.type }}</el-tag>
                  <span class="text">{{ a.text }}</span>
                </div>
              </div>
            </el-scrollbar>
          </div>
        </section>
      </div>

      <section class="panel chat-panel">
        <div class="panel-header">
          <span class="title">课堂群聊</span>
        </div>

        <div class="chat-container">
          <div class="chat-messages" ref="chatScrollRef">
            <div v-if="chats.length === 0" class="chat-empty">暂无消息</div>

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
                  <span class="sender-name">{{ c.senderName || '老师' }}</span>
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
                placeholder="发布群公告或消息..."
                @keyup.enter.prevent="sendChat"
                resize="none"
            />
            <div class="input-toolbar">
              <el-button type="primary" size="small" @click="sendChat">发送</el-button>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Promotion, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const courseId = Number(route.params.courseId)
const courseTitle = ref(`课程 ${courseId}`)
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

const questionForm = ref({ title: '', content: '', mode: 'broadcast' })
const modeOptions = [
  { value: 'broadcast', label: '广播' },
  { value: 'hand', label: '举手' },
  { value: 'race', label: '抢答' },
  { value: 'assign', label: '点名' }
]
const activeTab = ref('questions')
const questions = ref([])
const currentQuestion = ref(null)
const queue = ref([])
const answers = ref([])
const chats = ref([])
const chatInput = ref('')
const chatScrollRef = ref(null)

let timer = null
let ws = null

onMounted(async () => {
  if (!localStorage.getItem('token')) {
    router.push('/login')
    return
  }
  await fetchQuestions()
  startPolling()
  connectWs()
  await loadChat()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (ws) ws.close()
})

const formatTime = (t) => t ? dayjs(t).format('HH:mm') : ''
const formatTimeShort = (t) => t ? dayjs(t).format('MM-DD HH:mm') : ''
const isMe = (senderId) => senderId == userInfo.id
const avatarInitial = (name) => name ? name.toString().trim().substring(0, 1).toUpperCase() : 'T'
const avatarColor = (role) => role === 'teacher' ? '#F56C6C' : '#409EFF'

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
    questions.value = await request.get('/teacher/classroom/questions', { params: { courseId } }) || []
    if (questions.value.length && !currentQuestion.value) {
      await selectQuestion(questions.value[0])
    }
  } catch (e) { console.error(e) }
}

const selectQuestion = async (q) => {
  currentQuestion.value = q
  await Promise.all([loadQueue(), loadAnswers()])
}

const loadQueue = async () => {
  if (!currentQuestion.value) return
  queue.value = await request.get(`/teacher/classroom/question/${currentQuestion.value.id}/queue`) || []
}
const loadAnswers = async () => {
  if (!currentQuestion.value) return
  answers.value = await request.get(`/teacher/online-question/${currentQuestion.value.id}/answers`) || []
}

const loadChat = async () => {
  chats.value = await request.get(`/teacher/classroom/${courseId}/chat`, { params: { limit: 200 } }) || []
  scrollToBottom()
}

// === Actions ===
const sendChat = async () => {
  if (!chatInput.value.trim()) return
  try {
    const res = await request.post(`/teacher/classroom/${courseId}/chat`, { content: chatInput.value.trim() })
    chatInput.value = ''
    if (res) {
      chats.value.push(res)
      scrollToBottom()
    }
  } catch (e) { ElMessage.error('发送失败') }
}

const publishQuestion = async () => {
  if (!questionForm.value.title) return ElMessage.warning('请输入标题')
  try {
    const payload = { ...questionForm.value, courseId }
    await request.post('/teacher/classroom/question', payload)
    ElMessage.success('已发布')
    questionForm.value.title = '' // Reset
    await fetchQuestions()
  } catch (e) { ElMessage.error('发布失败') }
}

const callAnswer = async (id) => {
  try {
    await request.post(`/teacher/classroom/answer/${id}/call`)
    ElMessage.success('已允许发言')
    await Promise.all([loadQueue(), loadAnswers()])
  } catch (e) { ElMessage.error('操作失败') }
}

const resetClassroom = async () => {
  const ok = window.confirm('开启新课堂会清空上一节的聊天与在线提问记录，确认继续？')
  if (!ok) return
  try {
    await request.post(`/teacher/classroom/${courseId}/reset`)
    ElMessage.success('已开启新的课堂')
    currentQuestion.value = null
    queue.value = []
    answers.value = []
    chats.value = []
    await fetchQuestions()
    await loadChat()
  } catch (e) { ElMessage.error('重置失败') }
}

// === Connection ===
const startPolling = () => {
  timer = setInterval(() => {
    if (currentQuestion.value) { loadQueue(); loadAnswers(); }
    else fetchQuestions()
  }, 5000)
}

const connectWs = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${window.location.host}/ws/classroom?courseId=${courseId}`
  ws = new WebSocket(url)
  ws.onmessage = (evt) => {
    try {
      const msg = JSON.parse(evt.data)
      if (msg.courseId !== courseId) return
      switch (msg.type) {
        case 'question': fetchQuestions(); break;
        case 'hand': case 'race': case 'answer': case 'call': loadQueue(); loadAnswers(); loadChat(); break;
        case 'chat':
          if (msg.payload) {
            chats.value.push(msg.payload)
            scrollToBottom()
          } else {
            loadChat()
          }
          break;
        case 'reset':
          currentQuestion.value = null;
          queue.value = [];
          answers.value = [];
          chats.value = [];
          fetchQuestions();
          break;
      }
    } catch (e) {}
  }
  ws.onerror = () => {}
  ws.onclose = () => setTimeout(connectWs, 5000)
}

const goBack = () => router.push('/teacher')
</script>

<style scoped lang="scss">
$primary: #409EFF;
$bg-color: #f0f2f5;
$panel-bg: #ffffff;
$border-color: #ebeef5;

.classroom {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
  font-family: 'PingFang SC', sans-serif;
}

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
    .back-btn { color: #606266; font-size: 18px; &:hover { color: $primary; } }
    .course-info {
      h2 { margin: 0; font-size: 18px; color: #303133; }
      .status-badge { font-size: 12px; color: #909399; display: flex; align-items: center; gap: 6px; .dot { width: 6px; height: 6px; border-radius: 50%; background: #67C23A; } }
    }
  }
}

.cls-body {
  flex: 1;
  display: grid;
  grid-template-columns: 320px 1fr 360px;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

.left-column, .middle-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
    height: 48px;
    padding: 0 16px;
    border-bottom: 1px solid $border-color;
    display: flex;
    align-items: center;
    justify-content: space-between;
    .title { font-weight: 600; font-size: 15px; color: #303133; }
  }
}

/* Control Panel */
.control-panel {
  .form-container { padding: 16px; background: #fbfbfc; }
}

/* List Panel */
.list-panel {
  flex: 1;
  .list-item {
    padding: 12px 16px; border-bottom: 1px solid #f5f7fa; cursor: pointer;
    &:hover { background: #f9fafc; }
    &.active { background: #ecf5ff; border-left: 3px solid $primary; }
    .item-title { font-size: 14px; color: #303133; margin-bottom: 4px; }
    .item-meta { display: flex; justify-content: space-between; color: #909399; font-size: 12px; }
  }
}

/* Feed Panel */
.feed-panel {
  flex: 1;
  .feed-content {
    flex: 1; overflow: hidden; padding: 16px; background: #f5f7fa;
    .feed-card {
      background: #fff; padding: 12px; border-radius: 8px; margin-bottom: 12px; box-shadow: 0 1px 2px rgba(0,0,0,0.03);
      .feed-header { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 12px; color: #909399; .user { display: flex; align-items: center; gap: 6px; } }
      .feed-body {
        .type-badge { margin-right: 6px; vertical-align: middle; }
        .text { font-size: 14px; color: #303133; vertical-align: middle; }
      }
    }
  }
}

/* Chat Panel (Shared Styles) */
.chat-panel {
  height: 100%;
  .chat-container { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: #f9f9f9; }

  .chat-messages {
    flex: 1; overflow-y: auto; padding: 20px;
    .chat-empty { text-align: center; color: #C0C4CC; margin-top: 100px; }

    .message-row {
      display: flex; margin-bottom: 16px;
      &.me {
        flex-direction: row-reverse;
        .message-body { align-items: flex-end; }
        .avatar-wrapper { margin-left: 10px; margin-right: 0; }
        .content-wrapper {
          align-items: flex-end;
          .meta-info { flex-direction: row-reverse; }
          .bubble { background: $primary; color: #fff; border-radius: 12px 0 12px 12px; }
        }
      }

      .avatar-wrapper { margin-right: 10px; flex-shrink: 0; }
      .avatar { width: 36px; height: 36px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-weight: bold; color: #fff; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }

      .content-wrapper {
        display: flex; flex-direction: column; max-width: 75%;
        .meta-info {
          display: flex; align-items: center; gap: 6px; margin-bottom: 4px; font-size: 12px; color: #909399;
          .role-badge { height: 18px; padding: 0 4px; font-size: 10px; line-height: 16px; }
        }
        .bubble {
          padding: 10px 14px;
          background: #fff;
          border-radius: 0 12px 12px 12px;
          box-shadow: 0 1px 2px rgba(0,0,0,0.05);
          font-size: 14px;
          line-height: 1.5;
          word-wrap: break-word;
          white-space: pre-wrap;
          color: #303133;
        }
      }
    }
  }

  .chat-input-area {
    padding: 12px;
    background: #fff;
    border-top: 1px solid $border-color;
    position: relative;
    .input-toolbar { position: absolute; bottom: 20px; right: 20px; }
    :deep(.el-textarea__inner) {
      padding-right: 60px; border: none; background: #f5f7fa; border-radius: 8px;
      &:focus { background: #fff; box-shadow: 0 0 0 1px $primary inset; }
    }
  }
}
</style>
