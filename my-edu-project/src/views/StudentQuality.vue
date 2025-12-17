<template>
  <div class="quality-container">
    <StudentHeader />

    <div class="quality-content">
      <div class="page-header">
        <h2>素质拓展与校园生活</h2>
        <p>申请素质学分或提交请假申请</p>
      </div>

      <div class="main-content">
        <el-tabs v-model="activeTab" class="custom-tabs">
        <el-tab-pane label="素质学分申请" name="credit">
          <div class="form-card">
            <el-alert title="毕业要求：素质学分需满 2 分。参加一次比赛可申请 0.2 学分。" type="info" show-icon style="margin-bottom:20px;"/>

            <el-form label-width="100px" :model="creditForm">
              <el-form-item label="申请类型">
                <el-radio-group v-model="creditForm.subType">
                  <el-radio label="ACTIVITY">素质活动</el-radio>
                  <el-radio label="COMPETITION">学科比赛 (+0.2分)</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="活动/比赛名称">
                <el-input v-model="creditForm.title" placeholder="请输入参加的活动或比赛名称" />
              </el-form-item>

              <el-form-item label="描述/心得">
                <el-input v-model="creditForm.content" type="textarea" rows="4" placeholder="请简述活动内容或比赛成绩..." />
              </el-form-item>

              <el-form-item label="证明材料">
                <el-upload
                    action="#"
                    :auto-upload="false"
                    :on-change="(f) => creditForm.file = f.raw"
                    list-type="picture-card"
                    :limit="1"
                >
                  <el-icon><Plus /></el-icon>
                </el-upload>
                <div class="upload-tip">请上传现场照片或获奖证书（必须）</div>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" @click="submitCredit" :loading="loading">提交申请</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="请假申请" name="leave">
          <div class="form-card">
            <el-form label-width="100px" :model="leaveForm">
              <el-form-item label="请假类型">
                <el-select v-model="leaveForm.leaveType">
                  <el-option label="病假" value="SICK" />
                  <el-option label="事假" value="AFFAIR" />
                </el-select>
              </el-form-item>

              <el-form-item label="是否离校">
                <el-switch v-model="leaveForm.isLeavingSchool" active-text="离校" inactive-text="在校" />
              </el-form-item>

              <el-form-item label="请假原因">
                <el-input v-model="leaveForm.reason" type="textarea" rows="3" placeholder="请输入详细请假原因" />
              </el-form-item>

              <el-form-item label="联系方式">
                <el-input v-model="leaveForm.contact" placeholder="请输入紧急联系电话" />
              </el-form-item>

              <el-form-item>
                <el-button type="warning" @click="submitLeave" :loading="loading">提交请假条</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import StudentHeader from '@/components/StudentHeader.vue'

const activeTab = ref('credit')
const loading = ref(false)

const creditForm = reactive({
  subType: 'ACTIVITY',
  title: '',
  content: '',
  file: null
})

const leaveForm = reactive({
  leaveType: 'AFFAIR',
  isLeavingSchool: false,
  reason: '',
  contact: ''
})

const submitCredit = async () => {
  if(!creditForm.title || !creditForm.file) return ElMessage.warning('请补全信息并上传证明')

  loading.value = true
  const formData = new FormData()
  formData.append('type', creditForm.subType === 'COMPETITION' ? 'QUALITY_COMPETITION' : 'QUALITY_ACTIVITY')
  formData.append('title', creditForm.title)
  formData.append('content', creditForm.content)
  formData.append('file', creditForm.file)

  try {
    await request.post('/quality/apply/credit', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    ElMessage.success('申请已提交，请等待素质教师审核')
    // Reset form
    creditForm.title = ''
    creditForm.content = ''
    creditForm.file = null
  } catch(e) {
    ElMessage.error(e.response?.data || '提交失败')
  } finally {
    loading.value = false
  }
}

const submitLeave = async () => {
  if(!leaveForm.reason || !leaveForm.contact) return ElMessage.warning('请填写完整')

  loading.value = true
  try {
    await request.post('/quality/apply/leave', {
      type: 'LEAVE_APPLICATION',
      content: JSON.stringify({
        isLeaving: leaveForm.isLeavingSchool,
        contact: leaveForm.contact,
        leaveType: leaveForm.leaveType
      }),
      reason: leaveForm.reason
    })
    ElMessage.success('请假申请已提交')
    leaveForm.reason = ''
    leaveForm.contact = ''
  } catch(e) {
    ElMessage.error('提交失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.quality-container { min-height: 100vh; background-color: #F5F7FA; }
.quality-content { padding: 20px; max-width: 800px; margin: 0 auto; }
.page-header { text-align: center; margin-bottom: 30px; color: #333; }
.form-card { background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); }
.upload-tip { font-size: 12px; color: #999; margin-top: 5px; }
</style>
