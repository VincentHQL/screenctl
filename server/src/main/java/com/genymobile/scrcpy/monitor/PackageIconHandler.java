package com.genymobile.scrcpy.monitor;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Handler for retrieving application icons
 * Route: GET /{packageName}/icon
 */
public class PackageIconHandler implements HttpHandler {
    
    private static final String TAG = "PackageIconHandler";
    private static final int DEFAULT_ICON_SIZE = 192; // dp
    
    private final PackageManager packageManager;
    
    public PackageIconHandler(PackageManager packageManager) {
        this.packageManager = packageManager;
    }
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        // Extract package name from URI path
        String uri = request.getUri();
        String packageName = extractPackageName(uri);
        
        if (packageName == null || packageName.isEmpty()) {
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.BAD_REQUEST,
                "text/plain",
                "Invalid package name"
            );
        }
        
        try {
            // Get application info
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            
            // Load icon drawable
            Drawable iconDrawable = packageManager.getApplicationIcon(appInfo);
            
            // Convert drawable to bitmap
            Bitmap bitmap = drawableToBitmap(iconDrawable);
            
            // Compress to PNG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            byte[] iconBytes = outputStream.toByteArray();
            
            Log.d(TAG, "Returning icon for package: " + packageName + ", size: " + iconBytes.length + " bytes");
            
            // Return as PNG image
            HttpResponse response = HttpResponse.newChunkedResponse(
                HttpResponse.Status.OK,
                "image/png",
                new ByteArrayInputStream(iconBytes)
            );
            
            // Add cache headers
            response.addHeader("Cache-Control", "public, max-age=86400"); // 24 hours
            
            return response;
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + packageName);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.NOT_FOUND,
                "text/plain",
                "Package not found: " + packageName
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting icon for package: " + packageName, e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "text/plain",
                "Error retrieving icon: " + e.getMessage()
            );
        }
    }
    
    /**
     * Extract package name from URI like "/{packageName}/icon"
     */
    private String extractPackageName(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        
        // Remove leading slash
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        
        // Split by slash and get first part
        String[] parts = uri.split("/");
        if (parts.length >= 1) {
            return parts[0];
        }
        
        return null;
    }
    
    /**
     * Convert Drawable to Bitmap
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }
        
        // Create bitmap from drawable
        Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
            );
        }
        
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        
        return bitmap;
    }
}
