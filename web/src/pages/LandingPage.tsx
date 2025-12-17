import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import Button from '../components/Button';
import FeatureCard from '../components/FeatureCard';

export default function LandingPage() {
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
          <div className="flex items-center gap-6">
            <Link to="/features" className="text-text-secondary hover:text-text transition-colors hidden sm:block">
              Features
            </Link>
            <Link to="/login">
              <Button variant="primary" size="sm">Get Started</Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="relative pt-32 pb-20 px-6 overflow-hidden">
        {/* Background effects */}
        <div className="absolute inset-0 grid-pattern" />
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary/20 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-secondary/20 rounded-full blur-3xl" />

        <div className="relative max-w-5xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
          >
            {/* Badge */}
            <div className="inline-flex items-center gap-2 bg-surface border border-border rounded-full px-4 py-2 mb-8">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
              </span>
              <span className="text-sm text-text-secondary">Powered by WebRTC</span>
            </div>

            {/* Main heading */}
            <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold mb-6 leading-tight tracking-tight">
              <span className="text-text">Real-Time Screen</span>
              <br />
              <span className="gradient-text">Sharing & Control</span>
            </h1>

            <p className="text-xl text-text-secondary mb-10 max-w-2xl mx-auto leading-relaxed">
              Stream your Android device screen to any browser with ultra-low latency.
              Control remotely with touch gestures — all in real-time.
            </p>

            {/* CTA Buttons */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link to="/login">
                <Button size="lg" className="group">
                  Start Streaming
                  <span className="ml-2 group-hover:translate-x-1 transition-transform">→</span>
                </Button>
              </Link>
              <Link to="/features">
                <Button variant="outline" size="lg">
                  Explore Features
                </Button>
              </Link>
            </div>

            {/* Stats */}
            <div className="flex items-center justify-center gap-8 md:gap-16 mt-16">
              {[
                { value: '1080p', label: 'HD Quality' },
                { value: '<50ms', label: 'Latency' },
                { value: 'P2P', label: 'Connection' },
              ].map((stat, i) => (
                <motion.div
                  key={stat.label}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.5 + i * 0.1 }}
                  className="text-center"
                >
                  <div className="text-2xl md:text-3xl font-bold gradient-text">{stat.value}</div>
                  <div className="text-sm text-text-muted mt-1">{stat.label}</div>
                </motion.div>
              ))}
            </div>
          </motion.div>

          {/* Hero Visual */}
          <motion.div
            initial={{ opacity: 0, y: 40 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="mt-20 relative"
          >
            <div className="relative rounded-2xl border border-border bg-surface p-2 glow">
              {/* Browser chrome */}
              <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
                <div className="flex gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-red-500/80" />
                  <div className="w-3 h-3 rounded-full bg-yellow-500/80" />
                  <div className="w-3 h-3 rounded-full bg-green-500/80" />
                </div>
                <div className="flex-1 mx-4">
                  <div className="bg-background rounded-lg px-4 py-1.5 text-sm text-text-muted text-center">
                    livecast.app/stage
                  </div>
                </div>
              </div>

              {/* Screen preview */}
              <div className="aspect-video bg-background rounded-lg flex items-center justify-center relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-secondary/5" />
                <div className="text-center relative z-10">
                  <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-surface border border-border flex items-center justify-center">
                    <span className="text-4xl">📱</span>
                  </div>
                  <p className="text-text-secondary">Your Android screen streams here</p>
                  <p className="text-text-muted text-sm mt-2">Touch to control • Real-time response</p>
                </div>

                {/* Animated elements */}
                <div className="absolute top-4 right-4 flex items-center gap-2 bg-surface/80 backdrop-blur rounded-lg px-3 py-1.5 border border-border">
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-red-500"></span>
                  </span>
                  <span className="text-xs text-text-secondary">LIVE</span>
                </div>
              </div>
            </div>

            {/* Floating cards */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.8 }}
              className="absolute -left-4 md:-left-12 top-1/4 animate-float hidden md:block"
            >
              <div className="bg-surface rounded-xl p-4 border border-border shadow-glow-sm">
                <span className="text-2xl">⚡</span>
                <p className="text-xs text-text-secondary mt-2">Ultra Fast</p>
              </div>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 1 }}
              className="absolute -right-4 md:-right-12 top-1/3 animate-float hidden md:block"
              style={{ animationDelay: '1s' }}
            >
              <div className="bg-surface rounded-xl p-4 border border-border shadow-glow-sm">
                <span className="text-2xl">🔒</span>
                <p className="text-xs text-text-secondary mt-2">Encrypted</p>
              </div>
            </motion.div>
          </motion.div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-24 px-6 relative">
        <div className="absolute inset-0 bg-gradient-to-b from-background via-background-light/50 to-background" />

        <div className="relative max-w-6xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            viewport={{ once: true }}
            className="text-center mb-16"
          >
            <h2 className="text-3xl md:text-4xl font-bold mb-4 text-text">
              Why Choose <span className="gradient-text">LiveCast</span>?
            </h2>
            <p className="text-text-secondary max-w-2xl mx-auto">
              Everything you need for seamless screen sharing and remote device control
            </p>
          </motion.div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            <FeatureCard
              icon="⚡"
              title="Ultra-Low Latency"
              description="Real-time streaming powered by WebRTC technology with automatic quality adjustment."
              delay={0}
            />
            <FeatureCard
              icon="👆"
              title="Touch Control"
              description="Control the streaming device remotely with intuitive touch gestures."
              delay={0.1}
            />
            <FeatureCard
              icon="🌐"
              title="Cross-Platform"
              description="Works on any modern browser — Chrome, Firefox, Safari, Edge."
              delay={0.2}
            />
            <FeatureCard
              icon="🔒"
              title="Secure Connection"
              description="End-to-end encrypted peer-to-peer connection using WebRTC."
              delay={0.3}
            />
            <FeatureCard
              icon="📺"
              title="HD Quality"
              description="Up to 1080p video streaming with adaptive bitrate for smooth viewing."
              delay={0.4}
            />
            <FeatureCard
              icon="🚀"
              title="Easy Setup"
              description="No plugins required. Just sign in and start streaming instantly."
              delay={0.5}
            />
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="py-24 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            viewport={{ once: true }}
            className="text-center mb-16"
          >
            <h2 className="text-3xl md:text-4xl font-bold mb-4 text-text">
              Get Started in <span className="gradient-text">3 Steps</span>
            </h2>
            <p className="text-text-secondary">Simple setup, powerful results</p>
          </motion.div>

          <div className="space-y-6">
            {[
              { step: '01', title: 'Install the App', desc: 'Download LiveCast on your Android device from the Play Store', icon: '📱' },
              { step: '02', title: 'Start Broadcasting', desc: 'Open the app and tap the broadcast button to share your screen', icon: '📡' },
              { step: '03', title: 'View & Control', desc: 'Sign in on this website to view and control the stream remotely', icon: '🎮' },
            ].map((item, index) => (
              <motion.div
                key={item.step}
                initial={{ opacity: 0, x: -20 }}
                whileInView={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                viewport={{ once: true }}
                className="group relative flex items-center gap-6 p-6 bg-surface rounded-2xl border border-border hover:border-primary/50 transition-all duration-300 card-hover"
              >
                <div className="absolute inset-0 rounded-2xl bg-gradient-to-r from-primary/5 to-secondary/5 opacity-0 group-hover:opacity-100 transition-opacity" />

                <div className="relative flex-shrink-0 w-14 h-14 rounded-xl bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
                  <span className="text-white font-bold text-lg">{item.step}</span>
                </div>

                <div className="relative flex-1">
                  <h3 className="font-semibold text-lg mb-1 text-text flex items-center gap-2">
                    {item.title}
                    <span className="text-xl">{item.icon}</span>
                  </h3>
                  <p className="text-text-secondary">{item.desc}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
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
            <h2 className="text-3xl md:text-5xl font-bold mb-6 text-text">
              Ready to <span className="gradient-text">Start Streaming</span>?
            </h2>
            <p className="text-text-secondary mb-10 max-w-xl mx-auto text-lg">
              Join thousands of users who trust LiveCast for their screen sharing needs
            </p>
            <Link to="/login">
              <Button size="lg" className="group">
                Get Started Free
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

