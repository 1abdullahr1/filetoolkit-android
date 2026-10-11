package com.filetoolkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filetoolkit.app.data.HistoryItem
import com.filetoolkit.app.data.HistoryRepository
import com.filetoolkit.app.data.OperationResult
import com.filetoolkit.app.data.ToolCategory
import com.filetoolkit.app.data.ToolDefinition
import com.filetoolkit.app.data.ToolRepository
import com.filetoolkit.app.ui.screens.*
import com.filetoolkit.app.ui.theme.FileToolkitTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var historyRepository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyRepository = HistoryRepository(applicationContext)

        setContent {
            FileToolkitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val historyList by historyRepository.history.collectAsState()

                    var currentTool by remember { mutableStateOf<ToolDefinition?>(null) }
                    var lastResult by remember { mutableStateOf<OperationResult?>(null) }
                    var lastToolTitle by remember { mutableStateOf("") }

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                historyItems = historyList,
                                onSelectTool = { tool ->
                                    currentTool = tool
                                    when (tool.category) {
                                        ToolCategory.IMAGES -> navController.navigate("tool_image")
                                        ToolCategory.PDF -> navController.navigate("tool_pdf")
                                        ToolCategory.TEXT -> navController.navigate("tool_text")
                                        else -> navController.navigate("tool_image")
                                    }
                                },
                                onOpenHistory = { navController.navigate("history") },
                                onOpenFormatMatrix = { navController.navigate("format_matrix") }
                            )
                        }

                        composable("tool_image") {
                            currentTool?.let { tool ->
                                ImageToolScreen(
                                    tool = tool,
                                    onNavigateBack = { navController.popBackStack() },
                                    onResult = { res, title ->
                                        lastResult = res
                                        lastToolTitle = title
                                        // Save to history
                                        if (res.outputPath != null) {
                                            val outFile = File(res.outputPath)
                                            val inFile = File(res.inputPath)
                                            historyRepository.addHistoryItem(
                                                HistoryItem(
                                                    toolTitle = title,
                                                    inputFileName = inFile.name,
                                                    outputFileName = outFile.name,
                                                    outputFilePath = res.outputPath,
                                                    isSuccess = res.success,
                                                    inputSize = res.inputSizeBytes,
                                                    outputSize = res.outputSizeBytes,
                                                    errorMessage = res.errorMessage
                                                )
                                            )
                                        }
                                        navController.navigate("result")
                                    }
                                )
                            }
                        }

                        composable("tool_pdf") {
                            currentTool?.let { tool ->
                                PdfToolScreen(
                                    tool = tool,
                                    onNavigateBack = { navController.popBackStack() },
                                    onResult = { res, title ->
                                        lastResult = res
                                        lastToolTitle = title
                                        if (res.outputPath != null) {
                                            val outFile = File(res.outputPath)
                                            val inFile = File(res.inputPath)
                                            historyRepository.addHistoryItem(
                                                HistoryItem(
                                                    toolTitle = title,
                                                    inputFileName = inFile.name,
                                                    outputFileName = outFile.name,
                                                    outputFilePath = res.outputPath,
                                                    isSuccess = res.success,
                                                    inputSize = res.inputSizeBytes,
                                                    outputSize = res.outputSizeBytes,
                                                    errorMessage = res.errorMessage
                                                )
                                            )
                                        }
                                        navController.navigate("result")
                                    }
                                )
                            }
                        }

                        composable("tool_text") {
                            currentTool?.let { tool ->
                                TextToolScreen(
                                    tool = tool,
                                    onNavigateBack = { navController.popBackStack() },
                                    onResult = { res, title ->
                                        lastResult = res
                                        lastToolTitle = title
                                        if (res.outputPath != null) {
                                            val outFile = File(res.outputPath)
                                            historyRepository.addHistoryItem(
                                                HistoryItem(
                                                    toolTitle = title,
                                                    inputFileName = "Text Buffer",
                                                    outputFileName = outFile.name,
                                                    outputFilePath = res.outputPath,
                                                    isSuccess = res.success,
                                                    inputSize = res.inputSizeBytes,
                                                    outputSize = res.outputSizeBytes,
                                                    errorMessage = res.errorMessage
                                                )
                                            )
                                        }
                                        navController.navigate("result")
                                    }
                                )
                            }
                        }

                        composable("result") {
                            lastResult?.let { res ->
                                ResultsScreen(
                                    result = res,
                                    toolTitle = lastToolTitle,
                                    onNavigateHome = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }

                        composable("history") {
                            HistoryScreen(
                                historyItems = historyList,
                                onClearHistory = { historyRepository.clearHistory() },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("format_matrix") {
                            FormatMatrixScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
