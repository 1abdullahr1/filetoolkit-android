package com.filetoolkit.app.data

import com.google.gson.annotations.SerializedName

enum class ToolCategory {
    IMAGES,
    DOCUMENTS,
    PDF,
    TEXT,
    HISTORY
}

data class ToolDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val iconName: String,
    val acceptsMultiple: Boolean = false,
    val acceptedMimeTypes: List<String> = listOf("*/*")
)

data class FileInfo(
    val path: String,
    val filename: String,
    val format: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    @SerializedName("page_count") val pageCount: Int? = null,
    @SerializedName("is_valid") val isValid: Boolean = true,
    val error: String? = null
)

data class ImageConvertRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_path") val outputPath: String,
    @SerializedName("target_format") val targetFormat: String,
    val quality: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerializedName("keep_aspect_ratio") val keepAspectRatio: Boolean? = true,
    @SerializedName("rotation_degrees") val rotationDegrees: Int? = null,
    @SerializedName("crop_x") val cropX: Int? = null,
    @SerializedName("crop_y") val cropY: Int? = null,
    @SerializedName("crop_width") val cropWidth: Int? = null,
    @SerializedName("crop_height") val cropHeight: Int? = null,
    @SerializedName("strip_metadata") val stripMetadata: Boolean? = null,
    @SerializedName("bg_color_hex") val bgColorHex: String? = null
)

data class BatchImageRequest(
    val items: List<ImageConvertRequest>
)

data class OperationResult(
    val success: Boolean,
    @SerializedName("input_path") val inputPath: String = "",
    @SerializedName("output_path") val outputPath: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("input_size_bytes") val inputSizeBytes: Long = 0,
    @SerializedName("output_size_bytes") val outputSizeBytes: Long = 0
)

data class BatchResult(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val items: List<OperationResult> = emptyList()
)

data class PdfMergeRequest(
    @SerializedName("input_paths") val inputPaths: List<String>,
    @SerializedName("output_path") val outputPath: String
)

data class PdfSplitRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_dir") val outputDir: String,
    @SerializedName("pages_per_file") val pagesPerFile: Int? = 1
)

data class PdfExtractPagesRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_path") val outputPath: String,
    @SerializedName("page_numbers") val pageNumbers: List<Int>
)

data class PdfRotatePagesRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_path") val outputPath: String,
    @SerializedName("rotation_degrees") val rotationDegrees: Int,
    @SerializedName("page_numbers") val pageNumbers: List<Int>? = null
)

data class PdfDeletePagesRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_path") val outputPath: String,
    @SerializedName("page_numbers") val pageNumbers: List<Int>
)

data class PdfWatermarkRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_path") val outputPath: String,
    @SerializedName("watermark_text") val watermarkText: String,
    @SerializedName("font_size") val fontSize: Float? = 48f,
    val opacity: Float? = 0.5f
)

data class PdfPageNumberRequest(
    @SerializedName("input_path") val inputPath: String,
    @SerializedName("output_path") val outputPath: String,
    val format: String? = "Page {n} of {total}"
)

data class ImagesToPdfRequest(
    @SerializedName("input_paths") val inputPaths: List<String>,
    @SerializedName("output_path") val outputPath: String
)

data class TextToPdfRequest(
    val text: String,
    @SerializedName("output_path") val outputPath: String,
    val title: String? = null,
    @SerializedName("font_size") val fontSize: Float? = 11f,
    @SerializedName("line_spacing") val lineSpacing: Float? = 1.35f,
    @SerializedName("margin_pt") val marginPt: Float? = 45f,
    @SerializedName("page_size") val pageSize: String? = "A4"
)

data class TextToImageRequest(
    val text: String,
    @SerializedName("output_path") val outputPath: String,
    @SerializedName("target_format") val targetFormat: String = "png",
    @SerializedName("font_size") val fontSize: Int? = 16,
    @SerializedName("max_width") val maxWidth: Int? = 800,
    @SerializedName("bg_color_hex") val bgColorHex: String? = "#FFFFFF",
    @SerializedName("text_color_hex") val textColorHex: String? = "#111827"
)

data class HistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val toolTitle: String,
    val inputFileName: String,
    val outputFileName: String,
    val outputFilePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean,
    val inputSize: Long,
    val outputSize: Long,
    val errorMessage: String? = null
)

data class FormatMatrixItem(
    val format: String,
    val extension: String,
    @SerializedName("can_read") val canRead: Boolean,
    @SerializedName("can_write") val canWrite: Boolean,
    val category: String,
    val description: String
)
