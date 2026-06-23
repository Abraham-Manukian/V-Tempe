import SwiftUI
import UIKit
import AppIos

struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        FullScreenComposeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private class FullScreenComposeViewController: UIViewController {
    private lazy var composeVC = IosEntryPointKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(composeVC)
        view.addSubview(composeVC.view)
        composeVC.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            composeVC.view.topAnchor.constraint(equalTo: view.topAnchor),
            composeVC.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            composeVC.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            composeVC.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        composeVC.didMove(toParent: self)
        let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
        tap.cancelsTouchesInView = false
        tap.delegate = self
        view.addGestureRecognizer(tap)
    }

    @objc private func dismissKeyboard() {
        view.endEditing(true)
    }

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var childForStatusBarStyle: UIViewController? { nil }
}

extension FullScreenComposeViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        return !isInsideFirstResponder(touch.view)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        return true
    }
    private func isInsideFirstResponder(_ view: UIView?) -> Bool {
        guard let view = view else { return false }
        if view.isFirstResponder { return true }
        return view.subviews.contains { isInsideFirstResponder($0) }
    }
}

@main
struct VtempeApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView().ignoresSafeArea()
        }
    }
}
