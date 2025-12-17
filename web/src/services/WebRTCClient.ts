import { SignalingClient, SignalingMessage, ICE_SEPARATOR } from './SignalingClient';

// ICE servers configuration - add your STUN/TURN servers here
const ICE_SERVERS: RTCConfiguration = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' },
    // Add TURN servers for better NAT traversal if needed
    // { urls: 'turn:your-turn-server.com:3478', username: 'user', credential: 'pass' }
  ],
  iceCandidatePoolSize: 10,
};

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'failed';

export interface WebRTCCallbacks {
  onRemoteStream: (stream: MediaStream) => void;
  onConnectionStateChange: (state: ConnectionState) => void;
  onDataChannelMessage: (message: string) => void;
}

export class WebRTCClient {
  private peerConnection: RTCPeerConnection | null = null;
  private dataChannel: RTCDataChannel | null = null;
  private signalingClient: SignalingClient;
  private callbacks: WebRTCCallbacks;
  private pendingIceCandidates: RTCIceCandidate[] = [];
  private remoteDescriptionSet = false;
  private isViewer = false; // Whether this client is viewing (sent offer) or broadcasting

  constructor(signalingClient: SignalingClient, callbacks: WebRTCCallbacks) {
    this.signalingClient = signalingClient;
    this.callbacks = callbacks;

    this.signalingClient.setOnMessageCallback(this.handleSignalingMessage.bind(this));
  }

  // Start listening for Android broadcasts (passive mode - wait for offers)
  startListening() {
    this.signalingClient.startListeningForBroadcasts();
  }

  // Initiate viewing session (active mode - send offer to Android)
  async startViewing(userId: string) {
    this.isViewer = true;
    this.callbacks.onConnectionStateChange('connecting');

    // Create peer connection
    this.createPeerConnection();

    // Create data channel for sending commands to Android
    this.dataChannel = this.peerConnection!.createDataChannel('control');
    this.setupDataChannel();

    // Create and send offer
    try {
      const offer = await this.peerConnection!.createOffer({
        offerToReceiveVideo: true,
        offerToReceiveAudio: false,
      });
      await this.peerConnection!.setLocalDescription(offer);
      console.log('[WebRTC] Created offer for viewing');

      // Send offer via signaling
      await this.signalingClient.sendOffer(offer.sdp || '', userId);
    } catch (error) {
      console.error('[WebRTC] Error creating offer:', error);
      this.callbacks.onConnectionStateChange('failed');
    }
  }

  private async handleSignalingMessage(message: SignalingMessage) {
    console.log('[WebRTC] Received signaling message:', message.command);

    switch (message.command) {
      case 'OFFER':
        await this.handleOffer(message.data);
        break;
      case 'ANSWER':
        await this.handleAnswer(message.data);
        break;
      case 'ICE':
        await this.handleRemoteIceCandidate(message.data);
        break;
      case 'DISCONNECT':
        this.handleDisconnect();
        break;
    }
  }

  private createPeerConnection() {
    this.peerConnection = new RTCPeerConnection(ICE_SERVERS);

    // Handle ICE candidates
    this.peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        console.log('[WebRTC] Sending ICE candidate');
        this.signalingClient.sendIceCandidate(event.candidate);
      }
    };

    // Handle connection state changes
    this.peerConnection.onconnectionstatechange = () => {
      const state = this.peerConnection?.connectionState;
      console.log('[WebRTC] Connection state:', state);

      switch (state) {
        case 'connected':
          this.callbacks.onConnectionStateChange('connected');
          break;
        case 'disconnected':
        case 'closed':
          this.callbacks.onConnectionStateChange('disconnected');
          break;
        case 'failed':
          this.callbacks.onConnectionStateChange('failed');
          break;
      }
    };

    // Handle ICE connection state
    this.peerConnection.oniceconnectionstatechange = () => {
      console.log('[WebRTC] ICE connection state:', this.peerConnection?.iceConnectionState);
    };

    // Handle remote stream
    this.peerConnection.ontrack = (event) => {
      console.log('[WebRTC] Received remote track:', event.track.kind);
      if (event.streams && event.streams[0]) {
        this.callbacks.onRemoteStream(event.streams[0]);
      }
    };

    // Handle data channel from remote (when we're broadcaster receiving viewer's data channel)
    this.peerConnection.ondatachannel = (event) => {
      console.log('[WebRTC] Received data channel:', event.channel.label);
      this.dataChannel = event.channel;
      this.setupDataChannel();
    };
  }

  // Handle offer from Android viewer (web is broadcaster)
  private async handleOffer(sdp: string) {
    console.log('[WebRTC] Processing offer (broadcaster mode)');
    this.isViewer = false;
    this.callbacks.onConnectionStateChange('connecting');

    // Create peer connection
    this.createPeerConnection();

    // Set remote description (offer)
    const offer = new RTCSessionDescription({
      type: 'offer',
      sdp: sdp,
    });

    try {
      await this.peerConnection!.setRemoteDescription(offer);
      this.remoteDescriptionSet = true;
      console.log('[WebRTC] Remote description set');

      // Process any pending ICE candidates
      for (const candidate of this.pendingIceCandidates) {
        await this.peerConnection!.addIceCandidate(candidate);
      }
      this.pendingIceCandidates = [];

      // Create and send answer
      const answer = await this.peerConnection!.createAnswer();
      await this.peerConnection!.setLocalDescription(answer);
      console.log('[WebRTC] Answer created and set as local description');

      // Send answer via signaling
      await this.signalingClient.sendAnswer(answer.sdp || '');
    } catch (error) {
      console.error('[WebRTC] Error handling offer:', error);
      this.callbacks.onConnectionStateChange('failed');
    }
  }

  // Handle answer from Android broadcaster (web is viewer)
  private async handleAnswer(sdp: string) {
    console.log('[WebRTC] Processing answer (viewer mode)');

    if (!this.peerConnection) {
      console.error('[WebRTC] No peer connection to set answer');
      return;
    }

    const answer = new RTCSessionDescription({
      type: 'answer',
      sdp: sdp,
    });

    try {
      await this.peerConnection.setRemoteDescription(answer);
      this.remoteDescriptionSet = true;
      console.log('[WebRTC] Remote answer set');

      // Process any pending ICE candidates
      for (const candidate of this.pendingIceCandidates) {
        await this.peerConnection.addIceCandidate(candidate);
      }
      this.pendingIceCandidates = [];
    } catch (error) {
      console.error('[WebRTC] Error handling answer:', error);
      this.callbacks.onConnectionStateChange('failed');
    }
  }

  private async handleRemoteIceCandidate(iceString: string) {
    const parts = iceString.split(ICE_SEPARATOR);
    if (parts.length !== 3) {
      console.error('[WebRTC] Invalid ICE candidate format');
      return;
    }

    const [sdpMid, sdpMLineIndex, candidate] = parts;
    const iceCandidate = new RTCIceCandidate({
      sdpMid,
      sdpMLineIndex: parseInt(sdpMLineIndex, 10),
      candidate,
    });

    if (this.peerConnection && this.remoteDescriptionSet) {
      try {
        await this.peerConnection.addIceCandidate(iceCandidate);
        console.log('[WebRTC] Added ICE candidate');
      } catch (error) {
        console.error('[WebRTC] Error adding ICE candidate:', error);
      }
    } else {
      // Queue candidate if remote description not set yet
      this.pendingIceCandidates.push(iceCandidate);
      console.log('[WebRTC] Queued ICE candidate');
    }
  }

  private setupDataChannel() {
    if (!this.dataChannel) return;

    this.dataChannel.onopen = () => {
      console.log('[WebRTC] Data channel opened');
    };

    this.dataChannel.onmessage = (event) => {
      console.log('[WebRTC] Data channel message:', event.data);
      this.callbacks.onDataChannelMessage(event.data);
    };

    this.dataChannel.onclose = () => {
      console.log('[WebRTC] Data channel closed');
    };

    this.dataChannel.onerror = (error) => {
      console.error('[WebRTC] Data channel error:', error);
    };
  }

  private handleDisconnect() {
    console.log('[WebRTC] Handling disconnect');
    this.dispose();
    this.callbacks.onConnectionStateChange('disconnected');
  }

  // Send touch event to Android
  // Format: "x y endX endY gestureType" (space-separated)
  // gestureType: TAP, DOUBLE_TAP, LONG_PRESS, PRESS, DRAG_START, DRAG_END, etc.
  sendTouchEvent(gestureType: string, x: number, y: number, endX?: number, endY?: number) {
    if (!this.dataChannel || this.dataChannel.readyState !== 'open') {
      console.warn('[WebRTC] Data channel not ready');
      return;
    }

    // Convert normalized coordinates (0-1) to pixel coordinates
    // Note: The Android side expects pixel coordinates that will be scaled
    // For now, we'll send normalized values and let the Android side handle scaling
    const message = `${x} ${y} ${endX ?? ''} ${endY ?? ''} ${gestureType}`;

    this.dataChannel.send(message);
    console.log('[WebRTC] Sent touch event:', message);
  }

  // Send navigation command to Android
  // Format: Single word matching CallAction enum: "Home", "GoBack", "GoToRecent", "UnlockDevice"
  sendNavigationCommand(command: 'home' | 'back' | 'recent' | 'power') {
    if (!this.dataChannel || this.dataChannel.readyState !== 'open') {
      console.warn('[WebRTC] Data channel not ready');
      return;
    }

    // Map web command names to Android CallAction enum names
    const commandMap: Record<string, string> = {
      home: 'Home',
      back: 'GoBack',
      recent: 'GoToRecent',
      power: 'UnlockDevice',
    };

    const message = commandMap[command];
    this.dataChannel.send(message);
    console.log('[WebRTC] Sent navigation command:', message);
  }

  async disconnect() {
    await this.signalingClient.disconnectCall();
    this.dispose();
  }

  dispose() {
    if (this.dataChannel) {
      this.dataChannel.close();
      this.dataChannel = null;
    }

    if (this.peerConnection) {
      this.peerConnection.close();
      this.peerConnection = null;
    }

    this.remoteDescriptionSet = false;
    this.pendingIceCandidates = [];
    console.log('[WebRTC] Disposed');
  }
}

