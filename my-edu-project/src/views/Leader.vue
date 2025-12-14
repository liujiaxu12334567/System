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
          <div>
            <el-button type="danger" @click="openBatchExamDialog" style="margin-left: 10px;">
              批量发布考试
            </el-button>
            <el-button type="primary" @click="openBatchMaterialDialog" style="margin-left: 10px;">
              批量下发资料/任务
            </el-button>
          </div>
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

        <el-divider style="margin: 24px 0;" />

        <div class="panel-header" style="margin-top: 0;">
          <h3>教师数据分析</h3>
          <div style="display:flex; gap: 10px; align-items:center;">
            <el-input v-model="analysisMetric" placeholder="指标metric（默认 classroom_online_performance）" style="width: 320px;" />
            <el-button type="primary" :loading="analysisLoading" @click="fetchTeacherAnalysis">刷新</el-button>
          </div>
        </div>

        <el-table :data="teacherAnalysisList" border stripe style="width: 100%">
          <el-table-column prop="teacherName" label="教师" width="140" />
          <el-table-column prop="courseCount" label="课程数" width="90" align="center" />
          <el-table-column label="最新分析时间" width="170">
            <template #default="scope">
              <span>{{ formatTs(scope.row.latest?.generatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最新分析课程" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="scope.row.latest?.courseName">{{ scope.row.latest.courseName }}（{{ scope.row.latest.classId }}）</span>
              <span v-else class="text-gray">暂无</span>
            </template>
          </el-table-column>
          <el-table-column label="最新分析摘要" min-width="260" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="scope.row.latest?.valueJson">{{ previewJson(scope.row.latest.valueJson) }}</span>
              <span v-else class="text-gray">暂无</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="scope">
              <el-button size="small" plain @click="openTeacherAnalysisDetail(scope.row)">查看详情</el-button>
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

        <div v-if="selectedContentType === '测验'" class="quiz-editor">
          <el-alert title="请添加单选题。勾选单选框代表该选项为正确答案。" type="success" :closable="false" style="margin-bottom: 15px;" />
          <el-form label-width="80px">
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="测验标题"><el-input v-model="contentTitle" placeholder="如: 第一章阶段测试" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="截止时间"><el-date-picker v-model="contentDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" placeholder="选填" /></el-form-item></el-col>
            </el-row>
          </el-form>

          <div class="question-list-box">
            <div v-for="(q, index) in quizData.questions" :key="index" class="question-edit-item">
              <div class="q-header">
                <span class="q-idx">第 {{ index + 1 }} 题</span>
                <el-button type="danger" link size="small" @click="removeQuestion(index)">删除</el-button>
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
            <el-button type="primary" plain style="width:100%; margin-top:10px" @click="addQuestion">+ 添加单选题</el-button>
          </div>
        </div>

        <div v-else-if="selectedContentType === '知识图谱'" class="graph-editor-container">
          <el-form label-width="80px">
            <el-form-item label="图谱名称">
              <el-input v-model="contentTitle" placeholder="请输入图谱名称" />
            </el-form-item>
          </el-form>
          <div class="editor-layout">
            <div class="editor-panel">
              <div class="panel-section">
                <h4>1. 添加节点</h4>
                <div class="form-row">
                  <el-input v-model="newNodeName" placeholder="名称" size="small" style="width: 140px; margin-right:5px"/>
                  <el-select v-model="newNodeCategory" placeholder="类型" size="small" style="width: 80px; margin-right:5px">
                    <el-option label="根" :value="0" /><el-option label="一级" :value="1" /><el-option label="二级" :value="2" />
                  </el-select>
                  <el-button type="primary" size="small" @click="addNode">添加</el-button>
                </div>
                <div class="node-list">
                  <el-tag v-for="(n,i) in graphData.nodes" :key="i" closable @close="removeNode(i)" style="margin:2px">{{n.name}}</el-tag>
                </div>
              </div>
              <div class="panel-section">
                <h4>2. 建立连线</h4>
                <div class="form-row">
                  <el-select v-model="newLinkSource" size="small" placeholder="起点" style="width:100px"><el-option v-for="n in graphData.nodes" :key="n.id" :label="n.name" :value="n.id"/></el-select>
                  <span style="margin:0 5px">-></span>
                  <el-select v-model="newLinkTarget" size="small" placeholder="终点" style="width:100px"><el-option v-for="n in graphData.nodes" :key="n.id" :label="n.name" :value="n.id"/></el-select>
                  <el-button size="small" style="margin-left:5px" @click="addLink">连线</el-button>
                </div>
                <div class="link-list"><div v-for="(l,i) in graphData.links" :key="i" class="link-item">{{getNodeName(l.source)}}->{{getNodeName(l.target)}} <el-icon class="del-icon" @click="removeLink(i)"><Close /></el-icon></div></div>
              </div>
            </div>
            <div class="preview-panel"><div ref="chartRef" class="echarts-box"></div></div>
          </div>
        </div>

        <div v-else-if="selectedContentType === '目录'" class="catalog-editor-container">
          <el-form label-width="80px">
            <el-form-item label="目录名称">
              <el-input v-model="contentTitle" placeholder="请输入目录名称" />
            </el-form-item>
          </el-form>
          <div class="catalog-actions">
            <el-button type="primary" plain @click="addChapter">+ 添加章节</el-button>
            <el-button type="danger" plain @click="clearCatalog">清空目录</el-button>
          </div>
          <div class="catalog-tree-box">
            <el-tree :data="catalogData" node-key="id" default-expand-all :expand-on-click-node="false">
              <template #default="{ node, data }">
                <div class="custom-tree-node">
                  <span class="node-label">
                    <el-tag v-if="data.level===1" size="small" effect="dark">第 {{ data.index }} 章</el-tag>
                    <el-tag v-else size="small" type="info" effect="plain">小节</el-tag>
                    {{ data.label }}
                  </span>
                  <div class="tree-actions">
                    <el-button v-if="data.level === 1" link type="primary" size="small" @click="addSection(data)">添加小节</el-button>
                    <el-button link type="warning" size="small" @click="editCatalogNode(data)">重命名</el-button>
                    <el-button link type="danger" size="small" @click="removeCatalogNode(node, data)">删除</el-button>
                  </div>
                </div>
              </template>
            </el-tree>
            <el-empty v-if="catalogData.length === 0" description="暂无目录，请添加章节" :image-size="80" />
          </div>
        </div>

        <el-form v-else-if="selectedContentType === '教材'" label-width="100px" style="margin-top: 20px;">
          <el-form-item label="教材名称"><el-input v-model="textbookForm.name" /></el-form-item>
          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="ISBN"><el-input v-model="textbookForm.isbn" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="作者"><el-input v-model="textbookForm.author" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="出版社"><el-input v-model="textbookForm.publisher" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="版次"><el-input v-model="textbookForm.edition" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="简介"><el-input v-model="textbookForm.intro" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="链接"><el-input v-model="textbookForm.url" placeholder="http://" /></el-form-item>
        </el-form>

        <el-form v-else label-width="100px" style="margin-top: 20px;">
          <el-form-item label="标题" v-if="selectedContentType !== '教材'">
            <el-input v-model="contentTitle" :placeholder="titlePlaceholder" />
          </el-form-item>

          <el-form-item :label="selectedContentType === '作业' ? '作业要求' : '描述'" v-if="selectedContentType !== '教材'">
            <el-input
                v-model="contentPayload"
                type="textarea"
                :rows="6"
                :placeholder="selectedContentType === '作业' ? '请输入详细的作业要求，支持文字描述...' : '请输入内容描述...'"
            />
          </el-form-item>

          <el-form-item label="截止时间" v-if="['作业','测验','项目'].includes(selectedContentType)">
            <el-date-picker v-model="contentDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择截止时间" style="width: 100%" />
          </el-form-item>

          <el-form-item label="上传附件">
            <el-upload ref="uploadRef" action="#" :auto-upload="false" :on-change="(f,l)=>fileList=l" :limit="1">
              <template #trigger><el-button type="primary">选取文件</el-button></template>
              <template #tip>
                <div class="el-upload__tip" v-if="selectedContentType === '作业'">
                  可上传作业模板、参考资料等 (Word/PDF/ZIP)
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="contentDialogVisible = false">取消</el-button>
          <el-button type="success" :loading="uploading" @click="submitCourseMaterial">
            {{ getSubmitButtonText() }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="analysisDialogVisible" title="教师数据分析详情" width="900px">
        <div v-if="currentTeacherAnalysis" style="margin-bottom: 10px;">
          <div style="font-weight: 600; margin-bottom: 6px;">
            教师：{{ currentTeacherAnalysis.teacherName }}（课程数：{{ currentTeacherAnalysis.courseCount }}）
          </div>
          <div class="text-gray">metric：{{ analysisMetric || 'classroom_online_performance' }}</div>
        </div>
        <el-table v-if="currentTeacherAnalysis" :data="currentTeacherAnalysis.courses || []" border stripe height="520">
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="classId" label="班级" width="100" align="center" />
          <el-table-column prop="semester" label="学期" width="140" />
          <el-table-column label="生成时间" width="170">
            <template #default="scope">{{ formatTs(scope.row.generatedAt) }}</template>
          </el-table-column>
          <el-table-column label="分析JSON" min-width="320" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="scope.row.valueJson">{{ previewJson(scope.row.valueJson) }}</span>
              <span v-else class="text-gray">暂无</span>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="analysisDialogVisible = false">关闭</el-button>
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
        <p style="margin-bottom: 15px; font-weight: bold;">原任课教师: {{ currentRow.teacher || '未分配' }}</p>
        <el-select v-model="selectedTeacher" placeholder="请选择新的任课教师" style="width: 100%">
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

      <el-dialog v-model="batchExamDialogVisible" title="批量发布考试" width="800px" top="5vh">
        <el-form label-width="100px">
          <el-form-item label="目标课程">
            <el-select
                v-model="batchExamForm.courseNames"
                multiple
                filterable
                placeholder="请选择要发布考试的课程名称"
                style="width: 100%"
            >
              <el-option
                  v-for="c in distinctCourseNames"
                  :key="c"
                  :label="c"
                  :value="c"
              />
            </el-select>
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="考试标题"><el-input v-model="batchExamForm.title" placeholder="如: 期中考试" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="考试时长"><el-input-number v-model="batchExamForm.duration" :min="10" :max="180" style="width: 100%;" /> 分钟</el-form-item></el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间">
                <el-date-picker v-model="batchExamForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" placeholder="选择考试开始时间" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="截止时间">
                <el-date-picker v-model="batchExamForm.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" placeholder="选择考试截止时间" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-divider>试题列表</el-divider>

        <div class="question-list-box">
          <div v-for="(q, index) in batchExamForm.questions" :key="index" class="question-edit-item">
            <div class="q-header">
              <span class="q-idx">第 {{ index + 1 }} 题</span>
              <el-button type="danger" link size="small" @click="batchExamForm.questions.splice(index, 1)">删除</el-button>
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

          <el-button type="primary" plain style="width:100%; margin-top:10px" @click="batchExamForm.questions.push({ title: '', options: ['','','',''], answer: 0, score: 20 })">+ 添加单选题</el-button>
        </div>

        <template #footer>
          <el-button @click="batchExamDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="submitBatchExam">确认批量发布</el-button>
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
                  v-for="item in contentTypes"
                  :key="item"
                  :label="item"
              >
                {{ item }}
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <div v-if="batchMaterialForm.type === '测验'" class="quiz-editor">
            <el-alert title="请添加单选题。勾选单选框代表该选项为正确答案。" type="success" :closable="false" style="margin-bottom: 15px;" />
            <el-form label-width="80px">
              <el-row :gutter="20">
                <el-col :span="12"><el-form-item label="测验标题"><el-input v-model="batchMaterialForm.title" placeholder="如: 第一章阶段测试" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="截止时间"><el-date-picker v-model="batchMaterialForm.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" placeholder="必填" /></el-form-item></el-col>
              </el-row>
            </el-form>
            <div class="question-list-box">
              <div v-for="(q, index) in quizData.questions" :key="index" class="question-edit-item">
                <div class="q-header">
                  <span class="q-idx">第 {{ index + 1 }} 题</span>
                  <el-button type="danger" link size="small" @click="removeQuestion(index)">删除</el-button>
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
              <el-button type="primary" plain style="width:100%; margin-top:10px" @click="addQuestion">+ 添加单选题</el-button>
            </div>
          </div>

          <div v-else-if="batchMaterialForm.type === '知识图谱'" class="graph-editor-container">
            <el-form label-width="80px">
              <el-form-item label="图谱名称">
                <el-input v-model="batchMaterialForm.title" placeholder="请输入图谱名称" />
              </el-form-item>
            </el-form>
            <div class="editor-layout">
              <div class="editor-panel">
                <div class="panel-section">
                  <h4>1. 添加节点</h4>
                  <div class="form-row">
                    <el-input v-model="newNodeName" placeholder="名称" size="small" style="width: 140px; margin-right:5px"/>
                    <el-select v-model="newNodeCategory" placeholder="类型" size="small" style="width: 80px; margin-right:5px">
                      <el-option label="根" :value="0" /><el-option label="一级" :value="1" /><el-option label="二级" :value="2" />
                    </el-select>
                    <el-button type="primary" size="small" @click="addNode">添加</el-button>
                  </div>
                  <div class="node-list">
                    <el-tag v-for="(n,i) in graphData.nodes" :key="i" closable @close="removeNode(i)" style="margin:2px">{{n.name}}</el-tag>
                  </div>
                </div>
                <div class="panel-section">
                  <h4>2. 建立连线</h4>
                  <div class="form-row">
                    <el-select v-model="newLinkSource" size="small" placeholder="起点" style="width:100px"><el-option v-for="n in graphData.nodes" :key="n.id" :label="n.name" :value="n.id"/></el-select>
                    <span style="margin:0 5px">-></span>
                    <el-select v-model="newLinkTarget" size="small" placeholder="终点" style="width:100px"><el-option v-for="n in graphData.nodes" :key="n.id" :label="n.name" :value="n.id"/></el-select>
                    <el-button size="small" style="margin-left:5px" @click="addLink">连线</el-button>
                  </div>
                  <div class="link-list"><div v-for="(l,i) in graphData.links" :key="i" class="link-item">{{getNodeName(l.source)}}->{{getNodeName(l.target)}} <el-icon class="del-icon" @click="removeLink(i)"><Close /></el-icon></div></div>
                </div>
              </div>
              <div class="preview-panel"><div ref="chartRef" class="echarts-box"></div></div>
            </div>
          </div>

          <div v-else-if="batchMaterialForm.type === '目录'" class="catalog-editor-container">
            <el-form label-width="80px">
              <el-form-item label="目录名称">
                <el-input v-model="batchMaterialForm.title" placeholder="请输入目录名称" />
              </el-form-item>
            </el-form>
            <div class="catalog-actions">
              <el-button type="primary" plain @click="addChapter">+ 添加章节</el-button>
              <el-button type="danger" plain @click="clearCatalog">清空目录</el-button>
            </div>
            <div class="catalog-tree-box">
              <el-tree :data="catalogData" node-key="id" default-expand-all :expand-on-click-node="false">
                <template #default="{ node, data }">
                  <div class="custom-tree-node">
                    <span class="node-label">
                      <el-tag v-if="data.level===1" size="small" effect="dark">第 {{ data.index }} 章</el-tag>
                      <el-tag v-else size="small" type="info" effect="plain">小节</el-tag>
                      {{ data.label }}
                    </span>
                    <div class="tree-actions">
                      <el-button v-if="data.level === 1" link type="primary" size="small" @click="addSection(data)">添加小节</el-button>
                      <el-button link type="warning" size="small" @click="editCatalogNode(data)">重命名</el-button>
                      <el-button link type="danger" size="small" @click="removeCatalogNode(node, data)">删除</el-button>
                    </div>
                  </div>
                </template>
              </el-tree>
              <el-empty v-if="catalogData.length === 0" description="暂无目录，请添加章节" :image-size="80" />
            </div>
          </div>

          <el-form v-else-if="batchMaterialForm.type === '教材'" label-width="100px" style="margin-top: 20px;">
            <el-form-item label="教材名称"><el-input v-model="textbookForm.name" /></el-form-item>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="ISBN"><el-input v-model="textbookForm.isbn" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="作者"><el-input v-model="textbookForm.author" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="出版社"><el-input v-model="textbookForm.publisher" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="版次"><el-input v-model="textbookForm.edition" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="简介"><el-input v-model="textbookForm.intro" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="链接"><el-input v-model="textbookForm.url" placeholder="http://" /></el-form-item>
          </el-form>

          <el-form v-else label-width="100px" style="margin-top: 20px;">
            <el-form-item label="标题">
              <el-input v-model="batchMaterialForm.title" :placeholder="batchMaterialForm.type === '作业' ? '请输入作业标题 (如: 第一次大作业)' : '请输入标题'" />
            </el-form-item>

            <el-form-item :label="batchMaterialForm.type === '作业' ? '作业要求' : '描述'">
              <el-input
                  v-model="batchMaterialForm.content"
                  type="textarea"
                  :rows="6"
                  :placeholder="batchMaterialForm.type === '作业' ? '请输入详细的作业要求，支持文字描述...' : '请输入内容描述...'"
              />
            </el-form-item>

            <el-form-item label="截止时间" v-if="['作业','测验','项目'].includes(batchMaterialForm.type)">
              <el-date-picker v-model="batchMaterialForm.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择截止时间" style="width: 100%" />
            </el-form-item>

            <el-form-item label="上传附件">
              <el-upload ref="batchUploadRef" action="#" :auto-upload="false" :on-change="(f,l)=>batchMaterialForm.fileList=l" :limit="1">
                <template #trigger><el-button type="primary">选取文件</el-button></template>
                <template #tip>
                  <div class="el-upload__tip">
                    可上传附件 (Word/PDF/ZIP等)
                  </div>
                </template>
              </el-upload>
            </el-form-item>
          </el-form>
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
import { ref, reactive, onMounted, nextTick, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataLine, UserFilled, Switch, Bell, Close, DocumentChecked, ChatLineRound, Operation, Monitor } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const router = useRouter()
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  const activeMenu = ref('1')
  const courseList = ref([])
  const teacherList = ref([])
  const teacherAnalysisList = ref([])
  const analysisMetric = ref('classroom_online_performance')
  const analysisLoading = ref(false)
  const analysisDialogVisible = ref(false)
  const currentTeacherAnalysis = ref(null)
  const applicationList = ref([])

// 弹窗状态
const contentDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const notificationDialogVisible = ref(false)
const examDialogVisible = ref(false)
const batchMaterialDialogVisible = ref(false)
const batchExamDialogVisible = ref(false)
const currentRow = ref({})
const selectedTeacher = ref('')
// 图谱编辑器辅助状态 (最小化声明)
const newNodeName = ref('')
const newNodeCategory = ref(0)
const newLinkSource = ref(null)
const newLinkTarget = ref(null)

// 考试发布状态 (单个)
const examForm = reactive({ title: '', startTime: '', deadline: '', duration: 60, questions: [{ title: '', options: ['','','',''], answer: 0, score: 20 }] })
const addExamQuestion = () => examForm.questions.push({ title: '', options: ['','','',''], answer: 0, score: 20 })
const removeExamQuestion = (idx) => examForm.questions.splice(idx, 1)

// 批量考试发布状态
const batchExamForm = reactive({
  courseNames: [],
  title: '',
  startTime: '',
  deadline: '',
  duration: 60,
  questions: [{ title: '', options: ['','','',''], answer: 0, score: 20 }]
})

// 内容下发数据
const contentTypes = ref(['导学', '教材', '测验', '作业', '知识图谱', '目录', 'FAQ', '学习资料', '项目'])
const selectedContentType = ref('导学')
const contentTitle = ref('')
const contentPayload = ref('')
const contentDeadline = ref('')
const fileList = ref([])
const uploading = ref(false)

// 复杂类型数据结构
const quizData = reactive({ questions: [{ title: '', options: ['','','',''], answer: 0, score: 10 }] })
const textbookForm = reactive({ name:'', isbn:'', author:'', publisher:'', edition:'', intro:'', url:'' })
const graphData = reactive({ nodes: [], links: [], categories: [{name:'根节点'},{name:'一级知识点'},{name:'二级知识点'}] })
const catalogData = ref([])
const notificationForm = reactive({ title: '', content: '' })
const notificationTarget = ref(null)

// 批量下发表单
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

// 图表引用和辅助函数 (最小化实现)
const chartRef = ref(null); let myChart = null;
const initChart = () => { /* ... */ }
const resizeChart = () => myChart && myChart.resize()
const addNode = () => { /* ... */ }
const removeNode = (i) => { /* ... */ }
const addLink = () => { /* ... */ }
const removeLink = (i) => { /* ... */ }
const getNodeName = (id) => { /* ... */ }
const clearGraph = () => { catalogData.value = []; graphData.nodes=[]; graphData.links=[]; /* updateChartOption() */ }
// 目录逻辑 (保持不变)
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
      if (activeMenu.value === '2') {
        await fetchTeacherAnalysis()
      }
    } catch(e){}
  }

  const fetchTeacherAnalysis = async () => {
    analysisLoading.value = true
    try {
      teacherAnalysisList.value = await request.get('/leader/teacher/analysis', { params: { metric: analysisMetric.value } }) || []
    } catch (e) {
      ElMessage.error(e?.response?.data || e?.message || '加载教师分析失败')
    } finally {
      analysisLoading.value = false
    }
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

  const formatTs = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '—')
  const previewJson = (s) => {
    if (!s) return '—'
    const text = typeof s === 'string' ? s : JSON.stringify(s)
    return text.length > 140 ? text.slice(0, 140) + '…' : text
  }
  const openTeacherAnalysisDetail = (row) => {
    currentTeacherAnalysis.value = row
    analysisDialogVisible.value = true
  }

// 类型切换
const handleTypeChange = (type) => {
  selectedContentType.value = type
  if (type === '测验' && quizData.questions.length === 0) addQuestion()
  if (type === '知识图谱') nextTick(()=>{ initChart() })
}

// 批量下发类型切换时重置内容
const resetBatchContent = () => {
  batchMaterialForm.title = '';
  batchMaterialForm.content = '';
  batchMaterialForm.deadline = '';
  batchMaterialForm.fileList = [];
  quizData.questions = [{ title: '', options: ['','','',''], answer: 0, score: 20 }];
  catalogData.value = [];
  graphData.nodes = [];
  graphData.links = [];
  Object.keys(textbookForm).forEach(k => textbookForm[k] = '')

  if (batchUploadRef.value) batchUploadRef.value.clearFiles();
  if (batchMaterialForm.type === '知识图谱') nextTick(()=>{ initChart() })
}


// 打开单个考试对话框
const openExamDialog = () => {
  // 重新设置考试表单，确保至少有一道题
  examForm.title = '';
  examForm.startTime = '';
  examForm.deadline = '';
  examForm.duration = 60;
  examForm.questions = [{ title: '', options: ['','','',''], answer: 0, score: 20 }];
  examDialogVisible.value = true;
}

// 提交考试 (单个课程，逻辑不变)
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
  quizData.questions=[{ title: '', options: ['','','',''], answer: 0, score: 10 }];
  catalogData.value=[]; graphData.nodes=[]; graphData.links=[];
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

// 【关键：调整教师功能】
const openAssignDialog = (row) => {
  currentRow.value = row;
  // 预设当前教师姓名
  selectedTeacher.value = row.teacher || '';
  assignDialogVisible.value = true;
}

const submitAssign = async () => {
  if (!selectedTeacher.value) return ElMessage.warning('请选择任课教师');

  try {
    // 构造请求体，包含课程ID、新的教师姓名和班级ID (班级ID用于后端更新教师执教范围)
    const payload = {
      id: currentRow.value.id,
      teacher: selectedTeacher.value,
      classId: currentRow.value.classId
    };

    await request.post('/leader/course/update', payload);

    ElMessage.success('任课教师调整成功！');
    assignDialogVisible.value=false;
    fetchData(); // 刷新列表
  } catch (e) {
    ElMessage.error(e.response?.data || '调整失败');
  }
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

// 【新增】打开批量下发资料对话框
const openBatchMaterialDialog = () => {
  batchMaterialForm.courseNames = [];
  batchMaterialForm.type = '作业';
  resetBatchContent();
  batchMaterialDialogVisible.value = true;
}

// 【新增】打开批量发布考试对话框
const openBatchExamDialog = () => {
  batchExamForm.courseNames = [];
  batchExamForm.title = '';
  batchExamForm.startTime = '';
  batchExamForm.deadline = '';
  batchExamForm.duration = 60;
  // 确保批量考试问题列表被重置并初始化
  batchExamForm.questions = [{ title: '', options: ['','','',''], answer: 0, score: 20 }];

  batchExamDialogVisible.value = true;
}

// 【新增】提交批量发布考试
const submitBatchExam = async () => {
  if (batchExamForm.courseNames.length === 0) return ElMessage.warning('请选择至少一个目标课程');
  if (batchExamForm.questions.length === 0) return ElMessage.warning('请至少添加一道题目');
  if (!batchExamForm.title) return ElMessage.warning('请填写考试标题');
  if (!batchExamForm.startTime) return ElMessage.warning('请设置考试开始时间');
  if (!batchExamForm.deadline) return ElMessage.warning('请设置考试截止时间');

  if (dayjs(batchExamForm.startTime).isAfter(dayjs(batchExamForm.deadline))) {
    return ElMessage.warning('开始时间不能晚于截止时间');
  }

  const contentPayload = { questions: batchExamForm.questions }
  const status = dayjs(batchExamForm.startTime).isAfter(dayjs()) ? "未开始" : "进行中";

  try {
    await request.post(`/leader/course/batch-publish-exam`, {
      courseNames: batchExamForm.courseNames,
      title: batchExamForm.title,
      content: JSON.stringify(contentPayload),
      startTime: batchExamForm.startTime,
      deadline: batchExamForm.deadline,
      duration: batchExamForm.duration,
      status: status
    });
    ElMessage.success('批量考试发布成功，初始状态为: ' + status);
    batchExamDialogVisible.value = false;
    fetchData();
  } catch (e) {
    ElMessage.error(e.response?.data || '批量发布失败');
  }
}

// 【新增】提交批量下发 (资料)
const submitBatchMaterial = async () => {
  if (batchMaterialForm.courseNames.length === 0) return ElMessage.warning('请选择至少一个目标课程');

  let finalContent = batchMaterialForm.content;
  let finalTitle = batchMaterialForm.title;
  const type = batchMaterialForm.type;
  const deadline = batchMaterialForm.deadline;

  // --- 1. 数据校验与内容构造 ---
  if (type === '测验') {
    if (quizData.questions.length === 0) return ElMessage.warning('请至少添加一道题目');
    if (!deadline) return ElMessage.warning('请设置截止时间');
    finalContent = JSON.stringify({ deadline, questions: quizData.questions });
    if (!finalTitle) finalTitle = '在线测验';
  } else if (type === '教材') {
    if (!textbookForm.name) return ElMessage.warning('请填写教材名称');
    finalContent = JSON.stringify({ ...textbookForm });
    finalTitle = textbookForm.name;
  } else if (type === '知识图谱') {
    finalContent = JSON.stringify(graphData);
    if (!finalTitle) finalTitle = '知识图谱';
  } else if (type === '目录') {
    if (catalogData.value.length === 0) return ElMessage.warning('请添加章节目录');
    finalContent = JSON.stringify(catalogData.value);
    if (!finalTitle) finalTitle = '课程目录';
  } else if (['作业', '项目'].includes(type)) {
    if (!finalTitle) return ElMessage.warning('请填写任务标题');
    if (!deadline) return ElMessage.warning('请设置截止时间');
    const payload = { text: finalContent, deadline };
    finalContent = JSON.stringify(payload);
  }

  if (!finalTitle) return ElMessage.warning('请填写资料标题');

  uploading.value = true;

  const formData = new FormData();
  formData.append('type', type);
  formData.append('title', finalTitle);
  formData.append('content', finalContent);

  // 截止时间
  if (deadline) {
    formData.append('deadline', deadline);
  } else if (['作业', '项目', '测验'].includes(type)) {
    formData.append('deadline', '');
  }

  batchMaterialForm.courseNames.forEach(name => {
    formData.append('courseNames', name);
  });

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
