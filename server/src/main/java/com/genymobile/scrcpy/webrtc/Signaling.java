package com.genymobile.scrcpy.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.Options;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;

import java.net.URI;

public class Signaling implements AsyncProcessor {
    private HandlerThread handlerThread;
    private Handler handler;
    private SignalingObserver observer;

    private PeerConnectionFactory peerConnectionFactory;
    private EglBase rootEglBase;

    private WebSocketClient webSocketClient;

    class SignalClient extends  WebSocketClient {

        public SignalClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake serverHandshake) {

        }

        @Override
        public void onMessage(String s) {

        }

        @Override
        public void onClose(int i, String s, boolean b) {

        }

        @Override
        public void onError(Exception e) {

        }
    }


    public Signaling(Context context, Options options) {
        handlerThread = new HandlerThread("signaling");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
//        webSocketClient = new SignalClient(serverUri);

        rootEglBase = EglBase.create();
    }

    private void register() {

    }

    private ClientHandler createClientHandler() {
        EglBase rootBase = EglBase.create();

        PeerConnectionFactory.builder().setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                rootBase.getEglBaseContext(), false, false
        ));

        return null;
    }

    @Override
    public void start(TerminationListener listener) {

    }

    @Override
    public void stop() {

    }

    @Override
    public void join() throws InterruptedException {

    }

    public void setSignalObserver(SignalingObserver observer) {

    }
}
