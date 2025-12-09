<template>
  <div class="leader-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">课题组管理中心</div>
      <el-menu default-active="1" class="el-menu-vertical" background-color="#2a2d43" text-color="#bfcbd9" active-text-color="#ffffff">
        <el-menu-item index="1"><el-icon><DataLine /></el-icon>课程管理</el-menu-item>
        <el-menu-item index="2"><el-icon><UserFilled /></el-icon>教师调配</el-menu-item>
        <el-menu-item index="3"><el-icon><Reading /></el-icon>考试安排</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header-bar">
        <div class="breadcrumb">首页 / 课题组管理</div>
        <div class="user-profile">
          <span>欢迎您，{{ userInfo.realName || '组长' }}</span>
          <el-button link type="primary" @click="logout" style="margin-left: 15px">退出</el-button>
        </div>
      </div>

      <div class="content-panel">
        <div class="panel-header">
          <h3>课程管理与排课</h3>
          <el-button type="primary" @click="openCourseDialog">+ 发布新课程</el-button>
        </div>

        <el-table :data="courseList" border stripe style="width: 100%">
          <el-table-column prop="name" label="课程名称" width="180" />
          <el-table-column prop="code" label="课程代码" width="100" />
          <el-table-column prop="semester" label="学期" width="150" />
          <el-table-column prop="teacher" label="任课教师">
            <template #default="scope">
              <el-tag v-if="scope.row.teacher" type="success">{{ scope.row.teacher }}</el-tag>
              <el-tag v-else type="info">未分配</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-tag effect="plain">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250">
            <template #default="scope">
              <el-button size="small" type="warning" @click="openAssignDialog(scope.row)">分配教师</el-button>
              <el-button size="small" type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
              <el-button size="small" type="primary" plain>发布考试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-dialog v-model="courseDialogVisible" title="发布新课程" width="500px">
        <el-form :model="courseForm" label-width="80px">
          <el-form-item label="课程名称">
            <el-input v-model="courseForm.name" placeholder="例如：高级Java程序设计" />
          </el-form-item>
          <el-form-item label="所属学期">
            <el-select v-model="courseForm.semester" placeholder="请选择学期" style="width: 100%">
              <el-option label="2025-2026学年 第1学期" value="2025-1" />
              <el-option label="2024-2025学年 第2学期" value="2024-2" />
            </el-select>
          </el-form-item>
          <el-form-item label="主讲教师">
            <el-select v-model="courseForm.teacher" placeholder="请选择(可选)" style="width: 100%">
              <el-option
                  v-for="t in teacherList"
                  :key="t.userId"
                  :label="t.realName"
                  :value="t.realName"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="courseDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitCourse">确认发布</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="assignDialogVisible" title="分配任课教师" width="400px">
        <p style="margin-bottom: 15px">当前课程：{{ currentRow.name }}</p>
        <el-select v-model="selectedTeacher" placeholder="请选择教师" style="width: 100%">
          <el-option
              v-for="t in teacherList"
              :key="t.userId"
              :label="t.realName"
              :value="t.realName"
          />
        </el-select>
        <template #footer>
          <el-button @click="assignDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAssign">确认分配</el-button>
        </template>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DataLine, UserFilled, Reading, Notebook } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userInfo = ref({})
const courseList = ref([])
const teacherList = ref([])

// 弹窗控制
const courseDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const courseForm = ref({ name: '', semester: '', teacher: '' })
const currentRow = ref({})
const selectedTeacher = ref('')

onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch(e) {}
  }
  fetchData()
})

const fetchData = async () => {
  try {
    // 1. 获取课程列表
    const resCourses = await request.get('/leader/course/list')
    courseList.value = resCourses || []

    // 2. 获取教师列表 (Role=3)
    const resTeachers = await request.get('/leader/teacher/list')
    teacherList.value = resTeachers || []
  } catch (error) {
    console.error(error)
  }
}

// === 发布课程 ===
const openCourseDialog = () => {
  courseForm.value = { name: '', semester: '2025-1', teacher: '' }
  courseDialogVisible.value = true
}

const submitCourse = async () => {
  if(!courseForm.value.name) return ElMessage.warning('请填写课程名称')
  try {
    await request.post('/leader/course/add', courseForm.value)
    ElMessage.success('课程发布成功')
    courseDialogVisible.value = false
    fetchData()
  } catch (e) {}
}

// === 分配教师 ===
const openAssignDialog = (row) => {
  currentRow.value = row
  selectedTeacher.value = row.teacher || ''
  assignDialogVisible.value = true
}

const submitAssign = async () => {
  try {
    await request.post('/leader/course/update', {
      id: currentRow.value.id,
      teacher: selectedTeacher.value
    })
    ElMessage.success('教师分配成功')
    assignDialogVisible.value = false
    fetchData()
  } catch (e) {}
}

// === 删除课程 ===
const handleDelete = async (id) => {
  try {
    await request.post(`/leader/course/delete/${id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {}
}

const logout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped lang="scss">
.leader-container { display: flex; height: 100vh; background-color: #f0f2f5; }
.sidebar { background-color: #2a2d43; color: #fff; display: flex; flex-direction: column;
  .logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #1f2233; color: #fff; }
  .el-menu-vertical { border-right: none; }
}
.main-content { padding: 0; display: flex; flex-direction: column; width: 100%; }
.header-bar { height: 60px; background: #fff; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; box-shadow: 0 1px 4px rgba(0,21,41,.08);
  .breadcrumb { color: #97a8be; font-size: 14px; }
  .user-profile { font-size: 14px; color: #333; }
}
.content-panel { margin: 20px; background: #fff; padding: 20px; border-radius: 4px; flex: 1;
  .panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
    h3 { margin: 0; font-size: 16px; border-left: 4px solid #409EFF; padding-left: 10px; }
  }
}
</style>