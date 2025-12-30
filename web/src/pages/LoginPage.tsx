import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import Button from '../components/Button';
import ThemeToggle from '../components/ThemeToggle';

export default function LoginPage() {
  const { signInWithGoogle, isLoading, error, clearError } = useAuth();
  const navigate = useNavigate();

  const handleGoogleLogin = async () => {
    try {
      clearError();
      await signInWithGoogle();
      navigate('/stage');
    } catch {
      // Error is handled by context
    }
  };

  return (
    <div className="min-h-screen flex bg-background transition-colors duration-300">
      {/* Left Panel - Login */}
      <div className="flex-1 flex items-center justify-center p-8 relative">
        <div className="absolute inset-0 grid-pattern" />
        <div className="absolute top-1/4 right-1/4 w-96 h-96 bg-primary/10 rounded-full blur-3xl" />
        
        {/* Theme Toggle - Fixed Position */}
        <div className="absolute top-6 right-6">
          <ThemeToggle />
        </div>

        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5 }}
          className="relative w-full max-w-md"
        >
          <Link to="/" className="inline-flex items-center gap-2 mb-8 text-text-secondary hover:text-text transition-colors">
            <span>←</span>
            <span>Back to home</span>
          </Link>

          <div className="mb-8">
            <h1 className="text-3xl font-bold mb-2 text-text">Welcome to LiveCast</h1>
            <p className="text-text-secondary">
              Sign in with your Google account to start viewing broadcasts
            </p>
          </div>

          {error && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="p-4 mb-6 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm"
            >
              {error}
            </motion.div>
          )}

          {/* Google Sign In Button */}
          <Button
            variant="primary"
            className="w-full flex items-center justify-center gap-3 py-4"
            onClick={handleGoogleLogin}
            isLoading={isLoading}
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path
                fill="currentColor"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="currentColor"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="currentColor"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
              />
              <path
                fill="currentColor"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
              />
            </svg>
            Continue with Google
          </Button>

          <p className="text-center mt-6 text-text-muted text-sm">
            By signing in, you agree to our Terms of Service and Privacy Policy
          </p>
        </motion.div>
      </div>

      {/* Right Panel - Visual */}
      <div className="hidden lg:flex flex-1 relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/20 via-background to-secondary/20" />
        <div className="absolute inset-0 grid-pattern" />
        <div className="absolute top-1/3 left-1/3 w-96 h-96 bg-primary/30 rounded-full blur-3xl" />
        <div className="absolute bottom-1/3 right-1/3 w-96 h-96 bg-secondary/30 rounded-full blur-3xl" />

        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="relative flex items-center justify-center p-12 w-full"
        >
          <div className="max-w-md text-center">
            <div className="w-24 h-24 mx-auto mb-8 rounded-2xl bg-gradient-to-br from-primary to-secondary flex items-center justify-center glow">
              <span className="text-5xl">📺</span>
            </div>
            <h2 className="text-3xl font-bold mb-4 text-text">Stream. View. Control.</h2>
            <p className="text-text-secondary leading-relaxed">
              Sign in to view live broadcasts from Android devices. Control screens remotely with
              touch gestures, navigate apps, and experience ultra-low latency WebRTC streaming.
            </p>

            <div className="mt-12 flex justify-center gap-8">
              <div className="text-center">
                <div className="text-3xl font-bold gradient-text">1080p</div>
                <div className="text-sm text-text-muted mt-1">HD Quality</div>
              </div>
              <div className="text-center">
                <div className="text-3xl font-bold gradient-text">&lt;50ms</div>
                <div className="text-sm text-text-muted mt-1">Latency</div>
              </div>
              <div className="text-center">
                <div className="text-3xl font-bold gradient-text">P2P</div>
                <div className="text-sm text-text-muted mt-1">Connection</div>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  );
}

