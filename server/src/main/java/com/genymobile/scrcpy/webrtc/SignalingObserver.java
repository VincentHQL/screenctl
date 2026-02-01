package com.genymobile.scrcpy.webrtc;

public interface SignalingObserver {
    void onRegistered();
    void onClose();
    void onError();
}
