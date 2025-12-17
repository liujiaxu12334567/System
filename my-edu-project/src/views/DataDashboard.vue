<template>
  <div class="dashboard-container">
    <div class="dashboard-header">
      <h2>学生数据综合大屏</h2>
      <div class="header-controls">
        <el-select v-model="selectedClass" placeholder="选择班级" @change="handleClassChange">
          <el-option label="全部班级" value="all" />
          <el-option v-for="cls in classList" :key="cls.id" :label="cls.name" :value="cls.id" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="handleDateChange"
        />
      </div>
    </div>

    <div class="dashboard-grid">
      <!-- 卡片1：班级概览 -->
      <el-card shadow="hover" class="dashboard-card">
        <template #header>
          <div class="card-header">
            <span>班级概览</span>
          </div>
        </template>
        <div class="overview-stats">
          <div class="stat-item">
            <div class="stat-value">{{ totalStudents }}</div>
            <div class="stat-label">学生总数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ totalCourses }}</div>
            <div class="stat-label">课程总数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ totalExams }}</div>
            <div class="stat-label">考试次数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ avgAttendance }}%</div>
            <div class="stat-label">平均出勤率</div>
          </div>
        </div>
      </el-card>

      <!-- 卡片2：学生发言统计 -->
      <el-card shadow="hover" class="dashboard-card">
        <template #header>
          <div class="card-header">
            <span>学生发言统计</span>
            <el-button size="small" type="primary" plain @click="refreshData">刷新数据</el-button>
          </div>
        </template>
        <div class="chart-container">
          <div ref="speechChartRef" class="speech-chart"></div>
        </div>
      </el-card>

      <!-- 卡片3：考试成绩分布 -->
      <el-card shadow="hover" class="dashboard-card">
        <template #header>
          <div class="card-header">
            <span>考试成绩分布</span>
          </div>
        </template>
        <div class="chart-container">
          <div ref="scoreChartRef" class="score-chart"></div>
        </div>
      </el-card>

      <!-- 卡片4：出勤情况 -->
      <el-card shadow="hover" class="dashboard-card">
        <template #header>
          <div class="card-header">
            <span>出勤情况</span>
          </div>
        </template>
        <div class="chart-container">
          <div ref="attendanceChartRef" class="attendance-chart"></div>
        </div>
      </el-card>
    </div>

    <!-- 数据表格：详细数据 -->
    <el-card shadow="hover" class="dashboard-card full-width">
      <template #header>
        <div class="card-header">
          <span>详细数据</span>
        </div>
      </template>
      <el-table :data="studentDetails" border stripe style="width: 100%">
        <el-table-column prop="studentId" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="className" label="班级" width="120" />
        <el-table-column prop="speechCount" label="发言次数" width="100" />
        <el-table-column prop="avgScore" label="平均成绩" width="100" />
        <el-table-column prop="attendanceRate" label="出勤率" width="100">
          <template #default="scope">
            <el-progress :percentage="scope.row.attendanceRate" :stroke-width="10" :show-text="false" />
            <span style="margin-left: 10px">{{ scope.row.attendanceRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestExam" label="最近考试" width="150" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, watch } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

// 响应式数据
const selectedClass = ref('all')
const dateRange = ref([])
const classList = ref([])
const studentDetails = ref([])

// 统计数据
const totalStudents = ref(0)
const totalCourses = ref(0)
const totalExams = ref(0)
const avgAttendance = ref(0)

// 图表引用
const speechChartRef = ref(null)
const scoreChartRef = ref(null)
const attendanceChartRef = ref(null)

// 图表实例
let speechChart = null
let scoreChart = null
let attendanceChart = null

// 模拟数据（实际项目中应从后端获取）
const mockData = {
  students: [
    { id: 1, name: '张三', className: '2023级1班', speechCount: 15, avgScore: 85, attendanceRate: 95, latestExam: '2025-12-10' },
    { id: 2, name: '李四', className: '2023级1班', speechCount: 8, avgScore: 78, attendanceRate: 88, latestExam: '2025-12-10' },
    { id: 3, name: '王五', className: '2023级2班', speechCount: 22, avgScore: 92, attendanceRate: 98, latestExam: '2025-12-10' },
    { id: 4, name: '赵六', className: '2023级2班', speechCount: 12, avgScore: 88, attendanceRate: 92, latestExam: '2025-12-10' },
    { id: 5, name: '钱七', className: '2023级3班', speechCount: 18, avgScore: 90, attendanceRate: 96, latestExam: '2025-12-10' },
    { id: 6, name: '孙八', className: '2023级3班', speechCount: 5, avgScore: 75, attendanceRate: 85, latestExam: '2025-12-10' },
    { id: 7, name: '周九', className: '2023级1班', speechCount: 25, avgScore: 95, attendanceRate: 99, latestExam: '2025-12-10' },
    { id: 8, name: '吴十', className: '2023级2班', speechCount: 10, avgScore: 82, attendanceRate: 90, latestExam: '2025-12-10' }
  ],
  classes: [
    { id: 1, name: '2023级1班' },
    { id: 2, name: '2023级2班' },
    { id: 3, name: '2023级3班' }
  ],
  stats: {
    totalStudents: 8,
    totalCourses: 12,
    totalExams: 5,
    avgAttendance: 92
  }
}

// 初始化数据
const initData = () => {
  // 实际项目中应从后端获取数据
  // request.get('/api/dashboard/data').then(res => {
  //   studentDetails.value = res.students
  //   classList.value = res.classes
  //   totalStudents.value = res.stats.totalStudents
  //   totalCourses.value = res.stats.totalCourses
  //   totalExams.value = res.stats.totalExams
  //   avgAttendance.value = res.stats.avgAttendance
  // })
  
  // 使用模拟数据
  studentDetails.value = mockData.students
  classList.value = mockData.classes
  totalStudents.value = mockData.stats.totalStudents
  totalCourses.value = mockData.stats.totalCourses
  totalExams.value = mockData.stats.totalExams
  avgAttendance.value = mockData.stats.avgAttendance
}

// 初始化图表
const initCharts = () => {
  // 发言统计图表
  speechChart = echarts.init(speechChartRef.value)
  speechChart.setOption({
    title: {
      text: '学生发言次数分布',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: mockData.students.map(s => s.name)
    },
    yAxis: {
      type: 'value'
    },
    series: [{
      data: mockData.students.map(s => s.speechCount),
      type: 'bar',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#83bff6' },
          { offset: 0.5, color: '#188df0' },
          { offset: 1, color: '#188df0' }
        ])
      }
    }]
  })

  // 成绩分布图表
  scoreChart = echarts.init(scoreChartRef.value)
  scoreChart.setOption({
    title: {
      text: '考试成绩分布',
      left: 'center'
    },
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [{
      name: '成绩分布',
      type: 'pie',
      radius: '50%',
      data: [
        { value: 3, name: '90-100' },
        { value: 3, name: '80-89' },
        { value: 2, name: '70-79' },
        { value: 0, name: '60-69' },
        { value: 0, name: '60以下' }
      ],
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  })

  // 出勤情况图表
  attendanceChart = echarts.init(attendanceChartRef.value)
  attendanceChart.setOption({
    title: {
      text: '班级出勤率趋势',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['周一', '周二', '周三', '周四', '周五']
    },
    yAxis: {
      type: 'value',
      max: 100
    },
    series: [{
      data: [95, 92, 98, 90, 96],
      type: 'line',
      smooth: true,
      itemStyle: {
        color: '#5470c6'
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(84, 112, 198, 0.5)' },
          { offset: 1, color: 'rgba(84, 112, 198, 0.1)' }
        ])
      }
    }]
  })
}

// 处理班级变化
const handleClassChange = () => {
  // 实际项目中应根据班级过滤数据
  // request.get('/api/dashboard/data', { params: { classId: selectedClass.value } }).then(res => {
  //   studentDetails.value = res.students
  // })
  console.log('Selected class:', selectedClass.value)
}

// 处理日期变化
const handleDateChange = () => {
  console.log('Date range:', dateRange.value)
}

// 刷新数据
const refreshData = () => {
  ElMessage.info('刷新数据')
  initData()
}

// 监听窗口大小变化，调整图表大小
const handleResize = () => {
  speechChart && speechChart.resize()
  scoreChart && scoreChart.resize()
  attendanceChart && attendanceChart.resize()
}

// 生命周期
onMounted(() => {
  initData()
  initCharts()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-controls {
  display: flex;
  gap: 10px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.dashboard-card {
  margin-bottom: 20px;
}

.full-width {
  grid-column: 1 / -1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.overview-stats {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 20px 0;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.chart-container {
  height: 300px;
  width: 100%;
}

.speech-chart,
.score-chart,
.attendance-chart {
  width: 100%;
  height: 100%;
}
</style>
