<template>
  <div class="leader-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">课题组管理中心</div>
      <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical"
          background-color="#2a2d43"
          text-color="#bfcbd9"
          active-text-color="#ffffff"
          @select="handleMenuSelect"
      >
        <el-menu-item index="1"><el-icon><DataLine /></el-icon>我的课程管理</el-menu-item>
        <el-menu-item index="2"><el-icon><UserFilled /></el-icon>课题组成员与通知</el-menu-item>
        <el-menu-item index="3"><el-icon><DocumentChecked /></el-icon>申请审核</el-menu-item> <el-divider style="margin: 10px 0; border-color: #444761;" />
        <div class="switch-to-teacher">
          <el-button type="warning" plain @click="goToTeacherPage">
            <el-icon style="margin-right: 5px;"><Switch /></el-icon> 切换到教师工作台
          </el-button>
        </div>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header-bar">
        <div class="breadcrumb">
          首页 / {{ activeMenu === '1' ? '课程资料下发' : (activeMenu === '2' ? '成员与通知管理' : '申请审核') }}
        </div>
        <div class="user-profile">
          <span>欢迎您，{{ userInfo.realName || '组长' }}</span>
          <el-button link type="primary" @click="logout" style="margin-left: 15px">退出</el-button>
        </div>
      </div>

      <div v-if="activeMenu === '1'" class="content-panel">
        <div class="panel-header">
          <h3>我的课程列表与内容下发</h3>
          <el-button type="primary" @click="openBatchMaterialDialog" style="margin-left: 10px;">
            批量下发资料/任务
          </el-button>
        </div>
        <el-alert title="说明：您可以管理负责的课程，向对应班级下发教学资料、创建知识图谱或编辑课程目录。" type="info" show-icon style="margin-bottom: 20px;" />

        <el-table :data="courseList" border stripe style="width: 100%">
          <el-table-column prop="name" label="课程名称" width="180" />
          <el-table-column prop="classId" label="所属班级" width="100" />
          <el-table-column prop="semester" label="学期" width="150" />
          <el-table-column prop="teacher" label="任课教师" min-width="150">
            <template #default="scope">
              <el-tag v-if="scope.row.teacher">{{ scope.row.teacher }}</el-tag>
              <span v-else class="text-gray">待分配</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="350" fixed="right">
            <template #default="scope">
              <el-button size="small" type="success" plain @click="openContentDialog(scope.row)">下发资料/测验</el-button>
              <el-button size="small" type="danger" @click="currentRow=scope.row; openExamDialog()">发布考试</el-button>
              <el-button size="small" type="warning" @click="openAssignDialog(scope.row)">调整教师</el-button>
              <el-popconfirm title="确定删除该课程吗？" @confirm="handleDelete(scope.row.id)">
                <template #reference>
                  <el-button size="small" type="danger" link>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="activeMenu === '2'" class="content-panel">
        <div class="panel-header">
          <h3>课题组教师名单</h3>
          <el-button type="primary" @click="openNotificationDialog(null)">
            <el-icon style="margin-right:5px"><Bell /></el-icon> 向全体教师发送通知
          </el-button>
        </div>

        <el-table :data="teacherList" border stripe style="width: 100%">
          <el-table-column prop="realName" label="教师姓名" width="150" />
          <el-table-column prop="username" label="工号" width="150" />
          <el-table-column prop="roleType" label="角色" width="120">
            <template #default="scope">
              <el-tag v-if="scope.row.roleType === '2'" type="success">课题组长</el-tag>
              <el-tag v-else type="primary">普通教师</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前执教/负责" min-width="200" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="scope.row.roleType === '2'">
                 负责科目: <el-tag v-if="scope.row.teacherRank" type="success" effect="plain" size="small">{{ scope.row.teacherRank }}</el-tag>
                 <span v-else class="text-gray">未分配</span>
              </span>
              <span v-else>
                 执教班级: {{ scope.row.teachingClasses || '暂无' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button size="small" plain @click="openNotificationDialog(scope.row)">发送消息</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="activeMenu === '3'" class="content-panel">
        <h2>待审核的资料延期申请</h2>
        <el-alert v-if="applicationList.length === 0" title="当前没有待审核的资料延期申请记录" type="success" show-icon style="margin-bottom: 20px;" />

        <el-table :data="applicationList" border stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="teacherName" label="申请人" width="100" />
          <el-table-column prop="type" label="类型" width="100">
            <template #default="scope">
              <el-tag :type="getTypeTag(scope.row.type)">{{ formatType(scope.row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="申请内容" min-width="250" show-overflow-tooltip/>
          <el-table-column prop="reason" label="申请理由" min-width="150" show-overflow-tooltip/>
          <el-table-column prop="createTime" label="提交时间" width="160" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="scope">
              <el-button size="small" type="success" @click="handleReview(scope.row.id, 'APPROVED')">批准</el-button>
              <el-button size="small" type="danger" @click="handleReview(scope.row.id, 'REJECTED')">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-dialog v-model="contentDialogVisible"
                 :title="'下发内容 - ' + currentRow.name + ' (' + currentRow.classId + ')'"
                 :width="['知识图谱', '目录'].includes(selectedContentType) ? '900px' : '750px'"
                 :close-on-click-modal="false"
                 top="5vh">

        <div class="content-tabs">
          <el-button
              v-for="item in contentTypes"
              :key="item"
              :type="selectedContentType === item ? 'primary' : 'default'"
              @click="handleTypeChange(item)"
              style="margin: 0 10px 10px 0"
          >
            {{ item }}
          </el-button>
        </div>

        <ContentEditor
            :content-type="selectedContentType"
            :is-batch="false"
            v-model:content-title="contentTitle"
            v-model:content-payload="contentPayload"
            v-model:content-deadline="contentDeadline"
            :quiz-data="quizData"
            :textbook-form="textbookForm"
            :graph-data="graphData"
            :catalog-data="catalogData"
            :file-list="fileList"
            @add-question="addQuestion"
            @remove-question="removeQuestion"
            @add-chapter="addChapter"
            @add-section="addSection"
            @edit-node="editCatalogNode"
            @remove-node="removeCatalogNode"
            @add-graph-node="addNode"
            @remove-graph-node="removeNode"
            @add-graph-link="addLink"
            @remove-graph-link="removeLink"
            @set-file-list="fileList = $event"
            :chart-ref="chartRef"
        />

        <template #footer>
          <el-button @click="contentDialogVisible = false">取消</el-button>
          <el-button type="success" :loading="uploading" @click="submitCourseMaterial">
            {{ getSubmitButtonText() }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="examDialogVisible" :title="'发布考试 - ' + currentRow.name + ' (' + currentRow.classId + ')'" width="800px" top="5vh">
        <el-form label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="考试标题"><el-input v-model="examForm.title" placeholder="如: 期中考试" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="考试时长"><el-input-number v-model="examForm.duration" :min="10" :max="180" style="width: 100%;" /> 分钟</el-form-item></el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间">
                <el-date-picker v-model="examForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" placeholder="选择考试开始时间" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="截止时间">
                <el-date-picker v-model="examForm.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" placeholder="选择考试截止时间" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-divider>试题列表</el-divider>

        <div class="question-list-box">
          <div v-for="(q, index) in examForm.questions" :key="index" class="question-edit-item">
            <div class="q-header">
              <span class="q-idx">第 {{ index + 1 }} 题</span>
              <el-button type="danger" link size="small" @click="removeExamQuestion(index)">删除</el-button>
            </div>

            <el-input v-model="q.title" type="textarea" :rows="2" placeholder="请输入题干内容..." style="margin-bottom: 10px"/>

            <div v-for="(opt, oIdx) in q.options" :key="oIdx" class="option-row">
              <el-radio v-model="q.answer" :label="oIdx" class="correct-radio">
                {{ String.fromCharCode(65+oIdx) }}
              </el-radio>
              <el-input v-model="q.options[oIdx]" size="small" placeholder="请输入选项内容" />
            </div>

            <div class="score-set">
              分值：<el-input-number v-model="q.score" :min="1" :max="100" size="small" style="width:100px"/> 分
            </div>
          </div>

          <el-button type="primary" plain style="width:100%; margin-top:10px" @click="addExamQuestion">+ 添加单选题</el-button>
        </div>

        <template #footer>
          <el-button @click="examDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="submitExam">确认发布正式考试</el-button>
        </template>
      </el-dialog>


      <el-dialog v-model="assignDialogVisible" title="调整任课教师" width="400px">
        <p style="margin-bottom: 5px">当前课程：{{ currentRow.name }}</p>
        <el-select v-model="selectedTeacher" placeholder="请选择教师" style="width: 100%">
          <el-option v-for="t in teacherList" :key="t.userId" :label="t.realName" :value="t.realName" />
        </el-select>
        <template #footer>
          <el-button @click="assignDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAssign">确认调整</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="notificationDialogVisible" title="发送通知" width="500px">
        <el-form label-width="80px">
          <el-form-item label="标题"><el-input v-model="notificationForm.title" /></el-form-item>
          <el-form-item label="内容"><el-input type="textarea" v-model="notificationForm.content" :rows="4" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="notificationDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitNotification">发送</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="batchMaterialDialogVisible"
                 title="批量下发资料/任务"
                 :width="['知识图谱', '目录'].includes(batchMaterialForm.type) ? '900px' : '750px'"
                 top="5vh"
                 :close-on-click-modal="false">
        <el-form label-width="120px">

          <el-form-item label="目标课程">
            <el-select
                v-model="batchMaterialForm.courseNames"
                multiple
                filterable
                placeholder="请选择要下发的课程名称 (针对所有教授此课程的班级)"
                style="width: 100%"
            >
              <el-option
                  v-for="c in distinctCourseNames"
                  :key="c"
                  :label="c"
                  :value="c"
              />
            </el-select>
            <el-alert v-if="batchMaterialForm.courseNames.length === 0" title="提示：将向所有教授选中课程的班级下发资料" type="warning" :closable="false" style="margin-top: 10px; width: 100%;" />
          </el-form-item>

          <el-form-item label="资料类型">
            <el-radio-group v-model="batchMaterialForm.type" @change="resetBatchContent">
              <el-radio
                  v-for="item in contentTypes.filter(t => ['测验', '作业', '项目', '导学', 'FAQ', '学习资料'].includes(t))"
                  :key="item"
                  :label="item"
              >
                {{ item }}
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <ContentEditor
              :content-type="batchMaterialForm.type"
              :is-batch="true"
              v-model:content-title="batchMaterialForm.title"
              v-model:content-payload="batchMaterialForm.content"
              v-model:content-deadline="batchMaterialForm.deadline"
              :quiz-data="quizData"
              :textbook-form="textbookForm"
              :graph-data="graphData"
              :catalog-data="catalogData"
              :file-list="batchMaterialForm.fileList"
              @add-question="addQuestion"
              @remove-question="removeQuestion"
              @add-chapter="addChapter"
              @add-section="addSection"
              @edit-node="editCatalogNode"
              @remove-node="removeCatalogNode"
              @add-graph-node="addNode"
              @remove-graph-node="removeNode"
              @add-graph-link="addLink"
              @remove-graph-link="removeLink"
              @set-file-list="batchMaterialForm.fileList = $event"
              :chart-ref="chartRef"
          />

        </el-form>

        <template #footer>
          <el-button @click="batchMaterialDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitBatchMaterial" :loading="uploading">
            确认集体下发
          </el-button>
        </template>
      </el-dialog>
    </el-main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, onBeforeUnmount, computed, defineComponent } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataLine, UserFilled, Switch, Bell, Close, DocumentChecked, Upload, Minus, Plus } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const router = useRouter()
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
const activeMenu = ref('1')
const courseList = ref([])
const teacherList = ref([])
const applicationList = ref([])

// 弹窗状态
const contentDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const notificationDialogVisible = ref(false)
const examDialogVisible = ref(false)
const batchMaterialDialogVisible = ref(false)
const currentRow = ref({})
const selectedTeacher = ref('')

// 考试发布状态
const examForm = reactive({ title: '', startTime: '', deadline: '', duration: 60, questions: [] })
const addExamQuestion = () => examForm.questions.push({ title: '', options: ['','','',''], answer: 0, score: 20 })
const removeExamQuestion = (idx) => examForm.questions.splice(idx, 1)

// 内容下发数据
const contentTypes = ref(['导学', '教材', '测验', '作业', '知识图谱', '目录', 'FAQ', '学习资料', '项目'])
const selectedContentType = ref('导学')
const contentTitle = ref('')
const contentPayload = ref('')
const contentDeadline = ref('')
const fileList = ref([])
const uploading = ref(false)

// 复杂类型数据结构
const quizData = reactive({ questions: [] })
const textbookForm = reactive({ name:'', isbn:'', author:'', publisher:'', edition:'', intro:'', url:'' })
const graphData = reactive({ nodes: [], links: [], categories: [{name:'根节点'},{name:'一级知识点'},{name:'二级知识点'}] })
const catalogData = ref([])
const notificationForm = reactive({ title: '', content: '' })
const notificationTarget = ref(null)

// 【新增】批量下发表单
const batchMaterialForm = reactive({
  courseNames: [],
  type: '作业',
  title: '',
  content: '',
  deadline: '',
  fileList: []
})
const batchUploadRef = ref(null)

// 计算属性：获取不重复的课程名称列表 (用于批量下发选择框)
const distinctCourseNames = computed(() => {
  return [...new Set(courseList.value.map(c => c.name))];
});

const titlePlaceholder = computed(() => {
  if (selectedContentType.value === '作业') return '请输入作业标题 (如: 第一次大作业)'
  return '请输入标题'
})

// 图表引用和辅助函数 (为了避免污染 ContentEditor 的 JSX，这里只保留声明)
const chartRef = ref(null);
let myChart = null;
const initChart = () => { /* Logic to initialize echarts */ }
const resizeChart = () => myChart && myChart.resize()
const updateChartOption = () => { /* Logic to update echarts option */ }
const addNode = () => { /* Logic for graph editor */ }
const removeNode = (i) => { /* Logic for graph editor */ }
const addLink = () => { /* Logic for graph editor */ }
const removeLink = (i) => { /* Logic for graph editor */ }
const getNodeName = (id) => { /* Logic for graph editor */ }
const clearGraph = () => { graphData.nodes=[]; graphData.links=[]; /* updateChartOption() */ }

// === 目录逻辑 (保持完整) ===
const addChapter = () => ElMessageBox.prompt('章节名称').then(({value})=> value && catalogData.value.push({id:Date.now(),label:value,level:1,children:[],index:catalogData.value.length+1})).catch(()=>{})
const addSection = (d) => ElMessageBox.prompt('小节名称').then(({value})=> value && (d.children || (d.children=[])).push({id:Date.now(),label:value,level:2})).catch(()=>{})
const editCatalogNode = (d) => ElMessageBox.prompt('重命名',{inputValue:d.label}).then(({value})=> value && (d.label=value)).catch(()=>{})
const removeCatalogNode = (n,d) => { const p=n.parent, c=p.data.children||p.data, i=c.findIndex(x=>x.id===d.id); c.splice(i,1); if(d.level===1) catalogData.value.forEach((x,k)=>x.index=k+1) }
const clearCatalog = () => catalogData.value = []


onMounted(() => { fetchData(); fetchPendingApplications(); window.addEventListener('resize', resizeChart) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeChart); if(myChart) myChart.dispose() })

const fetchData = async () => {
  try {
    courseList.value = await request.get('/leader/course/list') || []
    teacherList.value = await request.get('/leader/teacher/list') || []
  } catch(e){}
}

const fetchPendingApplications = async () => {
  try {
    const res = await request.get('/leader/applications/pending');
    applicationList.value = res || [];
  } catch (e) {
    ElMessage.error('加载待审核申请失败');
  }
}

const handleReview = async (id, status) => {
  const action = status === 'APPROVED' ? '批准' : '驳回';
  const type = applicationList.value.find(a => a.id === id)?.type || 'DEADLINE_EXTENSION';

  try {
    await ElMessageBox.confirm(`确定${action}该${formatType(type)}申请吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: status === 'APPROVED' ? 'success' : 'danger'
    });

    await request.post('/leader/applications/review', { id, status });
    ElMessage.success(`操作成功：申请已${action}`);
    fetchPendingApplications();
  } catch (e) {
    if (e === 'cancel') return;
    ElMessage.error(`${action}失败：` + (e.response?.data || '服务器错误'));
  }
}


const handleMenuSelect = (idx) => {
  activeMenu.value = idx
  if (idx === '1') fetchData()
  else if (idx === '2') fetchData()
  else if (idx === '3') fetchPendingApplications()
}
const goToTeacherPage = () => router.push('/teacher')
const logout = () => { localStorage.clear(); router.push('/login') }

// 类型切换
const handleTypeChange = (type) => {
  selectedContentType.value = type
  if (type === '测验' && quizData.questions.length === 0) addQuestion()
  if (type === '知识图谱') nextTick(()=>{ initChart() })
}

// 批量下发类型切换时重置内容
const resetBatchContent = () => {
  // 仅重置内容，不重置目标课程
  batchMaterialForm.title = '';
  batchMaterialForm.content = '';
  batchMaterialForm.deadline = '';
  batchMaterialForm.fileList = [];
  if (batchUploadRef.value) batchUploadRef.value.clearFiles();
  // 确保 quizData 也被重置，否则测验编辑器会保留上次的数据
  quizData.questions = [{ title: '', options: ['','','',''], answer: 0, score: 20 }];
}


// 提交考试
const submitExam = async () => {
  if (examForm.questions.length === 0) return ElMessage.warning('请至少添加一道题目');
  if (!examForm.title) return ElMessage.warning('请填写考试标题');
  if (!examForm.startTime) return ElMessage.warning('请设置考试开始时间');
  if (!examForm.deadline) return ElMessage.warning('请设置考试截止时间');

  if (dayjs(examForm.startTime).isAfter(dayjs(examForm.deadline))) {
    return ElMessage.warning('开始时间不能晚于截止时间');
  }

  const contentPayload = { questions: examForm.questions }
  const status = dayjs(examForm.startTime).isAfter(dayjs()) ? "未开始" : "进行中";

  try {
    await request.post(`/leader/course/${currentRow.value.id}/publish-exam`, {
      title: examForm.title,
      content: JSON.stringify(contentPayload),
      startTime: examForm.startTime,
      deadline: examForm.deadline,
      duration: examForm.duration,
      status: status
    });
    ElMessage.success('考试发布成功，初始状态为: ' + status);
    examDialogVisible.value = false;
    fetchData();
  } catch (e) {
    ElMessage.error(e.response?.data || '发布失败');
  }
}


// === 资料和测验逻辑 ===
const addQuestion = () => quizData.questions.push({ title: '', options: ['','','',''], answer: 0, score: 10 })
const removeQuestion = (idx) => quizData.questions.splice(idx, 1)

const getSubmitButtonText = () => ['知识图谱','目录','测验'].includes(selectedContentType.value) ? '保存并发布' : '提交并下发'

const openContentDialog = (row) => {
  currentRow.value = row; selectedContentType.value = '导学';
  contentTitle.value=''; contentPayload.value=''; contentDeadline.value=''; fileList.value=[];
  quizData.questions=[]; catalogData.value=[]; graphData.nodes=[]; graphData.links=[];
  Object.keys(textbookForm).forEach(k => textbookForm[k] = '')
  contentDialogVisible.value = true
}

const submitCourseMaterial = async () => {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('type', selectedContentType.value)

    let title = contentTitle.value
    if (selectedContentType.value === '教材') title = textbookForm.name
    if (title) formData.append('title', title)

    if (selectedContentType.value === '测验') {
      if(quizData.questions.length === 0) throw new Error('请至少添加一道题目')
      const payload = { deadline: contentDeadline.value, questions: quizData.questions }
      formData.append('content', JSON.stringify(payload))
      formData.append('fileName', 'online_quiz.json')

    } else if (selectedContentType.value === '教材') {
      if(!textbookForm.name) throw new Error('请填写教材名称')
      const payload = { ...textbookForm, intro: textbookForm.intro }
      formData.append('content', JSON.stringify(payload))

    } else if (selectedContentType.value === '知识图谱') {
      formData.append('content', JSON.stringify(graphData))
      formData.append('fileName', '知识图谱.json')

    } else if (selectedContentType.value === '目录') {
      formData.append('content', JSON.stringify(catalogData.value))
      formData.append('fileName', '课程目录.json')

    } else if (['作业','项目'].includes(selectedContentType.value)) {
      const payload = { text: contentPayload.value, deadline: contentDeadline.value }
      formData.append('content', JSON.stringify(payload))
      if(!contentTitle.value) throw new Error('请填写作业标题')

    } else {
      formData.append('content', contentPayload.value)
    }

    if (fileList.value.length > 0) formData.append('file', fileList.value[0].raw)

    await request.post(`/leader/course/${currentRow.value.id}/upload-material`, formData, {headers:{'Content-Type':'multipart/form-data'}})
    ElMessage.success('发布成功'); contentDialogVisible.value = false
  } catch (e) { ElMessage.error(e.message || '失败') } finally { uploading.value = false }
}

const submitAssign = async () => {
  await request.post('/leader/course/update',{id:currentRow.value.id,teacher:selectedTeacher.value,classId:currentRow.value.classId});
  assignDialogVisible.value=false;
  fetchData()
}

const openNotificationDialog = (t) => {
  notificationTarget.value=t;
  notificationForm.title='';
  notificationForm.content='';
  notificationDialogVisible.value=true
}

const submitNotification = async () => {
  await request.post('/leader/notification/send',{title:notificationForm.title,content:notificationForm.content,targets:notificationTarget.value?[notificationTarget.value.username]:null});
  notificationDialogVisible.value=false
}

const handleDelete = async (id) => {
  await request.post(`/leader/course/delete/${id}`);
  fetchData()
}

// 【新增】打开批量下发对话框
const openBatchMaterialDialog = () => {
  batchMaterialForm.courseNames = [];
  batchMaterialForm.type = '作业';
  batchMaterialForm.title = '';
  batchMaterialForm.content = '';
  batchMaterialForm.deadline = '';
  batchMaterialForm.fileList = [];
  quizData.questions = [{ title: '', options: ['','','',''], answer: 0, score: 20 }]; // 初始化测验数据
  batchMaterialDialogVisible.value = true;
}

// 【新增】提交批量下发
const submitBatchMaterial = async () => {
  if (batchMaterialForm.courseNames.length === 0) return ElMessage.warning('请选择至少一个目标课程');
  if (!batchMaterialForm.title) return ElMessage.warning('请填写资料标题');

  // 检查任务类内容完整性
  if (['作业', '项目'].includes(batchMaterialForm.type)) {
    if (!batchMaterialForm.content) return ElMessage.warning('请填写任务要求');
    if (!batchMaterialForm.deadline) return ElMessage.warning('请设置截止时间');
  }
  if (batchMaterialForm.type === '测验') {
    if (quizData.questions.length === 0) return ElMessage.warning('请至少添加一道题目');
    if (!batchMaterialForm.deadline) return ElMessage.warning('请设置截止时间');
  }

  uploading.value = true;

  const formData = new FormData();
  formData.append('type', batchMaterialForm.type);
  formData.append('title', batchMaterialForm.title);

  // 内容和截止时间处理
  let finalContent = batchMaterialForm.content;
  if (['作业','测验','项目'].includes(batchMaterialForm.type)) {
    const payload = batchMaterialForm.type === '测验' ?
        { deadline: batchMaterialForm.deadline, questions: quizData.questions } :
        { text: batchMaterialForm.content, deadline: batchMaterialForm.deadline };
    finalContent = JSON.stringify(payload);
    formData.append('fileName', batchMaterialForm.type === '测验' ? 'online_quiz.json' : batchMaterialForm.title);
  } else {
    finalContent = batchMaterialForm.content;
  }

  formData.append('content', finalContent);
  formData.append('deadline', batchMaterialForm.deadline);

  // 关键：遍历多选列表，正确添加到 FormData
  batchMaterialForm.courseNames.forEach(name => {
    formData.append('courseNames', name);
  });

  // 添加文件
  if (batchMaterialForm.fileList.length > 0) {
    formData.append('file', batchMaterialForm.fileList[0].raw);
  }

  try {
    const res = await request.post('/leader/course/batch-material', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    ElMessage.success(res || '批量下发成功');
    batchMaterialDialogVisible.value = false;
    fetchData();
  } catch (e) {
    ElMessage.error(e.response?.data || '批量下发失败');
  } finally {
    uploading.value = false;
  }
}


// --- 辅助函数 ---
const formatType = (type) => {
  const map = { DEADLINE_EXTENSION: '延期申请', ADD: '新增学生', DELETE: '删除学生', RESET_PWD: '重置密码' }
  return map[type] || type
}
const getTypeTag = (type) => {
  const map = { DEADLINE_EXTENSION: 'warning', ADD: 'success', DELETE: 'danger', RESET_PWD: 'info' }
  return map[type] || 'info'
}
</script>

<style scoped>
/* 基础布局 */
.leader-container { display: flex; height: 100vh; background-color: #f0f2f5; }
.sidebar { background-color: #2a2d43; color: #fff; display: flex; flex-direction: column; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #1f2233; color: #fff; }
.el-menu-vertical { border-right: none; }
.switch-to-teacher { padding: 10px 20px; text-align: center; }
.main-content { flex: 1; padding: 0; display:flex; flex-direction:column; width: 100%; }
.header-bar { height: 60px; background: #fff; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; border-bottom: 1px solid #eee; }
.content-panel { margin: 20px; padding: 20px; background: #fff; flex:1; overflow-y:auto; border-radius: 4px; }
.panel-header { border-left: 4px solid #409EFF; padding-left: 10px; margin-bottom: 20px; font-weight: bold; font-size: 16px; display: flex; justify-content: space-between; align-items: center;}
.text-gray { color: #999; font-style: italic; }

/* 编辑器容器通用样式 */
.graph-editor-container, .quiz-editor, .catalog-editor-container {
  border: 1px solid #e0e0e0; border-radius: 4px; padding: 15px; background: #fafafa; max-height: 550px; overflow-y: auto;
}

/* 测验编辑器 */
.quiz-editor { padding: 15px; background: #fafafa; border-radius: 4px; border: 1px solid #e0e0e0; }
.question-edit-item { background: #fff; padding: 15px; margin-bottom: 10px; border: 1px solid #e0e0e0; border-radius: 4px; }
.q-header { display: flex; justify-content: space-between; margin-bottom: 10px; font-weight: bold; color: #409EFF; }
.option-row { display: flex; align-items: center; margin-bottom: 8px; }
.correct-radio { margin-right: 10px; width: 40px; }
.score-set { margin-top: 10px; text-align: right; font-size: 13px; color: #666; }

/* 图谱编辑器 */
.editor-layout { display: flex; height: 400px; gap: 10px; }
.editor-panel { width: 280px; display: flex; flex-direction: column; gap: 10px; overflow-y: auto; }
.panel-section { background: #fff; padding: 10px; border: 1px solid #eee; border-radius: 4px; }
.node-list, .link-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 5px; }
.link-item { font-size: 12px; background: #f0f2f5; padding: 2px 6px; border-radius: 4px; display: flex; align-items: center; .del-icon { margin-left: 5px; cursor: pointer; color: #999; &:hover{ color:red; } } }
.preview-panel { flex: 1; background: #fff; border: 1px solid #eee; .echarts-box { width: 100%; height: 100%; } }
.form-row { display: flex; align-items: center; margin-bottom: 5px; }

/* 目录编辑器 */
.catalog-editor-container { background: #fff; }
.catalog-actions { margin-bottom: 15px; }
.catalog-tree-box { border: 1px solid #eee; padding: 10px; min-height: 200px; max-height: 400px; overflow-y: auto; }
.custom-tree-node { flex: 1; display: flex; align-items: center; justify-content: space-between; font-size: 14px; padding-right: 8px;
  .node-label { display: flex; align-items: center; gap: 10px; }
}
.tree-actions { display: none; }
.custom-tree-node:hover .tree-actions { display: inline-block; }
</style>