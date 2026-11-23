# Cod3uchiha AI Camera

Native Kotlin Android camera prototype for Samsung Galaxy S22 Ultra (Snapdragon).

## Features
- CameraX rear-camera preview and still capture.
- On-device photo processing; no Internet permission or cloud upload.
- Optional Qualcomm ESRGAN TensorFlow Lite model import.
- GitHub Actions workflow builds a debug APK on pushes to main.

## Build
Open Android Studio or use GitHub Actions → Build Android APK.

## Model
Qualcomm AI Hub ESRGAN: https://aihub.qualcomm.com/mobile/models/esrgan
Expected float32 TFLite input `[1,128,128,3]`, output `[1,512,512,3]`.

## Limitations
Prototype not yet device-tested. Zoom is limited to the range exposed by CameraX; 100× is not guaranteed. TFLite CPU inference is used; QNN acceleration and multi-frame fusion are not integrated. AI super-resolution estimates plausible detail and cannot guarantee recovery of information absent from the capture.
