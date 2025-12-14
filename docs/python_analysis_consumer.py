"""
Python 数据分析消费者（RabbitMQ -> MySQL）

目标：
1) 消费 `analysis.exchange` 上的事件（routingKey=analysis.*）
2) 以 courseId 为粒度，从 MySQL 汇总/明细表聚合计算指标
3) 将结果写入 `analysis_result` 表（用于组长/管理员/教师页面展示）

依赖（示例）：
    pip install pika sqlalchemy pymysql

环境变量（建议显式配置）：
    RABBITMQ_URL=amqp://guest:guest@localhost:5672/
    MYSQL_URL=mysql+pymysql://root:password@localhost:3306/system?charset=utf8mb4

指标（metric）：
    - course_online_rate            来源：attendance_summary（可扩展 attendance_record）
    - homework_submission_rate      来源：sys_quiz_record + sys_material（更实时）；可选兼容 assignment_summary
    - interaction_score             来源：teacher_interaction + online_question/online_answer
    - chat_activity                 来源：course_chat（无事件时通过定时扫描补齐）
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime
from threading import Event, Thread
from time import sleep
from typing import Any, Dict, List, Optional, Tuple

import pika
from sqlalchemy import BigInteger, Column, DateTime, String, create_engine, text
from sqlalchemy.exc import IntegrityError, OperationalError, ProgrammingError
from sqlalchemy.dialects.mysql import JSON as MySQLJSON
from sqlalchemy.orm import declarative_base, sessionmaker

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
MYSQL_URL = os.getenv("MYSQL_URL", "mysql+pymysql://root:password@localhost:3306/system?charset=utf8mb4")

ANALYSIS_EXCHANGE = os.getenv("ANALYSIS_EXCHANGE", "analysis.exchange")
ANALYSIS_QUEUE = os.getenv("ANALYSIS_QUEUE", "analysis.queue")
ANALYSIS_DLX = os.getenv("ANALYSIS_DLX", "analysis.exchange.dlx")
ANALYSIS_DLQ = os.getenv("ANALYSIS_DLQ", "analysis.queue.dlq")

WINDOW_DAYS = int(os.getenv("ANALYSIS_WINDOW_DAYS", "7"))
CHAT_SCAN_INTERVAL_SECONDS = int(os.getenv("CHAT_SCAN_INTERVAL_SECONDS", "300"))
CHAT_SCAN_LOOKBACK_HOURS = int(os.getenv("CHAT_SCAN_LOOKBACK_HOURS", "24"))

Base = declarative_base()


class AnalysisResult(Base):
    __tablename__ = "analysis_result"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    course_id = Column(BigInteger, nullable=False)
    metric = Column(String(64), nullable=False)
    value_json = Column(MySQLJSON, nullable=False)
    generated_at = Column(DateTime, default=datetime.utcnow)
    event_id = Column(String(64), unique=True)


ENGINE = create_engine(MYSQL_URL, pool_pre_ping=True, pool_recycle=3600, future=True)
SessionLocal = sessionmaker(bind=ENGINE, expire_on_commit=False, future=True)


def safe_int(v: Any) -> Optional[int]:
    if v is None:
        return None
    try:
        return int(v)
    except Exception:
        return None


def init_db():
    Base.metadata.create_all(ENGINE)


def query_rows(sql: str, params: Dict[str, Any]) -> List[Dict[str, Any]]:
    with ENGINE.connect() as conn:
        rs = conn.execute(text(sql), params)
        return [dict(r) for r in rs.mappings().all()]


def query_one(sql: str, params: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    rows = query_rows(sql, params)
    return rows[0] if rows else None


def load_course_context(course_id: int) -> Dict[str, Any]:
    ctx = query_one(
        """
        SELECT
            c.id AS courseId,
            c.name AS courseName,
            c.semester AS semester,
            c.class_id AS classId,
            c.responsible_class_ids AS responsibleClassIds,
            c.teacher_id AS teacherId,
            c.group_id AS groupId,
            c.leader_id AS leaderId
        FROM sys_course c
        WHERE c.id = :courseId
        LIMIT 1
        """,
        {"courseId": course_id},
    )
    return ctx or {"courseId": course_id}


def parse_class_ids(value: Any) -> List[int]:
    if value is None:
        return []
    s = str(value).strip()
    if not s:
        return []
    parts: List[int] = []
    for seg in s.replace("，", ",").split(","):
        seg = seg.strip()
        if not seg:
            continue
        try:
            parts.append(int(seg))
        except Exception:
            continue
    return parts


def count_students_by_class_ids(class_ids: List[int]) -> int:
    ids = [int(x) for x in class_ids if x is not None]
    ids = sorted(set(ids))
    if not ids:
        return 0
    placeholders: List[str] = []
    params: Dict[str, Any] = {}
    for i, cid in enumerate(ids):
        key = f"c{i}"
        placeholders.append(f":{key}")
        params[key] = cid
    sql = f"""
        SELECT COUNT(*) AS cnt
        FROM sys_user
        WHERE role_type = '4'
          AND class_id IN ({",".join(placeholders)})
    """
    row = query_one(sql, params)
    return int((row or {}).get("cnt") or 0)


def fetch_last_n_days_series(table: str, course_id: int, date_col: str, cols: List[Tuple[str, str]]) -> List[Dict[str, Any]]:
    select_expr = ", ".join([f"{expr} AS `{alias}`" for expr, alias in cols])
    sql = f"""
        SELECT {date_col} AS `date`, {select_expr}
        FROM {table}
        WHERE course_id = :courseId
        GROUP BY {date_col}
        ORDER BY {date_col} DESC
        LIMIT :limit
    """
    rows = query_rows(sql, {"courseId": course_id, "limit": WINDOW_DAYS})
    rows.reverse()
    return rows


def compute_course_online_rate(course_id: int) -> Dict[str, Any]:
    series = fetch_last_n_days_series(
        "attendance_summary",
        course_id,
        "date",
        [("SUM(expected)", "expected"), ("SUM(present)", "present")],
    )

    def with_rate(r: Dict[str, Any]) -> Dict[str, Any]:
        expected = int(r.get("expected") or 0)
        present = int(r.get("present") or 0)
        rate = float(present) / expected if expected > 0 else 0.0
        return {**r, "rate": round(rate, 4)}

    series = [with_rate(r) for r in series]
    latest = series[-1] if series else None
    return {"metric": "course_online_rate", "courseId": course_id, "latest": latest, "lastDays": series}


def compute_homework_submission_rate(course_id: int) -> Dict[str, Any]:
    # 优先用真实提交明细（sys_quiz_record + sys_material），保证“学生提交作业/测验”能立刻体现在指标上
    # total(分母) 使用课程绑定班级下的学生数（role_type='4'）
    course_ctx = load_course_context(course_id)
    class_ids: List[int] = []
    class_id = safe_int(course_ctx.get("classId"))
    if class_id:
        class_ids.append(int(class_id))
    class_ids.extend(parse_class_ids(course_ctx.get("responsibleClassIds")))
    class_ids = sorted(set([c for c in class_ids if c is not None]))

    expected_students = count_students_by_class_ids(class_ids)

    series = query_rows(
        """
        SELECT DATE(q.submit_time) AS `date`,
               COUNT(DISTINCT q.user_id) AS `submitted`
        FROM sys_quiz_record q
        INNER JOIN sys_material m ON q.material_id = m.id
        WHERE m.course_id = :courseId
        GROUP BY DATE(q.submit_time)
        ORDER BY DATE(q.submit_time) DESC
        LIMIT :limit
        """,
        {"courseId": course_id, "limit": WINDOW_DAYS},
    )
    series.reverse()

    def with_rate(r: Dict[str, Any]) -> Dict[str, Any]:
        submitted = int(r.get("submitted") or 0)
        total = int(expected_students or 0)
        rate = float(submitted) / total if total > 0 else 0.0
        return {**r, "total": total, "rate": round(rate, 4)}

    series = [with_rate(r) for r in series]
    latest = series[-1] if series else None
    return {
        "metric": "homework_submission_rate",
        "courseId": course_id,
        "classIds": class_ids,
        "expectedStudents": expected_students,
        "latest": latest,
        "lastDays": series,
    }


def compute_interaction_score(course_id: int) -> Dict[str, Any]:
    interaction_series = fetch_last_n_days_series(
        "teacher_interaction",
        course_id,
        "date",
        [
            ("SUM(talk_time_seconds)", "talkTimeSeconds"),
            ("SUM(student_talk_seconds)", "studentTalkSeconds"),
            ("SUM(group_talk_seconds)", "groupTalkSeconds"),
            ("SUM(interaction_count)", "interactionCount"),
        ],
    )

    question_series = query_rows(
        """
        SELECT DATE(create_time) AS `date`, COUNT(*) AS `questionCount`
        FROM online_question
        WHERE course_id = :courseId
        GROUP BY DATE(create_time)
        ORDER BY DATE(create_time) DESC
        LIMIT :limit
        """,
        {"courseId": course_id, "limit": WINDOW_DAYS},
    )
    question_series.reverse()

    answer_series = query_rows(
        """
        SELECT DATE(a.create_time) AS `date`,
               COUNT(*) AS `answerCount`,
               COUNT(DISTINCT a.student_id) AS `answerStudents`
        FROM online_answer a
        INNER JOIN online_question q ON a.question_id = q.id
        WHERE q.course_id = :courseId
        GROUP BY DATE(a.create_time)
        ORDER BY DATE(a.create_time) DESC
        LIMIT :limit
        """,
        {"courseId": course_id, "limit": WINDOW_DAYS},
    )
    answer_series.reverse()

    def index_by_date(rows: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
        m: Dict[str, Dict[str, Any]] = {}
        for r in rows:
            d = str(r.get("date"))
            m[d] = r
        return m

    q_map = index_by_date(question_series)
    a_map = index_by_date(answer_series)

    enriched: List[Dict[str, Any]] = []
    for r in interaction_series:
        d = str(r.get("date"))
        talk_seconds = int(r.get("talkTimeSeconds") or 0)
        inter_count = int(r.get("interactionCount") or 0)
        q_cnt = int((q_map.get(d) or {}).get("questionCount") or 0)
        a_cnt = int((a_map.get(d) or {}).get("answerCount") or 0)

        talk_minutes = talk_seconds / 60.0
        score = min(100.0, inter_count * 2.0 + talk_minutes * 1.0 + q_cnt * 5.0 + a_cnt * 1.0)
        enriched.append({**r, "questionCount": q_cnt, "answerCount": a_cnt, "score": round(score, 2)})

    latest = enriched[-1] if enriched else None
    return {"metric": "interaction_score", "courseId": course_id, "latest": latest, "lastDays": enriched}


def compute_chat_activity(course_id: int) -> Dict[str, Any]:
    series = query_rows(
        """
        SELECT DATE(create_time) AS `date`,
               COUNT(*) AS `messageCount`,
               COUNT(DISTINCT sender_id) AS `activeSenders`,
               SUM(CASE WHEN role = 'student' THEN 1 ELSE 0 END) AS `studentMessageCount`
        FROM course_chat
        WHERE course_id = :courseId
        GROUP BY DATE(create_time)
        ORDER BY DATE(create_time) DESC
        LIMIT :limit
        """,
        {"courseId": course_id, "limit": WINDOW_DAYS},
    )
    series.reverse()

    enriched: List[Dict[str, Any]] = []
    for r in series:
        msg = int(r.get("messageCount") or 0)
        active = int(r.get("activeSenders") or 0)
        score = min(100.0, msg * 2.0 + active * 5.0)
        enriched.append({**r, "score": round(score, 2)})

    latest = enriched[-1] if enriched else None
    return {"metric": "chat_activity", "courseId": course_id, "latest": latest, "lastDays": enriched}


def store_metric(session, course_id: int, metric: str, value_json: Dict[str, Any], event_id: Optional[str]):
    now = datetime.utcnow()
    try:
        existing = None
        if event_id:
            existing = session.query(AnalysisResult).filter(AnalysisResult.event_id == event_id).one_or_none()
        if existing:
            existing.course_id = course_id
            existing.metric = metric
            existing.value_json = value_json
            existing.generated_at = now
        else:
            session.add(
                AnalysisResult(
                    course_id=course_id,
                    metric=metric,
                    value_json=value_json,
                    generated_at=now,
                    event_id=event_id,
                )
            )
        session.commit()
    except IntegrityError as ie:
        session.rollback()
        print(f"[analysis] duplicate/constraint skip: {ie}", file=sys.stderr)


def compute_and_store_all(course_id: int, base_event_id: Optional[str], trigger: str) -> List[Dict[str, Any]]:
    session = SessionLocal()
    try:
        ctx = load_course_context(course_id)

        def safe_compute(metric_name: str, fn):
            try:
                return fn()
            except (OperationalError, ProgrammingError) as e:
                return {"metric": metric_name, "courseId": course_id, "error": str(e)}

        metrics: List[Dict[str, Any]] = [
            safe_compute("course_online_rate", lambda: compute_course_online_rate(course_id)),
            safe_compute("homework_submission_rate", lambda: compute_homework_submission_rate(course_id)),
            safe_compute("interaction_score", lambda: compute_interaction_score(course_id)),
            safe_compute("chat_activity", lambda: compute_chat_activity(course_id)),
        ]

        for m in metrics:
            metric = m.get("metric") or "unknown"
            event_id = f"{base_event_id}:{metric}" if base_event_id else None
            value = {**ctx, **m, "trigger": trigger, "generatedAt": datetime.utcnow().isoformat()}
            store_metric(session, course_id, metric, value, event_id)
            print(f"[analysis] stored metric={metric} courseId={course_id} trigger={trigger}")
        return metrics
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def handle_action(envelope: Dict[str, Any]):
    action = envelope.get("action") or "unknown"
    payload = envelope.get("payload", {}) or {}

    course_id = safe_int(payload.get("courseId"))
    if not course_id:
        return

    base_event_id = envelope.get("eventId")
    metrics = compute_and_store_all(course_id, base_event_id, trigger=action)

    def find_metric(name: str) -> Optional[Dict[str, Any]]:
        for m in metrics:
            if m.get("metric") == name:
                return m
        return None

    # 事件发布早于汇总表写入时（例如 attendance.closed），做一次轻量重试以提高命中率
    if action == "analysis.attendance.closed":
        online = find_metric("course_online_rate") or {}
        if online.get("latest") is None and base_event_id:
            sleep(1.0)
            compute_and_store_all(course_id, base_event_id, trigger=f"{action}:retry")


def build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="RabbitMQ analysis consumer / MySQL aggregator")
    p.add_argument("--once", type=int, help="只对指定 courseId 计算一次并退出（不连接 RabbitMQ）")
    p.add_argument("--trigger", default="manual.once", help="写入 analysis_result 时的 trigger 字段（仅 --once 模式）")
    return p


def main():
    args = build_arg_parser().parse_args()
    init_db()

    if args.once:
        compute_and_store_all(int(args.once), base_event_id=None, trigger=str(args.trigger))
        return

    print(f"[analysis] RABBITMQ_URL={RABBITMQ_URL}")
    print(f"[analysis] MYSQL_URL={MYSQL_URL}")

    params = pika.URLParameters(RABBITMQ_URL)
    connection = pika.BlockingConnection(params)
    channel = connection.channel()
    channel.exchange_declare(exchange=ANALYSIS_EXCHANGE, exchange_type="topic", durable=True)
    channel.exchange_declare(exchange=ANALYSIS_DLX, exchange_type="topic", durable=True)

    queue_args = {"x-dead-letter-exchange": ANALYSIS_DLX}
    channel.queue_declare(queue=ANALYSIS_QUEUE, durable=True, arguments=queue_args)
    channel.queue_declare(queue=ANALYSIS_DLQ, durable=True)

    channel.queue_bind(queue=ANALYSIS_QUEUE, exchange=ANALYSIS_EXCHANGE, routing_key="analysis.#")
    channel.queue_bind(queue=ANALYSIS_DLQ, exchange=ANALYSIS_DLX, routing_key="#")

    stop_event = Event()

    def callback(ch, method, properties, body):
        try:
            try:
                body_str = body.decode("utf-8")
            except UnicodeDecodeError:
                body_str = body.decode("latin1", errors="ignore")
            envelope = json.loads(body_str)
            handle_action(envelope)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except json.JSONDecodeError as e:
            print(f"[analysis] invalid json: {e}; body={body[:200]!r}", file=sys.stderr)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        except Exception as e:
            print(f"[analysis] error: {e}", file=sys.stderr)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def chat_cron():
        if CHAT_SCAN_INTERVAL_SECONDS <= 0:
            return
        while not stop_event.is_set():
            try:
                course_rows = query_rows(
                    """
                    SELECT DISTINCT course_id AS courseId
                    FROM course_chat
                    WHERE create_time >= NOW() - INTERVAL :hours HOUR
                    ORDER BY course_id ASC
                    """,
                    {"hours": CHAT_SCAN_LOOKBACK_HOURS},
                )
                for r in course_rows:
                    cid = safe_int(r.get("courseId"))
                    if not cid:
                        continue
                    compute_and_store_all(cid, base_event_id=None, trigger="cron.chat_scan")
            except Exception as e:
                print(f"[analysis] chat_cron error: {e}", file=sys.stderr)
            sleep(CHAT_SCAN_INTERVAL_SECONDS)

    Thread(target=chat_cron, name="chat-cron", daemon=True).start()

    channel.basic_qos(prefetch_count=5)
    channel.basic_consume(queue=ANALYSIS_QUEUE, on_message_callback=callback, auto_ack=False)
    print("[analysis] consumer started, waiting for messages...")

    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        stop_event.set()
        print("[analysis] stopping...")
        try:
            channel.stop_consuming()
        except Exception:
            pass
        try:
            connection.close()
        except Exception:
            pass


if __name__ == "__main__":
    main()
