"""
MongoDB database layer for storing health records.
This is a drop-in replacement for database.py (SQLite).
"""

import os
import json
from datetime import datetime
from typing import List, Optional
from pymongo import MongoClient, ASCENDING, DESCENDING

# Retrieve MongoDB URI from environment or default to local installation
MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017/")
DB_NAME = "aidelle_db"
COLLECTION_NAME = "health_records"

def get_db():
    client = MongoClient(MONGODB_URI)
    return client[DB_NAME]

def get_collection():
    db = get_db()
    return db[COLLECTION_NAME]

def init_db():
    """Initialize the MongoDB schema (indexes)."""
    collection = get_collection()
    
    # Create indexes for frequently queried fields
    collection.create_index([("data_type", ASCENDING)])
    collection.create_index([("timestamp", DESCENDING)])

def insert_records(records: List[dict]) -> int:
    """
    Insert multiple health records into MongoDB.
    Returns the number of records inserted.
    """
    if not records:
        return 0

    collection = get_collection()
    # Prepare documents
    docs = []
    
    for record in records:
        # Convert metadata string to dict if needed, or leave as string
        metadata = record.get("metadata")
        if isinstance(metadata, str):
            try:
                metadata = json.loads(metadata)
            except Exception:
                pass
                
        doc = {
            "data_type": record["data_type"],
            "value": record["value"],
            "unit": record["unit"],
            "timestamp": record["timestamp"],
            "end_timestamp": record.get("end_timestamp"),
            "metadata": metadata,
            "device_id": record.get("device_id"),
            "created_at": datetime.utcnow().isoformat()
        }
        docs.append(doc)

    result = collection.insert_many(docs)
    return len(result.inserted_ids)

def get_records(
    data_type: Optional[str] = None,
    start_time: Optional[str] = None,
    end_time: Optional[str] = None,
    limit: int = 100,
) -> List[dict]:
    """
    Retrieve health records with optional filters.
    """
    collection = get_collection()
    query = {}

    if data_type:
        query["data_type"] = data_type
        
    if start_time or end_time:
        query["timestamp"] = {}
        if start_time:
            query["timestamp"]["$gte"] = start_time
        if end_time:
            query["timestamp"]["$lte"] = end_time

    cursor = collection.find(query).sort("timestamp", DESCENDING).limit(limit)
    
    results = []
    for doc in cursor:
        # PyMongo returns internal `_id`, map it to our generic string representation if needed
        # Or safely drop internal ObjectIDs
        doc["id"] = str(doc.pop("_id", ""))
        
        # Ensure metadata is string to match SQLite behaviour if needed by models
        if isinstance(doc.get("metadata"), dict):
            doc["metadata"] = json.dumps(doc["metadata"])
            
        results.append(doc)

    return results

def get_latest_by_type() -> List[dict]:
    """
    Get the most recent record for each data type using an aggregation pipeline.
    """
    collection = get_collection()
    
    pipeline = [
        # Sort sequentially
        {"$sort": {"timestamp": DESCENDING}},
        # Group by data_type and pick the first (most recent)
        {"$group": {
            "_id": "$data_type",
            "doc": {"$first": "$$ROOT"}
        }},
        {"$replaceRoot": {"newRoot": "$doc"}},
        {"$sort": {"data_type": ASCENDING}}
    ]
    
    cursor = collection.aggregate(pipeline)
    
    results = []
    for doc in cursor:
        doc["id"] = str(doc.pop("_id", ""))
        
        if isinstance(doc.get("metadata"), dict):
            doc["metadata"] = json.dumps(doc["metadata"])
            
        results.append(doc)
        
    return results

def get_record_count() -> int:
    """Get total number of stored records."""
    collection = get_collection()
    return collection.count_documents({})
