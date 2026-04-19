import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity, Heart, Thermometer,
  Pill, Settings, Bell, ChevronLeft, User,
  MapPin, Moon, Watch, Plus, Trash2, X
} from 'lucide-react';

import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import './NurseDashboard.css';

const heartRateDataMap = {
  1: [ // Fatimah
    { time: '08:00', value: 72 }, { time: '09:00', value: 75 },
    { time: '10:00', value: 85 }, { time: '11:00', value: 78 },
    { time: '12:00', value: 70 }, { time: '13:00', value: 74 },
    { time: '14:00', value: 73 },
  ],
  2: [ // Tan
    { time: '08:00', value: 95 }, { time: '09:00', value: 98 },
    { time: '10:00', value: 102 }, { time: '11:00', value: 99 },
    { time: '12:00', value: 98 }, { time: '13:00', value: 100 },
    { time: '14:00', value: 97 },
  ],
  3: [ // Karthik
    { time: '08:00', value: 110 }, { time: '09:00', value: 115 },
    { time: '10:00', value: 112 }, { time: '11:00', value: 118 },
    { time: '12:00', value: 122 }, { time: '13:00', value: 115 },
    { time: '14:00', value: 118 },
  ]
};

const patientsData = [
  { id: 1, name: 'Fatimah Abdullah', location: 'Section 14, PJ', status: 'Stable', bpm: 72, trend: 'stable', steps: 4500, oxygen: 98 },
  { id: 2, name: 'Tan Wei Seng', location: 'Cheras, KL', status: 'Warning', bpm: 98, trend: 'up', steps: 2100, oxygen: 94 },
  { id: 3, name: 'Karthik Pillai', location: 'Bayan Lepas, PG', status: 'Critical', bpm: 112, trend: 'up', steps: 1200, oxygen: 89 },
];

const SENSOR_TYPES = [
  { value: 'smartwatch', label: 'Smart Watch', icon: Watch },
  { value: 'temperature', label: 'Temperature Sensor', icon: Thermometer },
  { value: 'insulin', label: 'Smart Insulin Pump', icon: Activity },
  { value: 'gps', label: 'GPS Tracker', icon: MapPin },
  { value: 'sleep', label: 'Sleep Monitor', icon: Moon },
];

const dataApiUrl = import.meta.env.VITE_DATA_API_URL;

export default function NurseDashboard() {
  const navigate = useNavigate();
  const [selectedPatientId, setSelectedPatientId] = useState(1);
  const [patients, setPatients] = useState(patientsData);
  const [graphData, setGraphData] = useState([]);

  // ── Fetch Health Data ──
  useEffect(() => {
    if (!dataApiUrl) return;

    const fetchLatestData = async () => {
      try {
        const response = await fetch(`${dataApiUrl}/api/health-data/latest`);
        if (!response.ok) return;
        
        const data = await response.json();
        const latest = data.latest_records || [];

        // Map backend data to the patients state
        setPatients(prevPatients => prevPatients.map(p => {
          if (p.id === 1) { // Patient 1 is REAL (linked to API)
            const updates = {};
            const hrRecord = latest.find(r => r.data_type === 'heart_rate');
            if (hrRecord) {
              updates.bpm = Math.round(hrRecord.value);
              updates.status = hrRecord.value > 100 ? 'Critical' : hrRecord.value > 90 ? 'Warning' : 'Stable';
            }
            const stepsRecord = latest.find(r => r.data_type === 'steps');
            if (stepsRecord) {
              updates.steps = Math.round(stepsRecord.value);
            }
            const oxygenRecord = latest.find(r => r.data_type === 'oxygen_saturation');
            if (oxygenRecord) {
              updates.oxygen = Math.round(oxygenRecord.value);
            }
            return { ...p, ...updates };
          } else {
            // Patients 2 and 3 are FAKE (simulating live activity)
            const jitter = (val, range) => Math.round(val + (Math.random() * range - range / 2));
            return {
              ...p,
              bpm: jitter(p.id === 2 ? 98 : 112, 4),
              steps: p.steps + Math.floor(Math.random() * 3),
              oxygen: Math.min(100, Math.max(88, jitter(p.id === 2 ? 94 : 89, 1)))
            };
          }
        }));
      } catch (err) {
        console.error("Dashboard fetch error:", err);
      }
    };

    const fetchGraphData = async () => {
      // Only fetch graph data from API for Patient 1
      if (selectedPatientId !== 1) {
        setGraphData(heartRateDataMap[selectedPatientId] || []);
        return;
      }

      try {
        const response = await fetch(`${dataApiUrl}/api/health-data?data_type=heart_rate&limit=20`);
        if (!response.ok) return;
        
        const data = await response.json();
        const formattedData = [];
        const seen = new Set();
        data.reverse().forEach(record => {
           const d = new Date(record.timestamp);
           const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
           const value = Math.round(record.value);
           const key = `${time}-${value}`;
           if (!seen.has(key)) {
             seen.add(key);
             formattedData.push({ time, value });
           }
        });
        
        if (formattedData.length > 0) {
            setGraphData(formattedData);
        } else {
            setGraphData(heartRateDataMap[selectedPatientId]);
        }
      } catch (err) {
        console.error("Graph fetch error:", err);
        setGraphData(heartRateDataMap[selectedPatientId]);
      }
    };

    fetchLatestData();
    fetchGraphData();
    const interval = setInterval(() => {
      fetchLatestData();
      fetchGraphData();
    }, 5000); // Poll every 5 seconds
    return () => clearInterval(interval);
  }, [selectedPatientId]);

  const selectedPatient = patients.find(p => p.id === selectedPatientId);

  // ── Medications State ──
  const [medications, setMedications] = useState({
    1: [
      { id: 1, name: 'Lisinopril 10mg', time: '08:00 AM', frequency: 'Daily', status: 'taken' },
      { id: 2, name: 'Metformin 500mg', time: '12:00 PM', frequency: 'Daily', status: 'upcoming' },
      { id: 3, name: 'Aspirin 100mg', time: '08:00 PM', frequency: 'Daily', status: 'scheduled' },
    ],
    2: [
      { id: 1, name: 'Metformin 500mg', time: '08:00 AM', frequency: 'Twice daily', status: 'taken' },
      { id: 2, name: 'Amlodipine 5mg', time: '08:00 AM', frequency: 'Daily', status: 'taken' },
      { id: 3, name: 'Metformin 500mg', time: '08:00 PM', frequency: 'Twice daily', status: 'upcoming' },
    ],
    3: [
      { id: 1, name: 'Atorvastatin 20mg', time: '09:00 PM', frequency: 'Daily', status: 'scheduled' },
      { id: 2, name: 'Clopidogrel 75mg', time: '08:00 AM', frequency: 'Daily', status: 'taken' },
      { id: 3, name: 'Ramipril 5mg', time: '08:00 AM', frequency: 'Daily', status: 'missed' },
      { id: 4, name: 'Bisoprolol 2.5mg', time: '08:00 AM', frequency: 'Daily', status: 'taken' },
    ],
  });

  const [showAddMed, setShowAddMed] = useState(false);
  const [newMed, setNewMed] = useState({ name: '', time: '', frequency: 'Daily' });

  // ── Sensors State ──
  const [sensors, setSensors] = useState({
    1: [
      { id: 1, type: 'smartwatch', name: 'Apple Watch SE', status: true, battery: 82 },
      { id: 2, type: 'temperature', name: 'ThermoSense Pro', status: true, battery: 95 },
    ],
    2: [
      { id: 1, type: 'smartwatch', name: 'Samsung Galaxy Watch', status: true, battery: 45 },
      { id: 2, type: 'insulin', name: 'InsulinSync Pod', status: true, battery: 71 },
      { id: 3, type: 'gps', name: 'TrackSafe Mini', status: false, battery: 12 },
    ],
    3: [
      { id: 1, type: 'smartwatch', name: 'Fitbit Sense 2', status: true, battery: 58 },
      { id: 2, type: 'temperature', name: 'ThermoSense Pro', status: true, battery: 88 },
      { id: 3, type: 'insulin', name: 'InsulinSync Pod', status: true, battery: 34 },
      { id: 4, type: 'sleep', name: 'DreamTrack Pad', status: true, battery: 100 },
    ],
  });

  const [showAddSensor, setShowAddSensor] = useState(false);
  const [newSensor, setNewSensor] = useState({ type: 'smartwatch', name: '' });

  // ── Medication Handlers ──
  const addMedication = () => {
    if (!newMed.name.trim() || !newMed.time.trim()) return;
    const patientMeds = medications[selectedPatientId] || [];
    const nextId = patientMeds.length > 0 ? Math.max(...patientMeds.map(m => m.id)) + 1 : 1;
    setMedications({
      ...medications,
      [selectedPatientId]: [...patientMeds, { ...newMed, id: nextId, status: 'scheduled' }]
    });
    setNewMed({ name: '', time: '', frequency: 'Daily' });
    setShowAddMed(false);
  };

  const removeMedication = (medId) => {
    setMedications({
      ...medications,
      [selectedPatientId]: (medications[selectedPatientId] || []).filter(m => m.id !== medId)
    });
  };

  // ── Sensor Handlers ──
  const addSensor = () => {
    if (!newSensor.name.trim()) return;
    const patientSensors = sensors[selectedPatientId] || [];
    const nextId = patientSensors.length > 0 ? Math.max(...patientSensors.map(s => s.id)) + 1 : 1;
    setSensors({
      ...sensors,
      [selectedPatientId]: [...patientSensors, { ...newSensor, id: nextId, status: true, battery: 100 }]
    });
    setNewSensor({ type: 'smartwatch', name: '' });
    setShowAddSensor(false);
  };

  const removeSensor = (sensorId) => {
    setSensors({
      ...sensors,
      [selectedPatientId]: (sensors[selectedPatientId] || []).filter(s => s.id !== sensorId)
    });
  };

  const toggleSensor = (sensorId) => {
    setSensors({
      ...sensors,
      [selectedPatientId]: (sensors[selectedPatientId] || []).map(s =>
        s.id === sensorId ? { ...s, status: !s.status } : s
      )
    });
  };

  const getSensorIcon = (type) => {
    const found = SENSOR_TYPES.find(s => s.value === type);
    return found ? found.icon : Activity;
  };

  const getSensorLabel = (type) => {
    const found = SENSOR_TYPES.find(s => s.value === type);
    return found ? found.label : type;
  };

  const currentMeds = medications[selectedPatientId] || [];
  const currentSensors = sensors[selectedPatientId] || [];

  return (
    <div className="nurse-layout">

      {/* ── Top Bar ── */}
      <header className="nurse-topbar">
        <div className="topbar-left">
          <div className="brand" onClick={() => navigate('/')}>
            <img src="/icon.jpeg" alt="Aidelle" className="brand-logo" />
            <span>Aidelle</span>
          </div>
        </div>

        <div className="topbar-right">
          <button className="icon-btn"><Bell size={22} /></button>
          <div className="profile-btn">Nurse Admin</div>
          <button className="exit-btn-small" onClick={() => navigate('/')}>
            <ChevronLeft size={18} /> Exit
          </button>
        </div>
      </header>

      {/* ── Main Content ── */}
      <main className="nurse-main">

        {/* Patient Selection Cards */}
        <section className="patients-overview">
          <div className="section-header">
            <h2>Tracked Residents</h2>
          </div>

          <div className="patient-cards">
            {patients.map(patient => (
              <div
                key={patient.id}
                className={`patient-mini-card ${patient.status.toLowerCase()} ${selectedPatientId === patient.id ? 'selected' : ''}`}
                onClick={() => setSelectedPatientId(patient.id)}
              >
                <div className="patient-info-top">
                  <div className="avatar-small"><User size={20} /></div>
                  <div className="patient-meta">
                    <h3>{patient.name}</h3>
                    <span>{patient.location}</span>
                  </div>
                </div>
                <div className="patient-vitals-mini">
                  <div className="vital-item">
                    <Heart size={16} className="vital-icon" />
                    <span>{patient.bpm} BPM</span>
                  </div>
                  <div className="status-tag">{patient.status}</div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Dashboard Grid */}
        <div className="dashboard-grid">

          {/* ── BPM Chart ── */}
          <div className="vitals-section">
            <div className="glass-card full-height">
              <div className="card-header" style={{ flexWrap: 'wrap', gap: '10px' }}>
                <h2><Activity className="card-icon" color="var(--aidelle-danger)" /> Vitals: {selectedPatient.name}</h2>
                <div style={{ display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
                  <div className={`status-badge pulse ${selectedPatient.status.toLowerCase()}`}>{selectedPatient.status}</div>
                  <div className="status-badge" style={{ background: 'rgba(255, 255, 255, 0.4)', color: '#333', display: 'flex', alignItems: 'center' }}>
                    <Heart size={14} color="var(--aidelle-danger)" style={{ marginRight: '6px' }} />
                    {selectedPatient.bpm} BPM
                  </div>
                  <div className="status-badge" style={{ background: 'rgba(56, 189, 248, 0.2)', color: '#0284c7', display: 'flex', alignItems: 'center' }}>
                    <Activity size={14} style={{ marginRight: '6px' }} />
                    {selectedPatient.oxygen}% SpO2
                  </div>
                  <div className="status-badge" style={{ background: 'rgba(34, 197, 94, 0.2)', color: '#166534', display: 'flex', alignItems: 'center' }}>
                    <Activity size={14} style={{ marginRight: '6px' }} />
                    {selectedPatient.steps} Steps
                  </div>
                </div>
              </div>
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={graphData.length > 0 ? graphData : heartRateDataMap[selectedPatientId]}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                    <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{ fill: '#7a869a' }} />
                    <YAxis axisLine={false} tickLine={false} tick={{ fill: '#7a869a' }} domain={['dataMin - 10', 'dataMax + 10']} />
                    <Tooltip
                      contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: 'var(--shadow-md)' }}
                    />
                    <Line
                      type="monotone"
                      dataKey="value"
                      stroke="url(#colorBlue)"
                      strokeWidth={4}
                      dot={{ r: 4, strokeWidth: 2 }}
                      activeDot={{ r: 8, stroke: 'var(--aidelle-primary)', strokeWidth: 2 }}
                    />
                    <defs>
                      <linearGradient id="colorBlue" x1="0" y1="0" x2="1" y2="0">
                        <stop offset="0%" stopColor="var(--aidelle-primary)" />
                        <stop offset="100%" stopColor="var(--aidelle-secondary)" />
                      </linearGradient>
                    </defs>
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* ── Side Panels (Medication + Sensors) ── */}
          <div className="side-section">

            {/* ── Medication Panel ── */}
            <div className="glass-card mb-20">
              <div className="card-header">
                <h2><Pill className="card-icon" color="var(--aidelle-accent)" /> Medications</h2>
                <button className="add-btn" onClick={() => setShowAddMed(true)}>
                  <Plus size={16} /> Add
                </button>
              </div>

              {currentMeds.length === 0 ? (
                <div className="empty-state">
                  <Pill size={32} strokeWidth={1.5} />
                  <p>No medications assigned yet.</p>
                </div>
              ) : (
                <ul className="medicine-list">
                  {currentMeds.map(med => (
                    <li key={med.id} className={`med-item ${med.status}`}>
                      <div className="med-time">{med.time}</div>
                      <div className="med-details">
                        <span className="med-name">{med.name}</span>
                        <span className="med-freq">{med.frequency}</span>
                        <span className={`med-status ${med.status === 'taken' ? 'text-green' : med.status === 'missed' ? 'text-red' : med.status === 'upcoming' ? 'text-blue' : ''}`}>
                          {med.status.charAt(0).toUpperCase() + med.status.slice(1)}
                        </span>
                      </div>
                      <button className="remove-btn" onClick={() => removeMedication(med.id)} title="Remove medication">
                        <Trash2 size={14} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              {/* Add Medication Modal */}
              {showAddMed && (
                <div className="inline-form">
                  <div className="inline-form-header">
                    <span>Add Medication</span>
                    <button className="close-form-btn" onClick={() => setShowAddMed(false)}><X size={16} /></button>
                  </div>
                  <input
                    type="text"
                    placeholder="Medication name (e.g. Aspirin 100mg)"
                    value={newMed.name}
                    onChange={e => setNewMed({ ...newMed, name: e.target.value })}
                    className="form-input"
                  />
                  <div className="form-row">
                    <input
                      type="text"
                      placeholder="Time (e.g. 08:00 AM)"
                      value={newMed.time}
                      onChange={e => setNewMed({ ...newMed, time: e.target.value })}
                      className="form-input"
                    />
                    <select
                      value={newMed.frequency}
                      onChange={e => setNewMed({ ...newMed, frequency: e.target.value })}
                      className="form-select"
                    >
                      <option value="Daily">Daily</option>
                      <option value="Twice daily">Twice daily</option>
                      <option value="Weekly">Weekly</option>
                      <option value="As needed">As needed</option>
                    </select>
                  </div>
                  <button className="submit-btn" onClick={addMedication}>
                    <Plus size={16} /> Add Medication
                  </button>
                </div>
              )}
            </div>

            {/* ── Sensors Panel ── */}
            <div className="glass-card">
              <div className="card-header">
                <h2><Settings className="card-icon" /> Smart Sensors</h2>
                <button className="add-btn" onClick={() => setShowAddSensor(true)}>
                  <Plus size={16} /> Add
                </button>
              </div>

              {currentSensors.length === 0 ? (
                <div className="empty-state">
                  <Watch size={32} strokeWidth={1.5} />
                  <p>No sensors assigned yet.</p>
                </div>
              ) : (
                <div className="sensors-list">
                  {currentSensors.map(sensor => {
                    const SensorIcon = getSensorIcon(sensor.type);
                    return (
                      <div className="sensor-item" key={sensor.id}>
                        <div className="sensor-info">
                          <div className={`sensor-icon-wrap ${sensor.status ? 'active' : ''}`}>
                            <SensorIcon size={20} />
                          </div>
                          <div className="sensor-meta">
                            <span className="sensor-name">{sensor.name}</span>
                            <span className="sensor-type-label">{getSensorLabel(sensor.type)}</span>
                          </div>
                        </div>
                        <div className="sensor-actions">
                          <div className={`battery-indicator ${sensor.battery < 20 ? 'low' : sensor.battery < 50 ? 'medium' : 'high'}`}>
                            {sensor.battery}%
                          </div>
                          <label className="toggle-switch">
                            <input
                              type="checkbox"
                              checked={sensor.status}
                              onChange={() => toggleSensor(sensor.id)}
                            />
                            <span className="slider"></span>
                          </label>
                          <button className="remove-btn" onClick={() => removeSensor(sensor.id)} title="Remove sensor">
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Add Sensor Modal */}
              {showAddSensor && (
                <div className="inline-form">
                  <div className="inline-form-header">
                    <span>Add Sensor</span>
                    <button className="close-form-btn" onClick={() => setShowAddSensor(false)}><X size={16} /></button>
                  </div>
                  <select
                    value={newSensor.type}
                    onChange={e => setNewSensor({ ...newSensor, type: e.target.value })}
                    className="form-select"
                  >
                    {SENSOR_TYPES.map(st => (
                      <option key={st.value} value={st.value}>{st.label}</option>
                    ))}
                  </select>
                  <input
                    type="text"
                    placeholder="Device name (e.g. Apple Watch SE)"
                    value={newSensor.name}
                    onChange={e => setNewSensor({ ...newSensor, name: e.target.value })}
                    className="form-input"
                  />
                  <button className="submit-btn" onClick={addSensor}>
                    <Plus size={16} /> Add Sensor
                  </button>
                </div>
              )}
            </div>

          </div>
        </div>
      </main>
    </div>
  );
}
