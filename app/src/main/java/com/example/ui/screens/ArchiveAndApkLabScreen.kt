package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppStrings
import com.example.engines.apk.ApkAnalysisReport
import com.example.engines.archive.ArchiveInspectionReport
import com.example.ui.viewmodel.YemenManagerViewModel
import java.io.File

@Composable
fun ArchiveLabScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    report: ArchiveInspectionReport?,
    selectedEntries: Set<String>
) {
    var newArchiveName by remember { mutableStateOf("custom_bundle.zip") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("archive_lab_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Security Shield Banner (FR-AR-005)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(strings.zipSecurityNotice, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Create Archive Bar (FR-AR-003)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newArchiveName,
                onValueChange = { newArchiveName = it },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("create_zip_name_input")
            )
            Button(
                onClick = { viewModel.createZipFromActiveSelection(newArchiveName) },
                modifier = Modifier.testTag("create_zip_button")
            ) {
                Icon(Icons.Default.Archive, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(strings.createZipLabel)
            }
        }

        if (report == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.emptyFolderMessage)
            }
            return
        }

        // Archive Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${File(report.archivePath).name} • ${report.totalEntries} ${strings.archiveEntriesLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Compressed: ${report.totalCompressedBytes} B  →  Uncompressed: ${report.totalUncompressedBytes} B",
                    style = MaterialTheme.typography.bodySmall
                )
                report.securityWarnings.forEach { warn ->
                    Text(warn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Extraction Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.extractCurrentArchive(extractOnlySelected = false) },
                modifier = Modifier.weight(1f).testTag("extract_all_archive_button")
            ) { Text(strings.extractAllLabel) }

            OutlinedButton(
                onClick = { viewModel.extractCurrentArchive(extractOnlySelected = true) },
                enabled = selectedEntries.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("extract_selected_archive_button")
            ) { Text("${strings.extractSelectedLabel} (${selectedEntries.size})") }
        }

        // Archive Entries List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("archive_entries_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(report.entries, key = { it.entryPath }) { entry ->
                val isSelected = selectedEntries.contains(entry.entryPath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.toggleArchiveEntrySelection(entry.entryPath) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleArchiveEntrySelection(entry.entryPath) }
                        )
                        Column {
                            Text(entry.entryPath, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "CRC32: ${entry.crc32Hex} • ${entry.compressedSize}B → ${entry.uncompressedSize}B (Ratio ${"%.1f".format(entry.compressionRatio)}x)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (entry.isSuspiciousTraversal || entry.isSuspiciousBombRatio) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ApkLabScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    report: ApkAnalysisReport?
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var dexSearchQuery by remember { mutableStateOf("") }

    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.emptyFolderMessage)
        }
        return
    }

    var newVerName by remember(report.apkPath) { mutableStateOf("${report.manifestInfo.versionName}-mod") }
    var newVerCode by remember(report.apkPath) { mutableStateOf((report.manifestInfo.versionCode + 1).toString()) }
    var auditNote by remember(report.apkPath) { mutableStateOf("Rebuilt & signed locally in Yemen Manager") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("apk_lab_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // APK Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = "${File(report.apkPath).name} • ${report.manifestInfo.packageName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${report.manifestInfo.versionName} (${report.manifestInfo.versionCode}) • minSdk ${report.manifestInfo.minSdk} / targetSdk ${report.manifestInfo.targetSdk}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "SHA-256: ${report.sha256Hex.take(32)}...",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Sub-Tabs: Overview | Manifest | ARSC | DEX | Rebuild & Sign
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabs = listOf(
                strings.apkOverviewTab,
                strings.apkManifestTab,
                strings.apkResourcesTab,
                strings.apkDexTab,
                strings.apkSigningTab
            )
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("apk_subtab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("apk_inspector_content"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (activeTab) {
                0 -> {
                    item {
                        InspectorSectionCard(
                            title = strings.apkOverviewTab,
                            lines = listOf(
                                "File: ${report.apkPath}",
                                "Size: ${report.fileSize} bytes (${report.totalEntries} ZIP entries)",
                                "Package: ${report.manifestInfo.packageName}",
                                "Version: ${report.manifestInfo.versionName} (code ${report.manifestInfo.versionCode})",
                                "SDK: minSdk=${report.manifestInfo.minSdk}, targetSdk=${report.manifestInfo.targetSdk}",
                                "DEX Files: ${report.dexReports.size}",
                                "Signature Summary: ${report.signatureReport.certificateSummary}"
                            )
                        )
                    }
                    if (report.warnings.isNotEmpty()) {
                        item {
                            InspectorSectionCard(
                                title = "Notices & Warnings",
                                lines = report.warnings
                            )
                        }
                    }
                    item {
                        InspectorSectionCard(
                            title = "Assets, Res & Native Entries (${report.assetAndNativeEntries.size})",
                            lines = report.assetAndNativeEntries.ifEmpty { listOf("None") }
                        )
                    }
                }
                1 -> {
                    item {
                        InspectorSectionCard(
                            title = "Declared Permissions (${report.manifestInfo.permissions.size})",
                            lines = report.manifestInfo.permissions.ifEmpty { listOf("No permissions declared") }
                        )
                    }
                    item {
                        InspectorSectionCard(
                            title = "Components (Activities & Services)",
                            lines = (report.manifestInfo.activities.map { "Activity: $it" } +
                                report.manifestInfo.services.map { "Service: $it" }).ifEmpty { listOf("None") }
                        )
                    }
                    item {
                        InspectorSectionCard(
                            title = "Reconstructed AndroidManifest.xml (Static AXML Decode)",
                            lines = report.manifestInfo.reconstructedXml.lines(),
                            monospace = true
                        )
                    }
                }
                2 -> {
                    item {
                        InspectorSectionCard(
                            title = "resources.arsc Binary Table Summary",
                            lines = listOf(
                                "Present: ${report.arscSummary.isPresent}",
                                "Byte Size: ${report.arscSummary.byteSize} B",
                                "Resource Package Count: ${report.arscSummary.packageCount}",
                                "Global String Pool Count: ${report.arscSummary.stringPoolCount}"
                            )
                        )
                    }
                    item {
                        InspectorSectionCard(
                            title = "ARSC String Pool Sample",
                            lines = report.arscSummary.sampleStrings.ifEmpty { listOf("Empty") },
                            monospace = true
                        )
                    }
                }
                3 -> {
                    item {
                        OutlinedTextField(
                            value = dexSearchQuery,
                            onValueChange = { dexSearchQuery = it },
                            placeholder = { Text("Search DEX classes, methods, or string literals...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("dex_search_input")
                        )
                    }
                    items(report.dexReports, key = { it.entryName }) { dex ->
                        val q = dexSearchQuery.trim().lowercase()
                        val filteredClasses = dex.sampleClasses.filter { q.isEmpty() || it.lowercase().contains(q) }
                        val filteredMethods = dex.sampleMethods.filter { q.isEmpty() || it.lowercase().contains(q) }
                        val filteredStrings = dex.sampleStrings.filter { q.isEmpty() || it.lowercase().contains(q) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InspectorSectionCard(
                                title = "${dex.entryName} (DEX v${dex.dexVersion} • ${dex.byteSize} B)",
                                lines = listOf(
                                    "Adler32 Checksum: 0x${dex.checksumHex} (Verified=${dex.checksumVerified})",
                                    "SHA-1 Signature: ${dex.sha1Hex}",
                                    "Table Counts: strings=${dex.stringIdsCount}, types=${dex.typeIdsCount}, protos=${dex.protoIdsCount}, fields=${dex.fieldIdsCount}, methods=${dex.methodIdsCount}, classes=${dex.classDefsCount}"
                                ),
                                monospace = true
                            )
                            InspectorSectionCard(
                                title = "Classes (${filteredClasses.size})",
                                lines = filteredClasses.ifEmpty { listOf("No matching classes") },
                                monospace = true
                            )
                            InspectorSectionCard(
                                title = "Method Signatures (${filteredMethods.size})",
                                lines = filteredMethods.ifEmpty { listOf("No matching methods") },
                                monospace = true
                            )
                            InspectorSectionCard(
                                title = "DEX String Pool (${filteredStrings.size})",
                                lines = filteredStrings.ifEmpty { listOf("No matching strings") },
                                monospace = true
                            )
                        }
                    }
                }
                4 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Text(strings.apkSignWarningBanner, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = "Current Signature Entries: ${report.signatureReport.signatureEntries.joinToString(", ").ifEmpty { "Unsigned" }}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "MANIFEST.MF SHA-256 Entry Digests: ${report.signatureReport.manifestMfDigestsCount}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = strings.apkRebuildSignAction,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = newVerName,
                                    onValueChange = { newVerName = it },
                                    label = { Text("New versionName") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("apk_rebuild_version_name_input")
                                )
                                OutlinedTextField(
                                    value = newVerCode,
                                    onValueChange = { newVerCode = it.filter { c -> c.isDigit() } },
                                    label = { Text("New versionCode") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("apk_rebuild_version_code_input")
                                )
                                OutlinedTextField(
                                    value = auditNote,
                                    onValueChange = { auditNote = it },
                                    label = { Text("Rebuild Audit Note (assets/ym_rebuild_audit.json)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        viewModel.rebuildAndSignCurrentApk(
                                            newVersionName = newVerName,
                                            newVersionCode = newVerCode.toIntOrNull() ?: (report.manifestInfo.versionCode + 1),
                                            auditNote = auditNote
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("apk_rebuild_sign_button")
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.apkRebuildSignAction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorSectionCard(
    title: String,
    lines: List<String>,
    monospace: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            lines.forEach { line ->
                Text(
                    text = line,
                    style = if (monospace) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif
                )
            }
        }
    }
}
