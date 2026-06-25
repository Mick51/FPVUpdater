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

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.core.net.toUri

@Composable
fun RepoForm(
    onAdd: (String, String, String) -> Unit,
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
            singleLine = true,
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
            singleLine = true,
        )

        if (owner.isNotEmpty() && repo.isNotEmpty()) {
            Text(
                text = "Détecté : $owner / $repo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
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
    val themeMode by viewModel.themeMode.collectAsState(initial = "dark")
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Notifications
            item {
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
            }

            // Section Thème
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(id = R.string.theme_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                "dark" to R.string.theme_dark,
                                "light" to R.string.theme_light,
                                "system" to R.string.theme_system
                            ).forEach { (mode, labelRes) ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(stringResource(id = labelRes)) },
                                    leadingIcon = if (themeMode == mode) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = "Ajouter un dépôt via URL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                RepoForm { n, o, r -> viewModel.addUserRepository(n, o, r) }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = "Dépôts enregistrés",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (userProjects.isEmpty()) {
                item {
                    Text(
                        text = "Aucun dépôt personnalisé ajouté.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
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

            item { HorizontalDivider() }

            // Section À Propos
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(id = R.string.about_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        AboutRow(label = stringResource(id = R.string.author_label), value = "Mick")
                        AboutRow(
                            label = stringResource(id = R.string.contact_label),
                            value = "naudclick.informatik@gmail.com"
                        ) {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:naudclick.informatik@gmail.com".toUri()
                            }
                            context.startActivity(intent)
                        }
                        AboutRow(label = stringResource(id = R.string.version_label), value = BuildConfig.VERSION_NAME)
                        AboutRow(label = stringResource(id = R.string.license_label), value = "GNU GPL v3")
                    }
                }
            }
        }
    }
}

@Composable
fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
        )
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
                .fillMaxWidth(),
        ) {
            RepoForm { n, o, r -> 
                viewModel.addUserRepository(n, o, r)
                onNavigateBack()
            }
        }
    }
}
