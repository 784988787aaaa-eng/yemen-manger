package com.example.mtcore.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtcore.data.AppRepository
import com.example.mtcore.domain.FileItem
import java.util.Locale

private val ActiveBackground = Color(0xFFFFFFFF)
private val InactiveBackground = Color(0xFFF7F7F7)
private val DividerColor = Color(0xFFE0E0E0)
private val NameColor = Color(0xFF111111)
private val MetadataColor = Color(0xFF757575)
private val ModifiedColor = Color(0xFF2E7D32)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePane(
    modifier: Modifier,
    files: List<FileItem>,
    active: Boolean,
    isLeft: Boolean,
    onActivate: () -> Unit,
    onClickItem: (FileItem) -> Unit,
    onLongClickItem: (FileItem) -> Unit,
    selectedPaths: Set<String>,
    onToggleSelect: (FileItem) -> Unit
) {
    Column(
        modifier = modifier
            .background(if (active) ActiveBackground else InactiveBackground)
            .drawWithContent {
                drawContent()
                if (active) {
                    val corner = 9.dp.toPx()
                    val triangle = Path().apply {
                        if (isLeft) {
                            moveTo(size.width - corner, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, corner)
                        } else {
                            moveTo(0f, 0f)
                            lineTo(corner, 0f)
                            lineTo(0f, corner)
                        }
                        close()
                    }
                    drawPath(triangle, Color.Black)
                }
            }
            .clickable { onActivate() }
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(files, key = { item -> item.file.absolutePath + if (item.isParent) ":parent" else "" }) { item ->
                FileRow(
                    item = item,
                    onClick = { onClickItem(item) },
                    onLongClick = { onLongClickItem(item) },
                    selected = item.file.absolutePath in selectedPaths,
                    hasSelection = selectedPaths.isNotEmpty(),
                    onToggleSelect = { onToggleSelect(item) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    hasSelection: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val lowerName = item.name.lowercase(Locale.ROOT)
    val extension = item.file.extension.lowercase(Locale.ROOT)
    val isFolder = item.isDirectory || item.isParent
    val isNinePatch = !isFolder && lowerName.endsWith(".9.png")
    val isApk = !isFolder && extension == "apk"
    val apkIcon = if (isApk) {
        val loaded by AppRepository.rememberApkFileIcon(context, item.file)
        loaded
    } else {
        null
    }

    val imageExtensions = setOf("png", "webp", "jpg", "jpeg", "gif", "bmp", "ico")
    val fontExtensions = setOf("ttf", "otf")
    val codeExtensions = setOf(
        "kt", "kts", "java", "xml", "gradle", "bat", "sh", "js", "ts",
        "html", "css", "py", "c", "cpp", "h", "hpp", "smali"
    )
    val textExtensions = setOf(
        "md", "txt", "properties", "json", "yml", "yaml", "ini", "conf", "toml", "sql", "csv", "log"
    )
    val archiveExtensions = setOf("zip", "apk", "apks", "xapk", "apkm", "jar", "aab")

    val iconBackground = when {
        isFolder -> Color(0xFF262626)
        isNinePatch -> Color(0xFF3B6EAA)
        extension in imageExtensions -> Color(0xFF43A047)
        extension in fontExtensions -> Color(0xFF546E7A)
        extension == "dex" -> Color(0xFF43A047)
        extension == "arsc" -> Color(0xFFF9A825)
        extension == "xml" -> Color(0xFF3B6EAA)
        extension in codeExtensions || extension in textExtensions -> Color(0xFF3B6EAA)
        extension in archiveExtensions -> Color(0xFF6D4C41)
        else -> Color(0xFF5F6E78)
    }

    val icon = when {
        isFolder -> Icons.Default.Folder
        extension in imageExtensions -> Icons.Default.Image
        extension == "arsc" -> Icons.Default.Widgets
        extension in codeExtensions -> Icons.Default.Code
        extension in textExtensions -> Icons.Default.Description
        extension in archiveExtensions -> Icons.Default.Archive
        else -> Icons.Default.InsertDriveFile
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF81D4FA) else Color.Transparent)
            .combinedClickable(
                onClick = { if (hasSelection) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .size(34.dp)
                .background(if (apkIcon != null) Color.Transparent else iconBackground, RoundedCornerShape(7.dp))
                .clickable { onToggleSelect() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when {
                apkIcon != null -> {
                    Image(
                        bitmap = apkIcon,
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                }
                isNinePatch -> {
                    Text(".9", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                !isFolder && extension in fontExtensions -> {
                    Text("T", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                !isFolder && extension == "dex" -> {
                    Text("01", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                !isFolder && extension == "xml" -> {
                    Text("< >", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }
                !isFolder && (extension == "kt" || extension == "kts") -> {
                    Text("K", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                else -> {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 7.dp)) {
            Text(
                text = item.name,
                color = if (item.isRecentlyModified) ModifiedColor else NameColor,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.isParent) {
                val metadata = if (item.isDirectory) item.formattedDate() else "${item.formattedDate()} ${item.formattedSize()}"
                Text(metadata, color = MetadataColor, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
