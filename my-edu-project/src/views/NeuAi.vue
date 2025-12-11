<template>
  <div class="neu-ai-container">
    <header class="neu-header">
      <div class="header-inner">
        <div class="left-section">
          <h1 class="logo" @click="$router.push('/home')">Neuedu</h1>
          <nav class="nav-links">
            <a @click="$router.push('/home')">返回首页</a>
            <a class="active">NEU AI</a>
          </nav>
        </div>

        <div class="right-section">
          <el-dropdown trigger="click">
            <div class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="username">{{ userInfo.realName || '同学' }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="ai-layout">
      <aside class="ai-sidebar">
        <div class="menu-title">AI 能力中心</div>
        <ul class="ai-menu">
          <li :class="{ active: currentTab === 'chat' }" @click="switchTab('chat')">
            <el-icon><ChatLineRound /></el-icon> 智能助教
          </li>
          <li :class="{ active: currentTab === 'write' }" @click="switchTab('write')">
            <el-icon><EditPen /></el-icon> 写作润色
          </li>
          <li :class="{ active: currentTab === 'code' }" @click="switchTab('code')">
            <el-icon><Monitor /></el-icon> 代码解释器
          </li>
          <li :class="{ active: currentTab === 'plan' }" @click="switchTab('plan')">
            <el-icon><Calendar /></el-icon> 学习规划
          </li>
        </ul>
      </aside>

      <main class="ai-main">
        <div v-if="currentTab === 'chat'" class="feature-panel chat-panel">
          <div class="chat-history" ref="chatBoxRef">
            <div v-if="chatMessages.length === 0" class="empty-state">
              <el-icon :size="60" color="#E0E0E0"><MagicStick /></el-icon>
              <p>你好！我是 NEU AI 智能助教，有什么可以帮你？</p>
            </div>
            <div v-for="(msg, idx) in chatMessages" :key="idx" class="message-row" :class="msg.role">
              <div class="avatar">
                <el-icon v-if="msg.role === 'assistant'"><Cpu /></el-icon>
                <el-icon v-else><User /></el-icon>
              </div>
              <div class="bubble">
                <div class="content" style="white-space: pre-wrap;">{{ msg.content }}</div>
              </div>
            </div>
            <div v-if="loading" class="message-row assistant">
              <div class="avatar"><el-icon><Cpu /></el-icon></div>
              <div class="bubble typing-indicator">AI 正在思考中...</div>
            </div>
          </div>
          <div class="input-area">
            <el-input
                v-model="inputQuery"
                placeholder="输入你的问题..."
                @keyup.enter="handleChatSend"
                :disabled="loading"
            >
              <template #append>
                <el-button :icon="Position" @click="handleChatSend" :loading="loading" />
              </template>
            </el-input>
          </div>
        </div>

        <div v-if="currentTab === 'write'" class="feature-panel">
          <div class="panel-header">
            <h2>学术写作润色</h2>
            <p>粘贴您的论文段落，AI 将优化词句与逻辑（长文本可能需要 1-2 分钟，请耐心等待）。</p>
          </div>
          <div class="split-view">
            <div class="left-pane">
              <el-input
                  v-model="writeInput"
                  type="textarea"
                  :rows="15"
                  placeholder="在此处粘贴原文..."
                  resize="none"
              />
              <el-button
                  type="primary"
                  style="margin-top: 15px; width: 100%"
                  @click="handleWritePolish"
                  :loading="loading"
              >
                {{ loading ? 'AI 正在深度润色中...' : '开始润色' }}
              </el-button>
            </div>
            <div class="right-pane">
              <div class="result-box" v-if="writeResult" style="white-space: pre-wrap;">{{ writeResult }}</div>
              <div class="empty-result" v-else>
                <el-icon v-if="loading" class="is-loading" :size="30"><Loading /></el-icon>
                <span v-else>润色结果将显示在这里</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentTab === 'code'" class="feature-panel">
          <div class="panel-header">
            <h2>代码解释器</h2>
            <p>粘贴代码，AI 为您查找 Bug 并解释原理。</p>
          </div>
          <div class="split-view">
            <div class="left-pane">
              <el-input
                  v-model="codeInput"
                  type="textarea"
                  :rows="15"
                  placeholder="// 在此处粘贴代码..."
                  font-family="monospace"
                  resize="none"
              />
              <el-button
                  type="primary"
                  style="margin-top: 15px; width: 100%"
                  @click="handleCodeExplain"
                  :loading="loading"
              >
                {{ loading ? '正在分析代码...' : '开始分析' }}
              </el-button>
            </div>
            <div class="right-pane">
              <div class="result-box" v-if="codeResult" style="white-space: pre-wrap; font-family: monospace;">{{ codeResult }}</div>
              <div class="empty-result" v-else>
                <el-icon v-if="loading" class="is-loading" :size="30"><Loading /></el-icon>
                <span v-else>分析结果将显示在这里</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentTab === 'plan'" class="feature-panel">
          <div class="panel-header">
            <h2>个性化学习规划</h2>
            <p>输入学习目标，生成专属路线图。</p>
          </div>
          <div class="plan-input-box">
            <el-input v-model="planGoal" placeholder="例如：我想在3个月内掌握 Java Spring Boot 开发" size="large">
              <template #append>
                <el-button type="primary" @click="handlePlanGenerate" :loading="loading">
                  {{ loading ? '生成中...' : '生成计划' }}
                </el-button>
              </template>
            </el-input>
          </div>
          <div class="plan-result" v-if="planResult">
            <div class="markdown-body" style="white-space: pre-wrap; padding: 20px; background: #f9f9f9; border-radius: 8px;">{{ planResult }}</div>
          </div>
          <div class="plan-result empty" v-else-if="loading">
            <el-icon class="is-loading" :size="40" color="#409EFF"><Loading /></el-icon>
            <p>正在为您规划学习路线，请稍候...</p>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue' // 引入 onMounted
import { useRouter } from 'vue-router'         // 引入 useRouter
import { ChatLineRound, EditPen, Monitor, Calendar, MagicStick, Position, Cpu, User, Loading, ArrowDown, SwitchButton } from '@element-plus/icons-vue' // 引入 ArrowDown, SwitchButton
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter() // 初始化 router
const userInfo = ref({})   // 用户信息

const currentTab = ref('chat')
const loading = ref(false)

// Chat Data
const inputQuery = ref('')
const chatMessages = ref([])
const chatBoxRef = ref(null)

// Write Data
const writeInput = ref('')
const writeResult = ref('')

// Code Data
const codeInput = ref('')
const codeResult = ref('')

// Plan Data
const planGoal = ref('')
const planResult = ref('')

// ★★★ 核心修改：挂载时读取用户信息 ★★★
onMounted(() => {
  const storedUser = localStorage.getItem('userInfo')
  if (storedUser) {
    try { userInfo.value = JSON.parse(storedUser) } catch(e) {}
  }
})

// ★★★ 核心修改：退出登录逻辑 ★★★
const logout = () => {
  localStorage.clear()
  router.push('/login')
}

const switchTab = (tab) => {
  if (loading.value) return ElMessage.warning('请等待当前任务完成')
  currentTab.value = tab
}

const REQUEST_CONFIG = { timeout: 300000 }

// 1. 聊天处理
const handleChatSend = async () => {
  if (!inputQuery.value.trim()) return
  const msg = inputQuery.value
  inputQuery.value = ''

  chatMessages.value.push({ role: 'user', content: msg })
  loading.value = true
  scrollToBottom()

  try {
    const history = chatMessages.value.slice(-6).map(m => ({ role: m.role, content: m.content }))
    const res = await request.post('/ai/chat', { message: msg, history }, REQUEST_CONFIG)

    // 模拟打字机效果
    const fullText = res
    chatMessages.value.push({ role: 'assistant', content: '' })
    const lastIdx = chatMessages.value.length - 1

    let i = 0
    const speed = fullText.length > 500 ? 5 : 20

    const timer = setInterval(() => {
      chatMessages.value[lastIdx].content += fullText.charAt(i)
      i++
      scrollToBottom()
      if (i >= fullText.length) {
        clearInterval(timer)
        loading.value = false
      }
    }, speed)

  } catch (e) {
    console.error(e)
    chatMessages.value.push({ role: 'assistant', content: 'AI 响应超时或服务异常，请稍后重试。' })
    loading.value = false
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  })
}

// 2. 写作处理
const handleWritePolish = async () => {
  if (!writeInput.value) return ElMessage.warning('请输入内容')
  loading.value = true
  writeResult.value = ''

  try {
    const res = await request.post('/ai/write', { content: writeInput.value }, REQUEST_CONFIG)
    writeResult.value = res
  } catch (e) {
    ElMessage.error('请求超时，请尝试缩短文本长度')
  } finally {
    loading.value = false
  }
}

// 3. 代码处理
const handleCodeExplain = async () => {
  if (!codeInput.value) return ElMessage.warning('请输入代码')
  loading.value = true
  codeResult.value = ''

  try {
    const res = await request.post('/ai/code', { code: codeInput.value }, REQUEST_CONFIG)
    codeResult.value = res
  } catch (e) {
    ElMessage.error('请求失败')
  } finally {
    loading.value = false
  }
}

// 4. 计划处理
const handlePlanGenerate = async () => {
  if (!planGoal.value) return ElMessage.warning('请输入目标')
  loading.value = true
  planResult.value = ''

  try {
    const res = await request.post('/ai/plan', { goal: planGoal.value }, REQUEST_CONFIG)
    planResult.value = res
  } catch (e) {
    ElMessage.error('请求失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.neu-ai-container { height: 100vh; display: flex; flex-direction: column; background-color: #f5f7fa; }
.neu-header {
  height: 60px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.05); z-index: 10; flex-shrink: 0;
  .header-inner {
    width: 1200px; margin: 0 auto; height: 100%; display: flex; justify-content: space-between; align-items: center;
    .left-section {
      display: flex; align-items: center;
      .logo { font-size: 24px; color: #0041AB; margin-right: 40px; font-weight: 900; cursor: pointer; }
      .nav-links a {
        text-decoration: none; color: #606266; margin-right: 32px; font-size: 15px; cursor: pointer;
        &.active { color: #245FE6; font-weight: 700; }
        &:hover { color: #245FE6; }
      }
    }

    /* 右侧用户信息样式 */
    .right-section {
      display: flex; align-items: center;
      :deep(.el-dropdown) { cursor: pointer; }
      .user-info {
        display: flex; align-items: center; gap: 10px;
        padding: 0 5px;
        border-radius: 4px;
        &:hover { background-color: #f0f2f5; }
        .username { font-size: 14px; color: #333; font-weight: 500;}
      }
    }
  }
}

.ai-layout { flex: 1; display: flex; width: 1200px; margin: 20px auto; gap: 20px; overflow: hidden; }

.ai-sidebar {
  width: 240px; background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.05);
  .menu-title { font-size: 14px; color: #909399; margin-bottom: 15px; padding-left: 10px; }
  .ai-menu {
    list-style: none; padding: 0; margin: 0;
    li {
      padding: 12px 15px; margin-bottom: 8px; border-radius: 8px; cursor: pointer; color: #333; display: flex; align-items: center; gap: 10px; font-size: 15px; transition: all 0.2s;
      &:hover { background-color: #f0f7ff; color: #409EFF; }
      &.active { background-color: #409EFF; color: #fff; box-shadow: 0 4px 10px rgba(64,158,255,0.3); }
    }
  }
}

.ai-main {
  flex: 1; background: #fff; border-radius: 12px; padding: 30px; box-shadow: 0 2px 12px rgba(0,0,0,0.05); display: flex; flex-direction: column; overflow: hidden;

  .feature-panel { height: 100%; display: flex; flex-direction: column; }
  .chat-panel { padding-bottom: 0; }

  .panel-header {
    text-align: center; margin-bottom: 30px; flex-shrink: 0;
    h2 { font-size: 24px; color: #333; margin-bottom: 10px; }
    p { color: #666; font-size: 14px; }
  }

  /* Chat Styles */
  .chat-history {
    flex: 1; overflow-y: auto; padding: 20px; background: #f9f9f9; border-radius: 8px; margin-bottom: 20px;
    .empty-state { height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; color: #999; gap: 15px; }
    .message-row {
      display: flex; gap: 15px; margin-bottom: 20px;
      &.user { flex-direction: row-reverse; .bubble { background: #409EFF; color: #fff; border-radius: 12px 12px 0 12px; } }
      &.assistant { .bubble { background: #fff; color: #333; border: 1px solid #eee; border-radius: 12px 12px 12px 0; } }
      .avatar { width: 36px; height: 36px; border-radius: 50%; background: #eee; display: flex; justify-content: center; align-items: center; color: #666; flex-shrink: 0; }
      .bubble { padding: 10px 15px; max-width: 80%; line-height: 1.6; font-size: 14px; }
      .typing-indicator { color: #999; font-style: italic; }
    }
  }

  /* Split View for Write & Code */
  .split-view {
    flex: 1; display: flex; gap: 20px; height: 0;
    .left-pane, .right-pane { flex: 1; display: flex; flex-direction: column; height: 100%; }
    .result-box { flex: 1; padding: 15px; background: #f9f9f9; border-radius: 4px; overflow-y: auto; border: 1px solid #eee; line-height: 1.6; font-size: 14px; }
    .empty-result {
      flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center;
      color: #ccc; border: 1px dashed #ddd; border-radius: 4px; gap: 10px;
    }
  }

  /* Plan Styles */
  .plan-input-box { max-width: 600px; margin: 0 auto 30px; }
  .plan-result {
    flex: 1; overflow-y: auto;
    &.empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 15px; color: #909399; }
  }
}
</style>