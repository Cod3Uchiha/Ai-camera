# Cod3uchiha AI Camera

Native Kotlin Android camera prototype for Samsung Galaxy S22 Ultra (Snapdragon).

## Features
- CameraX rear-camera preview and still capture.
- On-device photo processing; no Internet permission or cloud upload.
- Optional Qualcomm ESRGAN TensorFlow Lite model import.
- GitHub Actions workflow to build a debug APK.

## Build
Open the project in Android Studio or push to GitHub and run **Actions → Build Android APK**.

## Model
Qualcomm AI Hub ESRGAN: https://aihub.qualcomm.com/mobile/models/esrgan

The current prototype expects a float32 TFLite model with input `[1,128,128,3]` and output `[1,512,512,3]`.

## Limitations
This is an initial prototype and has not yet been compiled or tested on the physical phone. CameraX exposes only the zoom range reported by the device; 100× cannot be guaranteed. TFLite CPU inference is used; Qualcomm QNN acceleration and multi-frame fusion are not yet integrated. AI super-resolution estimates detail and cannot guarantee recovery of information absent from the capture.
