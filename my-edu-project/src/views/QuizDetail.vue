<template>
  <div class="quiz-detail-container" v-loading="loading">
    <header class="quiz-header">
      <div class="inner">
        <div class="left">
          <el-button link @click="$router.go(-1)">
            <el-icon><ArrowLeft /></el-icon> 返回课程
          </el-button>
          <span class="divider">|</span>
          <h2 class="title">{{ quizTitle }}</h2>
          <el-tag v-if="materialType" type="info" class="type-tag">{{ materialType }}</el-tag>
        </div>
        <div class="right" v-if="mode === 'review' && materialType === '测验'">
          <span class="score-label">最终得分：</span>
          <span class="score-value">{{ myScore }}</span>
          <span class="total-score">/ {{ totalScore }} 分</span>
        </div>
      </div>
    </header>

    <div v-if="mode === 'review'" class="ai-chat-section">
      <div class="inner">
        <div class="chat-window">
          <div class="chat-header">
            <div class="title-box">
              <el-icon class="ai-icon" :size="22"><MagicStick /></el-icon>
              <span>AI 智能助教 (记忆模式)</span>
            </div>
            <el-tag effect="dark" type="primary" round size="small">DeepSeek V3</el-tag>
          </div>

          <div class="chat-messages" ref="chatBoxRef">
            <div v-for="(msg, index) in chatHistory" :key="index" class="message-row" :class="msg.role">
              <div class="avatar" v-if="msg.role === 'assistant'">
                <el-icon><Cpu /></el-icon>
              </div>
              <div class="bubble">
                <div class="bubble-content">{{ msg.content }}</div>
                <span v-if="msg.isTyping" class="cursor">|</span>
              </div>
              <div class="avatar user-avatar" v-if="msg.role === 'user'">
                <el-icon><User /></el-icon>
              </div>
            </div>

            <div v-if="initialAiFeedback" class="message-row assistant" style="margin-top: 15px;">
              <div class="avatar"><el-icon><Cpu /></el-icon></div>
              <div class="bubble">
                <div class="bubble-content">
                  <span style="font-weight: bold; color: #409EFF;">教师/AI反馈:</span> {{ initialAiFeedback }}
                </div>
              </div>
            </div>

          </div>

          <div class="chat-input-area">
            <div v-if="!hasSubmitted" class="start-btn-box">
              <el-alert title="请先提交您的测验或作业，才能启用智能分析。" type="info" :closable="false" style="margin-bottom: 10px;"/>
            </div>

            <div v-else-if="chatHistory.length === 0" class="start-btn-box">
              <el-button type="primary" size="large" round @click="startAnalysis" :loading="analyzing">
                <el-icon style="margin-right:5px"><DataAnalysis /></el-icon>
                {{ analyzing ? 'AI 正在分析您的作业...' : '开始智能分析与改错' }}
              </el-button>
            </div>

            <div v-else class="input-row">
              <el-input
                  v-model="userQuery"
                  placeholder="针对这次作业/测验，您还有什么疑问？(支持多轮对话)"
                  @keyup.enter="sendUserMessage"
                  :disabled="analyzing"
              />
              <el-button type="primary" @click="sendUserMessage" :loading="analyzing">发送</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="materialType === '测验'" class="quiz-body">
      <div class="question-paper">
        <div v-for="(q, index) in questions" :key="index" :id="'q-'+index" class="question-item">
          <div class="q-title">
            <span class="q-no">{{ index + 1 }}</span>
            <span class="q-type">单选题</span>
            <span class="q-text">{{ q.title }}</span>
            <span class="q-score">（{{ q.score }}分）</span>
          </div>
          <div class="q-options">
            <div v-for="(opt, oIdx) in q.options" :key="oIdx" class="option-row" :class="getOptionClass(index, oIdx)" @click="handleSelectOption(index, oIdx)">
              <span class="opt-label">{{ String.fromCharCode(65+oIdx) }}</span>
              <span class="opt-content">{{ opt }}</span>
              <div v-if="mode === 'review'" class="result-icon">
                <el-icon v-if="isRightOption(index, oIdx)" color="#67C23A"><Check /></el-icon>
                <el-icon v-else-if="isWrongSelected(index, oIdx)" color="#F56C6C"><Close /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="side-panel">
        <div class="panel-card">
          <div class="panel-header"><h3>{{ mode === 'take' ? '答题卡' : '成绩单' }}</h3></div>
          <div v-if="mode === 'review'" class="score-display">
            <div class="big-score">{{ myScore }}</div>
            <div class="score-desc">卷面总分</div>
          </div>
          <div class="card-grid">
            <div v-for="(q, index) in questions" :key="index" class="grid-item" :class="getGridClass(index)" @click="scrollToQuestion(index)">{{ index + 1 }}</div>
          </div>
          <div class="action-area">
            <el-button v-if="mode === 'take'" type="primary" size="large" class="submit-btn" @click="handleSubmit">提交试卷</el-button>
            <el-button v-else size="large" class="submit-btn" @click="$router.go(-1)">返回列表</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="materialType === '作业' || materialType === '项目'" class="assignment-body">
      <div class="task-card">
        <div class="task-header">
          <h3>{{ materialType }}内容</h3>
          <div class="meta-info">
            <span>截止时间：{{ taskContent.deadline || '无限制' }}</span>
            <span class="status-tag" :class="mode === 'review' ? 'done' : 'todo'">{{ mode === 'review' ? '已提交' : '待提交' }}</span>
          </div>
        </div>
        <div class="task-desc-content">
          <div class="desc-text">{{ taskContent.text }}</div>
          <div class="teacher-attachment" v-if="taskFilePath">
            <div class="attach-item">
              <el-icon><Paperclip /></el-icon> <span>{{ taskFileName }}</span>
              <el-button link type="primary" @click="downloadFile(taskFilePath, taskFileName)">下载</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="answer-card">
        <div class="card-header"><h3>我的作答</h3></div>
        <div v-if="mode === 'review'" class="submitted-content">
          <el-alert v-if="hasSubmitted" title="已提交" type="success" :closable="false" show-icon style="margin-bottom: 20px" />
          <el-alert v-else title="未提交" type="warning" :closable="false" show-icon style="margin-bottom: 20px" />

          <div class="answer-section">
            <div class="label">文本内容：</div>
            <div class="text-display">{{ myAnswerText || '（未填写文本）' }}</div>
          </div>
          <div class="answer-section" v-if="myAnswerFiles.length > 0">
            <div class="file-list">
              <div v-for="(file, i) in myAnswerFiles" :key="i" class="file-item-display">
                <el-icon><Document /></el-icon> 附件 {{ i + 1 }}
                <el-button link type="primary" @click="downloadFile(file, '我的作业_'+(i+1))">下载</el-button>
              </div>
            </div>
          </div>

          <div class="submit-bar" v-if="!hasSubmitted">
            <el-button size="large" @click="$router.go(-1)">返回</el-button>
            <el-button type="primary" size="large" @click="mode = 'take'">继续作答</el-button>
          </div>
          <div class="submit-bar" v-else>
            <el-button size="large" @click="$router.go(-1)">返回</el-button>
          </div>

        </div>
        <div v-else class="edit-content">
          <el-form label-position="top">
            <el-form-item label="正文内容"><el-input v-model="submitText" type="textarea" :rows="10" /></el-form-item>
            <el-form-item label="上传附件">
              <el-upload class="upload-demo" action="#" :auto-upload="false" :on-change="(f, l) => submitFiles = l" :on-remove="(f, l) => submitFiles = l" multiple :limit="5">
                <el-button type="primary" plain>点击上传</el-button>
              </el-upload>
            </el-form-item>
          </el-form>
          <div class="submit-bar">
            <el-button size="large" @click="$router.go(-1)">取消</el-button>
            <el-button type="primary" size="large" @click="submitAssignment" :loading="submitting">提交{{ materialType }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="assignment-body">
      <el-empty :description="`无法识别的资料类型: ${materialType}`" />
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Close, Paperclip, Document, Upload, MagicStick, Cpu, User, DataAnalysis } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const { courseId, materialId } = route.params
const mode = ref(route.query.mode || 'take')
const sourceType = route.query.type

const loading = ref(true)
const submitting = ref(false)
const quizTitle = ref('')
const materialType = ref('')

const questions = ref([])
const userAnswers = ref([])
const myScore = ref(0)
const taskContent = ref({ text: '', deadline: '' })
const taskFilePath = ref('')
const taskFileName = ref('')
const submitText = ref('')
const submitFiles = ref([])
const myAnswerText = ref('')
const myAnswerFiles = ref([])
const hasSubmitted = ref(false)
const initialAiFeedback = ref('')

// === 聊天与 AI 相关 ===
const chatHistory = ref([])
const userQuery = ref('')
const analyzing = ref(false)
const chatBoxRef = ref(null)

onMounted(async () => {
  await loadData()
  loading.value = false
})

const loadData = async () => {
  try {
    const allMaterials = await request.get(`/student/course/${courseId}/materials`)
    const target = allMaterials.find(m => m.id == materialId)
    if (!target) return

    // ★★★ 核心修复：优先使用 Query 参数，其次使用 API 返回的类型 ★★★
    materialType.value = sourceType || target.type

    quizTitle.value = target.title || target.fileName
    taskFilePath.value = target.filePath
    taskFileName.value = target.fileName

    let contentJson = {}
    try { contentJson = JSON.parse(target.content) } catch(e) { contentJson = { text: target.content, deadline: '' } }

    if (materialType.value === '测验') {
      questions.value = contentJson.questions || []
      if (mode.value === 'take') userAnswers.value = new Array(questions.value.length).fill(-1)
    } else {
      taskContent.value = contentJson
    }

    const record = await request.get(`/student/quiz/record/${materialId}`)

    if (record && record.id) {
      hasSubmitted.value = true
      mode.value = 'review'
      initialAiFeedback.value = record.aiFeedback

      if (materialType.value === '测验') {
        userAnswers.value = JSON.parse(record.userAnswers)
        myScore.value = record.score
      } else {
        try {
          const ans = JSON.parse(record.userAnswers)
          myAnswerText.value = ans.text
          myAnswerFiles.value = ans.files || []
        } catch (e) { myAnswerText.value = record.userAnswers }
      }
    }
  } catch (e) { console.error(e) }
}


// 1. 开始初始分析
const startAnalysis = () => {
  if (!hasSubmitted.value) return; // 安全检查

  if (initialAiFeedback.value) {
    pushToChat('assistant', "您好，我已根据您的作答记录，并结合老师的初步反馈为您提供了详细分析，请问您对哪个知识点有疑问？");
    return;
  }

  const prompt = "请帮我分析一下这次的作业/测验，指出我的错误并给出建议。";
  pushToChat('user', prompt);
  callAiApi();
}

// 2. 发送用户追问
const sendUserMessage = () => {
  if (!userQuery.value.trim() || analyzing.value) return
  pushToChat('user', userQuery.value)
  userQuery.value = ''
  callAiApi()
}

// 3. 调用后端 API 并处理打字机效果
const callAiApi = async () => {
  analyzing.value = true

  // 准备要发送的历史记录（去掉 isTyping 状态）
  const historyPayload = chatHistory.value.map(m => ({ role: m.role, content: m.content }))

  // 先放一个空的 AI 消息占位
  const aiMsgIndex = pushToChat('assistant', '', true)

  try {
    // 调用后端接口
    const res = await request.post('/student/quiz/chat', {
      materialId: materialId,
      history: historyPayload
    }, { timeout: 60000 })

    // 拿到完整结果后，开始打字机效果
    await typewriterEffect(aiMsgIndex, res)

  } catch (e) {
    chatHistory.value[aiMsgIndex].content = "AI 思考超时，请稍后再试。";
    chatHistory.value[aiMsgIndex].isTyping = false;
  } finally {
    analyzing.value = false;
  }
}

// 4. 打字机特效函数
const typewriterEffect = (index, fullText) => {
  return new Promise((resolve) => {
    let currentLen = 0
    const totalLen = fullText.length
    const speed = 20

    const timer = setInterval(() => {
      currentLen++
      // 检查当前元素是否存在
      if (chatHistory.value[index]) {
        chatHistory.value[index].content = fullText.substring(0, currentLen)
      }

      // 自动滚动到底部
      if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight

      if (currentLen >= totalLen) {
        clearInterval(timer)
        if (chatHistory.value[index]) {
          chatHistory.value[index].isTyping = false;
        }
        resolve()
      }
    }, speed)
  })
}

const pushToChat = (role, content, isTyping = false) => {
  chatHistory.value.push({ role, content, isTyping })
  nextTick(() => {
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  })
  return chatHistory.value.length - 1 // 返回索引
}

// === 提交逻辑 ===
const submitAssignment = async () => {
  if (!submitText.value && submitFiles.value.length === 0) return ElMessage.warning('请填写内容或上传附件')
  submitting.value = true
  const formData = new FormData()
  formData.append('materialId', materialId); formData.append('textAnswer', submitText.value)
  submitFiles.value.forEach(f => formData.append('files', f.raw))
  try {
    await request.post('/student/quiz/submit', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
    ElMessage.success('提交成功');
    window.location.reload()
  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败')
  } finally {
    submitting.value = false
  }
}

const handleSubmit = async () => {
  loading.value = true
  // 仅计算测验分数（如果不是测验，score默认为0或null）
  const score = questions.value.reduce((sum, q, i) => sum + (userAnswers.value[i]===q.answer ? q.score : 0), 0)
  const formData = new FormData()
  formData.append('materialId', materialId);
  formData.append('score', score);
  formData.append('userAnswers', JSON.stringify(userAnswers.value))
  try {
    await request.post('/student/quiz/submit', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
    ElMessage.success(`提交成功，得分: ${score} 分`);
    window.location.reload()
  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败')
  } finally {
    loading.value = false
  }
}

// 辅助函数
const handleSelectOption = (qIdx, oIdx) => { if (mode.value === 'take') userAnswers.value[qIdx] = oIdx }
const downloadFile = (path, name) => {
  if (!path) return
  // 使用 path 的最后一部分作为文件名查找
  const realName = path.split(/[\\/]/).pop()
  const url = `http://localhost:8080/uploads/${realName}`;
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', name);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link)
}
const scrollToQuestion = (i) => document.getElementById('q-'+i).scrollIntoView({behavior:'smooth'})
const getOptionClass = (qIdx, oIdx) => {
  if(mode.value==='take') return userAnswers.value[qIdx]===oIdx?'selected':'';
  const c=questions.value[qIdx].answer, u=userAnswers.value[qIdx];
  if(oIdx===c) return 'correct-bg';
  if(u===oIdx && u!==c) return 'wrong-bg';
  return ''
}
const isRightOption = (q,o) => questions.value[q].answer===o
const isWrongSelected = (q,o) => userAnswers.value[q]===o && userAnswers.value[q]!==questions.value[q].answer
const getGridClass = (i) => mode.value==='take'?(userAnswers.value[i]!==-1?'filled':''):(userAnswers.value[i]===questions.value[i].answer?'right':'error')
const totalScore = computed(() => questions.value.reduce((sum, q) => sum + q.score, 0))
</script>

<style scoped lang="scss">
.quiz-detail-container { min-height: 100vh; background-color: #f5f7fa; padding-bottom: 40px; }
.quiz-header { height: 60px; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.05); position: sticky; top: 0; z-index: 100; .inner { width: 1200px; margin: 0 auto; height: 100%; display: flex; justify-content: space-between; align-items: center; } .left { display: flex; align-items: center; gap: 15px; .title { margin: 0; font-size: 18px; color: #333; } .divider { color: #ddd; } .type-tag { font-weight: normal; } } .right { font-size: 16px; .score-value { color: #f56c6c; font-size: 24px; font-weight: bold; } } }

/* AI 聊天区域样式 */
.ai-chat-section {
  width: 100%; padding-top: 20px;
  .inner { width: 1200px; margin: 0 auto; }
}

.chat-window {
  background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e0e0e0;
  display: flex; flex-direction: column; height: 600px; /* 固定高度 */

  .chat-header {
    background: linear-gradient(135deg, #409EFF 0%, #36cfc9 100%); padding: 15px 25px; color: #fff; display: flex; justify-content: space-between; align-items: center;
    .title-box { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: bold; }
  }

  .chat-messages {
    flex: 1; padding: 25px; overflow-y: auto; background: #f9f9f9;
    .message-row {
      display: flex; margin-bottom: 20px; gap: 15px;
      &.user { flex-direction: row-reverse; }
      &.assistant { flex-direction: row; }
    }
    .avatar {
      width: 40px; height: 40px; border-radius: 50%; display: flex; justify-content: center; align-items: center; color: #fff; flex-shrink: 0;
      &.user-avatar { background: #E6A23C; }
      &:not(.user-avatar) { background: #409EFF; }
    }
    .bubble {
      max-width: 70%; padding: 12px 18px; border-radius: 12px; line-height: 1.6; font-size: 15px; position: relative; word-wrap: break-word;
    }
    .assistant .bubble { background: #fff; border: 1px solid #eee; color: #333; border-top-left-radius: 0; }
    .user .bubble { background: #409EFF; color: #fff; border-top-right-radius: 0; }

    .bubble-content { white-space: pre-wrap; font-family: sans-serif; }
    .cursor { animation: blink 1s infinite; font-weight: bold; color: #409EFF; }
  }

  .chat-input-area {
    padding: 20px; background: #fff; border-top: 1px solid #eee;
    .start-btn-box { text-align: center; }
    .input-row { display: flex; gap: 10px; }
  }
}

@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

/* 其他样式保持 */
.quiz-body, .assignment-body { width: 1200px; margin: 20px auto; display: flex; gap: 20px; }
.question-paper, .task-card, .answer-card { flex: 1; background: #fff; padding: 30px; border-radius: 4px; }
.side-panel { width: 280px; position: sticky; top: 80px; }
.panel-card { background: #fff; padding: 20px; border-radius: 4px; }
.card-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin: 20px 0; }
.grid-item { height: 35px; display: flex; justify-content: center; align-items: center; border: 1px solid #ddd; cursor: pointer; border-radius: 4px; &.filled { background: #409EFF; color: #fff; } &.right { background: #67C23A; color: #fff; } &.error { background: #F56C6C; color: #fff; } }
.q-title { font-size: 16px; margin-bottom: 20px; .q-no { font-size: 24px; color: #409EFF; margin-right: 10px; } .q-type { background: #f0f2f5; font-size: 12px; padding: 2px 6px; margin-right: 10px; } }
.option-row { padding: 15px; border: 1px solid #e4e7ed; border-radius: 4px; margin-bottom: 10px; cursor: pointer; display: flex; align-items: center; &:hover { background: #fafafa; } &.selected { border-color: #409EFF; background: #ecf5ff; } &.correct-bg { border-color: #67C23A; background: #f0f9eb; } &.wrong-bg { border-color: #F56C6C; background: #fef0f0; } .opt-label { margin-right: 15px; font-weight: bold; } .result-icon { margin-left: auto; font-size: 18px; } }
.analysis-box { background: #f8f9fa; padding: 15px; border-radius: 6px; margin-top: 15px; .result-status.correct { color: #67C23A; } .result-status.wrong { color: #F56C6C; } }
.submitted-content { .answer-section { margin-bottom: 20px; .label { font-weight: bold; margin-bottom: 10px; } .text-display { background: #f9f9f9; padding: 15px; border-radius: 4px; min-height: 100px; } } }

/* Assignment Specific Styles */
.task-card, .answer-card { flex: 1; min-height: 400px; }
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 1px solid #eee; padding-bottom: 10px; h3 { margin: 0; } }
.meta-info { display: flex; align-items: center; gap: 15px; font-size: 14px; color: #909399; .status-tag { padding: 4px 8px; border-radius: 4px; &.done { background: #67C23A; color: #fff; } &.todo { background: #E6A23C; color: #fff; } } }
.task-desc-content { .desc-text { white-space: pre-wrap; margin-bottom: 20px; font-size: 15px; color: #333; } .teacher-attachment { padding: 10px; border: 1px dashed #eee; border-radius: 4px; .attach-item { display: flex; align-items: center; gap: 10px; font-size: 14px; } } }
.submit-bar { display: flex; justify-content: flex-end; margin-top: 20px; gap: 10px; }
.answer-card .card-header { border-bottom: 1px solid #eee; }
.file-list { display: flex; flex-direction: column; gap: 8px; }
.file-item-display { display: flex; align-items: center; gap: 10px; background: #f5f5f5; padding: 8px; border-radius: 4px; font-size: 14px; }

</style>