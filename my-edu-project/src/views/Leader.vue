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
          <div class="header-buttons">
            <el-button type="warning" @click="openBatchAssignDialog" style="margin-right: 10px;">批量分配课程</el-button>
            <el-button type="primary" @click="openCourseDialog">+ 发布新课程</el-button>
          </div>
        </div>

        <el-table :data="courseList" border stripe style="width: 100%">
          <el-table-column prop="name" label="课程名称" width="180" />
          <el-table-column prop="classId" label="所属班级" width="100" />
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

              <el-popconfirm title="确定删除该课程吗？" @confirm="handleDelete(scope.row.id)">
                <template #reference>
                  <el-button size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>

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

          <el-form-item label="所属班级">
            <el-select v-model="courseForm.classId" placeholder="请选择所属班级 (必填)" style="width: 100%">
              <el-option
                  v-for="c in classList"
                  :key="c.id"
                  :label="c.name + ' (ID: ' + c.id + ')'"
                  :value="c.id"
              />
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
        <p style="margin-bottom: 5px">当前课程：{{ currentRow.name }}</p>
        <p style="margin-bottom: 15px; font-weight: bold;">所属班级ID: {{ currentRow.classId }}</p>
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

      <el-dialog v-model="batchAssignDialogVisible" title="批量分配/复制课程" width="600px">
        <el-form :model="batchAssignForm" label-width="100px">
          <el-form-item label="课程名称">
            <el-input v-model="batchAssignForm.name" placeholder="例如：高级Java程序设计" />
          </el-form-item>
          <el-form-item label="所属学期">
            <el-select v-model="batchAssignForm.semester" placeholder="请选择学期" style="width: 100%">
              <el-option label="2025-2026学年 第1学期" value="2025-1" />
              <el-option label="2024-2025学年 第2学期" value="2024-2" />
            </el-select>
          </el-form-item>

          <el-form-item label="分配教师">
            <el-select v-model="batchAssignForm.teacherNames" multiple placeholder="请选择主讲教师 (可多选)" style="width: 100%">
              <el-option
                  v-for="t in teacherList"
                  :key="t.userId"
                  :label="t.realName"
                  :value="t.realName"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="目标班级">
            <el-select v-model="batchAssignForm.classIds" multiple placeholder="请选择要分配的班级 (可多选)" style="width: 100%">
              <el-option
                  v-for="c in classList"
                  :key="c.id"
                  :label="c.name + ' (ID: ' + c.id + ')'"
                  :value="c.id"
              />
            </el-select>
          </el-form-item>

        </el-form>
        <template #footer>
          <el-button @click="batchAssignDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitBatchAssign">确认批量分配</el-button>
        </template>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { DataLine, UserFilled, Reading } from '@element-plus/icons-vue' // 移除 Notebook, 保持当前图标
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userInfo = ref({})
const courseList = ref([])
const teacherList = ref([]) // 包含 Leader 和 Teacher
const classList = ref([]) // 存储班级列表

// 弹窗控制
const courseDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const batchAssignDialogVisible = ref(false)
const courseForm = ref({ name: '', semester: '2025-1', teacher: '', classId: null })
const currentRow = ref({})
const selectedTeacher = ref('')
const batchAssignForm = reactive({ name: '', semester: '2025-1', teacherNames: [], classIds: [] })


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

    // 2. 获取教师和组长列表 (LeaderController 接口返回 Role=2 和 Role=3)
    const resTeachers = await request.get('/leader/teacher/list')
    teacherList.value = resTeachers || []

    // 3. 获取所有已创建的班级列表 (复用 Admin 接口)
    // 注意：AdminController 中的 /admin/classes 接口返回的班级列表用于课程分配
    const resClasses = await request.get('/admin/classes');
    classList.value = Array.isArray(resClasses) ? resClasses : [];

  } catch (error) {
    console.error(error)
    ElMessage.error('数据加载失败，请检查后端服务');
  }
}

// === 发布课程 ===
const openCourseDialog = () => {
  courseForm.value = { name: '', semester: '2025-1', teacher: '', classId: null }
  courseDialogVisible.value = true
}

const submitCourse = async () => {
  if(!courseForm.value.name) return ElMessage.warning('请填写课程名称')
  if(!courseForm.value.classId) return ElMessage.warning('请选择所属班级')
  try {
    // 调用 Leader Controller 的 add 接口
    await request.post('/leader/course/add', courseForm.value)
    ElMessage.success('课程发布成功')
    courseDialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e.response?.data || '课程发布失败')
  }
}

// === 单个分配教师 ===
const openAssignDialog = (row) => {
  currentRow.value = row
  selectedTeacher.value = row.teacher || ''
  assignDialogVisible.value = true
}

const submitAssign = async () => {
  if(!selectedTeacher.value) return ElMessage.warning('请选择任课教师')
  try {
    // 传递 classId 和 courseId，以便后端更新 teaching_classes
    await request.post('/leader/course/update', {
      id: currentRow.value.id,
      teacher: selectedTeacher.value,
      classId: currentRow.value.classId
    })
    ElMessage.success('教师分配成功，执教班级已同步更新')
    assignDialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e.response?.data || '分配教师失败')
  }
}

// === 批量分配 (核心下发) ===
const openBatchAssignDialog = () => {
  batchAssignForm.name = '';
  batchAssignForm.semester = '2025-1';
  batchAssignForm.teacherNames = [];
  batchAssignForm.classIds = [];
  batchAssignDialogVisible.value = true;
}

const submitBatchAssign = async () => {
  const form = batchAssignForm;
  if (!form.name || form.teacherNames.length === 0 || form.classIds.length === 0) {
    return ElMessage.warning('请填写课程名称，并选择至少一位教师和至少一个班级');
  }

  try {
    // 调用 Leader Controller 的批量分配接口
    await request.post('/leader/course/batch-assign', {
      name: form.name,
      semester: form.semester,
      teacherNames: form.teacherNames,
      classIds: form.classIds
    });
    ElMessage.success(`成功分配课程给 ${form.classIds.length} 个班级，教师执教班级已同步更新。`);
    batchAssignDialogVisible.value = false;
    fetchData();
  } catch (e) {
    ElMessage.error(`批量分配失败`);
  }
}


// === 删除课程 ===
const handleDelete = async (id) => {
  try {
    // 调用 Leader Controller 的删除接口
    await request.post(`/leader/course/delete/${id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    ElMessage.error(e.response?.data || '删除失败')
  }
}

const logout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped lang="scss">
// 样式保持不变
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
    .header-buttons { display: flex; }
  }
}
</style>