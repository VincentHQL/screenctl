package com.scrctl.agent;


import com.scrctl.agent.agent.AgentServer;
import com.scrctl.agent.agent.RouterSetup;
import com.scrctl.agent.device.ConfigurationException;
import com.scrctl.agent.util.Ln;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class Server {

    public static final String SERVER_PATH;

    static {
        String[] classPaths = System.getProperty("java.class.path").split(File.pathSeparator);
        // By convention, scrcpy is always executed with the absolute path of scrcpy-server.jar as the first item in the classpath
        SERVER_PATH = classPaths[0];
    }

    private static class Completion {
        private int running;
        private boolean fatalError;

        Completion(int running) {
            this.running = running;
        }

        synchronized void addCompleted(boolean fatalError) {
            --running;
            if (fatalError) {
                this.fatalError = true;
            }
            if (running == 0 || this.fatalError) {
                Looper.getMainLooper().quitSafely();
            }
        }
    }

    private Server() {
        // not instantiable
    }

    private static void scrcpy(Options options) throws IOException, ConfigurationException {
        CleanUp cleanUp = null;

        if (options.getCleanup()) {
            cleanUp = CleanUp.start(options);
        }

        int aid = options.getAid();

        Workarounds.apply();

        List<AsyncProcessor> asyncProcessors = new ArrayList<>();

        try {

            AgentServer agentServer = new AgentServer(aid);
            RouterSetup.setupRoutes(agentServer.getRouter(), FakeContext.get());
            asyncProcessors.add(agentServer);

            Completion completion = new Completion(asyncProcessors.size());
            for (AsyncProcessor asyncProcessor : asyncProcessors) {
                asyncProcessor.start((fatalError) -> {
                    completion.addCompleted(fatalError);
                });
            }

            Looper.loop(); // interrupted by the Completion implementation
        } finally {
            if (cleanUp != null) {
                cleanUp.interrupt();
            }
            for (AsyncProcessor asyncProcessor : asyncProcessors) {
                asyncProcessor.stop();
            }

            try {
                if (cleanUp != null) {
                    cleanUp.join();
                }
                for (AsyncProcessor asyncProcessor : asyncProcessors) {
                    asyncProcessor.join();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private static void prepareMainLooper() {
        // Like Looper.prepareMainLooper(), but with quitAllowed set to true
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                @SuppressLint("DiscouragedPrivateApi")
                Field field = Looper.class.getDeclaredField("sMainLooper");
                field.setAccessible(true);
                field.set(null, Looper.myLooper());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String... args) {
        int status = 0;
        try {
            internalMain(args);
        } catch (Throwable t) {
            Ln.e(t.getMessage(), t);
            status = 1;
        } finally {
            // By default, the Java process exits when all non-daemon threads are terminated.
            // The Android SDK might start some non-daemon threads internally, preventing the scrcpy server to exit.
            // So force the process to exit explicitly.
            System.exit(status);
        }
    }

    private static void internalMain(String... args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Ln.e("Exception on thread " + t, e);
        });

        prepareMainLooper();

        Options options = Options.parse(args);

        Ln.disableSystemStreams();
        Ln.initLogLevel(options.getLogLevel());

        Ln.i("Device: [" + Build.MANUFACTURER + "] " + Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

        try {
            scrcpy(options);
        } catch (ConfigurationException e) {
            // Do not print stack trace, a user-friendly error-message has already been logged
        }
    }
}
