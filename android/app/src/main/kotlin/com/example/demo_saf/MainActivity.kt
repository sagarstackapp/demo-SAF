package com.example.demo_saf

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.demo_saf/saf"
    private var pendingFilePickerResult: MethodChannel.Result? = null
    private var pendingDirectoryPickerResult: MethodChannel.Result? = null
    private val FILE_PICKER_REQUEST_CODE = 1001
    private val DIRECTORY_PICKER_REQUEST_CODE = 1002

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getAllFilesFromDirectory" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        try {
                            val files = getAllFilesFromDirectory(filePath)
                            result.success(files)
                        } catch (e: Exception) {
                            result.error("ERROR", "Failed to get files: ${e.message}", null)
                        }
                    } else {
                        result.error("ERROR", "File path is null", null)
                    }
                }
                "readFileContent" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        try {
                            val content = readFileContent(filePath)
                            result.success(content)
                        } catch (e: Exception) {
                            result.error("ERROR", "Failed to read file: ${e.message}", null)
                        }
                    } else {
                        result.error("ERROR", "File path is null", null)
                    }
                }
                "getFileUri" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        try {
                            val uri = getFileUri(filePath)
                            result.success(uri)
                        } catch (e: Exception) {
                            result.error("ERROR", "Failed to get URI: ${e.message}", null)
                        }
                    } else {
                        result.error("ERROR", "File path is null", null)
                    }
                }
                "pickFileWithSAF" -> {
                    // Native SAF file picker that returns original URI
                    try {
                        pendingFilePickerResult = result
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*" // Allow all file types
                        }
                        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to start file picker: ${e.message}", null)
                        pendingFilePickerResult = null
                    }
                }
                "pickDirectoryWithSAF" -> {
                    // Pick directory using ACTION_OPEN_DOCUMENT_TREE for persistent access
                    try {
                        pendingDirectoryPickerResult = result
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        }
                        startActivityForResult(intent, DIRECTORY_PICKER_REQUEST_CODE)
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to start directory picker: ${e.message}", null)
                        pendingDirectoryPickerResult = null
                    }
                }
                "listFilesInDirectory" -> {
                    // List all files in a directory tree URI
                    val directoryUri = call.argument<String>("directoryUri")
                    if (directoryUri != null) {
                        try {
                            val files = listFilesInDirectoryTree(directoryUri)
                            result.success(files)
                        } catch (e: Exception) {
                            result.error("ERROR", "Failed to list files: ${e.message}", null)
                        }
                    } else {
                        result.error("ERROR", "Directory URI is null", null)
                    }
                }
                "checkExistingPermissions" -> {
                    // Check if we already have persistable URI permissions
                    try {
                        val permissions = getPersistedUriPermissions()
                        val uris = permissions.map { it.uri.toString() }
                        result.success(uris)
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to check permissions: ${e.message}", null)
                    }
                }
                "getParentDirectoryUri" -> {
                    // Get parent directory URI from a file URI
                    val fileUri = call.argument<String>("fileUri")
                    if (fileUri != null) {
                        try {
                            val parentUri = getParentDirectoryUriFromFile(fileUri)
                            result.success(parentUri)
                        } catch (e: Exception) {
                            result.error("ERROR", "Failed to get parent directory: ${e.message}", null)
                        }
                    } else {
                        result.error("ERROR", "File URI is null", null)
                    }
                }
                "requestDirectoryAccess" -> {
                    // Request directory tree access for a specific directory
                    val directoryUri = call.argument<String>("directoryUri")
                    if (directoryUri != null) {
                        try {
                            pendingDirectoryPickerResult = result
                            // Open directory tree picker, but try to navigate to the specific directory
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                // Try to set the initial URI if possible
                                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(directoryUri))
                            }
                            startActivityForResult(intent, DIRECTORY_PICKER_REQUEST_CODE)
                        } catch (e: Exception) {
                            result.error("ERROR", "Failed to request directory access: ${e.message}", null)
                            pendingDirectoryPickerResult = null
                        }
                    } else {
                        result.error("ERROR", "Directory URI is null", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun getAllFilesFromDirectory(filePath: String): List<String> {
        try {
            // Check if it's a content:// URI (SAF) or a regular file path
            if (filePath.startsWith("content://")) {
                val fileUri = Uri.parse(filePath)
                android.util.Log.d("SAF", "Processing URI: $fileUri")
                
                // Check if it's a Downloads provider URI
                if (fileUri.authority == "com.android.providers.downloads.documents") {
                    return getAllFilesFromDownloadsProvider(fileUri)
                }
                
                // Use DocumentFile API for other SAF URIs
                val documentFile = DocumentFile.fromSingleUri(this, fileUri)
                
                if (documentFile == null || !documentFile.exists()) {
                    android.util.Log.e("SAF", "DocumentFile is null or doesn't exist")
                    return emptyList()
                }

                // Try to get parent directory
                val parentFile = documentFile.parentFile
                if (parentFile == null || !parentFile.exists()) {
                    android.util.Log.e("SAF", "Parent file is null or doesn't exist")
                    // If no parent accessible, return just this file
                    return listOf(fileUri.toString())
                }

                android.util.Log.d("SAF", "Parent file exists: ${parentFile.uri}, isDirectory: ${parentFile.isDirectory}")
                val files = mutableListOf<String>()
                
                // List ALL files in the parent directory (including zip, etc.)
                if (parentFile.isDirectory) {
                    try {
                        val children = parentFile.listFiles()
                        android.util.Log.d("SAF", "Found ${children?.size ?: 0} children in directory")
                        if (children != null) {
                            for (child in children) {
                                // Include all files regardless of type (json, zip, txt, etc.)
                                if (child.isFile) {
                                    android.util.Log.d("SAF", "Found file: ${child.name}")
                                    files.add(child.uri.toString())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // If we can't list directory, return just the selected file
                        android.util.Log.e("SAF", "Error listing directory: ${e.message}", e)
                        files.add(fileUri.toString())
                    }
                } else {
                    // If parent is not a directory, return just the selected file
                    files.add(fileUri.toString())
                }

                return files
            } else {
                // For regular file paths, use standard File API
                val file = File(filePath)
                if (!file.exists()) {
                    android.util.Log.e("SAF", "File does not exist: $filePath")
                    return emptyList()
                }
                
                val parentDir = file.parentFile
                if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory) {
                    android.util.Log.e("SAF", "Parent directory not accessible: ${file.parent}")
                    // If no parent directory, return just this file
                    return listOf(filePath)
                }
                
                android.util.Log.d("SAF", "Listing files in directory: ${parentDir.absolutePath}")
                val files = mutableListOf<String>()
                try {
                    val children = parentDir.listFiles()
                    if (children != null) {
                        android.util.Log.d("SAF", "Found ${children.size} items in directory")
                        for (child in children) {
                            // Include all files regardless of type (json, zip, txt, etc.)
                            if (child.isFile) {
                                android.util.Log.d("SAF", "Found file: ${child.name}")
                                files.add(child.absolutePath)
                            } else {
                                android.util.Log.d("SAF", "Skipping non-file: ${child.name} (isDirectory: ${child.isDirectory})")
                            }
                        }
                    } else {
                        android.util.Log.e("SAF", "listFiles() returned null")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SAF", "Error listing files: ${e.message}", e)
                }
                
                android.util.Log.d("SAF", "Total files found: ${files.size}")
                return files
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun readFileContent(filePath: String): String {
        try {
            // Check if it's a content:// URI or a regular file path
            if (filePath.startsWith("content://")) {
                // Use ContentResolver for SAF URIs (works with both document and tree URIs)
                val fileUri = Uri.parse(filePath)
                val contentResolver: ContentResolver = contentResolver
                
                return contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                } ?: throw Exception("Failed to open file")
            } else {
                // Use FileInputStream for regular file paths (cached files)
                val file = File(filePath)
                if (!file.exists()) {
                    throw Exception("File does not exist: $filePath")
                }
                
                return FileInputStream(file).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to read file: ${e.message}")
        }
    }

    private fun getFileUri(filePath: String): String {
        // If it's already a URI string, return it
        if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
            return filePath
        }
        
        // Otherwise, try to parse it as a URI
        val uri = Uri.parse(filePath)
        return uri.toString()
    }

    private fun getAllFilesFromDownloadsProvider(fileUri: Uri): List<String> {
        try {
            android.util.Log.d("SAF", "Handling Downloads provider URI")
            
            // Get the document ID from the URI
            val docId = DocumentsContract.getDocumentId(fileUri)
            android.util.Log.d("SAF", "Document ID: $docId")
            
            // Parse the document ID (format: "raw:/storage/emulated/0/Download/UPdate/filename.json")
            if (docId.startsWith("raw:/")) {
                // Fix: Keep the leading slash - path should be "/storage/..."
                val filePath = docId.substring(4) // Remove "raw:" prefix, keep the "/"
                android.util.Log.d("SAF", "File path from docId: $filePath")
                
                val file = File(filePath)
                if (!file.exists()) {
                    android.util.Log.e("SAF", "File does not exist: $filePath")
                    return emptyList()
                }
                
                val parentDir = file.parentFile
                if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory) {
                    android.util.Log.e("SAF", "Parent directory not accessible: ${file.parent}")
                    return listOf(fileUri.toString())
                }
                
                android.util.Log.d("SAF", "Listing files in directory: ${parentDir.absolutePath}")
                android.util.Log.d("SAF", "Directory exists: ${parentDir.exists()}, isDirectory: ${parentDir.isDirectory}, canRead: ${parentDir.canRead()}")
                
                val files = mutableListOf<String>()
                
                // For Downloads provider with raw:/ paths, we need to use MediaStore or File API
                // Since ContentResolver requires tree access which we don't have,
                // we'll use MediaStore API to query files, then fallback to File API
                try {
                    // Try MediaStore API first (works for Downloads folder on Android 10+)
                    val directoryPath = parentDir.absolutePath
                    android.util.Log.d("SAF", "Querying MediaStore for directory: $directoryPath")
                    
                    val projection = arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DATA,
                        MediaStore.Files.FileColumns.DISPLAY_NAME
                    )
                    
                    // Query all files in the directory
                    val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
                    val selectionArgs = arrayOf("$directoryPath/%")
                    
                    val mediaCursor = contentResolver.query(
                        MediaStore.Files.getContentUri("external"),
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )
                    
                    if (mediaCursor != null && mediaCursor.count > 0) {
                        android.util.Log.d("SAF", "Found ${mediaCursor.count} items via MediaStore")
                        while (mediaCursor.moveToNext()) {
                            val fileData = mediaCursor.getString(mediaCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                            val fileName = mediaCursor.getString(mediaCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                            
                            // Verify the file is actually in our target directory
                            if (fileData != null && fileData.startsWith(directoryPath)) {
                                android.util.Log.d("SAF", "Found file via MediaStore: $fileName")
                                try {
                                    // Build content URI for the file
                                    val childDocId = "raw:$fileData"
                                    val childUri = DocumentsContract.buildDocumentUri(
                                        "com.android.providers.downloads.documents",
                                        childDocId
                                    )
                                    files.add(childUri.toString())
                                } catch (e: Exception) {
                                    android.util.Log.w("SAF", "Could not build URI for $fileName, using path")
                                    files.add(fileData)
                                }
                            }
                        }
                        mediaCursor.close()
                    } else {
                        android.util.Log.w("SAF", "MediaStore query returned null or empty, trying File API")
                        // Fallback to File API
                        try {
                            val children = parentDir.listFiles()
                            if (children != null) {
                                android.util.Log.d("SAF", "Found ${children.size} items using File API")
                                for (child in children) {
                                    if (child.isFile) {
                                        android.util.Log.d("SAF", "Found file: ${child.name}")
                                        try {
                                            val childDocId = "raw:${child.absolutePath}"
                                            val childUri = DocumentsContract.buildDocumentUri(
                                                "com.android.providers.downloads.documents",
                                                childDocId
                                            )
                                            files.add(childUri.toString())
                                        } catch (e: Exception) {
                                            android.util.Log.w("SAF", "Could not build URI for ${child.name}, using path")
                                            files.add(child.absolutePath)
                                        }
                                    }
                                }
                            } else {
                                android.util.Log.e("SAF", "File API also returned null")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SAF", "Error using File API: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SAF", "Error querying directory: ${e.message}", e)
                    e.printStackTrace()
                }
                
                android.util.Log.d("SAF", "Total files found: ${files.size}")
                return files
            } else {
                // Handle other document ID formats
                android.util.Log.d("SAF", "Document ID format: $docId")
                // Try to get parent using DocumentFile
                val documentFile = DocumentFile.fromSingleUri(this, fileUri)
                val parentFile = documentFile?.parentFile
                if (parentFile != null && parentFile.exists() && parentFile.isDirectory) {
                    val files = mutableListOf<String>()
                    val children = parentFile.listFiles()
                    if (children != null) {
                        for (child in children) {
                            if (child.isFile) {
                                files.add(child.uri.toString())
                            }
                        }
                    }
                    return files
                }
            }
            
            return listOf(fileUri.toString())
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error in getAllFilesFromDownloadsProvider: ${e.message}", e)
            e.printStackTrace()
            return emptyList()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == FILE_PICKER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null && pendingFilePickerResult != null) {
                    // Return the original SAF URI
                    pendingFilePickerResult?.success(uri.toString())
                    pendingFilePickerResult = null
                } else {
                    pendingFilePickerResult?.error("ERROR", "No file selected", null)
                    pendingFilePickerResult = null
                }
            } else {
                pendingFilePickerResult?.error("ERROR", "File picker cancelled", null)
                pendingFilePickerResult = null
            }
        } else if (requestCode == DIRECTORY_PICKER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val treeUri = data.data
                if (treeUri != null && pendingDirectoryPickerResult != null) {
                    // Take persistable URI permission
                    try {
                        contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        android.util.Log.d("SAF", "Persistent permission granted for: $treeUri")
                        // Return the tree URI
                        pendingDirectoryPickerResult?.success(treeUri.toString())
                    } catch (e: Exception) {
                        android.util.Log.e("SAF", "Error taking persistable permission: ${e.message}", e)
                        // Still return the URI even if permission fails
                        pendingDirectoryPickerResult?.success(treeUri.toString())
                    }
                    pendingDirectoryPickerResult = null
                } else {
                    pendingDirectoryPickerResult?.error("ERROR", "No directory selected", null)
                    pendingDirectoryPickerResult = null
                }
            } else {
                pendingDirectoryPickerResult?.error("ERROR", "Directory picker cancelled", null)
                pendingDirectoryPickerResult = null
            }
        }
    }

    private fun listFilesInDirectoryTree(treeUriString: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val treeUri = Uri.parse(treeUriString)
            android.util.Log.d("SAF", "Listing files in directory tree: $treeUri")
            
            // Use DocumentFile to access the tree
            val treeDocumentFile = DocumentFile.fromTreeUri(this, treeUri)
            if (treeDocumentFile == null || !treeDocumentFile.exists() || !treeDocumentFile.isDirectory) {
                android.util.Log.e("SAF", "Tree URI is invalid or not a directory")
                return emptyList()
            }
            
            // List all files recursively (or just direct children)
            listFilesRecursive(treeDocumentFile, files)
            
            android.util.Log.d("SAF", "Found ${files.size} files in directory")
            return files
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error listing files: ${e.message}", e)
            return emptyList()
        }
    }

    private fun listFilesRecursive(documentFile: DocumentFile, files: MutableList<String>) {
        try {
            val children = documentFile.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isFile) {
                        // Add file URI
                        files.add(child.uri.toString())
                        android.util.Log.d("SAF", "Found file: ${child.name}")
                    } else if (child.isDirectory) {
                        // Recursively list files in subdirectories
                        listFilesRecursive(child, files)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error listing files recursively: ${e.message}", e)
        }
    }

    private fun getPersistedUriPermissions(): List<android.content.UriPermission> {
        return contentResolver.persistedUriPermissions
    }

    private fun getParentDirectoryUriFromFile(fileUriString: String): String? {
        try {
            val fileUri = Uri.parse(fileUriString)
            android.util.Log.d("SAF", "Getting parent directory for file: $fileUri")
            
            // Use DocumentFile to get parent
            val documentFile = DocumentFile.fromSingleUri(this, fileUri)
            if (documentFile != null && documentFile.exists()) {
                val parentFile = documentFile.parentFile
                if (parentFile != null && parentFile.exists()) {
                    val parentUri = parentFile.uri
                    android.util.Log.d("SAF", "Parent directory URI: $parentUri")
                    return parentUri.toString()
                }
            }
            
            // If DocumentFile doesn't work, try to build tree URI from document ID
            if (fileUri.authority == "com.android.providers.downloads.documents") {
                val docId = DocumentsContract.getDocumentId(fileUri)
                if (docId.startsWith("raw:/")) {
                    val filePath = docId.substring(4) // Remove "raw:" prefix
                    val file = File(filePath)
                    val parentDir = file.parentFile
                    if (parentDir != null) {
                        val parentDocId = "raw:${parentDir.absolutePath}"
                        val parentTreeUri = DocumentsContract.buildTreeDocumentUri(
                            "com.android.providers.downloads.documents",
                            parentDocId
                        )
                        android.util.Log.d("SAF", "Built parent tree URI: $parentTreeUri")
                        return parentTreeUri.toString()
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error getting parent directory: ${e.message}", e)
            return null
        }
    }
}
