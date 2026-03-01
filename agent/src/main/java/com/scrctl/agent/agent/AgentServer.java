package com.scrctl.agent.agent;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import com.scrctl.agent.AsyncProcessor;
import com.scrctl.agent.util.Ln;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AgentServer implements AsyncProcessor {
    private static final String SOCKET_NAME_PREFIX = "agent";
    
    private final Router router;
    private ExecutorService executorService;
    private Thread acceptThread;
    private int aid;
    
    public AgentServer(int aid) {
        this.aid = aid;
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
        if (aid == -1) {
            // If no AID is set, use "agent"
            return SOCKET_NAME_PREFIX;
        }

        return SOCKET_NAME_PREFIX + String.format("_%08x", aid);
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
                    Ln.e("agent error", e);
                } finally {
                    Ln.d("agent stopped");
                    executorService.shutdown();
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    listener.onTerminated(true);
                }
            }, "agent");

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
