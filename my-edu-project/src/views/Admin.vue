<template>
  <div class="teacher-layout-container">
    <el-aside width="220px" class="sidebar-bright">
      <div class="brand-header">
        <svg width="180" height="50" viewBox="0 0 200 60" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M30 38V26.8182L18 20L39.8182 8L61.6364 20V38" stroke="#409EFF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M18 20L39.8182 32L61.6364 20" stroke="#409EFF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <text x="75" y="36" fill="#303133" font-family="Arial, sans-serif" font-size="20" font-weight="bold">教师工作台</text>
          <text x="75" y="52" fill="#909399" font-family="Arial, sans-serif" font-size="12">Academic Platform</text>
        </svg>
      </div>

      <el-menu
          :default-active="activeMenu"
          class="el-menu-bright"
          @select="handleMenuSelect"
          background-color="#ffffff"
          text-color="#606266"
          active-text-color="#409EFF"
      >
        <el-menu-item index="1"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item index="2"><el-icon><Tickets /></el-icon><span>批量分班/入学</span></el-menu-item>
        <el-menu-item index="3"><el-icon><Reading /></el-icon><span>课程管理</span></el-menu-item>
        <el-menu-item index="4"><el-icon><DocumentChecked /></el-icon><span>申请审核</span></el-menu-item>
        <el-menu-item index="5"><el-icon><Bell /></el-icon><span>通知管理与统计</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content-bright">
      <div v-if="activeMenu === '1'" class="fade-in">
        <div class="header-actions-top">
          <h2 class="page-title">用户管理</h2>
          <div class="right-btns">
            <el-button type="warning" plain @click="openNotifyDialog" style="margin-right: 10px;">
              <el-icon style="margin-right: 4px"><Bell /></el-icon> 下发通知
            </el-button>
            <el-button type="primary" @click="openDialog(null)" class="add-button">+ 新增用户</el-button>
          </div>
        </div>

        <el-card shadow="hover" class="filter-card-bright">
          <div class="filter-controls">
            <el-select v-model="roleFilter" placeholder="按角色筛选" clearable @change="handleRoleChange" style="width: 160px; margin-right: 15px">
              <el-option label="全部用户" :value="null" />
              <el-option label="管理员" value="1" />
              <el-option label="课题组长" value="2" />
              <el-option label="普通教师" value="3" />
              <el-option label="学生" value="4" />
              <el-option label="素质教师" value="5" />
            </el-select>
            <el-input v-model="keyword" placeholder="🔍 搜索用户名/姓名" style="width: 260px;" @input="fetchUsers" clearable>
              <template #append><el-button :icon="Search" /></template>
            </el-input>
          </div>
        </el-card>

        <div class="table-container-bright">
          <el-table :data="userList" border style="width: 100%;" header-cell-class-name="table-header-bright">
            <el-table-column prop="username" label="账号/学号" width="140" />
            <el-table-column prop="realName" label="真实姓名" width="100" font-weight="bold"/>
            <el-table-column label="角色" width="110">
              <template #default="scope">
                <el-tag :type="getRoleTag(scope.row.roleType)" effect="light" round>{{ getRoleName(scope.row.roleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="班级/执教范围" min-width="180">
              <template #default="scope">
                <span v-if="scope.row.roleType === '4'" class="info-text">班级: <strong>{{ scope.row.classId || '未分班' }}</strong></span>
                <span v-else-if="scope.row.roleType === '2'" class="info-text">
                  负责: <strong>{{ scope.row.teacherRank || '未分配' }}</strong><span v-if="scope.row.college">（学院：{{ scope.row.college }}）</span>
                </span>
                <span v-else-if="scope.row.roleType === '3'" class="info-text">
                  学院: <strong>{{ scope.row.college || '-' }}</strong>
                </span>
                <span v-else-if="scope.row.roleType === '5'" class="info-text">
                  负责班级: <strong>{{ scope.row.teachingClasses || '-' }}</strong><span v-if="scope.row.college">（学院：{{ scope.row.college }}）</span>
                </span>
                <span v-else class="info-text">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" class-name="time-col"/>
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="openDialog(scope.row)">编辑</el-button>
                <el-popconfirm title="确定删除该用户吗？" @confirm="handleDelete(scope.row.userId)">
                  <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-container-bright">
          <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange" :current-page="pageNum" :page-sizes="[10, 20, 50]" :page-size="pageSize" layout="total, sizes, prev, pager, next, jumper" :total="total" background />
        </div>
      </div>

      <div v-if="activeMenu === '2'" class="batch-enrollment-container fade-in">
        <h2 class="page-title">批量学生入学与分班</h2>
        <el-alert title="操作指南" type="info" show-icon class="mb-20">
          <template #default>
            支持 .xlsx 或 .csv 格式。请确保第一行为表头（如'姓名'），第二行开始为真实数据。
          </template>
        </el-alert>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="hover" class="action-card">
              <template #header><div class="card-header"><span>🚀 学号范围快速创建</span></div></template>
              <el-form :model="rangeForm" label-width="100px">
                <el-form-item label="学号起始"><el-input v-model="rangeForm.startUsername" placeholder="例: 24107311201" /></el-form-item>
                <el-form-item label="学号结束"><el-input v-model="rangeForm.endUsername" placeholder="例: 24107311220" /></el-form-item>
                <el-form-item label="目标班级ID"><el-input v-model="rangeForm.targetClassId" placeholder="例: 202303" /></el-form-item>
                <el-form-item label="所属专业"><el-input v-model="rangeForm.major" placeholder="例: 软件工程" /></el-form-item>
                <el-form-item><el-button type="primary" :loading="loading.range" @click="submitRangeEnroll" style="width: 100%; margin-top: 10px;">开始批量创建</el-button></el-form-item>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover" class="action-card">
              <template #header><div class="card-header"><span>📂 表格文件导入</span></div></template>
              <el-form :model="uploadForm" label-width="100px">
                <el-form-item label="目标班级ID">
                  <el-input v-model="uploadForm.targetClassId" placeholder="例: 202303" />
                </el-form-item>
                <el-form-item label="所属专业">
                  <el-input v-model="uploadForm.major" placeholder="例: 软件工程" />
                </el-form-item>
                <el-form-item label="起始学号">
                  <el-input v-model="uploadForm.startUsername" placeholder="例: 24107311201" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="triggerUpload" :disabled="!isUploadReady" :loading="loading.upload" class="upload-btn-full">
                    {{ isUploadReady ? '确认并选择文件上传' : '请先填写上方信息' }}
                  </el-button>
                </el-form-item>
              </el-form>

              <div class="upload-area">
                <el-upload
                    ref="uploadRef"
                    class="upload-demo-bright"
                    drag
                    action="#"
                    :http-request="customUploadRequest"
                    :before-upload="beforeUploadCheck"
                    :show-file-list="true"
                    :auto-upload="false"
                    :limit="1"
                    :on-exceed="handleExceed"
                    :on-change="handleFileChange"
                >
                  <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                  <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
                </el-upload>
                <div class="debug-status">{{ debugStatus }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <div v-if="activeMenu === '3'" class="fade-in">
        <h2 class="page-title">课程管理</h2>
        <el-card shadow="never" class="content-panel-bright">
          <div class="panel-header-bright">
            <h3>课程列表</h3>
            <div class="header-buttons">
              <el-button type="warning" plain @click="openBatchAssignDialog" style="margin-right: 10px;">批量分配课程</el-button>
              <el-button type="success" plain @click="openCourseGroupDialog" style="margin-right: 10px;">课程组管理</el-button>
              <el-button type="info" plain @click="openTimetableDialog" style="margin-right: 10px;">课程表/排课</el-button>
              <el-button type="primary" @click="openCourseDialog">+ 分配课程到班级</el-button>
            </div>
          </div>
          <el-table :data="courseList" border style="width: 100%" header-cell-class-name="table-header-bright">
            <el-table-column prop="name" label="课程名称" min-width="180" font-weight="bold"/>
            <el-table-column prop="classId" label="所属班级" width="120" align="center">
              <template #default="scope"><el-tag size="small" effect="plain">{{ scope.row.classId }}班</el-tag></template>
            </el-table-column>
            <el-table-column prop="code" label="课程代码" width="100" />
            <el-table-column prop="semester" label="学期" width="150" />
            <el-table-column prop="teacher" label="任课教师" min-width="150">
              <template #default="scope">
                <el-tag v-if="scope.row.teacher" type="success" effect="light">{{ scope.row.teacher }}</el-tag>
                <el-tag v-else type="info" effect="plain">未分配</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope"><el-tag effect="plain">{{ scope.row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="openAssignDialog(scope.row)">分配教师</el-button>
                <el-popconfirm title="确定删除该课程吗？" @confirm="handleCourseDelete(scope.row.id)">
                  <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <div v-if="activeMenu === '4'" class="fade-in">
        <h2 class="page-title">待审核申请</h2>
        <el-card shadow="never" class="content-panel-bright">
          <el-alert v-if="applicationList.length === 0" title="当前没有待审核的申请记录" type="success" show-icon style="margin-bottom: 20px;" />
          <el-table :data="formattedApplications" border style="width: 100%" header-cell-class-name="table-header-bright">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="teacherName" label="申请人" width="100" />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="scope"><el-tag :type="getTypeTag(scope.row.type)" effect="light">{{ formatType(scope.row.type) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="申请内容" min-width="260">
              <template #default="scope">
                <div v-if="scope.row.type === 'QUALITY_ACTIVITY' || scope.row.type === 'QUALITY_COMPETITION'" class="app-card">
                  <el-image
                    v-if="scope.row.parsedContent?.img"
                    :src="resolveFileUrl(scope.row.parsedContent.img)"
                    fit="cover"
                    style="width: 60px; height: 60px; border-radius: 8px;"
                    :preview-src-list="[resolveFileUrl(scope.row.parsedContent.img)]"
                  />
                  <div class="app-card-text">
                    <div class="app-title">{{ scope.row.parsedContent?.title || '—' }}</div>
                    <div class="app-desc">{{ scope.row.parsedContent?.desc || '—' }}</div>
                  </div>
                </div>
                <div v-else-if="scope.row.type === 'LEAVE_APPLICATION'" class="app-meta">
                  <div><span class="label">请假类型：</span>{{ scope.row.parsedContent?.leaveType || '—' }}</div>
                  <div><span class="label">是否离校：</span>{{ scope.row.parsedContent?.isLeaving ? '是' : '否' }}</div>
                  <div><span class="label">联系方式：</span>{{ scope.row.parsedContent?.contact || '—' }}</div>
                </div>
                <div v-else-if="scope.row.type === 'DEADLINE_EXTENSION'" class="app-meta">
                  {{ scope.row.content }}
                </div>
                <div v-else class="app-meta">
                  {{ typeof scope.row.content === 'string' ? scope.row.content : JSON.stringify(scope.row.content) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="申请理由" min-width="150" show-overflow-tooltip/>
            <el-table-column prop="createTime" label="提交时间" width="160" class-name="time-col"/>
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="scope">
                <el-button link type="success" size="small" @click="handleReview(scope.row.id, 'APPROVED')">批准</el-button>
                <el-button link type="danger" size="small" @click="handleReview(scope.row.id, 'REJECTED')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <div v-if="activeMenu === '5'" class="fade-in">
        <h2 class="page-title">通知管理</h2>
        <el-card shadow="never" class="content-panel-bright">
          <div class="panel-header-bright">
            <h3>历史通知</h3>
            <el-button type="warning" plain @click="openNotifyDialog">
              <el-icon style="margin-right: 4px"><Promotion /></el-icon> 新建通知
            </el-button>
          </div>
          <el-table :data="notifyHistory" border style="width: 100%" header-cell-class-name="table-header-bright">
            <el-table-column prop="title" label="通知标题" min-width="150" font-weight="bold"/>
            <el-table-column prop="message" label="内容摘要" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createTime" label="发送时间" width="180" class-name="time-col">
              <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="scope">
                <el-tag v-if="scope.row.isActionRequired" type="danger" effect="dark">需填报</el-tag>
                <el-tag v-else type="info" effect="plain">普通通知</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right" align="center">
              <template #default="scope"><el-button link type="primary" size="small" @click="viewStats(scope.row)">查看统计</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>


      <el-dialog v-model="dialogVisible" :title="form.userId ? '编辑用户' : '新增用户'" width="500px" destroy-on-close>
        <el-form :model="form" label-width="100px" class="dialog-form">
          <el-form-item label="账号/学号"><el-input v-model="form.username" :disabled="!!form.userId" placeholder="请输入唯一账号"/></el-form-item>
          <el-form-item label="真实姓名"><el-input v-model="form.realName" placeholder="请输入姓名"/></el-form-item>
          <el-form-item label="角色身份">
            <el-select v-model="form.roleType" style="width:100%" placeholder="请选择">
              <el-option label="管理员" :value="1" />
              <el-option label="课题组长" :value="2" />
              <el-option label="普通教师" :value="3" />
              <el-option label="学生" :value="4" />
              <el-option label="素质教师" :value="5" />
            </el-select>
          </el-form-item>
          <template v-if="form.roleType === 2 || form.roleType === 3 || form.roleType === 5">
            <el-form-item label="学院"><el-input v-model="form.college" placeholder="例: 计算机学院"/></el-form-item>
          </template>
          <template v-if="form.roleType === 4 && !form.userId">
            <el-form-item label="专业"><el-input v-model="form.major" placeholder="例: 软件工程"/></el-form-item>
            <el-form-item label="班级ID"><el-input v-model="form.classId" type="number" placeholder="例: 202303"/></el-form-item>
          </template>
          <template v-if="form.roleType === 2">
            <el-form-item label="负责课程">
              <el-select v-model="form.managerCourses" multiple placeholder="请选择负责的课程" style="width: 100%">
                <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.name"/>
              </el-select>
            </el-form-item>
          </template>
          <template v-if="form.roleType === 5">
            <el-form-item label="负责班级">
              <el-select v-model="form.teachingClassesIds" multiple placeholder="请选择负责的班级" style="width: 100%">
                <el-option v-for="c in classList" :key="c.id" :label="c.name" :value="c.id"/>
              </el-select>
            </el-form-item>
          </template>
        </el-form>
        <template #footer><span class="dialog-footer"><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submitForm">确定</el-button></span></template>
      </el-dialog>

      <el-dialog v-model="courseDialogVisible" title="分配课程到班级" width="520px">
        <el-form :model="courseForm" label-width="80px">
          <el-form-item label="课程">
            <el-select v-model="courseForm.groupId" placeholder="请选择课程（课程组）" style="width: 100%">
              <el-option v-for="g in courseGroupList" :key="g.groupId" :label="`${g.name}（${g.semester}）`" :value="g.groupId"/>
            </el-select>
          </el-form-item>
          <el-form-item label="组长">
            <el-input :model-value="selectedCourseGroup?.leaderName || '未设置'" readonly />
          </el-form-item>
          <el-form-item label="专业">
            <el-select v-model="courseForm.major" filterable allow-create default-first-option placeholder="请选择专业" style="width: 100%">
              <el-option v-for="m in majorOptions" :key="m" :label="m" :value="m"/>
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="courseForm.classId" placeholder="请选择所属班级" style="width: 100%">
              <el-option v-for="c in filteredCourseClassList" :key="c.id" :label="c.name" :value="c.id"/>
            </el-select>
          </el-form-item>
          <el-form-item label="主讲教师">
            <el-select v-model="courseForm.teacherId" placeholder="请选择" style="width: 100%">
              <el-option v-for="t in teacherList" :key="t.userId" :label="formatTeacherLabel(t)" :value="t.userId"/>
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="courseDialogVisible = false">取消</el-button><el-button type="primary" @click="submitCourse">确认</el-button></template>
      </el-dialog>

      <el-dialog v-model="timetableDialogVisible" title="课程表 / 排课（到点才能开在线课堂）" width="980px" destroy-on-close>
        <el-alert
          title="说明：请为班级内每门课程设置开始/结束时间（开始时间必填）。老师只能在到达开始时间后开启在线课堂。"
          type="info"
          show-icon
          style="margin-bottom: 12px;"
        />
        <el-form :inline="true" label-width="80px" style="margin-bottom: 6px;">
          <el-form-item label="班级">
            <el-select v-model="timetableClassId" filterable placeholder="请选择班级" style="width: 260px" @change="fetchTimetable">
              <el-option v-for="c in classList" :key="c.id" :label="`${c.name}（${c.id}班）`" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="!timetableClassId" :loading="timetableLoading" @click="saveTimetable">保存排课</el-button>
            <el-button :disabled="!timetableClassId" :loading="timetableLoading" @click="fetchTimetable">刷新</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="timetableCourses" border style="width: 100%" v-loading="timetableLoading">
          <el-table-column prop="name" label="课程" min-width="160" />
          <el-table-column prop="teacher" label="任课教师" min-width="140">
            <template #default="scope">
              <el-tag v-if="scope.row.teacher" type="success" effect="light">{{ scope.row.teacher }}</el-tag>
              <el-tag v-else type="info" effect="plain">未分配</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="星期" width="120">
            <template #default="scope">
              <el-select v-model="scope.row.dayOfWeek" placeholder="请选择" style="width: 100%">
                <el-option label="周一" :value="1" />
                <el-option label="周二" :value="2" />
                <el-option label="周三" :value="3" />
                <el-option label="周四" :value="4" />
                <el-option label="周五" :value="5" />
                <el-option label="周六" :value="6" />
                <el-option label="周日" :value="7" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="200">
            <template #default="scope">
              <el-time-picker
                v-model="scope.row.startTime"
                value-format="HH:mm:ss"
                format="HH:mm"
                placeholder="请选择开始时间"
                style="width: 100%;"
              ></el-time-picker>
            </template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="200">
            <template #default="scope">
              <el-time-picker
                v-model="scope.row.endTime"
                value-format="HH:mm:ss"
                format="HH:mm"
                placeholder="可选"
                style="width: 100%;"
              ></el-time-picker>
            </template>
          </el-table-column>
          <el-table-column prop="semester" label="学期" width="120" />
        </el-table>
        <template #footer>
          <el-button @click="timetableDialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="batchAssignDialogVisible" title="批量分配" width="600px">
        <el-form :model="batchAssignForm" label-width="100px">
          <el-form-item label="课程">
            <el-select v-model="batchAssignForm.groupId" placeholder="请选择课程（课程组）" style="width: 100%">
              <el-option v-for="g in courseGroupList" :key="g.groupId" :label="`${g.name}（${g.semester}）`" :value="g.groupId"/>
            </el-select>
          </el-form-item>
          <el-form-item label="组长">
            <el-input :model-value="selectedBatchCourseGroup?.leaderName || '未设置'" readonly />
          </el-form-item>
          <el-form-item label="专业">
            <el-select v-model="batchAssignForm.major" filterable allow-create default-first-option placeholder="请选择专业" style="width: 100%">
              <el-option v-for="m in majorOptions" :key="m" :label="m" :value="m"/>
            </el-select>
          </el-form-item>
          <el-form-item label="目标班级">
            <el-select v-model="batchAssignForm.classIds" multiple style="width:100%">
              <el-option v-for="c in filteredBatchClassList" :key="c.id" :label="c.name" :value="c.id"/>
            </el-select>
          </el-form-item>
          <template v-for="cid in batchAssignForm.classIds" :key="cid">
            <el-form-item :label="`授课老师（${formatClassName(cid)}）`">
              <el-select v-model="batchAssignForm.teacherByClassId[cid]" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in teacherList" :key="t.userId" :label="formatTeacherLabel(t)" :value="t.userId"/>
              </el-select>
            </el-form-item>
          </template>
        </el-form>
        <template #footer><el-button @click="batchAssignDialogVisible = false">取消</el-button><el-button type="primary" @click="submitBatchAssign">确认</el-button></template>
      </el-dialog>

      <el-dialog v-model="assignDialogVisible" title="分配教师" width="400px">
        <el-select v-model="selectedTeacherId" style="width:100%">
          <el-option v-for="t in teacherList" :key="t.userId" :label="formatTeacherLabel(t)" :value="t.userId"/>
        </el-select>
        <template #footer><el-button @click="assignDialogVisible = false">取消</el-button><el-button type="primary" @click="submitAssign">确认</el-button></template>
      </el-dialog>

      <el-dialog v-model="courseGroupDialogVisible" title="课程组管理" width="780px">
        <el-form :model="courseGroupCreateForm" label-width="80px" style="margin-bottom: 12px;">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="课程名称">
                <el-input v-model="courseGroupCreateForm.name" placeholder="例如：Python数据分析" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="学期">
                <el-select v-model="courseGroupCreateForm.semester" style="width: 100%">
                  <el-option label="2025-2026学年 第1学期" value="2025-1" />
                  <el-option label="2024-2025学年 第2学期" value="2024-2" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="组长">
                <el-select v-model="courseGroupCreateForm.leaderId" clearable placeholder="可选" style="width: 100%">
                  <el-option v-for="t in teacherList" :key="t.userId" :label="formatTeacherLabel(t)" :value="t.userId"/>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="2" style="display:flex; align-items:center;">
              <el-button type="primary" @click="createCourseGroup">创建</el-button>
            </el-col>
          </el-row>
        </el-form>

        <el-table :data="courseGroupList" border style="width: 100%">
          <el-table-column prop="name" label="课程名称" min-width="180"/>
          <el-table-column prop="semester" label="学期" width="140"/>
          <el-table-column label="组长" min-width="200">
            <template #default="scope">
              <el-select
                v-model="scope.row.leaderId"
                clearable
                placeholder="未设置"
                style="width: 100%"
                @change="() => updateCourseGroupLeader(scope.row)"
              >
                <el-option v-for="t in teacherList" :key="t.userId" :label="formatTeacherLabel(t)" :value="t.userId"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="提示" min-width="220">
            <template #default="scope">
              <span v-if="scope.row.leaderName">当前：{{ scope.row.leaderName }}</span>
              <span v-else style="color: #999;">未设置组长（将无法分配班级）</span>
            </template>
          </el-table-column>
        </el-table>

        <template #footer>
          <el-button @click="courseGroupDialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="notifyDialogVisible" title="下发通知" width="600px">
        <el-form :model="notifyForm" label-width="80px">
          <el-form-item label="标题"><el-input v-model="notifyForm.title" /></el-form-item>
          <el-form-item label="内容"><el-input v-model="notifyForm.content" type="textarea" /></el-form-item>
          <el-form-item label="对象">
            <el-radio-group v-model="notifyForm.targetType">
              <el-radio label="SPECIFIC">指定用户</el-radio>
              <el-radio label="ALL_STUDENTS">全体学生</el-radio>
              <el-radio label="ALL_TEACHERS">全体教师</el-radio>
              <el-radio label="ALL">全校所有用户</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="选择用户" v-if="notifyForm.targetType === 'SPECIFIC'">
            <el-select
                v-model="notifyForm.userIds"
                multiple
                filterable
                remote
                :remote-method="searchUsersForNotify"
                placeholder="输入姓名搜索"
                style="width: 100%"
            >
              <el-option v-for="item in notifyUserOptions" :key="item.userId" :label="item.realName + ' (' + item.username + ')'" :value="item.userId"/>
            </el-select>
          </el-form-item>
          <el-form-item label="回执要求">
            <el-checkbox v-model="notifyForm.needReply">要求用户填写信息/回复</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="notifyDialogVisible = false">取消</el-button><el-button type="primary" @click="submitNotification">发送</el-button></template>
      </el-dialog>

      <el-dialog v-model="statsDialogVisible" title="通知统计详情" width="800px">
        <div style="margin-bottom:15px; font-weight:bold;">通知标题：{{ currentStatsTitle }}</div>
        <div class="stats-summary">
          <div class="summary-item">
            <span class="label">发送人数</span>
            <span class="value">{{ currentStatsSummary.total || 0 }}</span>
          </div>
          <div class="summary-item">
            <span class="label">已读</span>
            <span class="value success">{{ currentStatsSummary.readCount || 0 }}</span>
          </div>
          <div class="summary-item">
            <span class="label">已回复</span>
            <span class="value primary">{{ currentStatsSummary.replyCount || 0 }}</span>
          </div>
          <div class="summary-item">
            <span class="label">需填报</span>
            <span class="value">{{ currentStatsSummary.needReply ? '是' : '否' }}</span>
          </div>
          <div class="summary-item">
            <span class="label">阅读率</span>
            <span class="value">{{ calcRate(currentStatsSummary.readCount, currentStatsSummary.total) }}%</span>
          </div>
          <div class="summary-item">
            <span class="label">回复率</span>
            <span class="value">{{ calcRate(currentStatsSummary.replyCount, currentStatsSummary.total) }}%</span>
          </div>
        </div>
        <el-table :data="currentStatsList" height="400" border stripe>
          <el-table-column property="realName" label="姓名" width="120" />
          <el-table-column property="username" label="学号/工号" width="120" />
          <el-table-column property="roleType" label="身份" width="100">
            <template #default="scope">{{ getRoleName(scope.row.roleType) }}</template>
          </el-table-column>
          <el-table-column property="isRead" label="状态" width="100">
            <template #default="scope">
              <el-tag v-if="scope.row.isRead" type="success">已读/已回</el-tag>
              <el-tag v-else type="info">未读</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="userReply" label="填报/回复内容" min-width="200">
            <template #default="scope">
              <span v-if="scope.row.userReply" style="color:#333">{{ scope.row.userReply }}</span>
              <span v-else style="color:#ccc">--</span>
            </template>
          </el-table-column>
          <el-table-column property="createTime" label="发送时间" width="160">
            <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </el-dialog>

    </el-main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, watch } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { User, Tickets, Reading, DocumentChecked, Bell, UploadFilled, Search, Promotion, Close } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

// 状态定义
const activeMenu = ref('1')
const userList = ref([])
const courseList = ref([])
const applicationList = ref([])
const notifyHistory = ref([])
const teacherList = ref([])
const courseGroupList = ref([])
const classList = ref([])
const loading = reactive({ range: false, upload: false })
const debugStatus = ref('等待操作...')
const formattedApplications = computed(() => {
  return (applicationList.value || []).map(item => ({
    ...item,
    parsedContent: parseApplicationContent(item.content)
  }))
})

// 表单数据
const rangeForm = reactive({ startUsername: '', endUsername: '', targetClassId: null, major: null })
const uploadForm = reactive({ targetClassId: '', startUsername: '', major: '' })
const uploadPayload = computed(() => ({
  targetClassId: uploadForm.targetClassId,
  startUsername: uploadForm.startUsername,
  major: uploadForm.major
}))
const uploadActionUrl = '/api/admin/batch/upload'
const uploadRef = ref(null)
const courseForm = ref({ groupId: null, major: null, classId: null, teacherId: null })
const batchAssignForm = ref({ groupId: null, major: null, classIds: [], teacherByClassId: {} })
const courseGroupDialogVisible = ref(false)
const courseGroupCreateForm = ref({ name: '', semester: '2025-1', leaderId: null })
const notifyForm = reactive({ title: '', content: '', targetType: 'SPECIFIC', userIds: [], needReply: false })
const form = ref({ managerCourses: [], teachingClassesIds: [] }) // 用户表单

// 分页筛选
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const roleFilter = ref(null)
const classFilter = ref(null)
const subjectFilter = ref(null)

// 弹窗相关
const dialogVisible = ref(false)
const courseDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const batchAssignDialogVisible = ref(false)
const timetableDialogVisible = ref(false)
const notifyDialogVisible = ref(false)
const statsDialogVisible = ref(false)
const currentRow = ref({})
const selectedTeacherId = ref(null)
const notifyUserOptions = ref([])
const currentStatsList = ref([])
const currentStatsTitle = ref('')
const currentStatsSummary = ref({ total: 0, readCount: 0, replyCount: 0, needReply: false })

// 课程表/排课
const timetableClassId = ref(null)
const timetableCourses = ref([])
const timetableLoading = ref(false)

// 计算属性：检查上传条件是否满足
const isUploadReady = computed(() => {
  return uploadForm.targetClassId && uploadForm.startUsername && uploadForm.major
})

const classNameMap = computed(() => {
  const map = new Map()
  ;(classList.value || []).forEach((c) => map.set(Number(c.id), c.name))
  return map
})

const formatClassName = (classId) => classNameMap.value.get(Number(classId)) || String(classId)

const majorOptions = computed(() => {
  const majors = new Set()
  ;(classList.value || []).forEach((c) => {
    const m = (c?.major || '').trim()
    if (!m || m === '未分配专业') return
    majors.add(m)
  })
  return Array.from(majors)
})

const filteredCourseClassList = computed(() => {
  const m = (courseForm.value.major || '').trim()
  if (!m) return []
  return (classList.value || []).filter((c) => (c?.major || '').trim() === m)
})

const filteredBatchClassList = computed(() => {
  const m = (batchAssignForm.value.major || '').trim()
  if (!m) return []
  return (classList.value || []).filter((c) => (c?.major || '').trim() === m)
})

const formatTeacherLabel = (t) => {
  const name = t?.realName ? String(t.realName) : ''
  const college = t?.college ? String(t.college).trim() : ''
  return college ? `${name}（${college}）` : name
}

const selectedCourseGroup = computed(() => {
  if (!courseForm.value.groupId) return null
  return (courseGroupList.value || []).find((g) => g.groupId === courseForm.value.groupId) || null
})

const selectedBatchCourseGroup = computed(() => {
  if (!batchAssignForm.value.groupId) return null
  return (courseGroupList.value || []).find((g) => g.groupId === batchAssignForm.value.groupId) || null
})

watch(
  () => batchAssignForm.value.classIds,
  (classIds) => {
    const selected = new Set((classIds || []).map((v) => Number(v)))
    const mapping = batchAssignForm.value.teacherByClassId || {}
    Object.keys(mapping).forEach((key) => {
      if (!selected.has(Number(key))) delete mapping[key]
    })
    batchAssignForm.value.teacherByClassId = mapping
  },
  { deep: true }
)

watch(
  () => courseForm.value.major,
  () => {
    courseForm.value.classId = null
  }
)

watch(
  () => batchAssignForm.value.major,
  () => {
    batchAssignForm.value.classIds = []
    batchAssignForm.value.teacherByClassId = {}
  }
)

onMounted(() => {
  fetchUsers()
  fetchCourseAndTeacherData()
})

// ==========================================
// 【核心】自定义上传请求逻辑
// ==========================================
const triggerUpload = () => {
  if (!isUploadReady.value) {
    return ElMessage.warning('请先填写班级ID、专业和起始学号！')
  }
  // 触发文件选择框
  document.querySelector('.el-upload__input').click()
}

// 1. 上传前检查格式
const beforeUploadCheck = (file) => {
  console.log('【调试】文件选中:', file.name)
  const isExcelOrCsv = /\.(xlsx|csv)$/i.test(file.name)
  if (!isExcelOrCsv) {
    ElMessage.error('仅支持 .xlsx 或 .csv 文件！')
    return false
  }
  return true
}

// 2. 自定义上传函数（完全替代 el-upload 的默认请求）
const customUploadRequest = async (options) => {
  const { file } = options

  console.log('【调试】开始执行自定义上传...')
  debugStatus.value = '正在准备发送请求...'
  loading.upload = true

  const token = localStorage.getItem('token')
  console.log('【调试】当前 Token:', token ? (token.substring(0, 10) + '...') : '无')

  if (!token) {
    ElMessage.error('认证失效，请重新登录！')
    loading.upload = false
    return
  }

  // 组装 FormData
  const formData = new FormData()
  formData.append('file', file)
  formData.append('targetClassId', uploadForm.targetClassId)
  formData.append('startUsername', uploadForm.startUsername)
  formData.append('major', uploadForm.major)

  // 发送请求
  try {
    debugStatus.value = '正在上传数据...'
    const res = await request.post('/admin/batch/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 20000
    })

    console.log('【调试】上传成功，后端返回:', res)
    ElMessage.success(res || '批量导入成功！')
    debugStatus.value = '上传成功！'

    // 清理文件
    uploadRef.value.clearFiles()
    fetchUsers()

  } catch (error) {
    console.error('【调试】上传报错:', error)

    let errMsg = '上传失败'
    if (error.message && error.message.includes('401')) {
      errMsg = '认证失败 (401)，请检查 Token 是否过期'
    } else if (error.response && error.response.data) {
      errMsg = typeof error.response.data === 'string' ? error.response.data : JSON.stringify(error.response.data)
    }

    ElMessage.error(errMsg)
    debugStatus.value = '上传失败: ' + errMsg
  } finally {
    loading.upload = false
  }
}

// 当用户选择文件时触发
const handleFileChange = (uploadFile) => {
  // 这里可以拿到 raw file
  if(isUploadReady.value) {
    // 如果要实现选择即上传，可以在这里调用 customUploadRequest({ file: uploadFile.raw })
    // 但为了更可控，我们还是用 triggerUpload 按钮触发，这里只作为状态更新
  }
}

const handleExceed = (files) => {
  uploadRef.value.clearFiles()
  const file = files[0]
  uploadRef.value.handleStart(file)
  if(isUploadReady.value) {
    customUploadRequest({ file: file })
  }
}

// ==========================================
// 其他业务逻辑
// ==========================================
const fetchUsers = async () => {
  try {
    const params = {
      keyword: keyword.value,
      roleType: roleFilter.value,
      classId: classFilter.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    };
    // 清理空值
    Object.keys(params).forEach(key => { if (params[key] === null || params[key] === '') delete params[key]; });

    const res = await request.get('/admin/user/list', { params })
    userList.value = res.list || []
    total.value = res.total || 0
  } catch (e) { console.error(e) }
}

const fetchCourseAndTeacherData = async () => {
  try {
    const resC = await request.get('/admin/course/list')
    courseList.value = resC || []
    const [resLeaders, resTeachers, resGroups] = await Promise.all([
      request.get('/admin/user/list', { params: { roleType: '2', pageNum: 1, pageSize: 1000 } }),
      request.get('/admin/user/list', { params: { roleType: '3', pageNum: 1, pageSize: 1000 } }),
      request.get('/admin/course-group/list')
    ])
    teacherList.value = [...(resLeaders?.list || []), ...(resTeachers?.list || [])]
    courseGroupList.value = resGroups || []
    const resCl = await request.get('/admin/classes')
    classList.value = resCl || []
  } catch(e) {}
}


const submitRangeEnroll = async () => {
  if (!rangeForm.startUsername || !rangeForm.targetClassId) return ElMessage.warning('请补全信息')
  loading.range = true
  try {
    const res = await request.post('/admin/batch/enroll', rangeForm)
    ElMessage.success(res)
  } catch (e) { ElMessage.error(e.response?.data || '失败') }
  finally { loading.range = false }
}

// 菜单切换
const handleMenuSelect = (idx) => {
  activeMenu.value = idx;
  if(idx==='1') fetchUsers()
  if(idx==='3') fetchCourseAndTeacherData()
  if(idx==='4') {
    request.get('/admin/applications/pending').then(res => applicationList.value = res || [])
  }
  if(idx==='5') {
    request.get('/admin/notification/history').then(res => notifyHistory.value = res || [])
  }
}

// 辅助函数
const getRoleName = (t) => ({'1':'管理员','2':'课题组长','3':'普通教师','4':'学生','5':'素质教师'}[t] || '未知')
const getRoleTag = (t) => ({'1':'danger','2':'success','3':'primary','4':'info','5':'warning'}[t] || 'info')
const getTypeTag = (t) => ({
  ADD:'success',
  DELETE:'danger',
  RESET_PWD:'warning',
  DEADLINE_EXTENSION:'warning',
  QUALITY_ACTIVITY:'primary',
  QUALITY_COMPETITION:'primary',
  LEAVE_APPLICATION:'info'
}[t] || 'info')
const formatType = (t) => ({
  ADD:'新增学生',
  DELETE:'删除学生',
  RESET_PWD:'重置密码',
  DEADLINE_EXTENSION:'延期申请',
  QUALITY_ACTIVITY:'素质活动',
  QUALITY_COMPETITION:'素质竞赛',
  LEAVE_APPLICATION:'请假申请'
}[t]||t)
const formatDate = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : ''
const parseApplicationContent = (raw) => {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch (e) {
    return { text: raw }
  }
}
const resolveFileUrl = (path) => {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  if (path.startsWith('//')) return window.location.protocol + path
  if (path.startsWith('/')) return window.location.origin + path
  return `${window.location.origin}/${path}`
}

const handleSizeChange = (v) => { pageSize.value = v; fetchUsers() }
const handleCurrentChange = (v) => { pageNum.value = v; fetchUsers() }
const handleRoleChange = () => { classFilter.value = null; subjectFilter.value = null; fetchUsers(); }

// 简单CRUD
const openDialog = (row) => {
  if (row) {
    const isLeader = row.roleType === '2';
    const isQualityTeacher = row.roleType === '5';
    form.value = {
      userId: row.userId,
      username: row.username,
      realName: row.realName,
      roleType: Number(row.roleType),
      password: '',
      college: row.college || '',
      classId: row.classId,
      teachingClasses: row.teachingClasses,
      major: null,
      managerCourses: isLeader && row.teacherRank ? row.teacherRank.split(',') : [],
      teachingClassesIds: isQualityTeacher && row.teachingClasses ? row.teachingClasses.split(',').map(id => Number(id)) : []
    }
  } else {
    form.value = { roleType: 4, college: '', managerCourses: [], teachingClassesIds: [] }
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const url = form.value.userId ? '/admin/user/update' : '/admin/user/add'
  // 处理数组转字符串
  const payload = { ...form.value, roleType: String(form.value.roleType) }
  if (form.value.roleType === 2) payload.teacherRank = form.value.managerCourses.join(',')
  if (form.value.roleType === 5) {
    if (form.value.teachingClassesIds && form.value.teachingClassesIds.length > 4) {
      return ElMessage.warning('素质教师最多分配4个班级')
    }
    payload.teachingClasses = form.value.teachingClassesIds.join(',')
  }
  delete payload.managerCourses; delete payload.teachingClassesIds;

  try {
    await request.post(url, payload)
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchUsers()
  } catch(e) { ElMessage.error('操作失败') }
}

const handleDelete = async (id) => {
  await request.post(`/admin/user/delete/${id}`)
  fetchUsers()
}

// 课程相关
const openTimetableDialog = () => {
  timetableDialogVisible.value = true
  timetableClassId.value = null
  timetableCourses.value = []
}

const fetchTimetable = async () => {
  if (!timetableClassId.value) return
  timetableLoading.value = true
  try {
    const res = await request.get('/admin/course/timetable', { params: { classId: timetableClassId.value } })
    timetableCourses.value = res || []
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '获取课程表失败')
  } finally {
    timetableLoading.value = false
  }
}

const saveTimetable = async () => {
  if (!timetableClassId.value) return ElMessage.warning('请选择班级')
  const list = timetableCourses.value || []
  if (list.length === 0) return ElMessage.warning('该班级暂无课程')

  const schedules = list.map((c) => ({
    courseId: c.id,
    dayOfWeek: c.dayOfWeek,
    startTime: c.startTime,
    endTime: c.endTime
  }))
  const missing = schedules.filter((s) => !s.dayOfWeek || !s.startTime)
  if (missing.length > 0) return ElMessage.warning('请为每门课设置星期与开始时间')

  timetableLoading.value = true
  try {
    await request.post('/admin/course/schedule/batch', {
      classId: timetableClassId.value,
      schedules
    })
    ElMessage.success('排课已保存')
    await fetchCourseAndTeacherData()
    await fetchTimetable()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '保存失败')
  } finally {
    timetableLoading.value = false
  }
}

const openCourseDialog = () => {
  courseForm.value = { groupId: null, major: null, classId: null, teacherId: null }
  courseDialogVisible.value = true
}
const submitCourse = async () => {
  if (!courseForm.value.groupId) return ElMessage.warning('请选择课程')
  if (!String(courseForm.value.major || '').trim()) return ElMessage.warning('请选择专业')
  if (!courseForm.value.classId) return ElMessage.warning('请选择班级')
  if (!courseForm.value.teacherId) return ElMessage.warning('请选择主讲教师')
  if (!selectedCourseGroup.value?.leaderId) return ElMessage.warning('该课程尚未设置组长，请先在课程组管理中指定')

  try {
    await request.post('/admin/course/assign', {
      groupId: courseForm.value.groupId,
      major: String(courseForm.value.major || '').trim(),
      classId: courseForm.value.classId,
      teacherId: courseForm.value.teacherId
    })
    ElMessage.success('分配成功')
    courseDialogVisible.value = false
    fetchCourseAndTeacherData()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '分配失败')
  }
}
const openBatchAssignDialog = () => {
  batchAssignForm.value = { groupId: null, major: null, classIds: [], teacherByClassId: {} }
  batchAssignDialogVisible.value = true
}
const submitBatchAssign = async () => {
  if (!batchAssignForm.value.groupId) return ElMessage.warning('请选择课程')
  if (!selectedBatchCourseGroup.value?.leaderId) return ElMessage.warning('该课程尚未设置组长，请先在课程组管理中指定')
  if (!String(batchAssignForm.value.major || '').trim()) return ElMessage.warning('请选择专业')
  if (!batchAssignForm.value.classIds || batchAssignForm.value.classIds.length === 0) return ElMessage.warning('请选择目标班级')

  const assignments = (batchAssignForm.value.classIds || []).map((classId) => ({
    classId,
    teacherId: batchAssignForm.value.teacherByClassId?.[classId]
  }))
  const missing = assignments.filter((a) => !a.teacherId)
  if (missing.length > 0) {
    return ElMessage.warning('请为每个班级选择授课老师')
  }

  try {
    await request.post('/admin/course/batch-assign', {
      groupId: batchAssignForm.value.groupId,
      major: String(batchAssignForm.value.major || '').trim(),
      assignments
    })
    ElMessage.success('批量分配成功')
    batchAssignDialogVisible.value = false
    fetchCourseAndTeacherData()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '批量分配失败')
  }
}
const openAssignDialog = (row) => {
  currentRow.value = row
  selectedTeacherId.value = row.teacherId || null
  if (!selectedTeacherId.value && row.teacher) {
    const matched = (teacherList.value || []).find((t) => t.realName === row.teacher)
    selectedTeacherId.value = matched?.userId || null
  }
  assignDialogVisible.value = true
}
const submitAssign = async () => {
  if (!currentRow.value?.id) return
  if (!selectedTeacherId.value) return ElMessage.warning('请选择教师')
  try {
    await request.post('/admin/course/update', {
      id: currentRow.value.id,
      teacherId: selectedTeacherId.value
    })
    ElMessage.success('更新成功')
    assignDialogVisible.value=false
    fetchCourseAndTeacherData()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '更新失败')
  }
}
const handleCourseDelete = async (id) => {
  await request.post(`/admin/course/delete/${id}`)
  fetchCourseAndTeacherData()
}

const openCourseGroupDialog = () => {
  courseGroupDialogVisible.value = true
}

const createCourseGroup = async () => {
  if (!courseGroupCreateForm.value.name) return ElMessage.warning('请填写课程名称')
  if (!courseGroupCreateForm.value.semester) return ElMessage.warning('请选择学期')
  try {
    await request.post('/admin/course-group/create', {
      name: courseGroupCreateForm.value.name,
      semester: courseGroupCreateForm.value.semester,
      leaderId: courseGroupCreateForm.value.leaderId || null
    })
    ElMessage.success('创建成功')
    courseGroupCreateForm.value.name = ''
    courseGroupCreateForm.value.leaderId = null
    fetchCourseAndTeacherData()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '创建失败')
  }
}

const updateCourseGroupLeader = async (row) => {
  try {
    await request.post('/admin/course-group/update-leader', {
      groupId: row.groupId,
      leaderId: row.leaderId || null
    })
    ElMessage.success('更新成功')
    fetchCourseAndTeacherData()
  } catch (e) {
    ElMessage.error(e?.response?.data || e?.message || '更新失败')
    fetchCourseAndTeacherData()
  }
}

// 审核与通知
const handleReview = async (id, status) => {
  await request.post('/admin/applications/review', { id, status })
  handleMenuSelect('4')
}
const openNotifyDialog = () => {
  notifyForm.title = ''; notifyForm.content = ''; notifyForm.userIds = [];
  notifyUserOptions.value = [...userList.value];
  notifyDialogVisible.value = true
}
const searchUsersForNotify = async (query) => {
  const res = await request.get('/admin/user/list', { params: { keyword: query, pageNum: 1, pageSize: 20 } })
  notifyUserOptions.value = res.list || []
}
const submitNotification = async () => {
  if (!notifyForm.title || !notifyForm.content) return ElMessage.warning('请填写标题和内容');
  await request.post('/admin/notification/send', notifyForm)
  ElMessage.success('发送成功')
  notifyDialogVisible.value = false
}
const viewStats = async (row) => {
  currentStatsTitle.value = row.title;
  const [detail, summary] = await Promise.all([
    request.get(`/admin/notification/stats/${row.batchId}`),
    request.get(`/admin/notification/stats/summary/${row.batchId}`)
  ]);
  currentStatsList.value = detail || [];
  currentStatsSummary.value = summary || { total: 0, readCount: 0, replyCount: 0, needReply: false };
  statsDialogVisible.value = true;
}

const calcRate = (part, total) => {
  if (!total || total === 0) return 0;
  return ((Number(part || 0) * 100) / Number(total)).toFixed(1);
}

</script>

<style scoped>
/* 布局容器 */
.teacher-layout-container {
  display: flex;
  height: 100vh;
  background-color: #f5f7fa; /* 更柔和的背景色 */
}

/* 侧边栏 - 亮色主题 */
.sidebar-bright {
  background-color: #ffffff;
  border-right: 1px solid #e6e6e6; /* 轻微的边框 */
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05); /* 悬浮感阴影 */
  z-index: 10; /* 确保阴影在内容之上 */
  display: flex;
  flex-direction: column;
}

/* Logo 区域 */
.brand-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
}

/* 菜单项样式调整 */
.el-menu-bright {
  border-right: none;
  padding-top: 10px;
}

.el-menu-bright .el-menu-item {
  font-weight: 500;
  margin: 4px 8px;
  border-radius: 4px;
  height: 50px;
  line-height: 50px;
}

.el-menu-bright .el-menu-item.is-active {
  background-color: #ecf5ff !important; /* 激活项的浅蓝色背景 */
  font-weight: bold;
}

.el-menu-bright .el-icon {
  font-size: 18px;
  margin-right: 12px;
}

/* 主内容区域 - 亮色 */
.main-content-bright {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
  background-color: #f5f7fa;
}

/* 页面标题 */
.page-title {
  margin: 0;
  font-size: 24px;
  color: #303133;
  font-weight: 600;
}

.header-actions-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

/* 筛选卡片 - 亮色 */
.filter-card-bright {
  border: none;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05) !important;
}

.filter-controls {
  display: flex;
  align-items: center;
}

/* 表格容器与样式优化 */
.table-container-bright {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
/* Element Plus 表格头自定义类 */
:deep(.table-header-bright) {
  background-color: #fafafa !important;
  color: #606266;
  font-weight: 600;
}
.info-text {
  color: #606266;
  font-size: 13px;
}
.time-col {
  color: #909399;
  font-size: 13px;
}

.empty-tip {
  padding: 16px;
  text-align: center;
  color: #909399;
}

/* 分页 */
.pagination-container-bright {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  padding: 10px 0;
}

/* 批量操作卡片 */
.action-card {
  border: none;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
.card-header {
  font-weight: bold;
  font-size: 16px;
  color: #303133;
}

.stats-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  background: #f9fafc;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 12px;
}
.summary-item .label {
  display: block;
  color: #909399;
  font-size: 12px;
}
.summary-item .value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.summary-item .value.success { color: #67c23a; }
.summary-item .value.primary { color: #409eff; }
.upload-area {
  margin-top: 20px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 20px;
  text-align: center;
}
/* 调整上传组件样式 */
:deep(.upload-demo-bright .el-upload-dragger) {
  border-color: #d9d9d9;
  background-color: #fafafa;
}
:deep(.upload-demo-bright .el-upload-dragger:hover) {
  border-color: #409eff;
}
.upload-btn-full {
  width: 100%;
  margin-top: 15px;
  height: 40px;
  font-size: 15px;
}
.debug-status {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

/* 内容面板通用样式 */
.content-panel-bright {
  margin-top: 20px;
  border-radius: 8px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05) !important;
}
.panel-header-bright {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}
.panel-header-bright h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

/* 简单动画 */
.fade-in {
  animation: fadeIn 0.4s ease-in-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 弹窗表单样式微调 */
.dialog-form {
  padding-right: 20px;
}

/* 工具类 */
.mb-20 { margin-bottom: 20px; }

/* 申请内容卡片 */
.app-card {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-card-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.app-title {
  font-weight: 600;
  color: #303133;
}
.app-desc {
  color: #606266;
  font-size: 13px;
}
.app-meta .label {
  color: #909399;
  margin-right: 6px;
}
</style>
