<template>
  <div class="admin-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">系统管理后台</div>
      <el-menu :default-active="activeMenu" class="el-menu-vertical" @select="handleMenuSelect">
        <el-menu-item index="1"><el-icon><User /></el-icon>用户管理</el-menu-item>
        <el-menu-item index="2"><el-icon><Tickets /></el-icon>批量分班/入学</el-menu-item>
        <el-menu-item index="3"><el-icon><Reading /></el-icon>课程管理</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">

      <div v-if="activeMenu === '1'">

        <div class="header-actions-top">
          <h2>用户管理</h2>
          <el-button type="primary" @click="openDialog(null)" class="add-button">+ 新增用户</el-button>
        </div>

        <el-card shadow="never" class="filter-card">
          <div class="filter-controls">
            <el-select v-model="roleFilter" placeholder="按角色筛选" clearable @change="handleRoleChange" style="width: 150px; margin-right: 15px">
              <el-option label="全部用户" :value="null" />
              <el-option label="管理员" value="1" />
              <el-option label="课题组长" value="2" />
              <el-option label="普通教师" value="3" />
              <el-option label="学生" value="4" />
            </el-select>

            <template v-if="!roleFilter || roleFilter === '4'">
              <el-input
                  v-model="classFilter"
                  placeholder="按班级ID筛选 (学生)"
                  clearable
                  @change="fetchUsers"
                  style="width: 180px; margin-right: 15px"
                  type="number"
              />
            </template>

            <template v-if="roleFilter === '2' || roleFilter === '3'">
              <el-select v-model="subjectFilter" placeholder="按教授科目筛选" clearable @change="fetchUsers" style="width: 150px; margin-right: 15px">
                <el-option label="Java程序设计" value="Java" />
                <el-option label="Web前端" value="Web" />
              </el-select>
              <el-input
                  v-model="classFilter"
                  placeholder="按执教班级ID"
                  clearable
                  @change="fetchUsers"
                  style="width: 150px; margin-right: 15px"
                  type="number"
              />
            </template>


            <el-input
                v-model="keyword"
                placeholder="🔍 搜索姓名/账号"
                style="width: 250px;"
                @input="fetchUsers"
                clearable
            />
          </div>
        </el-card>

        <el-table :data="userList" border stripe style="width: 100%; margin-top: 15px;">
          <el-table-column prop="username" label="账号/学号" width="140" />
          <el-table-column prop="realName" label="真实姓名" width="100" />
          <el-table-column label="角色" width="110">
            <template #default="scope">
              <el-tag :type="getRoleTag(scope.row.roleType)">
                {{ getRoleName(scope.row.roleType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="班级/执教范围" min-width="180">
            <template #default="scope">
              <span v-if="scope.row.roleType === '4'">
                  所属班级: <el-tag size="small">{{ scope.row.classId || '未分班' }}</el-tag>
              </span>
              <span v-else-if="scope.row.teachingClasses">
                  执教班级: {{ scope.row.teachingClasses }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="160" />
          <el-table-column label="操作" width="180">
            <template #default="scope">
              <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>

              <el-popconfirm title="确定删除该用户吗？" @confirm="handleDelete(scope.row.userId)">
                <template #reference>
                  <el-button size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-container">
          <el-pagination
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              :current-page="pageNum"
              :page-sizes="[10, 20, 50, 100]"
              :page-size="pageSize"
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
          />
        </div>
      </div>

      <div v-if="activeMenu === '2'" class="batch-enrollment-container">
        <h2>批量学生入学与分班</h2>
        <el-alert title="说明：批量创建的学生默认角色为 '学生'，默认密码为 '123456'。" type="info" show-icon style="margin-bottom: 20px;" />

        <el-card shadow="hover" header="学号范围批量分班">
          <el-form :model="rangeForm" label-width="120px" :inline="true">
            <el-form-item label="学号起始">
              <el-input v-model="rangeForm.startUsername" placeholder="例如: 24107311201" style="width: 200px;" />
            </el-form-item>
            <el-form-item label="学号结束">
              <el-input v-model="rangeForm.endUsername" placeholder="例如: 24107311220" style="width: 200px;" />
            </el-form-item>
            <el-form-item label="目标班级ID">
              <el-input v-model="rangeForm.targetClassId" type="number" placeholder="例如: 202101" style="width: 200px;" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading.range" @click="submitRangeEnroll">
                批量创建并分班
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="hover" header="表格导入分班" style="margin-top: 20px;">
          <el-form :model="uploadForm" label-width="120px" :inline="true">
            <el-form-item label="目标班级ID">
              <el-input v-model="uploadForm.targetClassId" type="number" placeholder="例如: 202101" style="width: 200px;" />
            </el-form-item>
            <el-form-item label="起始学号">
              <el-input v-model="uploadForm.startUsername" placeholder="例如: 24107311201" style="width: 200px;" />
              <el-tag style="margin-left: 20px" type="warning">导入前必须填写此项，系统将顺序分配学号</el-tag>
            </el-form-item>
          </el-form>

          <el-upload
              class="upload-demo"
              drag
              :action="uploadActionUrl"
              :show-file-list="true"
              :before-upload="beforeUploadCheck"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :on-progress="handleUploadProgress"
              :disabled="loading.upload"
              :data="{ targetClassId: uploadForm.targetClassId, startUsername: uploadForm.startUsername }"
              :headers="uploadHeaders"
              :limit="1"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处，或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                ⚠️ **表格文件只需包含一列：`realName` (真实姓名)。**
                系统将使用您填写的起始学号顺序生成学号。
              </div>
            </template>
          </el-upload>
        </el-card>

      </div>

      <div v-if="activeMenu === '3'">
        <el-empty description="课程管理界面" />
      </div>


      <el-dialog v-model="dialogVisible" :title="form.userId ? '编辑用户' : '新增用户'" width="500px">
        <el-form :model="form" label-width="100px">

          <el-form-item label="账号/工号">
            <el-input v-model="form.username" :disabled="!!form.userId" />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" placeholder="不填则不修改(新增默认123456)" show-password />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.roleType" placeholder="请选择角色" style="width: 100%">
              <el-option label="管理员" :value="1" />
              <el-option label="课题组长" :value="2" />
              <el-option label="普通教师" :value="3" />
              <el-option label="学生" :value="4" />
            </el-select>
          </el-form-item>

          <el-form-item label="所属班级" v-if="form.roleType === 4">
            <el-input v-model="form.classId" placeholder="例如: 202101" type="number" />
          </el-form-item>

          <el-form-item label="执教班级" v-if="form.roleType === 2 || form.roleType === 3">
            <el-input v-model="form.teachingClasses" placeholder="多个班级用英文逗号分隔, 如: 202101,202102" />
          </el-form-item>

        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </template>
      </el-dialog>
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { User, Reading, DataBoard, Tickets, UploadFilled } from '@element-plus/icons-vue'

const userList = ref([])
// 搜索关键词
const keyword = ref('')
// 【筛选变量】
const roleFilter = ref(null)
const classFilter = ref(null)
const subjectFilter = ref(null)

// 【分页状态】
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)


const dialogVisible = ref(false)
const form = ref({})
const activeMenu = ref('1') // 默认激活用户管理

// 批量分班状态
const loading = reactive({ range: false, upload: false })
const rangeForm = reactive({
  startUsername: '',
  endUsername: '',
  targetClassId: null,
})
const uploadForm = reactive({
  targetClassId: null,
  startUsername: ''
})

// 上传组件所需数据
const uploadActionUrl = '/api/admin/batch/upload'
const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token')}`
}

// 【关键修改】发送筛选参数和分页参数给后端
const fetchUsers = async () => {
  try {
    const params = {
      keyword: keyword.value,
      roleType: roleFilter.value,
      classId: classFilter.value,
      pageNum: pageNum.value,     // 发送当前页码
      pageSize: pageSize.value    // 发送每页大小
    };

    // 清理空值参数
    Object.keys(params).forEach(key => {
      if (params[key] === null || params[key] === '') {
        delete params[key];
      }
    });

    // 假设后端返回 { list: [...], total: 100, pageNum: 1, pageSize: 10 }
    const res = await request.get('/admin/user/list', { params });

    userList.value = res.list || [];
    total.value = res.total || 0;
    pageNum.value = res.pageNum || 1;
    pageSize.value = res.pageSize || 10;

  } catch (error) {
    console.error("加载用户失败", error);
  }
}

// 角色筛选变更处理器：当角色切换时，清空班级和科目筛选，然后重新加载列表
const handleRoleChange = () => {
  classFilter.value = null;
  subjectFilter.value = null;
  fetchUsers();
}

// 【新增】处理每页大小变化
const handleSizeChange = (val) => {
  pageSize.value = val;
  pageNum.value = 1; // 改变大小后重置到第一页
  fetchUsers();
}

// 【新增】处理页码变化
const handleCurrentChange = (val) => {
  pageNum.value = val;
  fetchUsers();
}


// 【新增】上传前的校验函数
const beforeUploadCheck = (file) => {
  if (!uploadForm.targetClassId || !uploadForm.startUsername) {
    ElMessage.error('请务必填写目标班级ID和起始学号！');
    return false;
  }
  const startNum = parseInt(uploadForm.startUsername);
  if (isNaN(startNum) || startNum <= 0) {
    ElMessage.error('起始学号必须是有效数字！');
    return false;
  }
  const classIdNum = parseInt(uploadForm.targetClassId);
  if (isNaN(classIdNum) || classIdNum <= 0) {
    ElMessage.error('目标班级ID必须是有效数字！');
    return false;
  }

  loading.upload = true;
  return true;
}


const handleMenuSelect = (index) => {
  activeMenu.value = index
  if (index === '1') {
    fetchUsers()
  }
}

const openDialog = (row) => {
  if (row) {
    form.value = {
      ...row,
      password: '',
      classId: row.classId ? String(row.classId) : null,
      roleType: Number(row.roleType)
    }
  } else {
    form.value = { roleType: 4, classId: null, teachingClasses: null }
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const url = form.value.userId ? '/admin/user/update' : '/admin/user/add'

  let classIdValue = null;
  if (form.value.roleType === 4 && form.value.classId) {
    classIdValue = parseInt(form.value.classId, 10);
    if (isNaN(classIdValue)) {
      return ElMessage.error('班级ID必须是数字');
    }
  }

  const teachingClassesValue = form.value.teachingClasses || null;

  const payload = {
    ...form.value,
    classId: classIdValue,
    teachingClasses: teachingClassesValue,
    roleType: String(form.value.roleType)
  }

  try {
    await request.post(url, payload)
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchUsers()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 【新增】删除处理器
const handleDelete = async (id) => {
  try {
    await request.post(`/admin/user/delete/${id}`)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}


// 批量分班逻辑 (保持不变)
const submitRangeEnroll = async () => {
  if (!rangeForm.startUsername || !rangeForm.endUsername || !rangeForm.targetClassId) {
    return ElMessage.warning('请填写完整的学号范围和目标班级ID')
  }

  const startNum = parseInt(rangeForm.startUsername)
  const endNum = parseInt(rangeForm.endUsername)
  const targetClassIdNum = parseInt(rangeForm.targetClassId)

  if (startNum >= endNum) {
    return ElMessage.error('起始学号必须小于结束学号')
  }
  if (isNaN(targetClassIdNum)) {
    return ElMessage.error('目标班级ID必须是数字')
  }

  loading.range = true
  try {
    const res = await request.post('/admin/batch/enroll', {
      startUsername: rangeForm.startUsername,
      endUsername: rangeForm.endUsername,
      targetClassId: targetClassIdNum,
    })
    ElMessage.success(res)
    rangeForm.startUsername = ''
    rangeForm.endUsername = ''
    rangeForm.targetClassId = null
  } catch (error) {
    // 错误信息由 request.js 拦截器处理
  } finally {
    loading.range = false
  }
}

// 文件上传成功回调
const handleUploadSuccess = (response, file) => {
  loading.upload = false
  if (response && typeof response === 'string') {
    ElMessage.success('文件上传成功，' + response);
    fetchUsers();
  } else if (response && response.data) {
    ElMessage.success('文件上传成功，' + response.data);
    fetchUsers();
  } else {
    ElMessage.error(`文件上传失败：服务器未返回明确信息`);
  }
}

// 文件上传失败回调
const handleUploadError = (error) => {
  loading.upload = false
  const responseData = error.response?.data
  let errMsg = '网络连接失败或文件格式不正确';
  if (typeof responseData === 'string') {
    errMsg = responseData
  } else if (responseData && responseData.msg) {
    errMsg = responseData.msg
  }
  ElMessage.error(`上传失败: ${errMsg}`);
}

// 文件上传进度/开始
const handleUploadProgress = (event, file, fileList) => {
  // 进度开始时，loading 在 beforeUploadCheck 中已经设置为 true
}


// 辅助函数 (保持不变)
const getRoleName = (type) => {
  const map = {'1':'管理员', '2':'课题组长', '3':'普通教师', '4':'学生'}
  return map[String(type)] || '未知'
}
const getRoleTag = (type) => {
  const map = {'1':'danger', '2':'success', '3':'primary', '4':'info'}
  return map[String(type)]
}

onMounted(fetchUsers)
</script>

<style scoped>
/* 样式优化，让筛选框和按钮对齐 */
.admin-container { display: flex; height: 100vh; }
.sidebar { background-color: #304156; color: white; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #2b3649; }
.main-content { padding: 20px; background-color: #f0f2f5; }

/* 【新增】顶部操作区样式 */
.header-actions-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.header-actions-top h2 {
  margin: 0;
}


/* 筛选区域样式 */
.filter-card {
  margin-bottom: 20px;
  padding: 15px;
  display: flex;
  justify-content: flex-start; /* 保持筛选控件在左侧 */
  align-items: center;
}
.filter-controls {
  display: flex;
  align-items: center;
}

/* 【新增】分页容器样式 */
.pagination-container {
  margin-top: 20px;
  padding: 15px;
  background: #fff;
  border-radius: 4px;
  display: flex;
  justify-content: flex-end;
}


h2 { margin: 0; }
.batch-enrollment-container h2 { margin-bottom: 20px; }
.el-upload__text em { color: #409eff; }
</style>