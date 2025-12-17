<template>
  <div class="course-schedule-page">
    <StudentHeader />

    <main class="schedule-main">
      <section class="schedule-summary">
        <div>
          <p class="eyebrow">你的排课数据由课表自动推导</p>
          <h2>课程表总览</h2>
          <p class="summary-desc">
            当前 {{ scheduleData.courses.length }} 门课程，共 {{ totalSlots }} 条时段安排。
          </p>
        </div>
        <el-button type="primary" round :loading="loading" @click="fetchSchedule">重新加载</el-button>
      </section>

      <section class="schedule-board">
        <div class="board-header">
          <div>
            <h3>周课表</h3>
            <p>按照每周 1~7 分组，节次时间以排课为准</p>
          </div>
        </div>

        <div class="board-grid" v-if="!loading">
          <div class="day-column" v-for="day in dayList" :key="day.value">
            <div class="day-title">
              <span>{{ day.label }}</span>
              <small>{{ day.subLabel }}</small>
            </div>

            <div
                v-for="slot in groupedSlots[day.value]"
                :key="slotKey(slot)"
                class="slot-card"
            >
              <div class="slot-time">
                {{ slot.startTime || '待定' }} - {{ slot.endTime || '待定' }}
              </div>
              <div class="slot-body">
                <div class="slot-course">
                  {{ courseMap[slot.courseId]?.name || '暂无课程' }}
                </div>
                <div class="slot-meta">
                  <span>{{ courseMap[slot.courseId]?.teacher || '待定教师' }}</span>
                  <span class="divider">|</span>
                  <span>{{ slot.periodIndex ? slot.periodIndex + ' 节' : '节次未设' }}</span>
                </div>
              </div>
              <span class="slot-status" :class="statusClass(courseMap[slot.courseId]?.status)">
                {{ courseMap[slot.courseId]?.status || (slot.courseId ? '已安排' : '空白节') }}
              </span>
            </div>

            <div v-if="(groupedSlots[day.value] || []).length === 0" class="empty-slot">
              暂无课程安排
            </div>
          </div>
        </div>
        <el-skeleton animated :rows="3" v-else />
      </section>

      <section class="course-list">
        <header class="course-list-header">
          <div>
            <h3>课程概况</h3>
            <p>系统会根据课表推断「未开始/进行中/已结束」状态</p>
          </div>
        </header>
        <div v-if="scheduleData.courses.length">
          <div class="course-item" v-for="course in scheduleData.courses" :key="course.id">
            <div>
              <div class="course-name">{{ course.name }}</div>
              <div class="course-meta">
                <span>{{ course.semester || '学期未设置' }}</span>
                <span class="divider">|</span>
                <span>{{ course.teacher || '未定教师' }}</span>
              </div>
            </div>
            <div class="course-status">
              <span class="status-badge" :class="statusClass(course.status)">
                {{ course.status || '未开始' }}
              </span>
              <small v-if="course.startTime">下一节 {{ formatSlotTime(course.startTime) }}</small>
            </div>
          </div>
        </div>
        <div v-else class="empty-list">
          <el-empty description="暂无课程信息" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import StudentHeader from '@/components/StudentHeader.vue'
import request from '@/utils/request'

const router = useRouter()
const userInfo = ref({})
const scheduleData = ref({ courses: [], schedules: [] })
const loading = ref(false)

const dayList = [
  { value: 1, label: '周一', subLabel: '星期一' },
  { value: 2, label: '周二', subLabel: '星期二' },
  { value: 3, label: '周三', subLabel: '星期三' },
  { value: 4, label: '周四', subLabel: '星期四' },
  { value: 5, label: '周五', subLabel: '星期五' },
  { value: 6, label: '周六', subLabel: '星期六' },
  { value: 7, label: '周日', subLabel: '星期日' }
]

const scheduleFallback = { courses: [], schedules: [] }

const courseMap = computed(() => {
  const map = {};
  (scheduleData.value.courses || []).forEach((course) => {
    if (course && course.id != null) {
      map[course.id] = course
    }
  })
  return map
})

const groupedSlots = computed(() => {
  const map = {}
  dayList.forEach((day) => { map[day.value] = [] });
  (scheduleData.value.schedules || []).forEach((slot) => {
    if (!slot || slot.dayOfWeek == null) return
    const target = map[slot.dayOfWeek] || []
    target.push(slot)
  })
  Object.values(map).forEach((slots) => {
    slots.sort((a, b) => {
      if (a.startTime && b.startTime) {
        return a.startTime.localeCompare(b.startTime)
      }
      if (typeof a.periodIndex === 'number' && typeof b.periodIndex === 'number') {
        return a.periodIndex - b.periodIndex
      }
      return 0
    })
  })
  return map
})

const totalSlots = computed(() => (scheduleData.value.schedules || []).length)

const statusClass = (status) => {
  if (status === '进行中') return 'ongoing'
  if (status === '未开始') return 'pending'
  if (status === '已结束' || status === '已结课') return 'finished'
  return 'neutral'
}

const formatSlotTime = (value) => {
  if (!value) return ''
  return dayjs(value).format('MM-DD HH:mm')
}

const fetchSchedule = async () => {
  loading.value = true
  try {
    const res = await request.get('/student/course-schedule')
    scheduleData.value = res || scheduleFallback
  } catch (error) {
    const message = error?.response?.data || '无法加载课程表，请稍后再试'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

const isActive = (path) => router.currentRoute.value.path === path

const goHome = () => router.push('/home')

const slotKey = (slot) => `${slot.dayOfWeek || 0}-${slot.periodIndex || 0}-${slot.courseId || 'empty'}-${slot.startTime || 'na'}`

onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try {
      userInfo.value = JSON.parse(storedUser)
    } catch (e) {
      userInfo.value = {}
    }
  }
  fetchSchedule()
})
</script>

<style scoped lang="scss">
.course-schedule-page {
  min-height: 100vh;
  background: #f5f7fb;
  .neu-header {
    background: #fff;
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
      gap: 20px;
      .logo {
        font-size: 20px;
        font-weight: 800;
        color: #245fe6;
        cursor: pointer;
      }
      .nav-links {
        display: flex;
        gap: 20px;
        a {
          cursor: pointer;
          color: #606266;
          font-weight: 500;
          &.active {
            color: #245fe6;
            font-weight: 700;
          }
        }
      }
    }
    .right-section {
      display: flex;
      align-items: center;
      gap: 14px;
      .ai-btn {
        background: linear-gradient(90deg, #5383fc 0%, #766dff 100%);
        border: none;
      }
      .user-info {
        display: flex;
        align-items: center;
        gap: 10px;
        .username {
          font-weight: 600;
        }
      }
    }
  }

  .schedule-main {
    max-width: 1200px;
    margin: 0 auto;
    padding: 24px 18px 60px;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .schedule-summary {
    background: #fff;
    border-radius: 12px;
    padding: 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    .eyebrow {
      color: #909399;
      margin-bottom: 4px;
    }
    .summary-desc {
      color: #606266;
      margin-top: 6px;
    }
  }

  .schedule-board {
    background: #fff;
    border-radius: 14px;
    padding: 20px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.04);
    .board-header {
      border-bottom: 1px solid #f0f0f0;
      padding-bottom: 12px;
      margin-bottom: 18px;
      h3 {
        margin: 0;
        font-size: 18px;
        color: #303133;
      }
      p {
        margin: 4px 0 0;
        font-size: 12px;
        color: #909399;
      }
    }
    .board-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 14px;
    }
    .day-column {
      background: #fdfefe;
      border-radius: 10px;
      border: 1px solid #f0f0f0;
      padding: 12px;
      min-height: 220px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .day-title {
      font-weight: 600;
      display: flex;
      justify-content: space-between;
      color: #303133;
      small {
        font-size: 11px;
        color: #909399;
      }
    }
    .slot-card {
      background: #fff;
      border-radius: 8px;
      padding: 10px;
      border: 1px solid #e8ecf3;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
      display: flex;
      flex-direction: column;
      gap: 6px;
      .slot-time {
        font-size: 12px;
        color: #555;
      }
      .slot-body {
        .slot-course {
          font-weight: 600;
          color: #303133;
        }
        .slot-meta {
          font-size: 11px;
          color: #909399;
        }
        .divider {
          margin: 0 6px;
        }
      }
      .slot-status {
        align-self: flex-start;
        padding: 2px 10px;
        border-radius: 12px;
        font-size: 11px;
        color: #fff;
      }
      .slot-status.ongoing { background: #22c55e; }
      .slot-status.pending { background: #9ca3af; }
      .slot-status.finished { background: #ef4444; }
      .slot-status.neutral { background: #4f46e5; }
    }
    .empty-slot {
      font-size: 12px;
      color: #909399;
      text-align: center;
      margin-top: 8px;
    }
  }

  .course-list {
    background: #fff;
    border-radius: 14px;
    padding: 24px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.04);
    .course-list-header {
      margin-bottom: 16px;
      h3 {
        margin: 0;
        font-size: 18px;
      }
      p {
        margin: 6px 0 0;
        font-size: 12px;
        color: #909399;
      }
    }
    .course-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid #f0f0f0;
      padding: 14px 0;
      &:last-child { border-bottom: none; }
      .course-name {
        font-weight: 600;
        color: #303133;
      }
      .course-meta {
        color: #909399;
        font-size: 12px;
        margin-top: 4px;
        .divider {
          margin: 0 6px;
        }
      }
    }
    .course-status {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      text-align: right;
      .status-badge {
        display: inline-flex;
        padding: 3px 12px;
        border-radius: 12px;
        font-size: 12px;
        color: #fff;
        &.ongoing { background: #22c55e; }
        &.pending { background: #9ca3af; }
        &.finished { background: #ef4444; }
        &.neutral { background: #4f46e5; }
      }
      small {
        margin-top: 6px;
        color: #666;
        font-size: 11px;
      }
    }
    .empty-list {
      padding: 40px 0;
    }
  }
}
</style>
