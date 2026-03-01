package com.scrctl.agent.agent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP response
 */
public class HttpResponse {
    
    public enum Status {
        OK(200, "OK"),
        CREATED(201, "Created"),
        ACCEPTED(202, "Accepted"),
        NO_CONTENT(204, "No Content"),
        PARTIAL_CONTENT(206, "Partial Content"),
        REDIRECT(301, "Moved Permanently"),
        NOT_MODIFIED(304, "Not Modified"),
        BAD_REQUEST(400, "Bad Request"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        NOT_FOUND(404, "Not Found"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        REQUEST_TIMEOUT(408, "Request Timeout"),
        RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
        INTERNAL_ERROR(500, "Internal Server Error"),
        NOT_IMPLEMENTED(501, "Not Implemented"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable");
        
        private final int code;
        private final String description;
        
        Status(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private Status status;
    private String mimeType;
    private InputStream data;
    private long contentLength;
    private Map<String, String> headers;
    
    public HttpResponse(Status status) {
        this.status = status;
        this.mimeType = "text/plain";
        this.headers = new HashMap<>();
        this.contentLength = -1;
    }
    
    public HttpResponse(Status status, String mimeType, String data) {
        this(status);
        this.mimeType = mimeType;
        if (data != null) {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            this.data = new ByteArrayInputStream(bytes);
            this.contentLength = bytes.length;
        }
    }
    
    public HttpResponse(Status status, String mimeType, InputStream data, long contentLength) {
        this(status);
        this.mimeType = mimeType;
        this.data = data;
        this.contentLength = contentLength;
    }
    
    public static HttpResponse newFixedLengthResponse(String msg) {
        return new HttpResponse(Status.OK, "text/plain", msg);
    }
    
    public static HttpResponse newFixedLengthResponse(Status status, String mimeType, String msg) {
        return new HttpResponse(status, mimeType, msg);
    }
    
    public static HttpResponse newChunkedResponse(Status status, String mimeType, InputStream data) {
        return new HttpResponse(status, mimeType, data, -1);
    }
    
    public void addHeader(String name, String value) {
        headers.put(name, value);
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    /**
     * Send the response to the output stream
     */
    public void send(OutputStream outputStream) throws IOException {
        // Status line
        String statusLine = "HTTP/1.1 " + status.getCode() + " " + status.getDescription() + "\r\n";
        outputStream.write(statusLine.getBytes(StandardCharsets.UTF_8));
        
        // Default headers
        if (mimeType != null) {
            outputStream.write(("Content-Type: " + mimeType + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        
        if (contentLength >= 0) {
            outputStream.write(("Content-Length: " + contentLength + "\r\n").getBytes(StandardCharsets.UTF_8));
        } else {
            outputStream.write("Transfer-Encoding: chunked\r\n".getBytes(StandardCharsets.UTF_8));
        }
        
        // Custom headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String header = entry.getKey() + ": " + entry.getValue() + "\r\n";
            outputStream.write(header.getBytes(StandardCharsets.UTF_8));
        }
        
        // End of headers
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        
        // Body
        if (data != null) {
            if (contentLength >= 0) {
                // Fixed length
                sendFixedLength(outputStream);
            } else {
                // Chunked
                sendChunked(outputStream);
            }
        }
        
        outputStream.flush();
    }
    
    private void sendFixedLength(OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[4096];
        long remaining = contentLength;
        int bytesRead;
        
        while (remaining > 0 && (bytesRead = data.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
            outputStream.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }
    }
    
    private void sendChunked(OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = data.read(buffer)) > 0) {
            // Chunk size in hex
            String chunkSize = Integer.toHexString(bytesRead) + "\r\n";
            outputStream.write(chunkSize.getBytes(StandardCharsets.UTF_8));
            
            // Chunk data
            outputStream.write(buffer, 0, bytesRead);
            
            // End of chunk
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        
        // Last chunk
        outputStream.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
