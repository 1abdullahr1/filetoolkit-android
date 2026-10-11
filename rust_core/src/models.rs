use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileInfo {
    pub path: String,
    pub filename: String,
    pub format: String,
    pub mime_type: String,
    pub size_bytes: u64,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub page_count: Option<u32>,
    pub is_valid: bool,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImageConvertRequest {
    pub input_path: String,
    pub output_path: String,
    pub target_format: String, // "jpeg", "png", "webp", "bmp"
    pub quality: Option<u8>,   // 1 - 100 for JPEG/WebP
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub keep_aspect_ratio: Option<bool>,
    pub rotation_degrees: Option<i32>, // 0, 90, 180, 270
    pub crop_x: Option<u32>,
    pub crop_y: Option<u32>,
    pub crop_width: Option<u32>,
    pub crop_height: Option<u32>,
    pub strip_metadata: Option<bool>,
    pub bg_color_hex: Option<String>, // e.g. "#FFFFFF" for transparency flattening
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatchImageRequest {
    pub items: Vec<ImageConvertRequest>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OperationResult {
    pub success: bool,
    pub input_path: String,
    pub output_path: Option<String>,
    pub error_message: Option<String>,
    pub input_size_bytes: u64,
    pub output_size_bytes: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatchResult {
    pub total: usize,
    pub succeeded: usize,
    pub failed: usize,
    pub items: Vec<OperationResult>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfMergeRequest {
    pub input_paths: Vec<String>,
    pub output_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfSplitRequest {
    pub input_path: String,
    pub output_dir: String,
    pub pages_per_file: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfExtractPagesRequest {
    pub input_path: String,
    pub output_path: String,
    pub page_numbers: Vec<u32>, // 1-indexed
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfRotatePagesRequest {
    pub input_path: String,
    pub output_path: String,
    pub rotation_degrees: i32, // 90, 180, 270
    pub page_numbers: Option<Vec<u32>>, // None means all pages
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfDeletePagesRequest {
    pub input_path: String,
    pub output_path: String,
    pub page_numbers: Vec<u32>, // 1-indexed
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfWatermarkRequest {
    pub input_path: String,
    pub output_path: String,
    pub watermark_text: String,
    pub font_size: Option<f32>,
    pub opacity: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PdfPageNumberRequest {
    pub input_path: String,
    pub output_path: String,
    pub format: Option<String>, // e.g. "Page {n} of {total}"
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImagesToPdfRequest {
    pub input_paths: Vec<String>,
    pub output_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TextToPdfRequest {
    pub text: String,
    pub output_path: String,
    pub title: Option<String>,
    pub font_size: Option<f32>,
    pub line_spacing: Option<f32>,
    pub margin_pt: Option<f32>,
    pub page_size: Option<String>, // "A4", "LETTER"
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TextToImageRequest {
    pub text: String,
    pub output_path: String,
    pub target_format: String, // "png", "jpeg"
    pub font_size: Option<u32>,
    pub max_width: Option<u32>,
    pub bg_color_hex: Option<String>,
    pub text_color_hex: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FormatCapability {
    pub format: String,
    pub extension: String,
    pub can_read: bool,
    pub can_write: bool,
    pub category: String, // "Image", "Document", "PDF", "Text"
    pub description: String,
}
