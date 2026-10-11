# FileToolkit: All-in-One File Converter & PDF Toolkit for Android

[![CI Build](https://github.com/1abdullahr1/filetoolkit-android/actions/workflows/ci.yml/badge.svg)](https://github.com/1abdullahr1/filetoolkit-android/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSES.md)
[![Platform: Android](https://img.shields.io/badge/Platform-Android_7.0+-green.svg)](app/build.gradle.kts)
[![Engine: Rust](https://img.shields.io/badge/Engine-Rust_1.75+-orange.svg)](rust_core/Cargo.toml)

An offline-first, native Android application providing a comprehensive suite of file conversion, document processing, image manipulation, text export, and PDF utilities in a single unified interface.

---

## 1. Key Features

### 🖼️ Image Tools
- **Format Conversion:** Lossless and lossy conversion between **JPEG/JPG**, **PNG**, **WebP**, and **BMP**.
- **Resizing & Cropping:** Scale by dimensions or percentage while maintaining or unlocking aspect ratio.
- **Rotation:** High-speed 90°, 180°, and 270° orientation corrections.
- **Compression:** Dynamic quality control (1–100%) and automatic transparency flattening over custom or white backgrounds.
- **Metadata Protection:** Strip sensitive EXIF and geolocation data upon export.
- **Batch Processing:** Bulk conversion of multiple images with individualized success/failure metrics.
- **Images to PDF:** Combine collections of photos into clean, paginated PDF documents.

### 📄 PDF Toolkit
- **Merge PDFs:** Reorder and unify multiple PDF files into a single consolidated document.
- **Split PDFs:** Deconstruct documents by page numbers or page batches.
- **Extract Pages:** Extract individual or sets of pages into standalone PDFs.
- **Page Rotation:** Reorient upside-down or landscape pages.
- **Page Deletion:** Prune unwanted pages without damaging remaining catalog structures.
- **Compress PDF:** Recompress stream dictionaries and strip unreferenced objects.
- **Watermarking:** Add diagonal semi-transparent text watermarks.
- **Page Numbering:** Insert automated `"Page {n} of {total}"` footers.

### ✍️ Text-to-File Tools
- **Text to PDF:** Export entered or pasted text to multi-page PDFs with automatic pagination, custom line spacing, margins, and page sizes (A4 / US Letter).
- **Text to Image:** Render text onto PNG or JPEG image cards.

### 🛡️ Privacy & Security
- **100% Offline-First:** All transformations are executed locally in native Rust code on the device.
- **Scoped Storage & SAF:** Uses Android's Storage Access Framework (`CreateDocument`, `GetContent`) without requesting dangerous broad storage permissions.
- **Non-Destructive:** Original files are never modified or overwritten.

---

## 2. Technical Architecture

The application is structured into four distinct layers:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Android User Interface (Kotlin + Jetpack Compose)        │
│    - Material 3 Design (Light & Dark themes)                │
│    - Tool Directory, Live Progress Dialogs, Results & SAF   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 2. Kotlin Integration Layer (JNI & Storage)                 │
│    - NativeBridge JNI binding                               │
│    - Android ContentResolver URI streaming                  │
│    - Local History Persistence (SharedPreferences & JSON)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 3. Rust Native Processing Core (rust_core)                 │
│    - Format Sniffing & Magic Byte Detection                 │
│    - Bounded Memory Buffering & Structured Error Handling   │
│    - C-Dylib JNI Exports                                    │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 4. Native Processing Engines                                │
│    - image crate (JPEG, PNG, WebP, BMP)                     │
│    - lopdf crate (PDF Object Trees, Xrefs, Streams)         │
│    - flate2 crate (Deflate Stream Compression)              │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Project Structure

```
├── .github/workflows/ci.yml       # GitHub Actions CI build & test pipeline
├── app/                           # Android application module
│   ├── build.gradle.kts           # Compose, Android SDK, and JNI configs
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/filetoolkit/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── FileToolkitApp.kt
│       │   │   ├── data/
│       │   │   │   ├── Models.kt
│       │   │   │   ├── NativeBridge.kt
│       │   │   │   └── HistoryRepository.kt
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       ├── components/
│       │   │       └── screens/
│       │   └── res/
│       └── test/                  # Kotlin unit tests
├── rust_core/                     # Native Rust processing crate
│   ├── Cargo.toml
│   ├── src/
│   │   ├── lib.rs
│   │   ├── models.rs
│   │   ├── errors.rs
│   │   ├── jni_bridge.rs
│   │   ├── metadata_tools.rs
│   │   ├── image_tools.rs
│   │   ├── pdf_tools.rs
│   │   └── text_tools.rs
│   └── tests/
│       └── unit_tests.rs          # Pure-Rust unit & integration tests
├── FORMAT_COMPATIBILITY_MATRIX.md # Detailed format capabilities
├── LICENSES.md                    # Third-party licensing documentation
└── ACCEPTANCE_CRITERIA_VERIFICATION.md # Compliance mapping to AC-001 - AC-017
```

---

## 4. Building the Project

### Continuous Integration (CI)
The authoritative build environment runs on GitHub Actions (`.github/workflows/ci.yml`). Every commit builds the Rust test suite, cross-compiles native libraries for Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`), compiles the Android APK, and executes automated checks.

### Local Compilation (Optional)
If you have the Android SDK, NDK, and Rust toolchain installed:
```bash
# 1. Run Rust test suite
cd rust_core
cargo test

# 2. Build native libraries for Android (requires cargo-ndk)
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release

# 3. Build Android Debug APK
cd ..
./gradlew assembleDebug
```

---

## 5. Documentation
- [Format Compatibility Matrix](FORMAT_COMPATIBILITY_MATRIX.md)
- [Acceptance Criteria Verification](ACCEPTANCE_CRITERIA_VERIFICATION.md)
- [Licenses & Dependencies](LICENSES.md)
