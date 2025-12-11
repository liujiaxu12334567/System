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
        <el-menu-item index="4"><el-icon><DocumentChecked /></el-icon>申请审核</el-menu-item>
        <el-menu-item index="5"><el-icon><Bell /></el-icon>通知管理与统计</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">

      <div v-if="activeMenu === '1'">

        <div class="header-actions-top">
          <h2>用户管理</h2>
          <div class="right-btns">
            <el-button type="warning" @click="openNotifyDialog" style="margin-right: 10px;">
              <el-icon style="margin-right: 4px"><Bell /></el-icon> 下发通知
            </el-button>
            <el-button type="primary" @click="openDialog(null)" class="add-button">+ 新增用户</el-button>
          </div>
        </div>

        <el-card shadow="never" class="filter-card">
          <div class="filter-controls">
            <el-select v-model="roleFilter" placeholder="按角色筛选" clearable @change="handleRoleChange" style="width: 150px; margin-right: 15px">
              <el-option label="全部用户" :value="null" />
              <el-option label="管理员" value="1" />
              <el-option label="课题组长" value="2" />
              <el-option label="普通教师" value="3" />
              <el-option label="学生" value="4" />
              <el-option label="素质教师" value="5" />
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
              <span v-else-if="scope.row.roleType === '2'">
                  负责课程: <el-tag size="small">{{ scope.row.teacherRank || '未分配' }}</el-tag>
              </span>
              <span v-else-if="scope.row.teachingClasses">
                  {{ scope.row.roleType === '5' ? '负责班级: ' : '执教班级: ' }} {{ scope.row.teachingClasses }}
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
              <el-input v-model="rangeForm.targetClassId" placeholder="请输入班级ID (例如: 202303)" type="number" style="width: 200px;" />
            </el-form-item>

            <el-form-item label="所属专业">
              <el-input v-model="rangeForm.major" placeholder="请输入专业名称 (例如: 计算机科学)" style="width: 200px;" />
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
                  :disabled="!uploadForm.startUsername || !uploadForm.targetClassId || !uploadForm.major"
              >
                提交导入
              </el-button>
            </el-form-item>
          </el-form>

          <el-upload
              class="upload-demo"
              ref="uploadRef"
              drag
              :action="uploadActionUrl"
              :show-file-list="true"
              :before-upload="beforeUploadCheck"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :on-progress="handleUploadProgress"
              :auto-upload="false"
              :data="{ targetClassId: uploadForm.targetClassId, startUsername: uploadForm.startUsername, major: uploadForm.major }"
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

      <div v-if="activeMenu === '4'" class="application-review-container">
        <h2>待审核的教师申请</h2>
        <el-alert v-if="applicationList.length === 0" title="当前没有待审核的申请记录" type="success" show-icon style="margin-bottom: 20px;" />

        <el-table :data="applicationList" border stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="teacherName" label="申请人" width="100" />
          <el-table-column prop="type" label="类型" width="120">
            <template #default="scope">
              <el-tag :type="getTypeTag(scope.row.type)">{{ formatType(scope.row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="申请内容" min-width="180" show-overflow-tooltip/>
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

      <div v-if="activeMenu === '5'">
        <div class="header-actions-top">
          <h2>通知下发与统计</h2>
          <el-button type="warning" @click="openNotifyDialog">
            <el-icon style="margin-right: 4px"><Promotion /></el-icon> 新建通知
          </el-button>
        </div>

        <el-alert title="您可以查看历史发送记录，并统计用户的填报/阅读情况。" type="info" show-icon style="margin-bottom: 20px;" />

        <el-table :data="notifyHistory" border stripe style="width: 100%">
          <el-table-column prop="title" label="通知标题" min-width="150" />
          <el-table-column prop="message" label="内容摘要" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createTime" label="发送时间" width="180">
            <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="scope">
              <el-tag v-if="scope.row.isActionRequired" type="danger" effect="dark">需填报</el-tag>
              <el-tag v-else type="info">普通通知</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="scope">
              <el-button size="small" type="primary" plain @click="viewStats(scope.row)">查看统计</el-button>
            </template>
          </el-table-column>
        </el-table>
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
              <el-option label="素质教师" :value="5" />
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

          <template v-if="form.roleType === 2">
            <el-form-item label="负责课程" prop="managerCourses">
              <el-select
                  v-model="form.managerCourses"
                  multiple
                  placeholder="请选择课题组长负责的课程"
                  style="width: 100%"
              >
                <el-option
                    v-for="c in courseList"
                    :key="c.id"
                    :label="c.name"
                    :value="c.name"
                />
              </el-select>
            </el-form-item>
          </template>

          <template v-if="form.roleType === 5">
            <el-form-item label="负责班级">
              <el-select
                  v-model="form.teachingClassesIds"
                  multiple
                  placeholder="请选择负责的班级 (可多选)"
                  style="width: 100%"
              >
                <el-option
                    v-for="c in classList"
                    :key="c.id"
                    :label="c.name + ' (ID: ' + c.id + ')'"
                    :value="c.id"
                />
              </el-select>
            </el-form-item>
          </template>

        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="notifyDialogVisible" title="下发系统通知" width="600px">
        <el-form :model="notifyForm" label-width="100px">
          <el-form-item label="通知标题">
            <el-input v-model="notifyForm.title" placeholder="请输入通知标题" />
          </el-form-item>
          <el-form-item label="通知内容">
            <el-input v-model="notifyForm.content" type="textarea" :rows="4" placeholder="请输入通知正文" />
          </el-form-item>

          <el-form-item label="发送对象">
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
                placeholder="输入姓名搜索用户 (默认加载当前页用户)"
                style="width: 100%"
            >
              <el-option
                  v-for="item in notifyUserOptions"
                  :key="item.userId"
                  :label="item.realName + ' (' + item.username + ')'"
                  :value="item.userId"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="回执要求">
            <el-checkbox v-model="notifyForm.needReply">要求用户填写信息/回复</el-checkbox>
            <div style="font-size:12px; color:#999; line-height:1.2; margin-top:5px;">
              勾选后，用户在查看通知时会看到输入框，您可以在“查看统计”中看到回复内容。
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="notifyDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitNotification">确认发送</el-button>
          </span>
        </template>
      </el-dialog>

      <el-dialog v-model="statsDialogVisible" title="通知统计详情" width="800px">
        <div style="margin-bottom:15px; font-weight:bold;">通知标题：{{ currentStatsTitle }}</div>
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
          <el-form-item label="所选班级">
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

    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, nextTick } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { User, Reading, DataBoard, Tickets, UploadFilled, DocumentChecked, Bell, Plus, Upload, Promotion } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const userList = ref([])
const keyword = ref('')
const roleFilter = ref(null)
const classFilter = ref(null)
const subjectFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
// 【修改】添加 teachingClassesIds 以支持多选
const form = ref({ managerCourses: [], teachingClassesIds: [] })
const activeMenu = ref('1')
const loading = reactive({ range: false, upload: false })
const rangeForm = reactive({ startUsername: '', endUsername: '', targetClassId: null, major: null })
const uploadForm = reactive({ targetClassId: null, startUsername: '', major: null })
const uploadActionUrl = '/api/admin/batch/upload'
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }
const uploadRef = ref(null)

// --- 课程管理状态 ---
const courseList = ref([]) // 所有课程列表
const teacherList = ref([]) // 教师/组长列表
const courseDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const batchAssignDialogVisible = ref(false)
const courseForm = ref({ name: '', semester: '2025-1', teacher: '', classId: null })
const currentRow = ref({})
const selectedTeacher = ref('')
const batchAssignForm = ref({ name: '', semester: '2025-1', teacherNames: [], classIds: [] })
const classList = ref([]);

// --- 申请审核状态 ---
const applicationList = ref([]);

// --- 通知相关状态 ---
const notifyDialogVisible = ref(false)
const statsDialogVisible = ref(false)
const notifyHistory = ref([])
const currentStatsList = ref([])
const currentStatsTitle = ref('')
const notifyUserOptions = ref([])
const notifyForm = reactive({
  title: '',
  content: '',
  targetType: 'SPECIFIC', // 默认类型
  userIds: [],
  needReply: false
})

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
    // 1. 获取课程列表 (Admin 权限)
    const resCourses = await request.get('/admin/course/list');
    courseList.value = resCourses || []; // 用于弹窗的课程列表

    // 2. 获取教师列表 (包含 Leader 和 Teacher)
    const resTeachers = await request.get('/leader/teacher/list');
    teacherList.value = resTeachers || [];

    // 3. 获取所有已创建的班级列表
    const resClasses = await request.get('/admin/classes');
    classList.value = Array.isArray(resClasses) ? resClasses : [];

  } catch (error) {
    console.error("加载课程、教师或班级数据失败", error);
  }
}

// 获取待审核的申请列表
const fetchPendingApplications = async () => {
  try {
    const res = await request.get('/admin/applications/pending');
    applicationList.value = res || [];
  } catch (e) {
    ElMessage.error('加载待审核申请失败');
    console.error(e);
  }
}

// 获取通知历史
const fetchNotifyHistory = async () => {
  try {
    const res = await request.get('/admin/notification/history');
    notifyHistory.value = res || [];
  } catch(e) {
    ElMessage.error('加载通知历史失败');
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
    fetchCourseAndTeacherData();
  } else if (index === '4') { // 切换到申请审核
    fetchPendingApplications();
  } else if (index === '5') { // 切换到通知管理
    fetchNotifyHistory();
  }
}

// --- 用户管理 CRUD ---

const openDialog = (row) => {
  if (row) {
    const isLeader = row.roleType === '2';
    // 【修改】如果是素质教师 (Role=5)，解析 teachingClasses 字段
    const isQualityTeacher = row.roleType === '5';

    form.value = {
      userId: row.userId,
      username: row.username,
      realName: row.realName,
      roleType: Number(row.roleType),
      password: '',
      classId: row.classId,
      teachingClasses: row.teachingClasses,
      major: null,
      managerCourses: isLeader && row.teacherRank ? row.teacherRank.split(',') : [],
      // 【修改】解析素质教师的负责班级
      teachingClassesIds: isQualityTeacher && row.teachingClasses ?
          row.teachingClasses.split(',').map(id => Number(id)) : []
    }
  } else {
    // 新增用户
    form.value = {
      roleType: 4,
      classId: null,
      teachingClasses: null,
      major: null,
      username: '',
      realName: '',
      managerCourses: [],
      teachingClassesIds: [] // 新增初始化
    }
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const url = form.value.userId ? '/admin/user/update' : '/admin/user/add'

  if (!form.value.username || !form.value.realName) {
    return ElMessage.warning('请填写账号和真实姓名');
  }

  if (form.value.roleType === 4 && !form.value.userId) {
    if (!form.value.classId) return ElMessage.warning('新增学生必须填写班级ID');
    if (!form.value.major) return ElMessage.warning('新增学生必须填写专业名称');
  }

  const coursesToManage = form.value.managerCourses || [];

  // 【修改】处理素质教师的负责班级
  let teachingClassesStr = form.value.teachingClasses;
  if (form.value.roleType === 5) {
    teachingClassesStr = form.value.teachingClassesIds.join(',');
  } else if (form.value.roleType === 2) {
    teachingClassesStr = coursesToManage.join(',');
  }

  const payload = {
    ...form.value,
    roleType: String(form.value.roleType),
    // 关键：将课程名列表存储在 User.teacherRank 字段中
    teacherRank: form.value.roleType === 2 ? coursesToManage.join(',') : form.value.teacherRank,
    // 关键：将负责班级或课程列表放在 teachingClasses 字段中
    teachingClasses: teachingClassesStr,
  }

  // 清理临时字段
  delete payload.managerCourses;
  delete payload.teachingClassesIds;

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

// ... (其余部分代码保持不变：通知、课程、申请审核等) ...
const openNotifyDialog = () => {
  notifyForm.title = ''
  notifyForm.content = ''
  notifyForm.targetType = 'SPECIFIC'
  notifyForm.userIds = []
  notifyForm.needReply = false
  // 默认把当前页面表格里的用户加载进去，方便选择
  notifyUserOptions.value = [...userList.value]
  notifyDialogVisible.value = true
}

const searchUsersForNotify = async (query) => {
  const res = await request.get('/admin/user/list', {
    params: { keyword: query, pageNum: 1, pageSize: 20 }
  })
  notifyUserOptions.value = res.list || []
}

const submitNotification = async () => {
  if (!notifyForm.title || !notifyForm.content) return ElMessage.warning('请填写标题和内容');
  if (notifyForm.targetType === 'SPECIFIC' && notifyForm.userIds.length === 0) return ElMessage.warning('请选择用户');

  try {
    await request.post('/admin/notification/send', notifyForm)
    ElMessage.success('通知发送成功')
    notifyDialogVisible.value = false
    if(activeMenu.value === '5') fetchNotifyHistory()
  } catch (e) {
    ElMessage.error('发送失败')
  }
}

// 查看统计
const viewStats = async (row) => {
  currentStatsTitle.value = row.title;
  try {
    const res = await request.get(`/admin/notification/stats/${row.batchId}`);
    currentStatsList.value = res || [];
    statsDialogVisible.value = true;
  } catch(e) { ElMessage.error('加载统计失败'); }
}

// --- 课程管理逻辑 ---

const openCourseDialog = () => {
  courseForm.value = { name: '', semester: '2025-1', teacher: '', classId: null };
  courseDialogVisible.value = true;
}

const submitCourse = async () => {
  if(!courseForm.value.name) return ElMessage.warning('请填写课程名称');
  if(!courseForm.value.classId) return ElMessage.warning('请选择所属班级');

  const currentAdmin = JSON.parse(localStorage.getItem('userInfo'));

  try {
    const payload = {
      ...courseForm.value,
      managerName: currentAdmin.realName // 设置课题组长字段
    };

    await request.post('/admin/course/add', payload);
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

  const currentAdmin = JSON.parse(localStorage.getItem('userInfo'));

  try {
    await request.post('/admin/course/batch-assign', {
      name: form.name,
      semester: form.semester,
      teacherNames: form.teacherNames,
      classIds: form.classIds,
      managerName: currentAdmin.realName
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

  const payload = {
    id: currentRow.value.id,
    teacher: selectedTeacher.value,
    classId: currentRow.value.classId
  };

  try {
    await request.post('/admin/course/update', payload);

    ElMessage.success('教师分配成功，执教班级已同步更新。');
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
  if (!rangeForm.startUsername || !rangeForm.endUsername || !rangeForm.targetClassId || !rangeForm.major) {
    return ElMessage.warning('请填写完整的学号范围、目标班级ID和所属专业');
  }

  try {
    loading.range = true;
    const res = await request.post('/admin/batch/enroll', rangeForm);
    ElMessage.success(res);
  } catch (e) {
    ElMessage.error(e.response?.data || '批量创建失败');
  } finally {
    loading.range = false;
  }
};


const submitUpload = () => {
  if (!uploadForm.targetClassId || !uploadForm.startUsername || !uploadForm.major) {
    return ElMessage.warning('请确保班级ID、专业和起始学号都已填写！');
  }

  nextTick(() => {
    if (!uploadRef.value || !uploadRef.value.uploadFiles || uploadRef.value.uploadFiles.length === 0) {
      return ElMessage.warning('请先选择或拖拽文件！');
    }

    uploadRef.value.submit();
  });
};

const handleUploadSuccess = (response, file) => {
  loading.upload = false;
  uploadRef.value.clearFiles();
  ElMessage.success(response);
  fetchUsers();
};

const handleUploadError = (error) => {
  loading.upload = false;
  uploadRef.value.clearFiles();
  let message = '文件上传失败';
  if (error.response && error.response.data) {
    message = error.response.data;
  }
  ElMessage.error(message);
};

const handleUploadProgress = (event, file, fileList) => {
  loading.upload = true;
};

// --- 申请审核逻辑 ---

const handleReview = async (id, status) => {
  const action = status === 'APPROVED' ? '批准' : '驳回';
  try {
    await request.post('/admin/applications/review', { id, status });
    ElMessage.success(`操作成功：申请已${action}`);
    fetchPendingApplications(); // 刷新列表
  } catch (e) {
    ElMessage.error(`${action}失败：` + (e.response?.data || '服务器错误'));
    console.error(e);
  }
}

// --- 辅助函数 ---

const getRoleName = (type) => {
  const map = {'1':'管理员', '2':'课题组长', '3':'普通教师', '4':'学生', '5':'素质教师'}
  return map[String(type)] || '未知'
}
const getRoleTag = (type) => {
  const map = {'1':'danger', '2':'success', '3':'primary', '4':'info', '5':'warning'}
  return map[String(type)]
}
const formatType = (type) => {
  const map = { ADD: '新增学生', DELETE: '删除学生', RESET_PWD: '重置密码' }
  return map[type] || type
}
const getTypeTag = (type) => {
  const map = { ADD: 'success', DELETE: 'danger', RESET_PWD: 'warning' }
  return map[type] || 'info'
}
const formatDate = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : ''

onMounted(() => {
  fetchUsers();
  fetchCourseAndTeacherData();
})
</script>

<style scoped>
.admin-container { display: flex; height: 100vh; }
.sidebar { background-color: #304156; color: white; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #2b3649; }
.el-menu-vertical:not(.el-menu--collapse) { width: 200px; min-height: 400px; }
.main-content { padding: 20px; background-color: #f0f2f5; flex: 1; overflow-y: auto; }

.header-actions-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.header-actions-top h2 { margin: 0; }
.filter-card { margin-bottom: 20px; padding: 15px; display: flex; justify-content: flex-start; align-items: center; }
.filter-controls { display: flex; align-items: center; }
.pagination-container { margin-top: 20px; padding: 15px; background: #fff; border-radius: 4px; display: flex; justify-content: flex-end; }
.content-panel { margin: 0; padding: 20px; background: #fff; border-radius: 4px; flex: 1; }
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
h2 { margin: 0; }
.batch-enrollment-container h2 { margin-bottom: 20px; }
.el-upload__text em { color: #409eff; }
</style>