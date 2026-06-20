package com.example.fpvupdater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RepoForm(
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nom du projet (ex: Mon Drone)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = url,
            onValueChange = { 
                url = it
                // Extraction automatique
                if (it.contains("github.com/")) {
                    val cleanPath = it.substringAfter("github.com/").removeSuffix(".git").removeSuffix("/")
                    val parts = cleanPath.split("/")
                    if (parts.size >= 2) {
                        owner = parts[0]
                        repo = parts[1]
                    }
                }
            },
            label = { Text("Lien GitHub (URL)") },
            placeholder = { Text("https://github.com/owner/repo") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (owner.isNotEmpty() && repo.isNotEmpty()) {
            Text(
                text = "Détecté : $owner / $repo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Propriétaire") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Dépôt") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        Button(
            onClick = {
                if (name.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()) {
                    onAdd(name, owner, repo)
                    name = ""; url = ""; owner = ""; repo = ""
                }
            },
            modifier = Modifier.align(Alignment.End),
            enabled = name.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(id = R.string.add_btn))
        }
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
    val projects by viewModel.projects.collectAsState()
    val userProjects = remember(projects) { projects.filter { it.isUserAdded } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Notifications
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
                        Text(stringResource(id = R.string.notifications_label), style = MaterialTheme.typography.bodyLarge)
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

            HorizontalDivider()

            Text(
                text = "Ajouter un dépôt via URL",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            RepoForm(onAdd = { n, o, r -> viewModel.addUserRepository(n, o, r) })

            HorizontalDivider()

            Text(
                text = "Dépôts enregistrés",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (userProjects.isEmpty()) {
                Text(
                    text = "Aucun dépôt personnalisé ajouté.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(userProjects) { project ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            ListItem(
                                headlineContent = { Text(project.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${project.owner}/${project.repo}") },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeUserRepository(project) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(id = R.string.delete_repo_desc),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.add_repo_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            RepoForm(onAdd = { n, o, r -> 
                viewModel.addUserRepository(n, o, r)
                onNavigateBack()
            })
        }
    }
}
