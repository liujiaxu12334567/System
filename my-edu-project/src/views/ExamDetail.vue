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
      <div v-if="screenLocked" class="anti-cheat-overlay">
        <div class="overlay-content">
          <el-icon color="#F56C6C" :size="48"><WarningFilled /></el-icon>
          <h3>考试违规警告</h3>
          <p>由于检测到违规行为，屏幕已暂时锁定，请点击警告框上的“确定”解除。</p>
        </div>
      </div>

      <div class="question-paper">
        <el-alert title="考试期间请勿切换浏览器标签页或离开当前窗口，否则将被记录作弊次数！切屏记录将发送给监考老师。" type="warning" show-icon :closable="false" style="margin-bottom: 20px;" />

        <div v-for="(q, index) in questions" :key="index" class="question-item">
          <div class="q-header">
            <div class="q-index-tag">
              <span class="q-no">{{ index + 1 }}</span>
              <span class="q-type">单选题</span>
            </div>
            <div class="q-text-content">
              {{ q.title }}
            </div>
          </div>

          <el-radio-group v-model="userAnswers[index]" :disabled="screenLocked" class="option-list">
            <el-radio v-for="(opt, oIdx) in q.options" :key="oIdx" :label="oIdx" class="custom-radio-item">
              <span class="option-label">{{ String.fromCharCode(65+oIdx) }}</span>
              <span class="option-text">{{ opt }}</span>
            </el-radio>
          </el-radio-group>
        </div>
      </div>

      <div class="side-panel">
        <el-card class="action-card" shadow="hover">
          <div class="card-header-title">
            <el-icon><Tickets /></el-icon> <span>答题信息</span>
          </div>
          <div class="side-info-row">
            <span class="label">考试时长:</span>
            <span class="value">{{ examInfo.duration }} 分钟</span>
          </div>
          <div class="side-info-row">
            <span class="label">截止时间:</span>
            <span class="value">{{ examInfo.deadline }}</span>
          </div>
        </el-card>

        <el-card class="action-card score-card" shadow="hover">
          <div class="card-header-title">
            <el-icon><List /></el-icon> <span>答题卡</span>
          </div>
          <div class="card-grid">
            <div v-for="(q, index) in questions" :key="index" class="grid-item" :class="{ answered: userAnswers[index] !== -1 }">
              {{ index + 1 }}
            </div>
          </div>

          <el-button type="danger" size="large" @click="submitExam" :disabled="screenLocked" style="width: 100%; margin-top: 20px;">
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
import { ArrowLeft, WarningFilled, Tickets, List } from '@element-plus/icons-vue' // 引入 List 和 Tickets

const route = useRoute()
const router = useRouter()
const examId = route.params.examId

const loading = ref(true)
const hasSubmitted = ref(false)
const submittedCheatCount = ref(0)
const cheatCount = ref(0)
const screenLocked = ref(false);
let visibilityChangeTimer = null
let lastVisibilityChangeTime = 0;

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
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (timerInterval) clearInterval(timerInterval)
  if (visibilityChangeTimer) clearTimeout(visibilityChangeTimer)
})

// === 数据加载与考试计时 (逻辑不变) ===

const fetchExamData = async () => {
  try {
    const examDetails = await request.get(`/student/exam/record/${examId}`)

    if (examDetails.userId) {
      hasSubmitted.value = true
      submittedCheatCount.value = examDetails.cheatCount
      loading.value = false
      return
    }

    Object.assign(examInfo, examDetails)

    try {
      questions.value = JSON.parse(examDetails.content).questions || []
      userAnswers.value = new Array(questions.value.length).fill(-1)
    } catch(e) {
      questions.value = []
    }

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

// === 防作弊切屏检测 - 增强提示版本 (逻辑不变) ===

const handleVisibilityChange = () => {
  if (hasSubmitted.value) return;

  if (document.hidden) {
    console.log(`[Exam Anti-Cheat] Window hidden. Starting 2000ms timer.`);
    lastVisibilityChangeTime = Date.now();

    visibilityChangeTimer = setTimeout(() => {
      const timeElapsed = Date.now() - lastVisibilityChangeTime;

      if (document.hidden && timeElapsed >= 2000) {
        cheatCount.value++;
        console.error(`[Exam Anti-Cheat] CHEAT DETECTED. Count: ${cheatCount.value}. Time Elapsed: ${timeElapsed}ms`);

        screenLocked.value = true;

        ElMessageBox.alert(`警告：系统检测到您已切换浏览器标签页或离开考试窗口超过 2 秒，作弊次数已记录 ${cheatCount.value} 次！\n\n请立即返回作答！`, '违规操作警告', {
          confirmButtonText: '确定 (解除屏幕锁定)',
          type: 'error',
          callback: () => {
            screenLocked.value = false;
          }
        });
      }
    }, 2000)
  } else {
    if (visibilityChangeTimer) {
      clearTimeout(visibilityChangeTimer)
      visibilityChangeTimer = null
      console.log('[Exam Anti-Cheat] Window visible. Timer cleared.');
    }
  }
}

// === 提交逻辑 (逻辑不变) ===

const submitExam = async () => {
  if (hasSubmitted.value) return
  if (screenLocked.value) {
    ElMessage.warning('请先解除屏幕锁定再提交试卷。');
    return;
  }

  const confirmMessage = cheatCount.value > 0
      ? `您确定提交试卷吗？本次考试切屏次数为 ${cheatCount.value} 次，记录将发送给老师。`
      : '您确定提交试卷吗？';

  try {
    await ElMessageBox.confirm(confirmMessage, '确认提交', {
      confirmButtonText: '立即提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }

  loading.value = true

  const payload = {
    examId: examId,
    score: calculateScore(),
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

// === 辅助函数 (逻辑不变) ===

const calculateScore = () => {
  if (!Array.isArray(questions.value) || !Array.isArray(userAnswers.value)) return 0
  let total = 0
  for (let i = 0; i < questions.value.length; i++) {
    const q = questions.value[i] || {}
    const expected = Number(q.answer)
    const perScore = Number(q.score ?? 0)
    const actual = Number(userAnswers.value[i])
    if (Number.isFinite(expected) && Number.isFinite(actual) && actual === expected) {
      total += Number.isFinite(perScore) ? perScore : 0
    }
  }
  if (!Number.isFinite(total)) return 0
  return Math.max(0, Math.round(total))
}

const formatTime = (seconds) => {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap');

$primary-color: #409EFF;
$danger-color: #F56C6C;
$warning-color: #E6A23C;
$bg-light: #F7F8FA;
$bg-white: #FFFFFF;
$text-dark: #303133;
$text-secondary: #909399;

.exam-detail-container {
  min-height: 100vh;
  background-color: $bg-light;
  padding-bottom: 40px;
  font-family: 'Roboto', "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

// ================= 头部样式优化 =================
.exam-header {
  height: 60px;
  background: $bg-white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
  .inner {
    width: 1200px;
    margin: 0 auto;
    height: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .left {
    display: flex;
    align-items: center;
    gap: 15px;
    .el-button { color: $text-secondary; font-weight: 500; }
    .title {
      margin: 0;
      font-size: 20px;
      color: $text-dark;
      font-weight: 600;
    }
    .divider { color: #E4E7ED; }
    .type-tag { font-weight: normal; }
  }
  .right {
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 10px;
    .cheat-label, .timer-label { color: $text-dark; font-weight: 500; }
    .cheat-count {
      color: $danger-color;
      font-size: 20px;
      font-weight: 700;
      // 突出作弊次数
      background: #FEE;
      padding: 2px 8px;
      border-radius: 4px;
      border: 1px solid $danger-color;
    }
    .timer-value {
      color: #606266;
      font-weight: 700;
      font-size: 18px;
      padding-left: 5px;
    }
    .timer-value.warning { color: $warning-color; }
  }
}

// ================= 主体布局优化 =================
.exam-body {
  width: 1200px;
  margin: 20px auto;
  display: flex;
  gap: 20px;
  align-items: flex-start;
  position: relative;
}
.question-paper {
  flex: 1;
  background: $bg-white;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.side-panel {
  width: 300px; // 略微加宽侧边栏
  position: sticky;
  top: 80px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.action-card {
  border: 1px solid #EBEEF5;
  border-radius: 8px;
  .card-header-title {
    display: flex;
    align-items: center;
    font-weight: 600;
    color: $primary-color;
    font-size: 16px;
    margin-bottom: 15px;
    .el-icon { margin-right: 8px; font-size: 18px; }
  }
  .side-info-row {
    display: flex;
    justify-content: space-between;
    font-size: 14px;
    margin-bottom: 8px;
    .label { color: $text-secondary; }
    .value { color: $text-dark; font-weight: 500; }
  }
}
.score-card {
  padding-top: 10px;
}

// ================= 题目样式优化 =================
.question-item {
  margin-bottom: 30px;
  padding: 20px;
  border: 1px solid #E4E7ED;
  border-radius: 8px;
  transition: box-shadow 0.3s;
  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }
}
.q-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20px;
}
.q-index-tag {
  flex-shrink: 0;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: $primary-color;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  margin-right: 20px;
  color: $bg-white;
  box-shadow: 0 4px 10px rgba($primary-color, 0.3);
  .q-no {
    font-size: 24px;
    font-weight: 700;
    line-height: 1;
  }
  .q-type {
    font-size: 10px;
    line-height: 1;
    opacity: 0.8;
  }
}
.q-text-content {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: $text-dark;
  line-height: 1.6;
  padding-top: 10px;
}

// 选项列表样式
.option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .custom-radio-item {
    padding: 12px 15px;
    border: 1px solid #F0F2F5;
    border-radius: 6px;
    width: 100%;
    margin-left: 0; /* 移除默认左边距 */

    // 覆盖 Element Plus 默认样式，使其更像一个卡片
    :deep(.el-radio__label) {
      display: flex;
      align-items: center;
      width: 100%;
      padding-left: 10px;
    }

    // 选中时的样式
    &:hover {
      border-color: #C6E2FF;
      background-color: #F5F7FA;
    }

    .option-label {
      font-weight: 700;
      color: $primary-color;
      min-width: 20px;
      margin-right: 15px;
    }
    .option-text {
      color: $text-dark;
      font-size: 15px;
      line-height: 1.5;
    }
  }
}

// 答题卡网格
.card-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin: 10px 0;
}
.grid-item {
  height: 40px;
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px solid #E4E7ED;
  cursor: pointer;
  border-radius: 6px;
  font-size: 14px;
  background-color: #F0F2F5;
  color: $text-secondary;
  transition: all 0.2s;
  &.answered {
    background: $primary-color;
    color: $bg-white;
    border-color: $primary-color;
  }
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
  }
}

// 【防作弊：遮罩层样式】
.anti-cheat-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.95);
  z-index: 10;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: all;
  border-radius: 8px;
}
.overlay-content {
  background: #fff;
  padding: 40px 50px;
  border-radius: 12px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
  text-align: center;
  color: $text-dark;
  border-top: 5px solid $danger-color;
  h3 {
    color: $danger-color;
    margin: 15px 0 10px;
    font-size: 24px;
    font-weight: 700;
  }
  p {
    margin: 0;
    color: $text-secondary;
    font-size: 15px;
  }
}
</style>
