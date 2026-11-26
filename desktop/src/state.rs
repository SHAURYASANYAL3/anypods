use std::sync::{Arc, Mutex};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum NoiseMode {
    Off,
    NoiseCancellation,
    Transparency,
    Adaptive,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AncIntensity {
    Ultra,
    Balanced,
    Comfort,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SpatialAudioMode {
    Off,
    Fixed,
    Spatial3D,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum EqPreset {
    HarmanTarget,
    BassBoost,
    VocalRange,
    Brightness,
    StudioFlat,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PodState {
    pub name: String,
    pub model_id: String,
    pub chip_info: String,
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
    pub anc_intensity: AncIntensity,
    pub spatial_audio: SpatialAudioMode,
    pub eq_preset: EqPreset,
    pub lr_balance: f32,
    pub auto_pause_enabled: bool,
    pub low_battery_alert_enabled: bool,
    pub ambient_db: i32,
    pub radar_distance: f32,
    pub radar_rssi: i32,
    pub siren_active: bool,
    pub was_in_ear: bool,
    pub was_paused_by_us: bool,
    pub last_seen_time: u64,

    // Microphone Voice Quality & Clarity Boost (User Requested for Trigger Pod)
    pub mic_boost_enabled: bool,
    pub mic_boost_level: f32, // 1.0 to 2.5
    pub mic_hd_wideband: bool,
    pub mic_clarity_dsp: bool,
    pub mic_selection: String, // "Auto-Switch", "Always Left", "Always Right"

    // Bluetooth Signal Boost & Extended Wireless Range
    pub signal_boost_enabled: bool,
    pub signal_tx_power_dbm: i32,
    pub signal_range_pct: i32,

    // Live Detected Connected Hardware Devices
    pub connected_devices: Vec<String>,
}

impl Default for PodState {
    fn default() -> Self {
        Self {
            name: "TRIGGR Kraken X1".to_string(),
            model_id: "triggr_kraken_x1".to_string(),
            chip_info: "TRIGGR Gaming Series • Low Latency AAC/SBC • 48 kHz".to_string(),
            is_connected: true,
            left_battery: 90,
            right_battery: 90,
            case_battery: 80,
            is_left_charging: false,
            is_right_charging: false,
            is_case_charging: false,
            is_left_in_ear: true,
            is_right_in_ear: true,
            noise_mode: NoiseMode::NoiseCancellation,
            anc_intensity: AncIntensity::Ultra,
            spatial_audio: SpatialAudioMode::Spatial3D,
            eq_preset: EqPreset::HarmanTarget,
            lr_balance: 0.0,
            auto_pause_enabled: true,
            low_battery_alert_enabled: true,
            ambient_db: 42,
            radar_distance: 0.8,
            radar_rssi: -54,
            siren_active: false,
            was_in_ear: true,
            was_paused_by_us: false,
            last_seen_time: 0,

            mic_boost_enabled: true,
            mic_boost_level: 1.8,
            mic_hd_wideband: true,
            mic_clarity_dsp: true,
            mic_selection: "Auto-Switch".to_string(),

            signal_boost_enabled: true,
            signal_tx_power_dbm: 10,
            signal_range_pct: 35,

            connected_devices: vec!["TRIGGR Kraken X1".to_string(), "Neuton:A1".to_string()],
        }
    }
}

pub type SharedState = Arc<Mutex<PodState>>;
