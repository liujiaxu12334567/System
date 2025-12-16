# 在线课堂“按课节(会话)”说明

## 目标

同一门课程（`courseId`）的在线课堂数据（聊天、在线测试/互动）按“本节课/本次会话”隔离：
- 学生/老师在课堂页面看到的仅是**本节课**的数据，而不是历史累积
- 老师“下课/结束课堂”时，会对本节课互动数据做分析并落库到 `analysis_result`，可在教师端查看
- 再次开课会进入新的会话

## 会话机制（后端）

使用 Redis Key 维护会话边界：
- `classroom:active:{courseId}`：课堂是否开启（学生是否允许进入）
- `classroom:session:start:{courseId}`：本节课开始时间（用于过滤聊天/问题/回答）

## 接口

### 教师端

- 开课：`POST /api/teacher/classroom/{courseId}/start`
  - 若已开课则保持当前会话，不会重置 `sessionStart`
- 下课：`POST /api/teacher/classroom/{courseId}/end`
  - 会触发 `resetClassroom`：清空本节聊天与在线测试数据，并写入分析结果到 `analysis_result`
- 状态：`GET /api/teacher/classroom/{courseId}/status`
  - 返回 `{ active, sessionStart }`

### 学生端

- 状态：`GET /api/student/classroom/status/{courseId}`
  - 返回 `{ active, sessionStart }`

## 在线测试三种下发模式

通过在线问题（`OnlineQuestion`）的 `mode` 实现：
- `broadcast`：全体学生可见/可回答
- `race`：抢答（学生端只允许点击“抢答”，后端也会校验 mode）
- `assign`：点名（仅被点名学生可见/可回答）

教师发布问题接口（课堂内）：

`POST /api/teacher/classroom/question`

示例 payload：
```json
{
  "courseId": 3004,
  "title": "本题点名回答",
  "content": "请你说明结论与理由",
  "correctAnswer": "（可选）这里填写正确答案，用于判题/统计",
  "mode": "assign",
  "assignStudentId": 10086
}
```

## 前端接入点

- 教师课堂页：`my-edu-project/src/views/TeacherClassroom.vue`
  - 进入页面自动调用开课接口
  - 提供“结束课堂”按钮（会清空本节数据并生成分析）
  - 点名模式支持输入 `assignStudentId`
- 学生课堂页：`my-edu-project/src/views/StudentClassroom.vue`
  - 进入页面先检查课堂是否开启（未开课则返回首页）
