<template>
  <div class="teacher-container-light">
    <el-aside width="220px" class="sidebar-light">
      <div class="brand-area">
        <div class="brand-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="#409EFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="#409EFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="#409EFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <span class="brand-text">智慧教学大脑</span>
      </div>

      <el-menu :default-active="activeMenu" class="el-menu-vertical-light"
               background-color="#ffffff" text-color="#606266" active-text-color="#409EFF"
               @select="handleSelect">
        <el-menu-item index="1"><el-icon><DataAnalysis /></el-icon><span>班级学情分析</span></el-menu-item>
        <el-menu-item index="6"><el-icon><Collection /></el-icon><span>我的课程 (上课)</span></el-menu-item>
        <el-menu-item index="2"><el-icon><Document /></el-icon><span>申请审批中心</span></el-menu-item>
        <el-menu-item index="3"><el-icon><Reading /></el-icon><span>资源与作业调度</span></el-menu-item>
        <el-menu-item index="4"><el-icon><Monitor /></el-icon><span>考试全景监控</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content-light">
      <div class="dashboard-card header-bar-light">
        <div class="header-left">
          <div class="greeting-wrap">
            <h2 class="greeting">早安，{{ teacherName }} 老师</h2>
            <p class="date-badge">
              <el-icon class="mr-1"><Calendar /></el-icon> {{ currentDate }} | 教学第 12 周
            </p>
          </div>
        </div>
        <div class="header-actions">
          <div class="action-item">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger" class="badge-dot">
              <el-button circle class="icon-btn" :icon="Bell" @click="fetchNotifications"></el-button>
            </el-badge>
          </div>
          <el-dropdown trigger="click">
            <div class="user-profile-area">
              <el-avatar :size="36" style="background-color: #409EFF; color: #fff;">{{ teacherName.charAt(0) }}</el-avatar>
              <span class="username">{{ teacherName }}</span>
              <el-icon class="caret"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="logout" style="color: #F56C6C;">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 在线课程问答抽屉 -->
      <el-drawer v-model="coursePanelVisible" :title="activeCourse ? activeCourse.name + ' · 在线课堂' : '在线课堂'" size="50%" direction="rtl">
        <div v-if="activeCourse">
          <div class="qa-layout">
            <div class="qa-left">
              <div class="qa-form">
                <el-form label-width="80px">
                  <el-form-item label="提问方式">
                    <el-select v-model="questionForm.mode" size="small" style="width:100%">
                      <el-option v-for="m in modeOptions" :key="m.value" :label="m.label" :value="m.value"/>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="问题标题">
                    <el-input v-model="questionForm.title" placeholder="如：今日知识点理解如何？" />
                  </el-form-item>
                  <el-form-item label="问题描述">
                    <el-input v-model="questionForm.content" type="textarea" :rows="3" placeholder="可添加补充说明、点名学生、举手/抢答规则等" />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="publishQuestion" :disabled="!questionForm.title">发布问题</el-button>
                  </el-form-item>
                </el-form>
              </div>

              <div class="qa-list">
                <div class="qa-list-header">在线问题</div>
                <el-empty v-if="onlineQuestions.length === 0" description="暂无问题" />
                <el-scrollbar height="320" v-else>
                  <div v-for="q in onlineQuestions" :key="q.id" class="qa-item" :class="{'is-active': selectedQuestion && selectedQuestion.id === q.id}" @click="loadAnswers(q)">
                    <div class="qa-title">{{ q.title }}</div>
                    <div class="qa-meta">发布 {{ formatTime(q.createTime) }}</div>
                  </div>
                </el-scrollbar>
              </div>
            </div>

            <div class="qa-right">
              <div class="qa-list-header">回答 / 学生发言</div>
              <el-empty v-if="!selectedQuestion" description="请选择左侧问题" />
              <el-scrollbar v-else height="500">
                <div v-if="answers.length === 0" class="qa-empty">还没有回答，等待抢答或举手发言...</div>
                <div v-for="a in answers" :key="a.id" class="qa-answer">
                  <div class="qa-answer-user">学生ID: {{ a.studentId }}</div>
                  <div class="qa-answer-text">{{ a.answerText }}</div>
                  <div class="qa-meta">提交 {{ formatTime(a.createTime) }}</div>
                </div>
              </el-scrollbar>
              <div class="qa-tip">提示：学生可在“在线问题”中举手/抢答，教师点名后引导回答。</div>
            </div>
          </div>
        </div>
      </el-drawer>

      <div v-if="activeMenu === '1'" class="dashboard-wrapper-light fade-in">

        <div class="metrics-row-light">
          <div class="metric-card-pro bg-gradient-blue">
            <div class="metric-content">
              <div class="label">管理学生总数</div>
              <div class="value-group">
                <span class="number">{{ stats.studentCount }}</span><span class="unit">人</span>
              </div>
              <div class="trend">较上学期 +5% <el-icon><Top /></el-icon></div>
            </div>
            <div class="metric-icon-bg"><el-icon><UserFilled /></el-icon></div>
          </div>

          <div class="metric-card-pro bg-gradient-green">
            <div class="metric-content">
              <div class="label">平均出勤率</div>
              <div class="value-group">
                <span class="number">{{ stats.attendanceRate }}</span><span class="unit">%</span>
              </div>
              <div class="trend">保持稳定 <el-icon><Minus /></el-icon></div>
            </div>
            <div class="metric-icon-bg"><el-icon><Trophy /></el-icon></div>
          </div>

          <div class="metric-card-pro bg-gradient-orange">
            <div class="metric-content">
              <div class="label">课堂互动指数</div>
              <div class="value-group">
                <span class="number">{{ stats.interactionIndex }}</span><span class="unit">/10</span>
              </div>
              <div class="trend">活跃度高</div>
            </div>
            <div class="metric-icon-bg"><el-icon><ChatLineRound /></el-icon></div>
          </div>

          <div class="metric-card-pro bg-gradient-purple">
            <div class="metric-content">
              <div class="label">作业提交率</div>
              <div class="value-group">
                <span class="number">{{ stats.submissionRate }}</span><span class="unit">%</span>
              </div>
              <div class="trend">较上周 +2% <el-icon><Top /></el-icon></div>
            </div>
            <div class="metric-icon-bg"><el-icon><TrendCharts /></el-icon></div>
          </div>
        </div>

        <div class="charts-row-light">
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="dashboard-card chart-card-light">
                <div class="card-header-light">
                  <div class="header-title-group">
                    <span class="deco-bar bg-blue"></span>
                    <span class="title">师生发言时长占比</span>
                  </div>
                </div>
                <div ref="pieChartRef" class="chart-box-light"></div>
              </div>
            </el-col>
            <el-col :span="10">
              <div class="dashboard-card chart-card-light">
                <div class="card-header-light">
                  <div class="header-title-group">
                    <span class="deco-bar bg-purple"></span>
                    <span class="title">课堂专注度趋势</span>
                  </div>
                </div>
                <div ref="lineChartRef" class="chart-box-light"></div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="dashboard-card chart-card-light">
                <div class="card-header-light">
                  <div class="header-title-group">
                    <span class="deco-bar bg-green"></span>
                    <span class="title">班级能力雷达</span>
                  </div>
                </div>
                <div ref="radarChartRef" class="chart-box-light"></div>
              </div>
            </el-col>
          </el-row>
        </div>

        <div class="dashboard-card table-section-light">
          <div class="section-header-light">
            <h3 class="title">学生名单管理</h3>
            <div class="filters-light">
              <el-select v-model="classFilter" placeholder="选择班级" clearable @change="fetchStudents" style="width: 140px;">
                <el-option v-for="id in teachingClassIds" :key="id" :label="id+'班'" :value="id" />
              </el-select>
              <el-input v-model="keyword" placeholder="搜索姓名/学号" style="width: 200px;" @input="fetchStudents" clearable>
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-button type="primary" :icon="Plus" @click="openApplyDialog('ADD', null)">新增学生</el-button>
            </div>
          </div>

          <el-table :data="studentList" border style="width: 100%" height="400" header-cell-class-name="light-table-header" v-loading="loading">
            <el-table-column prop="username" label="学号" width="140" />
            <el-table-column prop="realName" label="姓名" width="120" font-weight="bold"/>
            <el-table-column prop="classId" label="班级" width="100" align="center">
              <template #default="scope"><el-tag effect="light">{{ scope.row.classId }}班</el-tag></template>
            </el-table-column>
            <el-table-column label="AI综合画像" width="150" align="center">
              <template #default>
                <el-progress :percentage="Number((Math.random() * (98 - 80) + 80).toFixed(0))" :status="Math.random()>0.5?'success':''"></el-progress>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="入学时间" min-width="160" />
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="openApplyDialog('RESET_PWD', scope.row)">重置</el-button>
                <el-popconfirm title="确定要发起删除申请吗？" @confirm="submitApplication">
                  <template #reference>
                    <el-button link type="danger" size="small" @click="openApplyDialog('DELETE', scope.row, false)">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-box-light">
            <el-pagination background @size-change="handleSizeChange" @current-change="handleCurrentChange" :current-page="pageNum" :page-sizes="[10, 20, 50]" :page-size="pageSize" layout="total, sizes, prev, pager, next, jumper" :total="total" />
          </div>
        </div>
      </div>

      <div v-if="activeMenu === '6'" class="dashboard-wrapper-light fade-in">
        <div class="dashboard-card content-block-light">
          <div class="block-header-light">
            <div class="header-title-group">
              <span class="deco-bar bg-blue"></span>
              <h3 class="title">我的授课列表 (上课/签到)</h3>
            </div>
            <el-button type="primary" plain @click="fetchMyCourses" :icon="Refresh">刷新列表</el-button>
          </div>

          <div class="course-grid">
            <div v-for="course in myCourseList" :key="course.id" class="course-card-pro">
              <div class="c-header">
                <span class="c-name">{{ course.name }}</span>
                <el-tag size="small" effect="dark">{{ course.classId }}班</el-tag>
              </div>
              <div class="c-body">
                <div class="info-row"><el-icon><User /></el-icon> 应到人数：<span class="bold">{{ course.studentCount || '统计中...' }}</span></div>
                <div class="info-row"><el-icon><Clock /></el-icon> {{ course.semester }}</div>

                <div class="checkin-control" v-if="checkInStatus[course.id]?.isActive">
                  <div class="active-badge">
                    <span class="pulse-dot"></span> 正在签到中
                  </div>
                  <div class="stats-num">
                    <span class="big">{{ checkInStatus[course.id]?.checkedCount || 0 }}</span>
                    <span class="small">/ {{ checkInStatus[course.id]?.totalCount || 0 }} 已签</span>
                  </div>
                  <div class="rate-bar" v-if="checkInStatus[course.id]?.rate">
                    实时出勤率: <span style="color:#67C23A;font-weight:bold;">{{ checkInStatus[course.id]?.rate }}%</span>
                  </div>
                  <el-button type="primary" plain @click="openCoursePanel(course)" style="margin-top:10px; width:100%">进入课程</el-button>
                  <el-button type="danger" round @click="stopClass(course.id)" style="margin-top:10px; width:100%">结束上课/签到</el-button>
                </div>

                <div class="start-control" v-else>
                  <el-button type="primary" size="large" round @click="startClass(course.id)" class="start-btn">
                    <el-icon style="margin-right:5px"><VideoPlay /></el-icon> 开始上课
                  </el-button>
                  <el-button type="default" size="large" round @click="openClassroom(course.id)" style="margin-top:12px; width:100%">
                    进入课程
                  </el-button>
                </div>
              </div>
            </div>
            <div v-if="myCourseList.length === 0" class="empty-course">
              <el-empty description="暂无关联课程，请确认您是否被分配为任课教师" />
            </div>
          </div>
        </div>
      </div>

      <div v-if="activeMenu === '2'" class="dashboard-card content-block-light fade-in">
        <div class="block-header-light"><h3 class="title">我的申请记录</h3></div>
        <el-table :data="applicationList" border style="width: 100%" header-cell-class-name="light-table-header">
          <el-table-column prop="type" label="类型" width="120">
            <template #default="scope"><el-tag effect="plain">{{ formatType(scope.row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="content" label="申请内容" show-overflow-tooltip/>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope"><el-tag :type="getStatusType(scope.row.status)">{{ formatStatus(scope.row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" width="180">
            <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="activeMenu === '3'" class="dashboard-card content-block-light fade-in">
        <div class="block-header-light">
          <h3 class="title">课程资料与作业批改</h3>
          <el-button type="primary" :icon="Upload">上传新资料</el-button>
        </div>
        <el-table :data="teachingMaterials" border style="width: 100%" header-cell-class-name="light-table-header">
          <el-table-column prop="fileName" label="资料名称" min-width="250" show-overflow-tooltip>
            <template #default="{row}">
              <div class="material-name">
                <el-icon v-if="row.type==='作业'" color="#409EFF" class="mr-2"><EditPen /></el-icon>
                <el-icon v-else-if="row.type==='测验'" color="#E6A23C" class="mr-2"><Stopwatch /></el-icon>
                <el-icon v-else color="#909399" class="mr-2"><Document /></el-icon>
                <span>{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="100" align="center">
            <template #default="scope"><el-tag :type="getMaterialTypeTag(scope.row.type)" effect="light">{{ scope.row.type }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="openSubmissionDialog(scope.row)">批改作业</el-button>
              <el-divider direction="vertical" />
              <el-button v-if="!isLeader" size="small" type="warning" link @click="openApplyDialog('DEADLINE_EXTENSION', scope.row)">申请延期</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="activeMenu === '4'" class="dashboard-card content-block-light fade-in">
        <div class="block-header-light">
          <h3 class="title">考试监控与作弊记录</h3>
          <el-select v-model="selectedExamId" placeholder="选择考试场次" clearable style="width: 240px;" @change="fetchCheatingRecords">
            <el-option v-for="exam in availableExams" :key="exam.id" :label="exam.title" :value="exam.id" />
          </el-select>
        </div>
        <el-table :data="cheatingRecords" border style="width: 100%" header-cell-class-name="light-table-header">
          <el-table-column prop="studentName" label="姓名" width="120" />
          <el-table-column prop="record.cheatCount" label="切屏/违规次数" width="150" align="center">
            <template #default="scope">
              <el-badge :value="scope.row.record.cheatCount" class="item" type="danger" />
            </template>
          </el-table-column>
          <el-table-column label="风险等级" width="120" align="center">
            <template #default="scope">
              <el-tag type="danger" v-if="scope.row.record.cheatCount > 5">高风险</el-tag>
              <el-tag type="warning" v-else>中风险</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center">
            <template #default="scope"><el-button link type="primary" :icon="View">查看抓拍日志</el-button></template>
          </el-table-column>
        </el-table>
      </div>

      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
        <el-form :model="applyForm" label-width="80px" class="dialog-form-light">
          <template v-if="applyForm.type === 'ADD'">
            <el-form-item label="学号"><el-input v-model="applyForm.newUsername" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="applyForm.newRealName" /></el-form-item>
            <el-form-item label="班级ID"><el-input v-model="applyForm.newClassId" type="number" /></el-form-item>
          </template>
          <el-form-item label="申请理由">
            <el-input type="textarea" v-model="applyForm.reason" rows="4" />
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submitApplication">提交申请</el-button></template>
      </el-dialog>

      <el-dialog v-model="submissionDialogVisible" title="作业批改" width="80%" top="5vh">
        <el-table :data="submissions" border height="500" header-cell-class-name="light-table-header">
          <el-table-column prop="studentName" label="姓名" width="120" font-weight="bold"/>
          <el-table-column label="内容预览" min-width="300">
            <template #default="{row}"><div class="answer-text-light">{{ row.answerText }}</div></template>
          </el-table-column>
          <el-table-column label="评分" width="220" align="center" fixed="right">
            <template #default="{row}">
              <div style="display:flex; align-items:center; justify-content:center;">
                <el-input-number v-model="row.gradeForm.score" :min="0" :max="100" size="small" style="width: 100px; margin-right: 10px;" />
                <el-button type="success" size="small" @click="submitGrade(row)">确认</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  DataAnalysis, Document, Reading, Monitor, Bell, UserFilled, Trophy, ChatLineRound, TrendCharts, Search, EditPen, Stopwatch,
  CaretBottom, Top, Bottom, Minus, Plus, Upload, View, Check, Calendar, Collection, VideoPlay, User, Clock, Refresh
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const teacherInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
const teacherName = ref(teacherInfo.realName || '教师')
const isLeader = teacherInfo.role === '2'
const activeMenu = ref('1')
const currentDate = dayjs().format('YYYY年MM月DD日 dddd')
const loading = ref(false)

// 数据状态
const stats = ref({ studentCount: 0, attendanceRate: 0, interactionIndex: 0, submissionRate: 0 })
const studentList = ref([])
const applicationList = ref([])
const teachingMaterials = ref([])
const cheatingRecords = ref([])
const notificationList = ref([])
const teachingClassIds = ref([])
const availableExams = ref([])
const unreadCount = computed(() => notificationList.value.filter(n => !n.isRead).length)

// 签到相关
const myCourseList = ref([])
const checkInStatus = reactive({})
let statusTimer = null

// 在线问答
const coursePanelVisible = ref(false)
const activeCourse = ref(null)
const onlineQuestions = ref([])
const selectedQuestion = ref(null)
const questionForm = ref({ title: '', content: '', mode: 'broadcast' })
const answers = ref([])

const keyword = ref('')
const classFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedExamId = ref(null)

const dialogVisible = ref(false)
const submissionDialogVisible = ref(false)
const dialogTitle = ref('')
const applyForm = ref({ type: '', reason: '', newUsername: '', newRealName: '', newClassId: null })
const submissions = ref([])
const currentMaterial = ref({})

const pieChartRef = ref(null)
const lineChartRef = ref(null)
const radarChartRef = ref(null)
let charts = []

onMounted(() => {
  if(teacherInfo.teachingClasses) {
    teachingClassIds.value = teacherInfo.teachingClasses.split(/[,，]/).map(s=>s.trim()).filter(s=>s)
    if(teachingClassIds.value.length > 0) classFilter.value = teachingClassIds.value[0]
  }
  fetchStudents()
  fetchApplications()
  fetchMaterials()
  fetchNotifications()
  fetchDashboardData()
  fetchExams()

  // 确保“我的课程”在刷新页面时也能加载
  fetchMyCourses()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  charts.forEach(c => c.dispose())
  if(statusTimer) clearInterval(statusTimer)
})

const handleSelect = (idx) => {
  activeMenu.value = idx
  if (idx === '1') {
    fetchStudents()
    fetchDashboardData()
  } else if (idx === '6') {
    fetchMyCourses()
  } else if (idx === '2') fetchApplications()
  else if (idx === '3') fetchMaterials()
  else if (idx === '4') fetchExams()
}

// --- 签到业务逻辑 ---
const fetchMyCourses = async () => {
  try {
    console.log("【调试】开始获取课程，当前教师:", teacherName.value);
    const res = await request.get('/teacher/courses')
    const list = Array.isArray(res) ? res : []
    if (list.length === 0) {
      const all = await request.get('/admin/course/list')
      const myName = (teacherName.value || '').replace(/\s+/g, '');
      myCourseList.value = (all || []).filter(c => (c.teacher || '').replace(/\s+/g, '').includes(myName));
    } else {
      myCourseList.value = list;
    }

    console.log("【调试】筛选后的课程:", myCourseList.value);

    // 刷新每个课程的状态
    myCourseList.value.forEach(c => refreshStatus(c.id))

    // 启动轮询
    if(!statusTimer) statusTimer = setInterval(refreshAllStatus, 3000)
  } catch(e){
    console.error("获取课程失败", e);
  }
}

const startClass = async (courseId) => {
  try {
    await request.post('/teacher/checkin/start', { courseId })
    ElMessage.success('上课开始！签到已开启')
    refreshStatus(courseId)
  } catch(e){ ElMessage.error('开启失败') }
}

const stopClass = async (courseId) => {
  try {
    await request.post('/teacher/checkin/stop', { courseId })
    ElMessage.success('签到已结束')
    refreshStatus(courseId)
  } catch(e){}
}

const refreshStatus = async (courseId) => {
  try {
    const res = await request.get(`/teacher/checkin/status/${courseId}`)
    checkInStatus[courseId] = res
  } catch(e){}
}

const refreshAllStatus = () => {
  if(activeMenu.value !== '6') return
  myCourseList.value.forEach(c => {
    if(checkInStatus[c.id]?.isActive) refreshStatus(c.id)
  })
}

const openClassroom = (courseId) => {
  router.push({ path: `/teacher/classroom/${courseId}` })
}

// --- 在线问答 ---
const openCoursePanel = async (course) => {
  activeCourse.value = course
  coursePanelVisible.value = true
  await fetchOnlineQuestions(course.id)
}

const fetchOnlineQuestions = async (courseId) => {
  try {
    onlineQuestions.value = await request.get('/teacher/online-questions', { params: { courseId } }) || []
    if (onlineQuestions.value.length) {
      await loadAnswers(onlineQuestions.value[0])
    } else {
      answers.value = []
      selectedQuestion.value = null
    }
  } catch (e) { ElMessage.error('加载问题失败') }
}

const publishQuestion = async () => {
  if (!questionForm.value.title) return ElMessage.warning('请输入问题标题')
  try {
    const payload = {
      courseId: activeCourse.value.id,
      title: questionForm.value.title,
      content: `[${modeLabel(questionForm.value.mode)}] ${questionForm.value.content || ''}`
    }
    await request.post('/teacher/online-question', payload)
    ElMessage.success('已发布在线问题')
    questionForm.value = { title: '', content: '', mode: 'broadcast' }
    await fetchOnlineQuestions(activeCourse.value.id)
  } catch (e) { ElMessage.error('发布失败') }
}

const loadAnswers = async (q) => {
  try {
    selectedQuestion.value = q
    answers.value = await request.get(`/teacher/online-question/${q.id}/answers`) || []
  } catch (e) { ElMessage.error('加载回答失败') }
}

const modeOptions = [
  { value: 'broadcast', label: '广播提问' },
  { value: 'assign', label: '点名提问' },
  { value: 'race', label: '抢答模式' },
  { value: 'hand', label: '学生举手' }
]
const modeLabel = (v) => (modeOptions.find(m => m.value === v)?.label) || v

// --- 初始化图表 ---
const initCharts = (chartData) => {
  const commonGrid = { top: '15%', left: '3%', right: '4%', bottom: '5%', containLabel: true }
  const textStyle = { color: '#606266' }

  if (pieChartRef.value && chartData.pieChart) {
    const pieChart = echarts.init(pieChartRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: '0%', left: 'center', icon: 'circle', textStyle },
      color: ['#409EFF', '#67C23A', '#E6A23C', '#909399'],
      series: [{
        name: '发言时长', type: 'pie', radius: ['50%', '75%'], center: ['50%', '45%'],
        avoidLabelOverlap: false, itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        data: chartData.pieChart
      }]
    })
    charts.push(pieChart)
  }

  if (lineChartRef.value && chartData.lineChart) {
    const lineChart = echarts.init(lineChartRef.value)
    lineChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: commonGrid,
      xAxis: { type: 'category', boundaryGap: false, data: ['08:00', '08:15', '08:30', '08:45', '09:00', '09:15', '09:30'] },
      yAxis: { type: 'value', min: 60, max: 100 },
      series: [{
        name: '专注度', type: 'line', smooth: true, symbolSize: 6,
        lineStyle: { width: 3, color: '#409EFF' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0,color:'rgba(64,158,255,0.3)'}, {offset:1,color:'rgba(64,158,255,0.02)'}]) },
        data: chartData.lineChart
      }]
    })
    charts.push(lineChart)
  }

  if (radarChartRef.value && chartData.radarChart) {
    const radarChart = echarts.init(radarChartRef.value)
    radarChart.setOption({
      tooltip: { trigger: 'item' },
      radar: {
        indicator: [
          { name: '出勤', max: 100 }, { name: '作业', max: 100 }, { name: '互动', max: 100 },
          { name: '测验', max: 100 }, { name: '创新', max: 100 }
        ],
        radius: '65%', center: ['50%', '55%']
      },
      series: [{
        type: 'radar',
        data: [
          { value: chartData.radarChart, name: '本班平均', itemStyle: { color: '#67C23A' }, areaStyle: { color: 'rgba(103, 194, 58, 0.2)' } }
        ]
      }]
    })
    charts.push(radarChart)
  }
}
const resizeCharts = () => charts.forEach(c => c.resize())

// --- API Calls ---
const fetchStudents = async () => {
  loading.value = true
  try {
    const res = await request.get('/teacher/students', { params: {
        keyword: keyword.value, classId: classFilter.value, pageNum: pageNum.value, pageSize: pageSize.value
      }})
    studentList.value = res.list || []
    total.value = res.total || 0
  } catch(e) { ElMessage.error('加载学生失败') }
  finally { loading.value = false }
}

const fetchDashboardData = async () => {
  try {
    const res = await request.get('/teacher/dashboard')
    if (res) {
      stats.value = res
      nextTick(() => initCharts(res))
    }
  } catch(e) { console.error(e) }
}

const fetchApplications = async () => {
  try { applicationList.value = await request.get('/teacher/my-applications') || [] } catch(e){}
}

const fetchMaterials = async () => {
  try { teachingMaterials.value = await request.get('/teacher/materials') || [] } catch(e){}
}

const fetchNotifications = async () => {
  try { notificationList.value = await request.get('/teacher/notifications') || [] } catch(e){}
}

const fetchExams = async () => {
  try { availableExams.value = await request.get('/teacher/exams') || [] } catch(e){}
}

const fetchCheatingRecords = async () => {
  if(!selectedExamId.value) return;
  try { cheatingRecords.value = await request.get(`/teacher/exam/${selectedExamId.value}/cheating-records`) || [] } catch(e){}
}

// 业务逻辑
const openApplyDialog = (type, row, autoSubmit = false) => {
  applyForm.value = { type, reason: '', newUsername: '', newRealName: '', newClassId: null, targetId: row?.userId }
  dialogTitle.value = type === 'ADD' ? '新增学生申请' : '操作确认'
  dialogVisible.value = true
}

const submitApplication = async () => {
  if(!applyForm.value.reason) return ElMessage.warning('请填写理由')
  let content = applyForm.value.type === 'ADD'
      ? `新增学生: ${applyForm.value.newRealName} (${applyForm.value.newUsername}) 到班级 ${applyForm.value.newClassId}`
      : `对学生ID ${applyForm.value.targetId} 进行 ${applyForm.value.type} 操作`

  await request.post('/teacher/apply', { ...applyForm.value, content })
  ElMessage.success('申请已提交')
  dialogVisible.value = false
  fetchApplications()
}

const openSubmissionDialog = async (material) => {
  currentMaterial.value = material
  const res = await request.get(`/teacher/material/${material.id}/submissions`)
  submissions.value = res.map(s => ({
    ...s, answerText: JSON.parse(s.record.userAnswers).text, gradeForm: { score: s.record.score || 0 }
  }))
  submissionDialogVisible.value = true
}

const submitGrade = async (row) => {
  await request.post('/teacher/grade', { id: row.record.id, score: row.gradeForm.score })
  ElMessage.success('评分完成')
  row.record.score = row.gradeForm.score
}

const formatTime = (t) => t ? dayjs(t).fromNow() : ''
const formatType = (t) => ({ADD:'新增',DELETE:'删除',RESET_PWD:'重置',DEADLINE_EXTENSION:'延期'}[t]||t)
const formatStatus = (s) => ({PENDING:'审核中',APPROVED:'通过',REJECTED:'驳回'}[s]||s)
const getStatusType = (s) => ({APPROVED:'success',REJECTED:'danger',PENDING:'warning'}[s]||'info')
const getMaterialTypeTag = (t) => ({'作业':'primary','测验':'warning'}[t]||'info')
const handleSizeChange = (v) => { pageSize.value = v; fetchStudents() }
const handleCurrentChange = (v) => { pageNum.value = v; fetchStudents() }
const logout = () => { localStorage.clear(); router.push('/login') }
</script>

<style scoped lang="scss">
/* ====== 全局定义 ====== */
$light-bg: #f5f7fa;
$white: #ffffff;
$primary: #409EFF;
$success: #67C23A;
$warning: #E6A23C;
$danger: #F56C6C;
$card-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);

.teacher-container-light {
  display: flex; height: 100vh; background-color: $light-bg;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; color: #303133;
}

.dashboard-card {
  background: $white; border-radius: 12px; box-shadow: $card-shadow; transition: all 0.3s;
  &:hover { box-shadow: 0 6px 16px rgba(0,0,0,0.08); }
}

/* ====== 侧边栏 ====== */
.sidebar-light {
  background: $white; border-right: 1px solid #e6e6e6; z-index: 10;
  .brand-area {
    height: 64px; display: flex; align-items: center; justify-content: center; gap: 10px; border-bottom: 1px solid #f0f0f0;
    .brand-icon { width: 32px; height: 32px; background: rgba(64, 158, 255, 0.1); border-radius: 8px; display: flex; align-items: center; justify-content: center; }
    .brand-text { font-size: 18px; font-weight: bold; }
  }
  .el-menu-vertical-light { border-right: none; padding-top: 15px; }
  :deep(.el-menu-item) {
    margin: 6px 12px; border-radius: 8px; height: 48px; font-weight: 500;
    &:hover { background-color: #ecf5ff; color: $primary; }
    &.is-active { background-color: #ecf5ff; color: $primary; font-weight: bold; border-left: 4px solid $primary; padding-left: 16px; }
    .el-icon { font-size: 18px; margin-right: 10px; }
  }
}

/* ====== 主内容区 ====== */
.main-content-light { padding: 24px; flex: 1; overflow-y: auto; background-color: $light-bg; }

.header-bar-light {
  padding: 20px 30px; margin-bottom: 24px; display: flex; justify-content: space-between; align-items: center;
  .greeting { font-size: 20px; font-weight: bold; margin: 0 0 8px 0; }
  .date-badge { font-size: 13px; color: #909399; }
  .header-actions {
    display: flex; align-items: center; gap: 25px;
    .icon-btn { font-size: 18px; &:hover { color: $primary; background: #ecf5ff; } }
    .user-profile-area { display: flex; align-items: center; gap: 10px; cursor: pointer; }
  }
}

/* ====== 仪表盘 ====== */
.metrics-row-light {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; margin-bottom: 24px;
  .metric-card-pro {
    padding: 24px; display: flex; align-items: center; gap: 20px; border-radius: 12px; color: #fff;
    box-shadow: 0 4px 12px rgba(0,0,0,0.1); position: relative; overflow: hidden;

    &.bg-gradient-blue { background: linear-gradient(135deg, #409EFF, #79bbff); }
    &.bg-gradient-green { background: linear-gradient(135deg, #67C23A, #95d475); }
    &.bg-gradient-orange { background: linear-gradient(135deg, #E6A23C, #f3d19e); }
    &.bg-gradient-purple { background: linear-gradient(135deg, #909399, #b1b3b8); }

    .metric-icon-bg {
      position: absolute; right: -10px; bottom: -15px; font-size: 90px; color: rgba(255,255,255,0.2); transform: rotate(-15deg);
    }
    .metric-content {
      position: relative; z-index: 2; flex: 1;
      .label { font-size: 14px; opacity: 0.9; margin-bottom: 8px; font-weight: 500; }
      .value-group { display: flex; align-items: baseline; .number { font-size: 32px; font-weight: 800; line-height: 1; } .unit { margin-left: 6px; font-size: 14px; opacity: 0.9; } }
      .trend { margin-top: 10px; font-size: 13px; display: flex; align-items: center; gap: 4px; opacity: 0.95; }
    }
  }
}

.charts-row-light {
  margin-bottom: 24px;
  .chart-card-light {
    padding: 20px;
    .card-header-light {
      margin-bottom: 20px;
      .icon-title { font-size: 16px; font-weight: bold; border-left: 4px solid $primary; padding-left: 12px; }
      .header-title-group { display: flex; align-items: center; }
      .deco-bar { width: 4px; height: 16px; border-radius: 2px; margin-right: 10px; }
      .bg-blue { background-color: $primary; }
      .bg-green { background-color: $success; }
      .bg-purple { background-color: #909399; }
    }
    .chart-box-light { height: 280px; width: 100%; }
  }
}

.table-section-light {
  padding: 24px;
  .section-header-light {
    display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
    .title { font-size: 18px; font-weight: bold; margin: 0; }
    .filters-light { display: flex; gap: 12px; }
  }
  .pagination-box-light { margin-top: 20px; display: flex; justify-content: flex-end; }
}

.content-block-light {
  padding: 24px;
  .block-header-light {
    margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center;
    .header-title-group { display: flex; align-items: center; }
    .deco-bar { width: 4px; height: 18px; background-color: $primary; margin-right: 10px; border-radius: 2px; }
    .title { font-size: 18px; font-weight: bold; margin: 0; }
  }
}

/* 课程卡片 (新) */
.course-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; }
.course-card-pro {
  background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); transition: all 0.3s; border: 1px solid #ebeef5;
  &:hover { transform: translateY(-5px); box-shadow: 0 10px 25px rgba(0,0,0,0.1); border-color: $primary; }
}
.c-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 1px solid #f0f0f0; padding-bottom: 15px; }
.c-name { font-size: 18px; font-weight: bold; color: #303133; }
.c-body { text-align: center; }
.info-row { color: #606266; margin-bottom: 8px; font-size: 14px; display: flex; align-items: center; justify-content: center; gap: 8px; .bold { font-weight: bold; color: $primary; } }
.checkin-control { margin-top: 25px; background: #ecf5ff; padding: 20px; border-radius: 8px; border: 1px dashed #409EFF; }
.active-badge { color: #67C23A; font-weight: bold; margin-bottom: 10px; display: flex; align-items: center; justify-content: center; gap: 6px; }
.pulse-dot { width: 8px; height: 8px; background: #67C23A; border-radius: 50%; animation: pulse 1.5s infinite; }
.stats-num { margin: 15px 0; }
.stats-num .big { font-size: 32px; font-weight: 800; color: #409EFF; }
.stats-num .small { font-size: 14px; color: #909399; margin-left: 5px; }
.rate-bar { font-size: 13px; color: #606266; margin-bottom: 10px; }
.start-control { margin-top: 40px; }
.start-btn { width: 100%; height: 45px; font-size: 16px; box-shadow: 0 4px 10px rgba(64,158,255,0.3); }
.empty-course { text-align: center; padding: 40px; }

/* 在线课程问答 */
.qa-layout { display: grid; grid-template-columns: 1.2fr 1fr; gap: 16px; }
.qa-form { background: #f7f9fc; padding: 12px; border-radius: 8px; margin-bottom: 12px; }
.qa-list { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.qa-list-header { font-weight: 600; margin-bottom: 8px; color: #303133; }
.qa-item { padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; cursor: pointer; transition: all .2s; }
.qa-item:hover { border-color: $primary; background: #f5faff; }
.qa-item.is-active { border-color: $primary; box-shadow: 0 4px 10px rgba(64,158,255,0.15); }
.qa-title { font-weight: 600; color: #303133; margin-bottom: 4px; }
.qa-meta { font-size: 12px; color: #909399; }
.qa-right { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; min-height: 300px; display: flex; flex-direction: column; gap: 12px; }
.qa-answer { padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; background: #f9fafc; }
.qa-answer-user { font-weight: 600; margin-bottom: 6px; color: #409EFF; }
.qa-answer-text { color: #303133; font-size: 14px; margin-bottom: 4px; }
.qa-empty { color: #909399; padding: 20px 0; text-align: center; }
.qa-tip { font-size: 13px; color: #909399; }

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(103, 194, 58, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(103, 194, 58, 0); }
}

:deep(.light-table-header) th { background-color: #f5f7fa !important; color: #606266; font-weight: 600; }
.mr-1 { margin-right: 4px; }
.mr-2 { margin-right: 8px; }
.material-name { display: flex; align-items: center; font-weight: 500; }
.answer-text-light { background: #f5f7fa; padding: 10px; border-radius: 4px; color: #606266; font-size: 13px; line-height: 1.5; max-height: 80px; overflow-y: auto; }
.dialog-form-light { padding: 0 10px; }
.fade-in { animation: fadeIn 0.5s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
</style>
