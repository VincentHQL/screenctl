package com.genymobile.scrcpy.monitor;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.util.Ln;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MonitorServer implements AsyncProcessor {
    private static final String SOCKET_NAME_PREFIX = "scrcpy_monitor";
    
    private final Router router;
    private ExecutorService executorService;
    private Thread acceptThread;
    private int scid;
    
    public MonitorServer(int scid) {
        this.scid = scid;
        this.router = new Router();
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Get the router for registering handlers
     */
    public Router getRouter() {
        return router;
    }
    
    private String getSocketName() {
        if (scid == -1) {
            // If no SCID is set, use "scrcpy" to simplify using scrcpy-server alone
            return SOCKET_NAME_PREFIX;
        }

        return SOCKET_NAME_PREFIX + String.format("_%08x", scid);
    }

    @Override
    public void start(TerminationListener listener) {
        try {
            LocalServerSocket serverSocket = new LocalServerSocket(getSocketName());
            acceptThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                            LocalSocket clientSocket = serverSocket.accept();
                            executorService.execute(() -> handleClient(clientSocket));
                    }
                } catch (IOException e) {
                    Ln.e("monitor error", e);
                } finally {
                    Ln.d("monitor stopped");
                    executorService.shutdown();
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    listener.onTerminated(true);
                }
            }, "monitor");

            acceptThread.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    @Override
    public void join() throws InterruptedException {
        if (acceptThread != null) {
            acceptThread.join();
        }
        if (executorService!=null) {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                Ln.e("wait client exit failed");
            }
        }
    }

    /**
     * Handle a client connection
     */
    private void handleClient(LocalSocket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();
            
            // Parse request
            HttpRequest request = HttpRequest.parse(inputStream);
            Ln.i("Request: " + request);

            // Handle request
            HttpResponse response;
            try {
                response = router.handle(request);
            } catch (Exception e) {
                response = HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Internal Server Error: " + e.getMessage()
                );
            }
            
            // Send response
            response.send(outputStream);
            
        } catch (IOException e) {
            // ignore
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
