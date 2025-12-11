<template>
  <div class="login-wrapper">
    <div class="login-left">
      <div class="left-content">
        <h2 class="brand-title">Smart Education</h2>
        <p class="brand-desc">开启智慧学习新时代</p>
      </div>
    </div>

    <div class="login-right">
      <div class="form-container">
        <div class="header">
          <p class="welcome" v-if="!isRegister">欢迎使用</p>
          <h2 class="title">{{ isRegister ? '注册新账号' : '东软智慧教育平台' }}</h2>
        </div>

        <el-form :model="form" size="large" @keyup.enter="handleSubmit">

          <el-form-item>
            <el-input
                v-model="form.username"
                placeholder="请输入学号/工号"
                :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item v-if="isRegister">
            <el-input
                v-model="form.realName"
                placeholder="请输入真实姓名"
                :prefix-icon="Postcard"
            />
          </el-form-item>

          <el-form-item>
            <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                show-password
                :prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item v-if="isRegister">
            <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="请再次确认密码"
                show-password
                :prefix-icon="Checked"
            />
          </el-form-item>

          <div class="options" v-if="!isRegister">
            <el-checkbox v-model="form.remember">记住我</el-checkbox>
            <el-link type="primary" :underline="false" style="font-size: 12px;">忘记密码?</el-link>
          </div>

          <el-button
              type="primary"
              class="action-btn"
              :loading="loading"
              @click="handleSubmit"
          >
            {{ isRegister ? '立即注册' : '登 录' }}
          </el-button>

          <div class="toggle-mode">
            <span>{{ isRegister ? '已有账号？' : '没有账号？' }}</span>
            <el-link type="primary" :underline="false" @click="toggleMode">
              {{ isRegister ? '去登录' : '立即注册' }}
            </el-link>
          </div>

        </el-form>

        <div class="footer-tips" v-if="!isRegister">
          <p>提示：推荐使用 Chrome 浏览器获得最佳体验</p>
        </div>
      </div>

      <div class="copyright">
        Copyright © 2024 Neuedu 东软教育科技
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Checked, Postcard } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const isRegister = ref(false)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  remember: true
})

const toggleMode = () => {
  isRegister.value = !isRegister.value
  form.password = ''
  form.confirmPassword = ''
  form.realName = ''
}

const handleSubmit = async () => {
  if (!form.username || !form.password) {
    return ElMessage.warning('请输入完整的账号信息')
  }

  // ============ 注册逻辑 ============
  if (isRegister.value) {
    if (form.password !== form.confirmPassword) {
      return ElMessage.error('两次输入的密码不一致')
    }
    if (!form.realName) {
      return ElMessage.warning('请输入真实姓名')
    }

    try {
      loading.value = true
      await request.post('/auth/register', {
        username: form.username,
        password: form.password,
        realName: form.realName,
        roleType: 4 // 注册默认还是学生，防止乱注册管理员
      })
      ElMessage.success('注册成功，请登录')
      toggleMode()
    } catch (error) {
      console.error('注册失败', error)
    } finally {
      loading.value = false
    }

  } else {
    // ============ 登录逻辑 ============
    try {
      loading.value = true
      const res = await request.post('/auth/login', {
        username: form.username,
        password: form.password
      })

      const token = res.token
      if (token) {
        localStorage.setItem('token', token)
        localStorage.setItem('userInfo', JSON.stringify(res))
        ElMessage.success('登录成功')

        // 【核心修改】精准的角色跳转逻辑
        // 1 = 管理员 -> /admin
        // 2 = 课程组长 -> /leader
        // 3 = 普通教师 -> /teacher (预留)
        // 4 = 学生 -> /home
        // 5 = 素质教师 -> /quality-teacher
        const role = String(res.role)

        if (role === '1') {
          router.push('/admin')
        } else if (role === '2') {
          router.push('/leader') // <--- 跳转到组长页面
        } else if (role === '3') {
          router.push('/teacher') // <--- 跳转到普通教师页面
        } else if (role === '5') {
          router.push('/quality-teacher') // <--- 跳转到素质教师页面
        } else {
          router.push('/home')
        }

      } else {
        ElMessage.warning('登录异常：未获取到令牌')
      }

    } catch (error) {
      console.error('登录失败', error)
    } finally {
      loading.value = false
    }
  }
}
</script>

<style scoped lang="scss">
.login-wrapper {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;

  .login-left {
    flex: 1;
    background: linear-gradient(135deg, #005BEA 0%, #00C6FB 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;

    .left-content {
      text-align: center;
      color: #fff;
      .brand-title {
        font-size: 48px;
        font-weight: bold;
        margin-bottom: 20px;
        text-shadow: 0 4px 8px rgba(0,0,0,0.1);
      }
      .brand-desc {
        font-size: 20px;
        opacity: 0.9;
        font-weight: 300;
        letter-spacing: 5px;
      }
    }
  }

  .login-right {
    flex: 1;
    background: #fff;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    position: relative;

    .form-container {
      width: 380px;
      padding: 40px;

      .header {
        margin-bottom: 40px;
        .welcome { font-size: 16px; color: #909399; margin-bottom: 8px; }
        .title { font-size: 30px; color: #303133; font-weight: 600; }
      }

      .options {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 24px;
      }

      .action-btn {
        width: 100%;
        height: 50px;
        font-size: 16px;
        border-radius: 8px;
        margin-bottom: 20px;
        background: linear-gradient(90deg, #409EFF 0%, #3a8ee6 100%);
        border: none;
        &:hover { opacity: 0.9; }
      }

      .toggle-mode {
        text-align: center;
        font-size: 14px;
        color: #606266;
        span { margin-right: 8px; }
      }

      .footer-tips {
        margin-top: 50px;
        font-size: 12px;
        color: #C0C4CC;
        text-align: center;
      }
    }

    .copyright {
      position: absolute;
      bottom: 20px;
      color: #DCDFE6;
      font-size: 12px;
    }
  }
}

@media (max-width: 768px) {
  .login-left { display: none !important; }
  .login-right { width: 100%; }
}
</style>