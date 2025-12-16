# Analysis 结果查询接口（后端）与页面接入（前端）

## 后端接口

### 1) 获取最新一条分析结果

`GET /api/analysis/result?courseId=3004&metric=classroom_online_performance`

返回：最新一条记录（无数据时返回 `null`）。

字段说明（核心字段）：
- `id`：记录 ID
- `courseId`：课程 ID
- `metric`：指标名（也就是分析动作名）
- `generatedAt`：生成时间
- `eventId`：事件 ID（可选）
- `valueJson`：原始 JSON 字符串
- `value`：`valueJson` 解析后的 JSON（前端优先用这个）

### 2) 获取最近 N 条分析结果（历史列表）

`GET /api/analysis/results?courseId=3004&metric=classroom_online_performance&limit=10`

返回：按 `generatedAt DESC, id DESC` 排序的列表。

## 指标示例：`classroom_online_performance`

当前实现下（`TeacherServiceImpl.resetClassroom` / `POST /api/teacher/classroom/{courseId}/analysis/generate` 写入），`value` 的典型结构：
- `courseId`
- `generatedAt`
- `students`：班级列表（每个元素含 `classId/className/studentCount/activeCount/score/students`）
- 其中 `students[].students` 为学生明细（`hand/race/answer/chat/called/correct/wrong/total/lastTime/...`）

## 前端接入位置

教师在线课堂页：`my-edu-project/src/views/TeacherClassroom.vue`

- 顶部新增“分析结果”按钮
- 弹窗中调用：
  - `POST /api/teacher/classroom/{courseId}/analysis/generate` 生成最新结果（避免上课中无结果）
  - `GET /api/analysis/result` 拉取最新结果
  - `GET /api/analysis/results` 拉取历史（默认 10 条）
- 将 `value.students` 渲染为班级表格，支持展开查看学生明细

## Python 侧指标（组长/管理员可用）

仓库内提供了 RabbitMQ 消费者：`docs/python_analysis_consumer.py`，会在收到 `analysis.*` 事件或定时扫描聊天表时，从 MySQL 聚合计算并写入 `analysis_result`。

推荐在组长页的 “教师数据分析” 输入框里使用以下 `metric`：
- `course_online_rate`：到课/在线率（来源：`attendance_summary`）
- `homework_submission_rate`：作业提交率（来源：`sys_quiz_record` + `sys_material`，更实时；可选兼容 `assignment_summary`）
- `interaction_score`：互动度评分（来源：`teacher_interaction` + `online_question/online_answer`）
- `chat_activity`：聊天活跃度（来源：`course_chat`）

运行示例（按需替换连接串）：
- Windows PowerShell：`$env:RABBITMQ_URL='amqp://guest:guest@localhost:5672/'; $env:MYSQL_URL='mysql+pymysql://root:password@localhost:3306/system?charset=utf8mb4'; python docs/python_analysis_consumer.py`
