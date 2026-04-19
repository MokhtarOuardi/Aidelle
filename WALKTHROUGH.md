# Aidelle — Full Project Walkthrough

> **How to set up, run, and demo the entire Aidelle ecosystem from scratch.**
> This guide covers every server-side and web component. The Android app is excluded.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Clone the Repository](#2-clone-the-repository)
3. [Environment Variables](#3-environment-variables)
4. [Step 1 — FastAPI Data Backend](#4-step-1--fastapi-data-backend)
5. [Step 2 — Streamlit Dashboard](#5-step-2--streamlit-dashboard)
6. [Step 3 — AI Agent Backend](#6-step-3--ai-agent-backend)
7. [Step 4 — Web Frontend](#7-step-4--web-frontend)
8. [Running Everything Together](#8-running-everything-together)
9. [Testing the System](#9-testing-the-system)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites

Before starting, make sure the following are installed on your machine:

| Tool | Version | Purpose | Download |
|------|---------|---------|----------|
| **Python** | 3.10+ | Backend servers | [python.org](https://www.python.org/downloads/) |
| **Node.js** | 18+ (LTS recommended) | Web frontend | [nodejs.org](https://nodejs.org/) |
| **Git** | Any recent | Clone the repo | [git-scm.com](https://git-scm.com/) |
| **NVIDIA GPU + CUDA** | Optional | Local Qwen vision model | [CUDA Toolkit](https://developer.nvidia.com/cuda-downloads) |

> **Note:** The Qwen 3.5 0.8B vision model runs locally. A CUDA-capable NVIDIA GPU with ≥4GB VRAM is recommended for fast inference. CPU-only mode works but is significantly slower.

### API Keys You'll Need

| Key | Required For | Get It From |
|-----|-------------|-------------|
| **Gemini API Key** | AI Agent reasoning (Gemini 3 Flash) | [Google AI Studio](https://aistudio.google.com/apikey) |
| **Camb AI API Key** | Text-to-Speech for the 3D avatar | [Camb AI](https://www.camb.ai/) |

---

## 2. Clone the Repository

```bash
git clone https://github.com/MokhtarOuardi/Aidelle.git
cd Aidelle
```

After cloning, your directory structure should look like this:

```
Aidelle/
├── aidelle-frontend/       ← React web app
├── Agent_Backend/          ← AI agent server
├── fastapi_backend/        ← Data API + Streamlit dashboard
├── Aidelle_Connect_app/    ← Android app (not covered here)
├── .env                    ← Root environment variables
└── README.md
```

---

## 3. Environment Variables

You need to create **two** `.env` files:

### 3a. Root `.env` (for the Agent Backend)

Create a file at `Aidelle/.env`:

```env
GEMINI_API_KEY=your_gemini_api_key_here
```

The Agent Backend loads this via `python-dotenv` on startup.

### 3b. Frontend `.env` (for the React app)

Create a file at `Aidelle/aidelle-frontend/.env`:

```env
VITE_AGENT_API_URL=http://localhost:8000
VITE_DATA_API_URL=http://localhost:8001
VITE_CAMB_API_KEY=your_camb_ai_api_key_here
```

> **Port Note:** The Data Backend runs on port `8001` and the Agent Backend on port `8000` to avoid conflicts. Adjust if your setup differs.

---

## 4. Step 1 — FastAPI Data Backend

This is the server that stores and serves health data (heart rate, SpO2, steps, GPS, gyroscope, accident alerts, etc.). It uses **SQLite** as the default database, and the `health_data.db` file is included in the repo with sample data.

### Install Dependencies

Open a dedicated terminal (Terminal 1):

```bash
cd fastapi_backend

# Create and activate a virtual environment
python -m venv venv

# Windows:
.\venv\Scripts\activate

# macOS/Linux:
source venv/bin/activate

# Install packages
pip install -r requirements.txt
```

The `requirements.txt` installs:
- `fastapi==0.115.0` — Web framework
- `uvicorn[standard]==0.30.0` — ASGI server
- `pydantic==2.9.0` — Data validation
- `streamlit==1.38.0` — Dashboard (used in Step 2)
- `pandas==2.0.3` — Data manipulation
- `plotly==5.23.0` — Interactive charts
- `pymongo==4.6.1` — Optional MongoDB support

### Start the Server

```bash
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

### Verify It's Running

Open your browser and navigate to:

| URL | What You'll See |
|-----|----------------|
| `http://localhost:8001/` | JSON health check with record count |
| `http://localhost:8001/docs` | Interactive Swagger API documentation |
| `http://localhost:8001/api/health-data/latest` | Latest reading per sensor type |

**Expected output** at `/`:
```json
{
  "status": "online",
  "service": "Aidelle Connect API",
  "total_records": 105,
  "timestamp": "2026-04-19T17:00:00.000000"
}
```

> **Keep this terminal open.** This server must stay running.

---

## 5. Step 2 — Streamlit Dashboard

The Streamlit dashboard visualizes health data from the same SQLite database. It runs as a separate process.

### Start the Dashboard

Open a new terminal (Terminal 2):

```bash
cd fastapi_backend

# Activate the same venv
# Windows:
.\venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# Launch Streamlit
streamlit run dashboard.py
```

### What You'll See

Streamlit opens automatically at `http://localhost:8501` with:

1. **Top-Level Metric Cards** — Average heart rate, SpO2, temperature, steps, sleep with outlier counts
2. **Time-Series Tabs** — Interactive Plotly scatter/line charts per metric, with alert threshold lines
3. **Sidebar Controls**:
   - Max Normal Heart Rate slider (60–200 bpm)
   - Min Normal SpO2 slider (85–100%)
   - Max Normal Temperature slider (36–41°C)
   - Daily Step Goal slider (1K–20K)
   - Min Sleep Target slider (2–12 hrs)
   - Time range filter (24h / 7d / 30d / All Time)
4. **Vital Stability Score** — A 0–100 composite health index
5. **Anomaly Log Table** — Chronological list of threshold violations

> **Keep this terminal open.** The dashboard auto-refreshes every 60 seconds.

---

## 6. Step 3 — AI Agent Backend

This is the intelligent core of Aidelle. It runs a **LangGraph ReAct agent** powered by **Gemini 3 Flash** for reasoning and **Qwen 3.5 0.8B** for local vision analysis (injury images/videos).

### Install Dependencies

Open a new terminal (Terminal 3):

```bash
cd Agent_Backend

# Create and activate a virtual environment
python -m venv venv

# Windows:
.\venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# Install all required packages
pip install langchain langgraph langchain-google-genai langchain-core transformers torch opencv-python pymongo python-dotenv qwen-vl-utils fastapi uvicorn[standard]
```

> **GPU Note:** For PyTorch with CUDA, install the GPU-specific version:
> ```bash
> pip install torch --index-url https://download.pytorch.org/whl/cu121
> ```

### What Happens on Startup

When the server starts, it will:

1. **Load Qwen 3.5 0.8B** — Downloads ~1.6GB of model weights from HuggingFace (first run only, cached afterwards). This takes 1–3 minutes depending on your internet and GPU.
2. **Initialize the Gemini client** — Connects to the Google AI API using your `GEMINI_API_KEY`.
3. **Register 6 tools** — PubMed search, medication reminders, emergency contact, injury video/image analysis, sensor anomaly detection.
4. **Create the ReAct agent** — LangGraph orchestrates the tool-calling loop.

### Start the Server

```bash
uvicorn api:app --host 0.0.0.0 --port 8000
```

### Verify It's Running

| URL | What You'll See |
|-----|----------------|
| `http://localhost:8000/health` | `{"status": "healthy", "model": "Qwen 3.5 0.8B"}` |
| `http://localhost:8000/docs` | Swagger docs with `/chat`, `/analyze-video`, `/analyze-image` |

### Test with a Quick Chat

Using `curl` or the Swagger UI:

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What medications do I need to take?"}'
```

The agent will:
1. Receive your message
2. Decide to call `check_patient_reminders` (tool)
3. Check the medication database
4. Return a warm, elderly-friendly response

> **Keep this terminal open.** This server must stay running for the frontend.

---

## 7. Step 4 — Web Frontend

The React frontend is the user-facing interface. It features a **3D VRM avatar** with voice conversation and a **nurse monitoring dashboard**.

### Install Dependencies

Open a new terminal (Terminal 4):

```bash
cd aidelle-frontend

# Install all packages
npm install
```

This installs (among others):
- `react` / `react-dom` 19 — UI framework
- `three` 0.183 + `@react-three/fiber` + `@react-three/drei` — 3D rendering
- `@pixiv/three-vrm` + `@pixiv/three-vrm-animation` — VRM avatar support
- `@camb-ai/browser-sdk` — Text-to-Speech
- `recharts` — Charts in the nurse dashboard
- `react-router-dom` — Client-side routing
- `lucide-react` — Icons

### Start the Dev Server

```bash
npm run dev
```

### What You'll See

Vite opens at `http://localhost:5173` with three routes:

#### `/` — Home Selection
A landing page with two cards:
- **Elderly Mobile View** → navigates to `/user`
- **Nurse Dashboard** → navigates to `/nurse`

#### `/user` — 3D AI Avatar (Elderly Interface)
This is the main interaction surface:

1. A **lifelike 3D VRM avatar** appears with a waving animation
2. Click the large **microphone button** to speak (or press `Space`)
3. Your speech is transcribed via the browser's **Web Speech API**
4. The transcript is sent to `POST /chat` on the Agent Backend
5. The AI response is synthesized into speech via **Camb AI TTS**
6. The avatar plays a **nodding animation** while speaking, with **procedural lip sync**
7. **Subtitles** appear word-by-word, synced to the speech pace
8. Click the **video icon** (right) to record a video of an injury — it's uploaded, analyzed by Qwen Vision, and the result is spoken back
9. Click the **chat icon** (left) to view conversation history

#### `/nurse` — Nurse Monitoring Dashboard
A comprehensive caregiver panel:

1. **Patient cards** at the top — click to select a patient (Fatimah, Tan, Karthik)
2. **Heart Rate chart** — interactive Recharts line graph per patient
3. **Medication panel** — view/add/remove medications with status tracking
4. **Sensor panel** — manage smart devices (smartwatch, temperature, insulin pump, GPS, sleep monitor) with battery levels and on/off toggles
5. **Live polling** — fetches latest health data from the FastAPI Data Backend every 5 seconds

> **Keep this terminal open.** The frontend auto-reloads on code changes.

---

## 8. Running Everything Together

Here's the full picture of what needs to be running simultaneously:

| Terminal | Directory | Command | Port | Purpose |
|----------|-----------|---------|------|---------|
| **1** | `fastapi_backend/` | `uvicorn main:app --host 0.0.0.0 --port 8001 --reload` | `8001` | Data API (health records) |
| **2** | `fastapi_backend/` | `streamlit run dashboard.py` | `8501` | Health dashboard |
| **3** | `Agent_Backend/` | `uvicorn api:app --host 0.0.0.0 --port 8000` | `8000` | AI agent (chat, vision) |
| **4** | `aidelle-frontend/` | `npm run dev` | `5173` | React web app |

### Startup Order

Start them **in this order** for the best experience:

1. **Data Backend** (port 8001) — No dependencies, starts instantly
2. **Streamlit Dashboard** (port 8501) — Reads from the same SQLite DB
3. **Agent Backend** (port 8000) — Takes 1–3 min to load Qwen model
4. **Frontend** (port 5173) — Connects to both backends; start last

### Quick Access URLs

| URL | What |
|-----|------|
| `http://localhost:5173` | Web Frontend (Home) |
| `http://localhost:5173/user` | 3D Avatar Interface |
| `http://localhost:5173/nurse` | Nurse Dashboard |
| `http://localhost:8001/docs` | Data API Swagger Docs |
| `http://localhost:8000/docs` | Agent API Swagger Docs |
| `http://localhost:8501` | Streamlit Health Dashboard |

---

## 9. Testing the System

### Test 1: Voice Conversation with the Avatar

1. Go to `http://localhost:5173/user`
2. Wait for the avatar to finish its waving animation
3. Click the microphone button and say: *"What medications do I need to take?"*
4. Watch the agent think → call the `check_patient_reminders` tool → respond with spoken audio and subtitles
5. The avatar will nod while speaking

### Test 2: Medical Research Query

1. Click the microphone and say: *"What is diabetes?"*
2. The agent will call `search_medical_database` (PubMed) → summarize the top 3 results in plain language

### Test 3: Sensor Anomaly Check

1. Say: *"Check my sensor readings"*
2. The agent will call `get_and_analyze_sensor_data` → report anomalies (high HR, low SpO2, fever spike)

### Test 4: Video Injury Analysis

1. Click the video camera icon (right side)
2. Record a short clip showing a simulated injury (e.g., a scrape or bruise)
3. Click **Stop & Send**
4. The video is uploaded to `/analyze-video`, converted from WebM to MP4, analyzed by Qwen Vision, and the AI responds verbally

### Test 5: Nurse Dashboard

1. Go to `http://localhost:5173/nurse`
2. Click on different patient cards to see their heart rate charts
3. Add a medication: click **+ Add** in the Medications panel
4. Add a sensor: click **+ Add** in the Smart Sensors panel
5. Toggle a sensor on/off and observe the status change

### Test 6: Push Data via API

Send mock health data directly to the Data Backend:

```bash
curl -X POST http://localhost:8001/api/health-data \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "demo-device",
    "records": [
      {
        "data_type": "heart_rate",
        "value": 112,
        "unit": "bpm",
        "timestamp": "2026-04-19T17:00:00Z"
      },
      {
        "data_type": "oxygen_saturation",
        "value": 93,
        "unit": "%",
        "timestamp": "2026-04-19T17:00:00Z"
      }
    ]
  }'
```

Then check `http://localhost:8501` — the Streamlit dashboard should show the new data with outlier alerts (HR > 100, SpO2 < 95).

---

## 10. Troubleshooting

### "GEMINI_API_KEY not set"
- Ensure your `Aidelle/.env` file contains `GEMINI_API_KEY=your_key`
- The Agent Backend uses `python-dotenv` to load it. Make sure the `.env` is in the **root `Aidelle/` directory**, not inside `Agent_Backend/`

### Qwen model download is stuck
- First run downloads ~1.6GB from HuggingFace. Check your internet connection
- If it fails, try: `huggingface-cli login` and retry
- For CPU-only: it will work but inference takes 30–60 seconds per query

### "CORS error" in the browser console
- Both backends have `allow_origins=["*"]`, so CORS should not be an issue
- Double-check that the ports in your frontend `.env` match the running servers

### Avatar doesn't speak (no audio)
- Verify `VITE_CAMB_API_KEY` is set in `aidelle-frontend/.env`
- Check the browser console for `Camb AI Error` messages
- Camb AI free tier has a 495-character limit per request; responses are auto-truncated

### Speech recognition doesn't work
- Web Speech API requires **HTTPS** or **localhost** — it won't work on plain `http://192.168.x.x`
- Use Chrome or Edge (Firefox has limited Web Speech API support)
- Allow microphone permissions when prompted

### Streamlit shows "No health data found"
- Make sure `health_data.db` exists in `fastapi_backend/`
- If it was deleted, start the Data Backend once (`uvicorn main:app`) to create an empty DB, then push data via the API

### Port conflicts
- If port 8000 or 8001 is occupied, change the port in the `uvicorn` command:
  ```bash
  uvicorn main:app --port 8002
  ```
- Update the corresponding `VITE_*_URL` in `aidelle-frontend/.env`

---

*Last Updated: April 19, 2026*
