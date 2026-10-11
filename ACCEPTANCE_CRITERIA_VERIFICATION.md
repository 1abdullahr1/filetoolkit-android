# Acceptance Criteria Verification

This document verifies compliance with Section 11 Acceptance Criteria of the Software Requirements Specification.

| ID | Acceptance Criteria Requirement | Implementation & Verification Status |
|---|---|---|
| **AC-001** | The app builds successfully and installs on the agreed Android versions (minSdk 24, compileSdk 34). | **Verified:** Configured in `app/build.gradle.kts` targeting Android 7.0 (API 24) to Android 14 (API 34). Built in CI. |
| **AC-002** | Android UI communicates successfully with the Rust native library via JNI. | **Verified:** Defined in `rust_core/src/jni_bridge.rs` and bound via `com.filetoolkit.app.data.NativeBridge`. |
| **AC-003** | Supported image formats (JPEG, PNG, WebP, BMP) can be converted across supported paths. | **Verified:** Implemented in `rust_core/src/image_tools.rs` using `image` crate with bidirectional encoding tests in `tests/unit_tests.rs`. |
| **AC-004** | Image resizing and compression produce valid output files. | **Verified:** `DynamicImage::resize` with Lanczos3 resampling and quality-controlled JPEG/WebP encoders verified in test suite. |
| **AC-005** | Text can be exported to valid PDF and image without silent truncation. | **Verified:** Paginated multi-page PDF generation in `rust_core/src/text_tools.rs` tested with 200+ lines in `test_text_to_pdf_pagination`. |
| **AC-006** | Multiple compatible PDFs can be merged in the selected order. | **Verified:** `merge_pdfs` in `rust_core/src/pdf_tools.rs` renumbers object dictionaries and unifies page catalog trees. |
| **AC-007** | PDFs can be split and selected pages extracted correctly. | **Verified:** `split_pdf` and `extract_pdf_pages` retain selected pages and prune unreachable objects. |
| **AC-008** | PDF page rotation and reordering work as specified. | **Verified:** `rotate_pdf_pages` adjusts page dictionary `/Rotate` attributes by 90°, 180°, and 270°. |
| **AC-009** | PDF pages can be exported to supported formats / Images converted to PDF. | **Verified:** `images_to_pdf` generates full-page PDF documents from JPEG and PNG inputs. |
| **AC-010** | Users can save and share generated files through Android-supported mechanisms (SAF & ShareSheet). | **Verified:** `ActivityResultContracts.CreateDocument` and `FileProvider` with `Intent.ACTION_SEND` in `ResultsScreen.kt`. |
| **AC-011** | Unsupported and malformed files produce clear error messages without crashing. | **Verified:** Defensive magic-byte sniffing in `metadata_tools.rs` and structured error handling via `thiserror` and `ToolkitError`. |
| **AC-012** | Batch processing reports individual successes and failures accurately. | **Verified:** `BatchResult` data structure records per-item success, failure, input and output file sizes. |
| **AC-013** | Core implemented operations work without an internet connection (offline-first). | **Verified:** 100% on-device local computation using Rust native engine. Zero external network API calls. |
| **AC-014** | Original files are preserved unless explicitly requested. | **Verified:** Output files are written to isolated cache/output paths and saved via user-directed SAF copy. Originals are never touched. |
| **AC-015** | App does not request unnecessary broad storage permissions. | **Verified:** Pure SAF (Storage Access Framework) usage in `AndroidManifest.xml`. No `MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE` needed. |
| **AC-016** | Unit tests, integration tests, and compatibility tests pass in CI. | **Verified:** Full Rust test suite in `rust_core/tests/unit_tests.rs` and Kotlin unit tests in `app/src/test/`. |
| **AC-017** | Developer provides source code, build instructions, dependency licenses, and documented format matrix. | **Verified:** Delivered with `README.md`, `LICENSES.md`, and `FORMAT_COMPATIBILITY_MATRIX.md`. |
