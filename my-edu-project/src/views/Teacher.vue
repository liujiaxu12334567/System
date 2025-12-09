<template>
  <div class="teacher-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">教师工作台</div>
      <el-menu :default-active="activeMenu" class="el-menu-vertical"
               background-color="#304156" text-color="#fff" active-text-color="#409EFF"
               @select="handleSelect">
        <el-menu-item index="1"><el-icon><Reading /></el-icon>我的课程与资料</el-menu-item>
        <el-menu-item index="2"><el-icon><User /></el-icon>学生管理(需审核)</el-menu-item>
        <el-menu-item index="3"><el-icon><Document /></el-icon>我的申请记录</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header-bar">
        <span>欢迎您，{{ teacherName }} 老师</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>

      <div v-if="activeMenu === '1'" class="content-block">
        <h3>我的授课列表与资料发布</h3>
        <el-empty description="暂无课程数据，请先联系课题组长分配课程" />
      </div>

      <div v-if="activeMenu === '2'" class="content-block">
        <div class="panel-header">
          <h3>我执教班级的学生名单</h3>
          <el-button type="primary" @click="openApplyDialog('ADD', null)">+ 申请新增学生</el-button>
        </div>

        <el-card shadow="never" class="filter-card">
          <div class="filter-controls">

            <el-select
                v-model="classFilter"
                placeholder="按班级ID筛选"
                clearable
                @change="fetchStudents"
                style="width: 150px; margin-right: 15px"
            >
              <el-option
                  v-for="id in teachingClassIds"
                  :key="id"
                  :label="id"
                  :value="id"
              />
            </el-select>

            <el-input
                v-model="keyword"
                placeholder="🔍 搜索姓名/学号"
                style="width: 250px;"
                @input="fetchStudents"
                clearable
            />
          </div>
        </el-card>

        <el-table :data="studentList" border stripe style="width: 100%; margin-top: 15px;">
          <el-table-column prop="username" label="学号" width="150" />
          <el-table-column prop="realName" label="姓名" width="120" />
          <el-table-column prop="classId" label="班级ID" width="100" />
          <el-table-column prop="createTime" label="入学时间" />
          <el-table-column label="操作" width="250">
            <template #default="scope">
              <el-button size="small" type="warning" plain @click="openApplyDialog('RESET_PWD', scope.row)">重置密码</el-button>
              <el-button size="small" type="danger" plain @click="openApplyDialog('DELETE', scope.row)">申请删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-container">
          <el-pagination
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              :current-page="pageNum"
              :page-sizes="[10, 20, 50]"
              :page-size="pageSize"
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
          />
        </div>
      </div>

      <div v-if="activeMenu === '3'" class="content-block">
        <h3>我的申请历史</h3>
        <el-table :data="applicationList" border style="width: 100%">
          <el-table-column prop="type" label="类型" width="120">
            <template #default="scope">
              <el-tag>{{ formatType(scope.row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="申请内容" />
          <el-table-column prop="reason" label="理由" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)">{{ formatStatus(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" width="180" />
        </el-table>
      </div>

      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
        <el-form :model="applyForm" label-width="80px">

          <template v-if="applyForm.type === 'ADD'">
            <el-form-item label="学生学号">
              <el-input v-model="applyForm.newUsername" placeholder="请输入学号" />
            </el-form-item>
            <el-form-item label="学生姓名">
              <el-input v-model="applyForm.newRealName" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="所属班级">
              <el-input v-model="applyForm.newClassId" placeholder="请输入所属班级ID" type="number" />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="目标学生">
              <el-input :value="currentRow.realName + ' (' + currentRow.username + ')'" disabled />
            </el-form-item>
          </template>

          <el-form-item label="申请理由">
            <el-input type="textarea" v-model="applyForm.reason" placeholder="请详细说明理由，如：学生退学、忘记密码等" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitApplication">提交审核</el-button>
        </template>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Reading, User, Document } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const teacherName = ref('教师')
const activeMenu = ref('2')
const studentList = ref([])
const applicationList = ref([])
const teachingClassIds = ref([]) // 存储教师的执教班级ID列表

// 分页和筛选状态
const keyword = ref('')
const classFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 弹窗相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const currentRow = ref({})
const applyForm = ref({ type: '', reason: '', newUsername: '', newRealName: '', newClassId: null })

onMounted(() => {
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) teacherName.value = JSON.parse(userInfo).realName

  // 初始化时获取学生的执教班级列表，用于筛选器
  initializeTeachingClasses()
  fetchStudents()
})

// 解析教师的执教班级列表
const initializeTeachingClasses = () => {
  const userInfo = JSON.parse(localStorage.getItem('userInfo'));
  if (userInfo && userInfo.teachingClasses) {
    teachingClassIds.value = userInfo.teachingClasses
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);
  }
}


const handleSelect = (index) => {
  activeMenu.value = index
  if (index === '2') fetchStudents()
  if (index === '3') fetchApplications()
}

// 分页处理函数
const handleSizeChange = (val) => {
  pageSize.value = val;
  pageNum.value = 1;
  fetchStudents();
}

const handleCurrentChange = (val) => {
  pageNum.value = val;
  fetchStudents();
}

// 获取学生列表 (调用 TeacherController 接口，后端已实现按班级过滤)
const fetchStudents = async () => {
  try {
    const params = {
      keyword: keyword.value,
      classId: classFilter.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    };

    const res = await request.get('/teacher/students', { params });

    // 后端现在返回分页结构 { list, total, pageNum, pageSize }
    studentList.value = res.list || [];
    total.value = res.total || 0;
    pageNum.value = res.pageNum || 1;
    pageSize.value = res.pageSize || 10;

    if (total.value === 0 && (keyword.value || classFilter.value)) {
      ElMessage.info('未找到匹配的学生记录');
    } else if (total.value === 0) {
      ElMessage.info('未分配执教班级或班级内没有学生');
    }

  } catch (e) {
    ElMessage.error('加载学生名单失败，请检查后端配置');
  }
}

// 获取申请记录
const fetchApplications = async () => {
  try {
    const res = await request.get('/teacher/my-applications');
    applicationList.value = res || [];
  } catch (e) {}
}

// 打开弹窗
const openApplyDialog = (type, row) => {
  applyForm.value = { type, reason: '', newUsername: '', newRealName: '', newClassId: null };
  currentRow.value = row || {};

  if (type === 'ADD') dialogTitle.value = '申请：新增学生';
  else if (type === 'DELETE') dialogTitle.value = '申请：删除学生';
  else if (type === 'RESET_PWD') dialogTitle.value = '申请：重置密码';

  dialogVisible.value = true;
}

// 提交申请
const submitApplication = async () => {
  if (!applyForm.value.reason) return ElMessage.warning('请填写申请理由');
  if (applyForm.value.type === 'ADD' && !applyForm.value.newUsername) return ElMessage.warning('请填写学生学号');

  // 构建提交数据
  let content = '';
  let targetId = currentRow.value.userId || 0;

  if (applyForm.value.type === 'ADD') {
    content = `新增学生：${applyForm.value.newRealName || '未命名'} (${applyForm.value.newUsername}), 班级ID: ${applyForm.value.newClassId || '未指定'}`;
  } else {
    const action = applyForm.value.type === 'DELETE' ? '删除' : '重置密码';
    content = `${action}：${currentRow.value.realName} (${currentRow.value.username})`;
  }

  const payload = {
    type: applyForm.value.type,
    targetId: targetId,
    reason: applyForm.value.reason,
    content: content,
  }

  try {
    await request.post('/teacher/apply', payload);
    ElMessage.success('申请已提交，请在记录中查看');
    dialogVisible.value = false;
    if (activeMenu.value === '3') fetchApplications();
  } catch (e) {}
}

const logout = () => {
  localStorage.clear();
  router.push('/login');
}

// 格式化工具 (保持不变)
const formatType = (type) => {
  const map = { ADD: '新增学生', DELETE: '删除学生', RESET_PWD: '重置密码' };
  return map[type] || type;
}
const formatStatus = (status) => {
  const map = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' };
  return map[status] || status;
}
const getStatusType = (status) => {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  return 'warning';
}
</script>

<style scoped>
/* 样式部分保持不变 */
.teacher-container { display: flex; height: 100vh; background-color: #f5f7fa; }
.sidebar { background-color: #304156; color: white; }
.logo { height: 60px; line-height: 60px; text-align: center; font-weight: bold; background-color: #2b3649; }
.main-content { padding: 20px; }
.header-bar { background: #fff; padding: 15px; margin-bottom: 20px; display: flex; justify-content: space-between; border-radius: 4px; }
.content-block { background: #fff; padding: 20px; border-radius: 4px; }
.panel-header { display: flex; justify-content: space-between; margin-bottom: 20px; align-items: center; }
h3 { margin: 0 0 10px; border-left: 4px solid #409EFF; padding-left: 10px; }

/* 新增的筛选和分页样式 */
.filter-card {
  margin-bottom: 20px;
  padding: 15px;
  background: #f9f9f9;
}
.filter-controls {
  display: flex;
  align-items: center;
}
.pagination-container {
  margin-top: 20px;
  padding: 15px;
  background: #fff;
  border-radius: 4px;
  display: flex;
  justify-content: flex-end;
}
</style>