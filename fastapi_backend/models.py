"""
Pydantic models for health data validation and serialization.
"""

from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime
from enum import Enum


class DataType(str, Enum):
    """Supported health data types from Health Connect and device sensors."""
    HEART_RATE = "heart_rate"
    STEPS = "steps"
    OXYGEN_SATURATION = "oxygen_saturation"
    SLEEP = "sleep"
    BODY_TEMPERATURE = "body_temperature"
    ACCELEROMETER = "accelerometer"
    GYROSCOPE = "gyroscope"
    GPS = "gps"
    ACCIDENT_ALERT = "accident_alert"


class HealthRecord(BaseModel):
    """A single health data record from Health Connect."""
    data_type: DataType = Field(..., description="Type of health measurement")
    value: float = Field(..., description="Measured value")
    unit: str = Field(..., description="Unit of measurement (e.g., bpm, steps, %, °C)")
    timestamp: datetime = Field(..., description="When the measurement was taken")
    end_timestamp: Optional[datetime] = Field(
        None, description="End time for range-based records like sleep sessions"
    )
    metadata: Optional[dict] = Field(
        None, description="Additional metadata (e.g., sleep stages, source app)"
    )


class HealthDataBatch(BaseModel):
    """A batch of health records sent from the Android app."""
    device_id: Optional[str] = Field(None, description="Identifier for the source device")
    records: List[HealthRecord] = Field(..., description="List of health records")


class HealthRecordResponse(BaseModel):
    """Response model for stored health records."""
    id: int
    data_type: str
    value: float
    unit: str
    timestamp: datetime
    end_timestamp: Optional[datetime] = None
    metadata: Optional[dict] = None
    device_id: Optional[str] = None
    created_at: datetime


class SyncResponse(BaseModel):
    """Response after successfully syncing health data."""
    message: str
    records_received: int
    records_stored: int
