use std::f32::consts::PI;

pub struct AudioPlayer;

impl AudioPlayer {
    /// Play raw WAV bytes in memory asynchronously
    pub fn play_wav_bytes(wav_bytes: &'static [u8], loop_audio: bool) {
        #[cfg(target_os = "windows")]
        {
            // SND_ASYNC = 0x0001, SND_NODEFAULT = 0x0002, SND_MEMORY = 0x0004, SND_LOOP = 0x0008
            let mut flags: u32 = 0x0001 | 0x0002 | 0x0004;
            if loop_audio {
                flags |= 0x0008;
            }
            unsafe {
                PlaySoundA(wav_bytes.as_ptr(), std::ptr::null_mut(), flags);
            }
        }

        #[cfg(target_os = "macos")]
        {
            std::thread::spawn(move || {
                use std::io::Write;
                let tmp_path = std::env::temp_dir().join("anypods_sound.wav");
                if let Ok(mut f) = std::fs::File::create(&tmp_path) {
                    let _ = f.write_all(wav_bytes);
                    let _ = std::process::Command::new("afplay")
                        .arg(tmp_path.to_str().unwrap_or_default())
                        .status();
                }
            });
        }

        #[cfg(target_os = "linux")]
        {
            std::thread::spawn(move || {
                use std::io::Write;
                let tmp_path = std::env::temp_dir().join("anypods_sound.wav");
                if let Ok(mut f) = std::fs::File::create(&tmp_path) {
                    let _ = f.write_all(wav_bytes);
                    let _ = std::process::Command::new("paplay")
                        .arg(tmp_path.to_str().unwrap_or_default())
                        .status();
                }
            });
        }
    }

    /// Stop any playing sound immediately
    pub fn stop_sound() {
        #[cfg(target_os = "windows")]
        unsafe {
            PlaySoundA(std::ptr::null(), std::ptr::null_mut(), 0);
        }
    }

    /// Play Apple-style ANC suction swoop (720 Hz -> 130 Hz)
    pub fn play_anc_chime() {
        static ANC_WAV: once_cell_or_lazy::LazyWav = once_cell_or_lazy::LazyWav::new(generate_anc_swoop);
        Self::play_wav_bytes(ANC_WAV.get(), false);
    }

    /// Play Transparency dual chime (1050 Hz + 2100 Hz harmonic)
    pub fn play_transparency_chime() {
        static TRANSPARENCY_WAV: once_cell_or_lazy::LazyWav = once_cell_or_lazy::LazyWav::new(generate_transparency_chime);
        Self::play_wav_bytes(TRANSPARENCY_WAV.get(), false);
    }

    /// Play In-Ear Optical double ding (880 Hz -> 1760 Hz)
    pub fn play_in_ear_chime() {
        static IN_EAR_WAV: once_cell_or_lazy::LazyWav = once_cell_or_lazy::LazyWav::new(generate_in_ear_chime);
        Self::play_wav_bytes(IN_EAR_WAV.get(), false);
    }

    /// Start piercing dual-tone Find My Siren (3200 Hz / 4000 Hz) in loop
    pub fn start_siren() {
        static SIREN_WAV: once_cell_or_lazy::LazyWav = once_cell_or_lazy::LazyWav::new(generate_siren_wav);
        Self::play_wav_bytes(SIREN_WAV.get(), true);
    }

    /// Play L/R Balance test chirp panned to left/right
    pub fn play_balance_test(balance: f32) {
        let (left_gain, right_gain) = if balance < 0.0 {
            (1.0, 1.0 + balance)
        } else {
            (1.0 - balance, 1.0)
        };

        let sample_rate = 44100;
        let duration = 0.25;
        let total_samples = (sample_rate as f32 * duration) as usize;
        let mut left_samples = Vec::with_capacity(total_samples);
        let mut right_samples = Vec::with_capacity(total_samples);

        for i in 0..total_samples {
            let t = i as f32 / sample_rate as f32;
            let env = (1.0 - t / duration).powf(2.0);
            let s = (2.0 * PI * 880.0 * t).sin() * env * 0.7;
            let l = (s * left_gain * 32767.0) as i16;
            let r = (s * right_gain * 32767.0) as i16;
            left_samples.push(l);
            right_samples.push(r);
        }

        let wav = create_stereo_wav(&left_samples, &right_samples, sample_rate);
        let boxed: &'static [u8] = Box::leak(wav.into_boxed_slice());
        Self::play_wav_bytes(boxed, false);
    }
}

#[cfg(target_os = "windows")]
extern "system" {
    fn PlaySoundA(psz_sound: *const u8, hmod: *mut std::ffi::c_void, fdw_sound: u32) -> i32;
}

mod once_cell_or_lazy {
    use std::sync::OnceLock;

    pub struct LazyWav {
        generator: fn() -> Vec<u8>,
        wav: OnceLock<&'static [u8]>,
    }

    impl LazyWav {
        pub const fn new(generator: fn() -> Vec<u8>) -> Self {
            Self {
                generator,
                wav: OnceLock::new(),
            }
        }

        pub fn get(&self) -> &'static [u8] {
            *self.wav.get_or_init(|| {
                let bytes = (self.generator)();
                Box::leak(bytes.into_boxed_slice())
            })
        }
    }
}

// -----------------------------------------------------------------------------
// WAV Synthesis Helpers
// -----------------------------------------------------------------------------

fn create_mono_wav(samples: &[i16], sample_rate: u32) -> Vec<u8> {
    let data_len = (samples.len() * 2) as u32;
    let file_len = 36 + data_len;
    let mut wav = Vec::with_capacity(44 + samples.len() * 2);
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&file_len.to_le_bytes());
    wav.extend_from_slice(b"WAVEfmt ");
    wav.extend_from_slice(&16u32.to_le_bytes());
    wav.extend_from_slice(&1u16.to_le_bytes()); // PCM
    wav.extend_from_slice(&1u16.to_le_bytes()); // Mono
    wav.extend_from_slice(&sample_rate.to_le_bytes());
    wav.extend_from_slice(&(sample_rate * 2).to_le_bytes());
    wav.extend_from_slice(&2u16.to_le_bytes());
    wav.extend_from_slice(&16u16.to_le_bytes());
    wav.extend_from_slice(b"data");
    wav.extend_from_slice(&data_len.to_le_bytes());
    for s in samples {
        wav.extend_from_slice(&s.to_le_bytes());
    }
    wav
}

fn create_stereo_wav(left_samples: &[i16], right_samples: &[i16], sample_rate: u32) -> Vec<u8> {
    let total_samples = left_samples.len();
    let data_len = (total_samples * 4) as u32;
    let file_len = 36 + data_len;
    let mut wav = Vec::with_capacity(44 + total_samples * 4);
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&file_len.to_le_bytes());
    wav.extend_from_slice(b"WAVEfmt ");
    wav.extend_from_slice(&16u32.to_le_bytes());
    wav.extend_from_slice(&1u16.to_le_bytes()); // PCM
    wav.extend_from_slice(&2u16.to_le_bytes()); // Stereo
    wav.extend_from_slice(&sample_rate.to_le_bytes());
    wav.extend_from_slice(&(sample_rate * 4).to_le_bytes());
    wav.extend_from_slice(&4u16.to_le_bytes());
    wav.extend_from_slice(&16u16.to_le_bytes());
    wav.extend_from_slice(b"data");
    wav.extend_from_slice(&data_len.to_le_bytes());
    for i in 0..total_samples {
        wav.extend_from_slice(&left_samples[i].to_le_bytes());
        wav.extend_from_slice(&right_samples[i].to_le_bytes());
    }
    wav
}

fn generate_anc_swoop() -> Vec<u8> {
    let sample_rate = 44100;
    let duration = 0.38;
    let total_samples = (sample_rate as f32 * duration) as usize;
    let mut samples = Vec::with_capacity(total_samples);

    let mut phase = 0.0_f32;
    for i in 0..total_samples {
        let t = i as f32 / sample_rate as f32;
        let progress = t / duration;
        let freq = 720.0 * (130.0_f32 / 720.0).powf(progress);
        phase += 2.0 * PI * freq / sample_rate as f32;

        let env = if progress < 0.15 {
            progress / 0.15
        } else {
            (-3.8 * (progress - 0.15)).exp()
        };

        let sample = (phase.sin() * env * 0.75 * 32767.0) as i16;
        samples.push(sample);
    }
    create_mono_wav(&samples, sample_rate)
}

fn generate_transparency_chime() -> Vec<u8> {
    let sample_rate = 44100;
    let duration = 0.32;
    let total_samples = (sample_rate as f32 * duration) as usize;
    let mut samples = Vec::with_capacity(total_samples);

    for i in 0..total_samples {
        let t = i as f32 / sample_rate as f32;
        let progress = t / duration;
        let env = if progress < 0.05 {
            progress / 0.05
        } else {
            (-6.5 * (progress - 0.05)).exp()
        };

        let s1 = (2.0 * PI * 1046.5 * t).sin() * 0.65;
        let s2 = (2.0 * PI * 2093.0 * t).sin() * 0.35;
        let sample = ((s1 + s2) * env * 0.75 * 32767.0) as i16;
        samples.push(sample);
    }
    create_mono_wav(&samples, sample_rate)
}

fn generate_in_ear_chime() -> Vec<u8> {
    let sample_rate = 44100;
    let duration = 0.30;
    let total_samples = (sample_rate as f32 * duration) as usize;
    let mut samples = Vec::with_capacity(total_samples);

    for i in 0..total_samples {
        let t = i as f32 / sample_rate as f32;
        let progress = t / duration;
        let freq = if progress < 0.5 { 880.0 } else { 1760.0 };
        let env = (-4.0 * (progress % 0.5)).exp();
        let sample = ((2.0 * PI * freq * t).sin() * env * 0.7 * 32767.0) as i16;
        samples.push(sample);
    }
    create_mono_wav(&samples, sample_rate)
}

fn generate_siren_wav() -> Vec<u8> {
    let sample_rate = 44100;
    let cycle_duration = 0.40; // 0.2s high, 0.2s low
    let total_samples = (sample_rate as f32 * cycle_duration) as usize;
    let mut samples = Vec::with_capacity(total_samples);

    for i in 0..total_samples {
        let t = i as f32 / sample_rate as f32;
        let freq = if t < 0.20 { 3200.0 } else { 4000.0 };
        let sample = ((2.0 * PI * freq * t).sin() * 0.85 * 32767.0) as i16;
        samples.push(sample);
    }
    create_mono_wav(&samples, sample_rate)
}
