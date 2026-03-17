package com.genymobile.scrcpy.audio;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.device.Streamer;
import com.genymobile.scrcpy.util.IO;
import com.genymobile.scrcpy.util.Ln;

import android.media.MediaCodec;
import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioRawRecorder implements AsyncProcessor {

    private final AudioCapture capture;
    private final Streamer streamer;

    private Thread thread;
    private final AtomicBoolean stopped = new AtomicBoolean();

    public AudioRawRecorder(AudioCapture capture, Streamer streamer) {
        this.capture = capture;
        this.streamer = streamer;
    }

    private void record() throws IOException, AudioCaptureException {
        if (Build.VERSION.SDK_INT < AndroidVersions.API_30_ANDROID_11) {
            Ln.w("Audio disabled: it is not supported before Android 11");
            streamer.writeDisableStream(false);
            return;
        }

        capture.checkCompatibility();

        final ByteBuffer buffer = ByteBuffer.allocateDirect(AudioConfig.MAX_READ_SIZE);
        final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean headerWritten = false;

        while (!stopped.get()) {
            try {
                while (!stopped.get() && !capture.isEnabled()) {
                    capture.waitUntilEnabled();
                }
                if (stopped.get()) {
                    return;
                }

                capture.start();
            } catch (Throwable t) {
                if (t instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (stopped.get() || !capture.isEnabled()) {
                    continue;
                }
                // Notify the client that the audio could not be captured
                streamer.writeDisableStream(false);
                if (t instanceof AudioCaptureException) {
                    throw (AudioCaptureException) t;
                }
                if (t instanceof IOException) {
                    throw (IOException) t;
                }
                throw new IOException("Could not start audio capture", t);
            }

            try {
                if (!headerWritten) {
                    streamer.writeAudioHeader();
                    headerWritten = true;
                }

                while (!Thread.currentThread().isInterrupted() && !stopped.get() && capture.isEnabled()) {
                    buffer.position(0);
                    int r = capture.read(buffer, bufferInfo);
                    if (r < 0) {
                        throw new IOException("Could not read audio: " + r);
                    }
                    buffer.limit(r);

                    streamer.writePacket(buffer, bufferInfo);
                }
            } catch (IOException e) {
                // Broken pipe is expected on close, because the socket is closed by the client
                if (IO.isBrokenPipe(e)) {
                    throw e;
                }
                if (!stopped.get() && capture.isEnabled()) {
                    Ln.e("Audio capture error", e);
                }
            } finally {
                capture.stop();
            }
        }
    }

    @Override
    public void start(TerminationListener listener) {
        thread = new Thread(() -> {
            boolean fatalError = false;
            try {
                record();
            } catch (AudioCaptureException e) {
                // Do not print stack trace, a user-friendly error-message has already been logged
            } catch (Throwable t) {
                Ln.e("Audio recording error", t);
                fatalError = true;
            } finally {
                Ln.d("Audio recorder stopped");
                listener.onTerminated(fatalError);
            }
        }, "audio-raw");
        thread.start();
    }

    @Override
    public void stop() {
        if (thread != null) {
            stopped.set(true);
            thread.interrupt();
            capture.stop();
        }
    }

    @Override
    public void join() throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
    }
}
