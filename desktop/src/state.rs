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
}

impl Default for PodState {
    fn default() -> Self {
        Self {
            name: "AirPods Pro (2nd Gen)".to_string(),
            model_id: "airpods_pro_2".to_string(),
            chip_info: "Apple H2 Chip • AAC-ELD • 48 kHz Studio Stereo".to_string(),
            is_connected: true,
            left_battery: 88,
            right_battery: 92,
            case_battery: 76,
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
        }
    }
}

pub type SharedState = Arc<Mutex<PodState>>;
