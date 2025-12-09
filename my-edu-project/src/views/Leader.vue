<template>
  <div class="leader-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">课题组管理中心</div>
      <el-menu default-active="1" class="el-menu-vertical" background-color="#2a2d43" text-color="#bfcbd9" active-text-color="#ffffff">
        <el-menu-item index="1"><el-icon><DataLine /></el-icon>我的课程管理</el-menu-item>
        <el-menu-item index="2"><el-icon><UserFilled /></el-icon>课题组成员</el-menu-item>
        <el-menu-item index="3"><el-icon><Reading /></el-icon>团队资料</el-menu-item>

        <el-divider style="margin: 10px 0; border-color: #444761;" />
        <div class="switch-to-teacher">
          <el-button type="warning" plain @click="goToTeacherPage">
            <el-icon style="margin-right: 5px;"><Switch /></el-icon> 切换到教师工作台
          </el-button>
        </div>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header-bar">
        <div class="breadcrumb">首页 / 课程资料下发</div>
        <div class="user-profile">
          <span>欢迎您，{{ userInfo.realName || '组长' }}</span>
          <el-button link type="primary" @click="logout" style="margin-left: 15px">退出</el-button>
        </div>
      </div>

      <div class="content-panel">
        <div class="panel-header">
          <h3>我的课程列表与内容下发</h3>
        </div>

        <el-alert title="说明：您只能管理您在教师列表中承担任课任务的课程。" type="info" show-icon style="margin-bottom: 20px;" />

        <el-table :data="courseList" border stripe style="width: 100%">
          <el-table-column prop="name" label="课程名称" width="180" />
          <el-table-column prop="classId" label="所属班级" width="100" />
          <el-table-column prop="semester" label="学期" width="150" />
          <el-table-column prop="teacher" label="任课教师" min-width="150" />
          <el-table-column label="操作" width="280">
            <template #default="scope">
              <el-button size="small" type="success" plain @click="openContentDialog(scope.row)">下发资料/测验</el-button>

              <el-button size="small" type="warning" @click="openAssignDialog(scope.row)">调整教师</el-button>

              <el-popconfirm title="确定删除该课程吗？" @confirm="handleDelete(scope.row.id)">
                <template #reference>
                  <el-button size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-dialog v-model="contentDialogVisible" :title="'下发内容 - ' + currentRow.name + ' (' + currentRow.classId + ')'" width="750px">
        <el-alert title="请选择您要下发的内容类型，并点击提交按钮下发给学生。" type="warning" :closable="false" style="margin-bottom: 20px;" />

        <div class="content-tabs">
          <el-button
              v-for="item in contentTypes"
              :key="item"
              :type="selectedContentType === item ? 'primary' : 'default'"
              @click="selectedContentType = item"
              style="margin: 0 10px 10px 0"
          >
            {{ item }}
          </el-button>
        </div>

        <el-form label-width="100px" style="margin-top: 20px;">
          <el-form-item :label="selectedContentType">
            <el-input
                v-model="contentPayload"
                :rows="3"
                type="textarea"
                placeholder="在此处填写资料链接、测验题目描述或上传文件..."
            />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="contentDialogVisible = false">取消</el-button>
          <el-button type="success" @click="submitCourseMaterial">提交并下发内容</el-button>
        </template>
      </el-dialog>


      <el-dialog v-model="assignDialogVisible" title="调整任课教师" width="400px">
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
          <el-button type="primary" @click="submitAssign">确认调整</el-button>
        </template>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
// 导入 Switch 图标
import { DataLine, UserFilled, Reading, Setting, Switch } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userInfo = ref({})
const courseList = ref([])
const teacherList = ref([])
// const classList = ref([]) // 班级列表 (在此组件中未使用，保持注释)

// 弹窗控制
const contentDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const currentRow = ref({})
const selectedTeacher = ref('')

// 内容下发状态
const contentTypes = ref(['导学', '教材', '教学目标', '知识图谱', '目录', 'FAQ', '学习资料', '测验', '作业', '项目'])
const selectedContentType = ref('导学')
const contentPayload = ref('')


onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch(e) {}
  }
  fetchData()
})

const fetchData = async () => {
  try {
    // 1. 获取当前 Leader 负责的课程列表
    const resCourses = await request.get('/leader/course/list')
    courseList.value = resCourses || []

    // 2. 获取教师和组长列表 (用于调整教师的下拉框)
    const resTeachers = await request.get('/leader/teacher/list')
    teacherList.value = resTeachers || []

  } catch (error) {
    console.error(error)
    ElMessage.error('数据加载失败，请检查后端服务');
  }
}

// 【新增功能】跳转到 /teacher 页面
const goToTeacherPage = () => {
  router.push('/teacher');
}


// === 内容下发功能 ===
const openContentDialog = (row) => {
  currentRow.value = row
  selectedContentType.value = '导学' // 默认选择
  contentPayload.value = ''
  contentDialogVisible.value = true
}

const submitCourseMaterial = async () => {
  if(!contentPayload.value) {
    return ElMessage.warning(`请为 [${selectedContentType.value}] 填写内容`);
  }

  try {
    const payload = {
      type: selectedContentType.value,
      content: contentPayload.value
    };

    await request.post(`/leader/course/${currentRow.value.id}/upload-material`, payload);

    ElMessage.success(`[${selectedContentType.value}] 资料已成功下发！`);
    contentDialogVisible.value = false;

  } catch (e) {
    ElMessage.error(e.response?.data || '资料下发失败');
  }
}


// === 调整教师 (保留，因为 Leader 有权调整自己负责的课程的任课教师) ===
const openAssignDialog = (row) => {
  currentRow.value = row
  selectedTeacher.value = row.teacher || ''
  assignDialogVisible.value = true
}

const submitAssign = async () => {
  if(!selectedTeacher.value) return ElMessage.warning('请选择任课教师')
  try {
    // 传递 classId 和 courseId，以便后端更新 teaching_classes (复用 Leader update 接口)
    await request.post('/leader/course/update', {
      id: currentRow.value.id,
      teacher: selectedTeacher.value,
      classId: currentRow.value.classId
    })
    ElMessage.success('教师调整成功，执教班级已同步更新')
    assignDialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e.response?.data || '调整教师失败')
  }
}


// === 删除课程 ===
const handleDelete = async (id) => {
  try {
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
.leader-container { display: flex; height: 100vh; background-color: #f0f2f5; }
.sidebar { background-color: #2a2d43; color: #fff; display: flex; flex-direction: column;
  .logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #1f2233; color: #fff; }
  .el-menu-vertical {
    border-right: none;
    // 样式用于将按钮添加到菜单项中
    .switch-to-teacher {
      padding: 10px 20px;
      .el-button {
        width: 100%;
        font-weight: 600;
        background-color: #3f4769; /* 稍微深一点的背景 */
        border-color: #4f5787;
        &:hover {
          background-color: #555d88;
        }
      }
    }
  }
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