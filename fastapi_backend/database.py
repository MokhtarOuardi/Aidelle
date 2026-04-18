"""
SQLite database layer for storing health records.
"""

import sqlite3
import json
from datetime import datetime
from typing import List, Optional
from pathlib import Path

DB_PATH = Path(__file__).parent / "health_data.db"


def get_connection() -> sqlite3.Connection:
    """Get a SQLite database connection with row factory enabled."""
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db():
    """Initialize the database schema."""
    conn = get_connection()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS health_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            data_type TEXT NOT NULL,
            value REAL NOT NULL,
            unit TEXT NOT NULL,
            timestamp TEXT NOT NULL,
            end_timestamp TEXT,
            metadata TEXT,
            device_id TEXT,
            created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_data_type ON health_records(data_type)
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_timestamp ON health_records(timestamp)
    """)
    conn.commit()
    conn.close()


def insert_records(records: List[dict]) -> int:
    """
    Insert multiple health records into the database.
    Returns the number of records inserted.
    """
    conn = get_connection()
    cursor = conn.cursor()
    count = 0

    for record in records:
        cursor.execute("""
            INSERT INTO health_records (data_type, value, unit, timestamp, end_timestamp, metadata, device_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            record["data_type"],
            record["value"],
            record["unit"],
            record["timestamp"],
            record.get("end_timestamp"),
            json.dumps(record.get("metadata")) if record.get("metadata") else None,
            record.get("device_id"),
        ))
        count += 1

    conn.commit()
    conn.close()
    return count


def get_records(
    data_type: Optional[str] = None,
    start_time: Optional[str] = None,
    end_time: Optional[str] = None,
    limit: int = 100,
) -> List[dict]:
    """
    Retrieve health records with optional filters.
    """
    conn = get_connection()
    query = "SELECT * FROM health_records WHERE 1=1"
    params = []

    if data_type:
        query += " AND data_type = ?"
        params.append(data_type)
    if start_time:
        query += " AND timestamp >= ?"
        params.append(start_time)
    if end_time:
        query += " AND timestamp <= ?"
        params.append(end_time)

    query += " ORDER BY timestamp DESC LIMIT ?"
    params.append(limit)

    cursor = conn.execute(query, params)
    rows = cursor.fetchall()
    conn.close()

    results = []
    for row in rows:
        record = dict(row)
        if record.get("metadata"):
            record["metadata"] = json.loads(record["metadata"])
        results.append(record)

    return results


def get_latest_by_type() -> List[dict]:
    """
    Get the most recent record for each data type.
    """
    conn = get_connection()
    cursor = conn.execute("""
        SELECT * FROM health_records
        WHERE id IN (
            SELECT id FROM health_records
            GROUP BY data_type
            HAVING MAX(timestamp)
        )
        ORDER BY data_type
    """)
    rows = cursor.fetchall()
    conn.close()

    results = []
    for row in rows:
        record = dict(row)
        if record.get("metadata"):
            record["metadata"] = json.loads(record["metadata"])
        results.append(record)

    return results


def get_record_count() -> int:
    """Get total number of stored records."""
    conn = get_connection()
    cursor = conn.execute("SELECT COUNT(*) FROM health_records")
    count = cursor.fetchone()[0]
    conn.close()
    return count
