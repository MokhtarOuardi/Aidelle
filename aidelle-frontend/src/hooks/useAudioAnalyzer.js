import { useEffect, useRef } from 'react';

/**
 * useAudioAnalyzer hook
 * Analyzes frequency and volume from an HTMLAudioElement.
 * Uses a ref instead of React state to avoid 60fps re-renders,
 * which causes severe lag in 3D lip-syncing.
 */
export const useAudioAnalyzer = (audioElement) => {
  const analyzerDataRef = useRef({ volume: 0, frequencies: new Uint8Array(0) });
  const analyserRef = useRef(null);
  const dataArrayRef = useRef(null);
  const sourceRef = useRef(null);
  const audioContextRef = useRef(null);
  const animationFrameRef = useRef(null);

  useEffect(() => {
    if (!audioElement) return;

    // Initialize AudioContext on first interaction or when element changes
    if (!audioContextRef.current) {
      audioContextRef.current = new (window.AudioContext || window.webkitAudioContext)();
    }

    const context = audioContextRef.current;

    // Ensure context is resumed since we already have user interaction by the time audio plays
    if (context.state === 'suspended') {
      context.resume().catch(e => console.error("Could not resume AudioContext:", e));
    }

    // Only create source once for the same audio element
    if (!sourceRef.current) {
      try {
        sourceRef.current = context.createMediaElementSource(audioElement);
        analyserRef.current = context.createAnalyser();
        analyserRef.current.fftSize = 512; // Higher resolution for better vowel detection
        analyserRef.current.smoothingTimeConstant = 0.5; // Smooth out the jitter

        const bufferLength = analyserRef.current.frequencyBinCount;
        dataArrayRef.current = new Uint8Array(bufferLength);

        sourceRef.current.connect(analyserRef.current);
        analyserRef.current.connect(context.destination);
      } catch (e) {
        console.warn("AudioContext setup error (usually already connected):", e);
      }
    }

    const update = () => {
      if (analyserRef.current && !audioElement.paused) {
        analyserRef.current.getByteFrequencyData(dataArrayRef.current);

        // Calculate raw volume (RMS or simple average)
        let sum = 0;
        for (let i = 0; i < dataArrayRef.current.length; i++) {
          sum += dataArrayRef.current[i];
        }
        const average = sum / dataArrayRef.current.length;
        const normalizedVolume = Math.min(1, average / 100); // Sensetive normalization

        // Update the ref directly instead of triggering a React render
        analyzerDataRef.current.volume = normalizedVolume;
        analyzerDataRef.current.frequencies = dataArrayRef.current;
        // if (normalizedVolume > 0.01) console.log("Analyzer:", normalizedVolume.toFixed(3));
      } else {
        analyzerDataRef.current.volume = 0;
      }
      animationFrameRef.current = requestAnimationFrame(update);
    };

    update();

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [audioElement]);

  return analyzerDataRef;
};
