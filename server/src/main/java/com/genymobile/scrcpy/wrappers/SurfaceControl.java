package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.Ln;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public final class SurfaceControl {

    private static final Class<?> CLASS;

    // see <https://android.googlesource.com/platform/frameworks/base.git/+/pie-release-2/core/java/android/view/SurfaceControl.java#305>
    public static final int POWER_MODE_OFF = 0;
    public static final int POWER_MODE_NORMAL = 2;

    static {
        try {
            CLASS = Class.forName("android.view.SurfaceControl");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Method getBuiltInDisplayMethod;
    private static Method setDisplayPowerModeMethod;
    private static Method getPhysicalDisplayTokenMethod;
    private static Method getPhysicalDisplayIdsMethod;

    private SurfaceControl() {
        // only static methods
    }

    public static void openTransaction() {
        try {
            CLASS.getMethod("openTransaction").invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void closeTransaction() {
        try {
            CLASS.getMethod("closeTransaction").invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setDisplayProjection(IBinder displayToken, int orientation, Rect layerStackRect, Rect displayRect) {
        try {
            CLASS.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                    .invoke(null, displayToken, orientation, layerStackRect, displayRect);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setDisplayLayerStack(IBinder displayToken, int layerStack) {
        try {
            CLASS.getMethod("setDisplayLayerStack", IBinder.class, int.class).invoke(null, displayToken, layerStack);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setDisplaySurface(IBinder displayToken, Surface surface) {
        try {
            CLASS.getMethod("setDisplaySurface", IBinder.class, Surface.class).invoke(null, displayToken, surface);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static IBinder createDisplay(String name, boolean secure) throws Exception {
        return (IBinder) CLASS.getMethod("createDisplay", String.class, boolean.class).invoke(null, name, secure);
    }

    private static Method getGetBuiltInDisplayMethod() throws NoSuchMethodException {
        if (getBuiltInDisplayMethod == null) {
            // the method signature has changed in Android 10
            // <https://github.com/Genymobile/scrcpy/issues/586>
            if (Build.VERSION.SDK_INT < AndroidVersions.API_29_ANDROID_10) {
                getBuiltInDisplayMethod = CLASS.getMethod("getBuiltInDisplay", int.class);
            } else {
                getBuiltInDisplayMethod = CLASS.getMethod("getInternalDisplayToken");
            }
        }
        return getBuiltInDisplayMethod;
    }

    public static boolean hasGetBuildInDisplayMethod() {
        try {
            getGetBuiltInDisplayMethod();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static IBinder getBuiltInDisplay() {
        try {
            Method method = getGetBuiltInDisplayMethod();
            if (Build.VERSION.SDK_INT < AndroidVersions.API_29_ANDROID_10) {
                // call getBuiltInDisplay(0)
                return (IBinder) method.invoke(null, 0);
            }

            // call getInternalDisplayToken()
            return (IBinder) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return null;
        }
    }

    private static Method getGetPhysicalDisplayTokenMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayTokenMethod == null) {
            getPhysicalDisplayTokenMethod = CLASS.getMethod("getPhysicalDisplayToken", long.class);
        }
        return getPhysicalDisplayTokenMethod;
    }

    public static IBinder getPhysicalDisplayToken(long physicalDisplayId) {
        try {
            Method method = getGetPhysicalDisplayTokenMethod();
            return (IBinder) method.invoke(null, physicalDisplayId);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return null;
        }
    }

    private static Method getGetPhysicalDisplayIdsMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayIdsMethod == null) {
            getPhysicalDisplayIdsMethod = CLASS.getMethod("getPhysicalDisplayIds");
        }
        return getPhysicalDisplayIdsMethod;
    }

    public static boolean hasGetPhysicalDisplayIdsMethod() {
        try {
            getGetPhysicalDisplayIdsMethod();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static long[] getPhysicalDisplayIds() {
        try {
            Method method = getGetPhysicalDisplayIdsMethod();
            return (long[]) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return null;
        }
    }

    private static Method getSetDisplayPowerModeMethod() throws NoSuchMethodException {
        if (setDisplayPowerModeMethod == null) {
            setDisplayPowerModeMethod = CLASS.getMethod("setDisplayPowerMode", IBinder.class, int.class);
        }
        return setDisplayPowerModeMethod;
    }

    public static boolean setDisplayPowerMode(IBinder displayToken, int mode) {
        try {
            Method method = getSetDisplayPowerModeMethod();
            method.invoke(null, displayToken, mode);
            return true;
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return false;
        }
    }

    public static void destroyDisplay(IBinder displayToken) {
        try {
            CLASS.getMethod("destroyDisplay", IBinder.class).invoke(null, displayToken);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Bitmap screenshot(IBinder displayToken, int width, int height) throws Exception {
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
            return screenshot14(displayToken, width, height);
        } else if (Build.VERSION.SDK_INT >= AndroidVersions.API_33_ANDROID_13) {
            return screenshot13(displayToken, width, height);
        } else if (Build.VERSION.SDK_INT >= AndroidVersions.API_28_ANDROID_9) {
            return screenshot9(displayToken, width, height);
        } else if (Build.VERSION.SDK_INT >= AndroidVersions.API_24_ANDROID_7_0) {
            return screenshot7(width, height);
        } else {
            return screenshot5(width, height);
        }
    }

    // Android 13+ (API 33+)
    private static Bitmap screenshot13(IBinder displayToken, int width, int height) throws Exception {
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
        Method captureDisplayMethod = CLASS.getMethod(
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
                    Method createBitmapMethod = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        createBitmapMethod = Bitmap.class.getMethod(
                                "wrapHardwareBuffer",
                                Class.forName("android.hardware.HardwareBuffer"),
                                ColorSpace.class
                        );
                    }
                    return (Bitmap) createBitmapMethod.invoke(null, hardwareBuffer, null);
                }
            }
        }
        return null;
    }

    // Android 14+ (API 34+)
    private static Bitmap screenshot14(IBinder displayToken, int width, int height) throws Exception {
        Method screenshotMethod = CLASS.getMethod("screenshot", IBinder.class);
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

        return null;
    }

    // android 9 - 12
    private static Bitmap screenshot9(IBinder displayToken, int width, int height) throws Exception {
        Method captureLayersMethod = CLASS.getMethod(
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

        return null;
    }


    // android 7 - 8
    private static Bitmap screenshot7(int width, int height) throws Exception {
        Method screenshotMethod = CLASS.getMethod(
                "screenshot",
                Rect.class,
                int.class,
                int.class,
                int.class
        );
        Rect sourceCrop = new Rect(0, 0, width, height);
        return (Bitmap) screenshotMethod.invoke(null, sourceCrop, width, height, 0);
    }

    // android 5 - 6
    private static Bitmap screenshot5(int width, int height) throws Exception {
        Method screenshotMethod = CLASS.getMethod(
                "screenshot",
                int.class,
                int.class
        );
        return (Bitmap) screenshotMethod.invoke(null, width, height);
    }

}
