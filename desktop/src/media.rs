/// Cross-platform media playback control (Windows, macOS, Linux).
/// Used by In-Ear Detection to auto-pause on earbud removal and auto-resume on insertion.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[allow(dead_code)]
pub enum MediaAction {
    PlayPause,
    Pause,
    Play,
}

pub struct MediaController;

impl MediaController {
    pub fn trigger(action: MediaAction) {
        #[cfg(target_os = "windows")]
        {
            Self::trigger_windows(action);
        }

        #[cfg(target_os = "macos")]
        {
            Self::trigger_macos(action);
        }

        #[cfg(target_os = "linux")]
        {
            Self::trigger_linux(action);
        }
    }

    #[cfg(target_os = "windows")]
    fn trigger_windows(action: MediaAction) {
        use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
            keybd_event, KEYEVENTF_KEYUP, VK_MEDIA_PLAY_PAUSE,
        };

        let vk = match action {
            MediaAction::PlayPause | MediaAction::Play => VK_MEDIA_PLAY_PAUSE,
            MediaAction::Pause => VK_MEDIA_PLAY_PAUSE,
        };

        unsafe {
            // Key Down
            keybd_event(vk as u8, 0, 0, 0);
            // Key Up
            keybd_event(vk as u8, 0, KEYEVENTF_KEYUP, 0);
        }
    }

    #[cfg(target_os = "macos")]
    fn trigger_macos(action: MediaAction) {
        let script = match action {
            MediaAction::PlayPause => {
                "tell application \"System Events\" to key code 16 using {command down}"
            }
            MediaAction::Pause => {
                "tell application \"Music\" to pause"
            }
            MediaAction::Play => {
                "tell application \"Music\" to play"
            }
        };

        let _ = std::process::Command::new("osascript")
            .arg("-e")
            .arg(script)
            .output();
    }

    #[cfg(target_os = "linux")]
    fn trigger_linux(action: MediaAction) {
        let arg = match action {
            MediaAction::PlayPause => "play-pause",
            MediaAction::Pause => "pause",
            MediaAction::Play => "play",
        };

        let _ = std::process::Command::new("playerctl")
            .arg(arg)
            .output();
    }
}
