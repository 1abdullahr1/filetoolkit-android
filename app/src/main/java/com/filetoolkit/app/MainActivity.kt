package com.filetoolkit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.filetoolkit.app.data.*
import com.filetoolkit.app.ui.screens.*
import com.filetoolkit.app.ui.theme.*
import com.google.gson.Gson
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var historyRepository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyRepository = HistoryRepository(applicationContext)
        CacheManager.pruneOldCache(applicationContext)

        setContent {
            FileToolkitTheme {
                val navController = rememberNavController()
                val historyList by historyRepository.history.collectAsState()
                val gson = remember { Gson() }

                var currentToolId by rememberSaveable { mutableStateOf<String?>(null) }
                val currentTool = currentToolId?.let { ToolRepository.findTool(it) }
                var lastResultJson by rememberSaveable { mutableStateOf<String?>(null) }
                val lastResult = remember(lastResultJson) {
                    lastResultJson?.let {
                        try {
                            gson.fromJson(it, OperationResult::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                var lastToolTitle by rememberSaveable { mutableStateOf("") }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isBottomNavVisible = currentRoute in listOf("home", "tools", "recent", "settings")

                Scaffold(
                    containerColor = PureBlack,
                    bottomBar = {
                        if (isBottomNavVisible) {
                            PersistentBottomNav(
                                currentRoute = currentRoute ?: "home",
                                onNavigate = { route ->
                                    if (route == "home") {
                                        val popped = navController.popBackStack("home", inclusive = false)
                                        if (!popped) {
                                            navController.navigate("home") {
                                                popUpTo("home") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    } else if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = { fadeIn(animationSpec = tween(90)) },
                        exitTransition = { fadeOut(animationSpec = tween(90)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(90)) },
                        popExitTransition = { fadeOut(animationSpec = tween(90)) }
                    ) {
                        // 1. Home Tab
                        composable("home") {
                            HomeScreen(
                                onSelectQuickAction = { toolId ->
                                    val tool = ToolRepository.findTool(toolId) ?: ToolRepository.allTools.first()
                                    currentToolId = tool.id
                                    when (tool.category) {
                                        ToolCategory.IMAGES -> navController.navigate("tool_image")
                                        ToolCategory.PDF -> navController.navigate("tool_pdf")
                                        ToolCategory.TEXT -> navController.navigate("tool_text")
                                        else -> navController.navigate("tool_image")
                                    }
                                },
                                onSelectCategory = { cat ->
                                    navController.navigate("tools")
                                },
                                onSearchClick = {
                                    navController.navigate("tools")
                                },
                                onHistoryClick = {
                                    navController.navigate("recent")
                                },
                                onOpenFormatMatrix = {
                                    navController.navigate("format_matrix")
                                }
                            )
                        }

                        // 2. Tools Tab
                        composable("tools") {
                            ToolsScreen(
                                onSelectTool = { tool ->
                                    currentToolId = tool.id
                                    when (tool.category) {
                                        ToolCategory.IMAGES -> navController.navigate("tool_image")
                                        ToolCategory.PDF -> navController.navigate("tool_pdf")
                                        ToolCategory.TEXT -> navController.navigate("tool_text")
                                        else -> navController.navigate("tool_image")
                                    }
                                }
                            )
                        }

                        // 3. Recent Tab
                        composable("recent") {
                            RecentScreen(
                                historyItems = historyList,
                                onClearHistory = { historyRepository.clearHistory() },
                                onNavigateToTools = { navController.navigate("tools") }
                            )
                        }

                        // 4. Settings Tab
                        composable("settings") {
                            SettingsScreen(
                                onClearHistory = { historyRepository.clearHistory() },
                                onOpenLicenses = { navController.navigate("format_matrix") }
                            )
                        }

                        // Tool Workflows
                        composable(
                            "tool_image",
                            enterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            exitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            popEnterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) },
                            popExitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) }
                        ) {
                            currentTool?.let { tool ->
                                ImageToolScreen(
                                    tool = tool,
                                    onNavigateBack = { navController.popBackStack() },
                                    onResult = { res, title ->
                                        lastResultJson = gson.toJson(res)
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

                        composable(
                            "tool_pdf",
                            enterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            exitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            popEnterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) },
                            popExitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) }
                        ) {
                            currentTool?.let { tool ->
                                PdfToolScreen(
                                    tool = tool,
                                    onNavigateBack = { navController.popBackStack() },
                                    onResult = { res, title ->
                                        lastResultJson = gson.toJson(res)
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

                        composable(
                            "tool_text",
                            enterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            exitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            popEnterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) },
                            popExitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) }
                        ) {
                            currentTool?.let { tool ->
                                TextToolScreen(
                                    tool = tool,
                                    onNavigateBack = { navController.popBackStack() },
                                    onResult = { res, title ->
                                        lastResultJson = gson.toJson(res)
                                        lastToolTitle = title
                                        if (res.outputPath != null) {
                                            val outFile = File(res.outputPath)
                                            historyRepository.addHistoryItem(
                                                HistoryItem(
                                                    toolTitle = title,
                                                    inputFileName = "Text",
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

                        composable(
                            "result",
                            enterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            exitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            popEnterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) },
                            popExitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) }
                        ) {
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

                        composable(
                            "format_matrix",
                            enterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            exitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180)) },
                            popEnterTransition = { fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) },
                            popExitTransition = { fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180)) }
                        ) {
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

@Composable
fun PersistentBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureBlack)
    ) {
        HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Home",
                icon = Icons.Default.Home,
                selected = currentRoute == "home",
                onClick = { onNavigate("home") },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                label = "Tools",
                icon = Icons.Default.GridView,
                selected = currentRoute == "tools",
                onClick = { onNavigate("tools") },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                label = "Recent",
                icon = Icons.Default.History,
                selected = currentRoute == "recent",
                onClick = { onNavigate("recent") },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                label = "Settings",
                icon = Icons.Default.Settings,
                selected = currentRoute == "settings",
                onClick = { onNavigate("settings") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) TextPrimary else TextMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) TextPrimary else TextMuted,
                fontSize = 11.sp
            )
        }
    }
}
