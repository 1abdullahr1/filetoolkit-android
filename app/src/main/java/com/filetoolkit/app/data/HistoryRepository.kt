package com.filetoolkit.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("file_toolkit_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val json = prefs.getString("history_items", "[]")
        val listType = object : TypeToken<List<HistoryItem>>() {}.type
        val items: List<HistoryItem> = try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        _history.value = items.sortedByDescending { it.timestamp }
    }

    fun addHistoryItem(item: HistoryItem) {
        val current = _history.value.toMutableList()
        current.add(0, item)
        // Keep up to 100 recent operations
        val trimmed = if (current.size > 100) current.take(100) else current
        _history.value = trimmed
        saveHistory(trimmed)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history_items").apply()
    }

    private fun saveHistory(items: List<HistoryItem>) {
        val json = gson.toJson(items)
        prefs.edit().putString("history_items", json).apply()
    }
}

object ToolRepository {
    val allTools = listOf(
        // Image Tools
        ToolDefinition(
            id = "img_convert",
            title = "Convert Image",
            description = "Convert images between JPG, PNG, WebP, and BMP with quality settings",
            category = ToolCategory.IMAGES,
            iconName = "image",
            acceptedMimeTypes = listOf("image/jpeg", "image/png", "image/webp", "image/bmp")
        ),
        ToolDefinition(
            id = "img_resize",
            title = "Resize & Crop Image",
            description = "Change width, height, aspect ratio or crop image regions",
            category = ToolCategory.IMAGES,
            iconName = "crop",
            acceptedMimeTypes = listOf("image/*")
        ),
        ToolDefinition(
            id = "img_circle_crop",
            title = "Circle Crop",
            description = "Crop images into perfect circular badges with transparency",
            category = ToolCategory.IMAGES,
            iconName = "account_circle",
            acceptsMultiple = false,
            acceptedMimeTypes = listOf("image/*")
        ),
        ToolDefinition(
            id = "img_compress",
            title = "Compress Image",
            description = "Reduce image file size and strip unnecessary metadata",
            category = ToolCategory.IMAGES,
            iconName = "compress",
            acceptedMimeTypes = listOf("image/jpeg", "image/webp", "image/png")
        ),
        ToolDefinition(
            id = "img_batch",
            title = "Batch Image Converter",
            description = "Convert or compress multiple images simultaneously",
            category = ToolCategory.IMAGES,
            iconName = "collections",
            acceptsMultiple = true,
            acceptedMimeTypes = listOf("image/*")
        ),
        ToolDefinition(
            id = "img_to_pdf",
            title = "Images to PDF",
            description = "Combine one or multiple photos into a single PDF document",
            category = ToolCategory.IMAGES,
            iconName = "picture_as_pdf",
            acceptsMultiple = true,
            acceptedMimeTypes = listOf("image/*")
        ),

        // PDF Tools
        ToolDefinition(
            id = "pdf_merge",
            title = "Merge PDFs",
            description = "Combine two or more PDF documents into one ordered PDF",
            category = ToolCategory.PDF,
            iconName = "merge",
            acceptsMultiple = true,
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_split",
            title = "Split PDF",
            description = "Split a large PDF document into individual pages or sections",
            category = ToolCategory.PDF,
            iconName = "call_split",
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_extract",
            title = "Extract Pages",
            description = "Extract specific page numbers into a new PDF",
            category = ToolCategory.PDF,
            iconName = "content_copy",
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_rotate",
            title = "Rotate Pages",
            description = "Rotate PDF pages by 90, 180, or 270 degrees",
            category = ToolCategory.PDF,
            iconName = "rotate_right",
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_delete_pages",
            title = "Delete Pages",
            description = "Remove unwanted pages from a PDF document",
            category = ToolCategory.PDF,
            iconName = "delete_sweep",
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_watermark",
            title = "Add Watermark",
            description = "Apply a custom diagonal watermark text across all PDF pages",
            category = ToolCategory.PDF,
            iconName = "branding_watermark",
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_page_numbers",
            title = "Add Page Numbers",
            description = "Insert formatted page numbering on each page footer",
            category = ToolCategory.PDF,
            iconName = "format_list_numbered",
            acceptedMimeTypes = listOf("application/pdf")
        ),
        ToolDefinition(
            id = "pdf_compress",
            title = "Compress PDF",
            description = "Optimize and compress internal PDF streams to reduce file size",
            category = ToolCategory.PDF,
            iconName = "archive",
            acceptedMimeTypes = listOf("application/pdf")
        ),

        // Text Tools
        ToolDefinition(
            id = "text_to_pdf",
            title = "Text to PDF",
            description = "Export written or pasted text to multi-page PDF with custom margins & font size",
            category = ToolCategory.TEXT,
            iconName = "text_snippet"
        ),
        ToolDefinition(
            id = "text_to_image",
            title = "Text to Image",
            description = "Render text onto high-contrast PNG or JPEG image cards",
            category = ToolCategory.TEXT,
            iconName = "image"
        )
    )

    val imageTools: List<ToolDefinition> = allTools.filter { it.category == ToolCategory.IMAGES }
    val pdfTools: List<ToolDefinition> = allTools.filter { it.category == ToolCategory.PDF }
    val textTools: List<ToolDefinition> = allTools.filter { it.category == ToolCategory.TEXT }

    private val toolMap: Map<String, ToolDefinition> = allTools.associateBy { it.id }

    fun findTool(id: String): ToolDefinition? = toolMap[id]

    fun searchTools(query: String): List<ToolDefinition> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return allTools
        return allTools.filter {
            it.title.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.name.lowercase().contains(q)
        }
    }
}
