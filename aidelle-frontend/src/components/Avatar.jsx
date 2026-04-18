import * as THREE from 'three';
import React, { useMemo, useEffect, useRef, useState } from 'react';
import { useLoader, useFrame } from '@react-three/fiber';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';
import { VRMLoaderPlugin } from '@pixiv/three-vrm';
import { VRMAnimationLoaderPlugin, createVRMAnimationClip, VRMLookAtQuaternionProxy } from '@pixiv/three-vrm-animation';
import { useAudioAnalyzer } from '../hooks/useAudioAnalyzer';

const Avatar = ({ url = '/assistant.vrm', animationState = 'idle', speaking = false, onAnimationFinished, audioElement }) => {
  const [clips, setClips] = useState({});
  const mixerRef = useRef(null);
  const actionsRef = useRef({});
  const currentActionNameRef = useRef(null);
  const blinkRef = useRef(0);
  const breathRef = useRef(0);
  const [targetExpression, setTargetExpression] = useState('neutral');

  // 0. Expression Cycling (Idling Variety)
  useEffect(() => {
    if (animationState === 'idle' && !speaking) {
      const cycleExpression = () => {
        const others = ['happy', 'neutral'];
        const next = others[Math.floor(Math.random() * others.length)];
        setTargetExpression(next);
      };
      const interval = setInterval(cycleExpression, 2000);
      return () => clearInterval(interval);
    } else {
      setTargetExpression('neutral');
    }
  }, [animationState, speaking]);

  // 0. Audio Analysis
  const analyzerDataRef = useAudioAnalyzer(audioElement);

  // 1. Load the Model
  const gltf = useLoader(GLTFLoader, url, (loader) => {
    loader.register((parser) => new VRMLoaderPlugin(parser));
  });

  const vrm = useMemo(() => {
    if (gltf.userData.vrm) {
      const v = gltf.userData.vrm;
      // Early attachment to suppress library warnings during clip creation
      if (v.lookAt && !v.lookAt.quaternionProxy) {
        v.lookAt.quaternionProxy = new VRMLookAtQuaternionProxy();
        v.lookAt.quaternionProxy.name = 'lookAtQuaternionProxy';
      }
      return v;
    }
    return null;
  }, [gltf]);

  useEffect(() => {
    if (vrm) {
      vrm.lookAt.enabled = true;

      // Setup LookAt proxy early to suppress library warnings during animation creation
      if (vrm.lookAt && !vrm.lookAt.quaternionProxy) {
        const proxy = new VRMLookAtQuaternionProxy();
        proxy.name = 'lookAtQuaternionProxy';
        vrm.lookAt.quaternionProxy = proxy;
      }
    }
  }, [vrm]);

  // 2. Load VRMA Animations
  const vrmaLoader = useMemo(() => {
    const loader = new GLTFLoader();
    loader.register((parser) => new VRMAnimationLoaderPlugin(parser));
    return loader;
  }, []);

  useEffect(() => {
    const loadAnimations = async () => {
      if (!vrm) return;

      // Setup LookAt proxy to suppress library warnings
      if (vrm.lookAt && !vrm.lookAt.quaternionProxy) {
        vrm.lookAt.quaternionProxy = new VRMLookAtQuaternionProxy();
        vrm.lookAt.quaternionProxy.name = 'lookAtQuaternionProxy';
      }

      const animationFiles = {
        idle: '/animation/Idle.vrma',
        talking: '/animation/Talking.vrma',
        thinking: '/animation/Thinking.vrma',
        waving: '/animation/Waving.vrma',
        nodding: '/animation/Head Nod Yes.vrma'
      };

      const loadedClips = {};

      for (const [name, path] of Object.entries(animationFiles)) {
        try {
          // Double check LookAt proxy before creating each clip to suppress warnings
          if (vrm.lookAt && !vrm.lookAt.quaternionProxy) {
            vrm.lookAt.quaternionProxy = new VRMLookAtQuaternionProxy();
          }

          const vrmaGltf = await vrmaLoader.loadAsync(path);
          const vrmAnimation = vrmaGltf.userData.vrmAnimations[0];
          if (vrmAnimation) {
            const clip = createVRMAnimationClip(vrmAnimation, vrm);
            clip.name = name;
            loadedClips[name] = clip;
          }
        } catch (e) {
          console.error(`Failed to load animation ${name}:`, e);
        }
      }

      setClips(loadedClips);
    };

    loadAnimations();
  }, [vrm, vrmaLoader]);

  const finishedCallbackRef = useRef(onAnimationFinished);
  useEffect(() => { finishedCallbackRef.current = onAnimationFinished; }, [onAnimationFinished]);

  // 3. Setup Mixer
  useEffect(() => {
    if (vrm && Object.keys(clips).length > 0) {
      mixerRef.current = new THREE.AnimationMixer(vrm.scene);

      Object.entries(clips).forEach(([name, clip]) => {
        const action = mixerRef.current.clipAction(clip);
        if (name === 'waving' || name === 'nodding') {
          action.setLoop(THREE.LoopOnce);
          action.clampWhenFinished = true;
        }
        actionsRef.current[name] = action;
      });

      const handleFinished = () => {
        if (finishedCallbackRef.current) finishedCallbackRef.current();
      };
      mixerRef.current.addEventListener('finished', handleFinished);

      // Initial State Trigger
      const initialAction = actionsRef.current[animationState] || actionsRef.current['idle'];
      if (initialAction) {
        initialAction.play();
        currentActionNameRef.current = animationState;
      }

      return () => {
        mixerRef.current?.removeEventListener('finished', handleFinished);
        mixerRef.current?.stopAllAction();
      };
    }
  }, [vrm, clips]); // onAnimationFinished is NOT a dependency anymore

  // 4. Handle State Transitions (Crossfade)
  useEffect(() => {
    const mixer = mixerRef.current;
    if (!mixer || !actionsRef.current) return;

    const nextAction = actionsRef.current[animationState];
    const prevActionName = currentActionNameRef.current;

    if (nextAction && prevActionName !== animationState) {
      const prevAction = actionsRef.current[prevActionName];

      // Determine fade duration: Talking to Idle needs more time
      let fadeDuration = 0.5;
      if (prevActionName === 'talking' && animationState === 'idle') fadeDuration = 0.8;
      if (animationState === 'waving') fadeDuration = 0.3; // Snappier start for greeting

      nextAction.reset();
      nextAction.setEffectiveTimeScale(1);
      nextAction.setEffectiveWeight(1);
      nextAction.fadeIn(fadeDuration);
      nextAction.play();

      if (prevAction) {
        prevAction.fadeOut(fadeDuration);
      }

      currentActionNameRef.current = animationState;
    }
  }, [animationState]);

  useFrame((state, delta) => {
    if (!vrm) return;

    // 1. Update Mixer (animations)
    mixerRef.current?.update(delta);

    // 2. Set LookAt Target
    vrm.lookAt.target = state.camera;

    // 3. Set Procedural Expressions & Procedural Polish
    blinkRef.current += delta;
    breathRef.current += delta;

    const breathScale = 1.0 + Math.sin(breathRef.current * 1.5) * 0.001;
    vrm.scene.scale.set(breathScale, breathScale, breathScale);

    // Only blink if we are in a neutral expression state
    if (targetExpression === 'neutral') {
      vrm.expressionManager.setValue('blink', Math.sin(blinkRef.current * 0.5) > 0.98 ? 1.0 : 0.0);
    } else {
      vrm.expressionManager.setValue('blink', 0);
    }

    // Smoothly transition between idle expressions
    const idleExpressions = ['happy', 'neutral'];
    idleExpressions.forEach(name => {
      const current = vrm.expressionManager.getValue(name) || 0;
      const target = (targetExpression === name) ? 1.0 : 0.0; // 0.7 for subtle natural look
      if (Math.abs(current - target) > 0.01) {
        vrm.expressionManager.setValue(name, THREE.MathUtils.lerp(current, target, delta * 1.5));
      }
    });

    if (speaking) {
      // Procedural Lip Sync (Guaranteed to work, bypasses AudioContext policies)
      const t = state.clock.getElapsedTime();
      const talkingSpeed = 16;

      // Combine two waves to simulate varied syllables
      const wave1 = (Math.sin(t * talkingSpeed) + 1) / 2;
      const wave2 = (Math.sin(t * talkingSpeed * 0.6 + 2.0) + 1) / 2;

      let openAmount = wave1 * 0.7 + wave2 * 0.3;

      // Make it snappy like real speech, not floaty
      if (openAmount < 0.2) openAmount = 0;
      else openAmount = (openAmount - 0.2) * 1.25;

      vrm.expressionManager.setValue('aa', Math.min(1.0, openAmount));
      // Occasionally trigger the 'ih' shape on wide peaks for variety
      vrm.expressionManager.setValue('ih', openAmount > 0.7 ? 0.3 : 0);
      vrm.expressionManager.setValue('ou', 0);
    } else {
      // Reset safely without locking the expressions every frame
      const currentAa = vrm.expressionManager.getValue('aa');
      if (currentAa && currentAa > 0.01) {
        vrm.expressionManager.setValue('aa', Math.max(0, currentAa - delta * 8));
        vrm.expressionManager.setValue('ih', 0);
        vrm.expressionManager.setValue('ou', 0);
      }
    }

    // 4. Update VRM LAST (Calculates IK, LookAt, and applies expressions)
    vrm.update(delta);
  });

  return <primitive object={vrm.scene} />;
};

export default Avatar;
