# Format Compatibility Matrix

This document defines the supported file formats, verified processing capabilities, and operational limitations for **FileToolkit Android (v1.0)** as required by Section 7 of the Software Requirements Specification.

## 1. Matrix Overview

| Format | Category | Read / Input | Write / Output | Core Engine | Key Capabilities & Options |
|---|---|---|---|---|---|
| **JPEG / JPG** | Image | Supported | Supported | Rust `image` | Lossy compression, quality slider (1-100%), transparency flattening with custom/default background, EXIF stripping |
| **PNG** | Image | Supported | Supported | Rust `image` | Full 32-bit RGBA transparency support, lossless re-encoding, EXIF stripping |
| **WebP** | Image | Supported | Supported | Rust `image` | Modern high-efficiency web format, lossy & lossless modes, metadata stripping |
| **BMP** | Image | Supported | Supported | Rust `image` | Uncompressed bitmap format, RGB24 flattening |
| **PDF** | Document / PDF | Supported | Supported | Rust `lopdf` | Merge multiple PDFs, split documents, extract selected pages, rotate (90°/180°/270°), page deletion, stream compression, text watermarks, automated footer page numbering, images to PDF |
| **TXT** | Text | Supported | Supported | Rust `text_tools` | UTF-8 plain text import and multi-page paginated PDF export with custom margins & font size, text to PNG/JPEG rasterization |
| **HTML** | Document | Supported (Sniff/Inspect) | Planned (v1.1) | Rust sniffer | Content type detection and structure sniffing |
| **RTF** | Document | Supported (Sniff/Inspect) | Planned (v1.1) | Rust sniffer | Header inspection |
| **DOCX / ODT**| Document | Evaluated | Planned (v1.2) | N/A | Complex office layout engines deferred to future phase to preserve offline bounded memory guarantees |

---

## 2. Format Support Rules & Safeguards

1. **Magic Byte Sniffing (FR-004):** File types are identified through file header signatures (magic bytes) rather than relying exclusively on filenames or extensions.
2. **No Fake Conversions (FR-006, AC-014):** Files are never silently renamed without genuine decoding and re-encoding.
3. **Transparency Handling (FR-010):** When converting transparent PNG/WebP files into formats without alpha channel support (JPEG/BMP), the image is blended over a solid background color (default `#FFFFFF`).
4. **Pagination Guarantee (FR-029, AC-005):** Long text exported to PDF flows across pages without silent truncation.
5. **PDF Integrity Safeguard (FR-047):** PDF operations preserve existing xref streams, document fonts, and vector paths.
