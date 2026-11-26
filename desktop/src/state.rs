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
    HeadTracked,
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
    // 1. Device identity
    pub name: String,
    pub model_id: String,
    pub chip_info: String,
    pub mac_address: String,
    pub is_connected: bool,

    // 2. Battery Telemetry (Left, Case, Right)
    pub left_battery: i32,
    pub right_battery: i32,
    pub case_battery: i32,
    pub is_left_charging: bool,
    pub is_right_charging: bool,
    pub is_case_charging: bool,
    pub is_left_in_ear: bool,
    pub is_right_in_ear: bool,

    // 3. Noise Control & ANC Intensity
    pub noise_mode: NoiseMode,
    pub anc_intensity: AncIntensity,
    pub ambient_db: i32,

    // 4. Audio Intelligence (Adaptive, Conversation Awareness, Personalized Volume)
    pub adaptive_audio_enabled: bool,
    pub conversation_awareness_enabled: bool,
    pub personalized_volume_enabled: bool,

    // 5. 3D Spatial Audio & Headphone Accommodations
    pub spatial_audio: SpatialAudioMode,
    pub tone_profile: String,
    pub tone_intensity: String,
    pub eq_preset: EqPreset,
    pub lr_balance: f32,

    // 6. Voice Microphone Quality & Boost (User-requested for Trigger Pod)
    pub mic_boost_enabled: bool,
    pub mic_boost_level: f32,
    pub mic_hd_wideband: bool,
    pub mic_clarity_dsp: bool,
    pub mic_selection: String,

    // 7. Bluetooth Signal Boost & Range Extender
    pub signal_boost_enabled: bool,
    pub signal_tx_power_dbm: i32,
    pub signal_range_pct: i32,

    // 8. In-Ear Detection & Smart Media Automation
    pub auto_pause_enabled: bool,
    pub low_battery_alert_enabled: bool,
    pub was_in_ear: bool,
    pub was_paused_by_us: bool,

    // 9. Hearing Health Decibel Shield
    pub decibel_shield_enabled: bool,
    pub decibel_shield_limit: i32,

    // 10. Precision Find My Radar & Audio Siren
    pub radar_distance: f32,
    pub radar_rssi: i32,
    pub siren_active: bool,
    pub ringing_left: bool,
    pub ringing_right: bool,

    // 11. Modal Popup
    pub show_popup_modal: bool,

    // 12. Hardware Devices List
    pub connected_devices: Vec<String>,
    pub last_seen_time: u64,
}

impl Default for PodState {
    fn default() -> Self {
        Self {
            name: "TRIGGR Kraken X1".to_string(),
            model_id: "triggr_kraken_x1".to_string(),
            chip_info: "TRIGGR Gaming Series • Low Latency AAC/SBC • 48 kHz".to_string(),
            mac_address: "3B:4A:28:14:69:32".to_string(),
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
            ambient_db: 42,

            adaptive_audio_enabled: true,
            conversation_awareness_enabled: true,
            personalized_volume_enabled: true,

            spatial_audio: SpatialAudioMode::HeadTracked,
            tone_profile: "Balanced Tone".to_string(),
            tone_intensity: "Moderate".to_string(),
            eq_preset: EqPreset::HarmanTarget,
            lr_balance: 0.0,

            mic_boost_enabled: true,
            mic_boost_level: 1.8,
            mic_hd_wideband: true,
            mic_clarity_dsp: true,
            mic_selection: "Auto-Switch".to_string(),

            signal_boost_enabled: true,
            signal_tx_power_dbm: 10,
            signal_range_pct: 35,

            auto_pause_enabled: true,
            low_battery_alert_enabled: true,
            was_in_ear: true,
            was_paused_by_us: false,

            decibel_shield_enabled: true,
            decibel_shield_limit: 85,

            radar_distance: 0.8,
            radar_rssi: -54,
            siren_active: false,
            ringing_left: false,
            ringing_right: false,

            show_popup_modal: false,

            connected_devices: vec!["TRIGGR Kraken X1".to_string(), "Neuton:A1".to_string()],
            last_seen_time: 0,
        }
    }
}

pub type SharedState = Arc<Mutex<PodState>>;
