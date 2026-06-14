package com.example.fpvupdater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import coil.compose.AsyncImage
import com.example.fpvupdater.ui.theme.FPVUpdaterTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        createNotificationChannel(this)
        
        setContent {
            FPVUpdaterTheme {
                NotificationPermissionHandler()
                
                val dataStoreManager = remember { DataStoreManager(this) }
                val viewModel: MainViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return MainViewModel(dataStoreManager) as T
                        }
                    }
                )

                AppNavigation(viewModel)
            }
        }
        scheduleUpdateCheck(this)
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") { 
            MainScreen(
                viewModel = viewModel, 
                onNavigateToSettings = { navController.navigate("settings") }
            ) 
        }
        composable("settings") { 
            SettingsScreen(
                viewModel = viewModel, 
                onNavigateBack = { navController.popBackStack() }
            ) 
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "FPV Updater",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                }
            )
        }
    ) { innerPadding ->
        MainContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notifications de mise à jour", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.toggleNotifications(it, context) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Vérifie les nouvelles versions en arrière-plan et envoie une notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val sortedProjects = remember(projects) { 
        projects.sortedBy { it.name.lowercase() } 
    }

    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { viewModel.refreshData() })

    Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column {
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = sortedProjects, key = { it.name }) { project ->
                    ProjectCard(
                        project = project,
                        onOpenUrl = { url -> openUrl(context, url) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCard(project: ProjectInfo, onOpenUrl: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { 
                // Optimisation du rendu matériel
                clip = true
                shape = RoundedCornerShape(16.dp)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo du projet
            AsyncImage(
                model = project.iconUrl,
                contentDescription = "${project.name} logo",
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Version Stable
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Stable", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        AssistChip(
                            onClick = { if (project.stableUrl.isNotEmpty()) onOpenUrl(project.stableUrl) },
                            label = { 
                                Text(
                                    text = project.stableVersion,
                                    color = if (project.stableUrl.isNotEmpty()) Color(0xFF4CAF50) else Color.Unspecified
                                ) 
                            },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.CheckCircle, 
                                    contentDescription = null,
                                    tint = if (project.stableUrl.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
                                ) 
                            },
                            enabled = project.stableUrl.isNotEmpty()
                        )
                    }
                    
                    // Version Beta
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Beta", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        AssistChip(
                            onClick = { if (project.betaUrl.isNotEmpty()) onOpenUrl(project.betaUrl) },
                            label = { 
                                Text(
                                    text = project.betaVersion,
                                    color = if (project.betaUrl.isNotEmpty()) Color(0xFFFF9800) else Color.Unspecified
                                ) 
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (project.betaUrl.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Warning, 
                                    contentDescription = null,
                                    tint = if (project.betaUrl.isNotEmpty()) Color(0xFFFF9800) else Color.Gray
                                ) 
                            },
                            enabled = project.betaUrl.isNotEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionHandler() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

fun scheduleUpdateCheck(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "FPVUpdateCheck",
        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
        updateRequest
    )
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Mises à jour FPV"
        val descriptionText = "Notifications lors de nouvelles versions"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("FPV_UPDATES_CHANNEL", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun openUrl(context: Context, url: String) {
    if (url.isNotEmpty()) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}

data class ProjectInfo(
    val name: String,
    val owner: String,
    val repo: String,
    val iconUrl: String = "",
    val stableVersion: String = "Chargement...",
    val stableUrl: String = "",
    val betaVersion: String = "Chargement...",
    val betaUrl: String = ""
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FPVUpdaterTheme {
        // MainScreen(...)
    }
}