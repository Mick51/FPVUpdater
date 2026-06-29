/*
 * Copyright (C) 2026 Mick
 *
 * Ce programme est un logiciel libre : vous pouvez le redistribuer et/ou le modifier
 * selon les termes de la Licence Publique Générale GNU telle que publiée par
 * la Free Software Foundation, soit la version 3 de la licence, ou (au choix)
 * toute version ultérieure.
 *
 * Ce programme est distribué dans l'espoir qu'il sera utile, mais SANS AUCUNE GARANTIE ;
 * sans même la garantie implicite de COMMERCIALISATION ou D'ADÉQUATION À UN USAGE PARTICULIER.
 * Voir la Licence Publique Générale GNU pour plus de détails.
 *
 * Vous devriez avoir reçu une copie de la Licence Publique Générale GNU avec ce programme.
 * Sinon, voir <https://www.gnu.org/licenses/>.
 */

package com.example.fpvupdater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.fpvupdater.ui.theme.VersionBeta
import com.example.fpvupdater.ui.theme.VersionStable
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        createNotificationChannel(this)
        
        setContent {
            val dataStoreManager = remember { DataStoreManager(this) }
            val viewModel: MainViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(dataStoreManager) as T
                    }
                },
            )
            val themeMode by viewModel.themeMode.collectAsState(initial = "dark")
            val language by viewModel.language.collectAsState(initial = "system")

            // Gestion de la langue
            val context = LocalContext.current
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val localeContext = remember(language) {
                if (language == "system") context
                else {
                    val locale = Locale.forLanguageTag(language)
                    Locale.setDefault(locale)
                    val config = android.content.res.Configuration(configuration)
                    config.setLocale(locale)
                    context.createConfigurationContext(config)
                }
            }

            CompositionLocalProvider(LocalContext provides localeContext) {
                FPVUpdaterTheme(themeMode = themeMode) {
                    NotificationPermissionHandler()

                    LaunchedEffect(Unit) {
                        viewModel.refreshData(this@MainActivity)
                    }

                    AppNavigation(viewModel)
                }
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
                onNavigateToSettings = { navController.navigate("settings") },
            ) { navController.navigate("add_repo") } 
        }
        composable("settings") { 
            SettingsScreen(
                viewModel = viewModel, 
            ) { navController.popBackStack() }
        }
        composable("add_repo") {
            AddRepoScreen(
                viewModel = viewModel,
            ) { navController.popBackStack() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddRepo: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdate()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "FPV Updater",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAddRepo) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_repo_title))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(id = R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refreshData(context) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
            }
        },
    ) { innerPadding ->
        MainContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val sortedProjects = remember(projects) { 
        projects.sortedBy { it.name.lowercase() } 
    }

    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { viewModel.refreshData(context) })

    Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
        val isDownloading by viewModel.isDownloading.collectAsState()

        Column {
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            
            if (appUpdateInfo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.app_update_available),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Version ${appUpdateInfo?.tagName} disponible",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        if (isDownloading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Téléchargement en cours...",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Button(
                                onClick = { appUpdateInfo?.let { viewModel.downloadAppUpdate(context, it) } },
                                modifier = Modifier.align(Alignment.End).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(id = R.string.download_update_btn), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = sortedProjects, key = { it.name }) { project ->
                    ProjectCard(
                        project = project,
                        onOpenUrl = { url -> openUrl(context, url) },
                        onDelete = if (project.isUserAdded) { 
                            { viewModel.removeUserRepository(project) } 
                        } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCard(
    project: ProjectInfo, 
    onOpenUrl: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Logo du projet réduit et placé à côté du nom
                        AsyncImage(
                            model = project.iconUrl,
                            contentDescription = "${project.name} logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Version Stable
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(id = R.string.stable_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AssistChip(
                                onClick = { if (project.stableUrl.isNotEmpty()) onOpenUrl(project.stableUrl) },
                                label = { 
                                    Text(
                                        text = project.stableVersion,
                                        color = if (project.stableUrl.isNotEmpty()) VersionStable else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                },
                                leadingIcon = { 
                                    Icon(
                                        Icons.Default.CheckCircle, 
                                        contentDescription = null,
                                        tint = if (project.stableUrl.isNotEmpty()) VersionStable else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                },
                                enabled = project.stableUrl.isNotEmpty()
                            )
                        }
                        
                        // Version Beta
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(id = R.string.beta_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AssistChip(
                                onClick = { if (project.betaUrl.isNotEmpty()) onOpenUrl(project.betaUrl) },
                                label = { 
                                    Text(
                                        text = project.betaVersion,
                                        color = if (project.betaUrl.isNotEmpty()) VersionBeta else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (project.betaUrl.isEmpty()) Color.Transparent else VersionBeta.copy(alpha = 0.1f)
                                ),
                                leadingIcon = { 
                                    Icon(
                                        Icons.Default.Warning, 
                                        contentDescription = null,
                                        tint = if (project.betaUrl.isNotEmpty()) VersionBeta else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                },
                                enabled = project.betaUrl.isNotEmpty()
                            )
                        }
                    }
                }

                onDelete?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.delete_repo_desc),
                            tint = MaterialTheme.colorScheme.error,
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
    ) { }

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
        ExistingPeriodicWorkPolicy.KEEP,
        updateRequest
    )
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_desc)
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
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }
}

data class ProjectInfo(
    val name: String,
    val owner: String,
    val repo: String,
    val iconUrl: String = "",
    val stableVersion: String = "Loading...",
    val stableUrl: String = "",
    val betaVersion: String = "Loading...",
    val betaUrl: String = "",
    val isUserAdded: Boolean = false
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FPVUpdaterTheme {
        // MainScreen(...)
    }
}
