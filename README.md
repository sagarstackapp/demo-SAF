# SAF File Picker Demo

A Flutter application demonstrating Storage Access Framework (SAF) implementation for accessing files without requiring `MANAGE_EXTERNAL_STORAGE` permission.

## Overview

This demo application shows how to:
1. Pick a JSON file using SAF (Storage Access Framework)
2. Read the JSON file content
3. Automatically access all other files from the same directory without manual selection

## Why SAF?

- **No Sensitive Permissions**: SAF doesn't require `MANAGE_EXTERNAL_STORAGE` permission, which is often rejected by Google Play Console
- **User-Friendly**: Users grant access through the system file picker
- **Secure**: Access is scoped to user-selected files/directories
- **Compatible**: Works with Android 5.0+ (API 21+)

## Features

- ✅ Pick JSON files using SAF file picker
- ✅ Read JSON file content via SAF URIs
- ✅ Automatically list all files in the same directory
- ✅ No sensitive storage permissions required
- ✅ Clean, modern UI with Material Design 3

## How It Works

1. **File Picking**: Uses `file_picker` package which leverages SAF on Android
2. **Directory Access**: Extracts parent directory URI from the picked file
3. **File Listing**: Uses Android's `DocumentFile` API to list all files in the directory
4. **File Reading**: Reads file content through SAF content URIs

## Setup

1. Install dependencies:
```bash
flutter pub get
```

2. Run the app:
```bash
flutter run
```

## Usage

1. Tap the "Pick JSON File" button
2. Select a JSON file from your device storage
3. The app will:
   - Display the JSON file content
   - Show all files found in the same directory
   - Display file paths/URIs

## Technical Implementation

### Flutter Side (`lib/services/saf_service.dart`)
- Handles file picking using `file_picker`
- Communicates with native Android code via platform channels
- Processes JSON files and directory listings

### Android Side (`android/app/src/main/kotlin/com/example/demo_saf/MainActivity.kt`)
- Implements SAF methods using `DocumentFile` API
- Handles content URI parsing and file access
- Lists files in parent directory using SAF

## Dependencies

- `file_picker: ^8.1.4` - File picking with SAF support
- `path_provider: ^2.1.4` - Path utilities
- `path: ^1.9.0` - Path manipulation

## Android Requirements

- Minimum SDK: 21 (Android 5.0)
- Uses AndroidX DocumentFile library
- No special permissions required in AndroidManifest.xml

## Notes

- This implementation works with SAF URIs (`content://` scheme)
- Files accessed through SAF are temporary unless you persist URI permissions
- For production apps, consider implementing URI permission persistence using `takePersistableUriPermission()`

## License

This is a demo project for educational purposes.
