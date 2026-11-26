/// Apple AirPods Bluetooth Low Energy (BLE) Beacon Parser.
/// Reverse engineered Apple Manufacturer Data format (ID 0x004C).

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AppleBeaconInfo {
    pub model_id: String,
    pub model_name: String,
    pub left_battery: i32,
    pub right_battery: i32,
    pub case_battery: i32,
    pub is_left_charging: bool,
    pub is_right_charging: bool,
    pub is_case_charging: bool,
    pub is_left_in_ear: bool,
    pub is_right_in_ear: bool,
    pub raw_hex: String,
}

pub struct AppleBeaconParser;

impl AppleBeaconParser {
    pub const APPLE_MANUFACTURER_ID: u16 = 76; // 0x004C

    pub fn parse(manufacturer_data: &[u8]) -> Option<AppleBeaconInfo> {
        if manufacturer_data.len() < 16 {
            return None;
        }

        let hex = manufacturer_data
            .iter()
            .map(|b| format!("{:02X}", b))
            .collect::<String>();

        Self::parse_hex(&hex)
    }

    pub fn parse_hex(hex: &str) -> Option<AppleBeaconInfo> {
        if hex.len() < 16 {
            return None;
        }

        let offset = if hex.starts_with("0719") {
            Some(0)
        } else {
            hex.find("0719")
        };

        let status = match offset {
            Some(idx) if hex.len() >= idx + 16 => &hex[idx..],
            _ => hex,
        };

        if status.len() < 16 {
            return None;
        }

        let chars: Vec<char> = status.chars().collect();
        if chars.len() < 16 {
            return None;
        }

        let nibble = |c: char| c.to_digit(16).map(|v| v as u8);

        let flip_nibble = nibble(chars[10])?;
        let is_flipped = (flip_nibble & 0x02) == 0;

        let left_raw = if is_flipped {
            nibble(chars[12])?
        } else {
            nibble(chars[13])?
        };

        let right_raw = if is_flipped {
            nibble(chars[13])?
        } else {
            nibble(chars[12])?
        };

        let case_raw = nibble(chars[15])?;

        // 0-10 nibbles to 0-100% battery (10 = 100%, 0-9 = value * 10 + 5; 15 = disconnected)
        let to_battery = |raw: u8| -> i32 {
            if raw <= 10 {
                if raw == 10 {
                    100
                } else {
                    (raw as i32 * 10 + 5).clamp(0, 100)
                }
            } else {
                -1
            }
        };

        let left_battery = to_battery(left_raw);
        let right_battery = to_battery(right_raw);
        let case_battery = to_battery(case_raw);

        let charge_status = nibble(chars[14])?;
        let charge_l = if is_flipped {
            (charge_status & 0b0010) != 0
        } else {
            (charge_status & 0b0001) != 0
        };
        let charge_r = if is_flipped {
            (charge_status & 0b0001) != 0
        } else {
            (charge_status & 0b0010) != 0
        };
        let charge_case = (charge_status & 0b0100) != 0;

        let in_ear_status = nibble(chars[11])?;
        let in_ear_l = if is_flipped {
            (in_ear_status & 0b1000) != 0
        } else {
            (in_ear_status & 0b0010) != 0
        };
        let in_ear_r = if is_flipped {
            (in_ear_status & 0b0010) != 0
        } else {
            (in_ear_status & 0b1000) != 0
        };

        let id_full = if status.len() >= 10 {
            status[6..10].to_uppercase()
        } else {
            String::new()
        };

        let id_single = if status.len() >= 8 {
            chars[7].to_ascii_uppercase()
        } else {
            ' '
        };

        let (model_id, model_name) = match id_full.as_str() {
            "0220" => ("airpods_1", "AirPods (1st Gen)"),
            "0F20" => ("airpods_2", "AirPods (2nd Gen)"),
            "1320" => ("airpods_3", "AirPods (3rd Gen)"),
            "1B20" => ("airpods_4", "AirPods 4"),
            "1C20" => ("airpods_4_anc", "AirPods 4 with ANC"),
            "0E20" => ("airpods_pro_1", "AirPods Pro (1st Gen)"),
            "1420" | "2420" | "2720" => ("airpods_pro_2", "AirPods Pro 2"),
            _ if id_single == 'A' || id_full.starts_with("0A") => ("airpods_max", "AirPods Max"),
            _ if id_single == 'B' || id_full == "0520" || id_full == "1020" => {
                ("beats_studio_buds", "Beats Studio Buds")
            }
            _ => ("airpods_pro_2", "AirPods Pro"),
        };

        Some(AppleBeaconInfo {
            model_id: model_id.to_string(),
            model_name: model_name.to_string(),
            left_battery,
            right_battery,
            case_battery,
            is_left_charging: charge_l,
            is_right_charging: charge_r,
            is_case_charging: charge_case,
            is_left_in_ear: in_ear_l,
            is_right_in_ear: in_ear_r,
            raw_hex: status.to_string(),
        })
    }
}
