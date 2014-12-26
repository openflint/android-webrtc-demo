package com.infthink.demo.webrtc;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;

import android.util.Log;

public class WebRtcHelper {
    private static final String TAG = "WebRtcHelper";

    /**
     * Struct holding the signaling parameters of an AppRTC room.
     */
    public static class SignalingParameters {
        public final List<PeerConnection.IceServer> iceServers;
        public final boolean initiator;
        public final MediaConstraints pcConstraints;
        public final MediaConstraints videoConstraints;
        public final MediaConstraints audioConstraints;
        public final String offerSdp;

        public SignalingParameters(List<PeerConnection.IceServer> iceServers,
                boolean initiator, MediaConstraints pcConstraints,
                MediaConstraints videoConstraints,
                MediaConstraints audioConstraints, String offerSdp) {
            this.iceServers = iceServers;
            this.initiator = initiator;
            this.pcConstraints = pcConstraints;
            this.videoConstraints = videoConstraints;
            this.audioConstraints = audioConstraints;
            this.offerSdp = offerSdp;
        }
    }

    /**
     * Callback interface for messages delivered on signaling channel.
     *
     * Methods are guaranteed to be invoked on the UI thread of |activity|.
     */
    public static interface SignalingEvents {
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        public void onParamInitDone(final SignalingParameters params);
    }

    private SignalingEvents events;
    private SignalingParameters signalingParameters;

    public WebRtcHelper(SignalingEvents events) {
        this.events = events;
    }

    public void initParams() {
        try {
            signalingParameters = initParameters();
            events.onParamInitDone(signalingParameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Disconnect
     */
    public void disconnect() {
    }

    // Fetches |url| and fishes the signaling parameters out of the JSON.
    private SignalingParameters initParameters() throws IOException,
            JSONException {
        String offerSdp = null;

        boolean initiator = true; // provide offer?

        LinkedList<PeerConnection.IceServer> iceServers = iceServersFromPCConfigJSON("");

        MediaConstraints pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair(
                "DtlsSrtpKeyAgreement", "true"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"));

        MediaConstraints videoConstraints = constraintsFromJSON(getAVConstraints(
                "video", "{}"));
        Log.d(TAG, "videoConstraints: " + videoConstraints);

        MediaConstraints audioConstraints = constraintsFromJSON(getAVVConstraints(
                "audio", "{}"));
        Log.d(TAG, "audioConstraints: " + audioConstraints);

        return new SignalingParameters(iceServers, initiator, pcConstraints,
                videoConstraints, audioConstraints, offerSdp);
    }

    // Return the constraints specified for |type| of "audio" or "video" in
    // |mediaConstraintsString|.
    private String getAVConstraints(String type, String mediaConstraintsString)
            throws JSONException {
        return "{\"mandatory\": { maxWidth: 1280, maxHeight: 720, minWidth: 640, minHeight: 480}, \"optional\": [{\"VoiceActivityDetection\": false}]}";
        // return
        // "{\"optional\": [{\"minWidth\": \"1280\", \"minHeight\": \"720\"}], \"mandatory\": {}}";
    }

    private String getAVVConstraints(String type, String mediaConstraintsString)
            throws JSONException {
        return "{\"mandatory\": {}, \"optional\": []}";
    }

    private MediaConstraints constraintsFromJSON(String jsonString)
            throws JSONException {
        if (jsonString == null) {
            return null;
        }
        MediaConstraints constraints = new MediaConstraints();
        JSONObject json = new JSONObject(jsonString);
        JSONObject mandatoryJSON = json.optJSONObject("mandatory");
        if (mandatoryJSON != null) {
            JSONArray mandatoryKeys = mandatoryJSON.names();
            if (mandatoryKeys != null) {
                for (int i = 0; i < mandatoryKeys.length(); ++i) {
                    String key = mandatoryKeys.getString(i);
                    String value = mandatoryJSON.getString(key);
                    constraints.mandatory
                            .add(new MediaConstraints.KeyValuePair(key, value));
                }
            }
        }
        JSONArray optionalJSON = json.optJSONArray("optional");
        if (optionalJSON != null) {
            for (int i = 0; i < optionalJSON.length(); ++i) {
                JSONObject keyValueDict = optionalJSON.getJSONObject(i);
                String key = keyValueDict.names().getString(0);
                String value = keyValueDict.getString(key);
                constraints.optional.add(new MediaConstraints.KeyValuePair(key,
                        value));
            }
        }
        return constraints;
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(
            String pcConfig) throws JSONException {
        LinkedList<PeerConnection.IceServer> ret = new LinkedList<PeerConnection.IceServer>();
        ret.add(new
        PeerConnection.IceServer("stun:stun.services.mozilla.com"));

        ret.add(new
                PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        return ret;
    }

}
