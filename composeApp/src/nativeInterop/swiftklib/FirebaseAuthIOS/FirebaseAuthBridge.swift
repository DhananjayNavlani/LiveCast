// filepath: /Users/dhananjay.navlani@grofers.com/AndroidLearning/LiveCast/composeApp/src/nativeInterop/swiftklib/FirebaseAuthIOS/FirebaseAuthBridge.swift

import Foundation
import FirebaseCore
import FirebaseAuth

/// Represents a Firebase authenticated user exposed to Kotlin/Native
@objc public class FirebaseUserData: NSObject {
    @objc public let uid: String
    @objc public let email: String?
    @objc public let displayName: String?
    @objc public let photoURL: String?
    @objc public let isAnonymous: Bool

    public init(uid: String, email: String?, displayName: String?, photoURL: String?, isAnonymous: Bool) {
        self.uid = uid
        self.email = email
        self.displayName = displayName
        self.photoURL = photoURL
        self.isAnonymous = isAnonymous
    }

    convenience init?(from user: User?) {
        guard let user = user else { return nil }
        self.init(
            uid: user.uid,
            email: user.email,
            displayName: user.displayName,
            photoURL: user.photoURL?.absoluteString,
            isAnonymous: user.isAnonymous
        )
    }
}

/// Result type for authentication operations
@objc public class FirebaseAuthResultData: NSObject {
    @objc public let success: Bool
    @objc public let user: FirebaseUserData?
    @objc public let errorMessage: String?

    private init(success: Bool, user: FirebaseUserData?, errorMessage: String?) {
        self.success = success
        self.user = user
        self.errorMessage = errorMessage
    }

    @objc public static func successResult(user: FirebaseUserData) -> FirebaseAuthResultData {
        return FirebaseAuthResultData(success: true, user: user, errorMessage: nil)
    }

    @objc public static func failureResult(errorMessage: String) -> FirebaseAuthResultData {
        return FirebaseAuthResultData(success: false, user: nil, errorMessage: errorMessage)
    }
}

/// Firebase Auth wrapper that can be called from Kotlin/Native
@objc public class FirebaseAuthBridge: NSObject {

    @objc public static let shared = FirebaseAuthBridge()

    private var authStateHandle: AuthStateDidChangeListenerHandle?
    private var authStateCallback: ((FirebaseUserData?) -> Void)?

    private override init() {
        super.init()
    }

    /// Configure Firebase - must be called before any other method
    @objc public func configure() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
    }

    /// Get the currently signed-in user
    @objc public func getCurrentUser() -> FirebaseUserData? {
        return FirebaseUserData(from: Auth.auth().currentUser)
    }

    /// Check if a user is currently signed in
    @objc public func isSignedIn() -> Bool {
        return Auth.auth().currentUser != nil
    }

    /// Sign in with email and password
    @objc public func signInWithEmail(
        _ email: String,
        password: String,
        completion: @escaping (FirebaseAuthResultData) -> Void
    ) {
        Auth.auth().signIn(withEmail: email, password: password) { authResult, error in
            if let error = error {
                completion(.failureResult(errorMessage: self.mapFirebaseError(error)))
                return
            }

            guard let user = authResult?.user else {
                completion(.failureResult(errorMessage: "Unknown error occurred"))
                return
            }

            if let userData = FirebaseUserData(from: user) {
                completion(.successResult(user: userData))
            } else {
                completion(.failureResult(errorMessage: "Failed to parse user data"))
            }
        }
    }

    /// Create a new user with email and password
    @objc public func createUserWithEmail(
        _ email: String,
        password: String,
        completion: @escaping (FirebaseAuthResultData) -> Void
    ) {
        Auth.auth().createUser(withEmail: email, password: password) { authResult, error in
            if let error = error {
                completion(.failureResult(errorMessage: self.mapFirebaseError(error)))
                return
            }

            guard let user = authResult?.user else {
                completion(.failureResult(errorMessage: "Unknown error occurred"))
                return
            }

            if let userData = FirebaseUserData(from: user) {
                completion(.successResult(user: userData))
            } else {
                completion(.failureResult(errorMessage: "Failed to parse user data"))
            }
        }
    }

    /// Sign in anonymously
    @objc public func signInAnonymously(completion: @escaping (FirebaseAuthResultData) -> Void) {
        Auth.auth().signInAnonymously { authResult, error in
            if let error = error {
                completion(.failureResult(errorMessage: self.mapFirebaseError(error)))
                return
            }

            guard let user = authResult?.user else {
                completion(.failureResult(errorMessage: "Unknown error occurred"))
                return
            }

            if let userData = FirebaseUserData(from: user) {
                completion(.successResult(user: userData))
            } else {
                completion(.failureResult(errorMessage: "Failed to parse user data"))
            }
        }
    }

    /// Sign out the current user
    @objc public func signOut() -> Bool {
        do {
            try Auth.auth().signOut()
            return true
        } catch {
            print("Error signing out: \(error.localizedDescription)")
            return false
        }
    }

    /// Send password reset email
    @objc public func sendPasswordResetEmail(
        _ email: String,
        completion: @escaping (FirebaseAuthResultData) -> Void
    ) {
        Auth.auth().sendPasswordReset(withEmail: email) { error in
            if let error = error {
                completion(.failureResult(errorMessage: self.mapFirebaseError(error)))
                return
            }

            // Return a dummy success result since password reset doesn't return a user
            let dummyUser = FirebaseUserData(
                uid: "",
                email: email,
                displayName: nil,
                photoURL: nil,
                isAnonymous: false
            )
            completion(.successResult(user: dummyUser))
        }
    }

    /// Add an auth state listener
    @objc public func addAuthStateListener(callback: @escaping (FirebaseUserData?) -> Void) {
        authStateCallback = callback
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            let userData = FirebaseUserData(from: user)
            self?.authStateCallback?(userData)
        }
    }

    /// Remove the auth state listener
    @objc public func removeAuthStateListener() {
        if let handle = authStateHandle {
            Auth.auth().removeStateDidChangeListener(handle)
            authStateHandle = nil
        }
        authStateCallback = nil
    }

    // MARK: - Private Helpers

    private func mapFirebaseError(_ error: Error) -> String {
        let nsError = error as NSError

        // Firebase Auth error codes
        switch nsError.code {
        case AuthErrorCode.invalidEmail.rawValue:
            return "Invalid email address"
        case AuthErrorCode.emailAlreadyInUse.rawValue:
            return "Email is already in use"
        case AuthErrorCode.weakPassword.rawValue:
            return "Password is too weak"
        case AuthErrorCode.wrongPassword.rawValue:
            return "Incorrect password"
        case AuthErrorCode.userNotFound.rawValue:
            return "No account found with this email"
        case AuthErrorCode.userDisabled.rawValue:
            return "This account has been disabled"
        case AuthErrorCode.networkError.rawValue:
            return "Network error. Please check your connection"
        case AuthErrorCode.tooManyRequests.rawValue:
            return "Too many attempts. Please try again later"
        case AuthErrorCode.operationNotAllowed.rawValue:
            return "This sign-in method is not enabled"
        default:
            return error.localizedDescription
        }
    }
}

