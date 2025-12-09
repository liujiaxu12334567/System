<template>
  <div class="admin-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">系统管理后台</div>
      <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical"
          @select="handleMenuSelect"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#ffffff"
      >
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
              <el-select
                  v-model="classFilter"
                  placeholder="按班级筛选 (学生)"
                  clearable
                  @change="fetchUsers"
                  style="width: 180px; margin-right: 15px"
              >
                <el-option
                    v-for="c in classList"
                    :key="c.id"
                    :label="c.name + ' (ID: ' + c.id + ')'"
                    :value="c.id"
                />
              </el-select>
            </template>

            <template v-if="roleFilter === '2' || roleFilter === '3'">
              <el-select v-model="subjectFilter" placeholder="按教授科目筛选" clearable @change="fetchUsers" style="width: 150px; margin-right: 15px">
                <el-option label="Java程序设计" value="Java" />
                <el-option label="Web前端" value="Web" />
              </el-select>
              <el-select
                  v-model="classFilter"
                  placeholder="按执教班级"
                  clearable
                  @change="fetchUsers"
                  style="width: 150px; margin-right: 15px"
              >
                <el-option
                    v-for="c in classList"
                    :key="c.id"
                    :label="c.name + ' (ID: ' + c.id + ')'"
                    :value="c.id"
                />
              </el-select>
            </template>


            <el-input
                v-model="keyword"
                placeholder="🔍 搜索用户名/姓名"
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

        <el-card shadow="hover" header="表格导入分班">
          <el-form :model="uploadForm" label-width="120px" :inline="true">

            <el-form-item label="目标班级ID">
              <el-input v-model="uploadForm.targetClassId" placeholder="请输入班级ID (例如: 202303)" type="number" style="width: 200px;" />
            </el-form-item>

            <el-form-item label="所属专业">
              <el-input v-model="uploadForm.major" placeholder="请输入专业名称" style="width: 200px;" />
            </el-form-item>

            <el-form-item label="起始学号">
              <el-input v-model="uploadForm.startUsername" placeholder="例如: 24107311201" style="width: 200px;" />
              <el-tag style="margin-left: 20px" type="warning">导入前必须填写此项，系统将顺序分配学号</el-tag>
            </el-form-item>

            <el-form-item>
              <el-button
                  type="primary"
                  :loading="loading.upload"
                  @click="submitUpload"
                  :disabled="!uploadForm.startUsername || !uploadForm.targetClassId"
              >
                提交导入
              </el-button>
            </el-form-item>

          </el-form>

          <el-upload
              class="upload-demo"
              ref="uploadRef"  drag
              :action="uploadActionUrl"
              :show-file-list="true"
              :before-upload="beforeUploadCheck"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :on-progress="handleUploadProgress"
              :auto-upload="false"  :data="{ targetClassId: uploadForm.targetClassId, startUsername: uploadForm.startUsername, major: uploadForm.major }"
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
        <h2>课程管理与分配</h2>
        <el-card shadow="never" class="content-panel" style="margin-top: 10px;">
          <div class="panel-header">
            <h3>所有课程列表</h3>
            <div class="header-buttons">
              <el-button type="warning" @click="openBatchAssignDialog" style="margin-right: 10px;">批量分配课程</el-button>
              <el-button type="primary" @click="openCourseDialog">+ 发布新课程</el-button>
            </div>
          </div>

          <el-table :data="courseList" border stripe style="width: 100%">
            <el-table-column prop="name" label="课程名称" min-width="180" />
            <el-table-column prop="classId" label="所属班级" width="100" />
            <el-table-column prop="code" label="课程代码" width="100" />
            <el-table-column prop="semester" label="学期" width="150" />
            <el-table-column prop="teacher" label="任课教师" min-width="150">
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
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button size="small" type="warning" @click="openAssignDialog(scope.row)">分配教师</el-button>

                <el-popconfirm title="确定删除该课程吗？" @confirm="handleCourseDelete(scope.row.id)">
                  <template #reference>
                    <el-button size="small" type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>


      <el-dialog v-model="dialogVisible" :title="form.userId ? '编辑用户' : '新增用户'" width="500px">
        <el-form :model="form" label-width="100px">

          <el-form-item label="账号/学号">
            <el-input v-model="form.username" :disabled="!!form.userId" />
          </el-form-item>

          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" />
          </el-form-item>

          <el-form-item label="密码">
            <el-input v-model="form.password" placeholder="不填则不修改(新增默认123456)" show-password />
          </el-form-item>

          <el-form-item label="角色">
            <el-select v-model="form.roleType" placeholder="请选择角色" style="width: 100%" :disabled="!!form.userId">
              <el-option label="管理员" :value="1" />
              <el-option label="课题组长" :value="2" />
              <el-option label="普通教师" :value="3" />
              <el-option label="学生" :value="4" />
            </el-select>
          </el-form-item>

          <template v-if="form.roleType === 4 && !form.userId">
            <el-form-item label="所属专业">
              <el-input v-model="form.major" placeholder="请输入专业名称" />
            </el-form-item>
            <el-form-item label="所属班级">
              <el-input v-model="form.classId" placeholder="请输入班级ID (例如: 202303)" type="number" />
            </el-form-item>
          </template>

        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </template>
      </el-dialog>

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
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { User, Reading, DataBoard, Tickets, UploadFilled } from '@element-plus/icons-vue'

const userList = ref([])
const keyword = ref('')
const roleFilter = ref(null)
const classFilter = ref(null)
const subjectFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const form = ref({})
const activeMenu = ref('1')
const loading = reactive({ range: false, upload: false })
const rangeForm = reactive({ startUsername: '', endUsername: '', targetClassId: null, major: null })
const uploadForm = reactive({ targetClassId: null, startUsername: '', major: null })
const uploadActionUrl = '/api/admin/batch/upload'
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }


// --- 课程管理状态 ---
const courseList = ref([])
const teacherList = ref([])
const courseDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const batchAssignDialogVisible = ref(false)
const courseForm = ref({ name: '', semester: '2025-1', teacher: '', classId: null })
const currentRow = ref({})
const selectedTeacher = ref('')
const batchAssignForm = ref({ name: '', semester: '2025-1', teacherNames: [], classIds: [] })
const classList = ref([]);


onMounted(() => {
  fetchUsers();
  fetchCourseAndTeacherData();
})


// --- 通用数据获取 ---

const fetchUsers = async () => {
  try {
    const params = {
      keyword: keyword.value,
      roleType: roleFilter.value,
      classId: classFilter.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
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

// 获取课程、教师和班级数据
const fetchCourseAndTeacherData = async () => {
  try {
    // 1. 获取课程列表 (现在是 Admin 权限)
    const resCourses = await request.get('/admin/course/list');
    courseList.value = resCourses || [];

    // 2. 获取教师列表
    const resTeachers = await request.get('/leader/teacher/list');
    teacherList.value = resTeachers || [];

    // 3. 获取所有已创建的班级列表
    const resClasses = await request.get('/admin/classes');
    classList.value = Array.isArray(resClasses) ? resClasses : [];

  } catch (error) {
    console.error("加载课程、教师或班级数据失败", error);
  }
}


// --- 筛选/分页事件处理 ---

const handleRoleChange = () => {
  classFilter.value = null;
  subjectFilter.value = null;
  fetchUsers();
}

const handleSizeChange = (val) => {
  pageSize.value = val;
  pageNum.value = 1;
  fetchUsers();
}

const handleCurrentChange = (val) => {
  pageNum.value = val;
  fetchUsers();
}

const handleMenuSelect = (index) => {
  activeMenu.value = index
  if (index === '1') {
    fetchUsers()
  } else if (index === '3') {
    fetchCourseAndTeacherData(); // 切换到课程管理时刷新课程和班级数据
  }
}

// --- 用户管理 CRUD ---

const openDialog = (row) => {
  if (row) {
    // 编辑用户：仅加载通用信息
    form.value = {
      userId: row.userId,
      username: row.username,
      realName: row.realName,
      roleType: Number(row.roleType),
      password: '', // 密码默认清空
      classId: row.classId,
      teachingClasses: row.teachingClasses,
      major: null // 编辑时major不加载或保留
    }
  } else {
    // 【修改】新增用户：默认学生，新增 major 字段
    form.value = { roleType: 4, classId: null, teachingClasses: null, major: null, username: '', realName: '' }
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const url = form.value.userId ? '/admin/user/update' : '/admin/user/add'

  if (!form.value.username || !form.value.realName) {
    return ElMessage.warning('请填写账号和真实姓名');
  }

  // 【新增学生时的校验】
  if (form.value.roleType === 4 && !form.value.userId) {
    if (!form.value.classId) return ElMessage.warning('新增学生必须填写班级ID');
    if (!form.value.major) return ElMessage.warning('新增学生必须填写专业名称'); // 强制要求 major
  }

  // 我们直接发送 form.value，后端 AdminController 必须能够处理 Map 结构并提取 major。
  const payload = {
    ...form.value,
    roleType: String(form.value.roleType),
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

const handleDelete = async (id) => {
  try {
    await request.post(`/admin/user/delete/${id}`)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}


// --- 课程管理逻辑 (CRUD and Batch) ---

const openCourseDialog = () => {
  courseForm.value = { name: '', semester: '2025-1', teacher: '', classId: null };
  courseDialogVisible.value = true;
}

const submitCourse = async () => {
  if(!courseForm.value.name) return ElMessage.warning('请填写课程名称');
  if(!courseForm.value.classId) return ElMessage.warning('请选择所属班级');
  try {
    await request.post('/admin/course/add', courseForm.value);
    ElMessage.success('课程发布成功');
    courseDialogVisible.value = false;
    fetchCourseAndTeacherData();
  } catch (e) {}
}

const openBatchAssignDialog = () => {
  batchAssignForm.value = { name: '', semester: '2025-1', teacherNames: [], classIds: [] };
  batchAssignDialogVisible.value = true;
}

const submitBatchAssign = async () => {
  const form = batchAssignForm.value;
  if (!form.name || form.teacherNames.length === 0 || form.classIds.length === 0) {
    return ElMessage.warning('请填写课程名称，并选择至少一位教师和至少一个班级');
  }

  try {
    await request.post('/admin/course/batch-assign', {
      name: form.name,
      semester: form.semester,
      teacherNames: form.teacherNames,
      classIds: form.classIds
    });
    ElMessage.success(`成功分配课程给 ${form.classIds.length} 个班级，教师执教班级已同步更新。`);
    batchAssignDialogVisible.value = false;
    fetchCourseAndTeacherData();
  } catch (e) {}
}

const openAssignDialog = (row) => {
  currentRow.value = row;
  selectedTeacher.value = row.teacher || '';
  assignDialogVisible.value = true;
}

const submitAssign = async () => {
  if(!selectedTeacher.value) return ElMessage.warning('请选择任课教师');
  try {
    await request.post('/admin/course/update', {
      id: currentRow.value.id,
      teacher: selectedTeacher.value
    });
    ElMessage.success('教师分配成功');
    assignDialogVisible.value = false;
    fetchCourseAndTeacherData();
  } catch (e) {}
}

const handleCourseDelete = async (id) => {
  try {
    await request.post(`/admin/course/delete/${id}`);
    ElMessage.success('删除成功');
    fetchCourseAndTeacherData();
  } catch (e) {}
}

// --- 批量入学逻辑 ---

const beforeUploadCheck = (file) => {
  if (!uploadForm.targetClassId) {
    ElMessage.error('请先填写目标班级ID');
    return false;
  }
  if (!uploadForm.startUsername) {
    ElMessage.error('请先填写起始学号');
    return false;
  }
  // 【新增校验】
  if (!uploadForm.major) {
    ElMessage.error('请先填写所属专业');
    return false;
  }

  const isXlsx = file.name.endsWith('.xlsx');
  if (!isXlsx) {
    ElMessage.error('上传文件只能是 XLSX 格式!');
  }
  return isXlsx;
};

const submitRangeEnroll = async () => {
  // 逻辑已移除，该函数不再使用
};

const handleUploadSuccess = (response, file) => {
  loading.upload = false;
  ElMessage.success(response);
};

const handleUploadError = (error) => {
  loading.upload = false;
  let message = '文件上传失败';
  if (error.response && error.response.data) {
    message = error.response.data;
  }
  ElMessage.error(message);
};

const handleUploadProgress = (event, file, fileList) => {
  loading.upload = true;
};

// 【新增】手动提交文件导入
const submitUpload = () => {
  // 1. 触发 beforeUploadCheck 校验
  if (!uploadForm.targetClassId || !uploadForm.startUsername || !uploadForm.major) {
    return ElMessage.warning('请确保班级ID、专业和起始学号都已填写！');
  }

  // 2. 检查是否有文件待上传
  if (document.querySelector('.el-upload-list__item') === null) {
    return ElMessage.warning('请先选择或拖拽文件！');
  }

  // 3. 手动触发上传
  // 注意：由于没有 ref，这里需要依赖一个 mock ref 或确保 Element Plus 版本支持
  // 最佳实践是使用 ref，这里我们假设 $refs.uploadRef 存在
  try {
    document.querySelector('.el-upload').__vue__.ctx.submit(); // 这是一个不稳定的 hack，但在某些 Element Plus 版本中可能有效
    // 推荐：如果使用 Element Plus 2+, 请在 template 中设置 ref="uploadRef"
    // 并在 script 中 const uploadRef = ref(null); uploadRef.value.submit();
  } catch (e) {
    ElMessage.error('无法触发上传，请确保已选择文件并填写了所有字段。');
  }
};

// --- 辅助函数 (保持不变) ---

const getRoleName = (type) => {
  const map = {'1':'管理员', '2':'课题组长', '3':'普通教师', '4':'学生'}
  return map[String(type)] || '未知'
}
const getRoleTag = (type) => {
  const map = {'1':'danger', '2':'success', '3':'primary', '4':'info'}
  return map[String(type)]
}

onMounted(() => {
  fetchUsers();
  fetchCourseAndTeacherData();
})
</script>

<style scoped>
/* 样式优化，让筛选框和按钮对齐 */
.admin-container { display: flex; height: 100vh; }
.sidebar { background-color: #304156; color: white; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #2b3649; }
.el-menu-vertical:not(.el-menu--collapse) { width: 200px; min-height: 400px; }
.main-content { padding: 20px; background-color: #f0f2f5; }

/* 【顶部操作区样式】 */
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

/* 【分页容器样式】 */
.pagination-container {
  margin-top: 20px;
  padding: 15px;
  background: #fff;
  border-radius: 4px;
  display: flex;
  justify-content: flex-end;
}

/* 课程管理样式 */
.content-panel {
  margin: 0;
  padding: 20px;
  background: #fff;
  border-radius: 4px;
  flex: 1;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header-buttons {
  display: flex;
}


h2 { margin: 0; }
.batch-enrollment-container h2 { margin-bottom: 20px; }
.el-upload__text em { color: #409eff; }
</style>