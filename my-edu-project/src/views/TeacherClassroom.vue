<template>
  <div class="classroom">
    <header class="cls-header">
      <div>
        <h2>{{ courseTitle }}</h2>
        <p>在线课堂 · 模式：{{ currentQuestion?.mode || 'broadcast' }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="publishQuestion">发布提问</el-button>
        <el-button @click="goBack">返回</el-button>
      </div>
    </header>

    <main class="cls-body">
      <section class="panel">
        <div class="panel-title">当前提问</div>
        <el-form label-width="80px" class="form-block">
          <el-form-item label="模式">
            <el-select v-model="questionForm.mode" style="width: 160px">
              <el-option v-for="m in modeOptions" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="questionForm.title" placeholder="如：请同学们总结今天的要点" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="questionForm.content" type="textarea" :rows="3" placeholder="可写点名学生或抢答规则" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="publishQuestion" :disabled="!questionForm.title">发布</el-button>
          </el-form-item>
        </el-form>

        <div class="question-list">
          <div class="list-header">问题列表</div>
          <el-empty v-if="questions.length === 0" description="暂无问题" />
          <el-scrollbar height="260" v-else>
            <div
              v-for="q in questions"
              :key="q.id"
              class="q-item"
              :class="{'active': currentQuestion && currentQuestion.id === q.id}"
              @click="selectQuestion(q)"
            >
              <div class="q-title">{{ q.title }}</div>
              <div class="q-desc">{{ q.description }}</div>
              <div class="q-meta">{{ formatTime(q.createTime) }} · 模式 {{ q.mode }}</div>
            </div>
          </el-scrollbar>
        </div>
      </section>

      <section class="panel">
        <div class="panel-title">举手 / 抢答队列</div>
        <el-empty v-if="queue.length === 0" description="暂无举手/抢答" />
        <el-table v-else :data="queue" size="small" border>
          <el-table-column prop="studentId" label="学生ID" width="120" />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{row}">
              <el-tag :type="row.state === 'called' ? 'success' : 'info'" size="small">{{ row.state }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="text" label="内容" />
          <el-table-column label="操作" width="140">
            <template #default="{row}">
              <el-button size="small" type="primary" @click="callAnswer(row.id)">允许发言</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-title">回答 / 发言流</div>
        <el-empty v-if="answers.length === 0" description="暂无发言" />
        <el-scrollbar height="500" v-else>
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
        <div class="panel-title">课堂群聊（所有老师/学生可见）</div>
        <el-scrollbar height="420">
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
                <div class="time">{{ formatTime(c.createTime) }}</div>
              </div>
            </div>
            <div class="chat-text">{{ c.content }}</div>
          </div>
        </el-scrollbar>
        <div class="chat-input">
          <el-input v-model="chatInput" placeholder="输入群聊内容" @keyup.enter="sendChat" />
          <el-button type="primary" @click="sendChat">发送</el-button>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'
// 采用原生 WebSocket（fallback 轮询已保留）

const router = useRouter()
const route = useRoute()
const courseId = Number(route.params.courseId)
const courseTitle = ref(`课程 ${courseId}`)

const questionForm = ref({ title: '', content: '', mode: 'broadcast' })
const modeOptions = [
  { value: 'broadcast', label: '广播提问' },
  { value: 'hand', label: '举手模式' },
  { value: 'race', label: '抢答模式' },
  { value: 'assign', label: '点名提问' }
]
const questions = ref([])
const currentQuestion = ref(null)
const queue = ref([])
const answers = ref([])
const chats = ref([])
const chatInput = ref('')
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

const formatTime = (t) => t ? dayjs(t).format('MM-DD HH:mm') : ''

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
}

const sendChat = async () => {
  if (!chatInput.value.trim()) return
  try {
    await request.post(`/teacher/classroom/${courseId}/chat`, { content: chatInput.value.trim() })
    chatInput.value = ''
    await loadChat()
  } catch (e) { ElMessage.error('发送失败') }
}

const publishQuestion = async () => {
  if (!questionForm.value.title) return ElMessage.warning('请输入标题')
  try {
    const payload = { ...questionForm.value, courseId }
    await request.post('/teacher/classroom/question', payload)
    ElMessage.success('已发布')
    questionForm.value = { title: '', content: '', mode: 'broadcast' }
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

const startPolling = () => {
  timer = setInterval(() => {
    if (currentQuestion.value) {
      loadQueue()
      loadAnswers()
    } else {
      fetchQuestions()
    }
  }, 5000)
}

const avatarInitial = (name) => {
  if (!name) return 'U'
  return name.toString().trim().substring(0, 1).toUpperCase()
}
const avatarColor = (role) => role === 'teacher' ? '#F56C6C' : '#409EFF'

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
          loadQueue()
          loadAnswers()
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
  ws.onclose = () => {
    // 简单重连
    setTimeout(connectWs, 5000)
  }
}

const goBack = () => router.push('/teacher')
</script>

<style scoped>
.classroom { padding: 20px; background: #f5f7fa; min-height: 100vh; }
.cls-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.cls-body { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.panel { background: #fff; border: 1px solid #ebeef5; border-radius: 10px; padding: 14px; min-height: 280px; }
.panel-title { font-weight: 700; margin-bottom: 10px; }
.form-block { background: #f7f9fc; padding: 10px; border-radius: 8px; }
.question-list { margin-top: 12px; }
.list-header { font-weight: 600; margin-bottom: 6px; }
.q-item { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px; margin-bottom: 8px; cursor: pointer; }
.q-item.active { border-color: #409EFF; background: #f5faff; }
.q-title { font-weight: 600; margin-bottom: 4px; }
.q-desc { font-size: 13px; color: #606266; margin-bottom: 4px; }
.q-meta { font-size: 12px; color: #909399; }
.answer-item { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px; margin-bottom: 8px; background: #f9fafc; }
.answer-meta { display: flex; gap: 8px; align-items: center; font-size: 13px; color: #606266; margin-bottom: 4px; }
.answer-text { font-size: 14px; color: #303133; }
.header-actions { display: flex; gap: 8px; }
.chat-item { border-bottom: 1px solid #f0f0f0; padding: 8px 0; }
.chat-meta { display: flex; gap: 8px; align-items: center; font-size: 12px; color: #606266; }
.chat-text { font-size: 14px; color: #303133; margin-left: 36px; }
.chat-input { display: flex; gap: 8px; margin-top: 10px; }
.avatar { width: 30px; height: 30px; border-radius: 50%; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.meta-text { display: flex; flex-direction: column; }
.meta-top { display: flex; gap: 6px; align-items: center; }
</style>
