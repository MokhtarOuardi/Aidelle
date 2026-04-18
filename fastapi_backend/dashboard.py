import streamlit as st
import pandas as pd
import sqlite3
import plotly.express as px
import plotly.graph_objects as go
from pathlib import Path
import datetime

# --- CONFIGURATION ---
st.set_page_config(
    page_title="Aidelle Health Monitoring",
    layout="wide"
)

DB_PATH = Path(__file__).parent / "health_data.db"

# --- DATA LOADING ---
@st.cache_data(ttl=60) # Cache data for 60 seconds
def load_data():
    try:
        conn = sqlite3.connect(str(DB_PATH))
        # Load records into pandas
        df = pd.read_sql_query("SELECT * FROM health_records", conn)
        conn.close()
        
        if not df.empty:
            df['timestamp'] = pd.to_datetime(df['timestamp'])
            df = df.sort_values(by='timestamp', ascending=False)
        return df
    except Exception as e:
        st.error(f"Failed to connect to database: {e}")
        return pd.DataFrame()

df = load_data()

# --- TITLE ---
st.title("Aidelle : Your Personal AI Nurse")

if df.empty:
    st.info("No health data found in the database yet. Sync your device to get started.")
    st.stop()

# --- SIDEBAR: ALERT CRITERIA ---
st.sidebar.header("Alert Setup Criteria")

# Alert settings dictionary
alerts = {}

# We extract unique datatypes
metrics = df['data_type'].unique()

if 'heart_rate' in metrics:
    alerts['heart_rate'] = st.sidebar.slider("Max Normal Heart Rate (bpm)", min_value=60.0, max_value=200.0, value=100.0, step=1.0)
    
if 'oxygen_saturation' in metrics:
    alerts['oxygen_saturation'] = st.sidebar.slider("Min Normal SpO2 (%)", min_value=85.0, max_value=100.0, value=95.0, step=1.0)

if 'body_temperature' in metrics:
    alerts['body_temperature'] = st.sidebar.slider("Max Normal Temp (°C)", min_value=36.0, max_value=41.0, value=37.5, step=0.1)

if 'steps' in metrics:
    alerts['steps'] = st.sidebar.slider("Daily Step Goal (steps)", min_value=1000, max_value=20000, value=10000, step=500)

if 'sleep' in metrics:
    alerts['sleep'] = st.sidebar.slider("Min Target Sleep (minutes)", min_value=120, max_value=720, value=420, step=30)

st.sidebar.divider()
st.sidebar.markdown("### 📅 Filter Time Range")
days_to_look_back = st.sidebar.radio("View Data From:", ["Last 24 Hours", "Last 7 Days", "Last 30 Days", "All Time"])

# Apply time mask
now = datetime.datetime.now(datetime.timezone.utc)
if days_to_look_back == "Last 24 Hours":
    threshold_time = pd.Timestamp.utcnow() - pd.Timedelta(days=1)
elif days_to_look_back == "Last 7 Days":
    threshold_time = pd.Timestamp.utcnow() - pd.Timedelta(days=7)
elif days_to_look_back == "Last 30 Days":
    threshold_time = pd.Timestamp.utcnow() - pd.Timedelta(days=30)
else:
    threshold_time = None

if threshold_time:
    # Ensure dataframe's timestamp is timezone aware for comparison
    if df['timestamp'].dt.tz is None:
        df['timestamp'] = df['timestamp'].dt.tz_localize('UTC')
        
    df_filtered = df[df['timestamp'] >= threshold_time]
else:
    df_filtered = df

if df_filtered.empty:
    st.warning("No data found for the selected time range.")
    st.stop()


# --- CALCULATE OUTLIERS ---
def is_outlier(row):
    dt = row['data_type']
    val = row['value']
    if dt == 'heart_rate' and val > alerts.get('heart_rate', 999): return True
    if dt == 'oxygen_saturation' and val < alerts.get('oxygen_saturation', -1): return True
    if dt == 'body_temperature' and val > alerts.get('body_temperature', 999): return True
    return False

df_filtered['is_outlier'] = df_filtered.apply(is_outlier, axis=1)

# --- TOP LEVEL METRICS ---
metric_cols = st.columns(len(metrics))

for i, m_type in enumerate(metrics):
    m_data = df_filtered[df_filtered['data_type'] == m_type]
    if m_data.empty: continue
    
    avg_val = m_data['value'].mean()
    outlier_count = m_data['is_outlier'].sum()
    
    with metric_cols[i]:
        title = m_type.replace('_', ' ').title()
        unit = m_data['unit'].iloc[0]
        st.metric(label=f"Avg {title}", value=f"{avg_val:.1f} {unit}")
        if outlier_count > 0 and m_type in ['heart_rate', 'body_temperature', 'oxygen_saturation']:
            st.error(f"{outlier_count} Outliers Detected!")
        else:
            st.success("Normal")


# --- TIME SERIES VISUALIZATION ---
st.divider()

tabs = st.tabs([m.replace('_', ' ').title() for m in metrics])

for i, m_type in enumerate(metrics):
    with tabs[i]:
        m_data = df_filtered[df_filtered['data_type'] == m_type].copy()
        if m_data.empty:
            st.write("No data available.")
            continue
            
        # Distinguish colors
        m_data['Status'] = m_data['is_outlier'].map({True: 'Outlier Alerts', False: 'Normal'})
        color_map = {'Normal': '#1f77b4', 'Outlier Alerts': '#ff7f0e'}
        
        fig = px.scatter(
            m_data, 
            x='timestamp', 
            y='value', 
            color='Status',
            color_discrete_map=color_map,
            title=f"{m_type.replace('_', ' ').title()} Over Time",
            labels={'value': m_data['unit'].iloc[0], 'timestamp': 'Time'}
        )
        
        # Add line to connect the dots if there are many
        fig.add_trace(go.Scatter(
            x=m_data['timestamp'], y=m_data['value'],
            mode='lines', line=dict(color='#1f77b4', width=1),
            showlegend=False, hoverinfo='skip'
        ))

        # Add Threshold Threshold Lines
        if m_type in alerts:
            threshold_val = alerts[m_type]
            fig.add_hline(y=threshold_val, line_dash="dot", annotation_text=f"Alert Threshold ({threshold_val})", line_color="red")
            
        st.plotly_chart(fig, use_container_width=True)


# --- AIDELLE HEALTH ENGINE ANALYSIS ---
st.divider()
st.subheader("Aidelle Tier 1 Analysis")

# Creative Analysis 1: Vital Stability Score
st.markdown("#### Vital Stability Score")
total_readings = len(df_filtered[df_filtered['data_type'].isin(['heart_rate', 'oxygen_saturation', 'body_temperature'])])
total_outliers = df_filtered['is_outlier'].sum()

if total_readings > 0:
    stability_score = max(0, 100 - ((total_outliers / total_readings) * 100 * 5)) # Heavily penalize outliers
    
    score_col, desc_col = st.columns([1, 3])
    with score_col:
        st.metric(label="Overall Stability Index", value=f"{stability_score:.0f}/100")
    with desc_col:
        if stability_score >= 90:
            st.success("Your vitals are exceptionally stable! Aidelle detects no significant anomalies.")
        elif stability_score >= 70:
            st.info("Your vitals are mostly stable, but there have been occasional readings crossing the alert thresholds.")
        else:
            st.error("Aidelle detected multiple vital outliers recently. Please monitor your health closely or consult a medical professional.")
else:
    st.info("Not enough critical vital data (HR, SpO2, Temp) to calculate a stability score.")


# Creative Analysis 2: Anomaly Log Table
st.markdown("#### Incident & Anomaly Log")
outlier_df = df_filtered[df_filtered['is_outlier'] == True]

if not outlier_df.empty:
    display_df = outlier_df[['timestamp', 'data_type', 'value', 'unit']].copy()
    display_df['data_type'] = display_df['data_type'].str.replace('_', ' ').str.title()
    display_df['timestamp'] = display_df['timestamp'].dt.strftime('%b %d, %H:%M')
    display_df.rename(columns={'timestamp': 'When', 'data_type': 'Vital', 'value': 'Reading', 'unit': 'Unit'}, inplace=True)
    st.dataframe(display_df, hide_index=True, use_container_width=True)
else:
    st.write("Awesome! No outlier incidents logged in the current timeframe.")
