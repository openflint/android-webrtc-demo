package com.infthink.demo.webrtc;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import tv.matchstick.flint.Flint;
import tv.matchstick.flint.FlintDevice;
import tv.matchstick.flint.FlintManager;
import tv.matchstick.flint.ResultCallback;
import tv.matchstick.flint.Status;
import android.util.Log;

public abstract class WebrtcChannel implements Flint.MessageReceivedCallback {
    private static final String TAG = WebrtcChannel.class.getSimpleName();

    private static final String WEBRTC_NAMESPACE = "urn:flint:com.infthink.demo.webrtc";

    protected WebrtcChannel() {
    }

    /**
     * Returns the namespace for this fling channel.
     */
    public String getNamespace() {
        return WEBRTC_NAMESPACE;
    }

    @Override
    public void onMessageReceived(FlintDevice flingDevice, String namespace,
            String message) {
        Log.d(TAG, "onTextMessageReceived: " + message);
    }

    private final class SendMessageResultCallback implements
            ResultCallback<Status> {
        String mMessage;

        SendMessageResultCallback(String message) {
            mMessage = message;
        }

        @Override
        public void onResult(Status result) {
            if (!result.isSuccess()) {
                Log.d(TAG,
                        "Failed to send message. statusCode: "
                                + result.getStatusCode() + " message: "
                                + mMessage);
            }
        }
    }

    /**
     * Send local SDP (offer or answer, depending on role) to the other
     * participant. Note that it is important to send the output of
     * create{Offer,Answer} and not merely the current value of
     * getLocalDescription() because the latter may include ICE candidates that
     * we might want to filter elsewhere.
     */
    public void sendOfferSdp(FlintManager apiClient,
            final SessionDescription sdp) {
        Log.e("flint_webrtc", "Offer[" + sdp.description + "]");
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "offer");
        jsonPut(json, "sdp", sdp.description);
        sendMessage(apiClient, json.toString());
    }

    public void sendAnswerSdp(FlintManager apiClient,
            final SessionDescription sdp) {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "answer");
        jsonPut(json, "sdp", sdp.description);
        sendMessage(apiClient, json.toString());
    }

    /**
     * Send Ice candidate to the other participant.
     */
    public void sendLocalIceCandidate(FlintManager apiClient,
            final IceCandidate candidate) {
        Log.e("flint_webrtc", "sendLocalIceCandidate:sdpMLineIndex[" + candidate.sdpMLineIndex+ "]sdpMid[" + candidate.sdpMid + "]candidate[" +candidate.sdp +"]");

        JSONObject json = new JSONObject();
        jsonPut(json, "type", "candidate");
        jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
        jsonPut(json, "sdpMid", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        sendMessage(apiClient, json.toString());
    }

    public void sendSwitchView(FlintManager apiClient) {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "switchview");
        sendMessage(apiClient, json.toString());
    }

    public void sendHello(FlintManager apiClient) {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "hello");
        sendMessage(apiClient, json.toString());
    }
    
    public void sendBye(FlintManager apiClient) {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "bye");
        sendMessage(apiClient, json.toString());
    }
    
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private final void sendMessage(FlintManager apiClient, String message) {
        Log.d(TAG, "Sending message: (ns=" + WEBRTC_NAMESPACE + ") " + message);
        Flint.FlintApi.sendMessage(apiClient, WEBRTC_NAMESPACE, message)
                .setResultCallback(new SendMessageResultCallback(message));
    }
}
