package com.scrctl.agent.agent;

/**
 * Interface for handling HTTP requests
 */
public interface HttpHandler {
    
    /**
     * Handle an HTTP request and return a response
     * 
     * @param request The HTTP request
     * @return The HTTP response
     */
    HttpResponse handle(HttpRequest request);
}
