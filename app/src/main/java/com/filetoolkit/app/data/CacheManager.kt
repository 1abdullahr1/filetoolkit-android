package com.filetoolkit.app.data

import android.content.Context
import java.io.File

object CacheManager {

    /**
     * Calculates total bytes consumed by temporary files in cache directory.
     */
    fun getCacheSizeBytes(context: Context): Long {
        return try {
            val cacheDir = context.cacheDir
            calculateDirectorySize(cacheDir)
        } catch (e: Exception) {
            0L
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) {
                calculateDirectorySize(f)
            } else {
                f.length()
            }
        }
        return size
    }

    /**
     * Deletes temporary conversion and inspection files older than maxAgeHours.
     */
    fun pruneOldCache(context: Context, maxAgeHours: Int = 24) {
        try {
            val cutoff = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)
            val files = context.cacheDir.listFiles() ?: return
            for (f in files) {
                if (f.lastModified() < cutoff) {
                    deleteRecursively(f)
                }
            }
        } catch (e: Exception) {
            // Ignore background cleanup errors
        }
    }

    /**
     * Purges all conversion and temporary cache files immediately.
     */
    fun clearAllCache(context: Context): Long {
        var freedBytes = 0L
        try {
            val files = context.cacheDir.listFiles() ?: return 0L
            for (f in files) {
                freedBytes += if (f.isDirectory) calculateDirectorySize(f) else f.length()
                deleteRecursively(f)
            }
        } catch (e: Exception) {
            // Ignore
        }
        return freedBytes
    }

    private fun deleteRecursively(fileOrDir: File) {
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { deleteRecursively(it) }
        }
        fileOrDir.delete()
    }
}
