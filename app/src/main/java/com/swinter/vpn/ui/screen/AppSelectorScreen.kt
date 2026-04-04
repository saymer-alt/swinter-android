package com.swinter.vpn.ui.screen

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swinter.vpn.ui.theme.DarkBackground
import com.swinter.vpn.ui.theme.TextPrimary
import com.swinter.vpn.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun drawableToBitmap(drawable: Drawable, size: Int = 48): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return bitmap
}

data class AppEntry(
    val name: String,
    val packageName: String,
    val icon: Bitmap,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    selectedApps: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>().also { it.addAll(selectedApps) } }

    // Load apps
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
                packages.mapNotNull { pkg ->
                    try {
                        val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                        val icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null }
                            ?: return@mapNotNull null
                        AppEntry(
                            name = appInfo.loadLabel(pm).toString(),
                            packageName = pkg.packageName,
                            icon = drawableToBitmap(icon),
                            isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                        )
                    } catch (_: Exception) { null }
                }
                .sortedWith(compareBy<AppEntry> { it.packageName !in selectedApps }
                    .thenBy { it.isSystem }
                    .thenBy { it.name.lowercase() })
            } catch (e: Exception) {
                android.util.Log.e("AppSelector", "Failed to load apps", e)
                emptyList()
            }
        }
        loading = false
    }

    // Sync back on leave
    DisposableEffect(Unit) {
        onDispose { onSelectionChanged(selected.toList()) }
    }

    val filtered = if (searchQuery.isBlank()) apps else apps.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приложения (${selected.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isChecked = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (app.isSystem) "** ${app.name}" else app.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) selected.add(app.packageName)
                                    else selected.remove(app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
