import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'services/saf_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SAF File Picker Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const SafDemoPage(),
    );
  }
}

class SafDemoPage extends StatefulWidget {
  const SafDemoPage({super.key});

  @override
  State<SafDemoPage> createState() => _SafDemoPageState();
}

class _SafDemoPageState extends State<SafDemoPage> {
  final SafService _safService = SafService();
  bool _isLoading = false;
  Map<String, dynamic>? _jsonData;
  List<String> _files = [];
  String? _error;
  String? _jsonFilePath;

  Future<void> _pickJsonAndGetFiles() async {
    setState(() {
      _isLoading = true;
      _error = null;
      _jsonData = null;
      _files = [];
      _jsonFilePath = null;
    });

    try {
      final result = await _safService.processJsonAndGetFiles();

      setState(() {
        _isLoading = false;
        if (result['success'] == true) {
          _jsonData = result['jsonData'] as Map<String, dynamic>?;
          _files = (result['files'] as List<dynamic>?)?.cast<String>() ?? [];
          _jsonFilePath = result['jsonFilePath'] as String?;
        } else {
          _error = result['error'] as String? ?? 'Unknown error occurred';
        }
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _error = e.toString();
      });
    }
  }

  String _getFileName(String uri) {
    try {
      // Handle content:// URIs
      if (uri.startsWith('content://')) {
        final uriObj = Uri.parse(uri);
        // Try to get the last path segment
        final segments = uriObj.pathSegments;
        if (segments.isNotEmpty) {
          return segments.last;
        }
        // Fallback: extract from URI string
        final parts = uri.split('/');
        if (parts.isNotEmpty) {
          return parts.last.split('?').first; // Remove query parameters
        }
      }
      // Handle regular file paths
      final parts = uri.split('/');
      return parts.isNotEmpty ? parts.last : uri;
    } catch (e) {
      return uri;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('SAF File Picker Demo'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Info Card
            Card(
              elevation: 4,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.info_outline,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'About SAF',
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Storage Access Framework (SAF) allows you to access files without '
                      'requiring MANAGE_EXTERNAL_STORAGE permission. Pick a JSON file and '
                      'automatically access all files in the same directory.',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Pick File Button
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _pickJsonAndGetFiles,
              icon: _isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.file_present),
              label: Text(_isLoading ? 'Processing...' : 'Pick JSON File'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                textStyle: const TextStyle(fontSize: 16),
              ),
            ),
            const SizedBox(height: 24),

            // Error Display
            if (_error != null)
              Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Row(
                    children: [
                      Icon(
                        Icons.error_outline,
                        color: Theme.of(context).colorScheme.onErrorContainer,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          _error!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.onErrorContainer,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            // JSON File Path
            if (_jsonFilePath != null) ...[
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.insert_drive_file,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'Selected JSON File',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      SelectableText(
                        _jsonFilePath!,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ),
            ],

            // JSON Data Display
            if (_jsonData != null) ...[
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.data_object,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'JSON Content',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Theme.of(context).colorScheme.surfaceVariant,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: SelectableText(
                          _formatJson(_jsonData!),
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                fontFamily: 'monospace',
                              ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],

            // Files List
            if (_files.isNotEmpty) ...[
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.folder,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'Files in Directory (${_files.length})',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      ..._files.map((file) => Padding(
                            padding: const EdgeInsets.symmetric(vertical: 4),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.description,
                                  size: 20,
                                  color: Theme.of(context).colorScheme.secondary,
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: SelectableText(
                                    _getFileName(file),
                                    style: Theme.of(context).textTheme.bodyMedium,
                                  ),
                                ),
                              ],
                            ),
                          )),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _formatJson(Map<String, dynamic> json) {
    try {
      const encoder = JsonEncoder.withIndent('  ');
      return encoder.convert(json);
    } catch (e) {
      return json.toString();
    }
  }
}
