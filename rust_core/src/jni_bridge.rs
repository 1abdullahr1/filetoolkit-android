use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

use crate::image_tools::{convert_image, process_batch_images};
use crate::metadata_tools::inspect_file;
use crate::models::{
    BatchImageRequest, FormatCapability, ImageConvertRequest, ImagesToPdfRequest,
    OperationResult, PdfDeletePagesRequest, PdfExtractPagesRequest, PdfMergeRequest,
    PdfPageNumberRequest, PdfRotatePagesRequest, PdfSplitRequest, PdfWatermarkRequest,
    TextToImageRequest, TextToPdfRequest,
};
use crate::pdf_tools::{
    add_page_numbers, compress_pdf, delete_pdf_pages, extract_pdf_pages, images_to_pdf,
    merge_pdfs, rotate_pdf_pages, split_pdf, watermark_pdf,
};
use crate::text_tools::{text_to_image, text_to_pdf};

fn make_jstring<'a>(mut env: JNIEnv<'a>, text: &str) -> jstring {
    env.new_string(text).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn get_string_utf(env: &mut JNIEnv, j_str: &JString) -> Result<String, String> {
    env.get_string(j_str)
        .map(|s| s.into())
        .map_err(|e| format!("JNI string error: {}", e))
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_getCoreVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    make_jstring(env, "1.0.0 (Rust Native Core)")
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_inspectFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let path_str = match get_string_utf(&mut env, &path) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"error": "{}"}}"#, e)),
    };

    let info = inspect_file(&path_str);
    let json = serde_json::to_string(&info).unwrap_or_else(|e| format!(r#"{{"error": "{}"}}"#, e));
    make_jstring(env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_convertImage<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: ImageConvertRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match convert_image(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_processBatchImages<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"total": 0, "succeeded": 0, "failed": 0, "items": []}}"#)),
    };

    let req: BatchImageRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(_) => return make_jstring(env, &format!(r#"{{"total": 0, "succeeded": 0, "failed": 0, "items": []}}"#)),
    };

    let batch_res = process_batch_images(&req);
    let out_json = serde_json::to_string(&batch_res).unwrap_or_else(|_| "{}".to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_mergePdfs<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: PdfMergeRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match merge_pdfs(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_paths.get(0).cloned().unwrap_or_default(),
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_splitPdf<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"[{{"success": false, "error_message": "{}"}}]"#, e)),
    };

    let req: PdfSplitRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"[{{"success": false, "error_message": "Invalid JSON: {}"}}]"#, e)),
    };

    let results = match split_pdf(&req) {
        Ok(res) => res,
        Err(e) => vec![OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        }],
    };

    let out_json = serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_extractPdfPages<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: PdfExtractPagesRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match extract_pdf_pages(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_rotatePdfPages<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: PdfRotatePagesRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match rotate_pdf_pages(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_deletePdfPages<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: PdfDeletePagesRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match delete_pdf_pages(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_compressPdf<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    in_path: JString<'local>,
    out_path: JString<'local>,
) -> jstring {
    let in_str = match get_string_utf(&mut env, &in_path) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };
    let out_str = match get_string_utf(&mut env, &out_path) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let result = match compress_pdf(&in_str, &out_str) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: in_str,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_watermarkPdf<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: PdfWatermarkRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match watermark_pdf(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_addPageNumbersPdf<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: PdfPageNumberRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match add_page_numbers(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_path,
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_imagesToPdf<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: ImagesToPdfRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match images_to_pdf(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: req.input_paths.get(0).cloned().unwrap_or_default(),
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_textToPdf<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: TextToPdfRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match text_to_pdf(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: "text_buffer".to_string(),
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_textToImage<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    req_json: JString<'local>,
) -> jstring {
    let json_str = match get_string_utf(&mut env, &req_json) {
        Ok(s) => s,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "{}"}}"#, e)),
    };

    let req: TextToImageRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => return make_jstring(env, &format!(r#"{{"success": false, "error_message": "Invalid JSON: {}"}}"#, e)),
    };

    let result = match text_to_image(&req) {
        Ok(res) => res,
        Err(e) => OperationResult {
            success: false,
            input_path: "text_buffer".to_string(),
            output_path: None,
            error_message: Some(e.to_string()),
            input_size_bytes: 0,
            output_size_bytes: 0,
        },
    };

    let out_json = serde_json::to_string(&result).unwrap_or_else(|_| r#"{"success": false}"#.to_string());
    make_jstring(env, &out_json)
}

#[no_mangle]
pub extern "system" fn Java_com_filetoolkit_app_data_NativeBridge_getFormatMatrix<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let matrix = vec![
        FormatCapability {
            format: "PNG".to_string(),
            extension: "png".to_string(),
            can_read: true,
            can_write: true,
            category: "Image".to_string(),
            description: "Lossless raster image with transparency support".to_string(),
        },
        FormatCapability {
            format: "JPEG".to_string(),
            extension: "jpg".to_string(),
            can_read: true,
            can_write: true,
            category: "Image".to_string(),
            description: "Lossy photo compression with customizable quality".to_string(),
        },
        FormatCapability {
            format: "WEBP".to_string(),
            extension: "webp".to_string(),
            can_read: true,
            can_write: true,
            category: "Image".to_string(),
            description: "Modern web format with high compression efficiency".to_string(),
        },
        FormatCapability {
            format: "BMP".to_string(),
            extension: "bmp".to_string(),
            can_read: true,
            can_write: true,
            category: "Image".to_string(),
            description: "Uncompressed bitmap format".to_string(),
        },
        FormatCapability {
            format: "PDF".to_string(),
            extension: "pdf".to_string(),
            can_read: true,
            can_write: true,
            category: "Document".to_string(),
            description: "Portable Document Format (merge, split, rotate, text, watermark)".to_string(),
        },
        FormatCapability {
            format: "TXT".to_string(),
            extension: "txt".to_string(),
            can_read: true,
            can_write: true,
            category: "Text".to_string(),
            description: "Plain UTF-8 text export and import".to_string(),
        },
        FormatCapability {
            format: "HTML".to_string(),
            extension: "html".to_string(),
            can_read: true,
            can_write: true,
            category: "Document".to_string(),
            description: "HyperText Markup document structure".to_string(),
        },
        FormatCapability {
            format: "RTF".to_string(),
            extension: "rtf".to_string(),
            can_read: true,
            can_write: false,
            category: "Document".to_string(),
            description: "Rich Text Format inspection".to_string(),
        },
    ];

    let json = serde_json::to_string(&matrix).unwrap_or_else(|_| "[]".to_string());
    make_jstring(env, &json)
}
