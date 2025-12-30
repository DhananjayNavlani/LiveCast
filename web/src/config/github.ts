// GitHub Repository Configuration
// Update this with your actual GitHub repository details

export const GITHUB_CONFIG = {
  owner: 'dhananjaynavlani', // Replace with your GitHub username
  repo: 'LiveCast', // Replace with your repository name

  // API endpoints
  get repoUrl() {
    return `https://github.com/${this.owner}/${this.repo}`;
  },

  get releasesUrl() {
    return `${this.repoUrl}/releases`;
  },

  get latestReleaseUrl() {
    return `${this.repoUrl}/releases/latest`;
  },

  get apiReleasesUrl() {
    return `https://api.github.com/repos/${this.owner}/${this.repo}/releases`;
  },

  get apiLatestReleaseUrl() {
    return `https://api.github.com/repos/${this.owner}/${this.repo}/releases/latest`;
  }
};

// Asset name patterns for APKs
export const APK_PATTERNS = {
  debug: /livecast-.*-debug\.apk$/i,
  release: /livecast-.*-release\.apk$/i,
};

// Type definitions for GitHub Release API response
export interface GitHubAsset {
  name: string;
  browser_download_url: string;
  size: number;
  download_count: number;
  content_type: string;
}

export interface GitHubRelease {
  tag_name: string;
  name: string;
  body: string;
  published_at: string;
  html_url: string;
  assets: GitHubAsset[];
  prerelease: boolean;
  draft: boolean;
}

// Utility function to format file size
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Utility function to format date
export function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  });
}

