//! Numbers as people read them, in the units they chose.

use crate::model::*;

pub fn duration(secs: f64) -> String {
    let s = secs.max(0.0).round() as u64;
    let (h, m, s) = (s / 3600, s % 3600 / 60, s % 60);
    if h > 0 {
        format!("{h}:{m:02}:{s:02}")
    } else {
        format!("{m}:{s:02}")
    }
}

pub fn weight_value(kg: f64, unit: WeightUnit) -> f64 {
    match unit {
        WeightUnit::Kg => kg,
        WeightUnit::Lb => kg * LB_PER_KG,
    }
}

pub fn weight_from(value: f64, unit: WeightUnit) -> f64 {
    match unit {
        WeightUnit::Kg => value,
        WeightUnit::Lb => value / LB_PER_KG,
    }
}

pub fn unit_label(unit: WeightUnit) -> &'static str {
    match unit {
        WeightUnit::Kg => "kg",
        WeightUnit::Lb => "lb",
    }
}

/// "135 lb", "62.5 kg": a tenth at most, no trailing ".0".
pub fn weight(kg: f64, unit: WeightUnit) -> String {
    format!("{} {}", number(weight_value(kg, unit), 1), unit_label(unit))
}

pub fn number(v: f64, decimals: usize) -> String {
    let s = format!("{v:.decimals$}");
    if s.contains('.') {
        s.trim_end_matches('0').trim_end_matches('.').to_string()
    } else {
        s
    }
}

/// Thousands separated: 12,450.
pub fn grouped(v: f64) -> String {
    let n = v.round() as i64;
    let digits = n.abs().to_string();
    let mut out = String::new();
    for (i, c) in digits.chars().enumerate() {
        if i > 0 && (digits.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }
    if n < 0 {
        format!("-{out}")
    } else {
        out
    }
}

pub fn distance_value(m: f64, unit: DistanceUnit) -> f64 {
    match unit {
        DistanceUnit::Km => m / 1000.0,
        DistanceUnit::Mi => m / M_PER_MI,
    }
}

pub fn distance_label(unit: DistanceUnit) -> &'static str {
    match unit {
        DistanceUnit::Km => "km",
        DistanceUnit::Mi => "mi",
    }
}

pub fn distance(m: f64, unit: DistanceUnit) -> String {
    format!("{:.2} {}", distance_value(m, unit), distance_label(unit))
}

/// Minutes per km or mile, "8:45 /mi".
pub fn pace(m: f64, secs: f64, unit: DistanceUnit) -> String {
    let d = distance_value(m, unit);
    if d < 0.01 || secs <= 0.0 {
        return format!("–:–– /{}", distance_label(unit));
    }
    format!("{} /{}", duration(secs / d), distance_label(unit))
}

pub fn speed(m: f64, secs: f64, unit: DistanceUnit) -> String {
    if secs <= 0.0 {
        return format!("0.0 {}/h", if unit == DistanceUnit::Km { "km" } else { "mi" });
    }
    let per_h = distance_value(m, unit) / (secs / 3600.0);
    format!("{per_h:.1} {}", if unit == DistanceUnit::Km { "km/h" } else { "mph" })
}

pub fn elevation(m: f64, unit: DistanceUnit) -> String {
    match unit {
        DistanceUnit::Km => format!("{} m", m.round()),
        DistanceUnit::Mi => format!("{} ft", (m * 3.28084).round()),
    }
}

/// The short line under a workout in the history.
pub fn headline(w: &Workout, s: &Settings) -> String {
    let dur = duration(w.duration_secs);
    match w.kind {
        WorkoutKind::Lifting => {
            let sets = w
                .lifting
                .as_ref()
                .map(|l| l.exercises.iter().flat_map(|e| &e.sets).filter(|s| s.done).count())
                .unwrap_or(0);
            let vol = weight_value(w.volume_kg(), s.weight_unit);
            format!("{dur} · {sets} sets · {} {}", grouped(vol), unit_label(s.weight_unit))
        }
        WorkoutKind::Walk
        | WorkoutKind::Run
        | WorkoutKind::Hike
        | WorkoutKind::Bike
        | WorkoutKind::StationaryBike
        | WorkoutKind::Boat
        | WorkoutKind::Drive => {
            match w.distance() {
                Some(d) if d > 0.0 => {
                    let rate = if w.kind.uses_speed() {
                        speed(d, w.gps.as_ref().map(|g| g.moving_secs).filter(|m| *m > 0.0).unwrap_or(w.duration_secs), s.distance_unit)
                    } else {
                        pace(d, w.gps.as_ref().map(|g| g.moving_secs).filter(|m| *m > 0.0).unwrap_or(w.duration_secs), s.distance_unit)
                    };
                    match w.steps() {
                        Some(n) => format!("{} · {dur} · {rate} · {} steps", distance(d, s.distance_unit), grouped(n as f64)),
                        None => format!("{} · {dur} · {rate}", distance(d, s.distance_unit)),
                    }
                }
                _ => match &w.timer {
                    Some(t) if t.mode == TimerMode::Program => format!("{dur} · {}", t.program_name),
                    _ => dur,
                },
            }
        }
        WorkoutKind::Meditation => dur,
        WorkoutKind::Breathing => {
            let rounds = w.breath.as_ref().map(|b| b.rounds.len()).unwrap_or(0);
            let best = w.breath.as_ref().map(|b| b.rounds.iter().map(|r| r.hold_secs).fold(0.0, f64::max)).unwrap_or(0.0);
            format!("{rounds} rounds · best hold {}", duration(best))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats() {
        assert_eq!(duration(3733.0), "1:02:13");
        assert_eq!(duration(59.6), "1:00");
        assert_eq!(grouped(12450.0), "12,450");
        assert_eq!(grouped(999.0), "999");
        assert_eq!(weight(weight_from(135.0, WeightUnit::Lb), WeightUnit::Lb), "135 lb");
        assert_eq!(weight(62.5, WeightUnit::Kg), "62.5 kg");
        assert_eq!(pace(1609.344, 525.0, DistanceUnit::Mi), "8:45 /mi");
    }
}
