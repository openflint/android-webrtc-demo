var flint = window.flint || {};

(function () {
  'use strict';
  
  // namespace which should be equal to sender's.   
  var WEBRTC_NAMESPACE = 'urn:flint:com.infthink.demo.webrtc';
 
  // APP NAME
  var WEBRTC_APPNAME = '~flint_android_webrtc_demo';

  function Webrtc(divs) {
    self = this;

    self.peers = {};
    self.videos = {};
    self.streams = {};
  
    window.flintReceiverManager = new ReceiverManagerWrapper(WEBRTC_APPNAME);
    window.messageBus = window.flintReceiverManager.createMessageBus(WEBRTC_NAMESPACE);
    window.messageBus.on("message", function(senderId, message) {
      console.log("onMessage called with: " + message);
      var data = JSON.parse(message);
      ("onMessage" in self) && self.onMessage(senderId, data);
    });

    window.messageBus.onsenderConnected = self.onSenderConnected.bind(this);
    window.messageBus.onsenderDisconnected = self.onSenderDisconnected.bind(this);

    // webkitRTCPeerConnection is Chrome specific
    window.RTCPeerConnection = window.RTCPeerConnection|| window.mozRTCPeerConnection || window.webkitRTCPeerConnection;
    window.SessionDescription = window.RTCSessionDescription || window.mozRTCSessionDescription || window.webkitRTCSessionDescription;
    window.RTCIceCandidate = window.RTCIceCandidate || window.mozRTCIceCandidate;
    window.URL = window.URL || window.webkitURL || window.mozURL || window.msURL;

    // ready to receive messages
    window.flintReceiverManager.open();
  };

  Webrtc.prototype = {

    log: function(msg) {
      console.log("flint.Webrtc: " + msg);
    },

    failure: function(x) {
      this.log("ERROR: " + JSON.stringify(x));
    },
    
    getPeerConnection: function(senderId) {
      return this.peers[senderId]; 
    },

    // hide video videw
    hideVideo: function(element) {
      element.classList.remove('active');
      element.classList.add('hidden');
    },
 
    // show video view
    showVideo: function(element) {
        element.classList.remove('hidden');
        element.classList.add('active');
    },

    // called when sender connected
    onSenderConnected: function (senderId) {
      this.log('onSenderConnected. Total number of senders: ' + Object.keys(window.flintReceiverManager.getSenderList()).length);
      self.showVideo(divs.large_video);
      if (Object.keys(window.flintReceiverManager.getSenderList()).length == 2) {
        self.showVideo(divs.mini_video);
      }
    },

    // called when sender disconnected
    onSenderDisconnected: function (senderId) {
      // delete related data
      var pc = this.getPeerConnection(senderId);
      if (pc !== 'undefined') {
        pc.close();
        delete self.peers[senderId];
        delete self.videos[senderId];
        delete self.streams[senderId];
      }

      // hide mini video
      self.hideVideo(divs.mini_video);

      this.log('onSenderDisconnected. Total number of senders: ' + Object.keys(window.flintReceiverManager.getSenderList()).length);

      // display large video
      if (Object.keys(window.flintReceiverManager.getSenderList()).length == 1) {
          var sender = Object.keys(window.flintReceiverManager.getSenderList())[0];
          if (senderId == sender) {
            this.log("disconnected??");
            return;
          }

          self.attachMediaStream(divs.large_video, self.streams[sender]);
          divs.mini_video.src = "";
          
          self.videos[sender] = 'large';
         
          // notify all that someone left
          self.broadcastBye(senderId);
      }

      // close app?
      if (Object.keys(window.flintReceiverManager.getSenderList()).length == 0) {
        window.close();
      }
    },

    // send message to sender app
    sendMessage : function(senderId, msg) {
      if (msg == null) {
        this.log("send ignore for msg is null!!");
        return;
      }
      this.log("Sending: senderId: " + senderId + " msg:"+ msg);
      window.messageBus.send(JSON.stringify(msg), senderId);
    },

    // called when received message from sender app
    onMessage: function(senderId, data) {
      if (!data) {
        return;
      }

      if (Object.keys(window.flintReceiverManager.getSenderList()).length > 2) {
        this.sendMessage(senderId, {'type': 'byte', 'data':'room is full!Current user num[' + Object.keys(window.flintReceiverManager.getSenderList()).length + ']'});
	return;
      }
      
      this.log("Received message " + JSON.stringify(data));

      if (data) {
        if (data.type === "offer") {
         this.processOffer(senderId, data);
        } else if (data.type === 'candidate') {
          this.processIceCandidate(senderId, data);
        } else if (data.type === "switchview") {
          this.switchView(senderId, data);
        } else if (data.type === "bye") {
          this.broadcastBye(senderId);
        } else {
          this.log("unknown command!!!" + data.type);
        }
      }
    },

    processAnswer: function(senderId, sdp) {
      this.log('Received answer...' + sdp.sdp);
      var des = new window.SessionDescription(sdp);
      var pc = self.getPeerConnection(senderId);
      pc.setRemoteDescription(des);
    },

    // switch video view. mini<->large
    switchView: function() {
      this.log("switchView!!");
      var senders = Object.keys(window.flintReceiverManager.getSenderList());
      if (senders.length == 0) {
        return;
      }

      // switch views. mini<->large
      if (senders.length == 2) {
          var large = divs.large_video.src;
          var mini = divs.mini_video.src;
          divs.large_video.src = mini;
          divs.mini_video.src = large;
         
          var one = senders[0];
          var other = senders[1];
          var source = self.videos[one];
          self.videos[one] = self.videos[other];
          self.videos[other] = source;
      }
    },
  
    // broadcast bye
    broadcastBye: function(senderId) {
      window.messageBus.send("{'type':'bye', 'data': 'some user is left!'}");
    },

    reattachMediaStream: function(to, from) {
      if (typeof to.srcObject !== 'undefined') {
        to.srcObject = from.srcObject;
      } else {
        to.src = from.src;
      }
    },

    attachMediaStream: function(element, stream) {
      if (typeof element.srcObject !== 'undefined') {
        element.srcObject = stream;
      } else if (typeof element.mozSrcObject !== 'undefined') {
        element.mozSrcObject = stream;
      } else if (typeof element.src !== 'undefined') {
        element.src = URL.createObjectURL(stream);
      } else {
        console.log('Error attaching stream to element.');
      }
    },

    // process offer. Echo sender will be serviced by one created RTCPeerConnection.
    processOffer: function(senderId, sdp) {
      this.log("Applying offer");

      function _createPeerConnection(senderId) {
        //var config = {"iceServers":[]};
        var config = {"iceServers":[{"url":"stun:stun.services.mozilla.com"}, {"url": "stun:stun.l.google.com:19302"}]};

        var pc = new window.RTCPeerConnection(config, {});

        if (self.peers[senderId]) {
          var peerConn = self.peers[senderId];
          peerConn.close();
          delete self.peers[senderId];
        }

        // save it?
        self.peers[senderId] = pc;

        // Set callbacks or new media streams
        pc.onaddstream = function(obj) {
          self.log("Adding remote video stream!");

          // let the new guy displayed in large video view
          self.attachMediaStream(divs.large_video, obj.stream);
          self.videos[senderId] = 'large';
          self.streams[senderId] = obj.stream;

          var senders = Object.keys(window.flintReceiverManager.getSenderList());

          // TEMP fix WA for no stream issue
          //if (senders.length == 1) {
          //  pc.addStream(obj.stream);
          //}

          // let the other one displayed in mini video view.
          if (senders.length == 2) {
            var one = senders[0];
            var other = senders[1];
            var mini = null;
            if (one != senderId) {
              mini = one;
            } else {
              mini = other;
            }
            self.attachMediaStream(divs.mini_video, self.streams[mini]);
            self.videos[mini] = 'mini';

            /*
            // let remote video displayed on sender app
            if (one == senderId) {
              self.log("add stream one?????");
              pc.addStream(self.streams[other]);

              var peerConn = self.getPeerConnection(other);
              peerConn.removeStream(self.streams[other]);
              peerConn.addStream(self.streams[one]);
            } else {
              self.log("add stream other?????");
              pc.addStream(self.streams[one]);

              var peerConn = self.getPeerConnection(one);
              peerConn.removeStream(self.streams[one]);
              peerConn.addStream(self.streams[other]);
            }
            */
          }
        }

        pc.onremovestream = function(obj) {
          self.log("Remove video stream");
        }
 
        pc.onicecandidate = _onIceCandidate.bind(this);
        pc.onsignalingstatechange = _onSignalingStateChange.bind(this);
        pc.oniceconnectionstatechange = _onIceConnectionStateChange.bind(this);
        pc.onicechange = _onIceStateChange.bind(this);

        return pc;
      }

      function transitionToActive() {
        divs.large_video.oncanplay = undefined;
      }

      function waitForRemoteVideo() {
        if (divs.large_video.readyState >= 2) {
          self.log('Remote video started; currentTime: ' + divs.large_video.currentTime);
          transitionToActive();
        } else {
          divs.large_video.oncanplay = waitForRemoteVideo;
        }
      }

      function _setRemoteOfferSuccess() {
        var remoteStreams = pc.getRemoteStreams();
        if (remoteStreams.length > 0 && remoteStreams[0].getVideoTracks().length > 0) {
          self.log("Waiting for remote video.");
          waitForRemoteVideo();
        }
        self.log("Successfully applied offer...create answer!");
        var mediaConstraints = {
          'mandatory': {
        	'OfferToReceiveAudio': true,
                'OfferToReceiveVideo': true
          },
        };
        pc.createAnswer(_createAnswerSuccess.bind(this), self.failure, mediaConstraints);
      }

      function _createAnswerSuccess(sdp) {
        self.log("Successfully created answer " + JSON.stringify(sdp));

        pc.setLocalDescription(sdp, _setLocalAnswerSuccess, self.failure);
        self.sendMessage(senderId, sdp);
      }

      function _setLocalAnswerSuccess(sdp) {
        self.log("Successfully applied local description: " + JSON.stringify(sdp));
      }

      function _onIceCandidate(evt) {
        self.log("New ICE candidate:" + evt);

        if (evt.candidate) {
          window.messageBus.send(JSON.stringify({
		type: "candidate",
		sdpMLineIndex: evt.candidate.sdpMLineIndex,
		sdpMid: evt.candidate.sdpMid,
		candidate: evt.candidate.candidate
		}) , senderId);
        }  
      }

      function _onSignalingStateChange() {
        self.log("Signaling state change. New state = " + pc.signalingState);
      }

      function _onIceConnectionStateChange() {
        self.log("Ice state change. New state = " + pc.iceConnectionState);
      }

      function _onIceStateChange(x) {
        self.log("Ice state change. New state = " + x);
      }

      var pc = _createPeerConnection(senderId);
      pc.setRemoteDescription(new window.SessionDescription(sdp),
                                 _setRemoteOfferSuccess.bind(this), self.failure);
    },

    processIceCandidate: function(senderId, msg) {
      this.log("Applying ICE candidate: " + JSON.stringify(msg));
      var candidate = new window.RTCIceCandidate({
		sdpMLineIndex: msg.sdpMLineIndex,
		sdpMid: msg.sdpMid,
		candidate: msg.candidate
      });

      var pc = self.getPeerConnection(senderId);
      if (pc) {
        pc.addIceCandidate(candidate);
      } else {
        this.failure("processIceCandidate: " + candidate + " pc is null!");
      }
    },
  };

  // Exposes public functions and APIs
  flint.Webrtc = Webrtc;
})();
