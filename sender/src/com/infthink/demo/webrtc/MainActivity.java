/**
 * Copyright (C) 2013-2015, Infthink (Beijing) Technology Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.infthink.demo.webrtc;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoRendererGui.ScalingType;

import tv.matchstick.flint.ApplicationMetadata;
import tv.matchstick.flint.ConnectionResult;
import tv.matchstick.flint.Flint;
import tv.matchstick.flint.Flint.ApplicationConnectionResult;
import tv.matchstick.flint.FlintDevice;
import tv.matchstick.flint.FlintManager;
import tv.matchstick.flint.FlintMediaControlIntent;
import tv.matchstick.flint.ResultCallback;
import tv.matchstick.flint.Status;
import android.app.Fragment;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.infthink.demo.webrtc.WebRtcHelper.SignalingParameters;

public class MainActivity extends FragmentActivity implements
        WebRtcHelper.SignalingEvents, PeerConnectionClient.PeerConnectionEvents {

    private static final String TAG = "flint_webrtc";

    //private static final String APP_URL = "http://castapp.infthink.com/mirror/webrtc/index.html";
    private static final String APP_URL = "http://openflint.github.io/android-webrtc-demo/receiver/index.html";

    private PeerConnectionClient mPeerConn;

    private boolean mIceConnected;

    private boolean mStreamAdded = false;

    private View mRootView;
    private TextView mEncoderStatView;
    private View mMenuBar;
    private TextView mRoomName;
    private GLSurfaceView mVideoView;
    private VideoRenderer.Callbacks mLocalRender;
    private VideoRenderer.Callbacks mRemoteRender;
    private ImageButton mVideoScalingButton;

    private TextView mHudView;
    private final LayoutParams mHudLayout = new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

    private ScalingType mScalingType;

    private Toast mLogToast;

    private AppRTCAudioManager mAudioManager = null;

    private WebRtcHelper mWebrtcHelper;

    private SignalingParameters mSignalingParameters;

    private int mStartBitrate = 0;

    private FlintDevice mSelectedDevice;
    private FlintManager mApiClient;
    private Flint.Listener mFlingListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private WebrtcChannel mWebrtcChannel;
    private MediaRouteButton mMediaRouteButton;
    private int mRouteCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_fullscreen);

        // init flint related
        String APPLICATION_ID = "~flint_android_webrtc_demo";
        Flint.FlintApi.setApplicationId(APPLICATION_ID);

        mWebrtcChannel = new MyWebrtcChannel();

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(
                        FlintMediaControlIntent
                                .categoryForFlint(APPLICATION_ID)).build();

        mMediaRouterCallback = new MediaRouterCallback();
        mFlingListener = new FlingListener();
        mConnectionCallbacks = new ConnectionCallbacks();

        mIceConnected = false;

        // init views
        mRootView = findViewById(android.R.id.content);
        mEncoderStatView = (TextView) findViewById(R.id.encoder_stat);
        mMenuBar = findViewById(R.id.menubar_fragment);
        mRoomName = (TextView) findViewById(R.id.room_name);
        mVideoView = (GLSurfaceView) findViewById(R.id.glview);

        mMediaRouteButton = (MediaRouteButton) mMenuBar
                .findViewById(R.id.media_route_button);
        mMediaRouteButton.setRouteSelector(mMediaRouteSelector);

        VideoRendererGui.setView(mVideoView);

        mScalingType = ScalingType.SCALE_ASPECT_FILL;
        mRemoteRender = VideoRendererGui.create(0, 0, 100, 100, mScalingType,
                false);
        mLocalRender = VideoRendererGui.create(0, 0, 100, 100, mScalingType,
                true);

        mVideoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int visibility = mMenuBar.getVisibility() == View.VISIBLE ? View.INVISIBLE
                        : View.VISIBLE;
                mEncoderStatView.setVisibility(visibility);
                mMenuBar.setVisibility(visibility);
                mRoomName.setVisibility(visibility);
                if (visibility == View.VISIBLE) {
                    mEncoderStatView.bringToFront();
                    mMenuBar.bringToFront();
                    mRoomName.bringToFront();
                    mRootView.invalidate();
                }
            }
        });

        ((ImageButton) findViewById(R.id.button_disconnect))
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        logAndToast("Disconnecting call.");
                        disconnect();
                    }
                });

        ((ImageButton) findViewById(R.id.button_switch_camera))
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mPeerConn != null) {
                            mPeerConn.switchCamera();
                        }
                    }
                });

        ((ImageButton) findViewById(R.id.button_toggle_debug))
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int visibility = mHudView.getVisibility() == View.VISIBLE ? View.INVISIBLE
                                : View.VISIBLE;
                        mHudView.setVisibility(visibility);

                        // use this to send view switch
                        if (mApiClient != null && mApiClient.isConnected()) {
                            mWebrtcChannel.sendSwitchView(mApiClient);
                        }
                    }
                });

        mVideoScalingButton = (ImageButton) findViewById(R.id.button_scaling_mode);
        mVideoScalingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mScalingType == ScalingType.SCALE_ASPECT_FILL) {
                    mVideoScalingButton
                            .setBackgroundResource(R.drawable.ic_action_full_screen);
                    mScalingType = ScalingType.SCALE_ASPECT_FIT;
                } else {
                    mVideoScalingButton
                            .setBackgroundResource(R.drawable.ic_action_return_from_full_screen);
                    mScalingType = ScalingType.SCALE_ASPECT_FILL;
                }
                updateVideoView();
            }
        });

        mHudView = new TextView(this);
        mHudView.setTextColor(Color.BLACK);
        mHudView.setBackgroundColor(Color.WHITE);
        mHudView.setAlpha(0.4f);
        mHudView.setTextSize(TypedValue.COMPLEX_UNIT_PT, 5);
        mHudView.setVisibility(View.INVISIBLE);
        addContentView(mHudView, mHudLayout);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        mAudioManager = AppRTCAudioManager.create(this);

        // ready to init webrtc params
        mWebrtcHelper = new WebRtcHelper(this);
        mWebrtcHelper.initParams();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        setSelectedDevice(null);
        mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    /**
     * Called when the options menu is first created.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_stop:
            stopApplication();
            break;
        }

        return true;
    }

    public static class MenuBarFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater
                    .inflate(R.layout.fragment_menubar, container, false);
        }
    }

    @Override
    public void onParamInitDone(SignalingParameters params) {

        // TODO Auto-generated method stub
        logAndToast("onInitDone...");

        if (mAudioManager != null) {
            // Store existing audio settings and change audio mode to
            // MODE_IN_COMMUNICATION for best possible VoIP performance.
            logAndToast("Initializing the audio manager...");
            mAudioManager.init();
        }
        mSignalingParameters = params;
        abortUnless(PeerConnectionFactory.initializeAndroidGlobals(this, true,
                true, true, VideoRendererGui.getEGLContext()),
                "Failed to initializeAndroidGlobals");
        logAndToast("Creating peer connection...");
        if (mPeerConn != null) {
            mPeerConn.close();
            mPeerConn = null;
        }

        mPeerConn = new PeerConnectionClient(this, mLocalRender, mRemoteRender,
                mSignalingParameters, this, mStartBitrate);
        /*
         * if (mPeerConn.isHDVideo()) {
         * setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); }
         * else {
         * setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
         * }
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (mApiClient != null && mApiClient.isConnected()) {
            mPeerConn.createOffer();
        }
        // if (mApiClient != null && mApiClient.isConnected()) {
        // mWebrtcChannel.sendHello(mApiClient);
        // }
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        // TODO Auto-generated method stub
        logAndToast("onLocalDescription...");

        if (mWebrtcHelper != null) {
            logAndToast("Sending " + sdp.type + " ...");
            if (mSignalingParameters.initiator) {
                mWebrtcChannel.sendOfferSdp(mApiClient, sdp);
            } else {
                mWebrtcChannel.sendAnswerSdp(mApiClient, sdp);
            }
        }
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        // TODO Auto-generated method stub
        logAndToast("onIceCandidate...");

        if (mWebrtcChannel != null) {
            mWebrtcChannel.sendLocalIceCandidate(mApiClient, candidate);
        }
    }

    @Override
    public void onIceConnected() {
        // TODO Auto-generated method stub
        logAndToast("onIceConnected...");

        // logAndToast("ICE connected");
        mIceConnected = true;
        updateVideoView();
    }

    @Override
    public void onIceDisconnected() {
        // TODO Auto-generated method stub
        logAndToast("onIceDisconnected...");

        mIceConnected = false;
        mStreamAdded = false;
        updateVideoView();
    }

    @Override
    public void onAddStream(MediaStream stream) {
        // TODO Auto-generated method stub
        logAndToast("onAddStream...");
        mStreamAdded = true;
    }

    @Override
    public void onPeerConnectionError(String description) {
        // TODO Auto-generated method stub
        logAndToast("onPeerConnectionError...");
    }

    private class MyWebrtcChannel extends WebrtcChannel {
        public void onMessageReceived(FlintDevice flingDevice,
                String namespace, String message) {
            Log.e(TAG, "WebrtcChannel: message received:" + message);
            try {
                JSONObject json = new JSONObject(message);
                String type = (String) json.get("type");
                if (type.equals("candidate")) {
                    IceCandidate candidate = new IceCandidate(
                            (String) json.get("sdpMid"),
                            json.getInt("sdpMLineIndex"),
                            (String) json.get("candidate"));
                    onRemoteIceCandidate(candidate);
                } else if (type.equals("answer") || type.equals("offer")) {
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type),
                            (String) json.get("sdp"));
                    Log.e(TAG, "onMessageReceived:type[" + type + "]sdp["
                            + (String) json.get("sdp") + "]");
                    onRemoteDescription(sdp);
                } else if (type.equals("bye")) {
                    String data = (String) json.get("data");
                    onChannelClose(data);
                } else {
                    onChannelError("Unexpected channel message: " + message);
                }
            } catch (JSONException e) {
                onChannelError("Channel message JSON parsing error: "
                        + e.toString());
            }
        }
    }

    private class MediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            Log.d(TAG, "onRouteAdded");
            if (++mRouteCount == 1) {
                // Show the button when a device is discovered.
                mMediaRouteButton.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            Log.d(TAG, "onRouteRemoved");
            if (--mRouteCount == 0) {
                // Hide the button if there are no devices discovered.
                mMediaRouteButton.setVisibility(View.GONE);
            }
        }

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo route) {
            Log.d(TAG, "onRouteSelected: " + route);
            MainActivity.this.onRouteSelected(route);
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo route) {
            Log.d(TAG, "onRouteUnselected: " + route);
            MainActivity.this.onRouteUnselected(route);
        }
    }

    private class FlingListener extends Flint.Listener {
        @Override
        public void onApplicationDisconnected(int statusCode) {
            Log.d(TAG, "Flint.Listener.onApplicationDisconnected: "
                    + statusCode);

            mSelectedDevice = null;
            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());

            if (mApiClient == null) {
                return;
            }

            try {
                Flint.FlintApi.removeMessageReceivedCallbacks(mApiClient,
                        mWebrtcChannel.getNamespace());
            } catch (IOException e) {
                Log.w(TAG, "Exception while launching application", e);
            }
        }
    }

    /**
     * Called when a user selects a route.
     */
    private void onRouteSelected(RouteInfo route) {
        Log.d(TAG, "onRouteSelected: " + route.getName());

        FlintDevice device = FlintDevice.getFromBundle(route.getExtras());
        setSelectedDevice(device);
    }

    /**
     * Called when a user unselects a route.
     */
    private void onRouteUnselected(RouteInfo route) {
        if (route != null) {
            Log.d(TAG, "onRouteUnselected: " + route.getName());
        }
        setSelectedDevice(null);
    }

    /**
     * Stop receiver application.
     */
    public void stopApplication() {
        if (mApiClient == null || !mApiClient.isConnected()) {
            return;
        }

        Flint.FlintApi.stopApplication(mApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status result) {
                        if (result.isSuccess()) {
                            //
                        }
                    }
                });
    }

    private void setSelectedDevice(FlintDevice device) {
        Log.d(TAG, "setSelectedDevice: " + device);
        mSelectedDevice = device;

        if (mSelectedDevice != null) {
            try {
                disconnectApiClient();
                connectApiClient();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Exception while connecting API client", e);
                disconnectApiClient();
            }
        } else {
            if (mApiClient != null) {
                if (mApiClient.isConnected()) {
                    mWebrtcChannel.sendBye(mApiClient);
                }

                // stopApplication();

                disconnectApiClient();
            }

            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
        }
    }

    private void connectApiClient() {
        Flint.FlintOptions apiOptions = Flint.FlintOptions.builder(
                mSelectedDevice, mFlingListener).build();
        mApiClient = new FlintManager.Builder(this)
                .addApi(Flint.API, apiOptions)
                .addConnectionCallbacks(mConnectionCallbacks).build();
        mApiClient.connect();
    }

    private void disconnectApiClient() {
        if (mApiClient != null) {
            mApiClient.disconnect();
            mApiClient = null;
        }
    }

    private class ConnectionCallbacks implements
            FlintManager.ConnectionCallbacks {
        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "ConnectionCallbacks.onConnectionSuspended");
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "ConnectionCallbacks.onConnected");
            Flint.FlintApi.launchApplication(mApiClient, APP_URL)
                    .setResultCallback(
                            new ApplicationConnectionResultCallback());
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.d(TAG, "ConnectionFailedListener.onConnectionFailed");
            setSelectedDevice(null);
        }
    }

    private final class ApplicationConnectionResultCallback implements
            ResultCallback<ApplicationConnectionResult> {
        @Override
        public void onResult(ApplicationConnectionResult result) {
            Status status = result.getStatus();
            ApplicationMetadata appMetaData = result.getApplicationMetadata();

            if (status.isSuccess()) {
                Log.d(TAG, "ConnectionResultCallback: " + appMetaData.getData());
                try {
                    Flint.FlintApi.setMessageReceivedCallbacks(mApiClient,
                            mWebrtcChannel.getNamespace(), mWebrtcChannel);

                    mWebrtcHelper.initParams(); // start another connection?
                } catch (IOException e) {
                    Log.w(TAG, "Exception while launching application", e);
                }
            } else {
                Log.d(TAG,
                        "ConnectionResultCallback. Unable to launch the game. statusCode: "
                                + status.getStatusCode());
            }
        }
    }

    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (mLogToast != null) {
            mLogToast.cancel();
        }
        mLogToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        mLogToast.show();
    }

    private void disconnect() {
        if (mWebrtcHelper != null) {
            mWebrtcHelper.disconnect();
            mWebrtcHelper = null;
        }
        if (mPeerConn != null) {
            mPeerConn.close();
            mPeerConn = null;
        }
        if (mAudioManager != null) {
            mAudioManager.close();
            mAudioManager = null;
        }
        finish();
    }

    private void updateVideoView() {
        VideoRendererGui.update(mRemoteRender, 0, 0, 100, 100, mScalingType);
        if (mIceConnected && mStreamAdded) {
            VideoRendererGui.update(mLocalRender, 70, 70, 28, 28,
                    ScalingType.SCALE_ASPECT_FIT);
        } else {
            VideoRendererGui.update(mLocalRender, 0, 0, 100, 100, mScalingType);
        }
    }

    private void onRemoteDescription(SessionDescription sdp) {
        // TODO Auto-generated method stub
        logAndToast("onRemoteDescription... " + sdp);

        if (mPeerConn == null) {
            return;
        }
        logAndToast("Received remote " + sdp.type + " ...");
        mPeerConn.setRemoteDescription(sdp);
        if (!mSignalingParameters.initiator) {
            logAndToast("Creating ANSWER...");
            // Create answer. Answer SDP will be sent to offering client in
            // PeerConnectionEvents.onLocalDescription event.
            mPeerConn.createAnswer();
        }
    }

    private void onRemoteIceCandidate(IceCandidate candidate) {
        // TODO Auto-generated method stub

        logAndToast("onRemoteIceCandidate: " + candidate + " ...");
        if (mPeerConn != null) {
            mPeerConn.addRemoteIceCandidate(candidate);
        }
    }

    private void onChannelClose(String description) {
        // TODO Auto-generated method stub
        logAndToast("onChannelClose...");

        mIceConnected = false;
        mStreamAdded = false;
        updateVideoView();
    }

    private void onChannelError(String description) {
        // TODO Auto-generated method stub
        logAndToast("onChannelError...: " + description);

        MainActivity.this.onRouteUnselected(null);
    }

    // Poor-man's assert(): die with |msg| unless |condition| is true.
    private static void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

}
