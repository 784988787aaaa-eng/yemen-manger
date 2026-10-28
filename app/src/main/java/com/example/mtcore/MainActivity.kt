package com.example.mtcore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mtcore.ui.CodeEditorScreen
import com.example.mtcore.ui.DexEditorScreen
import com.example.mtcore.ui.FileExplorerScreen
import com.example.mtcore.ui.HexEditorScreen
import com.example.mtcore.ui.InstalledAppsScreen
import com.example.mtcore.ui.MainViewModel
import com.example.mtcore.ui.StringPoolScreen
import com.example.mtcore.ui.theme.MTCoreTheme
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(cacheDir) as T
            }
        }
    }

    private var permissionGranted by mutableStateOf(false)
    private var languageCode by mutableStateOf("en")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val nowGranted = hasPermission()
        if (!permissionGranted && nowGranted) mainViewModel.refreshAll()
        permissionGranted = nowGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = hasPermission()
        setContent {
            val localizedContext = localizedContext(this, languageCode)
            val direction = if (languageCode == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalLayoutDirection provides direction
            ) {
                MTCoreTheme {
                    Surface(Modifier.fillMaxSize()) {
                        MainApp(
                            permissionGranted = permissionGranted,
                            requestPermission = { requestPermission() },
                            viewModel = mainViewModel,
                            languageCode = languageCode,
                            onToggleLanguage = { languageCode = if (languageCode == "en") "ar" else "en" }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val previouslyGranted = permissionGranted
        val currentlyGranted = hasPermission()
        permissionGranted = currentlyGranted
        if (!previouslyGranted && currentlyGranted) mainViewModel.refreshAll()
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}

private fun localizedContext(base: Context, languageCode: String): Context {
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocale(Locale(languageCode))
    configuration.setLayoutDirection(Locale(languageCode))
    return base.createConfigurationContext(configuration)
}

@Composable
private fun MainApp(
    permissionGranted: Boolean,
    requestPermission: () -> Unit,
    viewModel: MainViewModel,
    languageCode: String,
    onToggleLanguage: () -> Unit
) {
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val explorerState by viewModel.explorer.collectAsStateWithLifecycle()
    var currentFile by remember { mutableStateOf<File?>(null) }
    var activeStringPoolTarget by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    var activeHexFile by remember { mutableStateOf<File?>(null) }
    var showInstalledApps by remember { mutableStateOf(false) }
    var showSigningKeyScreen by remember { mutableStateOf(false) }
    var activeDexFiles by remember { mutableStateOf<List<File>?>(null) }
    var pendingArchiveFile by remember { mutableStateOf<File?>(null) }
    var showArchiveInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        when {
            currentFile != null -> {
                CodeEditorScreen(viewModel) { wasSaved ->
                    val editedFile = editor.file
                    if (wasSaved && editedFile != null && viewModel.archiveFor(editedFile) != null) {
                        pendingArchiveFile = viewModel.resolveOriginFile(editedFile)
                        showArchiveInfo = true
                    }
                    currentFile = null
                }
            }
            activeStringPoolTarget != null -> {
                val (poolFile, isTranslationMode) = activeStringPoolTarget!!
                StringPoolScreen(
                    file = poolFile,
                    initialTranslationMode = isTranslationMode,
                    onSavedBinary = { modifiedBinary ->
                        viewModel.markFileModified(modifiedBinary)
                        if (viewModel.archiveFor(modifiedBinary) != null) {
                            pendingArchiveFile = modifiedBinary
                            showArchiveInfo = true
                        }
                    },
                    onBack = { activeStringPoolTarget = null }
                )
            }
            activeHexFile != null -> {
                HexEditorScreen(
                    file = activeHexFile!!,
                    onSavedFile = { modifiedFile ->
                        viewModel.markFileModified(modifiedFile)
                        if (viewModel.archiveFor(modifiedFile) != null) {
                            pendingArchiveFile = modifiedFile
                            showArchiveInfo = true
                        }
                    },
                    onBack = { activeHexFile = null }
                )
            }
            activeDexFiles != null -> {
                DexEditorScreen(
                    dexFiles = activeDexFiles!!,
                    onOpenSmaliClass = { dexClass ->
                        viewModel.openDexClassInEditor(dexClass) { smaliFile ->
                            currentFile = smaliFile
                        }
                    },
                    onExit = { activeDexFiles = null }
                )
            }
            showSigningKeyScreen -> {
                com.example.mtcore.ui.SigningKeyScreen(
                    fallbackCacheDir = viewModel.appCacheDir,
                    onBack = { showSigningKeyScreen = false },
                    onNavigateToDirectory = { dir ->
                        viewModel.navigateTo(dir, explorerState.activePane)
                    }
                )
            }
            showInstalledApps -> {
                InstalledAppsScreen(
                    viewModel = viewModel,
                    onBack = { showInstalledApps = false },
                    onLocateExtractedApk = { extractedFile ->
                        showInstalledApps = false
                        viewModel.locateExtractedFile(extractedFile)
                    }
                )
            }
            else -> {
                FileExplorerScreen(
                    viewModel = viewModel,
                    onOpenFile = { file ->
                        if (viewModel.isBinaryXmlFile(file)) {
                            viewModel.decompileBinaryXmlToEditor(file) { decompiled ->
                                currentFile = decompiled
                            }
                        } else {
                            currentFile = file
                            viewModel.openFile(file)
                        }
                    },
                    onToggleLanguage = onToggleLanguage,
                    languageCode = languageCode,
                    onOpenInstalledApps = { showInstalledApps = true },
                    onOpenSigningKeyScreen = { showSigningKeyScreen = true },
                    onOpenDexEditor = { dexFiles -> activeDexFiles = dexFiles },
                    onOpenStringPoolScreen = { poolFile, isTrans -> activeStringPoolTarget = poolFile to isTrans },
                    onOpenHexEditorScreen = { hexFile -> activeHexFile = hexFile }
                )
            }
        }

        if (!permissionGranted) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.storage_access_required_title),
                            color = Color(0xFF111111),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.storage_access_required_message),
                            color = Color(0xFF333333),
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onToggleLanguage) {
                                Text(
                                    text = if (languageCode == "en") "العربية (AR)" else "English (EN)",
                                    color = Color(0xFF555555),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = requestPermission,
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Text(
                                    text = stringResource(R.string.allow_caps),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showArchiveInfo && pendingArchiveFile != null) {
        val fileName = pendingArchiveFile!!.name
        val prompt = if (languageCode == "ar") {
            "تم تعديل الملف \"$fileName\". هل تريد تحديث الأرشيف؟"
        } else {
            "File \"$fileName\" has been modified. Do you want to update it?"
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showArchiveInfo = false
                pendingArchiveFile = null
            },
            title = { Text("Info") },
            text = { Text(prompt) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val file = pendingArchiveFile!!
                    viewModel.updateArchive(file) { success, error ->
                        android.widget.Toast.makeText(
                            context,
                            if (success) "Archive updated successfully" else error.orEmpty(),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    showArchiveInfo = false
                    pendingArchiveFile = null
                }) { Text(if (languageCode == "ar") "موافق" else "OK") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showArchiveInfo = false
                    pendingArchiveFile = null
                }) { Text(if (languageCode == "ar") "إلغاء" else "CANCEL") }
            }
        )
    }
}
