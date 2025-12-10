<template>
  <div class="teacher-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">教师工作台</div>
      <el-menu :default-active="activeMenu" class="el-menu-vertical"
               background-color="#304156" text-color="#fff" active-text-color="#409EFF"
               @select="handleSelect">
        <el-menu-item index="1"><el-icon><Reading /></el-icon>学生管理(执教班级)</el-menu-item>
        <el-menu-item index="2"><el-icon><Document /></el-icon>我的申请记录</el-menu-item>
        <el-menu-item index="3"><el-icon><DocumentChecked /></el-icon>课程资料与批改</el-menu-item>
        <el-menu-item index="4"><el-icon><Tickets /></el-icon>学生考试记录</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header-bar">
        <span>欢迎您，{{ teacherName }} 老师</span>
        <div class="header-actions">
          <el-button type="warning" plain @click="openNotificationDialog">
            <el-icon style="margin-right: 5px;"><Bell /></el-icon> 下发通知
          </el-button>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </div>

      <div v-if="activeMenu === '1'" class="content-block">
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

      <div v-if="activeMenu === '2'" class="content-block">
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

      <div v-if="activeMenu === '3'" class="content-block">
        <h3>执教班级资料与学生提交情况</h3>

        <el-table :data="teachingMaterials" border stripe style="width: 100%">
          <el-table-column prop="fileName" label="资料名称 (含课程名)" min-width="250" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="100">
            <template #default="scope">
              <el-tag :type="getMaterialTypeTag(scope.row.type)">{{ scope.row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="截止时间" width="180">
            <template #default="scope">
              {{ parseDeadline(scope.row.content) || '未设置' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280">
            <template #default="scope">
              <el-button size="small" type="primary" plain @click="openSubmissionDialog(scope.row)">查看提交</el-button>
              <el-button v-if="isLeader" size="small" type="success" @click="openDeadlineDialog(scope.row, true)">直接延长</el-button>
              <el-button v-else size="small" type="warning" @click="openDeadlineDialog(scope.row, false)">申请延长</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="activeMenu === '4'" class="content-block">
        <h3>执教班级考试作弊记录</h3>
        <p class="exam-tip">此处仅显示作弊次数 > 0 的记录，完整记录请查看课程资料列表。</p>

        <el-select v-model="selectedExamId" placeholder="选择考试ID" clearable @change="fetchCheatingRecords" style="width: 200px; margin-bottom: 20px;">
          <el-option
              v-for="exam in availableExams"
              :key="exam.id"
              :label="exam.title"
              :value="exam.id"
          />
        </el-select>

        <el-table :data="cheatingRecords" border stripe style="width: 100%">
          <el-table-column prop="studentUsername" label="学号" width="120" />
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column prop="classId" label="班级ID" width="100" />
          <el-table-column prop="record.cheatCount" label="作弊次数" width="100">
            <template #default="scope">
              <el-tag type="danger">{{ scope.row.record.cheatCount }} 次</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="record.submitTime" label="提交时间" width="180" />
          <el-table-column label="得分" width="80">
            <template #default="scope">
              <span :style="{ color: scope.row.record.score > 60 ? '#67C23A' : '#E6A23C' }">{{ scope.row.record.score }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="scope">
              <el-button size="small" link type="primary" @click="viewCheatingDetail(scope.row)">详情</el-button>
            </template>
          </el-table-column>
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

      <el-dialog v-model="submissionDialogVisible" :title="`[${currentMaterial.type}] 提交记录 - ${currentMaterial.fileName}`" width="70%" top="5vh">

        <el-table :data="submissions" border stripe height="400" style="width: 100%; margin-top: 10px;">
          <el-table-column prop="studentUsername" label="学号" width="120" />
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column prop="classId" label="班级ID" width="100" />
          <el-table-column prop="record.submitTime" label="提交时间" width="180" />

          <el-table-column label="分数" width="150" align="center">
            <template #default="scope">
              <el-input-number
                  v-if="scope.row.record.score === 0 || scope.row.record.score === null"
                  v-model.number="scope.row.gradeForm.score"
                  :min="0"
                  :max="100"
                  size="small"
              />
              <el-tag v-else :type="scope.row.record.score >= 60 ? 'success' : 'danger'">
                {{ scope.row.record.score }} 分
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="280" fixed="right">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="openSubmissionDetail(scope.row)">查看详情</el-button>

              <el-button
                  v-if="scope.row.record.score === 0 || scope.row.record.score === null"
                  size="small"
                  type="success"
                  @click="submitGrade(scope.row)"
              >
                批改
              </el-button>
              <el-tag v-else type="info" size="small" style="margin-right: 5px;">已批改</el-tag>

              <el-button
                  size="small"
                  type="danger"
                  plain
                  @click="handleReject(scope.row.record.id, scope.row.studentName)"
              >
                打回重做
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>

      <el-dialog v-model="detailDialogVisible" :title="`作业详情 - ${currentSubmission.studentName}`" width="600px" destroy-on-close>
        <el-form label-width="80px">
          <el-form-item label="分数">
            <el-input-number v-model.number="currentSubmission.gradeForm.score" :min="0" :max="100" />
          </el-form-item>
          <el-form-item label="学生作答">
            <el-input type="textarea" :value="currentSubmission.answerText" :rows="10" readonly />
            <div v-if="currentSubmission.files && currentSubmission.files.length > 0">
              <p>附件:</p>
              <div v-for="(file, i) in currentSubmission.files" :key="i" style="margin-top: 5px;">
                <el-button link type="primary" @click="downloadFile(file, '学生作业附件'+i)">下载附件 {{ i + 1 }}</el-button>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="评语/反馈">
            <el-input type="textarea" v-model="currentSubmission.gradeForm.aiFeedback" :rows="5" placeholder="请输入评语或反馈" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="detailDialogVisible = false">取消</el-button>
          <el-button type="success" @click="submitGrade(currentSubmission)">确认批改</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="deadlineDialogVisible" :title="deadlineDialogTitle" width="400px" destroy-on-close>
        <el-form label-width="100px">
          <el-form-item label="当前截止">
            <el-tag type="info">{{ currentDeadlineInfo }}</el-tag>
          </el-form-item>
          <el-form-item label="新截止时间">
            <el-date-picker
                v-model="newDeadline"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择新的截止时间"
                style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-if="!isLeaderDeadline" label="申请理由">
            <el-input type="textarea" v-model="applyForm.reason" placeholder="申请延长的理由" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="deadlineDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitDeadlineRequest">
            {{ isLeaderDeadline ? '直接修改' : '提交延长申请' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="notificationDialogVisible" title="下发班级通知" width="500px">
        <el-form label-width="80px">
          <el-form-item label="标题">
            <el-input v-model="notificationForm.title" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input type="textarea" v-model="notificationForm.content" :rows="4" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="notificationDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitNotification">发送通知</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="cheatingDetailDialogVisible" :title="`作弊详情 - ${currentCheatingRecord.studentName}`" width="400px">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="作弊次数">
            <el-tag type="danger">{{ currentCheatingRecord.record.cheatCount }} 次</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="考试得分">
            <span :style="{ color: currentCheatingRecord.record.score > 60 ? '#67C23A' : '#E6A23C' }">{{ currentCheatingRecord.record.score }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentCheatingRecord.record.submitTime }}</el-descriptions-item>
          <el-descriptions-item label="处理建议">请联系教务处或课题组长进行后续处理。</el-descriptions-item>
        </el-descriptions>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Reading, User, Document, DocumentChecked, Tickets, Bell, Close } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const teacherInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
const teacherName = ref(teacherInfo.realName)
const isLeader = teacherInfo.role === '2'; // 判断是否为课题组长

const activeMenu = ref('1')
const studentList = ref([])
const applicationList = ref([])
const teachingClassIds = ref([]) // 存储教师的执教班级ID列表
const teachingMaterials = ref([]) // 教师执教班级的资料列表
const availableExams = ref([]) // 所有考试列表
const cheatingRecords = ref([]) // 作弊记录列表

// 分页和筛选状态
const keyword = ref('')
const classFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedExamId = ref(null)

// 弹窗状态 and Forms
const dialogVisible = ref(false)
const submissionDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const deadlineDialogVisible = ref(false)
const notificationDialogVisible = ref(false)
const cheatingDetailDialogVisible = ref(false)

const dialogTitle = ref('')
const currentRow = ref({})
const currentMaterial = ref({})
const currentSubmission = ref({ gradeForm: {} })
const currentCheatingRecord = ref({})
const submissions = ref([])

const applyForm = ref({ type: '', reason: '', newUsername: '', newRealName: '', newClassId: null, materialId: null })
const notificationForm = reactive({ title: '', content: '' })
const isLeaderDeadline = ref(false)
const newDeadline = ref(null)
const currentDeadlineInfo = ref('')
const deadlineDialogTitle = computed(() => isLeaderDeadline.value ? '直接修改资料截止时间' : '申请延长资料截止时间');


onMounted(() => {
  initializeTeachingClasses()
  fetchStudents()
  fetchMaterials()
  fetchAvailableExams()
})

const initializeTeachingClasses = () => {
  if (teacherInfo && teacherInfo.teachingClasses) {
    teachingClassIds.value = teacherInfo.teachingClasses
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);
  }
}

const fetchAvailableExams = async () => {
  try {
    const coursesRes = await request.get(`/home/data`);
    const courseList = coursesRes.courses || [];

    let allExams = [];
    for (const course of courseList) {
      if (course.id) {
        const exams = await request.get(`/student/course/${course.id}/exams`);
        exams.forEach(e => e.title = course.name + ' - ' + e.title);
        allExams.push(...exams);
      }
    }
    availableExams.value = allExams;
  } catch (e) {
    ElMessage.error('加载考试列表失败');
  }
}

const fetchCheatingRecords = async () => {
  if (!selectedExamId.value) {
    cheatingRecords.value = [];
    return;
  }
  try {
    const res = await request.get(`/teacher/exam/${selectedExamId.value}/cheating-records`);
    cheatingRecords.value = res || [];
  } catch (e) {
    ElMessage.error('加载作弊记录失败');
  }
}


const fetchMaterials = async () => {
  try {
    const res = await request.get('/teacher/materials');
    teachingMaterials.value = res || [];
  } catch (e) {
    ElMessage.error('加载资料失败');
  }
}


const handleSelect = (index) => {
  activeMenu.value = index
  if (index === '1') fetchStudents()
  if (index === '2') fetchApplications()
  if (index === '3') fetchMaterials()
  if (index === '4') fetchAvailableExams()
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

// 获取学生列表
const fetchStudents = async () => {
  try {
    const params = {
      keyword: keyword.value,
      classId: classFilter.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    };

    const res = await request.get('/teacher/students', { params });

    studentList.value = res.list || [];
    total.value = res.total || 0;
    pageNum.value = res.pageNum || 1;
    pageSize.value = res.pageSize || 10;

  } catch (e) {
    console.error(e);
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

// 打开增/删/改弹窗
const openApplyDialog = (type, row) => {
  applyForm.value = { type, reason: '', newUsername: '', newRealName: '', newClassId: null, materialId: null };
  currentRow.value = row || {};

  if (type === 'ADD') dialogTitle.value = '申请：新增学生';
  else if (type === 'DELETE') dialogTitle.value = '申请：删除学生';
  else if (type === 'RESET_PWD') dialogTitle.value = '申请：重置密码';

  dialogVisible.value = true;
}

// 提交申请 (增/删/改/延期)
const submitApplication = async () => {
  if (!applyForm.value.reason) return ElMessage.warning('请填写申请理由');

  let content = '';
  let targetId = currentRow.value.userId || 0;

  if (applyForm.value.type === 'ADD') {
    if (!applyForm.value.newUsername || !applyForm.value.newClassId) return ElMessage.warning('请填写学生学号和班级ID');
    content = `新增学生：${applyForm.value.newRealName || '未命名'} (${applyForm.value.newUsername}), 班级ID: ${applyForm.value.newClassId}`;
  } else if (applyForm.value.type === 'DEADLINE_EXTENSION') {
    if (!newDeadline.value) return ElMessage.warning('请选择新的截止时间');
    content = `请求将资料 [${currentMaterial.value.fileName}] 的截止时间延长至: ${newDeadline.value}`;
    targetId = currentMaterial.value.id;
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
    ElMessage.success('学生管理/资料修改申请已提交，请等待组长/管理员审核。');
    dialogVisible.value = false;
    deadlineDialogVisible.value = false;
    if (activeMenu.value === '2') fetchApplications();
  } catch (e) {
    ElMessage.error(e.response?.data || '提交失败');
  }
}

const openNotificationDialog = () => {
  notificationForm.title = '';
  notificationForm.content = '';
  notificationDialogVisible.value = true;
}

const submitNotification = async () => {
  if (!notificationForm.title || !notificationForm.content) return ElMessage.warning('请填写标题和内容');
  try {
    await request.post('/teacher/notification/send', notificationForm);
    ElMessage.success('班级通知已发送');
    notificationDialogVisible.value = false;
  } catch (e) {
    ElMessage.error(e.response?.data || '发送失败');
  }
}

// === 批改功能 ===
const openSubmissionDialog = async (material) => {
  currentMaterial.value = material;
  try {
    const res = await request.get(`/teacher/material/${material.id}/submissions`);
    // 为每个提交记录添加一个临时的批改表单状态
    submissions.value = res.map(item => ({
      ...item,
      // 解析作业文本和附件路径
      ...parseSubmissionContent(item.record.userAnswers),
      gradeForm: reactive({
        score: item.record.score || 0,
        aiFeedback: item.record.aiFeedback || ''
      })
    }));
    submissionDialogVisible.value = true;
  } catch (e) {
    ElMessage.error(e.response?.data || '加载提交记录失败');
  }
}

const openSubmissionDetail = (submission) => {
  currentSubmission.value = submission;
  detailDialogVisible.value = true;
}

const submitGrade = async (submission) => {
  try {
    const payload = {
      id: submission.record.id,
      score: submission.gradeForm.score,
      aiFeedback: submission.gradeForm.aiFeedback
    };

    await request.post('/teacher/grade', payload);
    ElMessage.success('批改成功！');
    detailDialogVisible.value = false;
    // 重新加载提交列表
    openSubmissionDialog(currentMaterial.value);
  } catch (e) {
    ElMessage.error(e.response?.data || '批改失败');
  }
}

// === 新增：打回功能 ===
const handleReject = async (recordId, studentName) => {
  try {
    await ElMessageBox.confirm(
        `确定要打回 ${studentName} 的提交记录吗？打回后学生可以重新提交。`,
        '确认打回',
        {
          confirmButtonText: '确定打回',
          cancelButtonText: '取消',
          type: 'warning'
        }
    );

    await request.post(`/teacher/reject-submission/${recordId}`);
    ElMessage.success('提交记录已成功打回，学生可以重新提交。');
    // 刷新提交列表
    openSubmissionDialog(currentMaterial.value);
  } catch (e) {
    if (e === 'cancel') return;
    ElMessage.error(e.response?.data || '打回失败');
  }
}


// === 延长截止时间 ===
const openDeadlineDialog = (material, isLeaderAction) => {
  currentMaterial.value = material;
  isLeaderDeadline.value = isLeaderAction;

  const currentDeadline = parseDeadline(material.content);
  currentDeadlineInfo.value = currentDeadline || '未设置';
  newDeadline.value = null;
  applyForm.value.reason = '';

  deadlineDialogVisible.value = true;
}

const submitDeadlineRequest = async () => {
  if (!newDeadline.value) return ElMessage.warning('请选择新的截止时间');

  if (isLeaderDeadline.value) {
    // 组长直接修改
    try {
      const payload = {
        materialId: currentMaterial.value.id,
        newDeadline: newDeadline.value
      };
      await request.post('/leader/material/update-deadline', payload);
      ElMessage.success('截止时间已直接更新！');
      deadlineDialogVisible.value = false;
      fetchMaterials(); // 刷新列表
    } catch (e) {
      ElMessage.error(e.response?.data || '更新失败');
    }

  } else {
    // 教师提交申请
    applyForm.value.type = 'DEADLINE_EXTENSION';
    applyForm.value.materialId = currentMaterial.value.id;
    await submitApplication();
  }
}

const viewCheatingDetail = (record) => {
  currentCheatingRecord.value = record;
  cheatingDetailDialogVisible.value = true;
}

const logout = () => {
  localStorage.clear();
  router.push('/login');
}

// === 辅助工具函数 ===
const parseDeadline = (content) => {
  try {
    const json = JSON.parse(content);
    return json.deadline;
  } catch (e) {
    return null;
  }
}

const parseSubmissionContent = (userAnswers) => {
  try {
    const json = JSON.parse(userAnswers);
    return {
      answerText: json.text || '(无文本作答)',
      files: json.files || []
    };
  } catch (e) {
    return {
      answerText: userAnswers || '(无内容)',
      files: []
    };
  }
}

const downloadFile = (path, name) => {
  if (!path) return
  const realName = path.split(/[\\/]/).pop()
  const url = `http://localhost:8080/uploads/${realName}`
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', name || realName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const formatType = (type) => {
  const map = { ADD: '新增学生', DELETE: '删除学生', RESET_PWD: '重置密码', DEADLINE_EXTENSION: '延期申请' };
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
const getMaterialTypeTag = (type) => {
  const map = { '测验': 'warning', '作业': 'primary', '项目': 'success' };
  return map[type] || 'info';
}
</script>

<style scoped>
/* 样式部分保持不变 */
.teacher-container { display: flex; height: 100vh; background-color: #f5f7fa; }
.sidebar { background-color: #304156; color: white; flex-shrink: 0; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #2b3649; }
.el-menu-vertical { border-right: none; }
.main-content { padding: 20px; flex: 1; display: flex; flex-direction: column; }
.header-bar { background: #fff; padding: 15px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; border-radius: 4px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.header-actions { display: flex; align-items: center; gap: 10px; }
.content-block { background: #fff; padding: 20px; border-radius: 4px; flex: 1; min-height: 400px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.panel-header { display: flex; justify-content: space-between; margin-bottom: 20px; align-items: center; }
h3 { margin: 0 0 10px; border-left: 4px solid #409EFF; padding-left: 10px; }
.exam-tip { font-size: 13px; color: #E6A23C; margin-bottom: 15px; }

/* 筛选和分页样式 */
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