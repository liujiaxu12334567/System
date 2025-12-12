<template>
  <div class="classroom">
    <header class="cls-header">
      <div>
        <h2>{{ courseTitle }}</h2>
        <p>在线课堂 · 模式：{{ currentQuestion?.mode || 'broadcast' }}</p>
      </div>
      <el-button @click="goBack">返回</el-button>
    </header>

    <main class="cls-body">
      <section class="panel">
        <div class="panel-title">在线问题</div>
        <el-empty v-if="questions.length === 0" description="暂无问题" />
        <el-scrollbar height="320" v-else>
          <div v-for="q in questions" :key="q.id" class="q-item" :class="{'active': currentQuestion && currentQuestion.id === q.id}" @click="selectQuestion(q)">
            <div class="q-title">{{ q.title }}</div>
            <div class="q-desc">{{ q.description }}</div>
            <div class="q-meta">{{ formatTime(q.createTime) }} · 模式 {{ q.mode }}</div>
          </div>
        </el-scrollbar>
      </section>

      <section class="panel">
        <div class="panel-title">举手 / 抢答</div>
        <div class="btn-group">
          <el-button type="primary" plain @click="handRaise" :disabled="!currentQuestion">举手</el-button>
          <el-button type="success" plain @click="raceAnswer" :disabled="!currentQuestion">抢答</el-button>
          <el-button type="danger" plain @click="openAnswerDialog" :disabled="!currentQuestion">提交回答</el-button>
        </div>
        <el-empty v-if="answers.length === 0" description="暂无发言" style="margin-top:16px" />
        <el-scrollbar height="360" v-else>
          <div class="answer-item" v-for="a in answers" :key="a.id">
            <div class="answer-meta">
              <span>学生 {{ a.studentId }}</span>
              <el-tag size="small" type="warning">{{ a.type }}</el-tag>
              <span class="time">{{ formatTime(a.createTime) }}</span>
            </div>
            <div class="answer-text">{{ a.text }}</div>
          </div>
        </el-scrollbar>
      </section>

      <section class="panel">
        <div class="panel-title">课堂群聊</div>
        <el-scrollbar height="360">
          <div v-if="chats.length === 0" class="qa-empty">暂无聊天</div>
          <div class="chat-item" v-for="c in chats" :key="c.id">
            <div class="chat-meta">
              <div class="avatar" :style="{backgroundColor: avatarColor(c.role)}">
                {{ avatarInitial(c.senderName || c.senderId) }}
              </div>
              <div class="meta-text">
                <div class="meta-top">
                  <span class="sender">{{ c.senderName || c.senderId }}</span>
                  <el-tag size="small" :type="c.role === 'teacher' ? 'danger' : 'info'">
                    {{ c.role === 'teacher' ? '教师' : '学生' }}
                  </el-tag>
                </div>
                <span class="time">{{ formatTime(c.createTime) }}</span>
              </div>
            </div>
            <div class="chat-text">{{ c.content }}</div>
          </div>
        </el-scrollbar>
        <div class="chat-input">
          <el-input v-model="chatInput" placeholder="输入课堂聊天内容" @keyup.enter="sendChat" />
          <el-button type="primary" @click="sendChat">发送</el-button>
        </div>
      </section>
    </main>

    <el-dialog v-model="answerDialogVisible" title="提交回答" width="500px">
      <el-input v-model="answerText" type="textarea" :rows="4" placeholder="输入您的回答/发言" />
      <template #footer>
        <el-button @click="answerDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="submitAnswer">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'
// 原生 WebSocket 订阅课堂事件

const route = useRoute()
const router = useRouter()
const courseId = Number(route.params.courseId)
const courseTitle = ref(`课程 ${courseId}`)
const questions = ref([])
const currentQuestion = ref(null)
const answers = ref([])
const answerDialogVisible = ref(false)
const answerText = ref('')
let timer = null
let ws = null
const chats = ref([])
const chatInput = ref('')

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

const formatTime = (t) => t ? dayjs(t).format('MM-DD HH:mm') : ''

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
  chats.value = await request.get(`/student/classroom/${courseId}/chat`, { params: { limit: 200 } }) || []
}

const sendChat = async () => {
  if (!chatInput.value.trim()) return
  try {
    await request.post(`/student/classroom/${courseId}/chat`, { content: chatInput.value.trim() })
    chatInput.value = ''
    await loadChat()
  } catch (e) { ElMessage.error('发送失败') }
}

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

const startPolling = () => {
  timer = setInterval(() => {
    if (currentQuestion.value) {
      loadAnswers()
    } else {
      fetchQuestions()
    }
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
        case 'question':
          fetchQuestions()
          break
        case 'hand':
        case 'race':
        case 'answer':
        case 'call':
          loadAnswers()
          loadChat()
          break
        case 'chat':
          loadChat()
          break
        default:
          break
      }
    } catch (e) {}
  }
  ws.onerror = () => {}
  ws.onclose = () => setTimeout(connectWs, 5000)
}

const goBack = () => router.push('/home')

const avatarInitial = (name) => {
  if (!name) return 'U'
  return name.toString().trim().substring(0, 1).toUpperCase()
}
const avatarColor = (role) => role === 'teacher' ? '#F56C6C' : '#409EFF'
</script>

<style scoped>
.classroom { padding: 20px; background: #f5f7fa; min-height: 100vh; }
.cls-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.cls-body { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.panel { background: #fff; border: 1px solid #ebeef5; border-radius: 10px; padding: 14px; min-height: 300px; }
.panel-title { font-weight: 700; margin-bottom: 10px; }
.q-item { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px; margin-bottom: 8px; cursor: pointer; }
.q-item.active { border-color: #409EFF; background: #f5faff; }
.q-title { font-weight: 600; margin-bottom: 4px; }
.q-desc { font-size: 13px; color: #606266; margin-bottom: 4px; }
.q-meta { font-size: 12px; color: #909399; }
.btn-group { display: flex; gap: 10px; margin-bottom: 12px; }
.answer-item { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px; margin-bottom: 8px; background: #f9fafc; }
.answer-meta { display: flex; gap: 8px; align-items: center; font-size: 13px; color: #606266; margin-bottom: 4px; }
.answer-text { font-size: 14px; color: #303133; }
.chat-item { border-bottom: 1px solid #f0f0f0; padding: 8px 0; }
.chat-meta { display: flex; gap: 8px; align-items: center; font-size: 12px; color: #606266; }
.chat-text { font-size: 14px; color: #303133; margin-left: 36px; }
.chat-input { display: flex; gap: 8px; margin-top: 10px; }
.avatar { width: 30px; height: 30px; border-radius: 50%; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.meta-text { display: flex; flex-direction: column; }
.meta-top { display: flex; gap: 6px; align-items: center; }
</style>
