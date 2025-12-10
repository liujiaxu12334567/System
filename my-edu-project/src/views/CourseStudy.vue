<template>
  <div class="study-container">
    <header class="global-header">
      <div class="inner">
        <div class="logo" @click="$router.push('/home')">Neuedu 东软教育</div>
        <div class="user-info">
          <el-avatar :size="30" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
          <span class="user-name">{{ userInfo.realName }}</span>
        </div>
      </div>
    </header>

    <div class="course-banner">
      <div class="banner-content">
        <div class="left-info">
          <div class="breadcrumb" @click="$router.push('/home')">
            <el-icon><ArrowLeft /></el-icon> 返回 | 课程详情
          </div>
          <h1 class="course-title">
            {{ courseInfo.name }}
            <span class="course-code">{{ courseInfo.code }}</span>
          </h1>
          <div class="teacher-info">
            <span class="id-badge">ID: {{ courseInfo.id }}</span>
            <span class="teacher-name">授课教师：{{ courseInfo.teacher }}</span>
          </div>
        </div>

        <div class="right-actions">
          <div class="action-btn">
            <el-icon><Document /></el-icon>
            <span>课程简介</span>
          </div>
          <div class="action-btn">
            <el-icon><Bell /></el-icon>
            <span>课程公告</span>
          </div>
        </div>
      </div>
    </div>

    <main class="main-body">
      <div class="nav-bar">
        <div
            class="nav-item"
            v-for="tab in tabs"
            :key="tab"
            :class="{ active: currentTab === tab }"
            @click="handleTabChange(tab)"
        >
          {{ tab }}
        </div>
      </div>

      <div v-if="currentTab === '知识图谱'" class="graph-view-container" v-loading="loading">
        <div v-if="hasGraphData" class="graph-layout">
          <div class="graph-sidebar">
            <div class="stat-card">
              <div class="icon-box bg-blue"><el-icon><DataAnalysis /></el-icon></div>
              <div class="stat-info">
                <div class="label">知识点总数</div>
                <div class="value">{{ graphNodeCount }}</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="icon-box bg-green"><el-icon><CircleCheck /></el-icon></div>
              <div class="stat-info">
                <div class="label">学习完成率</div>
                <div class="value">0%</div>
              </div>
            </div>

            <div class="legend-box" v-if="graphMode === 'graph'">
              <div class="legend-item"><span class="dot root"></span> 根节点</div>
              <div class="legend-item"><span class="dot l1"></span> 一级知识点</div>
              <div class="legend-item"><span class="dot l2"></span> 二级知识点</div>
            </div>
          </div>

          <div class="graph-main-wrapper">
            <div v-show="graphMode !== 'outline'" class="graph-main" ref="chartRef"></div>

            <div v-if="graphMode === 'outline'" class="outline-main">
              <el-scrollbar>
                <el-tree
                    :data="treeData"
                    default-expand-all
                    :highlight-current="true"
                    node-key="id"
                    :indent="24"
                >
                  <template #default="{ node, data }">
                    <span class="custom-tree-node">
                      <el-icon v-if="data.category === 0" color="#4075F3" style="margin-right:5px"><Flag /></el-icon>
                      <el-icon v-else-if="data.category === 1" color="#00BAAD" style="margin-right:5px"><Folder /></el-icon>
                      <el-icon v-else color="#68D391" style="margin-right:5px"><Document /></el-icon>
                      <span>{{ data.name }}</span>
                    </span>
                  </template>
                </el-tree>
              </el-scrollbar>
            </div>
          </div>

          <div class="graph-tools">
            <div class="tool-list">
              <div class="tool-btn" :class="{ active: graphMode === 'graph' }" @click="switchGraphMode('graph')">
                <el-icon><Share /></el-icon> 图谱模式
              </div>
              <div class="tool-btn" :class="{ active: graphMode === 'mindmap' }" @click="switchGraphMode('mindmap')">
                <el-icon><Operation /></el-icon> 导图模式
              </div>
              <div class="tool-btn" :class="{ active: graphMode === 'outline' }" @click="switchGraphMode('outline')">
                <el-icon><Notebook /></el-icon> 大纲模式
              </div>
            </div>
            <div class="zoom-controls" v-if="graphMode === 'graph'">
              <el-icon @click="zoomChart(1.2)"><ZoomIn /></el-icon>
              <el-icon @click="zoomChart(0.8)"><ZoomOut /></el-icon>
              <el-icon @click="resetChart"><Refresh /></el-icon>
            </div>
          </div>
        </div>
        <el-empty v-else description="老师暂未发布知识图谱" :image-size="120" />
      </div>

      <div v-else-if="currentTab === '目录'" class="catalog-view-container" v-loading="loading">
        <div v-if="hasCatalogData" class="catalog-wrapper">
          <div class="catalog-header">
            <h3>课程大纲</h3>
            <span class="sub-text">共 {{ catalogData.length }} 章</span>
          </div>
          <el-tree
              :data="catalogData"
              default-expand-all
              :expand-on-click-node="false"
              node-key="id"
              class="custom-catalog-tree"
          >
            <template #default="{ node, data }">
              <div class="catalog-node" :class="'level-' + data.level">
                <div class="node-content">
                  <el-icon v-if="data.level === 1" class="chapter-icon"><CollectionTag /></el-icon>
                  <el-icon v-else class="section-icon"><Reading /></el-icon>
                  <span class="node-label">
                    <span v-if="data.level === 1" class="chapter-index">第 {{ data.index }} 章</span>
                    {{ data.label }}
                  </span>
                </div>
                <div class="node-status" v-if="data.level === 2">
                  <el-tag size="small" type="info" effect="plain">未开始</el-tag>
                  <el-button link type="primary" size="small" style="margin-left: 10px">去学习</el-button>
                </div>
              </div>
            </template>
          </el-tree>
        </div>
        <el-empty v-else description="暂无课程目录" :image-size="120" />
      </div>

      <div v-else-if="currentTab === '教材'" class="textbook-view-container" v-loading="loading">
        <div v-if="filteredMaterials.length > 0">
          <div v-for="item in filteredMaterials" :key="item.id" class="textbook-card">
            <div class="card-header">基本信息</div>
            <div class="card-body">
              <div class="info-grid">
                <div class="info-item full">
                  <span class="label">教材名称：</span>
                  <span class="value bold">{{ parseTextbookInfo(item.content).name || item.fileName }}</span>
                </div>
                <div class="info-item">
                  <span class="label">ISBN：</span>
                  <span class="value">{{ parseTextbookInfo(item.content).isbn || '无' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">作者：</span>
                  <span class="value">{{ parseTextbookInfo(item.content).author || '无' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">出版社：</span>
                  <span class="value">{{ parseTextbookInfo(item.content).publisher || '无' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">版次：</span>
                  <span class="value">{{ parseTextbookInfo(item.content).edition || '无' }}</span>
                </div>
                <div class="info-item full">
                  <span class="label">简介：</span>
                  <span class="value">{{ parseTextbookInfo(item.content).intro || '暂无简介' }}</span>
                </div>
                <div class="info-item full">
                  <span class="label">链接：</span>
                  <span class="value">
                    <a v-if="parseTextbookInfo(item.content).url" :href="parseTextbookInfo(item.content).url" target="_blank" style="color:#409EFF">点击访问</a>
                    <span v-else>无</span>
                  </span>
                </div>
                <div class="info-item full">
                  <span class="label">附件：</span>
                  <div class="value attachment-box">
                    <span v-if="!item.filePath">无</span>
                    <div v-else class="file-link">
                      <el-icon><Document /></el-icon> {{ item.fileName }}
                      <el-button link type="primary" size="small" @click="previewFile(item.filePath, item.fileName)">预览</el-button>
                      <el-button link type="primary" size="small" @click="downloadFile(item.filePath, item.fileName)">下载</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无教材信息" :image-size="120" />
      </div>

      <div v-else-if="currentTab === '教学目标'" class="objective-view-container" v-loading="loading">
        <div v-if="filteredMaterials.length > 0">
          <div v-for="item in filteredMaterials" :key="item.id" class="objective-item">
            <div class="obj-icon"><el-icon><Aim /></el-icon></div>
            <div class="obj-content">
              <h4>{{ item.fileName !== '无文件' ? item.fileName : '教学目标' }}</h4>
              <p>{{ item.content }}</p>
            </div>
            <div class="obj-action" v-if="item.filePath">
              <el-button link type="primary" @click="downloadFile(item.filePath, item.fileName)">下载附件</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无教学目标" :image-size="120" />
      </div>

      <div v-else-if="currentTab === '考试'" class="task-view-container" v-loading="loading">
        <div v-if="examList.length > 0" class="task-list">
          <div v-for="(item, index) in examList" :key="item.id" class="task-item">
            <div class="task-left">
              <div class="task-tag">
                <el-tag type="danger" effect="dark" size="small">
                  正式考试 {{ index + 1 }}
                </el-tag>
              </div>
              <div class="task-detail">
                <div class="task-title">
                  {{ item.title }}
                </div>
                <div class="task-meta">
                  <span class="publish-info">截止时间：{{ item.deadline || '无限制' }}</span>
                  <span class="divider">|</span>
                  <span class="desc">
                     考试时长：{{ item.duration }} 分钟
                   </span>
                </div>
              </div>
            </div>
            <div class="task-right">
              <el-button v-if="item.status === '已交卷'" type="success" plain round size="small">
                已交卷
              </el-button>
              <el-button v-else-if="item.status === '未开始'" type="info" round size="small" disabled>
                未开始
              </el-button>
              <el-button v-else type="danger" round size="small" @click="goToExam(item.id)">
                开始考试
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无正式考试安排" :image-size="100" />
      </div>

      <div v-else-if="['作业', '测验', '项目'].includes(currentTab)" class="task-view-container" v-loading="loading">
        <div v-if="filteredMaterials.length > 0" class="task-list">
          <div v-for="(item, index) in filteredMaterials" :key="item.id" class="task-item">
            <div class="task-left">
              <div class="task-tag">
                <el-tag :type="currentTab === '测验' ? 'warning' : 'primary'" effect="dark" size="small">
                  {{ currentTab }} {{ index + 1 }}
                </el-tag>
              </div>
              <div class="task-detail">
                <div class="task-title">
                  {{ item.fileName !== '无文件' ? item.fileName : (item.title || item.content.substring(0, 20)) }}
                </div>
                <div class="task-meta">
                  <span class="publish-info">截止时间：{{ parseTaskInfo(item.content).deadline || '无限制' }}</span>
                  <span class="divider">|</span>
                  <span class="desc">
                     {{ currentTab === '测验' ? '共 100 分' : '请按要求提交' }}
                   </span>
                </div>
              </div>
            </div>
            <div class="task-right">
              <el-button v-if="item.filePath" link type="primary" @click="downloadFile(item.filePath, item.fileName)">
                下载附件
              </el-button>

              <template v-if="currentTab === '测验'">
                <el-button v-if="getQuizStatus(item.id).submitted" type="primary" plain round size="small" @click="goToQuiz(item, 'review')">
                  查看试卷
                </el-button>
                <el-button v-else type="primary" round size="small" @click="goToQuiz(item, 'take')">
                  开始测验
                </el-button>
              </template>

              <template v-else>
                <el-button v-if="getQuizStatus(item.id).submitted" type="success" plain round size="small" @click="goToQuiz(item, 'review')">
                  查看作业
                </el-button>
                <el-button v-else type="primary" round size="small" @click="goToQuiz(item, 'take')">
                  去提交
                </el-button>
              </template>
            </div>
          </div>
        </div>
        <el-empty v-else :description="'暂无' + currentTab" :image-size="100" />
      </div>

      <div v-else-if="currentTab === 'FAQ'" class="faq-view-container">
        <div v-if="filteredMaterials.length > 0" class="faq-list">
          <el-collapse accordion>
            <el-collapse-item v-for="(item, index) in filteredMaterials" :key="item.id" :name="index">
              <template #title>
                <div class="faq-title">
                  <el-icon class="question-icon"><QuestionFilled /></el-icon>
                  {{ item.fileName !== '无文件' ? item.fileName : '问题 ' + (index + 1) }}
                </div>
              </template>
              <div class="faq-content">
                {{ item.content || '暂无详细解答。' }}
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <el-empty v-else description="暂无常见问题" :image-size="100" />
      </div>

      <div v-else class="resource-view-container">
        <div class="filter-toolbar">
          <el-input
              v-model="searchKeyword"
              placeholder="请输入资料名称"
              class="search-input"
              :prefix-icon="Search"
              clearable
          />
          <div class="filter-groups">
            <el-select placeholder="资料类型" disabled class="filter-select"><el-option label="全部" value="" /></el-select>
          </div>
        </div>

        <div class="resource-list" v-loading="loading">
          <div v-if="filteredMaterials.length > 0">
            <div v-for="item in filteredMaterials" :key="item.id" class="resource-item">
              <div class="item-left">
                <div class="card-icon">
                  <el-icon v-if="isDoc(item.fileName)" color="#409EFF" :size="36"><Document /></el-icon>
                  <el-icon v-else-if="isPpt(item.fileName)" color="#E6A23C" :size="36"><DataBoard /></el-icon>
                  <el-icon v-else-if="isPdf(item.fileName)" color="#F56C6C" :size="36"><Files /></el-icon>
                  <el-icon v-else color="#909399" :size="36"><Files /></el-icon>
                </div>

                <div class="item-info">
                  <div class="item-title">
                    {{ item.fileName !== '无文件' ? item.fileName : (item.content ? item.content.substring(0, 50) : '未命名资料') }}
                  </div>
                  <div class="item-meta">{{ formatTime(item.createTime) }}</div>
                </div>
              </div>

              <div class="item-right">
                <el-button
                    v-if="item.filePath"
                    type="primary"
                    link
                    size="small"
                    @click="previewFile(item.filePath, item.fileName)"
                >
                  <el-icon style="margin-right: 4px"><View /></el-icon> 预览
                </el-button>

                <el-button
                    v-if="item.filePath"
                    type="primary"
                    round
                    size="small"
                    @click="downloadFile(item.filePath, item.fileName)"
                >
                  <el-icon style="margin-right: 4px"><Download /></el-icon> 下载
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无相关资料" :image-size="100" />
        </div>
      </div>
    </main>

    <div class="side-toolbar">
      <div class="tool-item">
        <el-icon :size="20"><TrendCharts /></el-icon>
        <span>学习统计</span>
      </div>
      <div class="tool-item">
        <el-icon :size="20"><ChatLineRound /></el-icon>
        <span>学习互动</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  ArrowLeft, Document, Bell, Search, Download, View,
  TrendCharts, ChatLineRound, DataAnalysis, CircleCheck,
  Share, Operation, Notebook, ZoomIn, ZoomOut, Refresh,
  Flag, Folder, CollectionTag, Reading, Aim, Files, DataBoard, QuestionFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

const loading = ref(false)
const courseInfo = ref({})
const allMaterials = ref([])
const examList = ref([])
const currentTab = ref('导学')
const tabs = ['导学', '教材', '教学目标', '知识图谱', '目录', 'FAQ', '学习资料', '测验', '作业', '项目', '考试'] // 【修改】新增考试Tab
const searchKeyword = ref('')
const onlyPublished = ref(true)

// 图谱/目录数据
const chartRef = ref(null); let myChart = null;
const hasGraphData = ref(false); const graphData = ref({})
const hasCatalogData = ref(false); const catalogData = ref([])
const graphNodeCount = ref(0); const graphMode = ref('graph')
const treeData = ref([])
const quizStatusMap = ref({})

// 允许预览的白名单
const previewWhiteList = ['pdf', 'txt', 'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'mp3', 'mp4']

onMounted(() => {
  fetchCourseInfo();
  fetchMaterials();
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart);
  if (myChart) myChart.dispose()
})

// === API 请求 ===
const fetchCourseInfo = async () => {
  try {
    courseInfo.value = await request.get(`/student/course/${courseId}/info`) || {}
  } catch(e) { console.error(e) }
}

const fetchMaterials = async () => {
  loading.value = true;
  try {
    // 1. 获取普通资料
    allMaterials.value = await request.get(`/student/course/${courseId}/materials`) || []

    // 2. 获取考试列表
    examList.value = await request.get(`/student/course/${courseId}/exams`) || []

    // 3. 获取测验和作业的提交状态
    for (const m of allMaterials.value) {
      if (['测验','作业','项目'].includes(m.type)) {
        try {
          const record = await request.get(`/student/quiz/record/${m.id}`)
          if (record && record.id) {
            quizStatusMap.value[m.id] = { submitted: true, score: record.score }
          } else {
            quizStatusMap.value[m.id] = { submitted: false }
          }
        } catch (e) {
          quizStatusMap.value[m.id] = { submitted: false }
        }
      }
    }
  } catch(e) { console.error(e) }
  finally { loading.value = false }
}

const handleTabChange = (tab) => {
  currentTab.value = tab
  if (tab === '知识图谱') renderGraph()
  if (tab === '目录') renderCatalog()
}

// === 数据解析辅助函数 ===
const parseTextbookInfo = (content) => {
  try { return JSON.parse(content) } catch (e) { return { intro: content } }
}

const parseTaskInfo = (content) => {
  try { return JSON.parse(content) } catch(e) { return { text: content } }
}

const getQuizStatus = (mid) => quizStatusMap.value[mid] || { submitted: false }

// 跳转逻辑
const goToQuiz = (item, mode) => {
  router.push({
    name: 'QuizDetail',
    params: { courseId: courseId, materialId: item.id },
    query: { mode: mode }
  })
}

// 【新增】跳转到考试页
const goToExam = (examId) => {
  router.push({
    name: 'ExamDetail',
    params: { examId: examId },
  })
}


// 列表过滤
const filteredMaterials = computed(() => {
  let list = allMaterials.value.filter(item => item.type === currentTab.value)
  if (searchKeyword.value) {
    list = list.filter(item =>
        (item.fileName && item.fileName.includes(searchKeyword.value)) ||
        (item.content && item.content.includes(searchKeyword.value))
    )
  }
  return list
})

// === 图谱渲染逻辑 (保持不变) ===
const convertGraphToTree = (nodes, links) => {
  if (!nodes || nodes.length === 0) return []
  let rootNode = nodes.find(n => n.category === 0)
  if (!rootNode) {
    const targetIds = new Set(links.map(l => l.target))
    rootNode = nodes.find(n => !targetIds.has(n.id)) || nodes[0]
  }
  const visited = new Set(); visited.add(rootNode.id)
  const buildTree = (parentId) => {
    return links.filter(l => l.source === parentId).map(link => {
      if (visited.has(link.target)) return null
      const child = nodes.find(n => n.id === link.target)
      if (!child) return null
      visited.add(child.id)
      return {
        id: child.id,
        name: child.name,
        category: child.category,
        children: buildTree(child.id)
      }
    }).filter(Boolean)
  }
  return [{
    id: rootNode.id,
    name: rootNode.name,
    category: rootNode.category,
    children: buildTree(rootNode.id)
  }]
}

const renderGraph = () => {
  const item = allMaterials.value.find(i => i.type === '知识图谱')
  if(item && item.content) {
    try {
      const data = JSON.parse(item.content);
      hasGraphData.value = true;
      graphNodeCount.value = data.nodes ? data.nodes.length : 0
      treeData.value = convertGraphToTree(data.nodes, data.links)
      nextTick(() => { switchGraphMode(graphMode.value) })
    } catch(e){ hasGraphData.value=false }
  } else hasGraphData.value=false
}

const switchGraphMode = (mode) => {
  graphMode.value = mode
  if (mode === 'outline' || !chartRef.value) return
  if (myChart) myChart.dispose()
  myChart = echarts.init(chartRef.value)

  let option = {}
  if (mode === 'graph') {
    const categories = [{name:'根节点',itemStyle:{color:'#4075F3'}},{name:'一级知识点',itemStyle:{color:'#00BAAD'}},{name:'二级知识点',itemStyle:{color:'#68D391'}}]
    const nodes = (graphData.value.nodes || []).map(n => ({id:n.id, name:n.name, symbolSize:n.category===0?60:(n.category===1?40:25), category:n.category, label:{show:true}}))
    // 重新读取 raw data
    const item = allMaterials.value.find(i => i.type === '知识图谱')
    const raw = JSON.parse(item.content)
    const rawNodes = raw.nodes.map(n=>({id:n.id, name:n.name, symbolSize:n.category===0?50:30, category:n.category, value:n.name, label:{show:true}}))

    option = {
      series: [{
        type: 'graph', layout: 'force', data: rawNodes, links: raw.links, categories: categories,
        roam: true, label:{position:'right',formatter:'{b}'},
        force:{repulsion:300,edgeLength:100}
      }]
    }
  } else if (mode === 'mindmap') {
    option = {
      series: [{
        type: 'tree', data: treeData.value, top: '2%', left: '10%', bottom: '2%', right: '20%',
        symbolSize: 15, label:{position:'left',align:'right',fontSize:16}, leaves:{label:{position:'right',align:'left',fontSize:14}},
        expandAndCollapse: true, initialTreeDepth: 3
      }]
    }
  }
  myChart.setOption(option)
}
const resizeChart = () => { if (myChart) myChart.resize() }
const zoomChart = (s) => { if(myChart && graphMode.value!=='mindmap') myChart.dispatchAction({type:'restore'}) }
const resetChart = () => { if(myChart) myChart.dispatchAction({type:'restore'}) }

const renderCatalog = () => {
  const item = allMaterials.value.find(i => i.type === '目录')
  if(item && item.content) {
    try{
      catalogData.value=JSON.parse(item.content); hasCatalogData.value=true
    } catch(e){ hasCatalogData.value=false }
  } else hasCatalogData.value=false
}

// === 文件操作 (保持不变) ===
const isDoc = (n) => /\.(doc|docx|txt)$/i.test(n)
const isPpt = (n) => /\.(ppt|pptx)$/i.test(n)
const isPdf = (n) => /\.(pdf)$/i.test(n)

const previewFile = (path, fileName) => {
  if (!path) return

  const name = fileName || path
  const ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase()
  const realName = path.split(/[\\/]/).pop()
  const url = `http://localhost:8080/uploads/${realName}`

  if (previewWhiteList.includes(ext)) {
    window.open(url, '_blank')
  } else {
    ElMessage.warning(`该格式 (.${ext}) 不支持在线预览，请点击“下载”按钮查看`)
  }
}

const downloadFile = (path, originalName) => {
  if (!path) return
  const realName = path.split(/[\\/]/).pop()
  const url = `http://localhost:8080/uploads/${realName}`

  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', originalName || realName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const formatTime = (t) => t ? t.replace('T',' ').substring(0,16) : ''
</script>

<style scoped lang="scss">
/* 1. 基础布局 */
.study-container { min-height: 100vh; background-color: #F7F8FA; font-family: sans-serif; }
.global-header {
  height: 60px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  .inner {
    width: 1200px; margin: 0 auto; height: 100%; display: flex; justify-content: space-between; align-items: center;
    .logo { font-size: 22px; font-weight: bold; color: #0041AB; cursor: pointer; }
    .user-info { display: flex; align-items: center; .user-name { margin-left: 8px; font-size: 14px; color: #333; } }
  }
}

/* 2. Banner */
.course-banner {
  height: 220px; background: linear-gradient(120deg, #5B86E5 0%, #36D1DC 100%); color: #fff; padding-top: 30px;
  .banner-content {
    width: 1200px; margin: 0 auto; display: flex; justify-content: space-between;
    .left-info {
      .breadcrumb { font-size: 14px; opacity: 0.8; cursor: pointer; display: flex; align-items: center; margin-bottom: 15px; }
      .course-title { font-size: 36px; font-weight: 600; margin: 0 0 15px 0; .course-code { font-size: 14px; background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 4px; margin-left: 15px; } }
      .teacher-info { font-size: 14px; opacity: 0.9; .id-badge { background: rgba(0,0,0,0.1); padding: 4px 10px; border-radius: 4px; margin-right: 15px; } }
    }
    .right-actions {
      display: flex; gap: 15px;
      .action-btn {
        width: 70px; height: 70px; background: rgba(255,255,255,0.15); border-radius: 8px; display: flex; flex-direction: column; justify-content: center; align-items: center; cursor: pointer;
        .el-icon { font-size: 24px; margin-bottom: 5px; } span { font-size: 12px; }
      }
    }
  }
}

/* 3. 主体卡片 */
.main-body {
  width: 1200px; margin: -50px auto 30px; background: #fff; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); min-height: 600px; padding-bottom: 30px;
}
.nav-bar {
  display: flex; padding: 15px 20px; border-bottom: 1px solid #f0f0f0;
  .nav-item {
    padding: 8px 20px; margin-right: 10px; font-size: 15px; color: #606266; cursor: pointer; border-radius: 20px;
    &:hover { color: #409EFF; background: #f5f7fa; }
    &.active { background: #E6F1FC; color: #0056D4; font-weight: 600; }
  }
}

/* 4. 教材视图样式 */
.textbook-view-container { padding: 30px; }
.textbook-card {
  border: 1px solid #eee; border-radius: 4px; margin-bottom: 20px;
  .card-header { background: #f5f7fa; padding: 10px 20px; font-weight: bold; border-bottom: 1px solid #eee; color: #333; }
  .card-body { padding: 20px; }
}
.info-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px;
  .info-item {
    display: flex; align-items: flex-start;
    &.full { grid-column: span 2; }
    .label { width: 80px; text-align: right; font-weight: bold; color: #606266; flex-shrink: 0; }
    .value { flex: 1; color: #333; &.bold { font-weight: bold; font-size: 16px; } }
  }
}
.attachment-box {
  display: flex; align-items: center;
  .file-link { display: flex; align-items: center; gap: 10px; }
}

/* 5. 教学目标视图样式 */
.objective-view-container { padding: 30px; }
.objective-item {
  display: flex; align-items: flex-start; padding: 20px; border: 1px solid #eee; margin-bottom: 15px; border-radius: 6px;
  .obj-icon { margin-right: 15px; color: #F56C6C; font-size: 24px; }
  .obj-content {
    h4 { margin: 0 0 5px 0; font-size: 16px; }
    p { margin: 0; color: #666; font-size: 14px; }
  }
  .obj-action { margin-left: auto; }
}

/* 6. 任务列表样式 (作业、测验, 考试) */
.task-view-container { padding: 20px 30px; }
.task-filter { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; .search-input { width: 300px; } }
.task-list {
  .task-item {
    display: flex; justify-content: space-between; align-items: center; padding: 20px; border: 1px solid #eee; border-radius: 6px; margin-bottom: 15px; transition: all 0.3s;
    &:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.05); border-color: #c6e2ff; }
    .task-left {
      display: flex; gap: 15px;
      .task-tag { margin-top: 2px; }
      .task-detail {
        .task-title { font-size: 16px; font-weight: bold; color: #333; margin-bottom: 8px; }
        .task-meta {
          font-size: 12px; color: #999; display: flex; align-items: center;
          .status-indicator { margin-right: 10px; }
          .divider { margin: 0 10px; color: #eee; }
        }
      }
    }
    .task-right { display: flex; align-items: center; gap: 20px; }
  }
}

/* 7. FAQ 样式 */
.faq-view-container { padding: 20px 40px; }
.faq-title { display: flex; align-items: center; font-size: 15px; font-weight: 500; .question-icon { margin-right: 10px; color: #409EFF; } }
.faq-content { padding: 10px 0 10px 26px; color: #666; line-height: 1.6; }

/* 8. 资源列表 (通用) */
.resource-view-container {
  .filter-toolbar { padding: 20px 30px; display: flex; align-items: center; gap: 20px; .search-input { width: 300px; } }
  .resource-list {
    padding: 0 30px;
    .resource-item {
      display: flex; align-items: center; padding: 20px 0; border-bottom: 1px solid #f5f5f5;
      .item-left {
        display: flex; align-items: center; flex: 1;
        .card-icon { margin-right: 15px; }
        .item-info {
          .item-title { font-size: 15px; font-weight: 500; }
          .item-meta { font-size: 12px; color: #999; margin-top: 5px; }
        }
      }
      .item-right { display: flex; gap: 10px; }
    }
  }
}

/* 9. 图谱与目录样式 */
.graph-view-container {
  padding: 20px; height: 600px;
  .graph-layout { display: flex; height: 100%; gap: 20px; }
  .graph-sidebar {
    width: 240px;
    .stat-card {
      display: flex; align-items: center; padding: 15px; background: #F7F9FC; border-radius: 8px; margin-bottom: 15px;
      .icon-box { width: 40px; height: 40px; border-radius: 50%; display: flex; justify-content: center; align-items: center; color: #fff; font-size: 20px; margin-right: 12px; &.bg-blue { background: #409EFF; } &.bg-green { background: #67C23A; } }
      .stat-info { .label { font-size: 12px; color: #909399; } .value { font-size: 20px; font-weight: bold; color: #303133; } }
    }
    .legend-box {
      margin-top: 30px; padding-left: 10px;
      .legend-item { display: flex; align-items: center; margin-bottom: 10px; font-size: 14px; color: #606266; .dot { width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; } .root { background: #4075F3; } .l1 { background: #00BAAD; } .l2 { background: #68D391; } }
    }
  }
  .graph-main-wrapper {
    flex: 1; background: #FAFAFA; border-radius: 8px; border: 1px solid #eee; overflow: hidden; position: relative;
    .graph-main { width: 100%; height: 100%; }
    .outline-main { padding: 20px; height: 100%; background: #fff; box-sizing: border-box; :deep(.el-scrollbar) { height: 100%; } .custom-tree-node { display: flex; align-items: center; font-size: 14px; } }
  }
  .graph-tools {
    width: 100px; display: flex; flex-direction: column; gap: 15px;
    .tool-list {
      background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-radius: 4px; padding: 5px 0;
      .tool-btn {
        padding: 12px 10px; font-size: 12px; color: #606266; text-align: center; cursor: pointer; display: flex; flex-direction: column; align-items: center;
        &:hover { color: #409EFF; background: #F0F9FF; }
        &.active { color: #409EFF; background: #E6F1FC; font-weight: bold; border-left: 3px solid #409EFF; }
        .el-icon { font-size: 18px; margin-bottom: 4px; }
      }
    }
    .zoom-controls {
      background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-radius: 4px; padding: 5px 0; display: flex; flex-direction: column; align-items: center;
      .el-icon { padding: 10px; cursor: pointer; font-size: 18px; color: #606266; &:hover { color: #409EFF; } }
    }
  }
}

.catalog-view-container {
  padding: 20px 40px;
  .catalog-wrapper { background: #fff; }
  .catalog-header { display: flex; align-items: baseline; margin-bottom: 20px; border-bottom: 1px solid #eee; padding-bottom: 10px; h3 { margin: 0; font-size: 18px; color: #333; } .sub-text { margin-left: 10px; font-size: 12px; color: #999; } }
  .custom-catalog-tree { :deep(.el-tree-node__content) { height: 50px; border-bottom: 1px dashed #eee; } }
  .catalog-node {
    display: flex; justify-content: space-between; align-items: center; width: 100%; padding-right: 20px;
    &.level-1 { font-weight: bold; font-size: 16px; color: #303133; .chapter-icon { margin-right: 8px; color: #409EFF; } }
    &.level-2 { font-size: 14px; color: #606266; .section-icon { margin-right: 8px; color: #67C23A; } }
  }
}

.side-toolbar {
  position: fixed; right: 20px; bottom: 100px; z-index: 999; background: #fff; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); border-radius: 4px;
  .tool-item {
    width: 60px; height: 60px; display: flex; flex-direction: column; justify-content: center; align-items: center; cursor: pointer; color: #606266; border-bottom: 1px solid #f5f5f5;
    &:last-child { border-bottom: none; } &:hover { color: #409EFF; } span { font-size: 12px; margin-top: 4px; }
  }
}
</style>