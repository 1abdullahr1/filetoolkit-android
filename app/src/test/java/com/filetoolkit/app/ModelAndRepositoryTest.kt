package com.filetoolkit.app

import com.filetoolkit.app.data.*
import com.filetoolkit.app.ui.components.formatFileSize
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ModelAndRepositoryTest {

    @Test
    fun testToolRepository() {
        val allTools = ToolRepository.allTools
        assertTrue("Repository must contain tools", allTools.isNotEmpty())

        val imgConvert = ToolRepository.findTool("img_convert")
        assertNotNull("img_convert tool must exist", imgConvert)
        assertEquals("Convert Image", imgConvert?.title)

        val searchResults = ToolRepository.searchTools("pdf")
        assertTrue("Searching for PDF should return multiple tools", searchResults.size >= 5)

        val emptySearch = ToolRepository.searchTools("nonexistent_random_tool_123")
        assertEquals(0, emptySearch.size)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("500 B", formatFileSize(500))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testSerialization() {
        val gson = Gson()
        val fileInfo = FileInfo(
            path = "/tmp/test.png",
            filename = "test.png",
            format = "PNG",
            mimeType = "image/png",
            sizeBytes = 2048,
            width = 100,
            height = 100,
            isValid = true
        )

        val json = gson.toJson(fileInfo)
        assertTrue(json.contains("size_bytes"))

        val deserialized = gson.fromJson(json, FileInfo::class.java)
        assertEquals(fileInfo.path, deserialized.path)
        assertEquals(fileInfo.width, deserialized.width)
    }

    @Test
    fun testOperationResult() {
        val res = OperationResult(
            success = true,
            inputPath = "/in.jpg",
            outputPath = "/out.png",
            inputSizeBytes = 5000,
            outputSizeBytes = 3500
        )
        assertTrue(res.success)
        assertEquals(3500L, res.outputSizeBytes)
    }
}
