package net.aiwings.uvcwebrtc;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jiangdg.ausbc.callback.IPreviewDataCallBack;
import com.jiangdg.ausbc.camera.CameraUvcStrategy;
import com.jiangdg.ausbc.camera.bean.CameraRequest;

import org.webrtc.CapturerObserver;
import org.webrtc.NV21Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UvcCapturer implements VideoCapturer {
    private Context context;
    private SurfaceViewRenderer svVideoRender;
    private SurfaceTextureHelper surfaceTextureHelper;
    private CapturerObserver capturerObserver;
    private Executor executor = Executors.newSingleThreadExecutor();
    private int UVC_PREVIEW_WIDTH = 1280;
    private int UVC_PREVIEW_HEIGHT = 720;
    private int UVC_PREVIEW_FPS = 30;


    CameraUvcStrategy mUvcStrategy; // From Jiang Dongguo's AUSBC library

    public UvcCapturer(Context context, SurfaceViewRenderer svVideoRender) {
        this.context = context;
        this.svVideoRender = svVideoRender;
        try {
            mUvcStrategy = new CameraUvcStrategy(context);
        } catch(Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int i, int i1, int i2) {
        if(mUvcStrategy != null) {
            UVC_PREVIEW_WIDTH = i;
            UVC_PREVIEW_HEIGHT = i1;
            UVC_PREVIEW_FPS = i2;
            mUvcStrategy.addPreviewDataCallBack(new IPreviewDataCallBack() {
                @Override
                public void onPreviewData(@Nullable byte[] bytes, @NonNull DataFormat dataFormat) {
                    NV21Buffer nv21Buffer = new NV21Buffer(bytes,UVC_PREVIEW_WIDTH,UVC_PREVIEW_HEIGHT, null);
                    VideoFrame frame = new VideoFrame(nv21Buffer, 0, System.nanoTime());
                    capturerObserver.onFrameCaptured(frame);
                }
            });
            mUvcStrategy.startPreview(getCameraRequest(), svVideoRender.getHolder());
        }
    }

    @Override
    public void stopCapture() throws InterruptedException {
        if (mUvcStrategy != null)
            mUvcStrategy.stopPreview();
    }

    @Override
    public void changeCaptureFormat(int i, int i1, int i2) {

    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    private CameraRequest getCameraRequest() {
        return new CameraRequest.Builder()
                .setFrontCamera(false)
                .setContinuousAFModel(true)
                .setContinuousAutoModel(true)
                .setPreviewWidth(UVC_PREVIEW_WIDTH)
                .setPreviewHeight(UVC_PREVIEW_HEIGHT)
                .create();
    }

}
