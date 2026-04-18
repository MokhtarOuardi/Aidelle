# Aidelle Virtual Assistant - Frontend

An accessible, and highly interactive 3D virtual assistant designed primarily for senior care and health assessments. This React-based application utilizes WebGL and WebRTC to deliver a beautiful conversational experience directly in the browser or via mobile wrapper.

## ✨ Core Features

*   **Interactive 3D Avatar (VRM)**: Built using `@react-three/fiber` and `@pixiv/three-vrm`. The avatar dynamically handles idling animations, biological micro-expressions (blinking, emotional cycling), and state syncs (waving, thinking, nodding).
*   **Procedural Lip Sync**: Real-time mouth movements synchronized with TTS audio generation, ensuring low latency and natural speaking mechanics without pre-baking animations.
*   **Accessible UI & Teleprompter**: A custom, glossy scrolling subtitle system specifically designed for readability by elderly users. Words glide in sequentially like a professional teleprompter and seamlessly fade out post-interaction.
*   **Camera & Computer Vision integration**: Utilizes the browser's `MediaRecorder` API to capture WebM video feeds of the user and streams them to an AI vision backend for injury/health assessment.
*   **TTS & STT Interop**: Built-in voice captures using the Web Speech API integrated smoothly with the ultra-fast Camb AI MARS text-to-speech SDK.

## 🛠 Tech Stack

*   **Framework**: [React](https://reactjs.org/) + [Vite](https://vitejs.dev/)
*   **3D Rendering**: `@react-three/fiber`, `three.js`, `@react-three/drei`
*   **Avatar Format**: `three-vrm` and `three-vrm-animation` (using `.vrma` skeletal animations)
*   **Styling**: Vanilla CSS with premium glassmorphism and keyframe animations.
*   **Icons**: `lucide-react`

## 🚀 Getting Started

### Prerequisites

Ensure you have Node.js (v18+) installed.

### Setup

1. **Clone & Install Dependencies**
   ```bash
   cd aidelle-frontend
   npm install
   ```

2. **Environment Variables**
   Create a `.env.local` file in the root directory (this is already ignored by `.gitignore`):
   ```env
   
   
   # Backend API URLs
   VITE_DATA_API_URL=http://localhost:8000
   VITE_AGENT_API_URL=http://localhost:8001

   # Camb AI configuration for Text-to-Speech
   VITE_CAMB_API_KEY=YOUR_API_KEY_HERE
   ```
   *(Note: Adjust the URLs to match the port of your running backend.)*

3. **Run the Development Server**
   ```bash
   npm run dev
   ```
   The application will be running on `http://localhost:5173`.

## 📁 Project Structure highlights

*   `/src/components/Avatar.jsx`: Handles all 3D geometry loading, procedural lip reading, keyframe clip playing, and idle expression cyclers.
*   `/src/views/UserMobileView.jsx`: The primary responsive interface handling the WebGL viewport, floating controls, subtitles, and hardware camera capture logic.
*   `/src/hooks/useBrain.js`: The bridge separating the frontend from the Python `Agent_Backend`.
*   `/src/hooks/useVoice.js`: Hardware microphone controller and Camb AI TTS API caller.

