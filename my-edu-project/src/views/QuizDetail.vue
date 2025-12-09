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
            <div
                v-for="(opt, oIdx) in q.options"
                :key="oIdx"
                class="option-row"
                :class="getOptionClass(index, oIdx)"
                @click="handleSelectOption(index, oIdx)"
            >
              <span class="opt-label">{{ String.fromCharCode(65+oIdx) }}</span>
              <span class="opt-content">{{ opt }}</span>
              <div v-if="mode === 'review'" class="result-icon">
                <el-icon v-if="isRightOption(index, oIdx)" color="#67C23A"><Check /></el-icon>
                <el-icon v-else-if="isWrongSelected(index, oIdx)" color="#F56C6C"><Close /></el-icon>
              </div>
            </div>
          </div>

          <div v-if="mode === 'review'" class="analysis-box">
            <div class="result-status" :class="userAnswers[index] === q.answer ? 'correct' : 'wrong'">
              {{ userAnswers[index] === q.answer ? '回答正确' : '回答错误' }}
            </div>
            <div class="result-detail">
              <span>正确答案：{{ String.fromCharCode(65+q.answer) }}</span>
              <span class="sep">|</span>
              <span>您的答案：{{ userAnswers[index] !== -1 ? String.fromCharCode(65+userAnswers[index]) : '未作答' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="side-panel">
        <div class="panel-card">
          <div class="panel-header">
            <h3>{{ mode === 'take' ? '答题卡' : '成绩单' }}</h3>
            <span class="sub-text">共 {{ questions.length }} 题</span>
          </div>
          <div v-if="mode === 'review'" class="score-display">
            <div class="big-score">{{ myScore }}</div>
            <div class="score-desc">卷面总分</div>
          </div>
          <div class="card-grid">
            <div v-for="(q, index) in questions" :key="index" class="grid-item" :class="getGridClass(index)" @click="scrollToQuestion(index)">
              {{ index + 1 }}
            </div>
          </div>
          <div class="action-area">
            <el-button v-if="mode === 'take'" type="primary" size="large" class="submit-btn" @click="handleSubmit">提交试卷</el-button>
            <el-button v-else size="large" class="submit-btn" @click="$router.go(-1)">返回列表</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="assignment-body">
      <div class="task-card">
        <div class="task-header">
          <h3>作业内容</h3>
          <div class="meta-info">
            <span>截止时间：{{ taskContent.deadline || '无限制' }}</span>
            <span class="status-tag" :class="mode === 'review' ? 'done' : 'todo'">{{ mode === 'review' ? '已完成' : '进行中' }}</span>
          </div>
        </div>

        <div class="task-desc-content">
          <div class="desc-text">{{ taskContent.text }}</div>

          <div class="teacher-attachment" v-if="taskFilePath">
            <div class="attach-label"><el-icon><Paperclip /></el-icon> 附件资料：</div>
            <div class="attach-item">
              <span>{{ taskFileName }}</span>
              <el-button link type="primary" size="small" @click="downloadFile(taskFilePath, taskFileName)">下载附件</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="answer-card">
        <div class="card-header">
          <h3>我的作答</h3>
        </div>

        <div v-if="mode === 'review'" class="submitted-content">
          <el-alert title="作业已提交" type="success" :closable="false" show-icon style="margin-bottom: 20px" />

          <div class="answer-section">
            <div class="label">文本内容：</div>
            <div class="text-display">{{ myAnswerText || '（未填写文本）' }}</div>
          </div>

          <div class="answer-section" v-if="myAnswerFiles.length > 0">
            <div class="label">提交的附件：</div>
            <div class="file-list">
              <div v-for="(file, i) in myAnswerFiles" :key="i" class="file-item-display">
                <el-icon><Document /></el-icon> 附件 {{ i + 1 }}
                <el-button link type="primary" @click="downloadFile(file, '我的作业_'+(i+1))">下载</el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="edit-content">
          <el-form label-position="top">
            <el-form-item label="正文内容">
              <el-input
                  v-model="submitText"
                  type="textarea"
                  :rows="10"
                  placeholder="请输入作业内容..."
                  class="custom-textarea"
              />
            </el-form-item>

            <el-form-item label="上传附件 (支持 Word, PDF, ZIP 等)">
              <el-upload
                  class="upload-demo"
                  action="#"
                  :auto-upload="false"
                  :on-change="(f, l) => submitFiles = l"
                  :on-remove="(f, l) => submitFiles = l"
                  multiple
                  :limit="5"
              >
                <el-button type="primary" plain icon="Upload">点击上传附件</el-button>
                <template #tip>
                  <div class="el-upload__tip">单个文件不超过 50MB，最多上传 5 个文件</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-form>

          <div class="submit-bar">
            <el-button size="large" @click="$router.go(-1)">取消</el-button>
            <el-button type="primary" size="large" @click="submitAssignment" :loading="submitting">提交作业</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Close, Paperclip, Document, Upload } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const { courseId, materialId } = route.params
const mode = ref(route.query.mode || 'take') // 'take' | 'review'

const loading = ref(true)
const submitting = ref(false)
const quizTitle = ref('')
const materialType = ref('')

// 测验数据
const questions = ref([])
const userAnswers = ref([])
const myScore = ref(0)

// 作业数据
const taskContent = ref({ text: '', deadline: '' })
const taskFilePath = ref('')
const taskFileName = ref('')
// 提交数据
const submitText = ref('')
const submitFiles = ref([])
// 回显数据
const myAnswerText = ref('')
const myAnswerFiles = ref([])

onMounted(async () => {
  await loadData()
  loading.value = false
})

const loadData = async () => {
  try {
    // 1. 获取题目信息
    const allMaterials = await request.get(`/student/course/${courseId}/materials`)
    const target = allMaterials.find(m => m.id == materialId)

    if (!target) {
      ElMessage.error('任务不存在')
      return
    }

    materialType.value = target.type
    quizTitle.value = target.title || target.fileName // 优先使用标题
    taskFilePath.value = target.filePath // 老师的附件路径
    taskFileName.value = target.fileName // 老师的附件名

    // 解析 content JSON
    let contentJson = {}
    try {
      contentJson = JSON.parse(target.content)
    } catch(e) {
      // 兼容旧数据的纯文本
      contentJson = { text: target.content, deadline: '' }
    }

    if (target.type === '测验') {
      questions.value = contentJson.questions || []
      if (mode.value === 'take') userAnswers.value = new Array(questions.value.length).fill(-1)
    } else {
      // 作业
      taskContent.value = contentJson // { text: "...", deadline: "..." }
    }

    // 2. 获取答题记录
    const record = await request.get(`/student/quiz/record/${materialId}`)
    if (record && record.id) {
      mode.value = 'review' // 有记录则强制进入查看模式

      if (target.type === '测验') {
        userAnswers.value = JSON.parse(record.userAnswers)
        myScore.value = record.score
      } else {
        // 作业回显解析
        try {
          const ans = JSON.parse(record.userAnswers)
          myAnswerText.value = ans.text
          myAnswerFiles.value = ans.files || []
        } catch (e) {
          myAnswerText.value = record.userAnswers
        }
      }
    }

  } catch (e) { console.error(e) }
}

// === 作业提交逻辑 ===
const submitAssignment = async () => {
  if (!submitText.value && submitFiles.value.length === 0) {
    return ElMessage.warning('请至少输入内容或上传一个附件')
  }

  submitting.value = true
  const formData = new FormData()
  formData.append('materialId', materialId)
  formData.append('textAnswer', submitText.value)
  // 添加文件
  submitFiles.value.forEach(f => {
    formData.append('files', f.raw)
  })

  try {
    await request.post('/student/quiz/submit', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('作业提交成功！')
    // 重新加载页面以进入 review 模式
    window.location.reload()
  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败')
  } finally {
    submitting.value = false
  }
}

// === 测验提交逻辑 ===
const handleSelectOption = (qIdx, oIdx) => { if (mode.value === 'take') userAnswers.value[qIdx] = oIdx }
const handleSubmit = () => {
  // 简单计算分数 (前端演示用，实际可后端算)
  const score = questions.value.reduce((sum, q, i) => sum + (userAnswers.value[i]===q.answer ? q.score : 0), 0)

  request.post('/student/quiz/submit', { // 这里 Content-Type 是 JSON
    materialId: materialId,
    score: score,
    userAnswers: JSON.stringify(userAnswers.value)
  }).then(() => {
    ElMessage.success(`提交成功！得分：${score}`)
    window.location.reload()
  })
}

// === 通用 ===
const downloadFile = (path, name) => {
  if (!path) return
  const url = `http://localhost:8080/uploads/${path.split(/[\\/]/).pop()}`
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', name)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const scrollToQuestion = (i) => document.getElementById('q-'+i).scrollIntoView({behavior:'smooth'})

// 样式类
const getOptionClass = (qIdx, oIdx) => {
  if(mode.value==='take') return userAnswers.value[qIdx]===oIdx?'selected':''
  const c=questions.value[qIdx].answer, u=userAnswers.value[qIdx]
  if(oIdx===c) return 'correct-bg'; if(u===oIdx && u!==c) return 'wrong-bg'; return ''
}
const isRightOption = (q,o) => questions.value[q].answer===o
const isWrongSelected = (q,o) => userAnswers.value[q]===o && userAnswers.value[q]!==questions.value[q].answer
const getGridClass = (i) => mode.value==='take'?(userAnswers.value[i]!==-1?'filled':''):(userAnswers.value[i]===questions.value[i].answer?'right':'error')
const totalScore = computed(() => questions.value.reduce((sum, q) => sum + q.score, 0))

</script>

<style scoped lang="scss">
.quiz-detail-container { min-height: 100vh; background-color: #f5f7fa; padding-bottom: 40px; }
.quiz-header { height: 60px; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.05); position: sticky; top: 0; z-index: 100; .inner { width: 1200px; margin: 0 auto; height: 100%; display: flex; justify-content: space-between; align-items: center; } .left { display: flex; align-items: center; gap: 15px; .title { margin: 0; font-size: 18px; color: #333; } .divider { color: #ddd; } .type-tag { font-weight: normal; } } .right { font-size: 16px; .score-value { color: #f56c6c; font-size: 24px; font-weight: bold; } } }

/* 测验样式 */
.quiz-body { width: 1200px; margin: 20px auto; display: flex; gap: 20px; align-items: flex-start; }
.question-paper { flex: 1; background: #fff; border-radius: 4px; padding: 40px; }
.question-item { margin-bottom: 40px; border-bottom: 1px dashed #eee; padding-bottom: 30px; }
.q-title { font-size: 16px; margin-bottom: 20px; .q-no { font-size: 24px; color: #409EFF; margin-right: 10px; } .q-type { background: #f0f2f5; font-size: 12px; padding: 2px 6px; margin-right: 10px; } }
.option-row { padding: 15px; border: 1px solid #e4e7ed; border-radius: 4px; margin-bottom: 10px; cursor: pointer; display: flex; align-items: center; &:hover { background: #fafafa; } &.selected { border-color: #409EFF; background: #ecf5ff; } &.correct-bg { border-color: #67C23A; background: #f0f9eb; } &.wrong-bg { border-color: #F56C6C; background: #fef0f0; } .opt-label { margin-right: 15px; font-weight: bold; } .result-icon { margin-left: auto; font-size: 18px; } }
.analysis-box { background: #f8f9fa; padding: 15px; border-radius: 6px; margin-top: 15px; .result-status.correct { color: #67C23A; } .result-status.wrong { color: #F56C6C; } }
.side-panel { width: 280px; position: sticky; top: 80px; }
.panel-card { background: #fff; padding: 20px; border-radius: 4px; }
.card-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin: 20px 0; }
.grid-item { height: 35px; display: flex; justify-content: center; align-items: center; border: 1px solid #ddd; cursor: pointer; border-radius: 4px; &.filled { background: #409EFF; color: #fff; } &.right { background: #67C23A; color: #fff; } &.error { background: #F56C6C; color: #fff; } }

/* 作业样式 */
.assignment-body { width: 1200px; margin: 20px auto; }
.task-card, .answer-card { background: #fff; padding: 30px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); }
.task-header, .card-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #eee; padding-bottom: 15px; margin-bottom: 20px; h3 { margin: 0; font-size: 18px; border-left: 4px solid #409EFF; padding-left: 10px; } }
.meta-info { color: #666; font-size: 14px; display: flex; gap: 15px; align-items: center; .status-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; &.todo { background: #E6A23C; color: #fff; } &.done { background: #67C23A; color: #fff; } } }
.task-desc-content { font-size: 15px; line-height: 1.8; color: #333; .desc-text { white-space: pre-wrap; margin-bottom: 20px; } }
.teacher-attachment { background: #f5f7fa; padding: 10px 15px; border-radius: 4px; display: flex; align-items: center; gap: 10px; .attach-label { color: #909399; font-size: 13px; display: flex; align-items: center; } }
.submitted-content { .answer-section { margin-bottom: 20px; .label { font-weight: bold; margin-bottom: 10px; } .text-display { background: #f9f9f9; padding: 15px; border-radius: 4px; min-height: 100px; } .file-list { display: flex; flex-direction: column; gap: 8px; } } }
.submit-bar { margin-top: 30px; text-align: center; }
</style>