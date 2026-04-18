"""
FastAPI server for receiving health data from the Aidelle Connect Android app.

Run with: uvicorn main:app --host 0.0.0.0 --port 8000 --reload
"""

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from typing import List, Optional
from datetime import datetime

from models import HealthDataBatch, HealthRecordResponse, SyncResponse, DataType
from database import init_db, insert_records, get_records, get_latest_by_type, get_record_count


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize the database on startup."""
    init_db()
    print("[OK] Database initialized")
    print("[READY] Aidelle Connect API is ready")
    print("[INFO] Aidelle Connect app can connect to http://<your-ip>:8000")
    yield


app = FastAPI(
    title="Aidelle Connect API",
    description="Receives and stores health data from the Aidelle Connect Android app",
    version="1.0.0",
    lifespan=lifespan,
)

# Allow connections from any origin (needed for local development)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root():
    """Health check endpoint."""
    record_count = get_record_count()
    return {
        "status": "online",
        "service": "Aidelle Connect API",
        "total_records": record_count,
        "timestamp": datetime.now().isoformat(),
    }


@app.post("/api/health-data", response_model=SyncResponse)
async def receive_health_data(batch: HealthDataBatch):
    """
    Receive a batch of health data records from the Android app.
    """
    if not batch.records:
        raise HTTPException(status_code=400, detail="No records provided")

    # Convert Pydantic models to dicts for database insertion
    records_to_insert = []
    for record in batch.records:
        records_to_insert.append({
            "data_type": record.data_type.value,
            "value": record.value,
            "unit": record.unit,
            "timestamp": record.timestamp.isoformat(),
            "end_timestamp": record.end_timestamp.isoformat() if record.end_timestamp else None,
            "metadata": record.metadata,
            "device_id": batch.device_id,
        })

    stored_count = insert_records(records_to_insert)

    print(f"[RECV] Received {len(batch.records)} records, stored {stored_count}")
    for record in batch.records:
        print(f"   - {record.data_type.value}: {record.value} {record.unit}")

    return SyncResponse(
        message="Health data synced successfully",
        records_received=len(batch.records),
        records_stored=stored_count,
    )


@app.get("/api/health-data", response_model=List[HealthRecordResponse])
async def get_health_data(
    data_type: Optional[DataType] = Query(None, description="Filter by data type"),
    start_time: Optional[str] = Query(None, description="Start time (ISO 8601)"),
    end_time: Optional[str] = Query(None, description="End time (ISO 8601)"),
    limit: int = Query(100, ge=1, le=1000, description="Max records to return"),
):
    """
    Retrieve stored health data records with optional filters.
    """
    records = get_records(
        data_type=data_type.value if data_type else None,
        start_time=start_time,
        end_time=end_time,
        limit=limit,
    )
    return records


@app.get("/api/health-data/latest")
async def get_latest_data():
    """
    Get the most recent reading for each data type.
    Useful for displaying a real-time dashboard.
    """
    records = get_latest_by_type()
    return {
        "latest_records": records,
        "timestamp": datetime.now().isoformat(),
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
