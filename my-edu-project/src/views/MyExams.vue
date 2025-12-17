<template>
  <div class="my-exams-container">
    <StudentHeader />

    <main class="main-content">
      <div class="page-title">
        <el-button link type="primary" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <span class="title-text">我的考试</span>
      </div>

      <el-card class="filter-panel" shadow="never">
        <div class="filter-row">
          <el-input v-model="keyword" placeholder="搜索考试标题/课程名" :prefix-icon="Search" clearable style="width: 260px" />
          <el-select v-model="answerFilter" placeholder="答题状态" clearable style="width: 140px; margin-left: 12px">
            <el-option label="未答" value="未答" />
            <el-option label="已答" value="已答" />
          </el-select>
          <el-select v-model="timeFilter" placeholder="考试状态" clearable style="width: 140px; margin-left: 12px">
            <el-option label="未开始" value="未开始" />
            <el-option label="进行中" value="进行中" />
            <el-option label="已结束" value="已结束" />
          </el-select>

          <el-button style="margin-left: auto" @click="fetchExams" :loading="loading">刷新</el-button>
        </div>
      </el-card>

      <el-table v-loading="loading" :data="filteredExams" border style="width: 100%; margin-top: 16px">
        <el-table-column prop="courseName" label="课程" min-width="160" />
        <el-table-column prop="title" label="考试" min-width="220" />
        <el-table-column label="答题状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.submitted ? 'success' : 'danger'" effect="light">
              {{ row.submitted ? '已答' : '未答' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="timeStatus" label="考试状态" width="110" />
        <el-table-column prop="deadline" label="截止时间" min-width="170" />
        <el-table-column label="成绩" width="90">
          <template #default="{ row }">
            <span>{{ row.submitted ? (row.score ?? '-') : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goToExam(row.examId)">
              {{ row.submitted ? '查看' : '去考试' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && filteredExams.length === 0" description="暂无考试" :image-size="120" style="margin-top: 24px" />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ArrowLeft, Search } from '@element-plus/icons-vue'
import StudentHeader from '@/components/StudentHeader.vue'

const router = useRouter()
const userInfo = ref({})
const loading = ref(false)
const exams = ref([])

const keyword = ref('')
const answerFilter = ref('')
const timeFilter = ref('')

onMounted(() => {
  try {
    userInfo.value = JSON.parse(localStorage.getItem('userInfo') || '{}')
  } catch (e) {
    userInfo.value = {}
  }
  fetchExams()
})

const fetchExams = async () => {
  loading.value = true
  try {
    const res = await request.get('/student/my-exams')
    exams.value = res || []
  } finally {
    loading.value = false
  }
}

const filteredExams = computed(() => {
  let list = exams.value || []

  if (keyword.value) {
    const k = keyword.value.trim()
    if (k) {
      list = list.filter((e) => String(e.title || '').includes(k) || String(e.courseName || '').includes(k))
    }
  }

  if (answerFilter.value) {
    list = list.filter((e) => (e.submitted ? '已答' : '未答') === answerFilter.value)
  }

  if (timeFilter.value) {
    list = list.filter((e) => String(e.timeStatus || '') === timeFilter.value)
  }

  return list
})

const goToExam = (examId) => {
  if (!examId) return
  router.push({ name: 'ExamDetail', params: { examId } })
}
</script>

<style scoped lang="scss">
.my-exams-container {
  min-height: 100vh;
  background: #f5f7fb;
}

.neu-header {
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;

  .header-inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 14px 18px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .left-section {
    display: flex;
    align-items: center;
    gap: 22px;
  }

  .logo {
    font-size: 20px;
    font-weight: 800;
    cursor: pointer;
    color: #245fe6;
    margin: 0;
  }

  .nav-links {
    display: flex;
    gap: 14px;
    a {
      color: #606266;
      cursor: pointer;
      text-decoration: none;
      &.active {
        color: #245fe6;
        font-weight: 700;
      }
    }
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #606266;
    .username {
      font-size: 14px;
      font-weight: 600;
    }
  }
}

.main-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 18px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  .title-text {
    font-size: 18px;
    font-weight: 800;
    color: #303133;
  }
}

.filter-panel {
  .filter-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}
</style>
