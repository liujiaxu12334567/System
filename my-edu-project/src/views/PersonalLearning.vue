<template>
  <div class="all-courses-container">
    <header class="neu-header">
      <div class="header-inner">
        <div class="left-section">
          <h1 class="logo">Neuedu</h1>
          <nav class="nav-links">
            <a @click="$router.push('/home')">首页</a>
            <a @click="$router.push('/all-courses')">课程学习</a>
            <a href="#" class="active">个性学习</a>
            <a @click="$router.push('/my-exams')">考试</a>
            <a @click="$router.push('/student-quality')">素质活动</a>
            <a href="#">毕业设计</a>
          </nav>
        </div>
        <div class="right-section">
          <el-button type="primary" round class="ai-btn" @click="$router.push('/neu-ai')">
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon> NEU AI
          </el-button>
          <div class="user-info">
            <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            <span class="username">{{ userInfo.realName || '同学' }}</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </div>
        </div>
      </div>
    </header>

    <main class="main-content">
      <div class="page-title">个性学习</div>

      <el-card class="filter-panel">
        <div class="filter-row">
          <el-input
              v-model="keyword"
              placeholder="请输入课程名称/代码/组长"
              :prefix-icon="Search"
              clearable
              style="width: 260px; margin-right: 20px;"
          />
          <el-select v-model="semesterFilter" placeholder="学期" clearable style="width: 200px; margin-right: 20px;">
            <el-option label="2025-2026学年 第1学期" value="2025-1" />
            <el-option label="2024-2025学年 第2学期" value="2024-2" />
          </el-select>
          <el-button @click="resetFilters">重置</el-button>
          <el-button style="margin-left: auto" @click="fetchCourses" :loading="loading">刷新</el-button>
        </div>
      </el-card>

      <el-skeleton :rows="5" animated v-if="loading" style="margin-top: 20px;" />

      <div class="course-list-grid" v-else>
        <div
            v-for="item in filteredCourses"
            :key="item.id"
            class="course-card"
            :class="getCourseCardClass(item)"
        >
          <span class="top-status-tag" :class="getStatusTagClass(item.status)">{{ item.status || '进行中' }}</span>

          <div class="card-content">
            <div class="course-title">{{ item.name }}</div>
            <div class="course-meta">
              <span>{{ item.semester || '-' }}</span>
              <span class="divider">|</span>
              <span>{{ item.teacher || '-' }}</span>
            </div>
            <p class="course-code">{{ item.code || '-' }}</p>
            <p class="course-manager">组长：{{ item.managerName || '-' }}</p>
          </div>

          <div class="card-footer">
            <span class="enter-text"></span>
            <div class="btns">
              <el-button size="small" type="primary" plain @click="goToCourse(item.id)">进入学习</el-button>
              <el-button size="small" @click="openAiDialog(item)" :loading="aiLoading && aiCourseId === item.id">AI解析</el-button>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="filteredCourses.length === 0 && !loading" description="暂无课程" />
    </main>

    <el-dialog v-model="aiDialogVisible" title="AI 解析" width="720px" destroy-on-close>
      <div style="white-space: pre-wrap; line-height: 1.65;">
        <el-skeleton :rows="6" animated v-if="aiLoading" />
        <div v-else>{{ aiResult || '暂无解析结果' }}</div>
      </div>
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="$router.push('/neu-ai')">打开 NEU AI</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ArrowDown, MagicStick, Search } from '@element-plus/icons-vue'

const router = useRouter()
const userInfo = ref({})
const loading = ref(true)
const courseList = ref([])

const keyword = ref('')
const semesterFilter = ref(null)

const aiDialogVisible = ref(false)
const aiLoading = ref(false)
const aiCourseId = ref(null)
const aiResult = ref('')

onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch (e) {}
  }
  fetchCourses()
})

const fetchCourses = async () => {
  loading.value = true
  try {
    const res = await request.get('/student/personal-learning/courses')
    courseList.value = res || []
  } catch (e) {
    courseList.value = []
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  keyword.value = ''
  semesterFilter.value = null
}

const filteredCourses = computed(() => {
  let list = courseList.value || []
  if (semesterFilter.value) {
    list = list.filter((c) => String(c.semester || '') === String(semesterFilter.value))
  }
  if (keyword.value && keyword.value.trim()) {
    const k = keyword.value.trim()
    list = list.filter((c) =>
      String(c.name || '').includes(k) ||
      String(c.code || '').includes(k) ||
      String(c.managerName || '').includes(k)
    )
  }
  return list
})

const goToCourse = (courseId) => {
  router.push({ name: 'CourseStudy', params: { id: courseId }, query: { mode: 'personal' } })
}

const openAiDialog = async (course) => {
  if (!course || !course.id) return
  aiDialogVisible.value = true
  aiLoading.value = true
  aiCourseId.value = course.id
  aiResult.value = ''

  try {
    // 拉取课程资料列表用于 AI 解析（不包含测验/考试信息）
    let materials = []
    try {
      const m = await request.get(`/student/course/${course.id}/materials`)
      materials = (m || []).filter((x) => !['测验', '考试'].includes(String(x.type || '')))
    } catch (e) {}

    const materialBrief = materials.slice(0, 20).map((m) => `- ${m.type || '资料'}：${m.fileName || '未命名'}`).join('\n')

    const prompt = [
      `请对这门课程做“学习型AI解析”，输出：学习目标、核心知识点、推荐学习顺序、学习建议、常见误区。`,
      `课程：${course.name || ''}（代码：${course.code || ''}，学期：${course.semester || ''}，授课：${course.teacher || ''}，组长：${course.managerName || ''}）`,
      materialBrief ? `课程资料摘要（最多20条）：\n${materialBrief}` : `课程资料摘要：暂无`
    ].join('\n\n')

    const res = await request.post('/ai/chat', { message: prompt, history: [] }, { timeout: 300000 })
    aiResult.value = res || ''
  } catch (e) {
    aiResult.value = 'AI 解析失败，请稍后重试。'
  } finally {
    aiLoading.value = false
  }
}

const getCourseCardClass = (item) => {
  const color = item?.color || 'blue'
  return `bg-${color}`
}

const getStatusTagClass = (status) => {
  if (status === '进行中') return 'tag-green'
  if (status === '未开始') return 'tag-gray'
  if (status === '已结束') return 'tag-red'
  return 'tag-green'
}
</script>

<style scoped lang="scss">
/* 复用 AllCourses 的整体结构（保持风格一致） */
.all-courses-container {
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

  .right-section {
    display: flex;
    align-items: center;
    gap: 14px;
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
  font-size: 20px;
  font-weight: 800;
  color: #303133;
  margin-bottom: 14px;
}

.filter-panel {
  .filter-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.course-list-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.course-card {
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  position: relative;
  border: 1px solid #ebeef5;
  transition: all 0.2s ease;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.06);
  }
}

.top-status-tag {
  position: absolute;
  right: 14px;
  top: 14px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}
.tag-green { background: #22c55e; }
.tag-gray { background: #9ca3af; }
.tag-red { background: #ef4444; }

.card-content {
  .course-title {
    font-size: 16px;
    font-weight: 800;
    color: #303133;
    margin-bottom: 10px;
    padding-right: 68px;
  }
  .course-meta {
    color: #909399;
    font-size: 12px;
    .divider { margin: 0 6px; }
  }
  .course-code {
    margin: 10px 0 0;
    font-size: 12px;
    color: #606266;
  }
  .course-manager {
    margin: 6px 0 0;
    font-size: 12px;
    color: #606266;
  }
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  .btns {
    display: flex;
    gap: 8px;
  }
}
</style>

