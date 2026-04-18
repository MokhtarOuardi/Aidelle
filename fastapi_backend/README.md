# Aidelle FastAPI Backend & Dashboard

This directory contains the Python-based REST server and the interactive web dashboard for **Aidelle : Your Personal AI Nurse**.

## Features
*   **FastAPI REST Server**: High-performance asynchronous API endpoints to ingested and retrieve time-series biometric data streamed from the Aidelle Android app.
*   **SQLite Persistence**: Zero-configuration, lightweight local database (`health_data.db`) optimized for time-series queries.
*   **Monitoring Dashboard**: A Streamlit web application providing live visualizations, customizable alertness thresholds, and the **Aidelle Tier 1 Analysis** (including the Vital Stability Score & Anomaly Logs).

## Setup & Installation

1. **Create and activate a virtual environment:**
   ```bash
   python -m venv venv
   
   # Windows
   .\venv\Scripts\activate
   
   # Unix/macOS
   source venv/bin/activate
   ```

2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

## Running the Services

Ensure your virtual environment is activated before running either service.

### 1. Start the API Server
Handles all incoming data from the mobile companion app.
```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```
*The API will be available at `http://localhost:8000`. Auto-generated Swagger API documentation can be accessed at `http://localhost:8000/docs`.*

### 2. Start the Tier 1 AI Nurse Dashboard
Visualizes the data stored in the local SQLite database.
```bash
streamlit run dashboard.py
```
*The interactive web interface will automatically open in your default browser at `http://localhost:8501`.*

## Internal Structure
*   `main.py`: Core FastAPI application and route declarations.
*   `models.py`: Pydantic data schemas for validating the incoming JSON payloads.
*   `database.py`: SQLite connection hooks, table creation, and CRUD functions.
*   `dashboard.py`: Streamlit logic, Pandas data manipulation, and Plotly graphics.
*   `requirements.txt`: Python package dependencies.
