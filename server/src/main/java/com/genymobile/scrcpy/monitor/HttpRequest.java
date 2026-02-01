package com.genymobile.scrcpy.monitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP request
 */
public class HttpRequest {
    
    public enum Method {
        GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, CONNECT, PATCH
    }
    
    private Method method;
    private String uri;
    private String protocol;
    private Map<String, String> headers;
    private Map<String, String> params;
    private String body;
    
    public HttpRequest() {
        this.headers = new HashMap<>();
        this.params = new HashMap<>();
    }
    
    /**
     * Parse HTTP request from input stream
     */
    public static HttpRequest parse(InputStream inputStream) throws IOException {
        HttpRequest request = new HttpRequest();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        
        // Parse request line: METHOD URI PROTOCOL
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request");
        }
        
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new IOException("Invalid request line: " + requestLine);
        }
        
        request.method = Method.valueOf(parts[0].toUpperCase());
        request.uri = parts[1];
        request.protocol = parts[2];
        
        // Parse query parameters from URI
        int queryIndex = request.uri.indexOf('?');
        if (queryIndex != -1) {
            String query = request.uri.substring(queryIndex + 1);
            request.uri = request.uri.substring(0, queryIndex);
            request.parseQueryString(query);
        }
        
        // Parse headers
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                request.headers.put(key.toLowerCase(), value);
            }
        }
        
        // Parse body if Content-Length is present
        String contentLength = request.headers.get("content-length");
        if (contentLength != null) {
            int length = Integer.parseInt(contentLength);
            if (length > 0) {
                char[] bodyChars = new char[length];
                int read = reader.read(bodyChars, 0, length);
                if (read > 0) {
                    request.body = new String(bodyChars, 0, read);
                    
                    // Parse form data if content-type is application/x-www-form-urlencoded
                    String contentType = request.headers.get("content-type");
                    if ("application/x-www-form-urlencoded".equals(contentType)) {
                        request.parseQueryString(request.body);
                    }
                }
            }
        }
        
        return request;
    }
    
    private void parseQueryString(String query) {
        if (query == null || query.isEmpty()) {
            return;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                String key = urlDecode(pair.substring(0, eqIndex));
                String value = urlDecode(pair.substring(eqIndex + 1));
                params.put(key, value);
            }
        }
    }
    
    private String urlDecode(String str) {
        try {
            return java.net.URLDecoder.decode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }
    
    public Method getMethod() {
        return method;
    }
    
    public String getUri() {
        return uri;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }
    
    public Map<String, String> getParams() {
        return params;
    }
    
    public String getParam(String name) {
        return params.get(name);
    }
    
    public String getBody() {
        return body;
    }
    
    @Override
    public String toString() {
        return method + " " + uri + " " + protocol;
    }
}
