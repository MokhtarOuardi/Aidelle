# Aidelle : Your Personal AI Nurse

![Aidelle Banner](assets/banner.jpg)

**Aidelle** is a full-stack, AI-powered health monitoring ecosystem designed to care for elderly users. It combines an **Android (Jetpack Compose)** mobile client, a **Python FastAPI** data-sync backend, and a **LangGraph-powered AI Agent** backend into one cohesive platform. The mobile app reads biometrics from smartwatches via Android **Health Connect**, syncs readings to a local server, and the AI agent provides conversational medical assistance — including injury vision analysis, medication reminders, PubMed research, anomaly detection, and emergency contact alerting.

---

## Architecture Overview

```mermaid
graph TD
    subgraph "Wearable Layer"
        W["Smartwatch\n(Xiaomi · Samsung · Pixel)"]
    end

    subgraph "Android Device"
        HC["Android Health Connect"]
        APP["Aidelle Connect App\nKotlin · Jetpack Compose"]
    end

    subgraph "Backend Server"
        FAST["FastAPI Data Server\n:8000"]
        AGENT["AI Agent Server\n:8000"]
        DASH["Streamlit Dashboard\n:8501"]
        SQLite["SQLite DB"]
        Mongo["MongoDB"]
    end

    subgraph "AI Models"
        GEMINI["Gemini 3 Flash\n(ReAct Reasoning)"]
        QWEN["Qwen 3.5 0.8B\n(Local Vision)"]
    end

    subgraph "External Services"
        PUBMED["PubMed / NCBI\ne-Utilities API"]
    end

    W -->|Companion App| HC
    HC -->|Read Permissions| APP
    APP -->|HTTP POST /api/health-data| FAST
    FAST --> SQLite
    FAST --> Mongo
    FAST --> DASH
    AGENT --> GEMINI
    AGENT --> QWEN
    AGENT --> PUBMED
    AGENT --> Mongo
```

### System Components

| # | Component | Tech Stack | Purpose |
|---|-----------|------------|---------|
| 1 | **Aidelle Connect** (Android App) | Kotlin 2.0, Jetpack Compose, Health Connect 1.1, Retrofit 2, WorkManager | Reads wearable biometrics via Health Connect, displays a dashboard, and syncs data to the backend every 15 min |
| 2 | **FastAPI Data Backend** | Python, FastAPI, SQLite, MongoDB, Pydantic v2 | RESTful API that ingests, stores, and serves time-series health records |
| 3 | **AI Agent Backend** | Python, LangGraph (ReAct), Gemini 3 Flash, Qwen 3.5 0.8B (local), LangChain | Conversational medical assistant with tool-calling: vision injury analysis, PubMed search, medication reminders, sensor anomaly detection, emergency alerting |
| 4 | **Streamlit Dashboard** | Streamlit, Plotly, Pandas | Live health visualization with configurable alert thresholds and a Vital Stability Score |

---

## Supported Health Metrics

Aidelle supports modular smart health monitoring including:

| Metric | Unit | Icon | Health Connect Record |
|--------|------|------|----------------------|
| Heart Rate | `bpm` | ❤️ | `HeartRateRecord` |
| Steps | `steps` | 👣 | `StepsRecord` |
| Blood Oxygen / SpO2 | `%` | 🩸 | `OxygenSaturationRecord` |
| Sleep Duration | `minutes` | 🛏️ | `SleepSessionRecord` |
| Body Temperature | `°C` | 🌡️ | `BodyTemperatureRecord` |

---

## AI Agent Capabilities

The Agent Backend uses a **dual-LLM architecture**:

- **Gemini 3 Flash** (cloud, via Google AI API) — Primary reasoning brain; handles the ReAct loop and tool orchestration.
- **Qwen 3.5 0.8B** (local, via HuggingFace Transformers) — Dedicated vision model for analyzing injury images and videos on-device.

### Agent Toolkit

| Tool | Description |
|------|-------------|
| `search_medical_database` | Queries **PubMed** (NCBI e-Utilities) for peer-reviewed medical articles and summarizes results in plain language |
| `check_patient_reminders` | Checks a MongoDB/mock medication database for overdue doses based on scheduling rules |
| `call_emergency_contact` | Sends an emergency alert message to the configured caretaker email |
| `analyze_injury_image_file` | Uses Qwen Vision to analyze a photo of an injury and provide first-aid assessment |
| `analyze_injury_video_file` | Uses Qwen Vision to analyze a video of an injury and provide first-aid assessment |
| `get_and_analyze_sensor_data` | Fetches daily sensor arrays (HR, BP, SpO2, temperature) and detects anomalies against clinical thresholds |

### Agent API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Agent health check |
| `POST` | `/chat` | Send a natural-language message; the agent reasons, calls tools, and replies |
| `POST` | `/analyze-video` | Upload a video file for direct injury analysis |
| `POST` | `/analyze-image` | Upload an image file for direct injury analysis |

---

## Project Structure

```
Aidelle/
├── README.md
├── .gitignore
├── assets/
│   └── banner.jpg
│
├── Aidelle_Connect_app/                     # Android Mobile App
│   ├── build.gradle.kts                     # Root Gradle (AGP 8.7, Kotlin 2.0.21)
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts                 # App-level dependencies
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/aidelle/sensorread/
│               ├── MainActivity.kt          # Entry point, permission launcher
│               ├── data/
│               │   ├── HealthConnectManager.kt  # Health Connect SDK wrapper
│               │   ├── api/
│               │   │   ├── ApiService.kt        # Retrofit interface
│               │   │   └── RetrofitClient.kt    # Configurable HTTP client
│               │   └── model/
│               │       └── HealthData.kt        # DTOs matching FastAPI schemas
│               ├── viewmodel/
│               │   └── HealthViewModel.kt       # MVVM state + sync logic
│               ├── worker/
│               │   └── HealthSyncWorker.kt      # WorkManager background sync
│               └── ui/
│                   ├── screens/
│                   │   └── HomeScreen.kt        # Main dashboard UI
│                   ├── components/
│                   │   └── HealthDataCard.kt    # Card + summary composables
│                   └── theme/
│                       ├── Color.kt
│                       ├── Theme.kt
│                       └── Type.kt
│
├── fastapi_backend/                         # Data Sync Backend
│   ├── README.md
│   ├── requirements.txt
│   ├── main.py                              # FastAPI app, CORS, routes
│   ├── models.py                            # Pydantic schemas + DataType enum
│   ├── database.py                          # SQLite CRUD layer
│   ├── mongodb.py                           # MongoDB drop-in replacement
│   └── dashboard.py                         # Streamlit health dashboard
│
└── Agent_Backend/                           # AI Agent Backend
    ├── api.py                               # FastAPI app with /chat, /analyze-*
    ├── medical_agent.py                     # CLI-based interactive agent
    ├── gemini_model.py                      # Gemini 3 Flash LangChain wrapper
    ├── local_qwen.py                        # Qwen 3.5 0.8B local LLM wrapper
    └── tools.py                             # LangChain tools (6 tools)
```

---

## Project Setup & Installation

### 1. FastAPI Data Backend

```bash
cd fastapi_backend/
python -m venv venv

# Windows:
.\venv\Scripts\activate
# Unix/macOS:
source venv/bin/activate

pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

*API at `http://localhost:8000` — Swagger UI at `/docs`.*

### 2. Streamlit Dashboard

```bash
cd fastapi_backend/
streamlit run dashboard.py
```

*Opens automatically at `http://localhost:8501`.*

### 3. AI Agent Backend

```bash
cd Agent_Backend/

# Create a .env file with your API key:
echo GEMINI_API_KEY=your_key_here > .env

# Install dependencies (LangGraph, LangChain, Transformers, PyTorch, etc.)
pip install langchain langgraph langchain-google-genai transformers torch opencv-python pymongo python-dotenv qwen-vl-utils fastapi uvicorn

# Run as API server:
uvicorn api:app --host 0.0.0.0 --port 8001

# Or run as interactive CLI:
python medical_agent.py --model gemini
```

### 4. Android App (Aidelle Connect)

1. Open `Aidelle_Connect_app/` in **Android Studio** (Ladybug or later).
2. Sync Project with Gradle Files.
3. Build and run on a **Physical Device** or Emulator running **API 28+**.
4. Tap the ⚙️ settings icon to configure your server URL (e.g. `http://192.168.x.x:8000`).
5. Grant Health Connect permissions and tap **Sync Now**.

---

## Data Backend API Documentation

### Entity-Relationship (Database)

The SQLite database `health_data.db` stores the `health_records` table:

| Column Name | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | `PRIMARY KEY, AUTOINCREMENT` | Unique record ID |
| `data_type` | `TEXT` | `NOT NULL` | Enumerated string: `heart_rate`, `steps`, etc. |
| `value` | `REAL` | `NOT NULL` | The actual reading (e.g., 98.2) |
| `unit` | `TEXT` | `NOT NULL` | e.g. `bpm`, `%`, `steps` |
| `timestamp` | `TEXT` | `NOT NULL` | ISO 8601 start timestamp of the reading |
| `end_timestamp` | `TEXT` | `NULL` | ISO 8601 end time (for durational data like sleep) |
| `metadata` | `TEXT` | `NULL` | JSON-encoded string for extra flags |
| `device_id` | `TEXT` | `NULL` | Device manufacturer & model identity |
| `created_at` | `TEXT` | `NOT NULL` | Backend insertion timestamp |

> **MongoDB Support:** A drop-in `mongodb.py` module mirrors the SQLite interface. Configure via the `MONGODB_URI` environment variable (defaults to `mongodb://localhost:27017/`, database `aidelle_db`).

### API Endpoints

#### `GET /`
**Health Check Endpoint.**
**Response (`200 OK`)**:
```json
{
  "status": "online",
  "service": "Aidelle Connect API",
  "total_records": 105,
  "timestamp": "2026-04-18T10:00:00"
}
```

#### `POST /api/health-data`
**Batch upload endpoint for pushing records from mobile client to backend.**
**Payload**: `HealthDataBatch`
```json
{
  "device_id": "samsung SM-G991B",
  "records": [
    {
      "data_type": "heart_rate",
      "value": 75.0,
      "unit": "bpm",
      "timestamp": "2026-04-18T08:30:00Z"
    }
  ]
}
```

#### `GET /api/health-data`
**Query all synced health data with optional query filters.**
**Params:**
- `data_type` (Optional): Filter to specific biometric.
- `start_time`, `end_time` (Optional ISO 8601 strings)
- `limit` (Default: 100)

#### `GET /api/health-data/latest`
**Fetches the most recent entry for every unique `data_type`. Perfect for rendering dashboards.**

---

## Streamlit Dashboard Features

The Aidelle Tier 1 dashboard (`dashboard.py`) provides:

- **Real-Time Metric Cards** — Average values per biometric with outlier alerts
- **Time-Series Plots** — Interactive Plotly scatter/line charts per metric, with alert threshold lines
- **Configurable Alert Criteria** — Sidebar sliders for max heart rate, min SpO2, max temperature, step goals, and sleep targets
- **Time Range Filtering** — Last 24 hours, 7 days, 30 days, or all time
- **Vital Stability Score** — A 0–100 composite index penalizing outlier readings
- **Anomaly Log Table** — Chronological incident log of all threshold violations

---

## Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| Mobile | Kotlin 2.0, Jetpack Compose (Material 3), Health Connect 1.1-alpha10, Retrofit 2.11, WorkManager |
| Data Backend | Python, FastAPI 0.115, Pydantic 2.9, SQLite (WAL mode), PyMongo 4.6 |
| AI Agent | LangGraph (ReAct), LangChain, Gemini 3 Flash (Google AI), Qwen 3.5 0.8B (local HuggingFace), OpenCV |
| Dashboard | Streamlit 1.38, Plotly 5.23, Pandas 2.0 |
| External APIs | PubMed NCBI e-Utilities |

---

## Contact and Credits

Developed by **Mokhtar Ouardi**, **Adam Aburaya**, **Omar Abouelmagd** and **Anas Aburaya** for the myAI Hackathon.

- **Mokhtar Ouardi**: [GitHub](https://github.com/MokhtarOuardi) | [Email](mailto:m.ouardi@graduate.utm.my)
- **Anas Aburaya**: [GitHub](https://github.com/Shadowpasha) | [Email](mailto:ameranas1923@gmail.com)
- **Adam Aburaya**: [GitHub](https://github.com/amer-adam) | [Email](mailto:ameradam6a@gmail.com)
- **Omar Abouelmagd**: [GitHub](https://github.com/OmarAbouelmagd) | [Email](mailto:omarabolmagd@gmail.com)

---
© 2026 InfiniTea Team. All rights reserved.
