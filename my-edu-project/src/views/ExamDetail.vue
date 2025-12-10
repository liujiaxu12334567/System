<template>
  <div class="exam-detail-container" v-loading="loading">
    <header class="exam-header">
      <div class="inner">
        <div class="left">
          <el-button link @click="$router.go(-1)">
            <el-icon><ArrowLeft /></el-icon> 返回列表
          </el-button>
          <span class="divider">|</span>
          <h2 class="title">{{ examInfo.title }}</h2>
          <el-tag type="danger" class="type-tag">正式考试</el-tag>
        </div>
        <div class="right">
          <span class="cheat-label">切屏次数：</span>
          <span class="cheat-count">{{ cheatCount }}</span>
          <span class="timer-label"> | 剩余时间：</span>
          <span class="timer-value" :class="{ warning: timeLeft < 600 }">{{ formatTime(timeLeft) }}</span>
        </div>
      </div>
    </header>

    <div v-if="hasSubmitted" class="submitted-view">
      <el-result
          icon="success"
          title="试卷已提交"
          :sub-title="'本次考试切屏次数为: ' + submittedCheatCount + ' 次'"
      >
        <template #extra>
          <el-button type="primary" @click="$router.go(-1)">返回考试列表</el-button>
        </template>
      </el-result>
    </div>

    <div v-else class="exam-body">
      <div class="question-paper">
        <el-alert title="考试期间请勿切换浏览器标签页或离开当前窗口，否则将被记录作弊次数！切屏记录将发送给监考老师。" type="warning" show-icon :closable="false" style="margin-bottom: 20px;" />

        <div v-for="(q, index) in questions" :key="index" class="question-item">
          <div class="q-title">
            <span class="q-no">{{ index + 1 }}</span>
            <span class="q-type">单选题</span>
            <span class="q-text">{{ q.title }}</span>
          </div>
          <el-radio-group v-model="userAnswers[index]">
            <el-radio v-for="(opt, oIdx) in q.options" :key="oIdx" :label="oIdx" style="display: block; margin-bottom: 10px;">
              {{ String.fromCharCode(65+oIdx) }}. {{ opt }}
            </el-radio>
          </el-radio-group>
        </div>
      </div>

      <div class="side-panel">
        <el-card class="action-card">
          <p>考试时长: {{ examInfo.duration }} 分钟</p>
          <p>截止时间: {{ examInfo.deadline }}</p>
          <el-button type="danger" size="large" @click="submitExam" style="width: 100%; margin-top: 20px;">
            提交试卷
          </el-button>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const examId = route.params.examId

const loading = ref(true)
const hasSubmitted = ref(false)
const submittedCheatCount = ref(0)
const cheatCount = ref(0) // 学生当前的切屏次数
let visibilityChangeTimer = null // 定时器用于检测切屏时间间隔

const examInfo = reactive({
  title: '加载中...',
  duration: 60,
  deadline: '',
  content: ''
})
const questions = ref([])
const userAnswers = ref([])
const timeLeft = ref(0)
let timerInterval = null

onMounted(() => {
  fetchExamData()
  // 【核心】监听窗口焦点变化事件
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (timerInterval) clearInterval(timerInterval)
  if (visibilityChangeTimer) clearTimeout(visibilityChangeTimer)
})

// === 数据加载与考试计时 ===

const fetchExamData = async () => {
  try {
    const examDetails = await request.get(`/student/exam/record/${examId}`)

    // 如果返回的是记录，则说明已提交
    if (examDetails.userId) {
      hasSubmitted.value = true
      submittedCheatCount.value = examDetails.cheatCount
      loading.value = false
      return
    }

    // 如果返回的是考试信息（未提交状态）
    Object.assign(examInfo, examDetails)

    try {
      questions.value = JSON.parse(examDetails.content).questions || []
      userAnswers.value = new Array(questions.value.length).fill(-1)
    } catch(e) {
      questions.value = []
    }

    // 开始计时
    timeLeft.value = examInfo.duration * 60
    startTimer()

  } catch(e) {
    ElMessage.error('考试加载失败，请检查考试ID和网络连接。')
    console.error(e)
  } finally {
    loading.value = false
  }
}

const startTimer = () => {
  if (timerInterval) clearInterval(timerInterval)
  timerInterval = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--
    } else {
      clearInterval(timerInterval)
      ElMessageBox.alert('考试时间到！系统将自动提交试卷。', '时间到', {
        confirmButtonText: '确定',
        callback: () => submitExam()
      })
    }
  }, 1000)
}

// === 防作弊切屏检测 ===

const handleVisibilityChange = () => {
  if (document.hidden) {
    // 离开页面
    visibilityChangeTimer = setTimeout(() => {
      // 离开超过 2 秒，记录切屏次数
      cheatCount.value++
      ElMessage.error(`警告：检测到切屏行为，已记录作弊次数 ${cheatCount.value} 次！`);
    }, 2000) // 容忍 2 秒
  } else {
    // 返回页面
    if (visibilityChangeTimer) {
      clearTimeout(visibilityChangeTimer)
      visibilityChangeTimer = null
    }
  }
}

// === 提交逻辑 ===

const submitExam = async () => {
  if (hasSubmitted.value) return

  const confirmMessage = cheatCount.value > 0
      ? `您确定提交试卷吗？本次考试切屏次数为 ${cheatCount.value} 次，记录将发送给老师。`
      : '您确定提交试卷吗？';

  await ElMessageBox.confirm(confirmMessage, '确认提交', {
    confirmButtonText: '立即提交',
    cancelButtonText: '取消',
    type: 'warning'
  })

  loading.value = true

  const payload = {
    examId: examId,
    score: 0, // 假设后端计算
    userAnswers: JSON.stringify(userAnswers.value),
    cheatCount: cheatCount.value
  }

  try {
    const res = await request.post('/student/exam/submit', payload)
    ElMessage.success(res || '考试提交成功！')
    hasSubmitted.value = true
    submittedCheatCount.value = cheatCount.value
    if (timerInterval) clearInterval(timerInterval)
  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败')
  } finally {
    loading.value = false
  }
}

// === 辅助函数 ===

const formatTime = (seconds) => {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
.exam-detail-container { min-height: 100vh; background-color: #f5f7fa; padding-bottom: 40px; }
.exam-header {
  height: 60px; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.05); position: sticky; top: 0; z-index: 100;
  .inner { width: 1200px; margin: 0 auto; height: 100%; display: flex; justify-content: space-between; align-items: center; }
  .left { display: flex; align-items: center; gap: 15px; .title { margin: 0; font-size: 18px; color: #333; } .divider { color: #ddd; } .type-tag { font-weight: normal; } }
  .right {
    font-size: 16px;
    .cheat-count { color: #F56C6C; font-size: 20px; font-weight: bold; }
    .timer-value { color: #606266; font-weight: bold; font-size: 18px; }
    .timer-value.warning { color: #E6A23C; }
  }
}

.exam-body { width: 1200px; margin: 20px auto; display: flex; gap: 20px; align-items: flex-start; }
.question-paper { flex: 1; background: #fff; border-radius: 4px; padding: 40px; }
.side-panel { width: 280px; position: sticky; top: 80px; }
.action-card { padding: 10px; }

/* 题目样式 */
.question-item { margin-bottom: 30px; border-bottom: 1px dashed #eee; padding-bottom: 20px; }
.q-title {
  font-size: 16px; margin-bottom: 15px;
  .q-no { font-size: 20px; color: #409EFF; margin-right: 10px; }
  .q-type { background: #f0f2f5; font-size: 12px; padding: 2px 6px; margin-right: 10px; }
  .q-text { font-weight: bold; }
}

.submitted-view {
  width: 1200px;
  margin: 40px auto;
  background: #fff;
  padding: 50px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}
</style>