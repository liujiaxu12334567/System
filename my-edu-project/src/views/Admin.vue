<template>
  <div class="admin-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">系统管理后台</div>
      <el-menu default-active="1" class="el-menu-vertical">
        <el-menu-item index="1"><el-icon><User /></el-icon>用户管理</el-menu-item>
        <el-menu-item index="2"><el-icon><Reading /></el-icon>课程管理</el-menu-item>
        <el-menu-item index="3"><el-icon><DataBoard /></el-icon>数据统计</el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main-content">
      <div class="header-actions">
        <h2>用户管理</h2>
        <div class="actions">
          <el-input v-model="keyword" placeholder="搜索用户名/姓名" style="width: 200px; margin-right: 10px" @input="fetchUsers" />
          <el-button type="primary" @click="openDialog(null)">+ 新增用户</el-button>
        </div>
      </div>

      <el-table :data="userList" border stripe style="width: 100%">
        <el-table-column prop="username" label="账号/学号" width="150" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column label="角色" width="150">
          <template #default="scope">
            <el-tag :type="getRoleTag(scope.row.roleType)">
              {{ getRoleName(scope.row.roleType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" />
        <el-table-column label="操作" width="250">
          <template #default="scope">
            <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>

            <el-button
                v-if="scope.row.roleType === 3"
                size="small"
                type="success"
                @click="changeRole(scope.row, 2)"
            >
              升为组长
            </el-button>
            <el-button
                v-if="scope.row.roleType === 2"
                size="small"
                type="warning"
                @click="changeRole(scope.row, 3)"
            >
              降为教师
            </el-button>

            <el-popconfirm title="确定删除该用户吗？" @confirm="handleDelete(scope.row.userId)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialogVisible" :title="form.userId ? '编辑用户' : '新增用户'" width="500px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="账号">
            <el-input v-model="form.username" :disabled="!!form.userId" />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" placeholder="不填则不修改(新增默认123456)" show-password />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.roleType" placeholder="请选择角色">
              <el-option label="管理员" :value="1" />
              <el-option label="课程组长" :value="2" />
              <el-option label="普通教师" :value="3" />
              <el-option label="学生" :value="4" />
            </el-select>
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
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const BASE_URL = 'http://localhost:8080'
const userList = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const form = ref({})

// 获取用户列表
const fetchUsers = async () => {
  const res = await axios.get(`${BASE_URL}/api/admin/user/list`, {
    params: { keyword: keyword.value },
    headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
  })
  userList.value = res.data
}

// 打开弹窗
const openDialog = (row) => {
  if (row) {
    form.value = { ...row, password: '' } // 编辑模式
  } else {
    form.value = { roleType: 4 } // 新增模式，默认学生
  }
  dialogVisible.value = true
}

// 提交表单
const submitForm = async () => {
  const url = form.value.userId ? `${BASE_URL}/api/admin/user/update` : `${BASE_URL}/api/admin/user/add`
  try {
    await axios.post(url, form.value, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchUsers()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 快速修改角色 (任命/撤销组长)
const changeRole = async (row, newRole) => {
  try {
    await axios.post(`${BASE_URL}/api/admin/user/update`, {
      userId: row.userId,
      roleType: newRole
    }, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    ElMessage.success('身份变更成功')
    fetchUsers()
  } catch (error) {
    ElMessage.error('变更失败')
  }
}

// 删除
const handleDelete = async (id) => {
  try {
    await axios.post(`${BASE_URL}/api/admin/user/delete/${id}`, {}, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 辅助函数
const getRoleName = (type) => {
  const map = {1:'管理员', 2:'课程组长', 3:'普通教师', 4:'学生'}
  return map[type] || '未知'
}
const getRoleTag = (type) => {
  const map = {1:'danger', 2:'success', 3:'primary', 4:'info'}
  return map[type]
}

onMounted(fetchUsers)
</script>

<style scoped>
.admin-container { display: flex; height: 100vh; }
.sidebar { background-color: #304156; color: white; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: bold; background-color: #2b3649; }
.main-content { padding: 20px; background-color: #f0f2f5; }
.header-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; background: #fff; padding: 15px; border-radius: 4px; }
h2 { margin: 0; }
</style>