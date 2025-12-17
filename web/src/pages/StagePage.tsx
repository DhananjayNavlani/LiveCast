import { useEffect, useRef, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import Button from '../components/Button';
import { SignalingClient } from '../services/SignalingClient';
import { WebRTCClient, ConnectionState } from '../services/WebRTCClient';

export default function StagePage() {
  const { user, signOut, isLoading } = useAuth();
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoContainerRef = useRef<HTMLDivElement>(null);

  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [webrtcClient, setWebrtcClient] = useState<WebRTCClient | null>(null);
  const [signalingClient, setSignalingClient] = useState<SignalingClient | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);

  const handleSignOut = async () => {
    webrtcClient?.dispose();
    signalingClient?.dispose();
    await signOut();
    navigate('/');
  };

  // Handle remote stream
  const handleRemoteStream = useCallback((stream: MediaStream) => {
    console.log('[Stage] Received remote stream');
    if (videoRef.current) {
      videoRef.current.srcObject = stream;
      videoRef.current.play().catch(console.error);
    }
  }, []);

  // Handle connection state changes
  const handleConnectionStateChange = useCallback((state: ConnectionState) => {
    console.log('[Stage] Connection state changed:', state);
    setConnectionState(state);
  }, []);

  // Handle data channel messages from Android
  const handleDataChannelMessage = useCallback((message: string) => {
    console.log('[Stage] Data channel message:', message);
  }, []);

  // Initialize WebRTC clients
  useEffect(() => {
    const signaling = new SignalingClient();
    const rtc = new WebRTCClient(signaling, {
      onRemoteStream: handleRemoteStream,
      onConnectionStateChange: handleConnectionStateChange,
      onDataChannelMessage: handleDataChannelMessage,
    });

    setSignalingClient(signaling);
    setWebrtcClient(rtc);
    setIsInitialized(true);

    return () => {
      rtc.dispose();
      signaling.dispose();
    };
  }, [handleRemoteStream, handleConnectionStateChange, handleDataChannelMessage]);

  // Start listening for Android broadcasts
  const handleStartListening = () => {
    if (webrtcClient && signalingClient) {
      webrtcClient.startListening();
      setConnectionState('connecting');
    }
  };

  // Initiate viewing session (send offer to Android)
  const handleStartViewing = async () => {
    if (webrtcClient && user) {
      await webrtcClient.startViewing(user.uid);
    }
  };

  // Touch event handlers for remote control
  const handleTouchStart = (e: React.TouchEvent | React.MouseEvent) => {
    if (connectionState !== 'connected' || !webrtcClient || !videoContainerRef.current) return;

    const rect = videoContainerRef.current.getBoundingClientRect();
    let x: number, y: number;

    if ('touches' in e) {
      x = (e.touches[0].clientX - rect.left) / rect.width;
      y = (e.touches[0].clientY - rect.top) / rect.height;
    } else {
      x = (e.clientX - rect.left) / rect.width;
      y = (e.clientY - rect.top) / rect.height;
    }

    webrtcClient.sendTouchEvent('TAP', x, y);
  };

  // Navigation button handlers
  const handleNavigation = (command: 'home' | 'back' | 'recent' | 'power') => {
    if (connectionState !== 'connected' || !webrtcClient) return;
    webrtcClient.sendNavigationCommand(command);
  };

  const handleDisconnect = async () => {
    if (webrtcClient) {
      await webrtcClient.disconnect();
    }
  };

  const isConnected = connectionState === 'connected';
  const isConnecting = connectionState === 'connecting';

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="glass border-b border-border sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
              <span className="text-white text-lg font-bold">▶</span>
            </div>
            <span className="font-bold text-xl text-text">LiveCast</span>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right mr-2">
              <p className="text-sm font-medium text-text">
                {user?.displayName || user?.email || 'Guest User'}
              </p>
              <p className="text-xs text-text-muted">
                {user?.isAnonymous ? 'Guest Account' : 'Subscriber'}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={handleSignOut} isLoading={isLoading}>
              Sign Out
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-12">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="text-center mb-12"
        >
          <div className={`inline-flex items-center gap-2 rounded-full px-4 py-2 mb-6 border ${
            isConnected ? 'bg-green-500/10 border-green-500/30 text-green-400' :
            isConnecting ? 'bg-yellow-500/10 border-yellow-500/30 text-yellow-400' :
            'bg-surface border-border text-text-secondary'
          }`}>
            <span className={`relative flex h-2 w-2`}>
              {(isConnected || isConnecting) && (
                <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${
                  isConnected ? 'bg-green-400' : 'bg-yellow-400'
                }`}></span>
              )}
              <span className={`relative inline-flex rounded-full h-2 w-2 ${
                isConnected ? 'bg-green-500' : isConnecting ? 'bg-yellow-500' : 'bg-text-muted'
              }`}></span>
            </span>
            <span className="text-sm font-medium">
              {isConnected ? 'Connected' : isConnecting ? 'Connecting...' : 'Waiting for broadcast'}
            </span>
          </div>

          <h1 className="text-3xl font-bold mb-4 text-text">
            {isConnected ? 'Live Stream' : 'Welcome to LiveCast Stage'}
          </h1>
          <p className="text-text-secondary max-w-xl mx-auto">
            {isConnected
              ? 'You are viewing the Android device stream. Click/tap on the video to interact.'
              : 'Start the LiveCast app on your Android device and begin broadcasting to see the stream here.'}
          </p>
        </motion.div>

        {/* Stream Area */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="bg-surface rounded-2xl border border-border overflow-hidden glow-sm"
        >
          {/* Stream Header */}
          <div className="px-6 py-4 border-b border-border flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-background-light rounded-full flex items-center justify-center">
                📱
              </div>
              <div>
                <p className="font-medium text-text">
                  {isConnected ? 'Android Device' : isConnecting ? 'Connecting...' : 'Waiting for broadcast...'}
                </p>
                <p className="text-sm text-text-muted">
                  {isConnected ? 'Live stream active' : 'No active streams'}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-2">
                <span className={`w-2 h-2 rounded-full ${
                  isConnected ? 'bg-green-500' : isConnecting ? 'bg-yellow-500' : 'bg-text-muted'
                }`} />
                <span className="text-sm text-text-secondary">
                  {isConnected ? 'Online' : isConnecting ? 'Connecting' : 'Offline'}
                </span>
              </div>
              {isConnected && (
                <Button variant="outline" size="sm" onClick={handleDisconnect}>
                  Disconnect
                </Button>
              )}
            </div>
          </div>

          {/* Video Area */}
          <div
            ref={videoContainerRef}
            className="aspect-video bg-background flex items-center justify-center relative"
            onClick={handleTouchStart}
            onTouchStart={handleTouchStart}
          >
            {isConnected ? (
              <video
                ref={videoRef}
                className="w-full h-full object-contain"
                autoPlay
                playsInline
                muted={false}
              />
            ) : (
              <div className="text-center">
                <div className={`w-20 h-20 mx-auto mb-6 rounded-2xl bg-surface border border-border flex items-center justify-center ${isConnecting ? '' : 'animate-pulse'}`}>
                  {isConnecting ? (
                    <div className="loading-spinner" />
                  ) : (
                    <span className="text-4xl">📺</span>
                  )}
                </div>
                <h3 className="text-xl font-semibold mb-2 text-text">
                  {isConnecting ? 'Connecting to Android...' : 'No Active Broadcast'}
                </h3>
                <p className="text-text-secondary max-w-md mx-auto mb-6">
                  {isConnecting
                    ? 'Establishing WebRTC connection...'
                    : 'Start the LiveCast app on an Android device and begin broadcasting, then connect from here.'}
                </p>
                {!isConnecting && isInitialized && (
                  <div className="flex flex-col sm:flex-row gap-3 justify-center">
                    <Button
                      variant="primary"
                      onClick={handleStartViewing}
                    >
                      📡 Connect to Android
                    </Button>
                    <Button
                      variant="outline"
                      onClick={handleStartListening}
                    >
                      👀 Wait for Broadcast
                    </Button>
                  </div>
                )}
              </div>
            )}

            {/* Touch overlay for interaction when connected */}
            {isConnected && (
              <div className="absolute inset-0 touch-overlay" />
            )}
          </div>

          {/* Controls */}
          <div className="px-6 py-4 border-t border-border bg-background-light">
            <div className="flex items-center justify-center gap-4">
              {[
                { icon: '🏠', command: 'home' as const, title: 'Home' },
                { icon: '◀️', command: 'back' as const, title: 'Back' },
                { icon: '⏹️', command: 'recent' as const, title: 'Recent Apps' },
                { icon: '🔓', command: 'power' as const, title: 'Power/Lock' },
              ].map((btn) => (
                <button
                  key={btn.command}
                  disabled={!isConnected}
                  onClick={() => handleNavigation(btn.command)}
                  className={`p-3 rounded-xl transition-all duration-200 ${
                    isConnected
                      ? 'bg-surface text-text hover:bg-surface-light hover:scale-105 cursor-pointer border border-border hover:border-primary/50'
                      : 'bg-surface/50 text-text-muted cursor-not-allowed border border-border/50'
                  }`}
                  title={btn.title}
                >
                  <span className="text-xl">{btn.icon}</span>
                </button>
              ))}
            </div>
            <p className="text-center text-xs text-text-muted mt-3">
              {isConnected
                ? 'Click on the video to interact • Use buttons for navigation'
                : 'Controls will be enabled when a broadcast is active'}
            </p>
          </div>
        </motion.div>

        {/* Instructions */}
        {!isConnected && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.4 }}
            className="mt-12 grid md:grid-cols-3 gap-6"
          >
            {[
              { step: '1', icon: '📱', title: 'Open Android App', desc: 'Launch the LiveCast app on your Android device' },
              { step: '2', icon: '📡', title: 'Start Broadcasting', desc: 'Tap the broadcast button to share your screen' },
              { step: '3', icon: '🎮', title: 'View & Control', desc: 'The stream will appear above with touch controls' },
            ].map((item) => (
              <div key={item.step} className="bg-surface rounded-xl p-6 border border-border card-hover">
                <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-lg font-bold text-white mb-4">
                  {item.step}
                </div>
                <h3 className="font-semibold mb-2 text-text flex items-center gap-2">
                  {item.title}
                  <span>{item.icon}</span>
                </h3>
                <p className="text-sm text-text-secondary">
                  {item.desc}
                </p>
              </div>
            ))}
          </motion.div>
        )}
      </main>
    </div>
  );
}

