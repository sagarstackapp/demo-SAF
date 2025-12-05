# SAF (Storage Access Framework) Implementation Documentation

## Table of Contents
1. [Overview](#overview)
2. [Achievable Features](#achievable-features)
3. [Unachievable Features & Limitations](#unachievable-features--limitations)
4. [Technical Implementation](#technical-implementation)
5. [Android Version Compatibility](#android-version-compatibility)
6. [Usage Guide](#usage-guide)
7. [Troubleshooting](#troubleshooting)

---

## Overview

This project implements a **Storage Access Framework (SAF)** based file picking solution for Android that allows accessing files without requiring sensitive permissions like `MANAGE_EXTERNAL_STORAGE`. The implementation focuses on accessing files in the Downloads folder and its subfolders across different Android versions, with special handling for Android 11+ restrictions.

### Key Objectives
- ✅ Access files in Downloads folder and subfolders
- ✅ No sensitive storage permissions required
- ✅ Cross-version compatibility (Android 9+)
- ✅ Automatic handling of Android 11+ restrictions

---

## Achievable Features

### ✅ 1. File Picking via SAF
**Status:** Fully Implemented

- Users can pick files using the native Android file picker
- Supports all file types (JSON, images, documents, etc.)
- Works on Android 9, 10, 11, and later versions
- File picker automatically navigates to Mobile → Download folder

**Implementation:**
- Uses `ACTION_OPEN_DOCUMENT` intent
- Sets `EXTRA_INITIAL_URI` to navigate directly to Download folder
- Returns content URI for the selected file

### ✅ 2. Accessing Files in Downloads Subfolders
**Status:** Fully Implemented (with workaround)

- Can access files in subfolders like `Download/folder/file.json`
- Works on Android 10, 11, and later versions
- Automatically requests directory tree access when needed
- Lists all files in the same directory as the picked file

**Implementation:**
- Converts Downloads provider URIs to external storage provider URIs
- Requests tree access for parent directories
- Uses DocumentFile API for recursive file listing

### ✅ 3. Automatic URI Conversion
**Status:** Fully Implemented

- Automatically converts Downloads provider URIs to external storage provider URIs
- Handles both `com.android.providers.downloads.documents` and `com.android.externalstorage.documents`
- Transparent conversion - users don't need to know about it

**Why This Works:**
- External storage provider (`com.android.externalstorage.documents`) doesn't have the same restrictions as Downloads provider
- Path format: `primary:Download/folder/file.json` works reliably
- Same files, different access method

### ✅ 4. Persistent URI Permissions
**Status:** Fully Implemented

- Takes persistable URI permissions for picked files
- Maintains access across app restarts
- Checks for existing permissions before requesting new ones

**Implementation:**
- Uses `takePersistableUriPermission()` for files
- Uses `ACTION_OPEN_DOCUMENT_TREE` for directory access
- Stores permissions in `contentResolver.persistedUriPermissions`

### ✅ 5. Cross-Version Compatibility
**Status:** Fully Implemented

- Works on Android 9 (API 28)
- Works on Android 10 (API 29)
- Works on Android 11+ (API 30+)
- Automatic fallback strategies for different Android versions

**Implementation:**
- Version checks for API-specific features (`EXTRA_INITIAL_URI` requires API 26+)
- Multi-layer fallback: DocumentFile → MediaStore → File API
- Graceful degradation on older versions

### ✅ 6. Direct Navigation to Download Folder
**Status:** Fully Implemented

- File picker opens directly in Mobile → Download folder
- Users don't need to navigate manually
- Avoids the restricted Downloads side menu

**Implementation:**
- Sets `EXTRA_INITIAL_URI` to `content://com.android.externalstorage.documents/document/primary:Download`
- File picker navigates to this location automatically
- Works on Android 8.0+ (API 26+)

---

## Unachievable Features & Limitations

### ❌ 1. Direct Access to Downloads Root via Side Menu
**Status:** Not Achievable on Android 11+

**Why:**
- Android 11+ restricts access to Downloads root folder when accessed via Downloads provider (`com.android.providers.downloads.documents`)
- The system file picker shows an empty folder when accessing Downloads subfolders from the side menu
- This is a **system-level restriction** that cannot be bypassed

**Workaround:**
- ✅ Navigate through **Mobile → Download → [subfolder]** instead
- ✅ File picker automatically opens in Mobile → Download folder (Android 8-12)
- ✅ On Android 13+, user navigates manually (EXTRA_INITIAL_URI skipped to avoid empty screen)
- ✅ Automatic URI conversion handles the difference transparently

### ❌ 1a. EXTRA_INITIAL_URI on Android 13+
**Status:** Causes Empty Screen on Android 13+

**Why:**
- Android 13+ (API 33+) has a bug/restriction where `EXTRA_INITIAL_URI` with document URIs causes the file picker to show an empty screen
- This is a **system-level issue** with Android 13's file picker implementation

**Workaround:**
- ✅ Skip `EXTRA_INITIAL_URI` on Android 13+
- ✅ File picker opens normally (user navigates manually)
- ✅ All other functionality works correctly
- ✅ User instructions guide to Mobile → Download path

**Technical Details:**
```kotlin
// Downloads provider (restricted):
content://com.android.providers.downloads.documents/document/raw:/storage/emulated/0/Download/folder/file.json

// External storage provider (works):
content://com.android.externalstorage.documents/document/primary:Download/folder/file.json
```

### ❌ 2. Hiding Downloads Option from Side Menu
**Status:** Not Achievable

**Why:**
- The file picker UI is controlled by the Android system, not our app
- We cannot modify or hide options in the system file picker's side menu
- This is a **system UI limitation**

**Workaround:**
- ✅ Set `EXTRA_INITIAL_URI` to navigate directly to Mobile → Download
- ✅ Users start in the working folder, avoiding the side menu
- ✅ Clear user instructions can guide users to use Mobile → Download path

### ❌ 3. Accessing Files Without User Interaction
**Status:** Not Achievable (by design)

**Why:**
- SAF requires explicit user permission for security
- Users must select files/folders through the system picker
- This is a **security feature**, not a limitation

**What We Can Do:**
- ✅ Request directory tree access once, then reuse it
- ✅ Check for existing persisted permissions
- ✅ Guide users through the permission flow

### ❌ 4. Accessing Android/data and Android/obb Directories
**Status:** Not Achievable on Android 11+

**Why:**
- Android 11+ completely restricts access to `Android/data/` and `Android/obb/` directories
- Even with SAF, these directories cannot be accessed
- This is a **system security restriction**

**Alternative:**
- Use app-specific directories (`getExternalFilesDir()`)
- Request `MANAGE_EXTERNAL_STORAGE` permission (requires special approval from Google Play)

### ❌ 5. Reading Files Without URI Permission
**Status:** Not Achievable

**Why:**
- SAF URIs require explicit permission grants
- Each file access needs permission (unless using tree access)
- This is a **security requirement**

**What We Do:**
- ✅ Take persistable URI permissions when files are picked
- ✅ Request tree access for directories to access all files within
- ✅ Reuse existing permissions when available

### ❌ 6. Writing Files Without Directory Tree Access
**Status:** Not Achievable

**Why:**
- Writing files requires directory tree access (`ACTION_OPEN_DOCUMENT_TREE`)
- Single file access (`ACTION_OPEN_DOCUMENT`) only grants read permission
- This is a **security restriction**

**What We Do:**
- ✅ Request directory tree access when needed
- ✅ Use `takePersistableUriPermission()` to maintain access
- ✅ Guide users through the directory selection process

### ❌ 7. Accessing Files via Direct File Paths
**Status:** Not Achievable on Android 10+

**Why:**
- Scoped storage restricts direct file path access
- `File` API doesn't work for external storage on Android 10+
- This is a **system security restriction**

**What We Do:**
- ✅ Use SAF URIs exclusively
- ✅ Convert file paths to URIs when possible
- ✅ Use DocumentFile API for file operations

---

## Technical Implementation

### Architecture

```
┌─────────────────┐
│   Flutter UI    │
│   (main.dart)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  SAF Service    │
│ (saf_service)   │
└────────┬────────┘
         │
         │ Platform Channel
         │
         ▼
┌─────────────────┐
│  MainActivity    │
│   (Kotlin)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Android SAF    │
│     APIs        │
└─────────────────┘
```

### Key Components

#### 1. Flutter Layer (`lib/services/saf_service.dart`)
- Handles file picking requests
- Processes JSON files
- Manages file listing operations
- Communicates with native Android code via platform channels

#### 2. Android Native Layer (`MainActivity.kt`)
- Implements SAF file picker (`ACTION_OPEN_DOCUMENT`)
- Implements directory tree picker (`ACTION_OPEN_DOCUMENT_TREE`)
- Handles URI conversion (Downloads → External Storage)
- Manages persistent URI permissions
- Lists files using DocumentFile API

### URI Conversion Flow

```
Downloads Provider URI (Restricted)
    ↓
convertDownloadsProviderToExternalStorage()
    ↓
External Storage Provider URI (Works)
    ↓
Use for all operations
```

**Example Conversion:**
```
Input:  content://com.android.providers.downloads.documents/document/raw:/storage/emulated/0/Download/folder/file.json
Output: content://com.android.externalstorage.documents/document/primary:Download/folder/file.json
```

### File Listing Flow

```
Pick File
    ↓
Get File URI
    ↓
Convert to External Storage URI (if needed)
    ↓
Try Direct Parent Access (DocumentFile API)
    ↓
    ├─ Success → List Files
    └─ Failure → Request Tree Access
                    ↓
                User Grants Access
                    ↓
                List Files Recursively
```

---

## Android Version Compatibility

### Android 9 (API 28)
- ✅ **File Access:** Direct file access via File API works
- ✅ **MediaStore:** Full access to MediaStore API
- ✅ **SAF:** Available but not required
- ✅ **Downloads:** Works with both File API and MediaStore
- ✅ **Subfolders:** Fully accessible

### Android 10 (API 29)
- ✅ **File Access:** Scoped storage introduced, but can opt-out
- ⚠️ **MediaStore:** Limited access due to scoped storage
- ✅ **SAF:** Recommended approach
- ✅ **Downloads:** Works but may require SAF for subfolders
- ✅ **Subfolders:** Accessible via SAF

### Android 11 (API 30)
- ❌ **File Access:** Strict scoped storage enforcement
- ⚠️ **MediaStore:** Limited access, may not return all files
- ✅ **SAF:** Required for accessing files
- ❌ **Downloads Root (Side Menu):** Restricted - shows empty folders
- ✅ **Downloads Subfolders:** Accessible via Mobile → Download path
- ✅ **Subfolders:** Works with URI conversion

### Android 12 (API 31)
- Same as Android 11
- ✅ All features work the same way
- ✅ URI conversion still required

### Android 13 (API 33)
- ⚠️ **EXTRA_INITIAL_URI:** May cause empty screen issue
- ✅ **File Picker:** Works without EXTRA_INITIAL_URI
- ✅ **Downloads Subfolders:** Accessible via Mobile → Download path
- ⚠️ **Navigation:** User needs to navigate manually (EXTRA_INITIAL_URI skipped)
- ✅ **URI Conversion:** Still works for file access
- ✅ **Package Visibility:** Requires queries in AndroidManifest.xml

**Special Handling:**
- EXTRA_INITIAL_URI is skipped on Android 13+ to avoid empty screen
- File picker opens normally, user navigates to Mobile → Download manually
- All other functionality works the same as Android 11/12

---

## Usage Guide

### Basic Usage

1. **Pick a File:**
   ```dart
   final result = await safService.processJsonAndGetFiles();
   if (result['success'] == true) {
     final jsonData = result['jsonData'];
     final files = result['files'];
   }
   ```

2. **The app will:**
   - Open file picker in Mobile → Download folder
   - Allow user to select a JSON file
   - Automatically convert Downloads provider URIs if needed
   - Request directory access if required
   - List all files in the same directory

### User Instructions

**For Best Results:**
1. When file picker opens, navigate to your subfolder within Download folder
2. Select your JSON file
3. Grant directory access when prompted (if needed)
4. All files in that folder will be listed automatically

**Avoid:**
- ❌ Don't use Downloads option from side menu (shows empty folders on Android 11+)
- ✅ Use Mobile → Download → [your folder] path instead

---

## Troubleshooting

### Issue: Files not showing in Downloads subfolder

**Symptoms:**
- File picker shows empty folder
- Cannot select files

**Cause:**
- Accessing Downloads subfolder via side menu on Android 11+

**Solution:**
- Navigate through Mobile → Download → [subfolder] instead
- File picker automatically opens in Mobile → Download folder

### Issue: Directory access not granted

**Symptoms:**
- Error: "Directory access was not granted"
- Only picked file is shown

**Cause:**
- User cancelled directory access request
- Tree access not granted

**Solution:**
- Make sure to grant access when directory picker appears
- Select the correct folder (not Downloads root)
- Try again and grant permission

### Issue: onActivityResult not called

**Symptoms:**
- File picker opens but nothing happens
- No file selected

**Cause:**
- User cancelled file picker
- Folder appears empty (Android 11+ restriction)

**Solution:**
- Use Mobile → Download path instead of Downloads side menu
- Make sure folder contains files
- Try selecting a file from a different location first

### Issue: Conversion fails

**Symptoms:**
- Error logs show conversion failure
- Files not accessible

**Cause:**
- Unsupported URI format
- Path parsing error

**Solution:**
- Check logcat for detailed error messages
- Ensure file is in Download folder or subfolder
- Try using Mobile → Download path directly

---

## Technical Limitations Summary

| Feature | Android 9 | Android 10 | Android 11+ | Reason |
|---------|-----------|------------|-------------|--------|
| Direct file access | ✅ | ⚠️ | ❌ | Scoped storage |
| Downloads side menu | ✅ | ✅ | ❌ | System restriction |
| Mobile → Download | ✅ | ✅ | ✅ | External storage provider |
| Downloads subfolders | ✅ | ✅ | ✅ | With URI conversion |
| Android/data access | ❌ | ❌ | ❌ | System security |
| MediaStore full access | ✅ | ⚠️ | ⚠️ | Scoped storage limits |

---

## Conclusion

This implementation successfully provides file access functionality using SAF while working around Android 11+ restrictions. The key achievement is the automatic URI conversion that allows accessing Downloads subfolders by converting Downloads provider URIs to external storage provider URIs.

**Main Achievements:**
- ✅ No sensitive permissions required
- ✅ Works across Android 9, 10, 11+
- ✅ Automatic handling of restrictions
- ✅ User-friendly navigation

**Known Limitations:**
- ❌ Cannot hide Downloads side menu (system UI)
- ❌ Downloads side menu shows empty folders on Android 11+ (system restriction)
- ❌ Requires user interaction for file selection (security requirement)

**Workarounds:**
- ✅ Direct navigation to Mobile → Download folder
- ✅ Automatic URI conversion
- ✅ Clear user guidance

---

## References

- [Android Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)
- [Android 11 Storage Updates](https://developer.android.com/about/versions/11/privacy/storage)
- [Scoped Storage](https://developer.android.com/training/data-storage)
- [DocumentFile API](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile)

---

**Document Version:** 1.0  
**Last Updated:** 2025  
**Compatible Android Versions:** 9.0 (API 28) and above


