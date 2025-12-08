<template>
  <div class="home-layout">
    <el-container style="height: 100vh;">

      <el-aside width="80px" class="sidebar">
        <div class="logo-area">
          <el-icon :size="40" color="#409EFF"><Reading /></el-icon>
          <span style="font-size: 12px; font-weight: bold; margin-top: 5px;">NEU AI</span>
        </div>

        <div class="nav-menu">
          <div class="nav-item">
            <el-icon :size="24"><ChatLineRound /></el-icon>
            <span>对话</span>
          </div>
          <div class="nav-item active">
            <el-icon :size="24"><Suitcase /></el-icon>
            <span>工具箱</span>
          </div>
          <div class="nav-item">
            <el-icon :size="24"><Cpu /></el-icon>
            <span>智能体</span>
          </div>
        </div>
      </el-aside>

      <el-main style="padding: 0; background-color: #F7F8FC;">

        <div class="top-banner">
          <h1>AI工具箱，为您打造高效课堂</h1>
          <p>运用多元工具，丰富教学形式，催生课堂新效能</p>
        </div>

        <div class="content-area">
          <h3>效率工具</h3>

          <div class="tool-grid">
            <div
                class="tool-card"
                v-for="(item, index) in tools"
                :key="index"
            >
              <div class="icon-box" :style="{ backgroundColor: item.bg }">
                <el-icon :size="24" :color:="item.color">
                  <component :is="item.icon" />
                </el-icon>
              </div>
              <div class="text-box">
                <h4>{{ item.title }}</h4>
                <p>{{ item.desc }}</p>
              </div>
            </div>
          </div>
        </div>

      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const tools = ref([
  { title: '代码纠错', desc: '扫描代码文本，AI 精准定位错误', icon: 'Edit', bg: '#FFEBEE', color: '#FF5252' },
  { title: '代码解读', desc: '拆解复杂代码，AI 梳理逻辑', icon: 'Document', bg: '#F3E5F5', color: '#9C27B0' },
  { title: '代码优化', desc: '定位性能瓶颈，AI 重构代码', icon: 'Cpu', bg: '#E8F5E9', color: '#4CAF50' },
  { title: '智能阅读', desc: 'AI 深度解析文档，提炼关键信息', icon: 'Reading', bg: '#FFF3E0', color: '#FF9800' },
  { title: '智能推荐', desc: '洞察学习偏好，智能推荐资源', icon: 'Star', bg: '#E3F2FD', color: '#2196F3' },
  { title: '智能写作', desc: '输入创作思路，AI 挥笔成章', icon: 'EditPen', bg: '#E0F7FA', color: '#00BCD4' },
  { title: '智能翻译', desc: '打破语言藩篱，AI 秒速翻译', icon: 'Connection', bg: '#E1F5FE', color: '#03A9F4' },
  { title: '试题陪练', desc: '定制练习计划，AI 陪练提升', icon: 'DataBoard', bg: '#FCE4EC', color: '#E91E63' },
])
</script>

<style scoped lang="scss">
.sidebar {
  background: #fff;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 20px;

  .logo-area {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 40px;
  }

  .nav-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 60px;
    height: 60px;
    margin-bottom: 20px;
    color: #909399;
    cursor: pointer;
    font-size: 12px;
    border-radius: 10px;

    &:hover {
      background-color: #f5f7fa;
    }

    &.active {
      color: #409EFF;
      background-color: #ecf5ff;
    }

    span {
      margin-top: 5px;
    }
  }
}

.top-banner {
  background: linear-gradient(90deg, #EBF5FF 0%, #F5F0FF 100%);
  padding: 40px;
  text-align: center;

  h1 {
    color: #409EFF;
    margin: 0 0 10px 0;
  }
  p {
    color: #606266;
    margin: 0;
  }
}

.content-area {
  padding: 30px;
  max-width: 1200px;
  margin: 0 auto;

  h3 {
    margin-bottom: 20px;
    font-size: 18px;
    color: #303133;
  }

  .tool-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr); /* 一行4个 */
    gap: 20px;
  }

  .tool-card {
    background: #fff;
    padding: 20px;
    border-radius: 8px;
    display: flex;
    align-items: flex-start;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 5px 15px rgba(0,0,0,0.1);
    }

    .icon-box {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 15px;
      flex-shrink: 0;
    }

    .text-box {
      h4 {
        margin: 0 0 8px 0;
        font-size: 15px;
        color: #333;
      }
      p {
        margin: 0;
        font-size: 12px;
        color: #999;
        line-height: 1.5;
      }
    }
  }
}
</style>