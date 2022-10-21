# Android WebRTC + USB Video Camera

We have created an Android APP, **UVCWebRTC**, which can stream USB Camera video using WebRTC. The test video below demostrates streaming USB camera through an Android phone to Android Emulator via a node.js singal server.

[![Android UVC WebRTC Test](http://img.youtube.com/vi/yqEiKIzNSds/0.jpg)](https://www.youtube.com/watch?v=yqEiKIzNSds)


Our code is based those two awesome repositories:

- [Shivam Maindola's AndroidWebRTC](https://github.com/shivammaindola/AndroidWebRTC)
- [Jiang Dongguo's AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera)

For the basics of WebRTC, please refer to Shivam Maindola's post:
[Step by Step Guide to Build WebRTC Native Android App](https://medium.com/@shivammaindola07/step-by-step-guide-to-build-webrtc-native-android-app-47898caa1594).


## Running the code
1. Compile our Android App [/UVCWebRTC](/UVCWebRTC) and install it on two phones. 
2. Go to [/signal_server](/signal_server) and run `npm install` to install packages.
3. Run the signal server `node signal_server.js`
4. Open the App on each phone and enter server IP address. You can choose to use UVC or default camera. Click the **Connect** button. The two Apps will exchange their SDP via signal server and connect to each other automatically!


## Combining UVC and WebRTC

The key part of this project is in [UvcCapturer.java](UVCWebRTC/app/src/main/java/net/aiwings/uvcwebrtc/UvcCapturer.java) :

```java
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
```
In short, we referred to [sutogan4ik's UsbCapturer](https://github.com/sutogan4ik/android-webrtc-usb-camera/blob/master/UsbCapturer.java) and replaced UVCCamera with Jiang Dongguo's CameraUvcStrategy.
