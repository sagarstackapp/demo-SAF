# SAF Implementation - Quick Reference

## ✅ Achievable Features

| Feature | Status | Notes |
|---------|--------|-------|
| File picking via SAF | ✅ | Works on all Android versions |
| Downloads subfolder access | ✅ | Via Mobile → Download path |
| Automatic URI conversion | ✅ | Downloads → External Storage |
| Persistent permissions | ✅ | Maintains access across restarts |
| Cross-version compatibility | ✅ | Android 9, 10, 11+ |
| Direct folder navigation | ✅ | Opens in Mobile → Download |

## ❌ Unachievable Features

| Feature | Status | Reason |
|---------|--------|--------|
| Downloads side menu access | ❌ | Android 11+ system restriction |
| Hide Downloads from side menu | ❌ | System UI, cannot modify |
| Access without user interaction | ❌ | Security requirement |
| Android/data access | ❌ | System security restriction |
| Direct file path access | ❌ | Scoped storage restriction |

## Key Workarounds

1. **Downloads Side Menu Issue:**
   - ❌ Problem: Shows empty folders on Android 11+
   - ✅ Solution: Use Mobile → Download path instead
   - ✅ Implementation: File picker navigates there automatically

2. **URI Conversion:**
   - Automatically converts Downloads provider URIs to external storage provider URIs
   - Transparent to users
   - Works seamlessly

## Android Version Matrix

| Android Version | Downloads Root | Downloads Subfolders | Mobile → Download |
|----------------|----------------|---------------------|-------------------|
| Android 9 | ✅ | ✅ | ✅ |
| Android 10 | ✅ | ✅ | ✅ |
| Android 11+ | ❌ (Side Menu) | ✅ (Mobile Path) | ✅ |

## User Instructions

**✅ DO:**
- Navigate through Mobile → Download → [subfolder]
- Grant directory access when prompted
- Select files from Download folder or subfolders

**❌ DON'T:**
- Use Downloads option from side menu (Android 11+)
- Try to access Android/data folder
- Expect files without user selection

## Technical Details

- **Primary Method:** SAF DocumentFile API
- **Fallback 1:** MediaStore API
- **Fallback 2:** File API (Android 9 only)
- **URI Conversion:** Downloads provider → External storage provider
- **Permission Model:** Persistent URI permissions


