use std::time::{SystemTime, UNIX_EPOCH};
use btleplug::api::{Central, CentralEvent, Manager as _, ScanFilter};
use btleplug::platform::Manager;
use futures::stream::StreamExt;
use crate::beacon::AppleBeaconParser;
use crate::media::{MediaAction, MediaController};
use crate::state::SharedState;

pub async fn run_bluetooth_scanner(state: SharedState) {
    println!("[AnyPods Desktop] Initializing Cross-Platform Bluetooth LE Engine...");

    let manager = match Manager::new().await {
        Ok(m) => m,
        Err(e) => {
            eprintln!("[AnyPods Desktop] Error initializing BLE manager: {:?}", e);
            return;
        }
    };

    let adapters = match manager.adapters().await {
        Ok(a) => a,
        Err(e) => {
            eprintln!("[AnyPods Desktop] Error fetching BLE adapters: {:?}", e);
            return;
        }
    };

    if adapters.is_empty() {
        eprintln!("[AnyPods Desktop] No Bluetooth adapter found on this system.");
        return;
    }

    let central = &adapters[0];
    println!("[AnyPods Desktop] Using Bluetooth adapter: {:?}", central.adapter_info().await);

    let mut events = match central.events().await {
        Ok(e) => e,
        Err(e) => {
            eprintln!("[AnyPods Desktop] Error subscribing to BLE events: {:?}", e);
            return;
        }
    };

    if let Err(e) = central.start_scan(ScanFilter::default()).await {
        eprintln!("[AnyPods Desktop] Error starting BLE scan: {:?}", e);
        return;
    }

    println!("[AnyPods Desktop] Listening for Apple AirPods proximity beacons...");

    while let Some(event) = events.next().await {
        if let CentralEvent::ManufacturerDataAdvertisement {
            manufacturer_data, ..
        } = event
        {
            if let Some(data) = manufacturer_data.get(&AppleBeaconParser::APPLE_MANUFACTURER_ID) {
                if let Some(beacon) = AppleBeaconParser::parse(data) {
                    let now = SystemTime::now()
                        .duration_since(UNIX_EPOCH)
                        .unwrap_or_default()
                        .as_secs();

                    let mut s = state.lock().unwrap();
                    s.is_connected = true;
                    s.name = beacon.model_name.clone();
                    s.model_id = beacon.model_id.clone();
                    s.left_battery = beacon.left_battery;
                    s.right_battery = beacon.right_battery;
                    s.case_battery = beacon.case_battery;
                    s.is_left_charging = beacon.is_left_charging;
                    s.is_right_charging = beacon.is_right_charging;
                    s.is_case_charging = beacon.is_case_charging;
                    s.is_left_in_ear = beacon.is_left_in_ear;
                    s.is_right_in_ear = beacon.is_right_in_ear;
                    s.last_seen_time = now;

                    // In-Ear Detection & Smart Media Automation
                    if s.auto_pause_enabled {
                        let is_now_in_ear = beacon.is_left_in_ear && beacon.is_right_in_ear;
                        let was_in_ear = s.was_in_ear;

                        if was_in_ear && !is_now_in_ear {
                            // Earbud removed -> Pause media
                            s.was_paused_by_us = true;
                            s.was_in_ear = false;
                            println!("[AnyPods Desktop] Earbud removed from ear -> Auto-Pausing media");
                            MediaController::trigger(MediaAction::Pause);
                        } else if !was_in_ear && is_now_in_ear {
                            // Earbud replaced -> Resume media
                            if s.was_paused_by_us {
                                s.was_paused_by_us = false;
                                println!("[AnyPods Desktop] Earbud inserted in ear -> Auto-Resuming media");
                                MediaController::trigger(MediaAction::Play);
                            }
                            s.was_in_ear = true;
                        }
                    }
                }
            }
        }
    }
}
