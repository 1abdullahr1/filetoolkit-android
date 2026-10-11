use rust_core::image_tools::{convert_image, process_batch_images};
use rust_core::metadata_tools::{detect_format_from_bytes, inspect_file};
use rust_core::models::*;
use rust_core::pdf_tools::*;
use rust_core::text_tools::{text_to_image, text_to_pdf};
use image::{Rgb, RgbImage};

fn create_test_image(path: &str, width: u32, height: u32) {
    let mut img = RgbImage::new(width, height);
    for x in 0..width {
        for y in 0..height {
            img.put_pixel(x, y, Rgb([(x % 256) as u8, (y % 256) as u8, 128]));
        }
    }
    img.save_with_format(path, image::ImageFormat::Png).expect("save test png");
}

#[test]
fn test_detect_format_from_bytes() {
    assert_eq!(detect_format_from_bytes(b"\x89PNG\r\n\x1a\n").0, "PNG");
    assert_eq!(detect_format_from_bytes(b"\xFF\xD8\xFF\xE0").0, "JPEG");
    assert_eq!(detect_format_from_bytes(b"RIFF\x00\x00\x00\x00WEBP").0, "WEBP");
    assert_eq!(detect_format_from_bytes(b"BM\x00\x00").0, "BMP");
    assert_eq!(detect_format_from_bytes(b"%PDF-1.4").0, "PDF");
    assert_eq!(detect_format_from_bytes(b"<!DOCTYPE html><html></html>").0, "HTML");
    assert_eq!(detect_format_from_bytes(b"Hello plain text world").0, "TXT");
}

#[test]
fn test_image_conversion_and_resize() {
    let dir = tempfile::tempdir().unwrap();
    let src_path = dir.path().join("input.png").to_string_lossy().to_string();
    let out_jpg = dir.path().join("output.jpg").to_string_lossy().to_string();
    let out_webp = dir.path().join("output.webp").to_string_lossy().to_string();
    let out_bmp = dir.path().join("output.bmp").to_string_lossy().to_string();

    create_test_image(&src_path, 100, 80);

    // 1. PNG to JPEG with resize and rotation
    let req_jpg = ImageConvertRequest {
        input_path: src_path.clone(),
        output_path: out_jpg.clone(),
        target_format: "jpeg".to_string(),
        quality: Some(80),
        width: Some(50),
        height: Some(40),
        keep_aspect_ratio: Some(true),
        rotation_degrees: Some(90),
        crop_x: None,
        crop_y: None,
        crop_width: None,
        crop_height: None,
        strip_metadata: Some(true),
        bg_color_hex: Some("#FFFFFF".to_string()),
        circle_crop: None,
    };

    let res_jpg = convert_image(&req_jpg).expect("convert to jpeg");
    assert!(res_jpg.success);
    assert!(res_jpg.output_size_bytes > 0);
    assert!(std::path::Path::new(&out_jpg).exists());

    // Inspect the converted file
    let info = inspect_file(&out_jpg);
    assert_eq!(info.format, "JPEG");
    assert!(info.is_valid);

    // 2. JPEG to WebP
    let req_webp = ImageConvertRequest {
        input_path: out_jpg.clone(),
        output_path: out_webp.clone(),
        target_format: "webp".to_string(),
        quality: Some(90),
        width: None,
        height: None,
        keep_aspect_ratio: None,
        rotation_degrees: None,
        crop_x: None,
        crop_y: None,
        crop_width: None,
        crop_height: None,
        strip_metadata: None,
        bg_color_hex: None,
        circle_crop: None,
    };
    let res_webp = convert_image(&req_webp).expect("convert to webp");
    assert!(res_webp.success);
    assert!(std::path::Path::new(&out_webp).exists());

    // 3. WebP to BMP
    let req_bmp = ImageConvertRequest {
        input_path: out_webp,
        output_path: out_bmp.clone(),
        target_format: "bmp".to_string(),
        quality: None,
        width: None,
        height: None,
        keep_aspect_ratio: None,
        rotation_degrees: None,
        crop_x: None,
        crop_y: None,
        crop_width: None,
        crop_height: None,
        strip_metadata: None,
        bg_color_hex: None,
        circle_crop: None,
    };
    let res_bmp = convert_image(&req_bmp).expect("convert to bmp");
    assert!(res_bmp.success);
    assert!(std::path::Path::new(&out_bmp).exists());
}

#[test]
fn test_batch_image_processing() {
    let dir = tempfile::tempdir().unwrap();
    let src1 = dir.path().join("img1.png").to_string_lossy().to_string();
    let src2 = dir.path().join("img2.png").to_string_lossy().to_string();
    let out1 = dir.path().join("out1.jpg").to_string_lossy().to_string();
    let out2 = dir.path().join("out2.jpg").to_string_lossy().to_string();

    create_test_image(&src1, 60, 60);
    create_test_image(&src2, 80, 80);

    let batch_req = BatchImageRequest {
        items: vec![
            ImageConvertRequest {
                input_path: src1,
                output_path: out1,
                target_format: "jpeg".to_string(),
                quality: Some(75),
                width: Some(30),
                height: Some(30),
                keep_aspect_ratio: Some(true),
                rotation_degrees: None,
                crop_x: None,
                crop_y: None,
                crop_width: None,
                crop_height: None,
                strip_metadata: None,
                bg_color_hex: None,
                circle_crop: None,
            },
            ImageConvertRequest {
                input_path: src2,
                output_path: out2,
                target_format: "jpeg".to_string(),
                quality: Some(75),
                width: Some(40),
                height: Some(40),
                keep_aspect_ratio: Some(true),
                rotation_degrees: None,
                crop_x: None,
                crop_y: None,
                crop_width: None,
                crop_height: None,
                strip_metadata: None,
                bg_color_hex: None,
                circle_crop: None,
            },
        ],
    };

    let result = process_batch_images(&batch_req);
    assert_eq!(result.total, 2);
    assert_eq!(result.succeeded, 2);
    assert_eq!(result.failed, 0);
}

#[test]
fn test_text_to_pdf_pagination() {
    let dir = tempfile::tempdir().unwrap();
    let out_pdf = dir.path().join("text_output.pdf").to_string_lossy().to_string();

    // Create long text that spans multiple pages
    let mut long_text = String::new();
    for i in 1..=200 {
        long_text.push_str(&format!("Line {}: This is a long sample text line intended to test multi-page automatic pagination without any silent content truncation.\n", i));
    }

    let req = TextToPdfRequest {
        text: long_text,
        output_path: out_pdf.clone(),
        title: Some("Automated Pagination Test Report".to_string()),
        font_size: Some(11.0),
        line_spacing: Some(1.3),
        margin_pt: Some(40.0),
        page_size: Some("A4".to_string()),
    };

    let res = text_to_pdf(&req).expect("text to pdf");
    assert!(res.success);
    assert!(std::path::Path::new(&out_pdf).exists());

    let info = inspect_file(&out_pdf);
    assert_eq!(info.format, "PDF");
    assert!(info.is_valid);
    assert!(info.page_count.unwrap_or(0) > 1, "Must span across multiple pages");
}

#[test]
fn test_text_to_image() {
    let dir = tempfile::tempdir().unwrap();
    let out_img = dir.path().join("text_image.png").to_string_lossy().to_string();

    let req = TextToImageRequest {
        text: "Hello Rust File Toolkit\nTesting bitmap text rendering".to_string(),
        output_path: out_img.clone(),
        target_format: "png".to_string(),
        font_size: Some(16),
        max_width: Some(500),
        bg_color_hex: Some("#FFFFFF".to_string()),
        text_color_hex: Some("#000000".to_string()),
    };

    let res = text_to_image(&req).expect("text to image");
    assert!(res.success);
    assert!(std::path::Path::new(&out_img).exists());

    let info = inspect_file(&out_img);
    assert_eq!(info.format, "PNG");
    assert!(info.is_valid);
}

#[test]
fn test_pdf_tools_workflow() {
    let dir = tempfile::tempdir().unwrap();
    let img1 = dir.path().join("page1.png").to_string_lossy().to_string();
    let img2 = dir.path().join("page2.png").to_string_lossy().to_string();
    let pdf1 = dir.path().join("doc1.pdf").to_string_lossy().to_string();
    let pdf2 = dir.path().join("doc2.pdf").to_string_lossy().to_string();
    let merged_pdf = dir.path().join("merged.pdf").to_string_lossy().to_string();
    let rotated_pdf = dir.path().join("rotated.pdf").to_string_lossy().to_string();
    let extracted_pdf = dir.path().join("extracted.pdf").to_string_lossy().to_string();
    let watermarked_pdf = dir.path().join("watermarked.pdf").to_string_lossy().to_string();
    let numbered_pdf = dir.path().join("numbered.pdf").to_string_lossy().to_string();

    create_test_image(&img1, 100, 100);
    create_test_image(&img2, 100, 100);

    // 1. Images to PDF
    let img_pdf_req1 = ImagesToPdfRequest {
        input_paths: vec![img1],
        output_path: pdf1.clone(),
    };
    images_to_pdf(&img_pdf_req1).expect("create pdf1");

    let img_pdf_req2 = ImagesToPdfRequest {
        input_paths: vec![img2],
        output_path: pdf2.clone(),
    };
    images_to_pdf(&img_pdf_req2).expect("create pdf2");

    // 2. Merge PDFs
    let merge_req = PdfMergeRequest {
        input_paths: vec![pdf1.clone(), pdf2.clone()],
        output_path: merged_pdf.clone(),
    };
    let merge_res = merge_pdfs(&merge_req).expect("merge pdfs");
    assert!(merge_res.success);

    let merged_info = inspect_file(&merged_pdf);
    assert_eq!(merged_info.page_count, Some(2));

    // 3. Rotate pages
    let rot_req = PdfRotatePagesRequest {
        input_path: merged_pdf.clone(),
        output_path: rotated_pdf.clone(),
        rotation_degrees: 90,
        page_numbers: Some(vec![1]),
    };
    let rot_res = rotate_pdf_pages(&rot_req).expect("rotate pdf pages");
    assert!(rot_res.success);

    // 4. Extract page
    let ext_req = PdfExtractPagesRequest {
        input_path: rotated_pdf.clone(),
        output_path: extracted_pdf.clone(),
        page_numbers: vec![1],
    };
    let ext_res = extract_pdf_pages(&ext_req).expect("extract pdf page");
    assert!(ext_res.success);
    let ext_info = inspect_file(&extracted_pdf);
    assert_eq!(ext_info.page_count, Some(1));

    // 5. Watermark PDF
    let wm_req = PdfWatermarkRequest {
        input_path: merged_pdf.clone(),
        output_path: watermarked_pdf.clone(),
        watermark_text: "CONFIDENTIAL".to_string(),
        font_size: Some(36.0),
        opacity: Some(0.5),
    };
    let wm_res = watermark_pdf(&wm_req).expect("watermark pdf");
    assert!(wm_res.success);

    // 6. Add page numbers
    let pn_req = PdfPageNumberRequest {
        input_path: watermarked_pdf,
        output_path: numbered_pdf.clone(),
        format: Some("Page {n} of {total}".to_string()),
    };
    let pn_res = add_page_numbers(&pn_req).expect("add page numbers");
    assert!(pn_res.success);
}

#[test]
fn test_circle_crop_integration() {
    let dir = tempfile::tempdir().unwrap();
    let src = dir.path().join("circle_in.png").to_string_lossy().to_string();
    let out = dir.path().join("circle_out.png").to_string_lossy().to_string();

    create_test_image(&src, 120, 120);

    let req = ImageConvertRequest {
        input_path: src,
        output_path: out.clone(),
        target_format: "png".to_string(),
        quality: None,
        width: None,
        height: None,
        keep_aspect_ratio: None,
        rotation_degrees: None,
        crop_x: None,
        crop_y: None,
        crop_width: None,
        crop_height: None,
        strip_metadata: None,
        bg_color_hex: None,
        circle_crop: Some(true),
    };

    let res = convert_image(&req).expect("circle crop image");
    assert!(res.success);
    assert!(std::path::Path::new(&out).exists());
}

