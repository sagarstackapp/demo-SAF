import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';

class SafService {
  static const MethodChannel _channel = MethodChannel('com.example.demo_saf/saf');

  /// Pick a JSON file using SAF (native implementation that returns original URI)
  Future<String?> pickFileWithSAF() async {
    try {
      if (Platform.isAndroid) {
        print('Calling native SAF picker...');
        // Add timeout to detect if picker doesn't return
        final String? uri = await _channel.invokeMethod('pickFileWithSAF')
            .timeout(
              const Duration(seconds: 60),
              onTimeout: () {
                print('File picker timeout - user may have cancelled or folder was empty');
                return null;
              },
            );
        print('Native SAF picker returned: $uri');
        return uri;
      } else {
        // For other platforms, use file_picker
        final result = await FilePicker.platform.pickFiles();
        return result?.files.first.path;
      }
    } catch (e) {
      print('Error picking file: $e');
      return null;
    }
  }

  /// Pick a JSON file using SAF (fallback to file_picker)
  Future<FilePickerResult?> pickJsonFile() async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles();
      return result;
    } catch (e) {
      print('Error picking file: $e');
      return null;
    }
  }

  /// Get all files from the same directory as the picked file using SAF
  Future<List<String>> getAllFilesFromDirectory(String filePath) async {
    try {
      if (Platform.isAndroid) {
        // Handle raw:/ URIs by converting to regular file path
        String actualPath = filePath;
        if (filePath.startsWith('raw:/')) {
          actualPath = filePath.substring(5); // Remove 'raw:/' prefix
        }
        
        // Use platform channel to get directory URI and list files
        final List<dynamic> files = await _channel.invokeMethod(
          'getAllFilesFromDirectory',
          {'filePath': actualPath},
        );
        return files.cast<String>();
      } else {
        // For other platforms, use standard file system
        final directory = Directory(filePath).parent;
        final files = directory.listSync()
            .where((item) => item is File)
            .map((item) => item.path)
            .toList();
        return files;
      }
    } catch (e) {
      print('Error getting files from directory: $e');
      return [];
    }
  }

  /// Read JSON file content
  Future<Map<String, dynamic>?> readJsonFile(String filePath) async {
    try {
      String content;
      
      if (Platform.isAndroid) {
        // Handle raw:/ URIs by converting to regular file path
        String actualPath = filePath;
        if (filePath.startsWith('raw:/')) {
          actualPath = filePath.substring(5); // Remove 'raw:/' prefix
        }
        
        // Check if it's a content:// URI or regular file path
        if (actualPath.startsWith('content://')) {
          // Use platform channel for SAF URIs
          content = await _channel.invokeMethod(
            'readFileContent',
            {'filePath': actualPath},
          );
        } else {
          // Use standard file reading for regular paths
          final file = File(actualPath);
          content = await file.readAsString();
        }
      } else {
        // For other platforms, use standard file reading
        final file = File(filePath);
        content = await file.readAsString();
      }
      
      return json.decode(content) as Map<String, dynamic>;
    } catch (e) {
      print('Error reading JSON file: $e');
      return null;
    }
  }

  /// Get file URI from file path (for Android SAF)
  Future<String?> getFileUri(String filePath) async {
    try {
      if (Platform.isAndroid) {
        final String? uri = await _channel.invokeMethod(
          'getFileUri',
          {'filePath': filePath},
        );
        return uri;
      }
      return filePath;
    } catch (e) {
      print('Error getting file URI: $e');
      return null;
    }
  }

  /// Check for existing persisted URI permissions
  Future<List<String>> checkExistingPermissions() async {
    try {
      if (Platform.isAndroid) {
        final List<dynamic> uris = await _channel.invokeMethod('checkExistingPermissions');
        return uris.cast<String>();
      } else {
        return [];
      }
    } catch (e) {
      print('Error checking permissions: $e');
      return [];
    }
  }

  /// Pick directory using SAF and get all files
  Future<String?> pickDirectoryWithSAF() async {
    try {
      if (Platform.isAndroid) {
        // First check if we already have permissions
        final existingPermissions = await checkExistingPermissions();
        if (existingPermissions.isNotEmpty) {
          print('Found existing permissions, using: ${existingPermissions.first}');
          // Return the first existing permission URI
          return existingPermissions.first;
        }
        
        print('No existing permissions, calling native SAF directory picker...');
        final String? uri = await _channel.invokeMethod('pickDirectoryWithSAF');
        print('Native SAF directory picker returned: $uri');
        return uri;
      } else {
        return null;
      }
    } catch (e) {
      print('Error picking directory: $e');
      return null;
    }
  }

  /// List all files in a directory tree URI
  Future<List<String>> listFilesInDirectory(String directoryUri) async {
    try {
      if (Platform.isAndroid) {
        final List<dynamic> files = await _channel.invokeMethod(
          'listFilesInDirectory',
          {'directoryUri': directoryUri},
        );
        return files.cast<String>();
      } else {
        return [];
      }
    } catch (e) {
      print('Error listing files in directory: $e');
      return [];
    }
  }

  /// Get parent directory URI from a file URI
  Future<String?> getParentDirectoryUri(String fileUri) async {
    try {
      if (Platform.isAndroid) {
        final String? parentUri = await _channel.invokeMethod(
          'getParentDirectoryUri',
          {'fileUri': fileUri},
        );
        return parentUri;
      }
      return null;
    } catch (e) {
      print('Error getting parent directory URI: $e');
      return null;
    }
  }

  /// Request directory access for a specific directory URI
  Future<String?> requestDirectoryAccess(String directoryUri) async {
    try {
      if (Platform.isAndroid) {
        final String? result = await _channel.invokeMethod(
          'requestDirectoryAccess',
          {'directoryUri': directoryUri},
        );
        return result;
      }
      return null;
    } catch (e) {
      print('Error requesting directory access: $e');
      return null;
    }
  }

  /// Process JSON file and get all files from same directory
  Future<Map<String, dynamic>> processJsonAndGetFiles() async {
    try {
      // Step 1: User picks a JSON file
      print('Starting file pick process...');
      String? fileUri = await pickFileWithSAF();
      
      if (fileUri == null || fileUri.isEmpty) {
        return {
          'success': false,
          'error': 'No file selected. If you tried to access Downloads subfolder from the side menu and saw an empty folder, please try navigating through: Mobile > Download > [your subfolder] instead.',
          'jsonData': null,
          'files': [],
        };
      }

      print('File URI received: $fileUri');

      // Check if this is a tree URI (Android 13+ uses ACTION_OPEN_DOCUMENT_TREE)
      // Tree URIs typically contain ":tree/" in the path
      bool isTreeUri = fileUri.contains(':tree/');
      List<String> allFiles = [];
      
      if (isTreeUri) {
        print('Tree URI detected (Android 13+) - listing files in tree...');
        // On Android 13+, we got a tree URI (folder), not a file URI
        // List all files in this tree and pick the first JSON file
        allFiles = await listFilesInDirectory(fileUri);
        print('Found ${allFiles.length} files in tree');
        
        // Find the first JSON file
        String? jsonFileUri;
        for (String file in allFiles) {
          if (file.toLowerCase().endsWith('.json')) {
            jsonFileUri = file;
            print('Found JSON file in tree: $jsonFileUri');
            break;
          }
        }
        
        if (jsonFileUri == null) {
          return {
            'success': false,
            'error': 'No JSON file found in the selected folder. Please select a folder that contains a JSON file.',
            'jsonData': null,
            'files': allFiles,
          };
        }
        
        // Use the found JSON file
        fileUri = jsonFileUri;
        print('Using JSON file from tree: $fileUri');
        
        // For tree URIs, we already have all files, so skip the directory listing step
        // Just read the JSON and return
        print('Reading JSON file from tree...');
        final jsonData = await readJsonFile(fileUri);
        if (jsonData == null) {
          return {
            'success': false,
            'error': 'Failed to read JSON file. Make sure you selected a valid JSON file.',
            'jsonData': null,
            'files': allFiles,
          };
        }
        
        return {
          'success': true,
          'error': null,
          'jsonData': jsonData,
          'files': allFiles,
          'jsonFilePath': fileUri,
        };
      }

      // Step 2: Read the JSON file (for non-tree URIs)
      print('Reading JSON file...');
      final jsonData = await readJsonFile(fileUri);
      if (jsonData == null) {
        return {
          'success': false,
          'error': 'Failed to read JSON file. Make sure you selected a valid JSON file.',
          'jsonData': null,
          'files': [],
        };
      }

      // Step 3: Try to list files directly from the picked file's directory
      // This works because we already have access to the picked file
      print('Attempting to list files directly from picked file directory...');
      allFiles = await getAllFilesFromDirectory(fileUri);
      
      // Check if we only got the picked file itself (meaning parent access failed)
      bool onlyPickedFile = allFiles.length == 1 && allFiles.first == fileUri;
      
      // If we got multiple files directly, use them (this works when parentFile is accessible)
      if (allFiles.isNotEmpty && !onlyPickedFile) {
        print('Successfully listed ${allFiles.length} files directly from directory');
      } else {
        // If we only got the picked file, parent access failed - need to request tree access
        print('Direct listing only returned picked file, parent access unavailable');
        // Step 4: If direct listing failed, try getting parent directory URI
        print('Direct listing failed, getting parent directory URI...');
        String? parentDirectoryUri = await getParentDirectoryUri(fileUri);
        
        if (parentDirectoryUri == null || parentDirectoryUri.isEmpty) {
          print('Could not determine parent directory, returning only picked file');
          allFiles = [fileUri]; // At least return the picked file
        } else {
          print('Parent directory URI: $parentDirectoryUri');

          // Step 5: Check if we already have permission for this directory
          final existingPermissions = await checkExistingPermissions();
          bool hasPermission = existingPermissions.any((uri) {
            // Check for exact match
            if (uri == parentDirectoryUri) return true;
            
            // Check if parentDirectoryUri is a subdirectory of an existing permission
            // For tree URIs, we need to check if the parent is within the granted tree
            try {
              // Normalize URIs for comparison (remove trailing slashes, etc.)
              final normalizedParent = parentDirectoryUri.replaceAll(RegExp(r'/$'), '');
              final normalizedExisting = uri.replaceAll(RegExp(r'/$'), '');
              
              // Check if parent is within the existing tree permission
              // Tree URIs typically end with :tree/documentId
              if (normalizedParent.startsWith(normalizedExisting) || 
                  normalizedExisting.startsWith(normalizedParent)) {
                return true;
              }
            } catch (e) {
              print('Error comparing URIs: $e');
            }
            
            return false;
          });

          String? directoryTreeUri = parentDirectoryUri;

          if (!hasPermission) {
            // Step 6: Request directory tree access for the parent directory
            print('No existing permission found, requesting directory access...');
            print('Parent directory URI to request: $parentDirectoryUri');
            
            // Add a small delay to ensure the previous picker is fully closed
            await Future.delayed(const Duration(milliseconds: 300));
            
            directoryTreeUri = await requestDirectoryAccess(parentDirectoryUri);
            
            if (directoryTreeUri == null || directoryTreeUri.isEmpty) {
              print('Directory access was not granted, returning only picked file');
              allFiles = [fileUri]; // At least return the picked file
            } else {
              print('Directory access granted: $directoryTreeUri');
              // Step 7: List all files in the directory using tree URI
              print('Listing files in directory tree...');
              allFiles = await listFilesInDirectory(directoryTreeUri);
              print('Found ${allFiles.length} files in directory');
            }
          } else {
            print('Using existing permission for directory');
            // Step 7: List all files in the directory using existing permission
            print('Listing files in directory...');
            allFiles = await listFilesInDirectory(directoryTreeUri);
            print('Found ${allFiles.length} files in directory');
          }
        }
      }

      return {
        'success': true,
        'error': null,
        'jsonData': jsonData,
        'files': allFiles,
        'jsonFilePath': fileUri,
      };
    } catch (e) {
      print('Error in processJsonAndGetFiles: $e');
      return {
        'success': false,
        'error': e.toString(),
        'jsonData': null,
        'files': [],
      };
    }
  }
}

