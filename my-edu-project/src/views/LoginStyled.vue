<template>
  <div class="login-page" :style="{ backgroundImage: `url(${loginBg})` }">
    <section class="login-panel" aria-label="登录面板">
      <header class="brand">
        <img class="brand-logo" :src="loginLogo" alt="logo" />
        <div class="brand-text">
          <div class="brand-name">{{ brandName }}</div>
          <div class="brand-sub">{{ brandSub }}</div>
        </div>
      </header>

      <div class="login-tabs" role="tablist" aria-label="登录类型">
        <button class="tab" :class="{ active: activeTab === 'teacher' }" @click="activeTab = 'teacher'" type="button">
          教师登录
        </button>
        <button class="tab" :class="{ active: activeTab === 'student' }" @click="activeTab = 'student'" type="button">
          学生登录
        </button>
      </div>

      <el-form class="login-form" :model="form" size="large" @keyup.enter="handleSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" class="pill-input" />
        </el-form-item>

        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" class="pill-input" />
        </el-form-item>

        <div class="options">
          <el-checkbox v-model="form.remember">记住密码</el-checkbox>
          <el-link type="primary" :underline="false" class="forget-link">忘记密码</el-link>
        </div>

        <el-button type="primary" class="login-btn" :loading="loading" @click="handleSubmit">
          登 录
        </el-button>
      </el-form>

      <footer class="quick-entry" aria-label="快捷入口">
        <div class="entry-item">
          <div class="entry-icon">教</div>
          <div class="entry-text">教师端</div>
        </div>
        <div class="entry-item">
          <div class="entry-icon">学</div>
          <div class="entry-text">学生端</div>
        </div>
        <div class="entry-item">
          <div class="entry-icon">屏</div>
          <div class="entry-text">投屏电脑端</div>
        </div>
      </footer>
    </section>

    <div class="copyright">
      Copyright © 2024 Neuedu 东软教育科技
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import loginBg from '@/assets/login-bg.svg'
import loginLogo from '@/assets/login-logo.svg'

const router = useRouter()
const loading = ref(false)
const activeTab = ref('teacher')

const brandName = '职教云'
const brandSub = 'ZHI JIAO YUN'

const form = reactive({
  username: '',
  password: '',
  remember: true
})

onMounted(() => {
  try {
    const cached = localStorage.getItem('remembered_username')
    if (cached) form.username = cached
  } catch (e) {}
})

const handleSubmit = async () => {
  if (!form.username || !form.password) {
    return ElMessage.warning('请输入完整的账号信息')
  }

  try {
    loading.value = true
    const res = await request.post('/auth/login', {
      username: form.username,
      password: form.password
    })

    const token = res.token
    if (!token) return ElMessage.warning('登录异常：未获取到令牌')

    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify(res))

    if (form.remember) localStorage.setItem('remembered_username', form.username)
    else localStorage.removeItem('remembered_username')

    ElMessage.success('登录成功')

    const role = String(res.role)
    if (role === '1') router.push('/admin')
    else if (role === '2') router.push('/leader')
    else if (role === '3') router.push('/teacher')
    else if (role === '5') router.push('/quality-teacher')
    else router.push('/home')
  } catch (error) {
    console.error('登录失败', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  position: relative;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding-left: 140px;
}

.login-panel {
  width: 420px;
  padding: 46px 48px 34px;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  box-shadow: 0 18px 60px rgba(10, 34, 110, 0.18);
  backdrop-filter: blur(6px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;

  .brand-logo {
    width: 46px;
    height: 46px;
  }

  .brand-text {
    line-height: 1.1;
  }

  .brand-name {
    font-size: 22px;
    font-weight: 700;
    color: #2f7bff;
    letter-spacing: 1px;
  }

  .brand-sub {
    margin-top: 6px;
    font-size: 12px;
    color: #7a8aa8;
    letter-spacing: 2.8px;
    text-transform: uppercase;
  }
}

.login-tabs {
  display: flex;
  gap: 18px;
  margin-bottom: 22px;

  .tab {
    border: none;
    background: transparent;
    font-size: 14px;
    color: #6b7a99;
    padding: 8px 4px 10px;
    cursor: pointer;
    position: relative;

    &.active {
      color: #2f7bff;
      font-weight: 700;

      &::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 3px;
        border-radius: 3px;
        background: linear-gradient(90deg, #2f7bff 0%, #22b6ff 100%);
      }
    }
  }
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 14px;
  }

  :deep(.pill-input .el-input__wrapper) {
    background: #eef3fb;
    border-radius: 999px;
    box-shadow: none;
    padding: 0 14px;
  }

  :deep(.pill-input .el-input__inner) {
    height: 46px;
    font-size: 14px;
  }

  :deep(.pill-input .el-input__prefix) {
    color: #97a6c3;
  }
}

.options {
  margin: 10px 2px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .forget-link {
    font-size: 12px;
    color: #2f7bff;
  }
}

.login-btn {
  width: 100%;
  height: 46px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 6px;
  border: none;
  background: linear-gradient(90deg, #2f7bff 0%, #22b6ff 100%);
  box-shadow: 0 12px 26px rgba(47, 123, 255, 0.28);

  &:hover {
    opacity: 0.95;
  }
}

.quick-entry {
  margin-top: 34px;
  display: flex;
  justify-content: space-between;
  gap: 18px;

  .entry-item {
    flex: 1;
    text-align: center;
    color: #5e6e8d;
    font-size: 12px;
    user-select: none;
  }

  .entry-icon {
    width: 44px;
    height: 44px;
    margin: 0 auto 10px;
    border-radius: 12px;
    background: rgba(47, 123, 255, 0.12);
    color: #2f7bff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 800;
    letter-spacing: 2px;
  }
}

.copyright {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16px;
  text-align: center;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.25);
  padding: 0 16px;
}

@media (max-width: 980px) {
  .login-page {
    padding-left: 0;
    justify-content: center;
    background-position: 70% center;
  }
}

@media (max-width: 520px) {
  .login-panel {
    width: calc(100% - 32px);
    padding: 36px 22px 26px;
    border-radius: 16px;
  }
  .quick-entry {
    gap: 10px;
  }
  .quick-entry .entry-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
  }
}
</style>

