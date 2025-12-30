
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import Button from '../components/Button';

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-background">
      {/* Minimal Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 glass">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
              <span className="text-white text-sm font-bold">▶</span>
            </div>
            <span className="font-bold text-lg text-text">LiveCast</span>
          </Link>
          <div className="flex items-center gap-4">
            <Link to="/login">
              <Button variant="primary" size="sm">Get Started</Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero Section - Minimal & Bold */}
      <section className="relative min-h-screen flex items-center justify-center px-4 sm:px-6 lg:px-8 overflow-hidden">
        {/* Subtle background effects */}
        <div className="absolute inset-0 grid-pattern opacity-50" />
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary/10 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-secondary/10 rounded-full blur-3xl" />

        <div className="relative max-w-6xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="space-y-8"
          >
            {/* Large, Minimal Heading */}
            <h1 className="text-7xl md:text-8xl lg:text-9xl font-bold tracking-tight">
              <span className="block text-text mb-2">Stream</span>
              <span className="gradient-text">Anywhere</span>
            </h1>

            {/* Single line description */}
            <p className="text-xl md:text-2xl text-text-secondary max-w-2xl mx-auto">
              Real-time screen sharing powered by WebRTC
            </p>

            {/* CTA */}
            <div className="pt-8">
              <Link to="/login">
                <Button size="lg" className="group text-lg px-10 py-5">
                  Start Now
                  <span className="ml-2 group-hover:translate-x-1 transition-transform">→</span>
                </Button>
              </Link>
            </div>
          </motion.div>

          {/* Large Visual Preview */}
          <motion.div
            initial={{ opacity: 0, y: 40 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="mt-20"
          >
            <div className="relative rounded-3xl border border-border bg-surface p-4 shadow-glow-sm mx-auto max-w-4xl">
              {/* Browser chrome - minimal */}
              <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
                <div className="flex gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-text-muted/30" />
                  <div className="w-3 h-3 rounded-full bg-text-muted/30" />
                  <div className="w-3 h-3 rounded-full bg-text-muted/30" />
                </div>
              </div>

              {/* Screen preview - large and prominent */}
              <div className="aspect-video bg-background rounded-2xl flex items-center justify-center relative overflow-hidden mt-4">
                <div className="absolute inset-0 bg-gradient-to-br from-primary/10 to-secondary/10" />
                
                {/* Large icon */}
                <div className="text-center relative z-10">
                  <div className="w-32 h-32 mx-auto mb-8 rounded-3xl bg-gradient-to-br from-primary to-secondary flex items-center justify-center shadow-glow">
                    <span className="text-6xl">📱</span>
                  </div>
                  <p className="text-2xl font-semibold text-text mb-2">Your Screen Here</p>
                  <p className="text-text-secondary">Ultra-low latency • Touch control</p>
                </div>

                {/* Live indicator */}
                <div className="absolute top-6 right-6 flex items-center gap-2 bg-surface/90 backdrop-blur rounded-xl px-4 py-2 border border-border">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500"></span>
                  </span>
                  <span className="text-sm font-semibold text-text">LIVE</span>
                </div>
              </div>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Minimal Features - Just 3 key points */}
      <section className="py-24 sm:py-32 px-4 sm:px-6 lg:px-8 relative">
        <div className="absolute inset-0 bg-gradient-to-b from-background via-background-light/30 to-background" />
        
        <div className="relative max-w-5xl mx-auto">
          <div className="grid md:grid-cols-3 gap-12 text-center">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5 }}
              viewport={{ once: true }}
            >
              <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-primary/20 to-secondary/20 flex items-center justify-center">
                <span className="text-5xl">⚡</span>
              </div>
              <h3 className="text-2xl font-bold text-text mb-3">Instant</h3>
              <p className="text-text-secondary">Sub-50ms latency</p>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.1 }}
              viewport={{ once: true }}
            >
              <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-primary/20 to-secondary/20 flex items-center justify-center">
                <span className="text-5xl">👆</span>
              </div>
              <h3 className="text-2xl font-bold text-text mb-3">Control</h3>
              <p className="text-text-secondary">Touch anywhere</p>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.2 }}
              viewport={{ once: true }}
            >
              <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-primary/20 to-secondary/20 flex items-center justify-center">
                <span className="text-5xl">🔒</span>
              </div>
              <h3 className="text-2xl font-bold text-text mb-3">Secure</h3>
              <p className="text-text-secondary">End-to-end encrypted</p>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Minimal CTA */}
      <section className="py-24 sm:py-32 px-4 sm:px-6 lg:px-8 relative">
        <div className="absolute inset-0 bg-gradient-to-r from-primary/10 via-secondary/10 to-primary/10" />
        
        <div className="relative max-w-3xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            viewport={{ once: true }}
          >
            <h2 className="text-5xl md:text-6xl font-bold mb-8 text-text">
              Ready to <span className="gradient-text">Stream</span>?
            </h2>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link to="/login">
                <Button size="lg" className="text-lg px-10 py-5">
                  Get Started Free
                </Button>
              </Link>
              <Link to="/download">
                <Button variant="outline" size="lg" className="text-lg px-10 py-5">
                  Download App
                </Button>
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Minimal Footer */}
      <footer className="py-8 px-4 sm:px-6 lg:px-8 border-t border-border">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-lg bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
              <span className="text-white text-xs font-bold">▶</span>
            </div>
            <span className="font-semibold text-text text-sm">LiveCast</span>
          </div>
          <div className="flex items-center gap-6 text-sm">
            <Link to="/features" className="text-text-secondary hover:text-text transition-colors">
              Features
            </Link>
            <Link to="/download" className="text-text-secondary hover:text-text transition-colors">
              Download
            </Link>
          </div>
          <p className="text-text-muted text-sm">
            © {new Date().getFullYear()}
          </p>
        </div>
      </footer>
    </div>
  );
}
