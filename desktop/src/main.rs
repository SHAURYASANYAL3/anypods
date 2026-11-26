mod beacon;
mod bluetooth;
mod media;
mod state;
mod ui;

use std::sync::{Arc, Mutex};
use eframe::egui::Vec2;
use state::{PodState, SharedState};
use ui::AnyPodsDesktopApp;

fn main() -> Result<(), eframe::Error> {
    println!("=================================================");
    println!("           AnyPods Pro - Desktop v1.3.2           ");
    println!("   Universal AirPods for Windows, macOS & Linux  ");
    println!("=================================================");

    let shared_state: SharedState = Arc::new(Mutex::new(PodState::default()));

    // Spawn Bluetooth LE Proximity Scanner on background Tokio runtime
    let bt_state = shared_state.clone();
    std::thread::spawn(move || {
        let rt = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
            .expect("Failed to initialize Tokio runtime for Bluetooth scanning");

        rt.block_on(async {
            bluetooth::run_bluetooth_scanner(bt_state).await;
        });
    });

    // Native desktop window configuration (cross-platform for Windows, macOS, Linux)
    let native_options = eframe::NativeOptions {
        viewport: eframe::egui::ViewportBuilder::default()
            .with_inner_size(Vec2::new(460.0, 780.0))
            .with_min_inner_size(Vec2::new(400.0, 600.0))
            .with_title("AnyPods Pro")
            .with_active(true)
            .with_resizable(true),
        ..Default::default()
    };

    eframe::run_native(
        "AnyPods Pro",
        native_options,
        Box::new(|_cc| Box::new(AnyPodsDesktopApp::new(shared_state))),
    )
}
