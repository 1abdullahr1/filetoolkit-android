use std::fs::File;
use std::io::Write;
use std::path::Path;
use lopdf::{Document, Object, ObjectId};
use crate::errors::ToolkitError;
use crate::models::{OperationResult, TextToImageRequest, TextToPdfRequest};
use image::{Rgb, RgbImage};

pub fn text_to_pdf(req: &TextToPdfRequest) -> Result<OperationResult, ToolkitError> {
    if req.text.trim().is_empty() {
        return Err(ToolkitError::InvalidParameter("Text content is empty".to_string()));
    }

    let page_size_str = req.page_size.as_deref().unwrap_or("A4").to_uppercase();
    let (page_w, page_h) = if page_size_str == "LETTER" {
        (612.0, 792.0)
    } else {
        (595.28, 841.89) // A4 default
    };

    let margin = req.margin_pt.unwrap_or(45.0).max(20.0);
    let font_size = req.font_size.unwrap_or(11.0).max(6.0);
    let line_spacing_multiplier = req.line_spacing.unwrap_or(1.35).max(1.0);
    let line_height = font_size * line_spacing_multiplier;

    let printable_w = page_w - (2.0 * margin);
    let printable_h = page_h - (2.0 * margin) - 40.0; // allow for header/footer

    // Approximate character width for Helvetica
    let avg_char_w = font_size * 0.52;
    let max_chars_per_line = ((printable_w / avg_char_w).floor() as usize).max(20);

    // Word wrap text into lines
    let mut wrapped_lines: Vec<String> = Vec::new();
    for raw_line in req.text.lines() {
        if raw_line.is_empty() {
            wrapped_lines.push(String::new());
            continue;
        }

        let words: Vec<&str> = raw_line.split_whitespace().collect();
        let mut cur_line = String::new();

        for word in words {
            if cur_line.is_empty() {
                cur_line.push_str(word);
            } else if cur_line.len() + 1 + word.len() <= max_chars_per_line {
                cur_line.push(' ');
                cur_line.push_str(word);
            } else {
                wrapped_lines.push(cur_line);
                cur_line = word.to_string();
            }
        }
        if !cur_line.is_empty() {
            wrapped_lines.push(cur_line);
        }
    }

    // Paginate lines
    let max_lines_per_page = ((printable_h / line_height).floor() as usize).max(10);
    let pages_chunks: Vec<&[String]> = wrapped_lines.chunks(max_lines_per_page).collect();
    let total_pages = pages_chunks.len().max(1);

    // Build PDF document
    let mut doc = Document::with_version("1.4");
    let pages_id = doc.new_object_id();
    let mut page_ids = Vec::new();

    // Font object: Helvetica Type 1
    let mut font_dict = lopdf::Dictionary::new();
    font_dict.set("Type", Object::Name(b"Font".to_vec()));
    font_dict.set("Subtype", Object::Name(b"Type1".to_vec()));
    font_dict.set("BaseFont", Object::Name(b"Helvetica".to_vec()));
    let font_id = doc.add_object(Object::Dictionary(font_dict));

    // Font bold for title
    let mut font_bold_dict = lopdf::Dictionary::new();
    font_bold_dict.set("Type", Object::Name(b"Font".to_vec()));
    font_bold_dict.set("Subtype", Object::Name(b"Type1".to_vec()));
    font_bold_dict.set("BaseFont", Object::Name(b"Helvetica-Bold".to_vec()));
    let font_bold_id = doc.add_object(Object::Dictionary(font_bold_dict));

    for (p_idx, chunk) in pages_chunks.iter().enumerate() {
        let current_page_num = p_idx + 1;
        let mut stream_content = String::new();

        // Optional title on first page
        let mut start_y = page_h - margin - 20.0;
        if current_page_num == 1 {
            if let Some(ref title) = req.title {
                let escaped_title = title.replace('\\', "\\\\").replace('(', "\\(").replace(')', "\\)");
                stream_content.push_str(&format!(
                    "BT /F2 {:.1} Tf {:.1} {:.1} Td ({}) Tj ET\n",
                    font_size + 5.0,
                    margin,
                    start_y,
                    escaped_title
                ));
                start_y -= line_height * 2.0;
            }
        }

        // Render page text lines
        stream_content.push_str(&format!("BT /F1 {:.1} Tf\n", font_size));
        let mut cur_y = start_y;

        for line in *chunk {
            let escaped_line = line.replace('\\', "\\\\").replace('(', "\\(").replace(')', "\\)");
            // Filter non-ASCII characters to avoid Type1 encoding mismatch
            let ascii_safe: String = escaped_line.chars().map(|c| if c.is_ascii() { c } else { '?' }).collect();
            stream_content.push_str(&format!("{:.1} {:.1} Td ({}) Tj\n", margin, cur_y, ascii_safe));
            cur_y -= line_height;
        }
        stream_content.push_str("ET\n");

        // Footer: page number
        let footer_text = format!("Page {} of {}", current_page_num, total_pages);
        stream_content.push_str(&format!(
            "BT /F1 9 Tf {:.1} 25 Td ({}) Tj ET\n",
            (page_w / 2.0) - 25.0,
            footer_text
        ));

        let content_id = doc.add_object(Object::Stream(lopdf::Stream::new(
            lopdf::Dictionary::new(),
            stream_content.as_bytes().to_vec(),
        )));

        let mut fonts = lopdf::Dictionary::new();
        fonts.set("F1", Object::Reference(font_id));
        fonts.set("F2", Object::Reference(font_bold_id));

        let mut resources = lopdf::Dictionary::new();
        resources.set("Font", Object::Dictionary(fonts));

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
        input_path: "text_buffer".to_string(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: req.text.len() as u64,
        output_size_bytes: out_size,
    })
}

// 5x7 Basic Bitmap Font for rendering text to raster images without external font files
const FONT_5X7: [[u8; 5]; 96] = [
    [0x00, 0x00, 0x00, 0x00, 0x00], // ' '
    [0x00, 0x00, 0x5F, 0x00, 0x00], // '!'
    [0x00, 0x07, 0x00, 0x07, 0x00], // '"'
    [0x14, 0x7F, 0x14, 0x7F, 0x14], // '#'
    [0x24, 0x2A, 0x7F, 0x2A, 0x12], // '$'
    [0x23, 0x13, 0x08, 0x64, 0x62], // '%'
    [0x36, 0x49, 0x55, 0x22, 0x50], // '&'
    [0x00, 0x05, 0x03, 0x00, 0x00], // '\''
    [0x00, 0x1C, 0x22, 0x41, 0x00], // '('
    [0x00, 0x41, 0x22, 0x1C, 0x00], // ')'
    [0x14, 0x08, 0x3E, 0x08, 0x14], // '*'
    [0x08, 0x08, 0x3E, 0x08, 0x08], // '+'
    [0x00, 0x50, 0x30, 0x00, 0x00], // ','
    [0x08, 0x08, 0x08, 0x08, 0x08], // '-'
    [0x00, 0x60, 0x60, 0x00, 0x00], // '.'
    [0x20, 0x10, 0x08, 0x04, 0x02], // '/'
    [0x3E, 0x51, 0x49, 0x45, 0x3E], // '0'
    [0x00, 0x42, 0x7F, 0x40, 0x00], // '1'
    [0x42, 0x61, 0x51, 0x49, 0x46], // '2'
    [0x21, 0x41, 0x45, 0x4B, 0x31], // '3'
    [0x18, 0x14, 0x12, 0x7F, 0x10], // '4'
    [0x27, 0x45, 0x45, 0x45, 0x39], // '5'
    [0x3C, 0x4A, 0x49, 0x49, 0x30], // '6'
    [0x01, 0x71, 0x09, 0x05, 0x03], // '7'
    [0x36, 0x49, 0x49, 0x49, 0x36], // '8'
    [0x06, 0x49, 0x49, 0x29, 0x1E], // '9'
    [0x00, 0x36, 0x36, 0x00, 0x00], // ':'
    [0x00, 0x56, 0x36, 0x00, 0x00], // ';'
    [0x08, 0x14, 0x22, 0x41, 0x00], // '<'
    [0x14, 0x14, 0x14, 0x14, 0x14], // '='
    [0x00, 0x41, 0x22, 0x14, 0x08], // '>'
    [0x02, 0x01, 0x51, 0x09, 0x06], // '?'
    [0x32, 0x49, 0x79, 0x41, 0x3E], // '@'
    [0x7E, 0x11, 0x11, 0x11, 0x7E], // 'A'
    [0x7F, 0x49, 0x49, 0x49, 0x36], // 'B'
    [0x3E, 0x41, 0x41, 0x41, 0x22], // 'C'
    [0x7F, 0x41, 0x41, 0x22, 0x1C], // 'D'
    [0x7F, 0x49, 0x49, 0x49, 0x41], // 'E'
    [0x7F, 0x09, 0x09, 0x09, 0x01], // 'F'
    [0x3E, 0x41, 0x49, 0x49, 0x7A], // 'G'
    [0x7F, 0x08, 0x08, 0x08, 0x7F], // 'H'
    [0x00, 0x41, 0x7F, 0x41, 0x00], // 'I'
    [0x20, 0x40, 0x41, 0x3F, 0x01], // 'J'
    [0x7F, 0x08, 0x14, 0x22, 0x41], // 'K'
    [0x7F, 0x40, 0x40, 0x40, 0x40], // 'L'
    [0x7F, 0x02, 0x0C, 0x02, 0x7F], // 'M'
    [0x7F, 0x04, 0x08, 0x10, 0x7F], // 'N'
    [0x3E, 0x41, 0x41, 0x41, 0x3E], // 'O'
    [0x7F, 0x09, 0x09, 0x09, 0x06], // 'P'
    [0x3E, 0x41, 0x51, 0x21, 0x5E], // 'Q'
    [0x7F, 0x09, 0x19, 0x29, 0x46], // 'R'
    [0x46, 0x49, 0x49, 0x49, 0x31], // 'S'
    [0x01, 0x01, 0x7F, 0x01, 0x01], // 'T'
    [0x3F, 0x40, 0x40, 0x40, 0x3F], // 'U'
    [0x1F, 0x20, 0x40, 0x20, 0x1F], // 'V'
    [0x3F, 0x40, 0x38, 0x40, 0x3F], // 'W'
    [0x63, 0x14, 0x08, 0x14, 0x63], // 'X'
    [0x07, 0x08, 0x70, 0x08, 0x07], // 'Y'
    [0x61, 0x51, 0x49, 0x45, 0x43], // 'Z'
    [0x00, 0x7F, 0x41, 0x41, 0x00], // '['
    [0x02, 0x04, 0x08, 0x10, 0x20], // '\'
    [0x00, 0x41, 0x41, 0x7F, 0x00], // ']'
    [0x04, 0x02, 0x01, 0x02, 0x04], // '^'
    [0x40, 0x40, 0x40, 0x40, 0x40], // '_'
    [0x00, 0x01, 0x02, 0x04, 0x00], // '`'
    [0x20, 0x54, 0x54, 0x54, 0x78], // 'a'
    [0x7F, 0x48, 0x44, 0x44, 0x38], // 'b'
    [0x38, 0x44, 0x44, 0x44, 0x20], // 'c'
    [0x38, 0x44, 0x44, 0x48, 0x7F], // 'd'
    [0x38, 0x54, 0x54, 0x54, 0x18], // 'e'
    [0x08, 0x7E, 0x09, 0x01, 0x02], // 'f'
    [0x0C, 0x52, 0x52, 0x52, 0x3E], // 'g'
    [0x7F, 0x08, 0x04, 0x04, 0x78], // 'h'
    [0x00, 0x44, 0x7D, 0x40, 0x00], // 'i'
    [0x20, 0x40, 0x44, 0x3D, 0x00], // 'j'
    [0x7F, 0x10, 0x28, 0x44, 0x00], // 'k'
    [0x00, 0x41, 0x7F, 0x40, 0x00], // 'l'
    [0x7C, 0x04, 0x18, 0x04, 0x78], // 'm'
    [0x7C, 0x08, 0x04, 0x04, 0x78], // 'n'
    [0x38, 0x44, 0x44, 0x44, 0x38], // 'o'
    [0x7C, 0x14, 0x14, 0x14, 0x08], // 'p'
    [0x08, 0x14, 0x14, 0x18, 0x7C], // 'q'
    [0x7C, 0x08, 0x04, 0x04, 0x08], // 'r'
    [0x48, 0x54, 0x54, 0x54, 0x20], // 's'
    [0x04, 0x3F, 0x44, 0x40, 0x20], // 't'
    [0x3C, 0x40, 0x40, 0x20, 0x7C], // 'u'
    [0x1C, 0x20, 0x40, 0x20, 0x1C], // 'v'
    [0x3C, 0x40, 0x30, 0x40, 0x3C], // 'w'
    [0x44, 0x28, 0x10, 0x28, 0x44], // 'x'
    [0x0C, 0x50, 0x50, 0x50, 0x3C], // 'y'
    [0x44, 0x64, 0x54, 0x4C, 0x44], // 'z'
    [0x00, 0x08, 0x36, 0x41, 0x00], // '{'
    [0x00, 0x00, 0x7F, 0x00, 0x00], // '|'
    [0x00, 0x41, 0x36, 0x08, 0x00], // '}'
    [0x08, 0x08, 0x2A, 0x1C, 0x08], // '~'
    [0x7F, 0x7F, 0x7F, 0x7F, 0x7F], // del
];

pub fn text_to_image(req: &TextToImageRequest) -> Result<OperationResult, ToolkitError> {
    if req.text.trim().is_empty() {
        return Err(ToolkitError::InvalidParameter("Text is empty".to_string()));
    }

    let scale = (req.font_size.unwrap_or(24) / 8).max(1) as u32;
    let char_w = 6 * scale;
    let line_h = 10 * scale;
    let margin = 20 * scale;

    let lines: Vec<&str> = req.text.lines().collect();
    let max_len = lines.iter().map(|l| l.len()).max().unwrap_or(1);

    let img_w = (margin * 2 + (max_len as u32 * char_w)).max(200);
    let img_h = (margin * 2 + (lines.len() as u32 * line_h)).max(100);

    let bg_color = crate::image_tools::parse_hex_color(req.bg_color_hex.as_deref().unwrap_or("#FFFFFF"));
    let text_color = crate::image_tools::parse_hex_color(req.text_color_hex.as_deref().unwrap_or("#111827"));

    let mut img = RgbImage::from_pixel(img_w, img_h, Rgb([bg_color.0, bg_color.1, bg_color.2]));

    for (row_idx, line) in lines.iter().enumerate() {
        let y_start = margin + (row_idx as u32 * line_h);

        for (col_idx, ch) in line.chars().enumerate() {
            let x_start = margin + (col_idx as u32 * char_w);
            let ascii_idx = if ch.is_ascii() && (ch as u32) >= 32 && (ch as u32) < 127 {
                (ch as usize) - 32
            } else {
                ('?' as usize) - 32
            };

            let glyph = &FONT_5X7[ascii_idx];
            for col in 0..5 {
                let col_byte = glyph[col];
                for row in 0..7 {
                    if (col_byte & (1 << row)) != 0 {
                        for sx in 0..scale {
                            for sy in 0..scale {
                                let px = x_start + (col as u32 * scale) + sx;
                                let py = y_start + (row as u32 * scale) + sy;
                                if px < img_w && py < img_h {
                                    img.put_pixel(px, py, Rgb([text_color.0, text_color.1, text_color.2]));
                                }
                            }
                        }
                    }
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

    let target_fmt = req.target_format.to_lowercase();
    match target_fmt.as_str() {
        "jpeg" | "jpg" => {
            img.save_with_format(out_path, image::ImageFormat::Jpeg)?;
        }
        _ => {
            img.save_with_format(out_path, image::ImageFormat::Png)?;
        }
    }

    let out_size = std::fs::metadata(out_path)?.len();

    Ok(OperationResult {
        success: true,
        input_path: "text_buffer".to_string(),
        output_path: Some(req.output_path.clone()),
        error_message: None,
        input_size_bytes: req.text.len() as u64,
        output_size_bytes: out_size,
    })
}
