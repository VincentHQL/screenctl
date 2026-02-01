package com.genymobile.scrcpy.webrtc;

import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.VideoCodecStatus;
import org.webrtc.VideoEncoder;
import org.webrtc.VideoFrame;

public class HardwareVideoEncoder implements VideoEncoder {
    HardwareVideoEncoderFactory
    @Override
    public VideoCodecStatus initEncode(Settings settings, Callback callback) {
        return null;
    }

    @Override
    public VideoCodecStatus release() {
        return null;
    }

    @Override
    public VideoCodecStatus encode(VideoFrame videoFrame, EncodeInfo encodeInfo) {
        return null;
    }

    @Override
    public VideoCodecStatus setRateAllocation(BitrateAllocation bitrateAllocation, int i) {
        return null;
    }

    @Override
    public ScalingSettings getScalingSettings() {
        return null;
    }

    @Override
    public String getImplementationName() {
        return "";
    }
}
