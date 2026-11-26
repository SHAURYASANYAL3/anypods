use eframe::egui::{self, Color32, FontId, Rounding, Stroke, Vec2};
use crate::state::{NoiseMode, SharedState};

pub struct AnyPodsDesktopApp {
    state: SharedState,
    is_beeping: bool,
}

impl AnyPodsDesktopApp {
    pub fn new(state: SharedState) -> Self {
        Self {
            state,
            is_beeping: false,
        }
    }
}

impl eframe::App for AnyPodsDesktopApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        // Request repaint to keep battery and status live
        ctx.request_repaint_after(std::time::Duration::from_millis(500));

        let state_clone = {
            let s = self.state.lock().unwrap();
            s.clone()
        };

        let mut style = (*ctx.style()).clone();
        style.visuals.dark_mode = true;
        style.visuals.override_text_color = Some(Color32::from_rgb(240, 240, 245));
        style.visuals.panel_fill = Color32::from_rgb(18, 18, 22);
        ctx.set_style(style);

        egui::CentralPanel::default().show(ctx, |ui| {
            ui.add_space(8.0);

            // Title & Status Bar
            ui.horizontal(|ui| {
                ui.label(egui::RichText::new("🎧 AnyPods Pro").size(22.0).strong().color(Color32::WHITE));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let (status_text, status_color) = if state_clone.is_connected {
                        ("● Connected", Color32::from_rgb(52, 199, 89))
                    } else {
                        ("○ Searching...", Color32::from_rgb(142, 142, 147))
                    };
                    ui.label(egui::RichText::new(status_text).size(12.0).color(status_color).strong());
                });
            });

            ui.add_space(4.0);
            ui.label(
                egui::RichText::new(format!("{} • Cross-Platform Studio Hub", state_clone.name))
                    .size(13.0)
                    .color(Color32::from_rgb(160, 160, 175)),
            );

            ui.add_space(14.0);

            // 1. Triple Battery Dials Card
            ui.group(|ui| {
                ui.set_width(ui.available_width());
                ui.vertical(|ui| {
                    ui.add_space(6.0);
                    ui.horizontal(|ui| {
                        render_battery_dial(
                            ui,
                            "Left Pod",
                            state_clone.left_battery,
                            state_clone.is_left_charging,
                            state_clone.is_left_in_ear,
                            Color32::from_rgb(10, 132, 255),
                        );

                        render_battery_dial(
                            ui,
                            "Case",
                            state_clone.case_battery,
                            state_clone.is_case_charging,
                            false,
                            Color32::from_rgb(191, 90, 242),
                        );

                        render_battery_dial(
                            ui,
                            "Right Pod",
                            state_clone.right_battery,
                            state_clone.is_right_charging,
                            state_clone.is_right_in_ear,
                            Color32::from_rgb(48, 209, 88),
                        );
                    });
                    ui.add_space(6.0);
                });
            });

            ui.add_space(12.0);

            // 2. Hardware Noise Control Selector
            ui.group(|ui| {
                ui.set_width(ui.available_width());
                ui.vertical(|ui| {
                    ui.label(egui::RichText::new("Hardware Noise Control").size(15.0).strong());
                    ui.add_space(6.0);

                    ui.horizontal(|ui| {
                        let current_mode = state_clone.noise_mode;

                        let modes = [
                            (NoiseMode::NoiseCancellation, "🔇 Noise Cancellation"),
                            (NoiseMode::Transparency, "🔊 Transparency"),
                            (NoiseMode::Adaptive, "⚡ Adaptive"),
                            (NoiseMode::Off, "○ Off"),
                        ];

                        for (mode, label) in modes {
                            let is_selected = current_mode == mode;
                            let btn = egui::Button::new(
                                egui::RichText::new(label)
                                    .size(12.0)
                                    .color(if is_selected { Color32::WHITE } else { Color32::from_rgb(180, 180, 195) })
                                    .strong(),
                            )
                            .fill(if is_selected { Color32::from_rgb(0, 122, 255) } else { Color32::from_rgb(34, 34, 42) })
                            .rounding(Rounding::same(8.0));

                            if ui.add(btn).clicked() {
                                let mut s = self.state.lock().unwrap();
                                s.noise_mode = mode;
                                println!("[AnyPods Desktop] Switched Noise Mode to: {:?}", mode);
                            }
                        }
                    });
                });
            });

            ui.add_space(12.0);

            // 3. In-Ear Detection & Smart Automation Card
            ui.group(|ui| {
                ui.set_width(ui.available_width());
                ui.vertical(|ui| {
                    ui.label(egui::RichText::new("In-Ear Detection & Media Automation").size(15.0).strong());
                    ui.add_space(4.0);
                    ui.label(
                        egui::RichText::new("Auto-pauses Spotify, YouTube & media when an earbud is removed, resumes when inserted.")
                            .size(11.5)
                            .color(Color32::from_rgb(160, 160, 175)),
                    );
                    ui.add_space(6.0);

                    let mut auto_pause = state_clone.auto_pause_enabled;
                    if ui.checkbox(&mut auto_pause, "Enable In-Ear Auto-Pause / Auto-Resume").changed() {
                        let mut s = self.state.lock().unwrap();
                        s.auto_pause_enabled = auto_pause;
                    }
                });
            });

            ui.add_space(12.0);

            // 4. Find My Earbuds Chime Trigger
            ui.group(|ui| {
                ui.set_width(ui.available_width());
                ui.vertical(|ui| {
                    ui.label(egui::RichText::new("Find My Earbuds Siren").size(15.0).strong());
                    ui.add_space(4.0);

                    let siren_text = if self.is_beeping { "⏹ Stop Finding Siren" } else { "🔔 Play Finding Siren" };
                    let siren_btn = egui::Button::new(egui::RichText::new(siren_text).size(13.0).strong().color(Color32::WHITE))
                        .fill(if self.is_beeping { Color32::from_rgb(255, 69, 58) } else { Color32::from_rgb(10, 132, 255) })
                        .rounding(Rounding::same(8.0));

                    if ui.add(siren_btn).clicked() {
                        self.is_beeping = !self.is_beeping;
                        println!("[AnyPods Desktop] Toggled Find My Siren: {}", self.is_beeping);
                    }
                });
            });
        });
    }
}

fn render_battery_dial(
    ui: &mut egui::Ui,
    label: &str,
    battery: i32,
    is_charging: bool,
    in_ear: bool,
    accent: Color32,
) {
    ui.vertical_centered(|ui| {
        let (rect, _response) = ui.allocate_exact_size(Vec2::new(90.0, 90.0), egui::Sense::hover());
        let painter = ui.painter();
        let center = rect.center();
        let radius = 34.0;

        // Outer ring with battery level accent
        let arc_color = if battery < 20 && !is_charging {
            Color32::from_rgb(255, 69, 58)
        } else {
            accent
        };
        painter.circle_stroke(center, radius, Stroke::new(4.0_f32, arc_color));

        // Inner fill
        painter.circle_filled(center, radius - 4.0_f32, Color32::from_rgb(24, 24, 30));

        // Battery percentage text
        let pct_text = if battery >= 0 {
            format!("{}%", battery)
        } else {
            "--".to_string()
        };
        painter.text(
            center - Vec2::new(0.0, 4.0),
            egui::Align2::CENTER_CENTER,
            pct_text,
            FontId::proportional(15.0),
            Color32::WHITE,
        );

        if is_charging {
            painter.text(
                center + Vec2::new(0.0, 12.0),
                egui::Align2::CENTER_CENTER,
                "⚡",
                FontId::proportional(12.0),
                Color32::from_rgb(255, 214, 10),
            );
        } else if in_ear {
            painter.text(
                center + Vec2::new(0.0, 12.0),
                egui::Align2::CENTER_CENTER,
                "👂",
                FontId::proportional(11.0),
                Color32::from_rgb(160, 160, 180),
            );
        }

        ui.add_space(4.0);
        ui.label(egui::RichText::new(label).size(12.0).strong().color(Color32::from_rgb(210, 210, 220)));
    });
}
