package net.aiwings.uvcwebrtc;

import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;
import static io.socket.client.Socket.EVENT_CONNECT;
import static io.socket.client.Socket.EVENT_DISCONNECT;

import android.Manifest;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;

public class WebRTCActivity extends AppCompatActivity {

    private static final String TAG = "WebRTCActivity";
    private static final int RC_CALL = 111;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final int VIDEO_RESOLUTION_WIDTH = 1280;
    public static final int VIDEO_RESOLUTION_HEIGHT = 720;
    public static final int FPS = 30;

    private String mServerURL = "http://192.168.0.16:3000";
    private Socket socket;
    private boolean isInitiator = false;
    private boolean isChannelReady = false;
    private boolean isStarted;
    private boolean isUsingUSBCamera = true;

    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceTextureHelper surfaceTextureHelper;

    private PeerConnection peerConnection;
    private EglBase rootEglBase;
    private SurfaceViewRenderer surfaceView1;
    private SurfaceViewRenderer surfaceView2;
    private PeerConnectionFactory factory;
    private VideoTrack videoTrackFromCamera;

    // AED WebRTC
    PeerConnectionFactory peerConnectionFactory;
    //PeerConnection peerConnection;
    SurfaceViewRenderer localView;
    SurfaceViewRenderer remoteView;
    MediaStream mediaStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webrtc);
    }

    public void onConnectServerClick(View view) {
        getPermission();
        mServerURL = ((EditText)findViewById(R.id.ipAddrEditText)).getText().toString();
        connectSignalServer(mServerURL);
        CreateWebRTC(false);
        WebRTCCall();
        sendMessage("got user media");
    }

    public void onUVCCheckBoxClick(View view) {
        isUsingUSBCamera = ((CheckBox)findViewById(R.id.uvcCheckBox)).isChecked();
    }

    // getPermission
    public void getPermission(){
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA,
                        Manifest.permission.INTERNET    // Socket IO
                }, 1);
    }

    @Override
    protected void onDestroy() {
        if (socket != null) {
            sendMessage("bye");
            socket.disconnect();
        }
        super.onDestroy();
    }

    // Connect to signal server using Socket.io
    // Example: "http://192.168.1.220:3000";
    // String URL = "https://calm-badlands-59575.herokuapp.com/";
    private void connectSignalServer(String URL) {
        try {
            socket = IO.socket(URL);

            socket.on(EVENT_CONNECT, args -> {
                Log.d(TAG, "connectSignalServer: connect");
                socket.emit("create or join", "foo");
            }).on("ipaddr", args -> {
                Log.d(TAG, "connectSignalServer: ipaddr");
            }).on("created", args -> {
                Log.d(TAG, "connectSignalServer: created");
                isInitiator = true;
            }).on("full", args -> {
                Log.d(TAG, "connectSignalServer: full");
            }).on("join", args -> {
                Log.d(TAG, "connectSignalServer: join");
                Log.d(TAG, "connectSignalServer: Another peer made a request to join room");
                Log.d(TAG, "connectSignalServer: This peer is the initiator of room");
                isChannelReady = true;
            }).on("joined", args -> {
                Log.d(TAG, "connectSignalServer: joined");
                isChannelReady = true;
            }).on("log", args -> {
                for (Object arg : args) {
                    Log.d(TAG,  "\"connectSignalServer: " + String.valueOf(arg));
                }
            }).on("message", args -> {
                Log.d(TAG, "connectSignalServer: got a message");

                try {
                    if (args[0] instanceof String) {
                        String message = (String) args[0];
                        if (message.equals("got user media")) {
                            maybeStart();
                        }
                    } else {
                        JSONObject message = (JSONObject) args[0];
                        Log.d(TAG, "connectSignalServer: got message " + message);
                        if (message.getString("type").equals("offer")) {
                            Log.d(TAG, "connectSignalServer: received an offer " + isInitiator + " " + isStarted);
                            if (!isInitiator && !isStarted) {
                                maybeStart();
                            }
                            peerConnection.setRemoteDescription(new SimpleSdpObserver(),
                                    new SessionDescription(OFFER, message.getString("sdp")));
                            doAnswer();
                        } else if (message.getString("type").equals("answer") && isStarted) {
                            peerConnection.setRemoteDescription(new SimpleSdpObserver(),
                                    new SessionDescription(ANSWER, message.getString("sdp")));

                        } else if (message.getString("type").equals("candidate") && isStarted) {
                            Log.d(TAG, "connectSignalServer: receiving candidates");
                            IceCandidate candidate = new IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"));
                            peerConnection.addIceCandidate(candidate);
                        }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }).on(EVENT_DISCONNECT, args -> {
                Log.d(TAG, "connectSignalServer: disconnect");
            }).on("connect_error", args -> {
                if (args[0] instanceof String) {
                    String message = (String) args[0];
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    //MirtDPM4
    private void doAnswer() {
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new MediaConstraints());
    }

    private void maybeStart() {
        Log.d(TAG, "maybeStart: " + isStarted + " " + isChannelReady);
        if (!isStarted && isChannelReady) {
            isStarted = true;
            if (isInitiator) {
                doCall();
            }
        }
    }

    private void doCall() {
        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: ");
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpMediaConstraints);
    }

    private void sendMessage(Object message) {
        socket.emit("message", message);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }

    public void CreateWebRTC(boolean isAudioGranted) {
        // *** WebRTC ***
        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
        // Create PeerConnectionFactory
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(this)
                .createInitializationOptions());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory =
                new DefaultVideoEncoderFactory(eglBaseContext, true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory =
                new DefaultVideoDecoderFactory(eglBaseContext);
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);

        //localView = findViewById(R.id.localView);
        localView = findViewById(R.id.surface_view);
        localView.release(); // Release first so we can reconnect later
        localView.setMirror(true);
        localView.setEnableHardwareScaler(true);
        localView.init(eglBaseContext, null);

        VideoSource videoSource;
        if (isUsingUSBCamera){
            // create USBCapturer (USB Camera)
            UvcCapturer usbCapturer = new UvcCapturer(getApplicationContext(), localView);
            videoSource = peerConnectionFactory.createVideoSource(usbCapturer.isScreencast());
            usbCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            usbCapturer.startCapture(1280, 720, 30);
        } else {
            // create VideoCapturer (Built-in Camera) / createCameraCapturer(true) <true for Front Camera, false for Rear Camera>
            VideoCapturer videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);
       }

        // Create VideoTrack
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);
        // Display in localView
        videoTrack.addSink(localView);

        //remoteView = findViewById(R.id.remoteView);
        remoteView = findViewById(R.id.surface_view2);
        remoteView.release(); // Release first so we can reconnect later
        remoteView.setMirror(true);
        remoteView.setEnableHardwareScaler(true);
        remoteView.init(eglBaseContext, null);

        mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream");
        mediaStream.addTrack(videoTrack);

        if (isAudioGranted){
            // Create AudioTrack
            AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
            AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

            AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);

            mediaStream.addTrack(audioTrack);
        }
    }

    private void WebRTCCall() {
        if (peerConnection != null) {
            peerConnection.close();
        }
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnectionAdapter("localconnection") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                //RabbitMQ.sendIceCandidate(iceCandidate);
                Log.d(TAG, "onIceCandidate: ");
                JSONObject message = new JSONObject();

                try {
                    message.put("type", "candidate");
                    message.put("label", iceCandidate.sdpMLineIndex);
                    message.put("id", iceCandidate.sdpMid);
                    message.put("candidate", iceCandidate.sdp);

                    Log.d(TAG, "onIceCandidate: sending candidate " + message);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(remoteView);
                /*runOnUiThread(() -> {
                    remoteVideoTrack.addSink(remoteView);
                });*/
            }
        });
        peerConnection.addStream(mediaStream);
    }

}