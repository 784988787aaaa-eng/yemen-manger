package com.example.mtcore.domain

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val isParent: Boolean = false,
    val isRecentlyModified: Boolean = false
) {
    fun formattedDate(): String {
        return SimpleDateFormat("yy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
    }

    fun formattedSize(): String {
        var size = file.length().toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var index = 0
        while (size >= 1024.0 && index < units.lastIndex) {
            size /= 1024.0
            index += 1
        }
        return if (index == 0) "${size.toLong()}${units[index]}" else String.format(Locale.US, "%.2f%s", size, units[index])
    }
}
