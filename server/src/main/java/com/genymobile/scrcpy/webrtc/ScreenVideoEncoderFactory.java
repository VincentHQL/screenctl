package com.genymobile.scrcpy.webrtc;

import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoEncoder;
import org.webrtc.VideoEncoderFactory;

public class ScreenVideoEncoderFactory implements VideoEncoderFactory {
    @Override
    public VideoEncoder createEncoder(VideoCodecInfo videoCodecInfo) {
        return null;
    }

    @Override
    public VideoCodecInfo[] getSupportedCodecs() {
        return new VideoCodecInfo[0];
    }
}
