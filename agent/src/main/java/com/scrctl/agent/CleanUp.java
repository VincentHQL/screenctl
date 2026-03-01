package com.scrctl.agent;

import com.scrctl.agent.util.Ln;

import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.io.IOException;

/**
 * Handle the cleanup of scrcpy, even if the main process is killed.
 * <p>
 * This is useful to restore some state when scrcpy is closed, even on device disconnection (which kills the scrcpy process).
 */
public final class CleanUp {
    private Thread thread;

    private CleanUp(Options options) {
        thread = new Thread(() -> runCleanUp(options), "cleanup");
        thread.start();
    }

    public static CleanUp start(Options options) {
        return new CleanUp(options);
    }

    public synchronized void interrupt() {
    }

    public void join() throws InterruptedException {
        thread.join();
    }

    private void runCleanUp(Options options) {

    }

    private void run() throws IOException {

    }



    public static void unlinkSelf() {
        try {
            new File(Server.SERVER_PATH).delete();
        } catch (Exception e) {
            Ln.e("Could not unlink server", e);
        }
    }

    @SuppressWarnings("deprecation")
    private static void prepareMainLooper() {
        Looper.prepareMainLooper();
    }

    public static void main(String... args) {
        try {
            // Start a new session to avoid being terminated along with the server process on some devices
            Os.setsid();
        } catch (ErrnoException e) {
            Ln.e("setsid() failed", e);
        }
        unlinkSelf();

        // Needed for workarounds
        prepareMainLooper();

        Ln.i("Cleaning up");

        System.exit(0);
    }
}
