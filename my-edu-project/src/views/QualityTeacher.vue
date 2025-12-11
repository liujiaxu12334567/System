<template>
  <div class="qt-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">素质教育中心</div>
      <el-menu default-active="1" class="el-menu-vertical" background-color="#2a2d43" text-color="#fff" active-text-color="#ffd04b">
        <el-menu-item index="1"><el-icon><List /></el-icon>待办审批</el-menu-item>
        <el-menu-item index="2"><el-icon><User /></el-icon>我的班级 (4个)</el-menu-item>
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
import { ref, reactive, onMounted } from 'vue' // 引入 reactive
import { useRouter } from 'vue-router'
import { List, User, Bell } from '@element-plus/icons-vue' // 引入 Bell 图标
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref('credit')
const creditList = ref([])
const leaveList = ref([])

// 【新增】通知相关状态
const notifyDialogVisible = ref(false)
const notifyForm = reactive({
  title: '',
  content: ''
})

onMounted(() => {
  fetchApplications()
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
</script>

<style scoped>
.qt-container { display: flex; height: 100vh; background: #f0f2f5; }
.sidebar { background-color: #2a2d43; color: #fff; }
.logo { height: 60px; line-height: 60px; text-align: center; font-weight: bold; background: #1f2233; }
.main-content { padding: 20px; flex: 1; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.header-actions { display: flex; gap: 10px; } /* 新增样式 */
.text-gray { color: #666; font-size: 13px; }
</style>