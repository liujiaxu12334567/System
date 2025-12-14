"""
最小示例：Python 侧消费 analysis. Exchange -> analysis.queue，解析 action，写入 analysis_result 表。

依赖（示例）：
    pip install pika sqlalchemy pymysql

运行前配置环境变量：
    export RABBITMQ_URL=amqp://guest:guest@localhost:5672/
    export MYSQL_URL=mysql+pymysql://root:password@localhost:3306/System
"""

import json
import os
import sys
from datetime import datetime

import pika
from sqlalchemy.exc import IntegrityError
from sqlalchemy import Column, BigInteger, String, Text, DateTime, create_engine
from sqlalchemy.dialects.mysql import JSON as MySQLJSON
from sqlalchemy.orm import declarative_base, sessionmaker

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
MYSQL_URL = os.getenv("MYSQL_URL", "mysql+pymysql://root:password@localhost:3306/System")

Base = declarative_base()


class AnalysisResult(Base):
    __tablename__ = "analysis_result"
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    course_id = Column(BigInteger, nullable=False)
    metric = Column(String(64), nullable=False)
    value_json = Column(MySQLJSON, nullable=False)
    generated_at = Column(DateTime, default=datetime.utcnow)
    event_id = Column(String(64), unique=True)


def get_session():
    engine = create_engine(MYSQL_URL, pool_pre_ping=True, future=True)
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine, expire_on_commit=False)()


def handle_action(envelope: dict, session):
    action = envelope.get("action")
    payload = envelope.get("payload", {}) or {}

    # 简单示例：按 action 写入 value_json，生产环境应在此做真实的 Pandas/Numpy 计算
    course_id = payload.get("courseId")
    if not course_id:
        return

    result = AnalysisResult(
        course_id=course_id,
        metric=action,
        value_json=payload,
        generated_at=datetime.utcnow(),
        event_id=envelope.get("eventId"),
    )
    try:
        session.add(result)
        session.commit()
        print(f"[analysis] stored metric={action} courseId={course_id}")
    except IntegrityError as ie:
        session.rollback()
        print(f"[analysis] duplicate/constraint skip: {ie}", file=sys.stderr)
    except Exception as e:
        session.rollback()
        raise e


def main():
    # 打印连接串用于排查计划任务环境变量问题
    print(f"[analysis] RABBITMQ_URL={RABBITMQ_URL}")
    print(f"[analysis] MYSQL_URL={MYSQL_URL}")

    params = pika.URLParameters(RABBITMQ_URL)
    connection = pika.BlockingConnection(params)
    channel = connection.channel()
    channel.exchange_declare(exchange="analysis.exchange", exchange_type="topic", durable=True)
    channel.exchange_declare(exchange="analysis.exchange.dlx", exchange_type="topic", durable=True)

    queue_args = {"x-dead-letter-exchange": "analysis.exchange.dlx"}
    channel.queue_declare(queue="analysis.queue", durable=True, arguments=queue_args)
    channel.queue_declare(queue="analysis.queue.dlq", durable=True)

    channel.queue_bind(queue="analysis.queue", exchange="analysis.exchange", routing_key="analysis.#")
    channel.queue_bind(queue="analysis.queue.dlq", exchange="analysis.exchange.dlx", routing_key="#")

    session = get_session()

    def callback(ch, method, properties, body):
        try:
            try:
                body_str = body.decode("utf-8")
            except UnicodeDecodeError:
                body_str = body.decode("latin1", errors="ignore")
            envelope = json.loads(body_str)
            handle_action(envelope, session)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except json.JSONDecodeError as e:
            print(f"[analysis] invalid json: {e}; body={body[:200]!r}", file=sys.stderr)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        except Exception as e:
            print(f"[analysis] error: {e}", file=sys.stderr)
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    channel.basic_qos(prefetch_count=10)
    channel.basic_consume(queue="analysis.queue", on_message_callback=callback, auto_ack=False)
    print("[analysis] consumer started, waiting for messages...")
    channel.start_consuming()


if __name__ == "__main__":
    main()
