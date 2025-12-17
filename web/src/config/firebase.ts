import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { getAnalytics, isSupported } from 'firebase/analytics';

// Your web app's Firebase configuration
const firebaseConfig = {
  apiKey: "AIzaSyAmbne_xzut7TU92xwRTdeF8IV3H7a28dI",
  authDomain: "livecast-b73ad.firebaseapp.com",
  projectId: "livecast-b73ad",
  storageBucket: "livecast-b73ad.firebasestorage.app",
  messagingSenderId: "873835613576",
  appId: "1:873835613576:web:dc9fdd95233f2233723e3d",
  measurementId: "G-10NDJVHGXJ"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);

// Initialize services
export const auth = getAuth(app);
export const firestore = getFirestore(app);
export const googleProvider = new GoogleAuthProvider();

// Initialize analytics only if supported (not in SSR/Node)
export const analytics = isSupported().then(yes => yes ? getAnalytics(app) : null);

export default app;

