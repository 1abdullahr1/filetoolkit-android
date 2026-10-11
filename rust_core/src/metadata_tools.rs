use std::fs::File;
use std::io::Read;
use std::path::Path;
use crate::models::FileInfo;
use crate::errors::ToolkitError;

pub fn detect_format_from_bytes(bytes: &[u8]) -> (&'static str, &'static str) {
    if bytes.len() >= 8 && bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        ("PNG", "image/png")
    } else if bytes.len() >= 3 && bytes.starts_with(b"\xFF\xD8\xFF") {
        ("JPEG", "image/jpeg")
    } else if bytes.len() >= 12 && bytes.starts_with(b"RIFF") && &bytes[8..12] == b"WEBP" {
        ("WEBP", "image/webp")
    } else if bytes.len() >= 2 && bytes.starts_with(b"BM") {
        ("BMP", "image/bmp")
    } else if bytes.len() >= 4 && bytes.starts_with(b"%PDF") {
        ("PDF", "application/pdf")
    } else if bytes.len() >= 5 && (bytes.starts_with(b"<!DOC") || bytes.starts_with(b"<html") || bytes.starts_with(b"<!doc")) {
        ("HTML", "text/html")
    } else if bytes.len() >= 5 && bytes.starts_with(b"{\\rtf") {
        ("RTF", "application/rtf")
    } else if std::str::from_utf8(bytes).is_ok() {
        ("TXT", "text/plain")
    } else {
        ("UNKNOWN", "application/octet-stream")
    }
}

pub fn inspect_file(file_path: &str) -> FileInfo {
    let path = Path::new(file_path);
    let filename = path.file_name()
        .map(|f| f.to_string_lossy().to_string())
        .unwrap_or_else(|| "unknown".to_string());

    let metadata = match std::fs::metadata(path) {
        Ok(m) => m,
        Err(e) => {
            return FileInfo {
                path: file_path.to_string(),
                filename,
                format: "UNKNOWN".to_string(),
                mime_type: "application/octet-stream".to_string(),
                size_bytes: 0,
                width: None,
                height: None,
                page_count: None,
                is_valid: false,
                error: Some(format!("Could not read file metadata: {}", e)),
            };
        }
    };

    let size_bytes = metadata.len();
    if size_bytes == 0 {
        return FileInfo {
            path: file_path.to_string(),
            filename,
            format: "EMPTY".to_string(),
            mime_type: "application/octet-stream".to_string(),
            size_bytes: 0,
            width: None,
            height: None,
            page_count: None,
            is_valid: false,
            error: Some("File is empty (0 bytes)".to_string()),
        };
    }

    let mut header = vec![0u8; 512.min(size_bytes as usize)];
    let (detected_fmt, mime) = match File::open(path) {
        Ok(mut f) => {
            let read_len = f.read(&mut header).unwrap_or(0);
            detect_format_from_bytes(&header[..read_len])
        }
        Err(e) => {
            return FileInfo {
                path: file_path.to_string(),
                filename,
                format: "UNKNOWN".to_string(),
                mime_type: "application/octet-stream".to_string(),
                size_bytes,
                width: None,
                height: None,
                page_count: None,
                is_valid: false,
                error: Some(e.to_string()),
            };
        }
    };

    let mut width = None;
    let mut height = None;
    let mut page_count = None;
    let mut is_valid = true;
    let mut error_msg = None;

    if matches!(detected_fmt, "PNG" | "JPEG" | "WEBP" | "BMP") {
        match image::image_dimensions(path) {
            Ok((w, h)) => {
                width = Some(w);
                height = Some(h);
            }
            Err(e) => {
                is_valid = false;
                error_msg = Some(format!("Image decode failed: {}", e));
            }
        }
    } else if detected_fmt == "PDF" {
        match lopdf::Document::load(path) {
            Ok(doc) => {
                page_count = Some(doc.get_pages().len() as u32);
            }
            Err(e) => {
                is_valid = false;
                error_msg = Some(format!("PDF parse failed: {}", e));
            }
        }
    }

    FileInfo {
        path: file_path.to_string(),
        filename,
        format: detected_fmt.to_string(),
        mime_type: mime.to_string(),
        size_bytes,
        width,
        height,
        page_count,
        is_valid,
        error: error_msg,
    }
}
