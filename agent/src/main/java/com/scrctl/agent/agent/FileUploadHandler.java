package com.scrctl.agent.agent;

import android.util.Base64;

import com.scrctl.agent.util.Ln;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for APK file upload and installation requests
 * Supports chunked upload with resumable transfer
 * 
 * Upload modes:
 * 1. Single upload: {"filename": "app.apk", "data": "base64_data"}
 * 2. Chunked upload: {"filename": "app.apk", "data": "base64_chunk", "chunkIndex": 0, "totalChunks": 5}
 * 3. Query progress: GET /upload?filename=app.apk
 * 4. Install uploaded file: POST /upload with {"filename": "app.apk", "action": "install"}
 */
public class FileUploadHandler implements HttpHandler {
    
    private static final String TEMP_DIR = "/data/local/tmp";
    private static final String UPLOAD_DIR = TEMP_DIR + "/uploads";
    
    // Track upload progress: filename -> UploadProgress
    private static final Map<String, UploadProgress> uploadProgressMap = new HashMap<>();
    
    /**
     * Upload progress tracker
     */
    private static class UploadProgress {
        String filename;
        int totalChunks;
        boolean[] receivedChunks;
        long lastUpdateTime;
        
        UploadProgress(String filename, int totalChunks) {
            this.filename = filename;
            this.totalChunks = totalChunks;
            this.receivedChunks = new boolean[totalChunks];
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        void markChunkReceived(int chunkIndex) {
            if (chunkIndex >= 0 && chunkIndex < totalChunks) {
                receivedChunks[chunkIndex] = true;
                lastUpdateTime = System.currentTimeMillis();
            }
        }
        
        boolean isComplete() {
            for (boolean received : receivedChunks) {
                if (!received) return false;
            }
            return true;
        }
        
        int getReceivedChunkCount() {
            int count = 0;
            for (boolean received : receivedChunks) {
                if (received) count++;
            }
            return count;
        }
        
        String toJson() {
            return "{\"filename\":\"" + filename + "\"," +
                   "\"totalChunks\":" + totalChunks + "," +
                   "\"receivedChunks\":" + getReceivedChunkCount() + "," +
                   "\"isComplete\":" + isComplete() + "," +
                   "\"progress\":" + String.format("%.2f", (getReceivedChunkCount() * 100.0 / totalChunks)) + "}";
        }
    }
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        // Ensure upload directory exists
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // Handle GET request - query upload progress
        if (request.getMethod() == HttpRequest.Method.GET) {
            String filename = request.getParam("filename");
            if (filename == null || filename.isEmpty()) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Missing filename parameter\"}"
                );
            }
            
            synchronized (uploadProgressMap) {
                UploadProgress progress = uploadProgressMap.get(filename);
                if (progress == null) {
                    return HttpResponse.newFixedLengthResponse(
                        HttpResponse.Status.NOT_FOUND,
                        "application/json",
                        "{\"error\":\"No upload found for this filename\"}"
                    );
                }
                
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.OK,
                    "application/json",
                    progress.toJson()
                );
            }
        }
        
        // Handle POST request - upload or install
        if (request.getMethod() != HttpRequest.Method.POST) {
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.METHOD_NOT_ALLOWED,
                "application/json",
                "{\"error\":\"Only POST and GET methods are allowed\"}"
            );
        }
        
        String body = request.getBody();
        if (body == null || body.isEmpty()) {
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.BAD_REQUEST,
                "application/json",
                "{\"error\":\"Request body is empty\"}"
            );
        }
        
        try {
            // Parse JSON manually
            String filename = extractJsonString(body, "filename");
            String action = extractJsonString(body, "action");
            
            if (filename == null) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Missing filename in request body\"}"
                );
            }
            
            // Handle install action
            if ("install".equals(action)) {
                return handleInstall(filename);
            }
            
            // Handle upload (chunked or single)
            String base64Data = extractJsonString(body, "data");
            if (base64Data == null) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Missing data in request body\"}"
                );
            }
            
            Integer chunkIndex = extractJsonInt(body, "chunkIndex");
            Integer totalChunks = extractJsonInt(body, "totalChunks");
            
            // Chunked upload
            if (chunkIndex != null && totalChunks != null) {
                return handleChunkedUpload(filename, base64Data, chunkIndex, totalChunks);
            }
            
            // Single upload (original behavior)
            return handleSingleUpload(filename, base64Data);
            
        } catch (Exception e) {
            Ln.e("Error uploading file", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
            );
        }
    }
    
    /**
     * Handle single file upload (non-chunked)
     */
    private HttpResponse handleSingleUpload(String filename, String base64Data) {
        try {
            byte[] apkData = Base64.decode(base64Data, Base64.DEFAULT);
            File tempFile = new File(UPLOAD_DIR, filename);
            
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tempFile);
                fos.write(apkData);
                fos.flush();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            Ln.i("Saved file: " + tempFile.getAbsolutePath() + " (" + apkData.length + " bytes)");
            
            // Auto-install if it's an APK
            if (filename.endsWith(".apk")) {
                String result = installApk(tempFile);
                tempFile.delete();
                
                if (result == null) {
                    return HttpResponse.newFixedLengthResponse(
                        HttpResponse.Status.OK,
                        "application/json",
                        "{\"success\":true,\"message\":\"APK uploaded and installed successfully\"}"
                    );
                } else {
                    return HttpResponse.newFixedLengthResponse(
                        HttpResponse.Status.INTERNAL_ERROR,
                        "application/json",
                        "{\"success\":false,\"error\":\"" + escapeJson(result) + "\"}"
                    );
                }
            }
            
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.OK,
                "application/json",
                "{\"success\":true,\"message\":\"File uploaded successfully\",\"path\":\"" + tempFile.getAbsolutePath() + "\"}"
            );
            
        } catch (Exception e) {
            Ln.e("Error in single upload", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
            );
        }
    }
    
    /**
     * Handle chunked upload with resumable transfer
     */
    private HttpResponse handleChunkedUpload(String filename, String base64Data, int chunkIndex, int totalChunks) {
        try {
            // Validate chunk parameters
            if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Invalid chunkIndex: must be between 0 and \" + (totalChunks - 1)}"
                );
            }
            
            // Get or create progress tracker
            UploadProgress progress;
            synchronized (uploadProgressMap) {
                progress = uploadProgressMap.get(filename);
                if (progress == null) {
                    progress = new UploadProgress(filename, totalChunks);
                    uploadProgressMap.put(filename, progress);
                    Ln.i("Started chunked upload: " + filename + " (total chunks: " + totalChunks + ")");
                } else if (progress.totalChunks != totalChunks) {
                    return HttpResponse.newFixedLengthResponse(
                        HttpResponse.Status.BAD_REQUEST,
                        "application/json",
                        "{\"error\":\"Chunk count mismatch: expected " + progress.totalChunks + ", got " + totalChunks + "\"}"
                    );
                }
            }
            
            // Check if chunk already received
            if (progress.receivedChunks[chunkIndex]) {
                Ln.i("Chunk " + chunkIndex + " already received, skipping");
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.OK,
                    "application/json",
                    "{\"success\":true,\"message\":\"Chunk already received\",\"progress\":" + progress.toJson() + "}"
                );
            }
            
            // Decode and save chunk
            byte[] chunkData = Base64.decode(base64Data, Base64.DEFAULT);
            File targetFile = new File(UPLOAD_DIR, filename);
            
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(targetFile, "rw");
                // Write chunk at specific offset (assuming chunks are equal size except possibly last one)
                // For simplicity, we'll append chunks sequentially in order
                raf.seek(raf.length());
                raf.write(chunkData);
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            // Mark chunk as received
            synchronized (uploadProgressMap) {
                progress.markChunkReceived(chunkIndex);
            }
            
            Ln.i("Received chunk " + chunkIndex + "/" + (totalChunks - 1) + " for " + filename + 
                 " (" + chunkData.length + " bytes, progress: " + progress.getReceivedChunkCount() + "/" + totalChunks + ")");
            
            // Check if upload is complete
            if (progress.isComplete()) {
                Ln.i("Upload complete for " + filename);
                synchronized (uploadProgressMap) {
                    uploadProgressMap.remove(filename);
                }
                
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.OK,
                    "application/json",
                    "{\"success\":true,\"message\":\"Upload complete\",\"complete\":true," +
                    "\"path\":\"" + targetFile.getAbsolutePath() + "\"," +
                    "\"size\":" + targetFile.length() + "}"
                );
            }
            
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.OK,
                "application/json",
                "{\"success\":true,\"message\":\"Chunk received\",\"complete\":false,\"progress\":" + progress.toJson() + "}"
            );
            
        } catch (Exception e) {
            Ln.e("Error in chunked upload", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
            );
        }
    }
    
    /**
     * Handle install action
     */
    private HttpResponse handleInstall(String filename) {
        try {
            File apkFile = new File(UPLOAD_DIR, filename);
            if (!apkFile.exists()) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.NOT_FOUND,
                    "application/json",
                    "{\"error\":\"File not found: " + filename + "\"}"
                );
            }
            
            String result = installApk(apkFile);
            apkFile.delete();
            
            // Clean up progress tracker
            synchronized (uploadProgressMap) {
                uploadProgressMap.remove(filename);
            }
            
            if (result == null) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.OK,
                    "application/json",
                    "{\"success\":true,\"message\":\"APK installed successfully\"}"
                );
            } else {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.INTERNAL_ERROR,
                    "application/json",
                    "{\"success\":false,\"error\":\"" + escapeJson(result) + "\"}"
                );
            }
            
        } catch (Exception e) {
            Ln.e("Error installing APK", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
            );
        }
    }
    
    /**
     * Extract string value from JSON (simple parsing)
     */
    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }
        
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) {
            return null;
        }
        
        int startQuote = json.indexOf("\"", colonIndex);
        if (startQuote == -1) {
            return null;
        }
        
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) {
            return null;
        }
        
        return json.substring(startQuote + 1, endQuote);
    }
    
    /**
     * Extract integer value from JSON (simple parsing)
     */
    private Integer extractJsonInt(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }
        
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) {
            return null;
        }
        
        // Skip whitespace after colon
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        // Find end of number
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        
        if (valueEnd == valueStart) {
            return null;
        }
        
        try {
            return Integer.parseInt(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Install APK using pm command
     */
    private String installApk(File apkFile) {
        try {
            // Use pm install command
            ProcessBuilder pb = new ProcessBuilder(
                "pm", "install", "-r", "-t", apkFile.getAbsolutePath()
            );
            
            Process process = pb.start();
            
            // Read output
            InputStream inputStream = process.getInputStream();
            InputStream errorStream = process.getErrorStream();
            
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.append(new String(buffer, 0, bytesRead));
            }
            
            while ((bytesRead = errorStream.read(buffer)) != -1) {
                error.append(new String(buffer, 0, bytesRead));
            }
            
            int exitCode = process.waitFor();
            
            inputStream.close();
            errorStream.close();
            
            Ln.i("APK installation output: " + output.toString());
            if (error.length() > 0) {
                Ln.e("APK installation error: " + error.toString());
            }
            
            if (exitCode != 0) {
                return "Installation failed with exit code " + exitCode + ": " + error.toString();
            }
            
            String result = output.toString();
            if (result.contains("Success")) {
                return null; // Success
            } else {
                return result;
            }
            
        } catch (Exception e) {
            Ln.e("Error installing APK", e);
            return e.getMessage();
        }
    }
    
    /**
     * Escape string for JSON
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
