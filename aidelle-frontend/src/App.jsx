import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomeSelection from './views/HomeSelection';
import UserMobileView from './views/UserMobileView';
import NurseDashboard from './views/NurseDashboard';
import './index.css';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeSelection />} />
        <Route path="/user" element={<UserMobileView />} />
        <Route path="/nurse" element={<NurseDashboard />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
