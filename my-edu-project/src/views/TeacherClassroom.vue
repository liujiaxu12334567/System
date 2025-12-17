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
        <el-button type="danger" plain round @click="endClassroom" style="margin-right: 10px;">
          结束课堂
        </el-button>

        <el-button type="info" plain round @click="showAnalysis" style="margin-right: 10px;">
          <el-icon style="margin-right: 4px"><DataAnalysis /></el-icon> 分析结果
        </el-button>

        <el-button type="warning" plain round @click="showPerformance" style="margin-right: 10px;">
          <el-icon style="margin-right: 4px"><DataLine /></el-icon> 课堂表现
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
              <el-form-item v-if="questionForm.mode !== 'hand'" label="正确答案（用于判题）">
                <el-input v-model="questionForm.correctAnswer" placeholder="请输入正确答案（可留空）" />
              </el-form-item>
              <el-form-item v-if="questionForm.mode === 'assign'" label="点名学生ID（userId）">
                <el-input v-model="questionForm.assignStudentId" placeholder="请输入学生 userId" />
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
                    <el-tag size="small" effect="plain">{{ getModeLabel(q.mode) }}</el-tag>
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

    <el-dialog v-model="analysisVisible" title="在线课堂分析（最新）" width="1000px" center destroy-on-close>
      <div v-loading="analysisLoading">
        <el-empty v-if="!analysisResult" description="暂无分析结果" />

        <div v-else>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="Metric">{{ analysisResult.metric }}</el-descriptions-item>
            <el-descriptions-item label="GeneratedAt">{{ formatDateTime(analysisResult.generatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="EventId">{{ analysisResult.eventId || '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">班级概览</el-divider>
          <el-table v-if="analysisClasses.length > 0" :data="analysisClasses" stripe style="width: 100%">
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-table :data="row.students || []" size="small" stripe style="width: 100%">
                  <el-table-column prop="name" label="姓名" width="120" />
                  <el-table-column prop="username" label="学号" width="140" />
                  <el-table-column prop="hand" label="举手" width="80" align="center" />
                  <el-table-column prop="race" label="抢答" width="80" align="center" />
                  <el-table-column prop="answer" label="回答" width="80" align="center" />
                  <el-table-column prop="chat" label="发言" width="80" align="center" />
                  <el-table-column prop="called" label="点名" width="80" align="center" />
                  <el-table-column prop="correct" label="正确" width="80" align="center" />
                  <el-table-column prop="wrong" label="错误" width="80" align="center" />
                  <el-table-column prop="total" label="总互动" width="90" align="center" />
                  <el-table-column prop="lastTime" label="最近时间" min-width="160" />
                </el-table>
              </template>
            </el-table-column>
            <el-table-column prop="className" label="班级" min-width="180" />
            <el-table-column prop="studentCount" label="总人数" width="100" align="center" />
            <el-table-column prop="activeCount" label="活跃人数" width="110" align="center" />
            <el-table-column prop="score" label="活跃指数" width="110" align="center" />
          </el-table>
          <el-alert v-else type="info" show-icon :closable="false" title="结果结构非班级列表，可直接查看 value/valueJson" />

          <el-divider content-position="left">历史记录</el-divider>
          <el-table :data="analysisHistory" size="small" stripe style="width: 100%" empty-text="暂无历史记录">
            <el-table-column prop="generatedAt" label="GeneratedAt" width="190">
              <template #default="{ row }">{{ formatDateTime(row.generatedAt) }}</template>
            </el-table-column>
            <el-table-column prop="eventId" label="EventId" min-width="220" />
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="selectAnalysisHistory(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="analysisVisible = false">关闭</el-button>
        <el-button type="primary" @click="fetchAnalysisData">刷新</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="performanceVisible" title="课堂表现（当前会话）" width="900px" center destroy-on-close>
      <div v-loading="performanceLoading" class="perf-container">

        <div v-if="viewMode === 'class'" class="class-dashboard-view">
          <div v-if="!performanceData || performanceData.length === 0" class="empty-state">
            <el-empty description="当前暂无班级数据" />
          </div>
          <div v-else class="dashboard-grid">
            <div
                v-for="cls in performanceData"
                :key="cls.classId"
                class="class-card"
                @click="enterClassDetail(cls)"
            >
              <div class="card-title">{{ cls.className }}</div>
              <div class="gauge-chart">
                <el-progress
                    type="dashboard"
                    :percentage="cls.score"
                    :color="getScoreColor"
                    :width="120"
                >
                  <template #default="{ percentage }">
                    <span class="percentage-value">{{ percentage }}</span>
                    <span class="percentage-label">活跃指数</span>
                  </template>
                </el-progress>
              </div>
              <div class="card-stats">
                <div class="stat-row">
                  <span>总人数：{{ cls.studentCount }}</span>
                  <span>活跃：<span style="color:#67C23A">{{ cls.activeCount }}</span></span>
                </div>
                <div class="hint-text">点击查看详情 <el-icon><ArrowRight /></el-icon></div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="student-detail-view">
          <div class="detail-header">
            <el-button link @click="viewMode = 'class'">
              <el-icon><ArrowLeft /></el-icon> 返回班级概览
            </el-button>
            <span class="current-class-title">{{ currentClassStats?.className }} - 学生明细</span>
          </div>

          <el-table
              :data="currentClassStats?.students || []"
              height="400"
              stripe
              style="width: 100%"
              :default-sort="{ prop: 'total', order: 'descending' }"
          >
            <el-table-column prop="name" label="学生姓名" width="120" fixed />
            <el-table-column prop="username" label="学号" width="130" />
            <el-table-column prop="hand" label="举手" width="100" sortable align="center">
              <template #default="{row}">
                <el-tag v-if="row.hand > 0" type="primary" size="small">{{ row.hand }}</el-tag>
                <span v-else class="text-gray">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="race" label="抢答" width="100" sortable align="center">
              <template #default="{row}">
                <el-tag v-if="row.race > 0" type="warning" size="small">{{ row.race }}</el-tag>
                <span v-else class="text-gray">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="answer" label="答题" width="100" sortable align="center">
              <template #default="{row}">
                <el-tag v-if="row.answer > 0" type="success" size="small">{{ row.answer }}</el-tag>
                <span v-else class="text-gray">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="total" label="总计互动" width="120" sortable align="center">
              <template #default="{row}">
                <span style="font-weight:bold; font-size:16px">{{ row.total }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastTime" label="最近时间" min-width="160" sortable />
          </el-table>
        </div>

      </div>
      <template #footer>
        <el-button @click="performanceVisible = false">关闭</el-button>
        <el-button type="primary" @click="fetchPerformanceData">刷新数据</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Promotion, Refresh, DataLine, ArrowRight, DataAnalysis } from '@element-plus/icons-vue'
import request from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const courseId = Number(route.params.courseId)
const courseTitle = ref(`课程 ${courseId}`)
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

const questionForm = ref({ title: '', content: '', correctAnswer: '', mode: 'broadcast', assignStudentId: '' })
const modeOptions = [
  { value: 'broadcast', label: '广播' },
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

// 课堂表现状态
const performanceVisible = ref(false)
const performanceLoading = ref(false)
const performanceData = ref([])
const viewMode = ref('class') // 'class' | 'student'
const currentClassStats = ref(null)

const analysisVisible = ref(false)
const analysisLoading = ref(false)
const analysisMetric = ref('classroom_online_performance')
const analysisResult = ref(null)
const analysisHistory = ref([])
const analysisClasses = computed(() => {
  const students = analysisResult.value?.value?.students
  return Array.isArray(students) ? students : []
})

let timer = null
let ws = null

const startClassroom = async () => {
  try {
    await request.post(`/teacher/classroom/${courseId}/start`)
    return true
  } catch (e) {
    ElMessage.warning(e?.response?.data || e?.message || '未到上课时间，无法开启在线课堂')
    return false
  }
}

const endClassroom = async () => {
  try {
    await ElMessageBox.confirm(
      '结束本节课堂将清空本节聊天与在线测试数据，并生成分析结果（可在“分析结果”中查看）。是否继续？',
      '确认结束课堂',
      { type: 'warning', confirmButtonText: '结束课堂', cancelButtonText: '取消' }
    )
    await request.post(`/teacher/classroom/${courseId}/end`)
    ElMessage.success('已结束课堂')
    showAnalysis()
  } catch (e) {
    // cancel or error
  }
}

onMounted(async () => {
  if (!localStorage.getItem('token')) {
    router.push('/login')
    return
  }
  // 进入前预检：未到上课时间则禁止进入，并给出提示
  try {
    const status = await request.get(`/teacher/classroom/${courseId}/status`)
    if (!status?.canEnter) {
      ElMessage.warning(status?.message || '未到上课时间，无法进入在线课堂')
      router.replace('/teacher')
      return
    }
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '无法获取课堂状态')
    router.replace('/teacher')
    return
  }
  const ok = await startClassroom()
  if (!ok) {
    router.replace('/teacher')
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
const formatDateTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : ''
const getModeLabel = (mode) => {
  const map = { broadcast: '全体', race: '抢答', assign: '点名', hand: '举手' }
  return map[mode] || mode || '-'
}
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

// === 课堂表现逻辑 ===
const fetchPerformanceData = async () => {
  performanceLoading.value = true
  try {
    const res = await request.get('/teacher/classroom/performance', {
      params: { courseId }
    })
    performanceData.value = res || []

    // 如果当前在详情页，需要更新详情数据
    if (viewMode.value === 'student' && currentClassStats.value) {
      const updatedClass = performanceData.value.find(c => c.classId === currentClassStats.value.classId)
      if (updatedClass) currentClassStats.value = updatedClass
    }
  } catch (e) {
    ElMessage.error('获取课堂表现失败')
  } finally {
    performanceLoading.value = false
  }
}

const showPerformance = () => {
  viewMode.value = 'class'
  performanceVisible.value = true
  fetchPerformanceData()
}

const fetchAnalysisData = async () => {
  analysisLoading.value = true
  try {
    try {
      await request.post(`/teacher/classroom/${courseId}/analysis/generate`, {}, {
        params: { metric: analysisMetric.value }
      })
    } catch (e) {}

    const latest = await request.get('/analysis/result', {
      params: { courseId, metric: analysisMetric.value }
    })
    analysisResult.value = latest || null

    const list = await request.get('/analysis/results', {
      params: { courseId, metric: analysisMetric.value, limit: 10 }
    })
    analysisHistory.value = Array.isArray(list) ? list : []
  } catch (e) {
    ElMessage.error('获取分析结果失败')
  } finally {
    analysisLoading.value = false
  }
}

const showAnalysis = () => {
  analysisVisible.value = true
  fetchAnalysisData()
}

const selectAnalysisHistory = (row) => {
  analysisResult.value = row || null
}

const enterClassDetail = (classStats) => {
  currentClassStats.value = classStats
  viewMode.value = 'student'
}

const getScoreColor = (percentage) => {
  if (percentage < 30) return '#909399'
  if (percentage < 70) return '#E6A23C'
  return '#67C23A'
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
    await request.post(`/teacher/classroom/${courseId}/chat`, { content: chatInput.value.trim() })
    chatInput.value = ''
    await loadChat()
  } catch (e) { ElMessage.error('发送失败') }
}

const publishQuestion = async () => {
  if (!questionForm.value.title) return ElMessage.warning('请输入标题')
  if (questionForm.value.mode === 'assign' && !String(questionForm.value.assignStudentId || '').trim()) {
    return ElMessage.warning('点名模式需要填写学生ID')
  }
  try {
    const payload = { ...questionForm.value, courseId }
    await request.post('/teacher/classroom/question', payload)
    ElMessage.success('已发布')
    questionForm.value.title = ''
    questionForm.value.content = ''
    questionForm.value.correctAnswer = ''
    questionForm.value.assignStudentId = ''
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
        case 'chat': loadChat(); break;
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
      .status-badge { font-size: 12px; color: #909399; display: flex; align-items: center; gap: 6px; .dot { width: 6px; height: 6px; border-radius: 50%; background: #F56C6C; &.online { background: #67C23A; } } }
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

/* 课堂表现弹窗样式 */
.perf-container {
  min-height: 400px;
}

/* 视图1：仪表盘网格 */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
  padding: 10px;
}

.class-card {
  background: #fff;
  border: 1px solid #EBEEF5;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
    border-color: #409EFF;
  }

  .card-title {
    font-size: 18px;
    font-weight: bold;
    color: #303133;
    margin-bottom: 15px;
  }

  .gauge-chart {
    display: flex;
    justify-content: center;
    margin-bottom: 15px;

    .percentage-value {
      display: block;
      font-size: 28px;
      font-weight: bold;
      color: #303133;
    }
    .percentage-label {
      display: block;
      font-size: 12px;
      color: #909399;
      margin-top: 5px;
    }
  }

  .card-stats {
    border-top: 1px dashed #EBEEF5;
    padding-top: 15px;

    .stat-row {
      display: flex;
      justify-content: space-between;
      font-size: 14px;
      color: #606266;
      margin-bottom: 10px;
    }

    .hint-text {
      font-size: 12px;
      color: #409EFF;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }
  }
}

/* 视图2：详情表格 */
.student-detail-view {
  .detail-header {
    display: flex;
    align-items: center;
    gap: 15px;
    margin-bottom: 20px;
    padding-bottom: 10px;
    border-bottom: 1px solid #EBEEF5;

    .current-class-title {
      font-size: 18px;
      font-weight: bold;
      color: #303133;
    }
  }

  .text-gray {
    color: #C0C4CC;
  }
}
</style>
