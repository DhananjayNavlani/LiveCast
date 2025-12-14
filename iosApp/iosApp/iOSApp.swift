
import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

    init() {
        // Initialize Koin for dependency injection
        MainViewControllerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
