import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import Button from '../components/Button';
import ThemeToggle from '../components/ThemeToggle';
import {
  GITHUB_CONFIG,
  APK_PATTERNS,
  GitHubRelease,
  GitHubAsset,
  formatFileSize,
  formatDate
} from '../config/github';

interface ReleaseAsset extends GitHubAsset {
  type: 'debug' | 'release';
}

export default function DownloadPage() {
  const [release, setRelease] = useState<GitHubRelease | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchLatestRelease();
  }, []);

  const fetchLatestRelease = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch(GITHUB_CONFIG.apiLatestReleaseUrl);

      if (!response.ok) {
        if (response.status === 404) {
          throw new Error('No releases found. Check back soon!');
        }
        throw new Error('Failed to fetch release information');
      }

      const data: GitHubRelease = await response.json();
      setRelease(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  const getApkAssets = (): ReleaseAsset[] => {
    if (!release) return [];

    return release.assets
      .filter(asset => asset.name.endsWith('.apk'))
      .map(asset => ({
        ...asset,
        type: APK_PATTERNS.debug.test(asset.name) ? 'debug' : 'release'
      }))
      .sort((a, b) => (a.type === 'release' ? -1 : 1)); // Release first
  };

  const apkAssets = getApkAssets();

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
            <Link to="/features" className="text-text-secondary hover:text-text transition-colors hidden sm:block">
              Features
            </Link>
            <ThemeToggle />
            <Link to="/login">
              <Button variant="primary" size="sm">Get Started</Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <section className="relative pt-32 pb-20 px-6">
        {/* Background effects */}
        <div className="absolute inset-0 grid-pattern" />
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary/20 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-secondary/20 rounded-full blur-3xl" />

        <div className="relative max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="text-center mb-12"
          >
            <h1 className="text-4xl md:text-5xl font-bold mb-6">
              <span className="text-text">Download </span>
              <span className="gradient-text">LiveCast</span>
            </h1>
            <p className="text-xl text-text-secondary max-w-2xl mx-auto">
              Get the Android app to start streaming your screen
            </p>
          </motion.div>

          {/* Loading State */}
          {loading && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-center py-20"
            >
              <div className="inline-flex items-center gap-3">
                <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                <span className="text-text-secondary">Fetching latest release...</span>
              </div>
            </motion.div>
          )}

          {/* Error State */}
          {error && !loading && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="bg-surface border border-border rounded-2xl p-8 text-center"
            >
              <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-yellow-500/10 flex items-center justify-center">
                <span className="text-3xl">⚠️</span>
              </div>
              <h3 className="text-xl font-semibold text-text mb-2">No Releases Available</h3>
              <p className="text-text-secondary mb-6">{error}</p>
              <a
                href={GITHUB_CONFIG.releasesUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Button variant="outline">
                  View GitHub Releases
                  <span className="ml-2">↗</span>
                </Button>
              </a>
            </motion.div>
          )}

          {/* Release Content */}
          {release && !loading && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
            >
              {/* Version Badge */}
              <div className="text-center mb-8">
                <div className="inline-flex items-center gap-2 bg-surface border border-border rounded-full px-4 py-2">
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                  </span>
                  <span className="text-sm text-text-secondary">
                    Latest Version: <span className="text-text font-semibold">{release.tag_name}</span>
                  </span>
                </div>
                <p className="text-text-muted text-sm mt-2">
                  Released on {formatDate(release.published_at)}
                </p>
              </div>

              {/* Download Cards */}
              <div className="grid md:grid-cols-2 gap-6 mb-12">
                {apkAssets.length > 0 ? (
                  apkAssets.map((asset, index) => (
                    <motion.div
                      key={asset.name}
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.3 + index * 0.1 }}
                      className={`relative bg-surface rounded-2xl p-6 border ${
                        asset.type === 'release'
                          ? 'border-primary/50 shadow-glow-sm'
                          : 'border-border'
                      }`}
                    >
                      {asset.type === 'release' && (
                        <div className="absolute -top-3 left-6">
                          <span className="bg-gradient-to-r from-primary to-secondary text-white text-xs font-semibold px-3 py-1 rounded-full">
                            Recommended
                          </span>
                        </div>
                      )}

                      <div className="flex items-start gap-4">
                        <div className={`w-14 h-14 rounded-xl flex items-center justify-center ${
                          asset.type === 'release'
                            ? 'bg-gradient-to-br from-primary to-secondary'
                            : 'bg-surface-light border border-border'
                        }`}>
                          <span className="text-2xl">
                            {asset.type === 'release' ? '📦' : '🔧'}
                          </span>
                        </div>

                        <div className="flex-1">
                          <h3 className="font-semibold text-lg text-text mb-1">
                            {asset.type === 'release' ? 'Release Build' : 'Debug Build'}
                          </h3>
                          <p className="text-text-secondary text-sm mb-3">
                            {asset.type === 'release'
                              ? 'Signed & optimized for production use'
                              : 'For testing and development'}
                          </p>

                          <div className="flex items-center gap-4 text-xs text-text-muted mb-4">
                            <span>{formatFileSize(asset.size)}</span>
                            <span>•</span>
                            <span>{asset.download_count} downloads</span>
                          </div>

                          <a
                            href={asset.browser_download_url}
                            download
                          >
                            <Button
                              variant={asset.type === 'release' ? 'primary' : 'outline'}
                              className="w-full"
                            >
                              <span className="mr-2">⬇️</span>
                              Download APK
                            </Button>
                          </a>
                        </div>
                      </div>
                    </motion.div>
                  ))
                ) : (
                  <div className="col-span-2 text-center py-8 text-text-secondary">
                    No APK files found in this release
                  </div>
                )}
              </div>

              {/* Release Notes */}
              {release.body && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.5 }}
                  className="bg-surface border border-border rounded-2xl p-6"
                >
                  <h3 className="font-semibold text-lg text-text mb-4 flex items-center gap-2">
                    <span>📝</span>
                    Release Notes
                  </h3>
                  <div className="prose prose-invert prose-sm max-w-none text-text-secondary whitespace-pre-wrap">
                    {release.body}
                  </div>
                </motion.div>
              )}

              {/* All Releases Link */}
              <div className="text-center mt-8">
                <a
                  href={GITHUB_CONFIG.releasesUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 text-primary hover:text-primary-light transition-colors"
                >
                  <span>View all releases on GitHub</span>
                  <span>↗</span>
                </a>
              </div>
            </motion.div>
          )}

          {/* Installation Instructions */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.6 }}
            className="mt-16"
          >
            <h2 className="text-2xl font-bold text-text text-center mb-8">
              How to Install
            </h2>

            <div className="grid md:grid-cols-3 gap-6">
              {[
                {
                  step: '1',
                  title: 'Download APK',
                  desc: 'Download the release APK file to your Android device',
                  icon: '⬇️'
                },
                {
                  step: '2',
                  title: 'Enable Install',
                  desc: 'Allow installation from unknown sources in your device settings',
                  icon: '⚙️'
                },
                {
                  step: '3',
                  title: 'Install & Open',
                  desc: 'Tap the downloaded file to install and open LiveCast',
                  icon: '✅'
                }
              ].map((item, index) => (
                <motion.div
                  key={item.step}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.7 + index * 0.1 }}
                  className="bg-surface border border-border rounded-xl p-6 text-center"
                >
                  <div className="w-12 h-12 mx-auto mb-4 rounded-xl bg-gradient-to-br from-primary/20 to-secondary/20 flex items-center justify-center">
                    <span className="text-2xl">{item.icon}</span>
                  </div>
                  <div className="text-xs text-primary font-semibold mb-2">STEP {item.step}</div>
                  <h3 className="font-semibold text-text mb-2">{item.title}</h3>
                  <p className="text-sm text-text-secondary">{item.desc}</p>
                </motion.div>
              ))}
            </div>
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

