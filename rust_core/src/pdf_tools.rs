use std::collections::BTreeMap;
use std::fs::File;
use std::io::{BufWriter, Read, Write};
use std::path::Path;
use lopdf::{Document, Object, ObjectId, StringFormat};
use flate2::write::ZlibEncoder;
use flate2::Compression;
use crate::errors::ToolkitError;
use crate::models::{
    ImagesToPdfRequest, OperationResult, PdfDeletePagesRequest, PdfExtractPagesRequest,
    PdfMergeRequest, PdfPageNumberRequest, PdfRotatePagesRequest, PdfSplitRequest,
    PdfWatermarkRequest,
};

pub fn merge_pdfs(req: &PdfMergeRequest) -> Result<OperationResult, ToolkitError> {
    if req.input_paths.is_empty() {
        return Err(ToolkitError::InvalidParameter("No input PDFs provided for merge".to_string()));
    }

    let mut total_in_size: u64 = 0;
    for p in &req.input_paths {
        let meta = std::fs::metadata(p).map_err(|e| ToolkitError::FileNotFound(format!("{}: {}", p, e)))?;
        total_in_size += meta.len();
    }

    let mut target_doc = Document::load(&req.input_paths[0])
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load '{}': {}", req.input_paths[0], e)))?;

    for path in &req.input_paths[1..] {
        let mut doc_to_merge = Document::load(path)
            .map_err(|e| ToolkitError::PdfError(format!("Failed to load '{}': {}", path, e)))?;

        let offset = target_doc.max_id + 1;
        doc_to_merge.renumber_objects_with(offset);

        let target_catalog_id = target_doc.trailer.get(b"Root")
            .and_then(|o| o.as_reference())
            .map_err(|e| ToolkitError::PdfError(format!("Invalid catalog: {}", e)))?;

        let target_pages_id = target_doc.get_object(target_catalog_id)
            .and_then(|o| o.as_dict())
            .and_then(|d| d.get(b"Pages"))
            .and_then(|o| o.as_reference())
            .map_err(|e| ToolkitError::PdfError(format!("Invalid pages dict: {}", e)))?;

        let doc_pages = doc_to_merge.get_pages();
        let new_page_ids: Vec<ObjectId> = doc_pages.values().copied().collect();

        // Update each new page's Parent to target_pages_id
        for &page_id in &new_page_ids {
            if let Ok(page_dict) = doc_to_merge.get_object_mut(page_id).and_then(|o| o.as_dict_mut()) {
                page_dict.set("Parent", Object::Reference(target_pages_id));
            }
        }

        // Copy all objects from doc_to_merge into target_doc
        for (id, obj) in doc_to_merge.objects {
            target_doc.objects.insert(id, obj);
        }

        // Append new page IDs to target_doc's Pages dict
        if let Ok(pages_dict) = target_doc.get_object_mut(target_pages_id).and_then(|o| o.as_dict_mut()) {
            let mut existing_kids: Vec<Object> = match pages_dict.get(b"Kids") {
                Ok(Object::Array(ref arr)) => arr.clone(),
                _ => Vec::new(),
            };
            for page_id in new_page_ids {
                existing_kids.push(Object::Reference(page_id));
            }
            let total_count = existing_kids.len() as i64;
            pages_dict.set("Kids", Object::Array(existing_kids));
            pages_dict.set("Count", Object::Integer(total_count));
        }

        target_doc.max_id = offset + doc_to_merge.max_id;
    }

    target_doc.trailer.set("Size", Object::Integer((target_doc.max_id + 1) as i64));
    target_doc.prune_objects();

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    target_doc.save(&req.output_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to save merged PDF: {}", e)))?;

    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_paths[0].clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: total_in_size,
        output_size_bytes: out_size,
    })
}

pub fn split_pdf(req: &PdfSplitRequest) -> Result<Vec<OperationResult>, ToolkitError> {
    let doc = Document::load(&req.input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let total_pages = doc.get_pages().len() as u32;
    if total_pages == 0 {
        return Err(ToolkitError::EmptyDocument);
    }

    let pages_per_file = req.pages_per_file.unwrap_or(1).max(1);
    let in_size = std::fs::metadata(&req.input_path)?.len();
    let out_dir = Path::new(&req.output_dir);
    if !out_dir.exists() {
        std::fs::create_dir_all(out_dir)?;
    }

    let in_stem = Path::new(&req.input_path)
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|| "document".to_string());

    let mut results = Vec::new();
    let mut part = 1;

    for start_page in (1..=total_pages).step_by(pages_per_file as usize) {
        let end_page = (start_page + pages_per_file - 1).min(total_pages);
        let out_filename = format!("{}_part{}_pages_{}-{}.pdf", in_stem, part, start_page, end_page);
        let out_path = out_dir.join(&out_filename).to_string_lossy().to_string();

        let mut sub_doc = Document::load(&req.input_path)?;
        let to_delete: Vec<u32> = (1..=total_pages)
            .filter(|p| *p < start_page || *p > end_page)
            .collect();

        sub_doc.delete_pages(&to_delete);
        sub_doc.prune_objects();
        sub_doc.save(&out_path)?;

        let out_size = std::fs::metadata(&out_path)?.len();
        results.push(OperationResult {
            success: true,
            input_path: req.input_path.clone(),
            output_path: Some(out_path),
            error_message: None,
            input_size_bytes: in_size,
            output_size_bytes: out_size,
        });

        part += 1;
    }

    Ok(results)
}

pub fn extract_pdf_pages(req: &PdfExtractPagesRequest) -> Result<OperationResult, ToolkitError> {
    let mut doc = Document::load(&req.input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let total_pages = doc.get_pages().len() as u32;
    let in_size = std::fs::metadata(&req.input_path)?.len();

    let keep_set: std::collections::HashSet<u32> = req.page_numbers.iter().copied().collect();
    let to_delete: Vec<u32> = (1..=total_pages)
        .filter(|p| !keep_set.contains(p))
        .collect();

    if to_delete.len() as u32 >= total_pages {
        return Err(ToolkitError::InvalidParameter("Cannot delete all pages from PDF".to_string()));
    }

    doc.delete_pages(&to_delete);
    doc.prune_objects();

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(&req.output_path)?;
    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_path.clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn rotate_pdf_pages(req: &PdfRotatePagesRequest) -> Result<OperationResult, ToolkitError> {
    let mut doc = Document::load(&req.input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let in_size = std::fs::metadata(&req.input_path)?.len();
    let pages = doc.get_pages();
    let target_pages: std::collections::HashSet<u32> = req.page_numbers
        .as_ref()
        .map(|nums| nums.iter().copied().collect())
        .unwrap_or_else(|| pages.keys().copied().collect());

    let norm_deg = ((req.rotation_degrees % 360) + 360) % 360;

    for (page_num, page_id) in pages {
        if target_pages.contains(&page_num) {
            if let Ok(page_dict) = doc.get_object_mut(page_id).and_then(|o| o.as_dict_mut()) {
                let current_rot = page_dict.get(b"Rotate")
                    .and_then(|o| o.as_i64())
                    .unwrap_or(0);
                let new_rot = (current_rot + norm_deg as i64) % 360;
                page_dict.set("Rotate", Object::Integer(new_rot));
            }
        }
    }

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(&req.output_path)?;
    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_path.clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn delete_pdf_pages(req: &PdfDeletePagesRequest) -> Result<OperationResult, ToolkitError> {
    let mut doc = Document::load(&req.input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let in_size = std::fs::metadata(&req.input_path)?.len();
    let total_pages = doc.get_pages().len() as u32;

    if req.page_numbers.len() as u32 >= total_pages {
        return Err(ToolkitError::InvalidParameter("Cannot delete all pages".to_string()));
    }

    doc.delete_pages(&req.page_numbers);
    doc.prune_objects();

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(&req.output_path)?;
    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_path.clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn compress_pdf(input_path: &str, output_path: &str) -> Result<OperationResult, ToolkitError> {
    let mut doc = Document::load(input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let in_size = std::fs::metadata(input_path)?.len();

    // Compress all streams using flate
    doc.compress();
    doc.prune_objects();

    let out_path = Path::new(output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(output_path)?;
    let out_size = std::fs::metadata(output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: input_path.to_string(),
        output_path: Some(output_path.to_string()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn watermark_pdf(req: &PdfWatermarkRequest) -> Result<OperationResult, ToolkitError> {
    let mut doc = Document::load(&req.input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let in_size = std::fs::metadata(&req.input_path)?.len();
    let pages = doc.get_pages();
    let text = &req.watermark_text;
    let font_size = req.font_size.unwrap_or(48.0);

    // Escape text for PDF string literal
    let escaped_text = text.replace('\\', "\\\\").replace('(', "\\(").replace(')', "\\)");

    // Create watermark stream content
    // Gray color, 45 degree rotation, center placement
    let wm_stream_str = format!(
        "q 0.75 0.75 0.75 rg BT /Helvetica {} Tf 0.7071 0.7071 -0.7071 0.7071 200 300 Tm ({}) Tj ET Q",
        font_size, escaped_text
    );

    for (_num, page_id) in pages {
        let stream_obj = Object::Stream(lopdf::Stream::new(
            lopdf::Dictionary::new(),
            wm_stream_str.as_bytes().to_vec(),
        ));
        let stream_id = doc.add_object(stream_obj);

        if let Ok(page_dict) = doc.get_object_mut(page_id).and_then(|o| o.as_dict_mut()) {
            match page_dict.get(b"Contents") {
                Ok(Object::Array(ref arr)) => {
                    let mut new_arr = arr.clone();
                    new_arr.push(Object::Reference(stream_id));
                    page_dict.set("Contents", Object::Array(new_arr));
                }
                Ok(Object::Reference(ref single_ref)) => {
                    let new_arr = vec![Object::Reference(*single_ref), Object::Reference(stream_id)];
                    page_dict.set("Contents", Object::Array(new_arr));
                }
                _ => {
                    page_dict.set("Contents", Object::Reference(stream_id));
                }
            }
        }
    }

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(&req.output_path)?;
    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_path.clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn add_page_numbers(req: &PdfPageNumberRequest) -> Result<OperationResult, ToolkitError> {
    let mut doc = Document::load(&req.input_path)
        .map_err(|e| ToolkitError::PdfError(format!("Failed to load PDF: {}", e)))?;

    let in_size = std::fs::metadata(&req.input_path)?.len();
    let pages = doc.get_pages();
    let total = pages.len();
    let format_str = req.format.as_deref().unwrap_or("Page {n} of {total}");

    for (page_num, page_id) in pages {
        let label = format_str
            .replace("{n}", &page_num.to_string())
            .replace("{total}", &total.to_string());
        let escaped = label.replace('\\', "\\\\").replace('(', "\\(").replace(')', "\\)");

        let footer_stream = format!(
            "q 0.2 0.2 0.2 rg BT /Helvetica 10 Tf 270 25 Td ({}) Tj ET Q",
            escaped
        );

        let stream_obj = Object::Stream(lopdf::Stream::new(
            lopdf::Dictionary::new(),
            footer_stream.as_bytes().to_vec(),
        ));
        let stream_id = doc.add_object(stream_obj);

        if let Ok(page_dict) = doc.get_object_mut(page_id).and_then(|o| o.as_dict_mut()) {
            match page_dict.get(b"Contents") {
                Ok(Object::Array(ref arr)) => {
                    let mut new_arr = arr.clone();
                    new_arr.push(Object::Reference(stream_id));
                    page_dict.set("Contents", Object::Array(new_arr));
                }
                Ok(Object::Reference(ref single_ref)) => {
                    let new_arr = vec![Object::Reference(*single_ref), Object::Reference(stream_id)];
                    page_dict.set("Contents", Object::Array(new_arr));
                }
                _ => {
                    page_dict.set("Contents", Object::Reference(stream_id));
                }
            }
        }
    }

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(&req.output_path)?;
    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_path.clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn images_to_pdf(req: &ImagesToPdfRequest) -> Result<OperationResult, ToolkitError> {
    if req.input_paths.is_empty() {
        return Err(ToolkitError::InvalidParameter("No images specified to create PDF".to_string()));
    }

    let mut doc = Document::with_version("1.4");
    let pages_id = doc.new_object_id();
    let mut page_ids = Vec::new();
    let mut total_in_size: u64 = 0;

    for (idx, img_path) in req.input_paths.iter().enumerate() {
        let meta = std::fs::metadata(img_path).map_err(|e| ToolkitError::FileNotFound(format!("{}: {}", img_path, e)))?;
        total_in_size += meta.len();

        let img = image::open(img_path)
            .map_err(|e| ToolkitError::ImageError(format!("Failed to open image '{}': {}", img_path, e)))?;

        let rgb = img.to_rgb8();
        let (width, height) = rgb.dimensions();

        // Encode image raw bytes with flate
        let mut encoder = ZlibEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(rgb.as_raw()).map_err(|e| ToolkitError::IoError(e))?;
        let compressed_bytes = encoder.finish().map_err(|e| ToolkitError::IoError(e))?;

        let mut img_dict = lopdf::Dictionary::new();
        img_dict.set("Type", Object::Name(b"XObject".to_vec()));
        img_dict.set("Subtype", Object::Name(b"Image".to_vec()));
        img_dict.set("Width", Object::Integer(width as i64));
        img_dict.set("Height", Object::Integer(height as i64));
        img_dict.set("ColorSpace", Object::Name(b"DeviceRGB".to_vec()));
        img_dict.set("BitsPerComponent", Object::Integer(8));
        img_dict.set("Filter", Object::Name(b"FlateDecode".to_vec()));

        let img_stream = lopdf::Stream::new(img_dict, compressed_bytes);
        let img_obj_id = doc.add_object(Object::Stream(img_stream));

        let img_name = format!("Im{}", idx + 1);

        // Standard page: 595.28 x 841.89 pt (A4)
        let page_w = 595.0;
        let page_h = 842.0;

        // Scale image to fit within page keeping aspect ratio
        let img_aspect = width as f64 / height as f64;
        let page_aspect = page_w / page_h;
        let (draw_w, draw_h) = if img_aspect > page_aspect {
            (page_w - 40.0, (page_w - 40.0) / img_aspect)
        } else {
            ((page_h - 40.0) * img_aspect, page_h - 40.0)
        };
        let offset_x = (page_w - draw_w) / 2.0;
        let offset_y = (page_h - draw_h) / 2.0;

        let content_str = format!(
            "q {:.2} 0 0 {:.2} {:.2} {:.2} cm /{} Do Q",
            draw_w, draw_h, offset_x, offset_y, img_name
        );
        let content_id = doc.add_object(Object::Stream(lopdf::Stream::new(
            lopdf::Dictionary::new(),
            content_str.as_bytes().to_vec(),
        )));

        let mut xobjects = lopdf::Dictionary::new();
        xobjects.set(img_name, Object::Reference(img_obj_id));

        let mut resources = lopdf::Dictionary::new();
        resources.set("XObject", Object::Dictionary(xobjects));

        let mut page_dict = lopdf::Dictionary::new();
        page_dict.set("Type", Object::Name(b"Page".to_vec()));
        page_dict.set("Parent", Object::Reference(pages_id));
        page_dict.set("MediaBox", Object::Array(vec![
            Object::Integer(0),
            Object::Integer(0),
            Object::Real(page_w as f32),
            Object::Real(page_h as f32),
        ]));
        page_dict.set("Contents", Object::Reference(content_id));
        page_dict.set("Resources", Object::Dictionary(resources));

        let page_id = doc.add_object(Object::Dictionary(page_dict));
        page_ids.push(page_id);
    }

    let kids_objects: Vec<Object> = page_ids.iter().map(|id| Object::Reference(*id)).collect();
    let mut pages_dict = lopdf::Dictionary::new();
    pages_dict.set("Type", Object::Name(b"Pages".to_vec()));
    pages_dict.set("Kids", Object::Array(kids_objects));
    pages_dict.set("Count", Object::Integer(page_ids.len() as i64));
    doc.objects.insert(pages_id, Object::Dictionary(pages_dict));

    let catalog_id = doc.new_object_id();
    let mut catalog_dict = lopdf::Dictionary::new();
    catalog_dict.set("Type", Object::Name(b"Catalog".to_vec()));
    catalog_dict.set("Pages", Object::Reference(pages_id));
    doc.objects.insert(catalog_id, Object::Dictionary(catalog_dict));

    doc.trailer.set("Root", Object::Reference(catalog_id));
    doc.trailer.set("Size", Object::Integer((doc.max_id + 1) as i64));

    let out_path = Path::new(&req.output_path);
    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    doc.save(&req.output_path)?;
    let out_size = std::fs::metadata(&req.output_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_paths[0].clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: total_in_size,
        output_size_bytes: out_size,
    })
}
