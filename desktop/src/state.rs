use std::sync::{Arc, Mutex};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum NoiseMode {
    Off,
    NoiseCancellation,
    Transparency,
    Adaptive,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PodState {
    pub name: String,
    pub model_id: String,
    pub is_connected: bool,
    pub left_battery: i32,
    pub right_battery: i32,
    pub case_battery: i32,
    pub is_left_charging: bool,
    pub is_right_charging: bool,
    pub is_case_charging: bool,
    pub is_left_in_ear: bool,
    pub is_right_in_ear: bool,
    pub noise_mode: NoiseMode,
    pub auto_pause_enabled: bool,
    pub was_in_ear: bool,
    pub was_paused_by_us: bool,
    pub last_seen_time: u64,
}

impl Default for PodState {
    fn default() -> Self {
        Self {
            name: "AirPods Pro 2".to_string(),
            model_id: "airpods_pro_2".to_string(),
            is_connected: false,
            left_battery: 85,
            right_battery: 90,
            case_battery: 75,
            is_left_charging: false,
            is_right_charging: false,
            is_case_charging: false,
            is_left_in_ear: true,
            is_right_in_ear: true,
            noise_mode: NoiseMode::NoiseCancellation,
            auto_pause_enabled: true,
            was_in_ear: true,
            was_paused_by_us: false,
            last_seen_time: 0,
        }
    }
}

pub type SharedState = Arc<Mutex<PodState>>;
