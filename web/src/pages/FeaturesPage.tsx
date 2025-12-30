import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import Button from '../components/Button';
import ThemeToggle from '../components/ThemeToggle';

interface FeatureDetailProps {
  emoji: string;
  title: string;
  description: string;
  highlights: string[];
}

function FeatureDetail({ emoji, title, description, highlights }: FeatureDetailProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      viewport={{ once: true }}
      className="group bg-surface rounded-2xl p-8 border border-border hover:border-primary/50 transition-all duration-300 card-hover"
    >
      <div className="absolute inset-0 rounded-2xl bg-gradient-to-r from-primary/5 to-secondary/5 opacity-0 group-hover:opacity-100 transition-opacity" />

      <div className="relative flex items-start gap-6">
        <div className="flex-shrink-0 w-16 h-16 bg-background-light rounded-2xl flex items-center justify-center text-3xl group-hover:scale-110 transition-transform duration-300">
          {emoji}
        </div>
        <div className="flex-1">
          <h3 className="text-xl font-semibold mb-3 text-text">{title}</h3>
          <p className="text-text-secondary mb-4 leading-relaxed">{description}</p>
          <ul className="grid md:grid-cols-2 gap-2">
            {highlights.map((highlight, index) => (
              <li key={index} className="flex items-center gap-2 text-sm text-text-secondary">
                <span className="w-1.5 h-1.5 bg-primary rounded-full" />
                {highlight}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </motion.div>
  );
}

export default function FeaturesPage() {
  return (
    <div className="min-h-screen bg-background">
      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 glass">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
              <span className="text-white text-lg font-bold">▶</span>
            </div>
            <span className="font-bold text-xl text-text">LiveCast</span>
          </Link>
          <div className="flex items-center gap-4">
            <Link to="/" className="text-text-secondary hover:text-text transition-colors">
              Home
            </Link>
            <Link to="/download" className="text-text-secondary hover:text-text transition-colors">
              Download
            </Link>
            <ThemeToggle />
            <Link to="/login">
              <Button variant="primary" size="sm">Get Started</Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section className="relative pt-32 pb-16 px-6 overflow-hidden">
        <div className="absolute inset-0 grid-pattern" />
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary/20 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-secondary/20 rounded-full blur-3xl" />

        <div className="relative max-w-4xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <h1 className="text-4xl md:text-5xl font-bold mb-6 text-text">
              Powerful <span className="gradient-text">Features</span>
            </h1>
            <p className="text-xl text-text-secondary max-w-2xl mx-auto">
              Everything you need for seamless screen sharing and remote device control
            </p>
          </motion.div>
        </div>
      </section>

      {/* Core Features */}
      <section className="py-16 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            className="mb-12"
          >
            <h2 className="text-2xl font-bold mb-2 text-text">Core Features</h2>
            <p className="text-text-secondary">The essentials for screen sharing and control</p>
          </motion.div>

          <div className="space-y-6">
            <FeatureDetail
              emoji="📺"
              title="Real-Time Screen Streaming"
              description="Share your Android device screen in real-time with minimal latency. Powered by WebRTC technology for peer-to-peer streaming with automatic quality adjustment based on network conditions."
              highlights={[
                '1080p video quality support',
                '30 FPS smooth streaming',
                'Automatic bitrate adjustment',
                'Works over WiFi and mobile data'
              ]}
            />
            <FeatureDetail
              emoji="👆"
              title="Touch Gesture Control"
              description="Control the broadcasting device remotely using intuitive touch gestures. All gestures are captured and transmitted in real-time to provide a seamless remote control experience."
              highlights={[
                'Single tap, double tap, long press',
                'Swipe gestures (all directions)',
                'Pinch to zoom support',
                'Smooth drag and scroll'
              ]}
            />
            <FeatureDetail
              emoji="🔓"
              title="Device Navigation Controls"
              description="Navigate the remote device as if you were holding it in your hands. Access system-level controls for complete device management."
              highlights={[
                'Home button functionality',
                'Back navigation',
                'Recent apps access',
                'Device unlock capability'
              ]}
            />
          </div>
        </div>
      </section>

      {/* Technical Features */}
      <section className="py-16 px-6 relative">
        <div className="absolute inset-0 bg-gradient-to-b from-background via-background-light/30 to-background" />

        <div className="relative max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            className="mb-12"
          >
            <h2 className="text-2xl font-bold mb-2 text-text">Technical Features</h2>
            <p className="text-text-secondary">Built with cutting-edge technology</p>
          </motion.div>

          <div className="space-y-6">
            <FeatureDetail
              emoji="🔗"
              title="WebRTC Technology"
              description="Built on the WebRTC standard for reliable, low-latency peer-to-peer connections. No server relay needed for video data."
              highlights={[
                'Direct peer-to-peer connection',
                'NAT traversal with STUN/TURN',
                'Adaptive streaming quality',
                'Browser-native support'
              ]}
            />
            <FeatureDetail
              emoji="🔐"
              title="Firebase Integration"
              description="Secure authentication and real-time signaling powered by Firebase. Your data is protected with industry-standard security."
              highlights={[
                'Email/password authentication',
                'Google Sign-In support',
                'Real-time Firestore signaling',
                'Secure data transmission'
              ]}
            />
          </div>
        </div>
      </section>

      {/* Platform Support */}
      <section className="py-16 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            className="mb-12"
          >
            <h2 className="text-2xl font-bold mb-2 text-text">Platform Support</h2>
            <p className="text-text-secondary">Works across all your devices</p>
          </motion.div>

          <div className="grid md:grid-cols-2 gap-6">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5 }}
              viewport={{ once: true }}
              className="bg-surface rounded-2xl p-6 border border-border card-hover"
            >
              <div className="text-4xl mb-4">📱</div>
              <h3 className="font-semibold text-lg mb-2 text-text">Android App (Broadcaster)</h3>
              <p className="text-text-secondary text-sm mb-4">
                Native Android app for broadcasting your screen. Supports Android 8.0+ with screen capture and accessibility services.
              </p>
              <ul className="space-y-2">
                {['Screen mirroring', 'Remote touch injection', 'System navigation'].map((item) => (
                  <li key={item} className="flex items-center gap-2 text-sm text-text-secondary">
                    <span className="text-primary">✓</span> {item}
                  </li>
                ))}
              </ul>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.1 }}
              viewport={{ once: true }}
              className="bg-surface rounded-2xl p-6 border border-border card-hover"
            >
              <div className="text-4xl mb-4">🌐</div>
              <h3 className="font-semibold text-lg mb-2 text-text">Web App (Viewer)</h3>
              <p className="text-text-secondary text-sm mb-4">
                View and control streams from any modern browser. No installation required — just sign in and connect.
              </p>
              <ul className="space-y-2">
                {['Chrome, Firefox, Safari, Edge', 'Responsive design', 'Touch & mouse support'].map((item) => (
                  <li key={item} className="flex items-center gap-2 text-sm text-text-secondary">
                    <span className="text-primary">✓</span> {item}
                  </li>
                ))}
              </ul>
            </motion.div>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 px-6 relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-r from-primary/20 via-secondary/20 to-primary/20" />
        <div className="absolute inset-0 grid-pattern" />

        <div className="relative max-w-4xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            viewport={{ once: true }}
          >
            <h2 className="text-3xl md:text-4xl font-bold mb-6 text-text">
              Ready to <span className="gradient-text">Get Started</span>?
            </h2>
            <p className="text-text-secondary mb-10 max-w-xl mx-auto">
              Start streaming your Android screen to any browser in minutes
            </p>
            <Link to="/login">
              <Button size="lg" className="group">
                Start Streaming Free
                <span className="ml-2 group-hover:translate-x-1 transition-transform">→</span>
              </Button>
            </Link>
          </motion.div>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-12 px-6 border-t border-border">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
              <span className="text-white text-sm font-bold">▶</span>
            </div>
            <span className="font-semibold text-text">LiveCast</span>
          </div>
          <p className="text-text-muted text-sm">
            © {new Date().getFullYear()} LiveCast. All rights reserved.
          </p>
        </div>
      </footer>
    </div>
  );
}

