//! Everything Evolve keeps: settings, the exercise library, routines, timer
//! programs, finished workouts and body weights. Serialized as JSON (the
//! database file and the export format are the same shape), so fields are
//! only ever added, with defaults, never renamed.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum WorkoutKind {
    Lifting,
    Walk,
    Run,
    Hike,
    Bike,
    StationaryBike,
    Meditation,
    Breathing,
}

impl WorkoutKind {
    pub fn label(self) -> &'static str {
        match self {
            WorkoutKind::Lifting => "Weights",
            WorkoutKind::Walk => "Walk",
            WorkoutKind::Run => "Run",
            WorkoutKind::Hike => "Hike",
            WorkoutKind::Bike => "Bike ride",
            WorkoutKind::StationaryBike => "Stationary bike",
            WorkoutKind::Meditation => "Meditation",
            WorkoutKind::Breathing => "Breathing",
        }
    }

    /// Walks, runs, hikes and rides follow the GPS.
    pub fn uses_gps(self) -> bool {
        matches!(self, WorkoutKind::Walk | WorkoutKind::Run | WorkoutKind::Hike | WorkoutKind::Bike)
    }

    pub const ALL: [WorkoutKind; 8] = [
        WorkoutKind::Lifting,
        WorkoutKind::Walk,
        WorkoutKind::Run,
        WorkoutKind::Hike,
        WorkoutKind::Bike,
        WorkoutKind::StationaryBike,
        WorkoutKind::Meditation,
        WorkoutKind::Breathing,
    ];
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum Equipment {
    Barbell,
    Dumbbell,
    Kettlebell,
    Machine,
    Cable,
    Bodyweight,
    Other,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum Intensity {
    Rest,
    Easy,
    Moderate,
    Hard,
    Max,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum WeightUnit {
    Kg,
    Lb,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum DistanceUnit {
    Km,
    Mi,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
#[serde(default)]
pub struct Settings {
    /// The look's name (see the app's Theme.kt).
    pub theme: String,
    pub weight_unit: WeightUnit,
    pub distance_unit: DistanceUnit,
    /// Keep the screen on while a workout is under way (each workout can
    /// change it for itself).
    pub keep_screen_on: bool,
    /// Export everything to `export_uri` as soon as a workout is saved.
    pub auto_export: bool,
    /// The folder exports go to: an Android document tree URI.
    pub export_uri: String,
    /// Rest between sets for exercises new to a routine.
    pub default_rest_secs: u32,
    /// How far around a GPS workout the map is kept for offline use.
    pub map_cache_miles: f64,
    pub breath_count: u32,
    /// The hold after the recovery breath.
    pub breath_recovery_secs: u32,
    /// Seconds per breath for the pacing guide; 0 hides it.
    pub breath_pace_secs: f64,
    pub meditation_minutes: u32,
    pub sounds: bool,
    pub vibrate: bool,
    /// No longer used: weeks always start on Sunday. Kept so old files still read.
    pub week_start_day: u32,
    /// How many automatic exports are kept in the export folder.
    pub auto_export_keep: u32,
    /// When the map is dark: by the light sensor (indoors and at night), or always light or dark.
    pub map_mode: MapMode,
    /// No longer used: the light map is always Bright. Kept so old files still read.
    pub map_light_style: String,
    /// No longer used: the dark map is always Midnight.
    pub map_dark_style: String,
    /// How strongly text stands out from the background, whatever the theme.
    pub contrast: Contrast,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum MapMode {
    Auto,
    Light,
    Dark,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum Contrast {
    Standard,
    High,
    Maximum,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            theme: "Lotus".into(),
            weight_unit: WeightUnit::Lb,
            distance_unit: DistanceUnit::Mi,
            keep_screen_on: true,
            auto_export: false,
            export_uri: String::new(),
            default_rest_secs: 90,
            map_cache_miles: 10.0,
            breath_count: 40,
            breath_recovery_secs: 10,
            breath_pace_secs: 3.0,
            meditation_minutes: 10,
            sounds: true,
            vibrate: true,
            week_start_day: 7,
            auto_export_keep: 10,
            map_mode: MapMode::Auto,
            map_light_style: "Bright".into(),
            map_dark_style: "Midnight".into(),
            contrast: Contrast::Standard,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct Exercise {
    pub id: String,
    pub name: String,
    pub equipment: Equipment,
    #[serde(default)]
    pub notes: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct SetPlan {
    pub reps: u32,
    pub weight_kg: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct RoutineExercise {
    pub exercise_id: String,
    pub sets: Vec<SetPlan>,
    pub rest_secs: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct Routine {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub notes: String,
    pub exercises: Vec<RoutineExercise>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TimerInterval {
    pub label: String,
    pub secs: u32,
    pub intensity: Intensity,
}

/// Intervals played in order, `repeats` times over.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TimerBlock {
    pub repeats: u32,
    pub intervals: Vec<TimerInterval>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TimerProgram {
    pub id: String,
    pub name: String,
    pub blocks: Vec<TimerBlock>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct SetLog {
    pub target_reps: u32,
    pub reps: u32,
    pub weight_kg: f64,
    pub done: bool,
    #[serde(default)]
    pub done_at_ms: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct ExerciseLog {
    pub exercise_id: String,
    pub name: String,
    pub equipment: Equipment,
    pub rest_secs: u32,
    pub sets: Vec<SetLog>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct LiftingLog {
    #[serde(default)]
    pub routine_id: Option<String>,
    pub routine_name: String,
    pub exercises: Vec<ExerciseLog>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct GpsLog {
    pub distance_m: f64,
    pub elevation_gain_m: f64,
    pub moving_secs: f64,
    pub start_lat: f64,
    pub start_lon: f64,
    pub point_count: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum TimerMode {
    /// Counts up until stopped.
    Stopwatch,
    /// One countdown.
    Countdown,
    /// A [TimerProgram] of intervals.
    Program,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TimerLog {
    pub mode: TimerMode,
    pub program_name: String,
    pub intervals_done: u32,
    pub intervals_total: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct BreathRound {
    pub breaths: u32,
    pub hold_secs: f64,
    pub recovery_secs: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct BreathLog {
    pub rounds: Vec<BreathRound>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct Workout {
    pub id: String,
    pub kind: WorkoutKind,
    pub name: String,
    pub started_at_ms: i64,
    pub duration_secs: f64,
    #[serde(default)]
    pub notes: String,
    /// Entered by hand after the fact (no timer ran).
    #[serde(default)]
    pub manual: bool,
    /// A distance typed in (stationary bike, a manual entry).
    #[serde(default)]
    pub distance_m: Option<f64>,
    #[serde(default)]
    pub lifting: Option<LiftingLog>,
    #[serde(default)]
    pub gps: Option<GpsLog>,
    #[serde(default)]
    pub timer: Option<TimerLog>,
    #[serde(default)]
    pub breath: Option<BreathLog>,
}

impl Workout {
    /// The distance covered, from the GPS or as typed in.
    pub fn distance(&self) -> Option<f64> {
        self.gps.as_ref().map(|g| g.distance_m).or(self.distance_m)
    }

    /// Weight × reps over every set done.
    pub fn volume_kg(&self) -> f64 {
        self.lifting
            .as_ref()
            .map(|l| {
                l.exercises
                    .iter()
                    .flat_map(|e| e.sets.iter())
                    .filter(|s| s.done)
                    .map(|s| s.weight_kg * s.reps as f64)
                    .sum()
            })
            .unwrap_or(0.0)
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct BodyWeight {
    pub id: String,
    pub at_ms: i64,
    pub weight_kg: f64,
    #[serde(default)]
    pub note: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TrackPoint {
    pub lat: f64,
    pub lon: f64,
    #[serde(default)]
    pub alt: Option<f64>,
    pub t_ms: i64,
    #[serde(default)]
    pub accuracy: f64,
    /// A new segment starts after each pause: the trail isn't joined across it.
    #[serde(default)]
    pub seg: u32,
}

/// The whole database: what `data.json` holds, without the GPS tracks
/// (kept a file per workout), and what an export holds, with them.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Database {
    pub version: u32,
    pub settings: Settings,
    pub exercises: Vec<Exercise>,
    pub routines: Vec<Routine>,
    pub programs: Vec<TimerProgram>,
    pub workouts: Vec<Workout>,
    pub body_weights: Vec<BodyWeight>,
}

pub const DB_VERSION: u32 = 1;

pub const LB_PER_KG: f64 = 2.204_622_621_8;
pub const M_PER_MI: f64 = 1609.344;
