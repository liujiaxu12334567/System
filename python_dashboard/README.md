# Python 数据分析大屏（单文件）

一个“可直接跑起来”的数据分析大屏示例：后端只用 Python 标准库起一个 HTTP 服务，前端用 ECharts 渲染图表（默认走 CDN，可选离线）。

## 启动

```powershell
cd "d:/HuaweiMoveData/Users/38929/Desktop/System/System"
python "python_dashboard/dashboard_server.py"
```

打开：`http://127.0.0.1:8008/`

## 使用自己的数据

支持传入 CSV（UTF-8），列名固定：

- `date`：`YYYY-MM-DD`
- `score`：综合指标（数值）
- `online`：在线人数
- `offline`：未在线人数

示例：

```powershell
python "python_dashboard/dashboard_server.py" --data "python_dashboard/sample_metrics.csv"
```

## 离线使用 ECharts（可选）

如果你的环境不能访问 CDN，把 `echarts.min.js` 放到：

`python_dashboard/static/echarts.min.js`

