# Third-Party Dependencies and Licensing

All dependencies utilized in **FileToolkit Android** have been vetted for compatibility with permissive open-source redistribution as required by NFR-023.

---

## 1. Rust Core Dependencies (`rust_core`)

| Crate | Version | License | Purpose |
|---|---|---|---|
| `image` | 0.25 | MIT / Apache-2.0 | Pure-Rust raster image decoding, encoding, transformations, and resizing |
| `lopdf` | 0.33 | MIT | Pure-Rust PDF document manipulation, merging, splitting, extraction, and object tree editing |
| `flate2` | 1.0 | MIT / Apache-2.0 | High-performance DEFLATE compression for PDF streams |
| `jni` | 0.21.1 | MIT / Apache-2.0 | Safe JNI bindings between Rust core and Kotlin JVM runtime |
| `serde` & `serde_json` | 1.0 | MIT / Apache-2.0 | Zero-allocation JSON serialization for JNI request/response messaging |
| `thiserror` | 1.0 | MIT / Apache-2.0 | Structured error definitions and ergonomic error propagation |

---

## 2. Android Kotlin Dependencies (`app`)

| Library | License | Purpose |
|---|---|---|
| Jetpack Compose BOM | Apache-2.0 | Declarative modern Android UI framework |
| Material 3 (`androidx.compose.material3`) | Apache-2.0 | Material Design 3 design system with dynamic light/dark theming |
| Navigation Compose | Apache-2.0 | In-app navigation stack and screen transitions |
| Kotlinx Coroutines | Apache-2.0 | Asynchronous IO thread dispatching for native calls |
| Gson | Apache-2.0 | Data transfer object serialization |
| AndroidX Core & Lifecycle | Apache-2.0 | Android OS integration, SAF picker, and Lifecycle management |

---

## 3. License Notice

The FileToolkit project is licensed under the MIT License. All incorporated third-party open-source components are distributed under permissive Apache-2.0 or MIT licenses without GPL copyleft restrictions.
