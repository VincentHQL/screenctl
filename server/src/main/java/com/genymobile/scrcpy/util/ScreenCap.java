package com.genymobile.scrcpy.util;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.wrappers.DisplayControl;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SurfaceControl;

import java.io.FileDescriptor;
import java.io.FileOutputStream;

public class ScreenCap {

    public static void takeScreenshot(int displayId, Size screenCapSize) {
        // 直接使用原始的 FileDescriptor.out 避免受 Ln.disableSystemStreams() 影响
        try (FileOutputStream out = new FileOutputStream(FileDescriptor.out)) {
            IBinder displayToken = getDisplayToken(displayId);
            if (displayToken == null) {
                Ln.e("Could not get display token for id: " + displayId);
                return;
            }

            int width = 0;
            int height = 0;
            if (screenCapSize != null) {
                width = screenCapSize.getWidth();
                height = screenCapSize.getHeight();
            }

            if (width <= 0 || height <= 0) {
                DisplayInfo displayInfo = ServiceManager.getDisplayManager().getDisplayInfo(displayId);
                if (displayInfo != null) {
                    Size size = displayInfo.getSize();
                    width = size.getWidth();
                    height = size.getHeight();
                } else {
                    Ln.e("Could not get display info for id: " + displayId);
                    return;
                }
            }

            // 调用 SurfaceControl 中适配各版本的 screenshot (3参数版本)
            Bitmap bitmap = SurfaceControl.screenshot(displayToken, width, height);
            if (bitmap != null) {
                // 直接向原始 fd 写入 PNG 数据
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                bitmap.recycle();
            } else {
                Ln.e("Screenshot failed: SurfaceControl.screenshot returned null");
            }
        } catch (Exception e) {
            Ln.e("Error taking screenshot: " + e.getMessage(), e);
        }
    }

    private static IBinder getDisplayToken(int displayId) {
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
            long[] ids = DisplayControl.getPhysicalDisplayIds();
            if (ids != null && ids.length > 0) {
                int index = (displayId >= 0 && displayId < ids.length) ? displayId : 0;
                return DisplayControl.getPhysicalDisplayToken(ids[index]);
            }
            return null;
        }

        if (displayId == 0) {
            IBinder token = SurfaceControl.getBuiltInDisplay();
            if (token != null) {
                return token;
            }
        }

        long[] physicalIds = SurfaceControl.getPhysicalDisplayIds();
        if (physicalIds != null && physicalIds.length > 0) {
            int index = (displayId >= 0 && displayId < physicalIds.length) ? displayId : 0;
            return SurfaceControl.getPhysicalDisplayToken(physicalIds[index]);
        }

        return null;
    }
}
