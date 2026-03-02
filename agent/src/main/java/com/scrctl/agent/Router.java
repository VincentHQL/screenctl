package com.scrctl.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple router for mapping URI patterns to handlers
 */
public class Router implements HttpHandler {
    
    private static class Route {
        Pattern pattern;
        HttpHandler handler;
        
        Route(Pattern pattern, HttpHandler handler) {
            this.pattern = pattern;
            this.handler = handler;
        }
    }
    
    private final Map<HttpRequest.Method, java.util.List<Route>> routes;
    private HttpHandler defaultHandler;
    private Map<String, String> pathParams;
    
    public Router() {
        this.routes = new HashMap<>();
        for (HttpRequest.Method method : HttpRequest.Method.values()) {
            routes.put(method, new java.util.ArrayList<>());
        }
        this.pathParams = new HashMap<>();
        
        // Default 404 handler
        this.defaultHandler = request -> 
            HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.NOT_FOUND,
                "text/plain",
                "404 Not Found: " + request.getUri()
            );
    }
    
    /**
     * Register a handler for a specific method and URI pattern
     */
    public Router on(HttpRequest.Method method, String pattern, HttpHandler handler) {
        Pattern regex = Pattern.compile(pattern);
        routes.get(method).add(new Route(regex, handler));
        return this;
    }
    
    /**
     * Register a GET handler
     */
    public Router get(String pattern, HttpHandler handler) {
        return on(HttpRequest.Method.GET, pattern, handler);
    }
    
    /**
     * Register a POST handler
     */
    public Router post(String pattern, HttpHandler handler) {
        return on(HttpRequest.Method.POST, pattern, handler);
    }
    
    /**
     * Register a PUT handler
     */
    public Router put(String pattern, HttpHandler handler) {
        return on(HttpRequest.Method.PUT, pattern, handler);
    }
    
    /**
     * Register a DELETE handler
     */
    public Router delete(String pattern, HttpHandler handler) {
        return on(HttpRequest.Method.DELETE, pattern, handler);
    }
    
    /**
     * Set default handler for unmatched routes
     */
    public Router setDefaultHandler(HttpHandler handler) {
        this.defaultHandler = handler;
        return this;
    }
    
    /**
     * Get path parameters extracted from the last matched route
     */
    public Map<String, String> getPathParams() {
        return pathParams;
    }
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        pathParams.clear();
        
        java.util.List<Route> methodRoutes = routes.get(request.getMethod());
        if (methodRoutes != null) {
            for (Route route : methodRoutes) {
                Matcher matcher = route.pattern.matcher(request.getUri());
                if (matcher.matches()) {
                    // Extract named groups as path parameters
                    extractPathParams(matcher);
                    return route.handler.handle(request);
                }
            }
        }
        
        return defaultHandler.handle(request);
    }
    
    /**
     * Extract named groups from matcher as path parameters
     */
    private void extractPathParams(Matcher matcher) {
        try {
            // Try to extract named groups if pattern has them
            Pattern pattern = matcher.pattern();
            String patternStr = pattern.pattern();
            
            // Simple extraction for named groups like (?<name>...)
            Pattern namedGroupPattern = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
            Matcher namedGroupMatcher = namedGroupPattern.matcher(patternStr);
            
            while (namedGroupMatcher.find()) {
                String groupName = namedGroupMatcher.group(1);
                try {
                    String value = matcher.group(groupName);
                    if (value != null) {
                        pathParams.put(groupName, value);
                    }
                } catch (IllegalArgumentException e) {
                    // Group name doesn't exist, ignore
                }
            }
        } catch (Exception e) {
            // Ignore errors in path param extraction
        }
    }
}
