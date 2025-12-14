
import SwiftUI
import ComposeApp
import FirebaseCore

@main
struct iOSApp: App {

    init() {
        // Initialize Firebase
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }

        // Initialize Koin for dependency injection
        MainViewControllerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
