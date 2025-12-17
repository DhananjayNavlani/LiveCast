import {
  collection,
  doc,
  onSnapshot,
  query,
  orderBy,
  limit,
  addDoc,
  updateDoc,
  setDoc,
  Timestamp,
  DocumentReference,
  Unsubscribe,
} from 'firebase/firestore';
import { firestore } from '../config/firebase';

export const ICE_SEPARATOR = '$';

export interface OfferAnswer {
  id?: string;
  sdp: string;
  isOffer: boolean;
  isCallActive?: boolean;
  viewerUserId?: string;
  timestamp?: Timestamp;
}

export interface IceCandidate {
  sdpMid: string;
  sdpMLineIndex: number;
  candidate: string;
}

export type SignalingCommand = 'OFFER' | 'ANSWER' | 'ICE' | 'DISCONNECT' | 'STATE';

export interface SignalingMessage {
  command: SignalingCommand;
  data: string;
  user?: unknown;
}

export class SignalingClient {
  private callDoc: DocumentReference | null = null;
  private callId: string | null = null;
  private listeners: Unsubscribe[] = [];
  private onMessageCallback: ((message: SignalingMessage) => void) | null = null;
  private isViewer: boolean = true; // Web is typically the viewer

  constructor() {
    // Don't start listening immediately - wait for connection to be initiated
  }

  setOnMessageCallback(callback: (message: SignalingMessage) => void) {
    this.onMessageCallback = callback;
  }

  private emit(command: SignalingCommand, data: string, user?: unknown) {
    if (this.onMessageCallback) {
      this.onMessageCallback({ command, data, user });
    }
  }

  // Start listening for Android broadcasts (when web is viewer)
  startListeningForBroadcasts() {
    this.isViewer = true;
    const callsRef = collection(firestore, 'calls');
    const q = query(callsRef, orderBy('timestamp', 'desc'), limit(1));

    const unsubscribe = onSnapshot(q, (snapshot) => {
      snapshot.docChanges().forEach((change) => {
        const data = change.doc.data() as OfferAnswer;
        const docTimestamp = data.timestamp?.seconds || 0;
        const now = Timestamp.now().seconds;

        if (change.type === 'added') {
          // Only process recent calls (within last 10 seconds)
          if (now - docTimestamp < 10) {
            this.callId = change.doc.id;
            this.callDoc = doc(firestore, 'calls', this.callId);

            // Android broadcaster sends OFFER, web viewer receives and answers
            if (data.isOffer && data.sdp) {
              console.log('[Signaling] Received offer from Android broadcaster');
              this.emit('OFFER', data.sdp);
              this.startListeningForOfferCandidates();
            }
          }
        } else if (change.type === 'modified') {
          if (!data.isCallActive) {
            console.log('[Signaling] Call disconnected');
            this.emit('DISCONNECT', '');
          } else if (!data.isOffer && data.sdp && !this.isViewer) {
            // If we're the broadcaster and receive an answer
            console.log('[Signaling] Received answer');
            this.emit('ANSWER', data.sdp);
          }
        }
      });
    });

    this.listeners.push(unsubscribe);
  }

  // Send offer to start viewing Android screen (web acts as subscriber/viewer)
  async sendOffer(sdp: string, viewerUserId: string) {
    this.isViewer = true;

    // Create a new call document
    const callsRef = collection(firestore, 'calls');
    const callDocRef = doc(callsRef);
    this.callDoc = callDocRef;
    this.callId = callDocRef.id;

    console.log('[Signaling] Sending offer to view Android screen');

    await setDoc(callDocRef, {
      sdp: sdp,
      isOffer: true,
      isCallActive: true,
      viewerUserId: viewerUserId,
      timestamp: Timestamp.now(),
    });

    // Listen for answer from Android broadcaster
    const unsubscribe = onSnapshot(callDocRef, (snapshot) => {
      const data = snapshot.data() as OfferAnswer | undefined;
      if (data && !data.isOffer && data.sdp) {
        console.log('[Signaling] Received answer from Android');
        this.emit('ANSWER', data.sdp);
      }
      if (data && !data.isCallActive) {
        console.log('[Signaling] Call ended');
        this.emit('DISCONNECT', '');
      }
    });
    this.listeners.push(unsubscribe);

    // Listen for ICE candidates from Android (answerCandidates)
    this.startListeningForAnswerCandidates();
  }

  private startListeningForOfferCandidates() {
    if (!this.callDoc) return;

    const candidatesRef = collection(this.callDoc, 'offerCandidates');
    const unsubscribe = onSnapshot(candidatesRef, (snapshot) => {
      snapshot.docChanges().forEach((change) => {
        if (change.type === 'added') {
          const ice = change.doc.data() as IceCandidate;
          const iceString = `${ice.sdpMid}${ICE_SEPARATOR}${ice.sdpMLineIndex}${ICE_SEPARATOR}${ice.candidate}`;
          console.log('[Signaling] Received ICE candidate from offer');
          this.emit('ICE', iceString);
        }
      });
    });

    this.listeners.push(unsubscribe);
  }

  private startListeningForAnswerCandidates() {
    if (!this.callDoc) return;

    const candidatesRef = collection(this.callDoc, 'answerCandidates');
    const unsubscribe = onSnapshot(candidatesRef, (snapshot) => {
      snapshot.docChanges().forEach((change) => {
        if (change.type === 'added') {
          const ice = change.doc.data() as IceCandidate;
          const iceString = `${ice.sdpMid}${ICE_SEPARATOR}${ice.sdpMLineIndex}${ICE_SEPARATOR}${ice.candidate}`;
          console.log('[Signaling] Received ICE candidate from answer');
          this.emit('ICE', iceString);
        }
      });
    });

    this.listeners.push(unsubscribe);
  }

  // Send answer when web receives offer from Android viewer
  async sendAnswer(sdp: string) {
    if (!this.callDoc) {
      console.error('[Signaling] No active call to send answer');
      return;
    }

    console.log('[Signaling] Sending answer');
    await updateDoc(this.callDoc, {
      sdp: sdp,
      isOffer: false,
      isCallActive: true,
    });

    // Start listening for ICE candidates from offer side
    this.startListeningForOfferCandidates();
  }

  async sendIceCandidate(candidate: RTCIceCandidate) {
    if (!this.callDoc) {
      console.error('[Signaling] No active call to send ICE candidate');
      return;
    }

    // If we're the viewer (sent offer), put ICE in offerCandidates
    // If we're the broadcaster (sent answer), put ICE in answerCandidates
    const collectionName = this.isViewer ? 'offerCandidates' : 'answerCandidates';
    const candidatesRef = collection(this.callDoc, collectionName);

    const ice: IceCandidate = {
      sdpMid: candidate.sdpMid || '',
      sdpMLineIndex: candidate.sdpMLineIndex || 0,
      candidate: candidate.candidate,
    };

    console.log(`[Signaling] Sending ICE candidate to ${collectionName}`);
    await addDoc(candidatesRef, ice);
  }

  async disconnectCall() {
    if (this.callDoc) {
      await updateDoc(this.callDoc, {
        isCallActive: false,
      });
    }
  }

  dispose() {
    this.listeners.forEach((unsubscribe) => unsubscribe());
    this.listeners = [];
    this.callDoc = null;
    this.callId = null;
  }
}

export const signalingClient = new SignalingClient();

