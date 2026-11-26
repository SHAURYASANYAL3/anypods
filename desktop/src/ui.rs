use std::f32::consts::PI;
use eframe::egui::{
    self, Color32, FontId, Pos2, Rect, Rounding, Stroke, Vec2,
};
use crate::audio_player::AudioPlayer;
use crate::media::{MediaAction, MediaController};
use crate::state::{
    AncIntensity, NoiseMode, SharedState, SpatialAudioMode,
};

pub struct AnyPodsDesktopApp {
    state: SharedState,
    hero_texture: Option<egui::TextureHandle>,
    selected_brand_filter: String,
    status_toast: Option<(String, std::time::Instant)>,
}

impl AnyPodsDesktopApp {
    pub fn new(state: SharedState) -> Self {
        Self {
            state,
            hero_texture: None,
            selected_brand_filter: "All".to_string(),
            status_toast: None,
        }
    }

    fn ensure_texture(&mut self, ctx: &egui::Context) {
        if self.hero_texture.is_none() {
            let img_bytes = include_bytes!("../assets/airpods_pro_2.png");
            if let Ok(dynamic_img) = image::load_from_memory(img_bytes) {
                let rgba = dynamic_img.to_rgba8();
                let size = [rgba.width() as usize, rgba.height() as usize];
                let pixels = rgba.into_raw();
                let color_image = egui::ColorImage::from_rgba_unmultiplied(size, &pixels);
                self.hero_texture = Some(ctx.load_texture(
                    "airpods_hero",
                    color_image,
                    egui::TextureOptions::LINEAR,
                ));
            }
        }
    }

    fn set_toast(&mut self, msg: &str) {
        self.status_toast = Some((msg.to_string(), std::time::Instant::now()));
    }
}

// Material 3 Expressive Color Tokens (Obsidian Dark Theme)
const COLOR_BG: Color32 = Color32::from_rgb(14, 16, 21);
const COLOR_SURFACE_CONTAINER: Color32 = Color32::from_rgb(22, 26, 34);
const COLOR_SURFACE_HIGH: Color32 = Color32::from_rgb(30, 35, 46);
const COLOR_SURFACE_HIGHEST: Color32 = Color32::from_rgb(39, 45, 59);
const COLOR_OUTLINE: Color32 = Color32::from_rgb(52, 60, 76);

const COLOR_PRIMARY: Color32 = Color32::from_rgb(56, 189, 248);       // Neon Cyan #38BDF8
const COLOR_PRIMARY_CONTAINER: Color32 = Color32::from_rgb(0, 75, 114);
const COLOR_ON_PRIMARY_CONTAINER: Color32 = Color32::from_rgb(203, 230, 255);

const COLOR_SUCCESS: Color32 = Color32::from_rgb(52, 211, 153);       // Emerald Green #34D399
const COLOR_TERTIARY: Color32 = Color32::from_rgb(192, 132, 252);     // Violet Purple #C084FC
const COLOR_CHARGING: Color32 = Color32::from_rgb(250, 204, 21);      // Amber Lightning #FACC15
const COLOR_ERROR: Color32 = Color32::from_rgb(248, 113, 113);

const COLOR_TEXT_PRIMARY: Color32 = Color32::from_rgb(243, 244, 246);
const COLOR_TEXT_SECONDARY: Color32 = Color32::from_rgb(156, 163, 175);
const COLOR_TEXT_MUTED: Color32 = Color32::from_rgb(107, 114, 128);

impl eframe::App for AnyPodsDesktopApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.ensure_texture(ctx);

        ctx.request_repaint_after(std::time::Duration::from_millis(150));

        let state_clone = {
            let s = self.state.lock().unwrap();
            s.clone()
        };

        let mut style = (*ctx.style()).clone();
        style.visuals.dark_mode = true;
        style.visuals.panel_fill = COLOR_BG;
        style.visuals.override_text_color = Some(COLOR_TEXT_PRIMARY);
        ctx.set_style(style);

        // Render main scrollable dashboard
        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical()
                .auto_shrink([false; 2])
                .show(ui, |ui| {
                    ui.add_space(6.0);

                    // TOP APP BAR
                    render_top_app_bar(self, ui, &state_clone);

                    // Toast message
                    if let Some((msg, time)) = &self.status_toast {
                        if time.elapsed().as_secs() < 3 {
                            ui.add_space(6.0);
                            egui::Frame::none()
                                .fill(COLOR_PRIMARY_CONTAINER)
                                .rounding(Rounding::same(12.0))
                                .inner_margin(egui::Margin::symmetric(12.0, 6.0))
                                .show(ui, |ui| {
                                    ui.label(
                                        egui::RichText::new(msg)
                                            .size(11.5)
                                            .strong()
                                            .color(COLOR_ON_PRIMARY_CONTAINER),
                                    );
                                });
                        }
                    }

                    ui.add_space(14.0);

                    // HERO CARD
                    render_hero_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // QUICK ACTION: AIRPODS POP-UP TRIGGER
                    render_popup_trigger_card(self, ui);

                    ui.add_space(14.0);

                    // 1. BATTERY & POWER CARD
                    render_battery_power_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 2. NOISE CONTROL MODES
                    render_noise_control_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 3. AUDIO INTELLIGENCE (Adaptive, Conversation Awareness, Personalized Volume)
                    render_audio_intelligence_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 4. 3D SPATIAL AUDIO
                    render_spatial_audio_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 5. HEADPHONE ACCOMMODATIONS
                    render_headphone_accommodations_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 6. LEFT / RIGHT AUDIO BALANCE
                    render_audio_balance_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 7. MICROPHONE QUALITY & VOICE CLARITY (User requested for Trigger Pod)
                    render_mic_boost_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 8. BLUETOOTH SIGNAL BOOST & RANGE EXTENDER (User requested)
                    render_signal_boost_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 9. IN-EAR DETECTION CARD
                    render_in_ear_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 10. HEARING HEALTH & DECIBEL LIMITER
                    render_hearing_health_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 11. PRECISION FIND MY RADAR & AUDIO SIREN
                    render_find_my_radar_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // 12. SOFTWARE UPDATE CARD
                    render_software_update_card(self, ui);

                    ui.add_space(24.0);
                });
        });

        // Floating Modal Popup (when triggered)
        if state_clone.show_popup_modal {
            render_floating_popup_modal(self, ctx, &state_clone);
        }
    }
}

// -----------------------------------------------------------------------------
// Top App Bar
// -----------------------------------------------------------------------------
fn render_top_app_bar(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    ui.horizontal(|ui| {
        ui.label(
            egui::RichText::new("🎧 AnyPods Pro")
                .size(21.0)
                .strong()
                .color(COLOR_TEXT_PRIMARY),
        );

        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            let sync_btn = egui::Button::new(
                egui::RichText::new("⚡ Sync")
                    .size(11.0)
                    .strong()
                    .color(COLOR_ON_PRIMARY_CONTAINER),
            )
            .fill(COLOR_PRIMARY_CONTAINER)
            .rounding(Rounding::same(50.0));

            if ui.add(sync_btn).clicked() {
                AudioPlayer::play_in_ear_chime();
                app.set_toast(&format!("⚡ Synced with {}", state.name));
            }
        });
    });

    ui.add_space(2.0);
    ui.label(
        egui::RichText::new("Universal Wireless Earbuds Superpowers & Acoustics Hub")
            .size(12.5)
            .color(COLOR_TEXT_SECONDARY),
    );
}

// -----------------------------------------------------------------------------
// Hero Card
// -----------------------------------------------------------------------------
fn render_hero_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.vertical_centered(|ui| {
                // Connection Status Pill
                let (conn_bg, conn_fg, conn_text) = if state.is_connected {
                    (COLOR_PRIMARY_CONTAINER, COLOR_ON_PRIMARY_CONTAINER, format!("● Connected ({})", state.name))
                } else {
                    (COLOR_SURFACE_HIGHEST, COLOR_TEXT_MUTED, "○ Searching for Earbuds...".to_string())
                };
                pill_badge(ui, &conn_text, conn_bg, conn_fg, 12.0);

                ui.add_space(10.0);

                // Earbuds Image with Soft Ambient Halo
                let img_height = 120.0;
                let (rect, _response) = ui.allocate_exact_size(Vec2::new(ui.available_width(), img_height), egui::Sense::hover());
                let painter = ui.painter();
                let center = rect.center();

                let glow_radius = 65.0;
                painter.circle_filled(
                    center,
                    glow_radius,
                    Color32::from_rgba_unmultiplied(56, 189, 248, 22),
                );

                if let Some(texture) = &app.hero_texture {
                    let aspect_ratio = texture.size()[0] as f32 / texture.size()[1] as f32;
                    let target_h = 110.0;
                    let target_w = target_h * aspect_ratio;
                    let img_rect = Rect::from_center_size(center, Vec2::new(target_w, target_h));
                    painter.image(
                        texture.id(),
                        img_rect,
                        Rect::from_min_max(Pos2::new(0.0, 0.0), Pos2::new(1.0, 1.0)),
                        Color32::WHITE,
                    );
                }

                ui.add_space(4.0);

                // Device Title & Subtitle
                ui.label(
                    egui::RichText::new(&state.name)
                        .size(21.0)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );
                ui.label(
                    egui::RichText::new(format!("MAC: {}  •  {}", state.mac_address, state.chip_info))
                        .size(11.5)
                        .color(COLOR_TEXT_SECONDARY),
                );

                ui.add_space(10.0);

                // Brand Selector Filter Chips
                ui.label(
                    egui::RichText::new("Brand Categories:")
                        .size(11.0)
                        .color(COLOR_TEXT_MUTED),
                );
                ui.add_space(4.0);

                ui.horizontal_wrapped(|ui| {
                    let brands = ["All", "Apple", "Samsung", "Google", "Sony", "Nothing", "Beats", "Universal"];
                    for brand in brands {
                        let is_sel = app.selected_brand_filter == brand;
                        let btn = egui::Button::new(
                            egui::RichText::new(brand)
                                .size(11.0)
                                .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                                .strong(),
                        )
                        .fill(if is_sel { COLOR_PRIMARY } else { COLOR_SURFACE_HIGHEST })
                        .rounding(Rounding::same(50.0));

                        if ui.add(btn).clicked() {
                            app.selected_brand_filter = brand.to_string();
                        }
                    }
                });

                ui.add_space(8.0);

                // Earbuds Models Picker Chips
                ui.horizontal_wrapped(|ui| {
                    let models = [
                        ("TRIGGR Kraken X1", "3B:4A:28:14:69:32", "TRIGGR Gaming Series • Low Latency AAC/SBC • 48 kHz", 90, 80, 90),
                        ("Neuton:A1", "96:8E:DC:EE:DD:7F", "Neuton Audio Engine • Hi-Fi SBC 44.1 kHz", 85, 75, 85),
                        ("AirPods Pro (2nd Gen)", "00:4C:E2:81:9B:10", "Apple H2 Chip • AAC-ELD 48 kHz Studio Stereo", 88, 76, 92),
                        ("AirPods 3", "00:4C:E3:42:11:50", "Apple H1 Chip • Adaptive EQ 48 kHz", 92, 85, 95),
                        ("AirPods Max", "00:4C:F0:12:88:99", "Dual H1 Processing • 40mm Dynamic Driver", 78, 0, 78),
                        ("Galaxy Buds2 Pro", "44:5E:CD:12:00:19", "Samsung Seamless Codec • 24-bit Hi-Fi", 85, 70, 85),
                        ("Sony WF-1000XM5", "AC:80:0A:77:44:22", "Integrated Processor V2 • LDAC 990 kbps", 90, 65, 90),
                        ("Nothing Ear (2)", "68:FE:F7:11:23:44", "Custom 11.6mm Diaphragm • LHDC 5.0 High-Res", 80, 60, 80),
                    ];

                    for (name, mac, chip, l_batt, c_batt, r_batt) in models {
                        let is_active = state.name == name;
                        let btn = egui::Button::new(
                            egui::RichText::new(name)
                                .size(11.0)
                                .color(if is_active { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                                .strong(),
                        )
                        .fill(if is_active { COLOR_PRIMARY } else { COLOR_SURFACE_HIGH })
                        .stroke(Stroke::new(1.0_f32, if is_active { COLOR_PRIMARY } else { COLOR_OUTLINE }))
                        .rounding(Rounding::same(12.0));

                        if ui.add(btn).clicked() {
                            let mut s = app.state.lock().unwrap();
                            s.name = name.to_string();
                            s.mac_address = mac.to_string();
                            s.chip_info = chip.to_string();
                            s.left_battery = l_batt;
                            s.case_battery = c_batt;
                            s.right_battery = r_batt;
                            s.is_connected = true;
                            drop(s);
                            AudioPlayer::play_in_ear_chime();
                            app.set_toast(&format!("Active Hardware: {}", name));
                        }
                    }
                });

                ui.add_space(16.0);

                // 3 M3 Battery Dials
                ui.horizontal(|ui| {
                    let dial_w = (ui.available_width() - 16.0) / 3.0;

                    ui.allocate_ui(Vec2::new(dial_w, 100.0), |ui| {
                        render_m3_battery_dial(
                            ui,
                            "Left Bud",
                            state.left_battery,
                            state.is_left_charging,
                            state.is_left_in_ear,
                            COLOR_PRIMARY,
                        );
                    });

                    ui.allocate_ui(Vec2::new(dial_w, 100.0), |ui| {
                        render_m3_battery_dial(
                            ui,
                            "Case",
                            state.case_battery,
                            state.is_case_charging,
                            false,
                            COLOR_TERTIARY,
                        );
                    });

                    ui.allocate_ui(Vec2::new(dial_w, 100.0), |ui| {
                        render_m3_battery_dial(
                            ui,
                            "Right Bud",
                            state.right_battery,
                            state.is_right_charging,
                            state.is_right_in_ear,
                            COLOR_SUCCESS,
                        );
                    });
                });
            });
        });
}

// -----------------------------------------------------------------------------
// Quick Actions: Pop-up Trigger Card
// -----------------------------------------------------------------------------
fn render_popup_trigger_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("AirPods Pop-up Card")
                    .size(16.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.label(
                egui::RichText::new("Show the floating popup card with live battery gauges and instant status.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            let popup_btn = egui::Button::new(
                egui::RichText::new("📱 Show AirPods Pop-up Card")
                    .size(12.5)
                    .strong()
                    .color(COLOR_ON_PRIMARY_CONTAINER),
            )
            .min_size(Vec2::new(ui.available_width(), 36.0))
            .fill(COLOR_PRIMARY_CONTAINER)
            .rounding(Rounding::same(50.0));

            if ui.add(popup_btn).clicked() {
                let mut s = app.state.lock().unwrap();
                s.show_popup_modal = true;
                drop(s);
                AudioPlayer::play_in_ear_chime();
            }
        });
}

// -----------------------------------------------------------------------------
// 1. Battery & Power Card
// -----------------------------------------------------------------------------
fn render_battery_power_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("1. Battery & Power")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let sync_btn = egui::Button::new(
                        egui::RichText::new("⚡ Sync")
                            .size(11.0)
                            .strong()
                            .color(COLOR_ON_PRIMARY_CONTAINER),
                    )
                    .fill(COLOR_PRIMARY_CONTAINER)
                    .rounding(Rounding::same(50.0));

                    if ui.add(sync_btn).clicked() {
                        AudioPlayer::play_in_ear_chime();
                        app.set_toast(&format!("⚡ Synced with {}", state.name));
                    }
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Auto-synced with connected Bluetooth hardware.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(12.0);

            ui.horizontal(|ui| {
                let pill_w = (ui.available_width() - 16.0) / 3.0;
                render_battery_pill(ui, pill_w, "Left Pod", state.left_battery, state.is_left_charging, "In Ear • Active");
                render_battery_pill(ui, pill_w, "Case", state.case_battery, state.is_case_charging, "Ready • In Case");
                render_battery_pill(ui, pill_w, "Right Pod", state.right_battery, state.is_right_charging, "In Ear • Active");
            });
        });
}

fn render_battery_pill(ui: &mut egui::Ui, width: f32, label: &str, level: i32, is_charging: bool, subtext: &str) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_HIGH)
        .rounding(Rounding::same(16.0))
        .inner_margin(egui::Margin::same(10.0))
        .show(ui, |ui| {
            ui.set_width(width);
            ui.vertical_centered(|ui| {
                ui.label(egui::RichText::new(label).size(11.5).color(COLOR_TEXT_SECONDARY));
                ui.add_space(2.0);
                ui.label(
                    egui::RichText::new(if level >= 0 { format!("{}%", level) } else { "--".to_string() })
                        .size(18.0)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );
                ui.add_space(2.0);
                ui.label(
                    egui::RichText::new(if is_charging { "⚡ Charging" } else { subtext })
                        .size(10.0)
                        .color(if is_charging { COLOR_CHARGING } else { COLOR_TEXT_MUTED }),
                );
            });
        });
}

// -----------------------------------------------------------------------------
// 2. Noise Control Card
// -----------------------------------------------------------------------------
fn render_noise_control_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("2. Noise Control")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if state.noise_mode == NoiseMode::Adaptive {
                        pill_badge(
                            ui,
                            &format!("Ambient: {} dB", state.ambient_db),
                            COLOR_PRIMARY_CONTAINER,
                            COLOR_ON_PRIMARY_CONTAINER,
                            11.0,
                        );
                    }
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Hardware ANC and environmental transparency switching with audio chimes.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(12.0);

            // 4-Segmented Control Island
            egui::Frame::none()
                .fill(COLOR_SURFACE_HIGHEST)
                .rounding(Rounding::same(50.0))
                .inner_margin(egui::Margin::same(4.0))
                .show(ui, |ui| {
                    ui.horizontal(|ui| {
                        let seg_w = (ui.available_width() - 12.0) / 4.0;
                        let modes = [
                            (NoiseMode::NoiseCancellation, "🔇 ANC"),
                            (NoiseMode::Off, "○ Off"),
                            (NoiseMode::Transparency, "🔊 Passthrough"),
                            (NoiseMode::Adaptive, "⚡ Adaptive"),
                        ];

                        for (mode, title) in modes {
                            let is_sel = state.noise_mode == mode;
                            let btn = egui::Button::new(
                                egui::RichText::new(title)
                                    .size(11.5)
                                    .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                                    .strong(),
                            )
                            .min_size(Vec2::new(seg_w, 32.0))
                            .fill(if is_sel { COLOR_PRIMARY } else { Color32::TRANSPARENT })
                            .rounding(Rounding::same(50.0));

                            if ui.add(btn).clicked() {
                                let mut s = app.state.lock().unwrap();
                                s.noise_mode = mode;
                                drop(s);

                                match mode {
                                    NoiseMode::NoiseCancellation => {
                                        AudioPlayer::play_anc_chime();
                                        app.set_toast("🔇 Active Noise Cancellation Engaged");
                                    }
                                    NoiseMode::Transparency => {
                                        AudioPlayer::play_transparency_chime();
                                        app.set_toast("🔊 Transparency Mode Activated");
                                    }
                                    NoiseMode::Adaptive => {
                                        AudioPlayer::play_in_ear_chime();
                                        app.set_toast("⚡ Adaptive Dynamic Audio Enabled");
                                    }
                                    NoiseMode::Off => {
                                        AudioPlayer::stop_sound();
                                        app.set_toast("○ Noise Control Off");
                                    }
                                }
                            }
                        }
                    });
                });

            // Cancellation Strength Sub-Chips
            if state.noise_mode == NoiseMode::NoiseCancellation {
                ui.add_space(14.0);
                ui.separator();
                ui.add_space(10.0);

                ui.horizontal(|ui| {
                    ui.label(
                        egui::RichText::new("Cancellation Strength")
                            .size(13.0)
                            .strong()
                            .color(COLOR_TEXT_PRIMARY),
                    );

                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        let badge_txt = match state.anc_intensity {
                            AncIntensity::Ultra => "⚡ 98% Studio Seal",
                            AncIntensity::Balanced => "⚡ 85% Commute",
                            AncIntensity::Comfort => "⚡ 68% Gentle",
                        };
                        pill_badge(ui, badge_txt, COLOR_PRIMARY_CONTAINER, COLOR_ON_PRIMARY_CONTAINER, 10.5);
                    });
                });

                ui.add_space(8.0);

                ui.horizontal(|ui| {
                    let chip_w = (ui.available_width() - 16.0) / 3.0;
                    let strengths = [
                        (AncIntensity::Ultra, "Ultra Studio", "-30 dB"),
                        (AncIntensity::Balanced, "Balanced", "-22 dB"),
                        (AncIntensity::Comfort, "Comfort", "-15 dB"),
                    ];

                    for (intensity, title, db) in strengths {
                        let is_sel = state.anc_intensity == intensity;
                        let btn = egui::Button::new(
                            egui::RichText::new(format!("{}\n{}", title, db))
                                .size(11.0)
                                .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                                .strong(),
                        )
                        .min_size(Vec2::new(chip_w, 42.0))
                        .fill(if is_sel { COLOR_PRIMARY_CONTAINER } else { COLOR_SURFACE_HIGH })
                        .stroke(Stroke::new(1.0_f32, if is_sel { COLOR_PRIMARY } else { COLOR_OUTLINE }))
                        .rounding(Rounding::same(14.0));

                        if ui.add(btn).clicked() {
                            let mut s = app.state.lock().unwrap();
                            s.anc_intensity = intensity;
                            drop(s);
                            AudioPlayer::play_anc_chime();
                            app.set_toast(&format!("ANC Strength: {} ({})", title, db));
                        }
                    }
                });
            }
        });
}

// -----------------------------------------------------------------------------
// 3. Audio Intelligence Card
// -----------------------------------------------------------------------------
fn render_audio_intelligence_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("3. Audio Intelligence")
                    .size(16.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.label(
                egui::RichText::new("Intelligent ambient audio adjustments and speech awareness.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            let mut adaptive = state.adaptive_audio_enabled;
            if ui.checkbox(&mut adaptive, "Adaptive Audio (Environmental auto-switching)").changed() {
                let mut s = app.state.lock().unwrap();
                s.adaptive_audio_enabled = adaptive;
            }

            let mut convo = state.conversation_awareness_enabled;
            if ui.checkbox(&mut convo, "Conversation Awareness (Auto-ducks media by 65%)").changed() {
                let mut s = app.state.lock().unwrap();
                s.conversation_awareness_enabled = convo;
                drop(s);
                app.set_toast(if convo { "Conversation Awareness Active" } else { "Conversation Awareness Disabled" });
            }

            let mut pvol = state.personalized_volume_enabled;
            if ui.checkbox(&mut pvol, "Personalized Volume (Acoustic learning curve)").changed() {
                let mut s = app.state.lock().unwrap();
                s.personalized_volume_enabled = pvol;
            }
        });
}

// -----------------------------------------------------------------------------
// 4. 3D Spatial Audio Card
// -----------------------------------------------------------------------------
fn render_spatial_audio_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("4. Spatial Audio")
                    .size(16.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.label(
                egui::RichText::new("Transforms standard stereo music into an expansive 3D acoustic stage.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            ui.horizontal(|ui| {
                let modes = [
                    (SpatialAudioMode::Off, "○ Off"),
                    (SpatialAudioMode::Fixed, "🎧 Fixed"),
                    (SpatialAudioMode::HeadTracked, "🌐 Head Tracked"),
                ];

                for (mode, label) in modes {
                    let is_sel = state.spatial_audio == mode;
                    let btn = egui::Button::new(
                        egui::RichText::new(label)
                            .size(11.5)
                            .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                            .strong(),
                    )
                    .fill(if is_sel { COLOR_PRIMARY } else { COLOR_SURFACE_HIGHEST })
                    .rounding(Rounding::same(50.0));

                    if ui.add(btn).clicked() {
                        let mut s = app.state.lock().unwrap();
                        s.spatial_audio = mode;
                        drop(s);
                        AudioPlayer::play_in_ear_chime();
                        app.set_toast(&format!("Spatial Audio: {}", label));
                    }
                }
            });
        });
}

// -----------------------------------------------------------------------------
// 5. Headphone Accommodations Card
// -----------------------------------------------------------------------------
fn render_headphone_accommodations_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("5. Headphone Accommodations")
                    .size(16.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.label(
                egui::RichText::new("Custom audio/visual profiles tuned for dialogue, acoustics, and brightness.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            // Profile chips
            ui.horizontal(|ui| {
                let profiles = ["Balanced Tone", "Vocal Range", "Brightness"];
                for prof in profiles {
                    let is_sel = state.tone_profile == prof;
                    let btn = egui::Button::new(
                        egui::RichText::new(prof)
                            .size(11.0)
                            .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                            .strong(),
                    )
                    .fill(if is_sel { COLOR_PRIMARY } else { COLOR_SURFACE_HIGH })
                    .stroke(Stroke::new(1.0_f32, if is_sel { COLOR_PRIMARY } else { COLOR_OUTLINE }))
                    .rounding(Rounding::same(12.0));

                    if ui.add(btn).clicked() {
                        let mut s = app.state.lock().unwrap();
                        s.tone_profile = prof.to_string();
                        drop(s);
                        app.set_toast(&format!("Acoustic Profile: {}", prof));
                    }
                }
            });

            ui.add_space(8.0);

            // Intensity chips
            ui.label(
                egui::RichText::new("Tuning Intensity:")
                    .size(11.5)
                    .color(COLOR_TEXT_MUTED),
            );
            ui.horizontal(|ui| {
                let intensities = ["Slight", "Moderate", "Strong"];
                for intn in intensities {
                    let is_sel = state.tone_intensity == intn;
                    let btn = egui::Button::new(
                        egui::RichText::new(intn)
                            .size(11.0)
                            .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                            .strong(),
                    )
                    .fill(if is_sel { COLOR_PRIMARY_CONTAINER } else { COLOR_SURFACE_HIGHEST })
                    .rounding(Rounding::same(50.0));

                    if ui.add(btn).clicked() {
                        let mut s = app.state.lock().unwrap();
                        s.tone_intensity = intn.to_string();
                    }
                }
            });
        });
}

// -----------------------------------------------------------------------------
// 6. Left / Right Audio Balance Card
// -----------------------------------------------------------------------------
fn render_audio_balance_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("6. Left / Right Audio Balance")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let bal_txt = if state.lr_balance < -0.05 {
                        format!("L {}%", (-state.lr_balance * 100.0) as i32)
                    } else if state.lr_balance > 0.05 {
                        format!("R {}%", (state.lr_balance * 100.0) as i32)
                    } else {
                        "Center".to_string()
                    };
                    pill_badge(ui, &bal_txt, COLOR_SURFACE_HIGHEST, COLOR_PRIMARY, 11.0);
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Adjust stereo acoustic balance between left and right channels.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(8.0);

            ui.horizontal(|ui| {
                let mut balance = state.lr_balance;
                if ui.add(egui::Slider::new(&mut balance, -1.0..=1.0).show_value(false)).changed() {
                    let mut s = app.state.lock().unwrap();
                    s.lr_balance = balance;
                }

                let ping_btn = egui::Button::new(
                    egui::RichText::new("🔊 Test Ping")
                        .size(11.0)
                        .strong()
                        .color(COLOR_ON_PRIMARY_CONTAINER),
                )
                .fill(COLOR_PRIMARY_CONTAINER)
                .rounding(Rounding::same(10.0));

                if ui.add(ping_btn).clicked() {
                    AudioPlayer::play_balance_test(state.lr_balance);
                    app.set_toast("🔊 Playing stereo balance test tone");
                }
            });

            ui.horizontal(|ui| {
                ui.label(egui::RichText::new("Left").size(11.0).color(COLOR_TEXT_MUTED));
                ui.with_layout(egui::Layout::centered_and_justified(egui::Direction::LeftToRight), |ui| {
                    ui.label(egui::RichText::new("Center").size(11.0).color(COLOR_TEXT_MUTED));
                });
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.label(egui::RichText::new("Right").size(11.0).color(COLOR_TEXT_MUTED));
                });
            });
        });
}

// -----------------------------------------------------------------------------
// 7. Microphone Quality & Voice Boost Card (For Trigger & All Pods)
// -----------------------------------------------------------------------------
fn render_mic_boost_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("7. Microphone Quality & Voice Boost")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let gain_db = (state.mic_boost_level - 1.0) * 12.0;
                    pill_badge(
                        ui,
                        &format!("+{:.1} dB Boost", gain_db),
                        COLOR_PRIMARY_CONTAINER,
                        COLOR_ON_PRIMARY_CONTAINER,
                        11.0,
                    );
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Upgrades muffled or quiet earbud microphones with 16 kHz HD Voice & AI DSP.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            let mut hd_voice = state.mic_hd_wideband;
            if ui.checkbox(&mut hd_voice, "HD Wideband Speech (mSBC 16 kHz Audio)").changed() {
                let mut s = app.state.lock().unwrap();
                s.mic_hd_wideband = hd_voice;
                drop(s);
                AudioPlayer::play_in_ear_chime();
                app.set_toast(if hd_voice { "16 kHz HD Voice Active" } else { "Standard 8 kHz Audio" });
            }

            let mut clarity_dsp = state.mic_clarity_dsp;
            if ui.checkbox(&mut clarity_dsp, "AI Voice Clarity DSP (Noise & Echo Suppression)").changed() {
                let mut s = app.state.lock().unwrap();
                s.mic_clarity_dsp = clarity_dsp;
                drop(s);
                app.set_toast(if clarity_dsp { "Voice Noise Suppression ON" } else { "DSP Bypassed" });
            }

            ui.add_space(10.0);

            ui.label(
                egui::RichText::new("Microphone Hardware Allocation")
                    .size(12.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.add_space(4.0);

            ui.horizontal(|ui| {
                let options = ["Auto-Switch", "Always Left", "Always Right"];
                for opt in options {
                    let is_sel = state.mic_selection == opt;
                    let btn = egui::Button::new(
                        egui::RichText::new(opt)
                            .size(11.0)
                            .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                            .strong(),
                    )
                    .fill(if is_sel { COLOR_PRIMARY } else { COLOR_SURFACE_HIGHEST })
                    .rounding(Rounding::same(50.0));

                    if ui.add(btn).clicked() {
                        let mut s = app.state.lock().unwrap();
                        s.mic_selection = opt.to_string();
                        drop(s);
                        app.set_toast(&format!("Mic Routed to: {}", opt));
                    }
                }
            });

            ui.add_space(12.0);

            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("Microphone Gain Boost")
                        .size(12.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let pct = ((state.mic_boost_level - 1.0) / 1.5 * 100.0) as i32;
                    pill_badge(ui, &format!("{}% Boost", pct), COLOR_SURFACE_HIGHEST, COLOR_PRIMARY, 10.5);
                });
            });

            ui.add_space(4.0);
            ui.horizontal(|ui| {
                let mut boost = state.mic_boost_level;
                if ui.add(egui::Slider::new(&mut boost, 1.0..=2.5).show_value(false)).changed() {
                    let mut s = app.state.lock().unwrap();
                    s.mic_boost_level = boost;
                }

                let test_btn = egui::Button::new(
                    egui::RichText::new("🔊 Test Mic Ping")
                        .size(11.0)
                        .strong()
                        .color(COLOR_ON_PRIMARY_CONTAINER),
                )
                .fill(COLOR_PRIMARY_CONTAINER)
                .rounding(Rounding::same(10.0));

                if ui.add(test_btn).clicked() {
                    AudioPlayer::play_balance_test(0.0);
                    app.set_toast("🔊 Mic Boost Test Audio Active");
                }
            });
        });
}

// -----------------------------------------------------------------------------
// 8. Bluetooth Signal Boost & Range Extender Card
// -----------------------------------------------------------------------------
fn render_signal_boost_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("8. Bluetooth Signal Boost & Range")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    pill_badge(
                        ui,
                        &format!("+{}% Coverage", state.signal_range_pct),
                        COLOR_PRIMARY_CONTAINER,
                        COLOR_ON_PRIMARY_CONTAINER,
                        11.0,
                    );
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Extends wireless transmission range and prevents audio dropouts through walls.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            ui.horizontal(|ui| {
                pill_badge(
                    ui,
                    &format!("⚡ TX Power: +{} dBm", state.signal_tx_power_dbm),
                    COLOR_SURFACE_HIGHEST,
                    COLOR_SUCCESS,
                    11.0,
                );
                pill_badge(
                    ui,
                    "📡 RSSI Filter: High-Gain",
                    COLOR_SURFACE_HIGHEST,
                    COLOR_PRIMARY,
                    11.0,
                );
            });

            ui.add_space(10.0);

            let mut sig_boost = state.signal_boost_enabled;
            if ui.checkbox(&mut sig_boost, "Enable Maximum RF Signal Boost & Packet Retention").changed() {
                let mut s = app.state.lock().unwrap();
                s.signal_boost_enabled = sig_boost;
                drop(s);
                AudioPlayer::play_in_ear_chime();
                app.set_toast(if sig_boost { "Signal Boost Engaged (+35% Range)" } else { "Standard RF Power" });
            }
        });
}

// -----------------------------------------------------------------------------
// 9. In-Ear Detection Card
// -----------------------------------------------------------------------------
fn render_in_ear_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("9. In-Ear Optical Detection")
                    .size(16.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.label(
                egui::RichText::new("Hardware infrared optical proximity sensors manage media playback.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            ui.horizontal(|ui| {
                let (l_bg, l_fg, l_txt) = if state.is_left_in_ear {
                    (COLOR_PRIMARY_CONTAINER, COLOR_ON_PRIMARY_CONTAINER, "👂 Left Bud: In-Ear (Active)")
                } else {
                    (COLOR_SURFACE_HIGHEST, COLOR_TEXT_MUTED, "⚪ Left Bud: Out of Ear")
                };
                pill_badge(ui, l_txt, l_bg, l_fg, 11.0);

                let (r_bg, r_fg, r_txt) = if state.is_right_in_ear {
                    (COLOR_PRIMARY_CONTAINER, COLOR_ON_PRIMARY_CONTAINER, "👂 Right Bud: In-Ear (Active)")
                } else {
                    (COLOR_SURFACE_HIGHEST, COLOR_TEXT_MUTED, "⚪ Right Bud: Out of Ear")
                };
                pill_badge(ui, r_txt, r_bg, r_fg, 11.0);
            });

            ui.add_space(10.0);

            let sim_txt = if state.is_left_in_ear && state.is_right_in_ear {
                "🎧 Remove Earbud (Trigger Real OS Media Auto-Pause)"
            } else {
                "🎧 Insert Earbud (Trigger Real OS Media Auto-Resume)"
            };

            let sim_btn = egui::Button::new(
                egui::RichText::new(sim_txt)
                    .size(12.0)
                    .strong()
                    .color(Color32::WHITE),
            )
            .min_size(Vec2::new(ui.available_width(), 32.0))
            .fill(COLOR_PRIMARY_CONTAINER)
            .rounding(Rounding::same(12.0));

            if ui.add(sim_btn).clicked() {
                let is_both_in = state.is_left_in_ear && state.is_right_in_ear;
                let mut s = app.state.lock().unwrap();
                s.is_left_in_ear = !is_both_in;
                s.is_right_in_ear = !is_both_in;
                drop(s);

                AudioPlayer::play_in_ear_chime();
                if is_both_in {
                    MediaController::trigger(MediaAction::Pause);
                    app.set_toast("⏸ Earbud Removed -> OS Media Paused!");
                } else {
                    MediaController::trigger(MediaAction::Play);
                    app.set_toast("▶ Earbud Reinserted -> OS Media Resumed!");
                }
            }

            ui.add_space(10.0);

            let mut auto_pause = state.auto_pause_enabled;
            if ui.checkbox(&mut auto_pause, "Auto-Pause & Resume on Earbud Removal").changed() {
                let mut s = app.state.lock().unwrap();
                s.auto_pause_enabled = auto_pause;
                drop(s);
                app.set_toast(if auto_pause { "Auto-Pause Enabled" } else { "Auto-Pause Disabled" });
            }

            let mut low_batt = state.low_battery_alert_enabled;
            if ui.checkbox(&mut low_batt, "Low Battery Audio Chimes (< 20%)").changed() {
                let mut s = app.state.lock().unwrap();
                s.low_battery_alert_enabled = low_batt;
                drop(s);
                app.set_toast(if low_batt { "Low Battery Alert Enabled" } else { "Low Battery Alert Disabled" });
            }
        });
}

// -----------------------------------------------------------------------------
// 10. Hearing Health & Decibel Limiter Card
// -----------------------------------------------------------------------------
fn render_hearing_health_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("10. Hearing Health & Decibel Shield")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    pill_badge(
                        ui,
                        &format!("Cap: {} dB", state.decibel_shield_limit),
                        COLOR_PRIMARY_CONTAINER,
                        COLOR_ON_PRIMARY_CONTAINER,
                        11.0,
                    );
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Real-time DSP peak limiter caps sudden loud sounds to protect eardrums.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            let mut shield = state.decibel_shield_enabled;
            if ui.checkbox(&mut shield, "Enable Safe Decibel Peak Limiter").changed() {
                let mut s = app.state.lock().unwrap();
                s.decibel_shield_enabled = shield;
                drop(s);
                app.set_toast(if shield { "Decibel Shield Engaged" } else { "Peak Limiter Bypassed" });
            }

            ui.add_space(6.0);
            let mut limit = state.decibel_shield_limit;
            if ui.add(egui::Slider::new(&mut limit, 75..=95).text("dB Peak Threshold")).changed() {
                let mut s = app.state.lock().unwrap();
                s.decibel_shield_limit = limit;
            }
        });
}

// -----------------------------------------------------------------------------
// 11. Precision Find My Radar & Audio Siren Card
// -----------------------------------------------------------------------------
fn render_find_my_radar_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("11. Precision Find My Radar")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    pill_badge(
                        ui,
                        &format!("{} m • Lock-On", state.radar_distance),
                        COLOR_PRIMARY_CONTAINER,
                        COLOR_ON_PRIMARY_CONTAINER,
                        11.0,
                    );
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("Real-time Bluetooth RSSI path loss distance tracking and siren.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(14.0);

            // Radar Visualizer
            let (radar_rect, _resp) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 90.0), egui::Sense::hover());
            let painter = ui.painter();
            let center = radar_rect.center();

            for r in [20.0_f32, 40.0, 60.0, 80.0] {
                painter.circle_stroke(
                    center,
                    r,
                    Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(56, 189, 248, 40)),
                );
            }

            painter.circle_filled(center, 7.0, COLOR_PRIMARY);
            painter.circle_stroke(center, 12.0, Stroke::new(1.5_f32, COLOR_PRIMARY));
            painter.text(
                center + Vec2::new(0.0, 18.0),
                egui::Align2::CENTER_CENTER,
                format!("RSSI: {} dBm", state.radar_rssi),
                FontId::proportional(11.0),
                COLOR_PRIMARY,
            );

            ui.add_space(12.0);

            // Ring Left and Ring Right buttons
            ui.horizontal(|ui| {
                let btn_w = (ui.available_width() - 8.0) / 2.0;

                let ring_l = egui::Button::new(
                    egui::RichText::new("🔔 Ring Left Bud")
                        .size(11.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                )
                .min_size(Vec2::new(btn_w, 32.0))
                .fill(COLOR_SURFACE_HIGH)
                .rounding(Rounding::same(12.0));

                if ui.add(ring_l).clicked() {
                    AudioPlayer::play_chirp_left();
                    app.set_toast("🔔 Chirping Left Bud");
                }

                let ring_r = egui::Button::new(
                    egui::RichText::new("🔔 Ring Right Bud")
                        .size(11.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                )
                .min_size(Vec2::new(btn_w, 32.0))
                .fill(COLOR_SURFACE_HIGH)
                .rounding(Rounding::same(12.0));

                if ui.add(ring_r).clicked() {
                    AudioPlayer::play_chirp_right();
                    app.set_toast("🔔 Chirping Right Bud");
                }
            });

            ui.add_space(10.0);

            // Piercing Siren Audio Trigger Button
            let siren_active = state.siren_active;
            let btn_txt = if siren_active {
                "⏹ Stop Finding Siren"
            } else {
                "🚨 Play +25dB Finding Siren (Dual-Tone Alarm)"
            };

            let siren_btn = egui::Button::new(
                egui::RichText::new(btn_txt)
                    .size(13.0)
                    .strong()
                    .color(Color32::WHITE),
            )
            .min_size(Vec2::new(ui.available_width(), 38.0))
            .fill(if siren_active { COLOR_ERROR } else { COLOR_PRIMARY })
            .rounding(Rounding::same(50.0));

            if ui.add(siren_btn).clicked() {
                let new_active = !siren_active;
                let mut s = app.state.lock().unwrap();
                s.siren_active = new_active;
                drop(s);

                if new_active {
                    AudioPlayer::start_siren();
                    app.set_toast("🚨 Finding Siren Blaring at Maximum Volume!");
                } else {
                    AudioPlayer::stop_sound();
                    app.set_toast("⏹ Finding Siren Silenced.");
                }
            }
        });
}

// -----------------------------------------------------------------------------
// 12. Software Update Card
// -----------------------------------------------------------------------------
fn render_software_update_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(28.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("12. Software Update")
                        .size(16.5)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    pill_badge(ui, "Latest Release", COLOR_PRIMARY_CONTAINER, COLOR_ON_PRIMARY_CONTAINER, 10.5);
                });
            });

            ui.add_space(2.0);
            ui.label(
                egui::RichText::new("AnyPods Pro is up to date (v1.3.2 Studio).")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            let chk_btn = egui::Button::new(
                egui::RichText::new("🔄 Check for Updates")
                    .size(12.0)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            )
            .min_size(Vec2::new(ui.available_width(), 32.0))
            .fill(COLOR_SURFACE_HIGH)
            .rounding(Rounding::same(50.0));

            if ui.add(chk_btn).clicked() {
                AudioPlayer::play_in_ear_chime();
                app.set_toast("✅ You have the latest version of AnyPods Pro!");
            }
        });
}

// -----------------------------------------------------------------------------
// Floating Pop-up Modal (Matching APK Popup Card)
// -----------------------------------------------------------------------------
fn render_floating_popup_modal(app: &mut AnyPodsDesktopApp, ctx: &egui::Context, state: &crate::state::PodState) {
    egui::Window::new("AirPods Modal")
        .title_bar(false)
        .resizable(false)
        .anchor(egui::Align2::CENTER_CENTER, Vec2::new(0.0, 0.0))
        .fixed_size(Vec2::new(320.0, 360.0))
        .frame(
            egui::Frame::none()
                .fill(COLOR_SURFACE_CONTAINER)
                .rounding(Rounding::same(32.0))
                .stroke(Stroke::new(1.5_f32, COLOR_PRIMARY))
                .inner_margin(egui::Margin::same(20.0)),
        )
        .show(ctx, |ui| {
            ui.vertical_centered(|ui| {
                // Top close button
                ui.horizontal(|ui| {
                    ui.label(
                        egui::RichText::new("AnyPods Connected")
                            .size(13.0)
                            .strong()
                            .color(COLOR_PRIMARY),
                    );
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        let close_btn = egui::Button::new(
                            egui::RichText::new("✕")
                                .size(12.0)
                                .color(COLOR_TEXT_MUTED),
                        )
                        .fill(Color32::TRANSPARENT);

                        if ui.add(close_btn).clicked() {
                            let mut s = app.state.lock().unwrap();
                            s.show_popup_modal = false;
                        }
                    });
                });

                ui.add_space(8.0);

                // Earbuds image
                if let Some(texture) = &app.hero_texture {
                    let aspect_ratio = texture.size()[0] as f32 / texture.size()[1] as f32;
                    let target_h = 100.0;
                    let target_w = target_h * aspect_ratio;
                    ui.image(egui::load::SizedTexture::new(texture.id(), Vec2::new(target_w, target_h)));
                }

                ui.add_space(6.0);

                ui.label(
                    egui::RichText::new(&state.name)
                        .size(19.0)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.add_space(14.0);

                // 3 mini battery rings
                ui.horizontal(|ui| {
                    let w = (ui.available_width() - 8.0) / 3.0;
                    ui.allocate_ui(Vec2::new(w, 80.0), |ui| {
                        render_m3_battery_dial(ui, "Left", state.left_battery, state.is_left_charging, state.is_left_in_ear, COLOR_PRIMARY);
                    });
                    ui.allocate_ui(Vec2::new(w, 80.0), |ui| {
                        render_m3_battery_dial(ui, "Case", state.case_battery, state.is_case_charging, false, COLOR_TERTIARY);
                    });
                    ui.allocate_ui(Vec2::new(w, 80.0), |ui| {
                        render_m3_battery_dial(ui, "Right", state.right_battery, state.is_right_charging, state.is_right_in_ear, COLOR_SUCCESS);
                    });
                });

                ui.add_space(14.0);

                // Done Button
                let done_btn = egui::Button::new(
                    egui::RichText::new("Done")
                        .size(13.0)
                        .strong()
                        .color(Color32::WHITE),
                )
                .min_size(Vec2::new(ui.available_width(), 36.0))
                .fill(COLOR_PRIMARY)
                .rounding(Rounding::same(50.0));

                if ui.add(done_btn).clicked() {
                    let mut s = app.state.lock().unwrap();
                    s.show_popup_modal = false;
                }
            });
        });
}

// -----------------------------------------------------------------------------
// Official Material 3 Expressive Circular Progress Dial with Rounded Caps & Gap
// -----------------------------------------------------------------------------
fn render_m3_battery_dial(
    ui: &mut egui::Ui,
    label: &str,
    battery: i32,
    is_charging: bool,
    in_ear: bool,
    active_color: Color32,
) {
    ui.vertical_centered(|ui| {
        let (rect, _response) = ui.allocate_exact_size(Vec2::new(76.0, 76.0), egui::Sense::hover());
        let painter = ui.painter();
        let center = rect.center();
        let radius = 30.0_f32;
        let stroke_w = 5.5_f32;

        let pct = if battery < 0 { 0.0 } else { (battery as f32 / 100.0).clamp(0.0, 1.0) };

        let start_angle = -PI / 2.0;
        let sweep_angle = pct * 2.0 * PI;
        let gap_angle = if pct > 0.05 && pct < 0.95 { 0.14_f32 } else { 0.0_f32 };

        let track_start = start_angle + sweep_angle + gap_angle;
        let track_sweep = (2.0 * PI - sweep_angle - gap_angle * 2.0).max(0.0);

        if track_sweep > 0.05 {
            draw_smooth_arc(painter, center, radius, track_start, track_start + track_sweep, stroke_w, COLOR_SURFACE_HIGHEST);
        }

        let stop_dot_center = center + Vec2::new(0.0, -radius);
        painter.circle_filled(stop_dot_center, stroke_w * 0.45, COLOR_SURFACE_HIGHEST);

        let arc_color = if is_charging {
            COLOR_CHARGING
        } else if battery < 20 {
            COLOR_ERROR
        } else {
            active_color
        };

        if sweep_angle > 0.05 {
            draw_smooth_arc(painter, center, radius, start_angle, start_angle + sweep_angle, stroke_w, arc_color);
        }

        let battery_str = if battery >= 0 { format!("{}%", battery) } else { "--".to_string() };
        painter.text(
            center - Vec2::new(0.0, 4.0),
            egui::Align2::CENTER_CENTER,
            battery_str,
            FontId::proportional(14.0),
            COLOR_TEXT_PRIMARY,
        );

        if is_charging {
            painter.text(
                center + Vec2::new(0.0, 12.0),
                egui::Align2::CENTER_CENTER,
                "⚡",
                FontId::proportional(11.0),
                COLOR_CHARGING,
            );
        } else if in_ear {
            painter.text(
                center + Vec2::new(0.0, 12.0),
                egui::Align2::CENTER_CENTER,
                "👂",
                FontId::proportional(10.0),
                COLOR_TEXT_SECONDARY,
            );
        }

        ui.add_space(4.0);

        let status_pill = if is_charging {
            "⚡ Charging"
        } else if in_ear {
            "In Ear"
        } else {
            "Ready"
        };

        ui.label(
            egui::RichText::new(label)
                .size(12.0)
                .strong()
                .color(COLOR_TEXT_PRIMARY),
        );
        ui.label(
            egui::RichText::new(status_pill)
                .size(10.5)
                .color(if is_charging { COLOR_CHARGING } else { COLOR_TEXT_SECONDARY }),
        );
    });
}

fn draw_smooth_arc(
    painter: &egui::Painter,
    center: Pos2,
    radius: f32,
    start_rad: f32,
    end_rad: f32,
    stroke_w: f32,
    color: Color32,
) {
    let steps = 36;
    let mut points = Vec::with_capacity(steps + 1);
    for i in 0..=steps {
        let t = start_rad + (end_rad - start_rad) * (i as f32 / steps as f32);
        points.push(center + Vec2::new(radius * t.cos(), radius * t.sin()));
    }

    if let (Some(first), Some(last)) = (points.first(), points.last()) {
        painter.circle_filled(*first, stroke_w / 2.0, color);
        painter.circle_filled(*last, stroke_w / 2.0, color);
    }
    painter.add(egui::Shape::line(points, Stroke::new(stroke_w, color)));
}

fn pill_badge(ui: &mut egui::Ui, text: &str, bg: Color32, fg: Color32, font_size: f32) {
    egui::Frame::none()
        .fill(bg)
        .rounding(Rounding::same(50.0))
        .inner_margin(egui::Margin::symmetric(10.0, 4.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new(text)
                    .size(font_size)
                    .strong()
                    .color(fg),
            );
        });
}
