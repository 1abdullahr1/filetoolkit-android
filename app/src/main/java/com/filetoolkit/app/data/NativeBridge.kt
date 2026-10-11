package com.filetoolkit.app.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeBridge {
    private const val TAG = "NativeBridge"
    private val gson = Gson()
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("rust_core")
            isNativeLoaded = true
            Log.i(TAG, "rust_core native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "rust_core native library could not be loaded: ${e.message}")
            isNativeLoaded = false
        }
    }

    fun isLoaded(): Boolean = isNativeLoaded

    // Native external declarations matching rust_core jni_bridge
    private external fun getCoreVersion(): String
    private external fun inspectFile(path: String): String
    private external fun convertImage(reqJson: String): String
    private external fun processBatchImages(reqJson: String): String
    private external fun mergePdfs(reqJson: String): String
    private external fun splitPdf(reqJson: String): String
    private external fun extractPdfPages(reqJson: String): String
    private external fun rotatePdfPages(reqJson: String): String
    private external fun deletePdfPages(reqJson: String): String
    private external fun compressPdf(inPath: String, outPath: String): String
    private external fun watermarkPdf(reqJson: String): String
    private external fun addPageNumbersPdf(reqJson: String): String
    private external fun imagesToPdf(reqJson: String): String
    private external fun textToPdf(reqJson: String): String
    private external fun textToImage(reqJson: String): String
    private external fun getFormatMatrix(): String

    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) return@withContext "Native core not loaded"
        try {
            getCoreVersion()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun inspect(path: String): FileInfo = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            val file = java.io.File(path)
            return@withContext FileInfo(
                path = path,
                filename = file.name,
                format = file.extension.uppercase(),
                mimeType = "application/octet-stream",
                sizeBytes = if (file.exists()) file.length() else 0L,
                isValid = file.exists()
            )
        }
        val json = inspectFile(path)
        gson.fromJson(json, FileInfo::class.java)
    }

    suspend fun convertImg(req: ImageConvertRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(
                success = false,
                inputPath = req.inputPath,
                errorMessage = "Native core library not loaded on this architecture"
            )
        }
        val reqJson = gson.toJson(req)
        val resJson = convertImage(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun batchConvertImg(req: BatchImageRequest): BatchResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext BatchResult(
                total = req.items.size,
                succeeded = 0,
                failed = req.items.size
            )
        }
        val reqJson = gson.toJson(req)
        val resJson = processBatchImages(reqJson)
        gson.fromJson(resJson, BatchResult::class.java)
    }

    suspend fun merge(req: PdfMergeRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(
                success = false,
                inputPath = req.inputPaths.firstOrNull() ?: "",
                errorMessage = "Native core library not loaded"
            )
        }
        val reqJson = gson.toJson(req)
        val resJson = mergePdfs(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun split(req: PdfSplitRequest): List<OperationResult> = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) return@withContext emptyList()
        val reqJson = gson.toJson(req)
        val resJson = splitPdf(reqJson)
        val listType = object : TypeToken<List<OperationResult>>() {}.type
        gson.fromJson(resJson, listType)
    }

    suspend fun extractPages(req: PdfExtractPagesRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = req.inputPath, errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = extractPdfPages(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun rotatePages(req: PdfRotatePagesRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = req.inputPath, errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = rotatePdfPages(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun deletePages(req: PdfDeletePagesRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = req.inputPath, errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = deletePdfPages(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun compressPdfFile(inPath: String, outPath: String): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = inPath, errorMessage = "Native library not loaded")
        }
        val resJson = compressPdf(inPath, outPath)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun watermark(req: PdfWatermarkRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = req.inputPath, errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = watermarkPdf(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun addNumbers(req: PdfPageNumberRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = req.inputPath, errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = addPageNumbersPdf(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun createPdfFromImages(req: ImagesToPdfRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = req.inputPaths.firstOrNull() ?: "", errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = imagesToPdf(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun exportTextToPdf(req: TextToPdfRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = "text", errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = textToPdf(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun exportTextToImage(req: TextToImageRequest): OperationResult = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) {
            return@withContext OperationResult(success = false, inputPath = "text", errorMessage = "Native library not loaded")
        }
        val reqJson = gson.toJson(req)
        val resJson = textToImage(reqJson)
        gson.fromJson(resJson, OperationResult::class.java)
    }

    suspend fun fetchFormatMatrix(): List<FormatMatrixItem> = withContext(Dispatchers.IO) {
        if (!isNativeLoaded) return@withContext emptyList()
        val resJson = getFormatMatrix()
        val listType = object : TypeToken<List<FormatMatrixItem>>() {}.type
        gson.fromJson(resJson, listType)
    }
}
