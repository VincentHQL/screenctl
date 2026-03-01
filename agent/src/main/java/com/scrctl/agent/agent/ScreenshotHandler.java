package com.scrctl.agent.agent;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IBinder;

import com.scrctl.agent.device.DisplayInfo;
import com.scrctl.agent.device.Size;
import com.scrctl.agent.util.Ln;
import com.scrctl.agent.wrappers.ServiceManager;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

/**
 * Handler for screenshot requests
 * Supports optional width and height parameters for custom resolution
 * Example: GET /screenshot?width=1080&height=1920
 * If no parameters provided, uses screen's default resolution
 */
public class ScreenshotHandler implements HttpHandler {
    
    private static final String TAG = "ScreenshotHandler";
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            // Parse width and height from query parameters
            Integer width = parseIntParam(request, "width");
            Integer height = parseIntParam(request, "height");
            
            // Take screenshot
            Bitmap screenshot = takeScreenshot(width, height);
            
            if (screenshot == null) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Failed to capture screenshot"
                );
            }
            
            // Convert bitmap to PNG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            byte[] imageData = outputStream.toByteArray();
            screenshot.recycle();
            
            // Return image response
            return new HttpResponse(
                HttpResponse.Status.OK,
                "image/png",
                new java.io.ByteArrayInputStream(imageData),
                imageData.length
            );
            
        } catch (Exception e) {
            Ln.e("Error taking screenshot", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "text/plain",
                "Error: " + e.getMessage()
            );
        }
    }
    
    /**
     * Parse integer parameter from request
     */
    private Integer parseIntParam(HttpRequest request, String paramName) {
        String value = request.getParam(paramName);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Ln.w("Invalid " + paramName + " parameter: " + value);
            return null;
        }
    }
    
    /**
     * Take a screenshot of the device screen
     * @param width Optional width for screenshot (null for default)
     * @param height Optional height for screenshot (null for default)
     * @return Bitmap of the screenshot, or null if failed
     */
    private Bitmap takeScreenshot(Integer width, Integer height) {
        try {
            // Get the default display
            IBinder displayToken = getDisplayToken();
            if (displayToken == null) {
                Ln.e("Failed to get display token");
                return null;
            }
            
            // Get display size if width/height not specified
            if (width == null || height == null) {
                DisplayInfo displayInfo =
                    ServiceManager.getDisplayManager().getDisplayInfo(0);
                if (displayInfo != null) {
                    Size size = displayInfo.getSize();
                    if (width == null) width = size.getWidth();
                    if (height == null) height = size.getHeight();
                } else {
                    // Fallback to default values
                    width = width != null ? width : 1080;
                    height = height != null ? height : 1920;
                }
            }
            
            // Create a Rect for the screenshot area
            Rect sourceCrop = new Rect(0, 0, width, height);
            
            // Use reflection to call SurfaceControl.screenshot
            return captureScreen(displayToken, sourceCrop, width, height);
            
        } catch (Exception e) {
            Ln.e("Exception in takeScreenshot", e);
            return null;
        }
    }
    
    /**
     * Get the display token for the default display
     */
    private IBinder getDisplayToken() {
        try {
            // Try to get built-in display token
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            Method method;
            
            try {
                // Android 10+ uses getInternalDisplayToken()
                method = surfaceControlClass.getMethod("getInternalDisplayToken");
                return (IBinder) method.invoke(null);
            } catch (NoSuchMethodException e) {
                // Android 9 and below use getBuiltInDisplay(int)
                method = surfaceControlClass.getMethod("getBuiltInDisplay", int.class);
                return (IBinder) method.invoke(null, 0); // 0 = main display
            }
        } catch (Exception e) {
            Ln.e("Error getting display token", e);
            return null;
        }
    }
    
    /**
     * Capture screen using SurfaceControl.screenshot
     */
    private Bitmap captureScreen(IBinder displayToken, Rect sourceCrop, int width, int height) {
        try {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            
            // Android 13+ (API 33+): captureDisplay(DisplayCaptureArgs)
            try {
                // Build DisplayCaptureArgs
                Class<?> builderClass = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs$Builder");
                Object builder = builderClass.getConstructor(IBinder.class).newInstance(displayToken);
                
                // Set size if specified
                Method setSizeMethod = builderClass.getMethod("setSize", int.class, int.class);
                builder = setSizeMethod.invoke(builder, width, height);
                
                // Build the args
                Method buildMethod = builderClass.getMethod("build");
                Object captureArgs = buildMethod.invoke(builder);
                
                // Call captureDisplay
                Method captureDisplayMethod = surfaceControlClass.getMethod(
                    "captureDisplay",
                    Class.forName("android.view.SurfaceControl$DisplayCaptureArgs")
                );
                Object screenCapture = captureDisplayMethod.invoke(null, captureArgs);
                
                if (screenCapture != null) {
                    // ScreenshotHardwareBuffer.asBitmap()
                    try {
                        Method asBitmapMethod = screenCapture.getClass().getMethod("asBitmap");
                        return (Bitmap) asBitmapMethod.invoke(screenCapture);
                    } catch (NoSuchMethodException e2) {
                        Method getHardwareBufferMethod = screenCapture.getClass().getMethod("getHardwareBuffer");
                        Object hardwareBuffer = getHardwareBufferMethod.invoke(screenCapture);
                        if (hardwareBuffer != null) {
                            Method createBitmapMethod = Bitmap.class.getMethod(
                                "wrapHardwareBuffer",
                                Class.forName("android.hardware.HardwareBuffer"),
                                android.graphics.ColorSpace.class
                            );
                            return (Bitmap) createBitmapMethod.invoke(null, hardwareBuffer, null);
                        }
                    }
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // Method not available, try next
            }
            
            // Android 14+ (API 34+): screenshot(IBinder)
            try {
                Method screenshotMethod = surfaceControlClass.getMethod("screenshot", IBinder.class);
                Object screenCapture = screenshotMethod.invoke(null, displayToken);
                if (screenCapture != null) {
                    // ScreenshotHardwareBuffer.asBitmap()
                    Method asBitmapMethod = screenCapture.getClass().getMethod("asBitmap");
                    Bitmap fullBitmap = (Bitmap) asBitmapMethod.invoke(screenCapture);
                    
                    // Scale if needed
                    if (fullBitmap != null && (fullBitmap.getWidth() != width || fullBitmap.getHeight() != height)) {
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(fullBitmap, width, height, true);
                        fullBitmap.recycle();
                        return scaledBitmap;
                    }
                    return fullBitmap;
                }
            } catch (NoSuchMethodException e) {
                // Method not available, try next
            }
            
            // Android 9-12: captureLayers(IBinder, Rect, float)
            try {
                Method captureLayersMethod = surfaceControlClass.getMethod(
                    "captureLayers",
                    IBinder.class,
                    Rect.class,
                    float.class
                );
                
                // Get actual display size for scale calculation
                DisplayInfo displayInfo =
                    ServiceManager.getDisplayManager().getDisplayInfo(0);
                int displayWidth = width;
                int displayHeight = height;
                if (displayInfo != null) {
                    Size size = displayInfo.getSize();
                    displayWidth = size.getWidth();
                    displayHeight = size.getHeight();
                }
                
                // Calculate scale factor
                float scale = Math.min(
                    (float) width / displayWidth,
                    (float) height / displayHeight
                );
                
                Rect captureRect = new Rect(0, 0, displayWidth, displayHeight);
                Object screenCapture = captureLayersMethod.invoke(null, displayToken, captureRect, scale);
                if (screenCapture != null) {
                    // ScreenshotGraphicBuffer.getBitmap() or asBitmap()
                    try {
                        Method getBitmapMethod = screenCapture.getClass().getMethod("getBitmap");
                        return (Bitmap) getBitmapMethod.invoke(screenCapture);
                    } catch (NoSuchMethodException e2) {
                        Method asBitmapMethod = screenCapture.getClass().getMethod("asBitmap");
                        return (Bitmap) asBitmapMethod.invoke(screenCapture);
                    }
                }
            } catch (NoSuchMethodException e) {
                // Method not available, try next
            }
            
            // Android 7-8: screenshot(Rect, int, int, int)
            try {
                Method screenshotMethod = surfaceControlClass.getMethod(
                    "screenshot",
                    Rect.class,
                    int.class,
                    int.class,
                    int.class
                );
                return (Bitmap) screenshotMethod.invoke(null, sourceCrop, width, height, 0);
            } catch (NoSuchMethodException e) {
                // Method not available
            }
            
            // Android 5-6: screenshot(int, int)
            try {
                Method screenshotMethod = surfaceControlClass.getMethod(
                    "screenshot",
                    int.class,
                    int.class
                );
                return (Bitmap) screenshotMethod.invoke(null, width, height);
            } catch (NoSuchMethodException e) {
                // Method not available
            }
            
            Ln.e("No compatible screenshot method found for this Android version");
            return null;
            
        } catch (Exception e) {
            Ln.e("Error capturing screen", e);
            return null;
        }
    }
}
