use std::fs::File;
use std::io::BufWriter;
use std::path::Path;
use image::{imageops::FilterType, DynamicImage, GenericImageView, ImageEncoder, ImageFormat, Rgba};
use crate::errors::ToolkitError;
use crate::models::{BatchImageRequest, BatchResult, ImageConvertRequest, OperationResult};

pub fn parse_hex_color(hex: &str) -> (u8, u8, u8) {
    let clean = hex.trim_start_matches('#');
    if clean.len() == 6 {
        let r = u8::from_str_radix(&clean[0..2], 16).unwrap_or(255);
        let g = u8::from_str_radix(&clean[2..4], 16).unwrap_or(255);
        let b = u8::from_str_radix(&clean[4..6], 16).unwrap_or(255);
        (r, g, b)
    } else {
        (255, 255, 255)
    }
}

pub fn flatten_alpha(img: &DynamicImage, bg_rgb: (u8, u8, u8)) -> DynamicImage {
    let (w, h) = img.dimensions();
    let rgba_img = img.to_rgba8();
    let mut rgb_img = image::RgbImage::new(w, h);

    for (x, y, pixel) in rgba_img.enumerate_pixels() {
        let alpha = pixel[3] as f32 / 255.0;
        let r = ((pixel[0] as f32 * alpha) + (bg_rgb.0 as f32 * (1.0 - alpha))).round() as u8;
        let g = ((pixel[1] as f32 * alpha) + (bg_rgb.1 as f32 * (1.0 - alpha))).round() as u8;
        let b = ((pixel[2] as f32 * alpha) + (bg_rgb.2 as f32 * (1.0 - alpha))).round() as u8;
        rgb_img.put_pixel(x, y, image::Rgb([r, g, b]));
    }

    DynamicImage::ImageRgb8(rgb_img)
}

pub fn convert_image(req: &ImageConvertRequest) -> Result<OperationResult, ToolkitError> {
    let in_path = Path::new(&req.input_path);
    if !in_path.exists() {
        return Err(ToolkitError::FileNotFound(req.input_path.clone()));
    }

    let in_meta = std::fs::metadata(in_path)?;
    let in_size = in_meta.len();

    let mut img = image::open(in_path)
        .map_err(|e| ToolkitError::ImageError(format!("Failed to open image '{}': {}", req.input_path, e)))?;

    // Cropping
    if let (Some(x), Some(y), Some(w), Some(h)) = (req.crop_x, req.crop_y, req.crop_width, req.crop_height) {
        if w > 0 && h > 0 && x < img.width() && y < img.height() {
            let actual_w = w.min(img.width() - x);
            let actual_h = h.min(img.height() - y);
            img = img.crop_imm(x, y, actual_w, actual_h);
        }
    }

    // Rotation
    if let Some(deg) = req.rotation_degrees {
        let norm_deg = ((deg % 360) + 360) % 360;
        match norm_deg {
            90 => img = img.rotate90(),
            180 => img = img.rotate180(),
            270 => img = img.rotate270(),
            _ => {}
        }
    }

    // Resizing
    if let (Some(w), Some(h)) = (req.width, req.height) {
        if w > 0 && h > 0 {
            let keep_ratio = req.keep_aspect_ratio.unwrap_or(true);
            if keep_ratio {
                img = img.resize(w, h, FilterType::Lanczos3);
            } else {
                img = img.resize_exact(w, h, FilterType::Lanczos3);
            }
        }
    }

    // Target format and output encoding
    let fmt_str = req.target_format.to_lowercase();
    let out_path = Path::new(&req.output_path);

    if let Some(parent) = out_path.parent() {
        if !parent.exists() {
            std::fs::create_dir_all(parent)?;
        }
    }

    let bg_color = req.bg_color_hex.as_deref().map(parse_hex_color).unwrap_or((255, 255, 255));

    match fmt_str.as_str() {
        "jpeg" | "jpg" => {
            let rgb_img = flatten_alpha(&img, bg_color);
            let file = File::create(out_path)?;
            let mut writer = BufWriter::new(file);
            let quality = req.quality.unwrap_or(85).clamp(1, 100);
            let encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut writer, quality);
            encoder.write_image(
                rgb_img.as_bytes(),
                rgb_img.width(),
                rgb_img.height(),
                image::ExtendedColorType::Rgb8,
            ).map_err(|e| ToolkitError::ImageError(format!("JPEG encoding failed: {}", e)))?;
        }
        "png" => {
            img.save_with_format(out_path, ImageFormat::Png)
                .map_err(|e| ToolkitError::ImageError(format!("PNG encoding failed: {}", e)))?;
        }
        "webp" => {
            img.save_with_format(out_path, ImageFormat::WebP)
                .map_err(|e| ToolkitError::ImageError(format!("WebP encoding failed: {}", e)))?;
        }
        "bmp" => {
            let rgb_img = flatten_alpha(&img, bg_color);
            rgb_img.save_with_format(out_path, ImageFormat::Bmp)
                .map_err(|e| ToolkitError::ImageError(format!("BMP encoding failed: {}", e)))?;
        }
        _ => {
            return Err(ToolkitError::UnsupportedFormat(format!("Unsupported output format: {}", req.target_format)));
        }
    }

    let out_meta = std::fs::metadata(out_path)?;
    let out_size = out_meta.len();

    Ok(OperationResult {
        success: true,
        input_path: req.input_path.clone(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: in_size,
        output_size_bytes: out_size,
    })
}

pub fn process_batch_images(req: &BatchImageRequest) -> BatchResult {
    let mut total = 0;
    let mut succeeded = 0;
    let mut failed = 0;
    let mut items = Vec::new();

    for item_req in &req.items {
        total += 1;
        match convert_image(item_req) {
            Ok(res) => {
                succeeded += 1;
                items.push(res);
            }
            Err(e) => {
                failed += 1;
                let in_size = std::fs::metadata(&item_req.input_path).map(|m| m.len()).unwrap_or(0);
                items.push(OperationResult {
                    success: false,
                    input_path: item_req.input_path.clone(),
                    output_path: None,
                    error_message: Some(e.to_string()),
                    input_size_bytes: in_size,
                    output_size_bytes: 0,
                });
            }
        }
    }

    BatchResult {
        total,
        succeeded,
        failed,
        items,
    }
}
