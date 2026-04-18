import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Smartphone, Activity } from 'lucide-react';
import './HomeSelection.css';

export default function HomeSelection() {
  const navigate = useNavigate();

  return (
    <div className="home-container">
      <div className="branding">
        <img src="/icon.jpeg" alt="Aidelle Logo" className="logo" />
        <h1>Aidelle</h1>
        <p>Select a demonstration view</p>
      </div>
      
      <div className="cards-container">
        <button className="demo-card" onClick={() => navigate('/user')}>
          <div className="icon-wrapper">
            <Smartphone size={48} strokeWidth={1.5} />
          </div>
          <h2>Elderly Mobile View</h2>
          <p>A streamlined, accessible interface showcasing our 3D AI Assistant.</p>
        </button>

        <button className="demo-card" onClick={() => navigate('/nurse')}>
          <div className="icon-wrapper">
            <Activity size={48} strokeWidth={1.5} />
          </div>
          <h2>Nurse Dashboard</h2>
          <p>A comprehensive system for monitoring vitals, sensors, and medicine.</p>
        </button>
      </div>
    </div>
  );
}
