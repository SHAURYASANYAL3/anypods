use std::f32::consts::PI;
use eframe::egui::{
    self, Color32, FontId, Pos2, Rect, Rounding, Stroke, Vec2,
};
use crate::state::{
    AncIntensity, EqPreset, NoiseMode, SharedState, SpatialAudioMode,
};

pub struct AnyPodsDesktopApp {
    state: SharedState,
    hero_texture: Option<egui::TextureHandle>,
    selected_brand: String,
}

impl AnyPodsDesktopApp {
    pub fn new(state: SharedState) -> Self {
        Self {
            state,
            hero_texture: None,
            selected_brand: "Apple".to_string(),
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

        // Keep UI reactive and buttery-smooth
        ctx.request_repaint_after(std::time::Duration::from_millis(300));

        let state_clone = {
            let s = self.state.lock().unwrap();
            s.clone()
        };

        // Material 3 Dark Palette Configuration
        let mut style = (*ctx.style()).clone();
        style.visuals.dark_mode = true;
        style.visuals.panel_fill = COLOR_BG;
        style.visuals.override_text_color = Some(COLOR_TEXT_PRIMARY);
        ctx.set_style(style);

        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical()
                .auto_shrink([false; 2])
                .show(ui, |ui| {
                    ui.add_space(6.0);

                    // ==========================================
                    // 1. TOP APP BAR (Material 3 Expressive)
                    // ==========================================
                    ui.horizontal(|ui| {
                        ui.label(
                            egui::RichText::new("🎧 AnyPods Pro")
                                .size(21.0)
                                .strong()
                                .color(COLOR_TEXT_PRIMARY),
                        );

                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            // Version / Engine Badge
                            pill_badge(
                                ui,
                                "⚡ v1.3.2 Studio",
                                COLOR_PRIMARY_CONTAINER,
                                COLOR_ON_PRIMARY_CONTAINER,
                                11.0,
                            );
                        });
                    });

                    ui.add_space(2.0);
                    ui.label(
                        egui::RichText::new("Universal AirPods Superpowers & Studio DSP Hub")
                            .size(12.5)
                            .color(COLOR_TEXT_SECONDARY),
                    );

                    ui.add_space(14.0);

                    // ==========================================
                    // 2. HERO CARD (Connected Device Showcase)
                    // ==========================================
                    render_hero_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // ==========================================
                    // 3. HARDWARE NOISE CONTROL
                    // ==========================================
                    render_noise_control_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // ==========================================
                    // 4. 3D SPATIAL AUDIO & HARMAN DSP
                    // ==========================================
                    render_spatial_dsp_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // ==========================================
                    // 5. IN-EAR DETECTION & AUTOMATION
                    // ==========================================
                    render_in_ear_card(self, ui, &state_clone);

                    ui.add_space(14.0);

                    // ==========================================
                    // 6. FIND MY EARBUDS & DISTANCE RADAR
                    // ==========================================
                    render_find_my_radar_card(self, ui, &state_clone);

                    ui.add_space(20.0);
                });
        });
    }
}

// -----------------------------------------------------------------------------
// Component: Hero Card with Earbuds Photo & M3 Battery Dials
// -----------------------------------------------------------------------------
fn render_hero_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(24.0))
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
                let img_height = 130.0;
                let (rect, _response) = ui.allocate_exact_size(Vec2::new(ui.available_width(), img_height), egui::Sense::hover());
                let painter = ui.painter();
                let center = rect.center();

                // Soft radial ambient glow behind earbuds
                let glow_radius = 65.0;
                painter.circle_filled(
                    center,
                    glow_radius,
                    Color32::from_rgba_unmultiplied(56, 189, 248, 22),
                );

                if let Some(texture) = &app.hero_texture {
                    let aspect_ratio = texture.size()[0] as f32 / texture.size()[1] as f32;
                    let target_h = 120.0;
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
                        .size(20.0)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );
                ui.label(
                    egui::RichText::new("Apple H2 Chip • AAC-ELD • 48 kHz Studio Stereo")
                        .size(11.5)
                        .color(COLOR_TEXT_SECONDARY),
                );

                ui.add_space(10.0);

                // Brand Selector Chips
                ui.horizontal_wrapped(|ui| {
                    let brands = ["All", "Apple", "Samsung", "Sony", "Nothing", "Beats"];
                    for brand in brands {
                        let is_sel = app.selected_brand == brand;
                        let btn = egui::Button::new(
                            egui::RichText::new(brand)
                                .size(11.0)
                                .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                                .strong(),
                        )
                        .fill(if is_sel { COLOR_PRIMARY } else { COLOR_SURFACE_HIGHEST })
                        .rounding(Rounding::same(50.0));

                        if ui.add(btn).clicked() {
                            app.selected_brand = brand.to_string();
                        }
                    }
                });

                ui.add_space(16.0);

                // 3 Distinct M3 Battery Dials (Left, Case, Right)
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

        let start_angle = -PI / 2.0; // 12 o'clock
        let sweep_angle = pct * 2.0 * PI;
        let gap_angle = if pct > 0.05 && pct < 0.95 { 0.14_f32 } else { 0.0_f32 };

        // 1. Draw Inactive Background Track Arc with rounded caps
        let track_start = start_angle + sweep_angle + gap_angle;
        let track_sweep = (2.0 * PI - sweep_angle - gap_angle * 2.0).max(0.0);

        if track_sweep > 0.05 {
            draw_smooth_arc(painter, center, radius, track_start, track_start + track_sweep, stroke_w, COLOR_SURFACE_HIGHEST);
        }

        // 2. Stop Indicator Dot at 100% position (-90°)
        let stop_dot_center = center + Vec2::new(0.0, -radius);
        painter.circle_filled(stop_dot_center, stroke_w * 0.45, COLOR_SURFACE_HIGHEST);

        // 3. Draw Active Progress Arc
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

        // Center Battery Text & Live Icon
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

        // Subtitle Status Pill
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

// Draw buttery anti-aliased circular arc with rounded caps
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

// -----------------------------------------------------------------------------
// Component: Hardware Noise Control Card with ANC Strength
// -----------------------------------------------------------------------------
fn render_noise_control_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(24.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("Noise Control")
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
                egui::RichText::new("Hardware ANC and environmental transparency switching.")
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
                            }
                        }
                    });
                });

            // Cancellation Strength Sub-Chips (when ANC active)
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
                        }
                    }
                });
            }
        });
}

// -----------------------------------------------------------------------------
// Component: 3D Spatial Audio & Harman Studio Acoustics Card
// -----------------------------------------------------------------------------
fn render_spatial_dsp_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(24.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("3D Spatial Audio & Harman DSP")
                    .size(16.5)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.label(
                egui::RichText::new("Binaural acoustic virtualizer and reference Harman target EQ.")
                    .size(12.0)
                    .color(COLOR_TEXT_SECONDARY),
            );

            ui.add_space(10.0);

            // Spatial Audio Modes
            ui.horizontal(|ui| {
                let modes = [
                    (SpatialAudioMode::Off, "○ Off"),
                    (SpatialAudioMode::Fixed, "🎧 Fixed Studio"),
                    (SpatialAudioMode::Spatial3D, "🌐 Spatial 3D"),
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
                    }
                }
            });

            ui.add_space(12.0);

            // Harman EQ Preset Chips
            ui.label(
                egui::RichText::new("Acoustic Target Presets")
                    .size(13.0)
                    .strong()
                    .color(COLOR_TEXT_PRIMARY),
            );
            ui.add_space(6.0);

            ui.horizontal_wrapped(|ui| {
                let presets = [
                    (EqPreset::HarmanTarget, "Harman Curve (+6dB Bass)"),
                    (EqPreset::VocalRange, "Vocal Range"),
                    (EqPreset::BassBoost, "Sub-Bass Punch"),
                    (EqPreset::Brightness, "Acoustic Strings"),
                    (EqPreset::StudioFlat, "Flat Reference"),
                ];

                for (preset, label) in presets {
                    let is_sel = state.eq_preset == preset;
                    let btn = egui::Button::new(
                        egui::RichText::new(label)
                            .size(11.0)
                            .color(if is_sel { Color32::WHITE } else { COLOR_TEXT_SECONDARY })
                            .strong(),
                    )
                    .fill(if is_sel { COLOR_PRIMARY_CONTAINER } else { COLOR_SURFACE_HIGH })
                    .stroke(Stroke::new(1.0_f32, if is_sel { COLOR_PRIMARY } else { COLOR_OUTLINE }))
                    .rounding(Rounding::same(12.0));

                    if ui.add(btn).clicked() {
                        let mut s = app.state.lock().unwrap();
                        s.eq_preset = preset;
                    }
                }
            });

            ui.add_space(14.0);

            // Stereo Channel Balance Slider
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("Stereo Channel Balance")
                        .size(13.0)
                        .strong()
                        .color(COLOR_TEXT_PRIMARY),
                );

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let bal_txt = if state.lr_balance < -0.05 {
                        format!("Left {}%", (-state.lr_balance * 100.0) as i32)
                    } else if state.lr_balance > 0.05 {
                        format!("Right {}%", (state.lr_balance * 100.0) as i32)
                    } else {
                        "Center".to_string()
                    };
                    pill_badge(ui, &bal_txt, COLOR_SURFACE_HIGHEST, COLOR_PRIMARY, 11.0);
                });
            });

            let mut balance = state.lr_balance;
            if ui.add(egui::Slider::new(&mut balance, -1.0..=1.0).show_value(false)).changed() {
                let mut s = app.state.lock().unwrap();
                s.lr_balance = balance;
            }
        });
}

// -----------------------------------------------------------------------------
// Component: In-Ear Optical Detection & Smart Automation Card
// -----------------------------------------------------------------------------
fn render_in_ear_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(24.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new("In-Ear Optical Detection")
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

            // Live Bud Ear Status Indicators
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

            ui.add_space(12.0);

            // Toggles
            let mut auto_pause = state.auto_pause_enabled;
            if ui.checkbox(&mut auto_pause, "Auto-Pause & Resume on Earbud Removal").changed() {
                let mut s = app.state.lock().unwrap();
                s.auto_pause_enabled = auto_pause;
            }

            let mut low_batt = state.low_battery_alert_enabled;
            if ui.checkbox(&mut low_batt, "Low Battery Audio Chimes (< 20%)").changed() {
                let mut s = app.state.lock().unwrap();
                s.low_battery_alert_enabled = low_batt;
            }
        });
}

// -----------------------------------------------------------------------------
// Component: Sub-Meter Find My Radar & Audio Siren Card
// -----------------------------------------------------------------------------
fn render_find_my_radar_card(app: &mut AnyPodsDesktopApp, ui: &mut egui::Ui, state: &crate::state::PodState) {
    egui::Frame::none()
        .fill(COLOR_SURFACE_CONTAINER)
        .rounding(Rounding::same(24.0))
        .stroke(Stroke::new(1.0_f32, COLOR_OUTLINE))
        .inner_margin(egui::Margin::same(18.0))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("Sub-Meter Find My Radar")
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

            // Radar Visualizer Box
            let (radar_rect, _resp) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 90.0), egui::Sense::hover());
            let painter = ui.painter();
            let center = radar_rect.center();

            // Concentric radar circles
            for r in [20.0_f32, 40.0, 60.0, 80.0] {
                painter.circle_stroke(
                    center,
                    r,
                    Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(56, 189, 248, 40)),
                );
            }

            // Central pulsing pod dot
            painter.circle_filled(center, 7.0, COLOR_PRIMARY);
            painter.circle_stroke(center, 12.0, Stroke::new(1.5_f32, COLOR_PRIMARY));
            painter.text(
                center + Vec2::new(0.0, 18.0),
                egui::Align2::CENTER_CENTER,
                format!("RSSI: {} dBm", state.radar_rssi),
                FontId::proportional(11.0),
                COLOR_PRIMARY,
            );

            ui.add_space(14.0);

            // Piercing Siren Audio Trigger Button
            let siren_active = state.siren_active;
            let btn_txt = if siren_active {
                "⏹ Stop Finding Siren"
            } else {
                "🚨 Play +25dB Finding Siren (Dual-Tone)"
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
                let mut s = app.state.lock().unwrap();
                s.siren_active = !s.siren_active;
                println!("[AnyPods Desktop] Find My Siren toggled: {}", s.siren_active);
            }
        });
}

// Pill badge helper
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
