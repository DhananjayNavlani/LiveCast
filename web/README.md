# LiveCast Web

A modern React web application for viewing and controlling LiveCast streams from Android devices.

## Features

- 🔐 **Firebase Authentication** - Email/password and anonymous login
- 📺 **Real-time Streaming** - View Android device screens via WebRTC
- 👆 **Remote Control** - Touch gestures and navigation controls
- 🎨 **Modern UI** - Clean, responsive design inspired by query.gg
- ⚡ **Fast** - Built with Vite and React 18

## Tech Stack

- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool
- **Tailwind CSS** - Styling
- **Framer Motion** - Animations
- **Firebase** - Authentication & Firestore signaling
- **React Router** - Navigation

## Getting Started

### Prerequisites

- Node.js 18+
- npm or yarn

### Installation

```bash
cd web
npm install
```

### Configuration

1. Copy the environment example file:
   ```bash
   cp .env.example .env.local
   ```

2. Fill in your Firebase configuration from the [Firebase Console](https://console.firebase.google.com/):
   - Go to Project Settings > General > Your apps > Web app
   - Copy the config values to `.env.local`

### Development

```bash
npm run dev
```

The app will be available at http://localhost:3000

### Build

```bash
npm run build
```

The build output will be in the `dist` folder.

## Project Structure

```
web/
├── public/              # Static assets
├── src/
│   ├── components/      # Reusable UI components
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── FeatureCard.tsx
│   │   └── ProtectedRoute.tsx
│   ├── config/          # Configuration files
│   │   └── firebase.ts
│   ├── context/         # React Context providers
│   │   └── AuthContext.tsx
│   ├── pages/           # Page components
│   │   ├── LandingPage.tsx
│   │   ├── FeaturesPage.tsx
│   │   ├── LoginPage.tsx
│   │   └── StagePage.tsx
│   ├── App.tsx          # App component with routing
│   ├── main.tsx         # Entry point
│   └── index.css        # Global styles
├── index.html           # HTML template
├── package.json
├── tailwind.config.js
├── tsconfig.json
└── vite.config.ts
```

## Deployment

This project is configured for Vercel deployment. The `vercel.json` in the root directory handles the build and deployment.

### Manual Deployment

1. Build the project:
   ```bash
   npm run build
   ```

2. Deploy the `dist` folder to your hosting provider.

## WebRTC Integration

The Stage page now includes full WebRTC support for connecting to Android devices:

### How It Works

1. **Signaling**: Uses Firebase Firestore for WebRTC signaling (offer/answer/ICE candidates)
2. **Connection**: Establishes peer-to-peer WebRTC connection between web and Android
3. **Streaming**: Receives video stream from Android's screen capture
4. **Control**: Sends touch events and navigation commands via DataChannel

### Connection Modes

1. **Connect to Android** - Web sends offer, waits for Android broadcaster to answer
2. **Wait for Broadcast** - Web listens for offers from Android viewers

### Data Channel Commands

Touch events are sent as: `"x y endX endY gestureType"` (space-separated)
- GestureType: `TAP`, `DOUBLE_TAP`, `LONG_PRESS`, `SWIPE_UP`, etc.

Navigation commands are sent as single words:
- `Home` - Go to home screen
- `GoBack` - Go back
- `GoToRecent` - Show recent apps
- `UnlockDevice` - Unlock/power button

### Files

- `src/services/SignalingClient.ts` - Firebase Firestore signaling
- `src/services/WebRTCClient.ts` - WebRTC peer connection management
- `src/pages/StagePage.tsx` - UI with video player and controls

## License

This project is part of the LiveCast application.

