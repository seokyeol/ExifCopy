# ExifCopy

ExifCopy is an Android application designed to transfer EXIF metadata from a source image to a target image. It is particularly useful when photos have lost their original camera settings, timestamps, or GPS location due to post-processing, resizing, or editing in software that strips image metadata.

## Key Features

- In-place EXIF Transfer: Directly writes EXIF tags into the target image without re-encoding or degrading image quality.
- Comprehensive Metadata Support: Preserves camera settings (exposure time, aperture, ISO, focal length, white balance), GPS coordinates, timestamps, device profiles, and copyright info.
- Optional Orientation Exclusion: Allows excluding the orientation tag to avoid unintended rotations on edited images.
- Independent Directory Memory: Remembers the last opened directory separately for target and source pickers across application restarts.
- Automatic Source Matching by Timestamp: Scans a designated source folder (including subdirectories) to automatically identify and select the original photo matching the target image's timestamp.
- Storage Access Framework (SAF) Compliance: Fully adheres to Android scoped storage policies with read/write permission validation and asynchronous background operations.

## How to Use

1. Select Target Image: Choose the image that needs EXIF data restored. If the target contains a timestamp tag, it will be displayed on screen.
2. Select Source Image:
   - Option A (Manual): Tap "Select source image" to browse and pick a specific file.
   - Option B (Automatic): Tap "Auto-find source from folder" and select a directory. The application will scan and match the source file sharing the same capture timestamp.
3. Configure Options: Check or uncheck "Exclude image orientation tag" as needed.
4. Execute: Tap "Execute" to copy all supported EXIF metadata from the source image to the target image.

## Supported EXIF Tags

ExifCopy transfers a wide spectrum of standard EXIF tags, including:
- Capture and modification timestamps (`DateTimeOriginal`, `DateTime`, `DateTimeDigitized`, `OffsetTime`, `SubSecTime`)
- Exposure settings (`ExposureTime`, `FNumber`, `ISOSpeedRatings`, `ExposureBiasValue`, `ExposureMode`, `ExposureProgram`, `MeteringMode`)
- Lens and camera specifications (`Make`, `Model`, `FocalLength`, `FocalLengthIn35mmFilm`, `ApertureValue`, `MaxApertureValue`)
- GPS metadata (`GPSLatitude`, `GPSLongitude`, `GPSAltitude`, `GPSDateStamp`, `GPSTimeStamp`, and tracking coordinates)
- Color, white balance, and sensor details (`WhiteBalance`, `LightSource`, `Flash`, `SceneCaptureType`)

## Requirements

- Minimum Android Version: Android 7.0 (API Level 24)
- Target Android Version: Android 12L (API Level 32)
- Architecture: Compatible with all standard Android architectures (ARM, ARM64, x86, x86_64)

## Building from Source

To build the project from the command line:

1. Ensure Java JDK 11 and the Android SDK are installed.
2. Configure `local.properties` with the path to your Android SDK:
   ```properties
   sdk.dir=/path/to/android-sdk
   ```
3. Run the Gradle build command:
   - On Linux / macOS:
     ```bash
     ./gradlew assembleDebug
     ```
   - On Windows:
     ```powershell
     .\gradlew.bat assembleDebug
     ```
   The compiled APK will be located under `app/build/outputs/apk/debug/app-debug.apk`.
