import { useState, useCallback, useRef, useEffect } from 'react';
import { CambClient } from '@camb-ai/browser-sdk';

export const useVoice = () => {
    const [isListening, setIsListening] = useState(false);
    const [isSpeaking, setIsSpeaking] = useState(false);
    const [audioElement, setAudioElement] = useState(null);
    const [interimTranscript, setInterimTranscript] = useState('');
    const recognitionRef = useRef(null);
    const audioRef = useRef(null);

    // Initialize Camb Client
    const cambClient = useRef(null);
    if (!cambClient.current && import.meta.env.VITE_CAMB_API_KEY) {
        cambClient.current = new CambClient({
            apiKey: import.meta.env.VITE_CAMB_API_KEY
        });
    }

    // 1. Initialize Speech Recognition (STT) - Keeping native browser STT for now
    const startListening = useCallback((onResult, options = {}) => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
            alert("Speech recognition is not supported in this browser.");
            return;
        }

        const { continuous = false } = options;

        // Always create a fresh instance to avoid stale callback closures
        recognitionRef.current = new SpeechRecognition();
        recognitionRef.current.continuous = continuous;
        recognitionRef.current.interimResults = true;
        recognitionRef.current.lang = 'en-US';

        recognitionRef.current.onstart = () => {
            setIsListening(true);
            setInterimTranscript('');
        };
        recognitionRef.current.onend = () => {
            setIsListening(false);
        };
        recognitionRef.current.onresult = (event) => {
            let interim = '';
            let final = '';
            for (let i = 0; i < event.results.length; i++) {
                const result = event.results[i];
                if (result.isFinal) {
                    final += result[0].transcript;
                } else {
                    interim += result[0].transcript;
                }
            }
            
            // For continuous mode, we want to accumulate or just pass the full latest finalized transcript
            if (final) {
                setInterimTranscript(final);
                if (onResult) onResult(final);
            } else {
                setInterimTranscript(interim);
            }
        };

        recognitionRef.current.start();
    }, []);

    const stopListening = useCallback(() => {
        if (recognitionRef.current) {
            recognitionRef.current.stop();
        }
    }, []);

    // 2. Camb AI Speech Synthesis (TTS)
    const speak = useCallback(async (text, onReady, onEnd) => {
        try {
            // Cancel existing audio if playing
            if (audioRef.current) {
                audioRef.current.pause();
                audioRef.current.currentTime = 0;
            }

            // Generate speech using Camb AI MARS Flash
            const ttsResponse = await cambClient.current.textToSpeech.tts({
                text: text,
                voice_id: 170629, 
                language: "en-us",
                speech_model: "mars-flash",
                output_configuration: {
                    format: "wav"
                }
            });

            const blob = await ttsResponse.blob();
            const url = URL.createObjectURL(blob);

            const audio = new Audio(url);
            audioRef.current = audio;
            setAudioElement(audio);

            audio.onended = () => {
                setIsSpeaking(false);
                URL.revokeObjectURL(url);
                if (onEnd) onEnd();
            };

            audio.onerror = (e) => {
                console.error("Audio playback error:", e);
                setIsSpeaking(false);
            };

            // Audio is ready to play
            setIsSpeaking(true); // Now we are officially speaking
            if (onReady) onReady();
            await audio.play();
        } catch (error) {
            console.error("Camb AI TTS Error:", error);
            setIsSpeaking(false);
        }
    }, []);

    // Cleanup
    useEffect(() => {
        return () => {
            if (audioRef.current) {
                audioRef.current.pause();
            }
            if (recognitionRef.current) {
                recognitionRef.current.stop();
            }
        };
    }, []);

    return {
        startListening,
        stopListening,
        speak,
        isListening,
        isSpeaking,
        audioElement, // Exporting for the analyzer
        interimTranscript
    };
};
