<template>
  <div class="qt-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">素质教育中心</div>
      <el-menu default-active="1" class="el-menu-vertical" background-color="#2a2d43" text-color="#fff" active-text-color="#ffd04b">
        <el-menu-item index="1"><el-icon><List /></el-icon>待办审批</el-menu-item>
        <el-menu-item index="2"><el-icon><User /></el-icon>我的班级 ({{ myClasses.length }}个)</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header">
        <h2>审批工作台</h2>
        <div class="header-actions">
          <el-button type="warning" @click="openNotifyDialog">
            <el-icon style="margin-right: 5px"><Bell /></el-icon> 发布通知
          </el-button>
          <el-button type="danger" plain @click="logout">退出登录</el-button>
        </div>
      </div>

      <el-card class="my-class-card" shadow="hover">
        <div class="card-header-row">
          <h3>我的班级（管理员分配）</h3>
          <span class="badge">{{ myClasses.length }} 个</span>
        </div>
        <div class="class-tags">
          <el-tag
              v-for="cls in myClasses"
              :key="cls"
              type="info"
              effect="plain"
              class="class-tag clickable"
              @click="openRecordDialog(cls)"
          >班级 {{ cls }}</el-tag>
          <span v-if="!myClasses.length" class="empty-tip">暂无分配班级，请联系管理员</span>
        </div>
      </el-card>

      <el-card class="attendance-card" shadow="hover">
        <div class="card-header-row">
          <h3>班级出勤率</h3>
          <el-button type="primary" size="small" @click="fetchAttendance">刷新</el-button>
        </div>
        <el-table :data="attendanceList" border stripe>
          <el-table-column prop="classId" label="班级" width="120">
            <template #default="{row}">班级 {{ row.classId }}</template>
          </el-table-column>
          <el-table-column prop="present" label="实到" width="120" />
          <el-table-column prop="expected" label="应到" width="120" />
          <el-table-column prop="rate" label="出勤率" width="140">
            <template #default="{row}">{{ row.rate }}%</template>
          </el-table-column>
        </el-table>
        <div v-if="!attendanceList.length" class="empty-tip">暂无出勤数据</div>
      </el-card>

      <el-dialog v-model="recordDialogVisible" title="班级出勤明细" width="900px" destroy-on-close>
        <div class="filter-row">
          <div>班级：{{ selectedClassId }}</div>
          <el-select v-model="recordFilter.courseId" placeholder="按课程筛选" clearable style="width: 200px" @change="fetchAttendanceRecords">
            <el-option v-for="c in recordCourseOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-select v-model="recordFilter.studentId" placeholder="按学生筛选" clearable style="width: 200px" @change="fetchAttendanceRecords">
            <el-option v-for="s in recordStudentOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" @click="fetchAttendanceRecords" :loading="recordLoading">刷新</el-button>
        </div>
        <el-table :data="attendanceRecords" border stripe v-loading="recordLoading" height="400">
          <el-table-column prop="courseName" label="课程" width="200" />
          <el-table-column prop="studentName" label="学生" width="150" />
          <el-table-column prop="date" label="日期" width="140" />
          <el-table-column prop="present" label="出勤" width="120">
            <template #default="{row}">
              <el-tag :type="row.present ? 'success' : 'danger'" effect="plain">{{ row.present ? '到' : '缺' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!recordLoading && attendanceRecords.length === 0" class="empty-tip">暂无明细</div>
      </el-dialog>

      <el-tabs v-model="activeTab" type="card">
        <el-tab-pane label="素质学分审批" name="credit">
          <el-table :data="creditList" border stripe>
            <el-table-column prop="teacherName" label="学生姓名" width="120" />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{row}">
                <el-tag :type="row.type === 'QUALITY_COMPETITION' ? 'success' : 'info'">
                  {{ row.type === 'QUALITY_COMPETITION' ? '学科比赛' : '素质活动' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请内容" min-width="200">
              <template #default="{row}">
                <div><strong>{{ JSON.parse(row.content).title }}</strong></div>
                <div class="text-gray">{{ JSON.parse(row.content).desc }}</div>
              </template>
            </el-table-column>
            <el-table-column label="证明材料" width="120">
              <template #default="{row}">
                <el-image
                    v-if="JSON.parse(row.content).img"
                    style="width: 50px; height: 50px"
                    :src="JSON.parse(row.content).img"
                    :preview-src-list="[JSON.parse(row.content).img]"
                    fit="cover" />
                <span v-else>无</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{row}">
                <el-button type="success" size="small" @click="handleReview(row.id, 'APPROVED')">通过</el-button>
                <el-button type="danger" size="small" @click="handleReview(row.id, 'REJECTED')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="请假审批" name="leave">
          <el-table :data="leaveList" border stripe>
            <el-table-column prop="teacherName" label="学生姓名" width="120" />
            <el-table-column label="详情" min-width="250">
              <template #default="{row}">
                <div v-if="row.content">
                  <el-tag size="small" effect="dark">{{ JSON.parse(row.content).leaveType === 'SICK' ? '病假' : '事假' }}</el-tag>
                  <el-tag size="small" type="warning" style="margin-left:5px">{{ JSON.parse(row.content).isLeaving ? '离校' : '在校' }}</el-tag>
                  <p>联系方式: {{ JSON.parse(row.content).contact }}</p>
                  <p>原因: {{ row.reason }}</p>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="申请时间" width="180" />
            <el-table-column label="操作" width="180">
              <template #default="{row}">
                <el-button type="success" size="small" @click="handleReview(row.id, 'APPROVED')">批准</el-button>
                <el-button type="danger" size="small" @click="handleReview(row.id, 'REJECTED')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <el-dialog v-model="notifyDialogVisible" title="下发通知 (给负责班级)" width="500px">
        <el-form label-width="80px">
          <el-form-item label="标题">
            <el-input v-model="notifyForm.title" placeholder="请输入通知标题" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="notifyForm.content" type="textarea" :rows="4" placeholder="请输入通知内容" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="notifyDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitNotification">发送</el-button>
        </template>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue' // 引入 reactive
import { useRouter } from 'vue-router'
import { List, User, Bell } from '@element-plus/icons-vue' // 引入 Bell 图标
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref('credit')
const creditList = ref([])
const leaveList = ref([])
const attendanceList = ref([])
const attendanceRecords = ref([])
const recordDialogVisible = ref(false)
const recordLoading = ref(false)
const selectedClassId = ref(null)
const recordFilter = reactive({ courseId: null, studentId: null })
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
const myClasses = computed(() => {
  const cls = userInfo.teachingClasses || ''
  return cls
    ? cls.split(',').map(s => s.trim()).filter(Boolean)
    : []
})

// 【新增】通知相关状态
const notifyDialogVisible = ref(false)
const notifyForm = reactive({
  title: '',
  content: ''
})

onMounted(() => {
  fetchApplications()
  fetchAttendance()
})

const fetchApplications = async () => {
  try {
    const res = await request.get('/quality/teacher/applications')
    creditList.value = res.filter(item => item.type.startsWith('QUALITY_'))
    leaveList.value = res.filter(item => item.type === 'LEAVE_APPLICATION')
  } catch(e) {
    ElMessage.error('加载数据失败')
  }
}

const handleReview = async (id, status) => {
  try {
    await request.post('/quality/teacher/review', { id, status })
    ElMessage.success('操作成功')
    fetchApplications()
  } catch(e) {
    ElMessage.error('操作失败')
  }
}

// 【新增】打开通知弹窗
const openNotifyDialog = () => {
  notifyForm.title = ''
  notifyForm.content = ''
  notifyDialogVisible.value = true
}

// 【新增】提交通知
const submitNotification = async () => {
  if(!notifyForm.title || !notifyForm.content) return ElMessage.warning('请填写完整')

  try {
    await request.post('/quality/teacher/notify', notifyForm)
    ElMessage.success('通知已下发给所有负责班级的学生')
    notifyDialogVisible.value = false
  } catch(e) {
    ElMessage.error(e.response?.data || '发送失败')
  }
}

const logout = () => {
  localStorage.clear()
  router.push('/login')
}

const fetchAttendance = async () => {
  try {
    attendanceList.value = await request.get('/quality/teacher/attendance')
  } catch (e) {
    ElMessage.error('加载出勤率失败')
  }
}

const recordCourseOptions = computed(() => {
  const set = new Map()
  attendanceRecords.value.forEach(r => {
    if (r.courseId && r.courseName) set.set(r.courseId, r.courseName)
  })
  return Array.from(set, ([value, label]) => ({ value, label }))
})
const recordStudentOptions = computed(() => {
  const set = new Map()
  attendanceRecords.value.forEach(r => {
    if (r.studentId && r.studentName) set.set(r.studentId, r.studentName)
  })
  return Array.from(set, ([value, label]) => ({ value, label }))
})

const openRecordDialog = async (clsId) => {
  selectedClassId.value = clsId
  recordFilter.courseId = null
  recordFilter.studentId = null
  recordDialogVisible.value = true
  await fetchAttendanceRecords()
}

const fetchAttendanceRecords = async () => {
  if (!selectedClassId.value) return
  recordLoading.value = true
  try {
    const params = { classId: selectedClassId.value }
    if (recordFilter.courseId) params.courseId = recordFilter.courseId
    if (recordFilter.studentId) params.studentId = recordFilter.studentId
    attendanceRecords.value = await request.get('/quality/teacher/attendance/records', { params })
    if (!attendanceRecords.value || attendanceRecords.value.length === 0) {
      ElMessage.info('暂无出勤明细')
    }
  } catch (e) {
    ElMessage.error(e.response?.data || '加载出勤明细失败')
  } finally {
    recordLoading.value = false
  }
}
</script>

<style scoped>
.qt-container { display: flex; height: 100vh; background: #f0f2f5; }
.sidebar { background-color: #2a2d43; color: #fff; }
.logo { height: 60px; line-height: 60px; text-align: center; font-weight: bold; background: #1f2233; }
.main-content { padding: 20px; flex: 1; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.header-actions { display: flex; gap: 10px; } /* 新增样式 */
.text-gray { color: #666; font-size: 13px; }
.my-class-card { margin-bottom: 16px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.badge { background: #f0f2f5; padding: 4px 10px; border-radius: 999px; font-size: 12px; color: #606266; }
.class-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.class-tag { background: #f5f7fa; border-color: #dcdfe6; color: #606266; }
.class-tag.clickable { cursor: pointer; }
.empty-tip { color: #909399; font-size: 13px; }
.attendance-card { margin-bottom: 16px; }
.filter-row { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
</style>
