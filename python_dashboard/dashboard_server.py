#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import argparse
import csv
import json
import os
import random
import sys
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse


INDEX_HTML = r"""<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
    <title>数据分析大屏</title>
    <style>
      :root{
        --bg0:#060b19; --bg1:#0b1430; --card:#0f1b3a; --card2:#0c1633;
        --line:rgba(255,255,255,.08);
        --text:#eaf0ff; --muted:rgba(234,240,255,.68);
        --a:#3b82f6; --g:#22c55e; --y:#f59e0b; --r:#ef4444; --p:#a855f7;
        --shadow:0 10px 30px rgba(0,0,0,.35);
        --radius:14px;
      }
      *{box-sizing:border-box}
      body{
        margin:0;
        font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", Arial, "Noto Sans", "Helvetica Neue", sans-serif;
        background: radial-gradient(1200px 800px at 20% 15%, rgba(59,130,246,.22), transparent 60%),
                    radial-gradient(900px 700px at 75% 18%, rgba(168,85,247,.16), transparent 55%),
                    linear-gradient(180deg, var(--bg1), var(--bg0));
        color:var(--text);
        min-height:100vh;
      }
      .wrap{max-width:1320px;margin:0 auto;padding:18px 18px 26px}
      .topbar{
        display:flex;align-items:center;justify-content:space-between;gap:12px;
        padding:14px 16px;border:1px solid var(--line);border-radius:var(--radius);
        background: linear-gradient(180deg, rgba(15,27,58,.92), rgba(12,22,51,.92));
        box-shadow: var(--shadow);
        position: sticky; top: 10px; z-index: 5; backdrop-filter: blur(10px);
      }
      .brand{display:flex;align-items:center;gap:10px}
      .logo{
        width:38px;height:38px;border-radius:12px;
        background: radial-gradient(circle at 30% 30%, rgba(59,130,246,.9), rgba(34,197,94,.35));
        box-shadow: 0 10px 25px rgba(59,130,246,.25);
      }
      .title{font-weight:800;letter-spacing:.6px}
      .sub{font-size:12px;color:var(--muted);margin-top:2px}
      .right{display:flex;align-items:center;gap:10px;flex-wrap:wrap;justify-content:flex-end}
      .pill{
        display:flex;align-items:center;gap:8px;
        padding:8px 10px;border-radius:999px;
        border:1px solid var(--line); background: rgba(255,255,255,.03);
        font-size:12px;color:var(--muted);
      }
      .pill b{color:var(--text);font-weight:700}
      .grid{
        display:grid; gap:14px; margin-top:14px;
        grid-template-columns: 1.15fr .85fr;
      }
      .card{
        border:1px solid var(--line); border-radius:var(--radius);
        background: linear-gradient(180deg, rgba(15,27,58,.78), rgba(12,22,51,.78));
        box-shadow: var(--shadow);
        overflow:hidden;
      }
      .card-h{
        display:flex;align-items:center;justify-content:space-between;gap:10px;
        padding:12px 14px;border-bottom:1px solid var(--line);
      }
      .card-h .h{font-weight:800}
      .card-h .hint{font-size:12px;color:var(--muted)}
      .card-b{padding:12px 12px 14px}
      .kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
      .kpi{
        padding:12px 12px;border-radius:12px;border:1px solid var(--line);
        background: rgba(255,255,255,.03);
      }
      .kpi .k{font-size:12px;color:var(--muted)}
      .kpi .v{font-size:26px;font-weight:900;margin-top:6px;letter-spacing:.3px}
      .kpi .d{font-size:12px;margin-top:8px;color:var(--muted)}
      .kpi .dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px;vertical-align:middle}
      .row2{display:grid;grid-template-columns:1.25fr .75fr;gap:14px;margin-top:14px}
      .chart{height:320px}
      .chart-s{height:300px}
      .table{
        width:100%;border-collapse:collapse;font-size:13px;
      }
      .table th,.table td{padding:10px 10px;border-bottom:1px solid var(--line)}
      .table th{color:var(--muted);font-weight:700;text-align:left}
      .tag{
        display:inline-flex;align-items:center;gap:6px;
        padding:4px 8px;border-radius:999px;border:1px solid var(--line);
        background: rgba(255,255,255,.03); color: var(--muted); font-size:12px;
      }
      .tag .dot{width:8px;height:8px;border-radius:50%}
      .footer{margin-top:14px;color:var(--muted);font-size:12px;text-align:center}
      @media (max-width: 1100px){
        .grid{grid-template-columns:1fr}
        .kpis{grid-template-columns:repeat(2,1fr)}
        .row2{grid-template-columns:1fr}
      }
    </style>
    <script>
      // ECharts: 优先本地 /static/echarts.min.js (离线可用)，否则走 CDN
      (function(){
        function load(src, onload){
          var s=document.createElement('script');
          s.src=src; s.async=true; s.onload=onload; s.onerror=function(){ onload(false); };
          document.head.appendChild(s);
        }
        load('/static/echarts.min.js', function(ok){
          if(ok!==false && window.echarts) return;
          load('https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js', function(){});
        });
      })();
    </script>
  </head>
  <body>
    <div class="wrap">
      <div class="topbar">
        <div class="brand">
          <div class="logo"></div>
          <div>
            <div class="title">数据分析大屏</div>
            <div class="sub">Python 单文件服务 · 大屏样式可二次定制</div>
          </div>
        </div>
        <div class="right">
          <div class="pill">数据源：<b id="ds">示例数据</b></div>
          <div class="pill">刷新：<b id="ref">5s</b></div>
          <div class="pill">更新时间：<b id="ts">-</b></div>
        </div>
      </div>

      <div class="grid">
        <div class="card">
          <div class="card-h">
            <div class="h">关键指标</div>
            <div class="hint">可从 CSV/JSON 读取并替换为你的业务口径</div>
          </div>
          <div class="card-b">
            <div class="kpis" id="kpis"></div>
            <div class="row2">
              <div class="card" style="background:transparent; box-shadow:none; border:1px solid var(--line);">
                <div class="card-h"><div class="h">趋势（近 14 天）</div><div class="hint">综合指标</div></div>
                <div class="card-b"><div id="trend" class="chart"></div></div>
              </div>
              <div class="card" style="background:transparent; box-shadow:none; border:1px solid var(--line);">
                <div class="card-h"><div class="h">结构分布</div><div class="hint">在线/离线</div></div>
                <div class="card-b"><div id="pie" class="chart"></div></div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-h">
            <div class="h">Top 列表</div>
            <div class="hint">示例：学生活跃度</div>
          </div>
          <div class="card-b">
            <table class="table" id="topTable">
              <thead>
                <tr>
                  <th style="width:54px;">#</th>
                  <th>姓名</th>
                  <th>学号</th>
                  <th style="width:110px;text-align:right;">活跃度</th>
                </tr>
              </thead>
              <tbody></tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="footer">提示：若内网环境无法访问 CDN，请把 ECharts 放到 <code>python_dashboard/static/echarts.min.js</code></div>
    </div>

    <script>
      const REFRESH_MS = 5000;
      const elTs = document.getElementById('ts');
      const elDs = document.getElementById('ds');
      document.getElementById('ref').textContent = (REFRESH_MS/1000)+'s';

      function fmtTs(iso){
        if(!iso) return '-';
        try{
          const d = new Date(iso);
          const pad = (n)=>String(n).padStart(2,'0');
          return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
        }catch(e){ return iso; }
      }

      function kpiCard(k){
        const dotColor = k.color || '#3b82f6';
        const delta = k.deltaText || '';
        return `
          <div class="kpi">
            <div class="k">${k.label}</div>
            <div class="v">${k.value}</div>
            <div class="d"><span class="dot" style="background:${dotColor}"></span>${delta}</div>
          </div>
        `;
      }

      function renderTable(rows){
        const tbody = document.querySelector('#topTable tbody');
        tbody.innerHTML = (rows||[]).map((r, idx)=>`
          <tr>
            <td style="color:var(--muted)">${idx+1}</td>
            <td>${r.name || '-'}</td>
            <td style="color:var(--muted)">${r.username || '-'}</td>
            <td style="text-align:right;font-weight:800">${r.score ?? '-'}</td>
          </tr>
        `).join('');
      }

      function ensureCharts(){
        if(!window.echarts) return null;
        const trend = echarts.init(document.getElementById('trend'));
        const pie = echarts.init(document.getElementById('pie'));
        window.addEventListener('resize', ()=>{ trend.resize(); pie.resize(); });
        return { trend, pie };
      }

      function renderCharts(charts, data){
        if(!charts) return;
        const t = data.trend || { x:[], y:[] };
        charts.trend.setOption({
          backgroundColor: 'transparent',
          grid: { left: 42, right: 16, top: 28, bottom: 36 },
          xAxis: { type:'category', data: t.x, axisLine:{lineStyle:{color:'rgba(255,255,255,.18)'}}, axisLabel:{color:'rgba(234,240,255,.7)'} },
          yAxis: { type:'value', axisLine:{show:false}, splitLine:{lineStyle:{color:'rgba(255,255,255,.08)'}}, axisLabel:{color:'rgba(234,240,255,.7)'} },
          tooltip: { trigger:'axis' },
          series: [{
            name:'综合',
            type:'line',
            data: t.y,
            smooth:true,
            showSymbol:false,
            lineStyle:{width:3,color:'#3b82f6'},
            areaStyle:{color:'rgba(59,130,246,.18)'}
          }]
        });

        const p = data.online || { online:0, offline:0 };
        charts.pie.setOption({
          backgroundColor:'transparent',
          tooltip: { trigger:'item' },
          legend: { bottom: 6, textStyle:{color:'rgba(234,240,255,.7)'} },
          series: [{
            type:'pie',
            radius:['45%','70%'],
            avoidLabelOverlap:true,
            label:{ show:false },
            labelLine:{ show:false },
            data:[
              { value: p.online, name:'在线', itemStyle:{color:'#22c55e'} },
              { value: p.offline, name:'未在线', itemStyle:{color:'#ef4444'} }
            ]
          }]
        });
      }

      async function fetchJson(url){
        const res = await fetch(url, { cache:'no-store' });
        if(!res.ok) throw new Error('HTTP '+res.status);
        return await res.json();
      }

      async function refresh(charts){
        const data = await fetchJson('/api/dashboard');
        elTs.textContent = fmtTs(data.generatedAt);
        elDs.textContent = data.dataSource || '示例数据';
        document.getElementById('kpis').innerHTML = (data.kpis||[]).map(kpiCard).join('');
        renderTable(data.topList||[]);
        renderCharts(charts, data);
      }

      function waitEchartsReady(){
        const start = Date.now();
        return new Promise((resolve)=>{
          const tick = ()=>{
            if(window.echarts) return resolve(true);
            if(Date.now()-start > 5000) return resolve(false);
            setTimeout(tick, 120);
          };
          tick();
        });
      }

      (async ()=>{
        const ok = await waitEchartsReady();
        const charts = ok ? ensureCharts() : null;
        try{ await refresh(charts); }catch(e){ console.error(e); }
        setInterval(()=>{ refresh(charts).catch(console.error); }, REFRESH_MS);
      })();
    </script>
  </body>
</html>
"""


@dataclass(frozen=True)
class DailyMetric:
    day: date
    score: float
    online: int
    offline: int


def _read_csv_metrics(path: Path) -> list[DailyMetric]:
    metrics: list[DailyMetric] = []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            day = datetime.strptime(row["date"], "%Y-%m-%d").date()
            metrics.append(
                DailyMetric(
                    day=day,
                    score=float(row.get("score") or 0),
                    online=int(row.get("online") or 0),
                    offline=int(row.get("offline") or 0),
                )
            )
    metrics.sort(key=lambda m: m.day)
    return metrics


def _generate_sample(days: int = 14) -> list[DailyMetric]:
    today = date.today()
    base = random.uniform(65, 82)
    metrics: list[DailyMetric] = []
    for i in range(days):
        d = today - timedelta(days=days - 1 - i)
        drift = random.uniform(-2.4, 2.6)
        base = max(30, min(98, base + drift))
        online = int(max(0, random.gauss(42, 6)))
        offline = int(max(0, random.gauss(8, 3)))
        metrics.append(DailyMetric(day=d, score=round(base, 1), online=online, offline=offline))
    return metrics


def _top_list(seed: int = 12) -> list[dict[str, Any]]:
    random.seed(seed + int(datetime.now().strftime("%H")))
    names = ["张三", "李四", "王五", "赵六", "陈晨", "刘洋", "孙悦", "周敏", "吴磊", "郑楠", "蒋欣", "沈泽"]
    rows = []
    for i, name in enumerate(names[:10]):
        rows.append({"name": name, "username": f"2025{1000+i}", "score": int(random.uniform(60, 100))})
    rows.sort(key=lambda r: r["score"], reverse=True)
    return rows


def build_dashboard_payload(data_path: Path | None) -> dict[str, Any]:
    if data_path and data_path.exists():
        metrics = _read_csv_metrics(data_path)
        data_source = f"CSV: {data_path.name}"
    else:
        metrics = _generate_sample(14)
        data_source = "示例数据"

    last = metrics[-1] if metrics else DailyMetric(day=date.today(), score=0, online=0, offline=0)
    prev = metrics[-2] if len(metrics) >= 2 else last

    def delta(a: float, b: float) -> str:
        v = round(a - b, 1)
        sign = "+" if v >= 0 else ""
        return f"较昨日 {sign}{v}"

    online_total = last.online
    offline_total = last.offline
    total = max(1, online_total + offline_total)
    online_rate = round(online_total / total * 100, 1)

    return {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "dataSource": data_source,
        "kpis": [
            {"label": "综合活跃指数", "value": f"{last.score:.1f}", "deltaText": delta(last.score, prev.score), "color": "#3b82f6"},
            {"label": "在线人数", "value": str(online_total), "deltaText": f"占比 {online_rate:.1f}%", "color": "#22c55e"},
            {"label": "未在线人数", "value": str(offline_total), "deltaText": f"占比 {100 - online_rate:.1f}%", "color": "#ef4444"},
            {"label": "互动事件数", "value": str(int(last.score * 7)), "deltaText": "举手/抢答/答题/发言", "color": "#a855f7"},
        ],
        "trend": {
            "x": [m.day.strftime("%m-%d") for m in metrics],
            "y": [m.score for m in metrics],
        },
        "online": {"online": online_total, "offline": offline_total},
        "topList": _top_list(seed=int(last.day.strftime("%Y%m%d"))),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "PythonDashboard/1.0"

    def _send(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/":
            self._send(HTTPStatus.OK, "text/html; charset=utf-8", INDEX_HTML.encode("utf-8"))
            return

        if path == "/api/dashboard":
            data_path = getattr(self.server, "data_path", None)
            payload = build_dashboard_payload(data_path)
            self._send(HTTPStatus.OK, "application/json; charset=utf-8", json.dumps(payload, ensure_ascii=False).encode("utf-8"))
            return

        if path.startswith("/static/"):
            base = getattr(self.server, "static_dir", None)
            if base is None:
                self._send(HTTPStatus.NOT_FOUND, "text/plain; charset=utf-8", "not found".encode("utf-8"))
                return
            fp = (base / path.removeprefix("/static/")).resolve()
            if not str(fp).startswith(str(base.resolve())):
                self._send(HTTPStatus.FORBIDDEN, "text/plain; charset=utf-8", "forbidden".encode("utf-8"))
                return
            if not fp.exists() or not fp.is_file():
                self._send(HTTPStatus.NOT_FOUND, "text/plain; charset=utf-8", "not found".encode("utf-8"))
                return
            ctype = "application/octet-stream"
            if fp.suffix == ".js":
                ctype = "application/javascript; charset=utf-8"
            body = fp.read_bytes()
            self._send(HTTPStatus.OK, ctype, body)
            return

        self._send(HTTPStatus.NOT_FOUND, "text/plain; charset=utf-8", "not found".encode("utf-8"))

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("%s - - [%s] %s\n" % (self.address_string(), self.log_date_time_string(), fmt % args))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="数据分析大屏（纯 Python 标准库）")
    parser.add_argument("--host", default="127.0.0.1", help="监听地址，默认 127.0.0.1")
    parser.add_argument("--port", type=int, default=8008, help="监听端口，默认 8008")
    parser.add_argument("--data", default="", help="可选：CSV 数据文件路径（utf-8），列：date,score,online,offline")
    parser.add_argument("--check", action="store_true", help="仅打印 /api/dashboard JSON 并退出")
    args = parser.parse_args(argv)

    data_path = Path(args.data).resolve() if args.data else None
    static_dir = Path(__file__).resolve().parent / "static"

    if args.check:
        payload = build_dashboard_payload(data_path)
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.data_path = data_path
    server.static_dir = static_dir

    print(f"Dashboard: http://{args.host}:{args.port}/")
    print("API:       /api/dashboard")
    if data_path:
        print(f"Data:      {data_path}")
    else:
        print("Data:      sample")
    print(f"Static:    {static_dir}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

