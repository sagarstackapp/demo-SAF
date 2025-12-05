package com.example.demo_saf

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SAF_DEBUG", "=== MainActivity onCreate ===")
        android.util.Log.d("SAF_DEBUG", "Saved instance state: ${savedInstanceState != null}")
        if (savedInstanceState != null) {
            android.util.Log.d("SAF_DEBUG", "Activity is being recreated from saved state")
            android.util.Log.d("SAF_DEBUG", "This might cause pendingFilePickerResult to be lost!")
        }
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("SAF_DEBUG", "=== MainActivity onResume ===")
        android.util.Log.d("SAF_DEBUG", "Pending file picker result: ${pendingFilePickerResult != null}")
        android.util.Log.d("SAF_DEBUG", "Pending directory picker result: ${pendingDirectoryPickerResult != null}")
        
        // Check if we have pending results that might have been lost
        // If we come back from file picker and no result was received, it was likely cancelled
        if (pendingFilePickerResult != null) {
            android.util.Log.d("SAF_DEBUG", "Pending file picker result exists on resume")
            android.util.Log.d("SAF_DEBUG", "Waiting to see if onActivityResult gets called...")
            
            // Wait a bit to see if onActivityResult gets called
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (pendingFilePickerResult != null) {
                    android.util.Log.w("SAF_DEBUG", "WARNING: File picker result still pending after resume delay")
                    android.util.Log.w("SAF_DEBUG", "This means onActivityResult was NOT called - user likely cancelled")
                    android.util.Log.w("SAF_DEBUG", "OR there's an issue with activity result handling")
                    // Don't error here automatically - user might have cancelled
                    // But log it so we can see what's happening
                } else {
                    android.util.Log.d("SAF_DEBUG", "File picker result was handled (pendingFilePickerResult is now null)")
                }
            }, 1500) // Wait 1.5 seconds for onActivityResult
        }
        if (pendingDirectoryPickerResult != null) {
            android.util.Log.d("SAF_DEBUG", "Pending directory picker result exists on resume")
        }
    }
    
    override fun onPause() {
        super.onPause()
        android.util.Log.d("SAF_DEBUG", "MainActivity onPause")
        android.util.Log.d("SAF_DEBUG", "Pending file picker result on pause: ${pendingFilePickerResult != null}")
        android.util.Log.d("SAF_DEBUG", "Pending directory picker result on pause: ${pendingDirectoryPickerResult != null}")
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("SAF_DEBUG", "MainActivity onNewIntent: $intent")
        setIntent(intent) // Important: update the intent for singleTop activities
    }
    
    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        android.util.Log.d("SAF_DEBUG", "MainActivity onSaveInstanceState")
    }
    
    override fun onRestoreInstanceState(savedInstanceState: android.os.Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        android.util.Log.d("SAF_DEBUG", "MainActivity onRestoreInstanceState")
    }

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
                    // Navigate directly to Mobile/Download to avoid Downloads side menu restrictions
                    try {
                        android.util.Log.d("SAF", "Setting up file picker - pendingFilePickerResult set")
                        android.util.Log.d("SAF", "Android version: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
                        pendingFilePickerResult = result
                        
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13+ - Try ACTION_OPEN_DOCUMENT_TREE approach (as suggested by Gemini)
                            // This is more reliable on Android 13+ for accessing Downloads folder
                            // User will select Downloads folder, then we can access files within it
                            android.util.Log.d("SAF", "Android 13+ detected - using ACTION_OPEN_DOCUMENT_TREE approach")
                            
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                // Grant permissions for persistent access
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                
                                // Try to set EXTRA_INITIAL_URI to Downloads folder for better UX
                                // This guides user to Downloads folder, but may not work on all devices
                                try {
                                    val downloadUri = DocumentsContract.buildDocumentUri(
                                        "com.android.externalstorage.documents",
                                        "primary:Download"
                                    )
                                    if (downloadUri != null) {
                                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                                        android.util.Log.d("SAF", "Set EXTRA_INITIAL_URI to navigate to Mobile/Download: $downloadUri")
                                    } else {
                                        android.util.Log.w("SAF", "Could not build Download URI for EXTRA_INITIAL_URI")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("SAF", "Could not set EXTRA_INITIAL_URI: ${e.message}")
                                    android.util.Log.d("SAF", "Continuing without EXTRA_INITIAL_URI - user will navigate manually")
                                    // Continue without EXTRA_INITIAL_URI - user will need to navigate to Downloads manually
                                }
                                
                                android.util.Log.d("SAF", "Intent configured with ACTION_OPEN_DOCUMENT_TREE for Android 13")
                                
                                // Verify intent can be resolved
                                val resolveInfo = packageManager.resolveActivity(this, PackageManager.MATCH_DEFAULT_ONLY)
                                if (resolveInfo == null) {
                                    android.util.Log.e("SAF", "ACTION_OPEN_DOCUMENT_TREE cannot be resolved!")
                                } else {
                                    android.util.Log.d("SAF", "ACTION_OPEN_DOCUMENT_TREE can be resolved: ${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}")
                                }
                            }
                        } else {
                            // Android 8-12 - Use standard setup with EXTRA_INITIAL_URI
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*" // Allow all file types
                                
                                // Build URI for Mobile/Download path (external storage)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val downloadUri = DocumentsContract.buildDocumentUri(
                                            "com.android.externalstorage.documents",
                                            "primary:Download"
                                        )
                                        if (downloadUri != null) {
                                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                                            android.util.Log.d("SAF", "Set EXTRA_INITIAL_URI to navigate to Mobile/Download: $downloadUri")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("SAF", "Could not set EXTRA_INITIAL_URI: ${e.message}")
                                    }
                                }
                            }
                        }
                        
                        android.util.Log.d("SAF", "Starting file picker activity with request code: $FILE_PICKER_REQUEST_CODE")
                        android.util.Log.d("SAF", "Intent action: ${intent.action}")
                        android.util.Log.d("SAF", "Intent type: ${intent.type}")
                        android.util.Log.d("SAF", "Intent categories: ${intent.categories}")
                        android.util.Log.d("SAF", "Intent extras: ${intent.extras}")
                        
                        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
                        android.util.Log.d("SAF", "File picker activity started")
                    } catch (e: Exception) {
                        android.util.Log.e("SAF", "Error starting file picker: ${e.message}", e)
                        e.printStackTrace()
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
                    android.util.Log.d("SAF_DEBUG", "=== listFilesInDirectory called ===")
                    android.util.Log.d("SAF_DEBUG", "Directory URI argument: $directoryUri")
                    if (directoryUri != null) {
                        try {
                            val files = listFilesInDirectoryTree(directoryUri)
                            android.util.Log.d("SAF_DEBUG", "listFilesInDirectoryTree returned ${files.size} files")
                            result.success(files)
                        } catch (e: Exception) {
                            android.util.Log.e("SAF_DEBUG", "ERROR in listFilesInDirectory: ${e.message}", e)
                            e.printStackTrace()
                            result.error("ERROR", "Failed to list files: ${e.message}", null)
                        }
                    } else {
                        android.util.Log.e("SAF_DEBUG", "ERROR: Directory URI is null")
                        result.error("ERROR", "Directory URI is null", null)
                    }
                }
                "checkExistingPermissions" -> {
                    // Check if we already have persistable URI permissions
                    try {
                        val permissions = getPersistedUriPermissions()
                        val uris = permissions.map { it.uri.toString() }
                        android.util.Log.d("SAF", "Found ${uris.size} persisted URI permissions")
                        uris.forEach { uri ->
                            android.util.Log.d("SAF", "Persisted permission: $uri")
                        }
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
                            val parsedUri = Uri.parse(directoryUri)
                            
                            android.util.Log.d("SAF", "Requesting directory access for: $parsedUri")
                            
                            // For EXTRA_INITIAL_URI, we need a document URI (not tree URI) for proper navigation
                            // The picker will navigate to this location and show the "Allow access" button
                            val navigationUri = if (DocumentsContract.isTreeUri(parsedUri)) {
                                // If it's a tree URI, try to convert it to document URI for navigation
                                // Tree URI format: content://authority/tree/documentId
                                // Document URI format: content://authority/document/documentId
                                try {
                                    val treeDocId = DocumentsContract.getTreeDocumentId(parsedUri)
                                    val docUri = DocumentsContract.buildDocumentUri(
                                        parsedUri.authority,
                                        treeDocId
                                    )
                                    android.util.Log.d("SAF", "Converted tree URI to document URI for navigation: $docUri")
                                    docUri
                                } catch (e: Exception) {
                                    android.util.Log.w("SAF", "Could not convert tree URI to document URI: ${e.message}")
                                    parsedUri // Fallback to original
                                }
                            } else {
                                // Already a document URI - perfect for navigation
                                parsedUri
                            }
                            
                            android.util.Log.d("SAF", "Using navigation URI: $navigationUri")
                            
                            // For external storage, ensure we have the correct document URI format
                            if (navigationUri.authority == "com.android.externalstorage.documents") {
                                // The URI should already be correct, but log it for debugging
                                android.util.Log.d("SAF", "External storage document URI format verified")
                            }
                            
                            // Open directory tree picker, but try to navigate to the specific directory
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                // EXTRA_INITIAL_URI was added in API 26 (Android 8.0)
                                // This helps navigate to the specific subfolder on supported versions
                                // Note: EXTRA_INITIAL_URI works better with document URIs for navigation
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, navigationUri)
                                        android.util.Log.d("SAF", "Set EXTRA_INITIAL_URI to: $navigationUri")
                                    } catch (e: Exception) {
                                        android.util.Log.w("SAF", "Could not set EXTRA_INITIAL_URI: ${e.message}")
                                        // Continue without initial URI - user will need to navigate manually
                                    }
                                } else {
                                    android.util.Log.d("SAF", "EXTRA_INITIAL_URI not available on API ${Build.VERSION.SDK_INT}")
                                }
                            }
                            startActivityForResult(intent, DIRECTORY_PICKER_REQUEST_CODE)
                        } catch (e: Exception) {
                            android.util.Log.e("SAF", "Error requesting directory access: ${e.message}", e)
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
                var fileUri = Uri.parse(filePath)
                android.util.Log.d("SAF", "Processing URI: $fileUri")
                
                // Convert Downloads provider URI to external storage provider URI immediately
                // This ensures we use the Mobile > Download path instead of Downloads side menu
                if (fileUri.authority == "com.android.providers.downloads.documents") {
                    android.util.Log.d("SAF", "Downloads provider detected, converting to external storage provider")
                    val convertedUri = convertDownloadsProviderToExternalStorage(fileUri)
                    if (convertedUri != null) {
                        android.util.Log.d("SAF", "Using converted external storage URI: $convertedUri")
                        fileUri = convertedUri
                    } else {
                        android.util.Log.w("SAF", "Could not convert, using Downloads provider fallback")
                        return getAllFilesFromDownloadsProvider(fileUri)
                    }
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
                    android.util.Log.w("SAF", "Parent file is null or doesn't exist via DocumentFile API")
                    android.util.Log.d("SAF", "This means we need to request tree access for the parent directory")
                    
                    // Return empty list to indicate parent access failed
                    // This will trigger the tree access request flow
                    return emptyList()
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
            android.util.Log.d("SAF", "Handling Downloads provider URI: $fileUri")
            
            // First, try using DocumentFile API directly (works better on Android 11+)
            // This is the preferred method as it uses SAF permissions we already have
            try {
                val documentFile = DocumentFile.fromSingleUri(this, fileUri)
                if (documentFile != null && documentFile.exists()) {
                    val parentFile = documentFile.parentFile
                    if (parentFile != null && parentFile.exists() && parentFile.isDirectory) {
                        android.util.Log.d("SAF", "Using DocumentFile API to access parent directory")
                        
                        // Check if parent is Downloads root (restricted on Android 11+)
                        val parentDocId = try {
                            DocumentsContract.getDocumentId(parentFile.uri)
                        } catch (e: Exception) {
                            null
                        }
                        
                        // On Android 11+, Downloads root access is restricted
                        // If parent is Downloads root, we might not be able to list files
                        val isDownloadsRoot = parentDocId?.let { 
                            it == "downloads" || it == "msf:downloads" || it.contains("downloads") && !it.contains("/")
                        } ?: false
                        
                        if (isDownloadsRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            android.util.Log.w("SAF", "Downloads root access restricted on Android 11+, using MediaStore fallback")
                            // Fall through to MediaStore/File API fallback
                        } else {
                            val files = mutableListOf<String>()
                            try {
                                val children = parentFile.listFiles()
                                android.util.Log.d("SAF", "Found ${children?.size ?: 0} children via DocumentFile")
                                if (children != null) {
                                    for (child in children) {
                                        if (child.isFile) {
                                            android.util.Log.d("SAF", "Found file via DocumentFile: ${child.name}")
                                            files.add(child.uri.toString())
                                        }
                                    }
                                }
                                if (files.isNotEmpty()) {
                                    android.util.Log.d("SAF", "Successfully listed ${files.size} files using DocumentFile API")
                                    return files
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("SAF", "DocumentFile API failed: ${e.message}, trying fallback")
                            }
                        }
                    } else {
                        android.util.Log.d("SAF", "Parent file not accessible, might be Downloads root")
                        // Try converting Downloads provider to external storage provider
                        // This works better on Android 11+ where Downloads provider is restricted
                        android.util.Log.d("SAF", "Attempting to convert Downloads provider to external storage provider")
                        val externalStorageUri = convertDownloadsProviderToExternalStorage(fileUri)
                        if (externalStorageUri != null) {
                            android.util.Log.d("SAF", "Converted to external storage URI, trying to access parent")
                            // Try to get parent using external storage URI
                            val externalDocFile = DocumentFile.fromSingleUri(this, externalStorageUri)
                            val externalParentFile = externalDocFile?.parentFile
                            if (externalParentFile != null && externalParentFile.exists() && externalParentFile.isDirectory) {
                                android.util.Log.d("SAF", "Successfully accessed parent via external storage provider")
                                val files = mutableListOf<String>()
                                try {
                                    val children = externalParentFile.listFiles()
                                    android.util.Log.d("SAF", "Found ${children?.size ?: 0} children via external storage provider")
                                    if (children != null) {
                                        for (child in children) {
                                            if (child.isFile) {
                                                android.util.Log.d("SAF", "Found file via external storage: ${child.name}")
                                                files.add(child.uri.toString())
                                            }
                                        }
                                    }
                                    if (files.isNotEmpty()) {
                                        android.util.Log.d("SAF", "Successfully listed ${files.size} files using external storage provider")
                                        return files
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("SAF", "External storage provider failed: ${e.message}")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SAF", "DocumentFile approach failed: ${e.message}, trying fallback")
            }
            
            // Fallback: Try using document ID parsing (for Android 9/10 compatibility)
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
                    return listOf(fileUri.toString())
                }
                
                val parentDir = file.parentFile
                if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory) {
                    android.util.Log.e("SAF", "Parent directory not accessible: ${file.parent}")
                    return listOf(fileUri.toString())
                }
                
                // Check if parent is Downloads root directory
                val isDownloadsRoot = parentDir.absolutePath.endsWith("/Download") || 
                                     parentDir.absolutePath.endsWith("/Downloads") ||
                                     parentDir.absolutePath.contains("/Download") && !parentDir.absolutePath.contains("/Download/")
                
                android.util.Log.d("SAF", "Listing files in directory: ${parentDir.absolutePath}")
                android.util.Log.d("SAF", "Is Downloads root: $isDownloadsRoot")
                android.util.Log.d("SAF", "Directory exists: ${parentDir.exists()}, isDirectory: ${parentDir.isDirectory}, canRead: ${parentDir.canRead()}")
                
                val files = mutableListOf<String>()
                
                // For Downloads provider with raw:/ paths, we need to use MediaStore or File API
                // Since ContentResolver requires tree access which we don't have,
                // we'll use MediaStore API to query files, then fallback to File API
                try {
                    val directoryPath = parentDir.absolutePath
                    android.util.Log.d("SAF", "Querying MediaStore for directory: $directoryPath")
                    
                    // MediaStore API behavior differs by Android version:
                    // - Android 9 and below: Full access via MediaStore
                    // - Android 10+: Scoped storage limits MediaStore access
                    // - Android 11+: Downloads root access restricted, but subfolders accessible
                    
                    val projection = arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DATA,
                        MediaStore.Files.FileColumns.DISPLAY_NAME
                    )
                    
                    // Query all files in the directory
                    // Note: On Android 10+, MediaStore may not return all files due to scoped storage
                    // For Downloads root, we need to query more broadly
                    val selection = if (isDownloadsRoot) {
                        // For Downloads root, query all files in Downloads directory
                        "${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.DATA} = ?"
                    } else {
                        "${MediaStore.Files.FileColumns.DATA} LIKE ?"
                    }
                    val selectionArgs = if (isDownloadsRoot) {
                        arrayOf("$directoryPath/%", directoryPath)
                    } else {
                        arrayOf("$directoryPath/%")
                    }
                    
                    android.util.Log.d("SAF", "MediaStore query - selection: $selection, args: ${selectionArgs.joinToString()}")
                    
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
                        // Note: On Android 10+, direct file access may be restricted
                        // This fallback works better on Android 9 and below
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
                                            // On Android 10+, if File API fails, we might need SAF tree access
                                            files.add(child.absolutePath)
                                        }
                                    }
                                }
                            } else {
                                android.util.Log.e("SAF", "File API also returned null")
                                // On Android 11+, this might fail for Downloads subfolders
                                // User will need to grant tree access via SAF
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SAF", "Error using File API: ${e.message}", e)
                            // On Android 10+, this is expected for restricted directories
                            // The app should request tree access via SAF instead
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
        
        android.util.Log.d("SAF_DEBUG", "=== onActivityResult called ===")
        android.util.Log.d("SAF_DEBUG", "Request code: $requestCode (expected: $FILE_PICKER_REQUEST_CODE or $DIRECTORY_PICKER_REQUEST_CODE)")
        android.util.Log.d("SAF_DEBUG", "Result code: $resultCode (RESULT_OK=${Activity.RESULT_OK}, RESULT_CANCELED=${Activity.RESULT_CANCELED})")
        android.util.Log.d("SAF_DEBUG", "Data: $data")
        android.util.Log.d("SAF_DEBUG", "Data URI: ${data?.data}")
        android.util.Log.d("SAF_DEBUG", "Data type: ${data?.type}")
        android.util.Log.d("SAF_DEBUG", "Pending file picker result: ${pendingFilePickerResult != null}")
        android.util.Log.d("SAF_DEBUG", "Pending directory picker result: ${pendingDirectoryPickerResult != null}")
        
        if (requestCode == FILE_PICKER_REQUEST_CODE) {
            android.util.Log.d("SAF_DEBUG", "File picker request code matches!")
            android.util.Log.d("SAF_DEBUG", "File picker result received - resultCode: $resultCode")
            if (resultCode == Activity.RESULT_OK && data != null) {
                var uri = data.data
                android.util.Log.d("SAF_DEBUG", "File picker returned URI: $uri")
                if (uri != null && pendingFilePickerResult != null) {
                    android.util.Log.d("SAF_DEBUG", "URI authority: ${uri.authority}")
                    android.util.Log.d("SAF_DEBUG", "Is tree URI: ${DocumentsContract.isTreeUri(uri)}")
                    
                    // On Android 13+, we use ACTION_OPEN_DOCUMENT_TREE which returns a tree URI
                    // We need to handle this differently - get the tree URI and then list files
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && DocumentsContract.isTreeUri(uri)) {
                        android.util.Log.d("SAF_DEBUG", "Android 13+ tree URI detected - this is from ACTION_OPEN_DOCUMENT_TREE")
                        android.util.Log.d("SAF_DEBUG", "Tree URI: $uri")
                        
                        // Take persistable URI permission for the tree
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            android.util.Log.d("SAF_DEBUG", "Persistent permission granted for tree: $uri")
                        } catch (e: Exception) {
                            android.util.Log.e("SAF_DEBUG", "Could not take persistable permission for tree: ${e.message}", e)
                        }
                        
                        // For Android 13+, return the tree URI
                        // The Dart side will detect it's a tree URI and call listFilesInDirectory
                        android.util.Log.d("SAF_DEBUG", "Returning tree URI to Flutter (Android 13+): $uri")
                        android.util.Log.d("SAF_DEBUG", "Dart side will detect tree URI and call listFilesInDirectory")
                        pendingFilePickerResult?.success(uri.toString())
                        pendingFilePickerResult = null
                        return
                    }
                    
                    // For Android < 13, handle file URI as before
                    // Convert Downloads provider URI to external storage provider URI
                    // This bypasses Android 11+ restrictions on Downloads provider
                    if (uri.authority == "com.android.providers.downloads.documents") {
                        android.util.Log.d("SAF", "Downloads provider detected, converting to external storage provider")
                        try {
                            val convertedUri = convertDownloadsProviderToExternalStorage(uri)
                            if (convertedUri != null) {
                                android.util.Log.d("SAF", "Successfully converted URI from Downloads provider to external storage provider")
                                android.util.Log.d("SAF", "Original: $uri")
                                android.util.Log.d("SAF", "Converted: $convertedUri")
                                uri = convertedUri
                            } else {
                                android.util.Log.w("SAF", "Could not convert Downloads provider URI, using original")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SAF", "Error during conversion: ${e.message}", e)
                            e.printStackTrace()
                            // Continue with original URI if conversion fails
                        }
                    } else {
                        android.util.Log.d("SAF", "Not Downloads provider, using URI as-is")
                    }
                    
                    // Take persistable URI permission for the picked file
                    // This is CRITICAL for Android 13+ - according to Android docs, we MUST persist permissions
                    // ACTION_OPEN_DOCUMENT provides persistable URIs, so this should work
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        android.util.Log.d("SAF", "Persistent permission granted for picked file: $uri")
                    } catch (e: Exception) {
                        android.util.Log.e("SAF", "CRITICAL: Could not take persistable permission: ${e.message}")
                        android.util.Log.e("SAF", "This means the file access will be temporary - app restart will lose access")
                        e.printStackTrace()
                        // Continue anyway - we'll still have temporary access, but this is not ideal
                        // According to Android docs, ACTION_OPEN_DOCUMENT should support persistable permissions
                    }
                    
                    // Return the URI (converted if needed)
                    android.util.Log.d("SAF", "Returning URI to Flutter: $uri")
                    pendingFilePickerResult?.success(uri.toString())
                    pendingFilePickerResult = null
                } else {
                    android.util.Log.e("SAF", "URI is null or pendingFilePickerResult is null")
                    pendingFilePickerResult?.error("ERROR", "No file selected", null)
                    pendingFilePickerResult = null
                }
            } else {
                android.util.Log.w("SAF", "File picker cancelled or no data - resultCode: $resultCode")
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
            android.util.Log.d("SAF_DEBUG", "=== Starting listFilesInDirectoryTree ===")
            android.util.Log.d("SAF_DEBUG", "Tree URI received: $treeUri")
            
            // Verify it's a tree URI
            if (!DocumentsContract.isTreeUri(treeUri)) {
                android.util.Log.e("SAF_DEBUG", "ERROR: URI is not a tree URI: $treeUri")
                return emptyList()
            }
            
            // Use DocumentFile API as primary method - it handles ExternalStorageProvider errors better
            // ContentResolver.query can fail with ExternalStorageProvider issues (like /storage/emulated errors)
            android.util.Log.d("SAF_DEBUG", "Using DocumentFile API (more reliable for ExternalStorageProvider)")
            return listFilesUsingDocumentFile(treeUri)
            
        } catch (e: Exception) {
            android.util.Log.e("SAF_DEBUG", "ERROR in listFilesInDirectoryTree: ${e.message}", e)
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Primary method using DocumentFile API - handles ExternalStorageProvider better
     */
    private fun listFilesUsingDocumentFile(treeUri: Uri): List<String> {
        val files = mutableListOf<String>()
        try {
            android.util.Log.d("SAF_DEBUG", "Using DocumentFile fallback for: $treeUri")
            val treeDocumentFile = DocumentFile.fromTreeUri(this, treeUri)
            if (treeDocumentFile == null || !treeDocumentFile.exists()) {
                android.util.Log.e("SAF_DEBUG", "DocumentFile is null or doesn't exist")
                return emptyList()
            }
            
            if (!treeDocumentFile.isDirectory) {
                android.util.Log.e("SAF_DEBUG", "DocumentFile is not a directory")
                return emptyList()
            }
            
            android.util.Log.d("SAF_DEBUG", "DocumentFile directory exists: ${treeDocumentFile.name}")
            listFilesRecursive(treeDocumentFile, files)
            android.util.Log.d("SAF_DEBUG", "DocumentFile found ${files.size} files")
            return files
        } catch (e: Exception) {
            android.util.Log.e("SAF_DEBUG", "ERROR in DocumentFile fallback: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Find a subfolder within a tree DocumentFile by matching document ID
     */
    private fun findSubfolderInTree(treeDocumentFile: DocumentFile, targetDocId: String): DocumentFile? {
        try {
            // For Downloads provider with raw:/ paths, extract the path
            if (targetDocId.startsWith("raw:/")) {
                val targetPath = targetDocId.substring(4)
                return findSubfolderByPath(treeDocumentFile, targetPath)
            }
            
            // For other providers, try to match by name/path
            val children = treeDocumentFile.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) {
                        // Try to get document ID for this child
                        try {
                            val childDocId = DocumentsContract.getDocumentId(child.uri)
                            if (childDocId == targetDocId || targetDocId.endsWith(childDocId)) {
                                return child
                            }
                            // Recursively search in subdirectories
                            val found = findSubfolderInTree(child, targetDocId)
                            if (found != null) {
                                return found
                            }
                        } catch (e: Exception) {
                            // Continue searching
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error finding subfolder: ${e.message}", e)
        }
        return null
    }
    
    /**
     * Find a subfolder by matching file path (for Downloads provider)
     */
    private fun findSubfolderByPath(treeDocumentFile: DocumentFile, targetPath: String): DocumentFile? {
        try {
            val children = treeDocumentFile.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) {
                        try {
                            val childDocId = DocumentsContract.getDocumentId(child.uri)
                            if (childDocId.startsWith("raw:/")) {
                                val childPath = childDocId.substring(4)
                                if (targetPath == childPath || targetPath.startsWith(childPath + "/")) {
                                    if (targetPath == childPath) {
                                        return child
                                    } else {
                                        // Continue searching in this subfolder
                                        val found = findSubfolderByPath(child, targetPath)
                                        if (found != null) {
                                            return found
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Continue searching
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error finding subfolder by path: ${e.message}", e)
        }
        return null
    }

    private fun listFilesRecursive(documentFile: DocumentFile, files: MutableList<String>) {
        try {
            if (!documentFile.exists()) {
                android.util.Log.w("SAF_DEBUG", "Cannot read directory (doesn't exist): ${documentFile.name}")
                return
            }
            
            if (!documentFile.canRead()) {
                android.util.Log.w("SAF_DEBUG", "Cannot read directory (no permission): ${documentFile.name}")
                return
            }
            
            android.util.Log.d("SAF_DEBUG", "Listing files in directory: ${documentFile.name}")
            val children = documentFile.listFiles()
            
            if (children != null) {
                android.util.Log.d("SAF_DEBUG", "Found ${children.size} items in: ${documentFile.name}")
                for (child in children) {
                    try {
                        if (child.isFile) {
                            // Add file URI
                            files.add(child.uri.toString())
                            android.util.Log.i("SAF_RESULT", "Found file: ${child.name}")
                        } else if (child.isDirectory) {
                            android.util.Log.d("SAF_DEBUG", "Entering subdirectory: ${child.name}")
                            // Recursively list files in subdirectories
                            // This is crucial for accessing files in subfolders
                            listFilesRecursive(child, files)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SAF_DEBUG", "Error processing item ${child.name}: ${e.message}")
                        // Continue with next item instead of stopping
                    }
                }
            } else {
                android.util.Log.w("SAF_DEBUG", "listFiles() returned null for: ${documentFile.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF_DEBUG", "Error listing files recursively in ${documentFile.name}: ${e.message}", e)
            // Don't throw - just log and continue
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
                    android.util.Log.d("SAF", "Parent directory URI (document): $parentUri")
                    
                    // Convert document URI to tree URI for proper directory access
                    val treeUri = convertDocumentUriToTreeUri(parentUri)
                    if (treeUri != null) {
                        android.util.Log.d("SAF", "Converted to tree URI: $treeUri")
                        return treeUri.toString()
                    }
                    
                    // Fallback: return document URI (will need conversion later)
                    return parentUri.toString()
                } else {
                    android.util.Log.d("SAF", "Parent file not accessible via DocumentFile, building from document ID")
                }
            }
            
            // For Downloads provider, try to convert to external storage provider URI
            // This works better on Android 11+ where Downloads provider is restricted
            if (fileUri.authority == "com.android.providers.downloads.documents") {
                android.util.Log.d("SAF", "Downloads provider detected, attempting to convert to external storage provider")
                val externalStorageUri = convertDownloadsProviderToExternalStorage(fileUri)
                if (externalStorageUri != null) {
                    android.util.Log.d("SAF", "Converted to external storage URI: $externalStorageUri")
                    // Recursively call with the converted URI
                    return getParentDirectoryUriFromFile(externalStorageUri.toString())
                }
            }
            
            // For external storage documents, build parent tree URI from document ID
            if (fileUri.authority == "com.android.externalstorage.documents") {
                android.util.Log.d("SAF", "Building parent tree URI for external storage")
                val parentTreeUri = buildParentTreeUriForExternalStorage(fileUri)
                if (parentTreeUri != null) {
                    android.util.Log.d("SAF", "Built parent tree URI: $parentTreeUri")
                    return parentTreeUri.toString()
                }
            }
            
            // If DocumentFile doesn't work, try to build tree URI from document ID (Downloads provider fallback)
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
                } else {
                    // Handle non-raw document IDs
                    // Try to extract parent document ID
                    try {
                        val parentDocId = DocumentsContract.getTreeDocumentId(fileUri)
                        if (parentDocId != null && parentDocId.isNotEmpty()) {
                            val parentTreeUri = DocumentsContract.buildTreeDocumentUri(
                                fileUri.authority,
                                parentDocId
                            )
                            android.util.Log.d("SAF", "Built parent tree URI from tree doc ID: $parentTreeUri")
                            return parentTreeUri.toString()
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("SAF", "Could not get tree document ID: ${e.message}")
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error getting parent directory: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Build parent tree URI for external storage documents
     * Document ID format: "primary:Download/Update/filename.json"
     * We need to extract "primary:Download/Update" and build tree URI
     */
    private fun buildParentTreeUriForExternalStorage(fileUri: Uri): Uri? {
        try {
            val docId = DocumentsContract.getDocumentId(fileUri)
            android.util.Log.d("SAF", "Document ID: $docId")
            
            // Document ID format: "primary:Download/Update/filename.json"
            // We need to get parent: "primary:Download/Update"
            if (docId.contains("/")) {
                val lastSlashIndex = docId.lastIndexOf("/")
                if (lastSlashIndex > 0) {
                    val parentDocId = docId.substring(0, lastSlashIndex)
                    android.util.Log.d("SAF", "Parent document ID: $parentDocId")
                    
                    // Build tree URI for parent directory
                    val parentTreeUri = DocumentsContract.buildTreeDocumentUri(
                        fileUri.authority,
                        parentDocId
                    )
                    android.util.Log.d("SAF", "Built parent tree URI: $parentTreeUri")
                    return parentTreeUri
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error building parent tree URI: ${e.message}", e)
        }
        return null
    }
    
    /**
     * Convert a document URI to a tree URI for directory access
     */
    private fun convertDocumentUriToTreeUri(documentUri: Uri): Uri? {
        try {
            // If it's already a tree URI, return it
            if (DocumentsContract.isTreeUri(documentUri)) {
                return documentUri
            }
            
            // Get the document ID
            val docId = DocumentsContract.getDocumentId(documentUri)
            if (docId.isNotEmpty()) {
                // Build tree URI from document ID
                val treeUri = DocumentsContract.buildTreeDocumentUri(
                    documentUri.authority,
                    docId
                )
                android.util.Log.d("SAF", "Converted document URI $documentUri to tree URI $treeUri")
                return treeUri
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error converting document URI to tree URI: ${e.message}", e)
        }
        return null
    }
    
    /**
     * Check if a directory URI is accessible through an existing tree permission
     */
    private fun findMatchingTreePermission(directoryUri: Uri): Uri? {
        try {
            val persistedPermissions = contentResolver.persistedUriPermissions
            val directoryDocId = DocumentsContract.getDocumentId(directoryUri)
            
            for (permission in persistedPermissions) {
                val treeUri = permission.uri
                if (DocumentsContract.isTreeUri(treeUri)) {
                    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    
                    // Check if the directory is within the tree permission
                    // For Downloads provider with raw:/ paths
                    if (directoryUri.authority == treeUri.authority) {
                        if (directoryDocId.startsWith("raw:/") && treeDocId.startsWith("raw:/")) {
                            val dirPath = directoryDocId.substring(4)
                            val treePath = treeDocId.substring(4)
                            if (dirPath.startsWith(treePath)) {
                                android.util.Log.d("SAF", "Found matching tree permission: $treeUri")
                                return treeUri
                            }
                        } else if (directoryDocId.startsWith(treeDocId)) {
                            // For other providers, check if directory ID starts with tree ID
                            android.util.Log.d("SAF", "Found matching tree permission: $treeUri")
                            return treeUri
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error finding matching tree permission: ${e.message}", e)
        }
        return null
    }
    
    /**
     * Convert Downloads provider URI to external storage provider URI
     * This helps bypass Android 11+ restrictions on Downloads provider
     */
    private fun convertDownloadsProviderToExternalStorage(downloadsUri: Uri): Uri? {
        try {
            android.util.Log.d("SAF", "Converting Downloads provider URI: $downloadsUri")
            val docId = DocumentsContract.getDocumentId(downloadsUri)
            android.util.Log.d("SAF", "Downloads provider document ID: $docId")
            
            // Downloads provider uses formats like:
            // - "raw:/storage/emulated/0/Download/folder/file.json"
            // - "msf:downloads" (for Downloads root)
            // - "downloads" (for Downloads root)
            
            if (docId.startsWith("raw:/")) {
                // Extract the path: "/storage/emulated/0/Download/folder/file.json"
                val filePath = docId.substring(4) // Remove "raw:" prefix
                android.util.Log.d("SAF", "File path from Downloads provider: $filePath")
                
                // Convert to external storage document ID format: "primary:Download/folder/file.json"
                // External storage uses "primary:" prefix for internal storage
                // Handle both "Download" and "Downloads" folder names
                val relativePath = if (filePath.startsWith("/storage/emulated/0/")) {
                    filePath.replace("/storage/emulated/0/", "")
                } else if (filePath.startsWith("/storage/emulated/0")) {
                    filePath.replace("/storage/emulated/0", "")
                } else {
                    // If path doesn't start with expected prefix, try to extract from Download/Downloads
                    val downloadIndex = filePath.indexOf("/Download")
                    if (downloadIndex >= 0) {
                        filePath.substring(downloadIndex + 1) // Remove leading "/"
                    } else {
                        val downloadsIndex = filePath.indexOf("/Downloads")
                        if (downloadsIndex >= 0) {
                            filePath.substring(downloadsIndex + 1).replace("Downloads", "Download")
                        } else {
                            filePath
                        }
                    }
                }
                
                val externalDocId = "primary:$relativePath"
                android.util.Log.d("SAF", "Converted to external storage document ID: $externalDocId")
                
                // Build external storage document URI
                val externalUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    externalDocId
                )
                android.util.Log.d("SAF", "Successfully converted to external storage URI: $externalUri")
                return externalUri
            } else if (docId == "downloads" || docId == "msf:downloads" || docId.contains("downloads")) {
                // Downloads root - convert to external storage Downloads
                val externalDocId = "primary:Download"
                val externalUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    externalDocId
                )
                android.util.Log.d("SAF", "Converted Downloads root to external storage URI: $externalUri")
                return externalUri
            } else {
                android.util.Log.w("SAF", "Unknown Downloads provider document ID format: $docId")
            }
        } catch (e: Exception) {
            android.util.Log.e("SAF", "Error converting Downloads provider to external storage: ${e.message}", e)
            e.printStackTrace()
        }
        return null
    }
}
