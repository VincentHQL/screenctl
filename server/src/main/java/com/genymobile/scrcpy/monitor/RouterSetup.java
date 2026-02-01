package com.genymobile.scrcpy.monitor;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Setup for routing HTTP requests to appropriate handlers
 */
public class RouterSetup {

    /**
     * Setup all routes for the monitor server
     * 
     * @param router The router to configure
     * @param context Android context for accessing system services
     */
    public static void setupRoutes(Router router, Context context) {
        PackageManager packageManager = context.getPackageManager();
        
        // GET /{packageName}/icon - Get application icon
        router.get("^/([a-zA-Z0-9._]+)/icon$", new PackageIconHandler(packageManager));
        
        // GET /screenshot - Take a screenshot (supports ?width=X&height=Y parameters)
        router.get("^/screenshot$", new ScreenshotHandler());
        
        // GET /packages - List all installed packages (supports pagination and filtering)
        router.get("^/packages$", new PackageListHandler(packageManager));
        
        // GET /device-info - Get device information
        router.get("^/device-info$", new DeviceInfoHandler());
        
        // POST /upload - Upload and install APK file
        router.post("^/upload$", new FileUploadHandler());
        
        // GET /health - Health check
        router.get("^/health$", request -> 
            HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.OK,
                "application/json",
                "{\"status\":\"ok\"}"
            )
        );
        
        // Default 404 handler
        router.setDefaultHandler(request -> 
            HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.NOT_FOUND,
                "text/plain",
                "404 Not Found: " + request.getUri()
            )
        );
    }
}
