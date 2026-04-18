import * as THREE from 'three';

const mixamoVRMRigMap = {
  mixamorigHips: 'hips',
  mixamorigSpine: 'spine',
  mixamorigSpine1: 'chest',
  mixamorigSpine2: 'upperChest',
  mixamorigNeck: 'neck',
  mixamorigHead: 'head',
  mixamorigLeftShoulder: 'leftShoulder',
  mixamorigLeftArm: 'leftUpperArm',
  mixamorigLeftForeArm: 'leftLowerArm',
  mixamorigLeftHand: 'leftHand',
  mixamorigRightShoulder: 'rightShoulder',
  mixamorigRightArm: 'rightUpperArm',
  mixamorigRightForeArm: 'rightLowerArm',
  mixamorigRightHand: 'rightHand',
  mixamorigLeftUpLeg: 'leftUpperLeg',
  mixamorigLeftLeg: 'leftLowerLeg',
  mixamorigLeftFoot: 'leftFoot',
  mixamorigLeftToeBase: 'leftToes',
  mixamorigRightUpLeg: 'rightUpperLeg',
  mixamorigRightLeg: 'rightLowerLeg',
  mixamorigRightFoot: 'rightFoot',
  mixamorigRightToeBase: 'rightToes',
};

/**
 * Robust Mixamo to VRM 1.0 Retargeting Utility
 * Implements axis swizzling and global rotation fixes.
 */
export function loadMixamoAnimation(fbx, vrm) {
  const clip = fbx.animations[0];
  if (!clip) return null;

  const tracks = [];
  const _q1 = new THREE.Quaternion();
  const _q2 = new THREE.Quaternion();
  const _v1 = new THREE.Vector3();

  // Root correction (180 deg around Y) to orient Mixamo to glTF/VRM
  const rootCorrection = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI);

  clip.tracks.forEach((track) => {
    const trackSplits = track.name.split('.');
    const mixamoBoneName = trackSplits[0];
    const propertyName = trackSplits[1];
    const vrmBoneName = mixamoVRMRigMap[mixamoBoneName];
    const vrmNode = vrm.humanoid.getNormalizedBoneNode(vrmBoneName);

    if (vrmNode) {
      if (propertyName === 'quaternion') {
        const times = track.times;
        const values = track.values;
        const newValues = new Float32Array(values.length);

        for (let i = 0; i < times.length; i++) {
          _q1.fromArray(values, i * 4);

          if (vrmBoneName === 'hips') {
            _q2.copy(_q1).premultiply(rootCorrection);
            _q2.toArray(newValues, i * 4);
          } else {
            _q1.toArray(newValues, i * 4);
          }
        }

        tracks.push(new THREE.QuaternionKeyframeTrack(`${vrmNode.name}.quaternion`, times, newValues));
      } else if (propertyName === 'position' && vrmBoneName === 'hips') {
        const times = track.times;
        const values = track.values;
        const newValues = new Float32Array(values.length);

        for (let i = 0; i < times.length; i++) {
          _v1.fromArray(values, i * 3);
          _v1.multiplyScalar(0.01);
          _v1.set(-_v1.x, _v1.y, -_v1.z);
          _v1.toArray(newValues, i * 3);
        }

        tracks.push(new THREE.VectorKeyframeTrack(`${vrmNode.name}.position`, times, newValues));
      }
    }
  });

  return new THREE.AnimationClip(clip.name, clip.duration, tracks);
}
