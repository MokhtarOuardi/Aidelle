import React, { useState, useEffect, Suspense, useCallback, useRef } from 'react';
import * as THREE from 'three';
import { Canvas } from '@react-three/fiber';
import { Environment, OrbitControls, ContactShadows } from '@react-three/drei';
import { Mic, MicOff, ChevronLeft, MessageSquare, Video, X, Send, Square } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Avatar from '../components/Avatar';
import { useBrain } from '../hooks/useBrain';
import { useVoice } from '../hooks/useVoice';
import './UserMobileView.css';

export default function UserMobileView() {
  const navigate = useNavigate();
  const [lastResponse, setLastResponse] = useState("");
  const [isThinking, setIsThinking] = useState(false);
  const [animationState, setAnimationState] = useState('idle');
  const [userTranscript, setUserTranscript] = useState("");
  const [aiSubtitle, setAiSubtitle] = useState("");
  const [showHistory, setShowHistory] = useState(false);
  const [conversationHistory, setConversationHistory] = useState([]);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [showCameraPreview, setShowCameraPreview] = useState(false);
  const [videoTranscriptFinal, setVideoTranscriptFinal] = useState("");

  const videoRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const recordedChunksRef = useRef([]);
  const streamRef = useRef(null);
  const recordingTimerRef = useRef(null);
  const transcriptBufferRef = useRef("");

  const { getResponse } = useBrain();
  const { startListening, stopListening, speak, isListening, isSpeaking, audioElement, interimTranscript } = useVoice();

  // Determine if user input is blocked (AI is thinking or speaking)
  const isInputBlocked = isThinking || isSpeaking || (animationState === 'nodding');

  // 1. Waving on start
  useEffect(() => {
    setAnimationState('waving');
  }, []);

  // 2. Talking/Idle state sync
  useEffect(() => {
    if (isSpeaking) {
      // Only switch to 'idle' (temporary override for 'talking') if we aren't already in an active ephemeral state
      if (animationState !== 'nodding' && animationState !== 'waving') {
        setAnimationState('idle');
      }
    } else if (!isThinking && (animationState === 'talking' || animationState === 'idle')) {
      // Stay in idle when done thinking/speaking
      if (animationState !== 'idle') setAnimationState('idle');
      // Clear subtitle when done speaking
      setTimeout(() => setAiSubtitle(""), 2000);
    }
  }, [isSpeaking, isThinking, animationState]);

  // 3. Show interim transcript while listening
  useEffect(() => {
    if (interimTranscript) {
      setUserTranscript(interimTranscript);
      if (isRecording) {
        transcriptBufferRef.current = interimTranscript;
      }
    }
  }, [interimTranscript, isRecording]);

  // 4. Space bar shortcut to talk
  useEffect(() => {
    const handleKeyDown = (e) => {
      // Don't trigger if user is typing in an input/textarea
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
      if (e.code === 'Space' && !e.repeat) {
        e.preventDefault();
        if (!isInputBlocked && !isListening) {
          handleSpeakClick();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isInputBlocked, isListening]);

  const handleSpeakClick = () => {
    if (isListening || isInputBlocked) return;

    setUserTranscript("");
    startListening(async (transcript) => {
      processInput(transcript);
    });
  };

  const processInput = async (text) => {
    setUserTranscript(text);
    setIsThinking(true);
    setAnimationState('thinking');

    // Add user message to history
    setConversationHistory(prev => [...prev, { role: 'user', text, timestamp: new Date() }]);

    // 1. Get AI Response from LLM
    const response = await getResponse(text);

    // 2. Start TTS Generation - remains in 'thinking' state until audio is ready
    speak(
      response,
      () => {
        // onReady: When audio is actually ready to play
        setIsThinking(false);
        setAiSubtitle(response);
        setAnimationState('nodding');
        setConversationHistory(prev => [...prev, { role: 'ai', text: response, timestamp: new Date() }]);
      },
      () => {
        // onEnd: handled by useEffect [isSpeaking]
      }
    );
  };

  const onAnimationFinished = useCallback(() => {
    if (animationState === 'nodding') {
      setAnimationState('idle');
    } else if (animationState === 'waving') {
      setAnimationState('idle');
    }
  }, [animationState]);

  // ── Camera / Video Recording ──────────────────────────────────
  
  const handleVideoButtonClick = () => {
    if (isRecording) {
      stopRecording();
    } else {
      startCameraAndRecording();
    }
  };

  const startCameraAndRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user' },
        audio: true
      });
      streamRef.current = stream;
      setShowCameraPreview(true);

      // Attach stream to video element
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      }, 100);

      // Start actual recording
      recordedChunksRef.current = [];
      transcriptBufferRef.current = "";
      setUserTranscript("");
      
      const mediaRecorder = new MediaRecorder(stream, {
        mimeType: 'video/webm;codecs=vp9'
      });

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          recordedChunksRef.current.push(e.data);
        }
      };

      mediaRecorder.onstop = () => {
        const blob = new Blob(recordedChunksRef.current, { type: 'video/webm' });
        sendVideoToApi(blob, transcriptBufferRef.current);
      };

      mediaRecorderRef.current = mediaRecorder;
      mediaRecorder.start();
      setIsRecording(true);
      setRecordingTime(0);

      // Start STT
      startListening((text) => {
        transcriptBufferRef.current = text;
      }, { continuous: true });

      // Timer
      recordingTimerRef.current = setInterval(() => {
        setRecordingTime(prev => prev + 1);
      }, 1000);

    } catch (err) {
      console.error("Camera access error:", err);
      // Fallback: If camera used by others or denied
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        alert("Camera permission denied. Please enable it to use video features.");
      } else {
        alert("Could not access camera/microphone.");
      }
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    stopListening();
    setIsRecording(false);
    clearInterval(recordingTimerRef.current);
  };

  const closeCamera = () => {
    if (isRecording) stopRecording();
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    setShowCameraPreview(false);
    setRecordingTime(0);
  };

  const sendVideoToApi = async (blob, transcript) => {
    // Fake API call – simulates uploading the video + transcript
    console.log("📹 Sending video to API...", { 
      size: blob.size, 
      type: blob.type,
      transcript: transcript 
    });

    setIsThinking(true); // Block UI while 'uploading'
    setAnimationState('thinking');

    try {
      // Simulate network delay
      await new Promise(resolve => setTimeout(resolve, 2000));

      console.log("✅ Video and transcript uploaded successfully (fake API)");
      
      // Add to conversation history
      setConversationHistory(prev => [...prev, {
        role: 'user',
        text: transcript ? `📹 Video: "${transcript}"` : '📹 Video message (no audio detected)',
        timestamp: new Date()
      }]);

      // Simulate AI acknowledging the video and what was said
      const fakeResponse = transcript 
        ? `I received your video and heard you say: "${transcript}". Let me help you with that.`
        : "I received your video. Let me take a look at that for you.";
      
      speak(
        fakeResponse,
        () => {
          setIsThinking(false);
          setAiSubtitle(fakeResponse);
          setAnimationState('nodding');
          setConversationHistory(prev => [...prev, { role: 'ai', text: fakeResponse, timestamp: new Date() }]);
        }
      );

    } catch (error) {
      console.error("Video upload error:", error);
      setIsThinking(false);
      setAnimationState('idle');
    }

    closeCamera();
  };

  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
      }
      clearInterval(recordingTimerRef.current);
    };
  }, []);

  return (
    <div className="mobile-page">
      <div className="mobile-container">

        <button className="back-btn" onClick={() => navigate('/')}>
          <ChevronLeft size={24} />
          <span>Exit</span>
        </button>

        {/* <div className="shortcut-hint">
          Press <kbd>Space</kbd> to talk
        </div> */}

        <div className="canvas-wrapper">
          <Canvas
            shadows={{ type: THREE.PCFShadowMap }}
            camera={{ position: [0, 0.75, 0.8], fov: 45 }}
            className="mobile-canvas"
          >
            <ambientLight intensity={1.8} color="#f8fbff" />
            <directionalLight
              position={[2, 2, 2]}
              intensity={0.8}
              castShadow
              shadow-mapSize={[1024, 1024]}
              color="#ffffff"
            />

            <Suspense fallback={null}>
              <group position={[0, -0.65, 0]}>
                <Avatar
                  animationState={animationState}
                  speaking={isSpeaking}
                  onAnimationFinished={onAnimationFinished}
                  audioElement={audioElement}
                />
              </group>
              <Environment preset="city" />
              <ContactShadows
                opacity={0.3}
                scale={2}
                blur={2.5}
                far={0.6}
                color="#367dec"
              />
            </Suspense>

            <OrbitControls
              enablePan={false}
              enableZoom={false}
              enableRotate={false}
              target={[0, 0.65, 0]}
              minDistance={0.5}
              maxDistance={3}
            />
          </Canvas>

          {/* AI Subtitles overlay on top of avatar */}
          {aiSubtitle && (isSpeaking || isThinking) && (
            <div className="ai-subtitle-overlay">
              <div className="ai-subtitle-text">{aiSubtitle}</div>
            </div>
          )}
        </div>

        {/* User Transcription Bar */}
        {(isListening || userTranscript) && (
          <div className={`user-transcript-bar ${isListening ? 'active' : 'fading'}`}>
            <div className="transcript-label">{isRecording ? "Capturing Audio..." : "You"}{isListening ? (isRecording ? "" : ' (listening...)') : ' said:'}</div>
            <div className="transcript-text">
              {userTranscript || <span className="transcript-placeholder">Speak now...</span>}
            </div>
          </div>
        )}

        <div className="mobile-controls">
          <div className="status-indicator">
            {isThinking
              ? "Aidelle is thinking..."
              : isListening
                ? (isRecording ? "Recording video & audio..." : "Listening...")
                : isSpeaking
                  ? "Aidelle is speaking..."
                  : "Tap to talk to Aidelle"}
          </div>

          {/* Input blocked overlay */}
          {isInputBlocked && (
            <div className="input-blocked-notice">
              <div className="blocked-dot"></div>
              {isThinking ? "Please wait while Aidelle thinks..." : "Aidelle is speaking..."}
            </div>
          )}

          <div className="controls-row">
            {/* History Button (Left) */}
            <button
              type="button"
              className="side-action-btn history-btn"
              onClick={() => setShowHistory(!showHistory)}
              disabled={isRecording}
              aria-label="Conversation history"
              title="View conversation history"
            >
              <MessageSquare size={24} />
            </button>

            {/* Main Speak Button (Center) */}
            <button
              type="button"
              className={`giant-speak-button ${isListening && !isRecording ? 'listening' : ''} ${isSpeaking ? 'speaking' : ''} ${isThinking ? 'thinking' : ''} ${isInputBlocked ? 'blocked' : ''}`}
              onClick={handleSpeakClick}
              disabled={isInputBlocked || isRecording}
              aria-label="Tap to speak"
            >
              <div className="button-ring-1"></div>
              <div className="button-ring-2"></div>
              <div className="mic-icon-wrapper">
                {isInputBlocked ? (
                  <MicOff size={48} strokeWidth={2.5} color="white" />
                ) : isListening && !isRecording ? (
                  <Mic size={48} strokeWidth={2.5} color="white" />
                ) : (
                  <Mic size={48} strokeWidth={2.5} color="white" />
                )}
              </div>
              {isInputBlocked && <div className="blocked-lock-ring"></div>}
            </button>

            {/* Camera/Video Button (Right) */}
            <button
              type="button"
              className={`side-action-btn camera-btn ${isRecording ? 'active-recording' : ''}`}
              onClick={handleVideoButtonClick}
              disabled={isInputBlocked && !isRecording}
              aria-label={isRecording ? "Stop recording" : "Record video"}
              title={isRecording ? "Stop and send video" : "Record and send a video"}
            >
              {isRecording ? <Square size={24} fill="currentColor" /> : <Video size={24} />}
            </button>
          </div>
        </div>

        {/* ── Conversation History Popup ── */}
        {showHistory && (
          <div className="history-overlay" onClick={() => setShowHistory(false)}>
            <div className="history-popup" onClick={e => e.stopPropagation()}>
              <div className="history-header">
                <h3>Conversation History</h3>
                <button className="history-close-btn" onClick={() => setShowHistory(false)}>
                  <X size={20} />
                </button>
              </div>
              <div className="history-body">
                {conversationHistory.length === 0 ? (
                  <div className="history-empty">
                    <MessageSquare size={40} strokeWidth={1.5} />
                    <p>No conversation yet.<br />Tap the mic to start talking!</p>
                  </div>
                ) : (
                  conversationHistory.map((msg, i) => (
                    <div key={i} className={`history-message ${msg.role}`}>
                      <div className="history-message-label">
                        {msg.role === 'user' ? 'You' : 'Aidelle'}
                      </div>
                      <div className="history-message-text">{msg.text}</div>
                      <div className="history-message-time">
                        {msg.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        )}

        {/* ── Camera Preview Overlay ── */}
        {showCameraPreview && (
          <div className="camera-overlay">
            <div className="camera-container">
              <div className="camera-header">
                <span className="camera-title">
                  <span className="rec-dot"></span>
                  Recording {formatTime(recordingTime)}
                </span>
                <button className="camera-close-btn" onClick={closeCamera}>
                  <X size={22} />
                </button>
              </div>

              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                className="camera-video"
              />

              <div className="camera-controls">
                <button className="record-btn recording" onClick={stopRecording}>
                  <div className="stop-square"></div>
                  <span>Stop & Send</span>
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
