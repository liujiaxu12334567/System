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

当前实现下（`TeacherServiceImpl.resetClassroom` 写入），`value` 的典型结构：
- `courseId`
- `generatedAt`
- `students`：班级列表（每个元素含 `classId/className/studentCount/activeCount/score/students`）
- 其中 `students[].students` 为学生明细（`hand/race/answer/total/lastTime/...`）

## 前端接入位置

教师在线课堂页：`my-edu-project/src/views/TeacherClassroom.vue`

- 顶部新增“分析结果”按钮
- 弹窗中调用：
  - `GET /api/analysis/result` 拉取最新结果
  - `GET /api/analysis/results` 拉取历史（默认 10 条）
- 将 `value.students` 渲染为班级表格，支持展开查看学生明细

