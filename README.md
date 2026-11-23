# Cod3uchiha AI Camera

Native Kotlin Android camera app targeting the Samsung Galaxy S22 Ultra Snapdragon.

## Features
- CameraX preview and high-quality still capture using the device camera HAL.
- Camera-reported zoom range; the Samsung camera HAL can switch supported physical rear lenses.
- Flash control.
- Automatic first-launch download of Qualcomm ESRGAN from its pinned Hugging Face revision.
- SHA-256 verification before installing the model.
- Automatic on-device AI super-resolution after capture.
- Photos saved to `Pictures/Cod3uchiha AI Camera`.

## Model download and privacy
The app downloads the approximately 67 MB Qualcomm ESRGAN TFLite model once, on first launch, and stores it in private app storage. A network connection is required for that initial download. The model is then used locally; captured photos are not uploaded. If the download fails, use the in-app retry action.

Model source: https://huggingface.co/qualcomm/ESRGAN
Pinned model file: https://huggingface.co/qualcomm/ESRGAN/resolve/4000626f7488977ec4273928c6b037f2fd3181e3/ESRGAN.tflite
License: Apache-2.0.

## Samsung camera compatibility
This is a native camera implementation using Android CameraX/Camera2 underneath. It is not a repackaged Samsung Camera APK: Samsung's stock camera app depends on proprietary Samsung framework components and vendor integrations that cannot be transplanted reliably into an ordinary app. Camera behavior and available lenses are supplied by the phone's camera HAL.

## Build
Open Android Studio or use GitHub Actions → Build Android APK.

## Limitations
The app has not yet been physically tested on the S22 Ultra. CameraX exposes only capabilities supported by the installed firmware. ESRGAN is single-frame super-resolution: it can synthesize plausible high-frequency detail but cannot recover scene information that was never captured. This version uses TensorFlow Lite CPU execution; Samsung's stock multi-frame processing and Qualcomm QNN/NPU acceleration are not yet integrated.
