//! The Graphs tab's series: one value per workout (or per week, or per
//! weigh-in), in the units chosen.

use crate::format::{distance_label, distance_value, unit_label, weight_value};
use crate::model::*;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Metric {
    BodyWeight,
    /// Weight × reps of each weights workout.
    LiftingVolume,
    /// The heaviest set of one exercise, each time it was done.
    ExerciseMaxWeight,
    /// Estimated one-rep max (Epley) of one exercise.
    ExerciseOneRepMax,
    ExerciseVolume,
    ExerciseReps,
    Distance,
    /// Minutes per km or mile.
    Pace,
    /// km/h or mph.
    Speed,
    Duration,
    ElevationGain,
    BreathMaxHold,
    BreathAverageHold,
    MeditationMinutes,
    WorkoutsPerWeek,
    MinutesPerWeek,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GraphQuery {
    pub metric: Metric,
    /// For the Exercise* metrics.
    pub exercise_id: Option<String>,
    /// For Distance, Pace, Speed, Duration, ElevationGain: only these kinds (empty: all that apply).
    pub kinds: Vec<WorkoutKind>,
    /// Only from this time on.
    pub since_ms: Option<i64>,
    /// Minutes east of UTC, for where weeks begin.
    pub utc_offset_min: i32,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GraphPoint {
    pub t_ms: i64,
    pub value: f64,
    /// The workout the point is from, if one.
    pub workout_id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GraphSeries {
    pub title: String,
    pub unit: String,
    /// Values are times in seconds, to show as m:ss.
    pub is_duration: bool,
    pub points: Vec<GraphPoint>,
}

fn epley(weight: f64, reps: u32) -> f64 {
    if reps == 0 {
        0.0
    } else if reps == 1 {
        weight
    } else {
        weight * (1.0 + reps as f64 / 30.0)
    }
}

fn sets_of<'a>(w: &'a Workout, exercise_id: &'a str) -> impl Iterator<Item = &'a SetLog> + 'a {
    w.lifting
        .iter()
        .flat_map(|l| l.exercises.iter())
        .filter(move |e| e.exercise_id == exercise_id)
        .flat_map(|e| e.sets.iter())
        .filter(|s| s.done && s.reps > 0)
}

pub fn graph(db: &Database, q: &GraphQuery) -> GraphSeries {
    let s = &db.settings;
    let wu = s.weight_unit;
    let du = s.distance_unit;
    let since = q.since_ms.unwrap_or(i64::MIN);
    let mut workouts: Vec<&Workout> = db.workouts.iter().filter(|w| w.started_at_ms >= since).collect();
    workouts.sort_by_key(|w| w.started_at_ms);
    let kind_ok = |w: &Workout, default: &dyn Fn(WorkoutKind) -> bool| {
        if q.kinds.is_empty() {
            default(w.kind)
        } else {
            q.kinds.contains(&w.kind)
        }
    };
    let moving = |w: &Workout| w.gps.as_ref().map(|g| g.moving_secs).filter(|m| *m > 0.0).unwrap_or(w.duration_secs);
    let per_workout = |f: &dyn Fn(&Workout) -> Option<f64>| -> Vec<GraphPoint> {
        workouts
            .iter()
            .filter_map(|w| f(w).map(|v| GraphPoint { t_ms: w.started_at_ms, value: v, workout_id: Some(w.id.clone()) }))
            .collect()
    };
    let distance_kinds = |k: WorkoutKind| k.uses_gps() || k == WorkoutKind::StationaryBike;
    let ex = q.exercise_id.clone().unwrap_or_default();
    let ex_name = db.exercises.iter().find(|e| e.id == ex).map(|e| e.name.clone()).unwrap_or_default();
    let series = |title: String, unit: &str, points: Vec<GraphPoint>| GraphSeries { title, unit: unit.into(), is_duration: false, points };
    match q.metric {
        Metric::BodyWeight => {
            let mut pts: Vec<GraphPoint> = db
                .body_weights
                .iter()
                .filter(|b| b.at_ms >= since)
                .map(|b| GraphPoint { t_ms: b.at_ms, value: weight_value(b.weight_kg, wu), workout_id: None })
                .collect();
            pts.sort_by_key(|p| p.t_ms);
            series("Body weight".into(), unit_label(wu), pts)
        }
        Metric::LiftingVolume => series(
            "Volume per workout".into(),
            unit_label(wu),
            per_workout(&|w| (w.kind == WorkoutKind::Lifting).then(|| weight_value(w.volume_kg(), wu))),
        ),
        Metric::ExerciseMaxWeight => series(
            format!("{ex_name}: heaviest set"),
            unit_label(wu),
            per_workout(&|w| sets_of(w, &ex).map(|s| s.weight_kg).reduce(f64::max).map(|v| weight_value(v, wu))),
        ),
        Metric::ExerciseOneRepMax => series(
            format!("{ex_name}: estimated 1RM"),
            unit_label(wu),
            per_workout(&|w| sets_of(w, &ex).map(|s| epley(s.weight_kg, s.reps)).reduce(f64::max).map(|v| weight_value(v, wu))),
        ),
        Metric::ExerciseVolume => series(
            format!("{ex_name}: volume"),
            unit_label(wu),
            per_workout(&|w| {
                let mut it = sets_of(w, &ex).peekable();
                it.peek()?;
                Some(weight_value(it.map(|s| s.weight_kg * s.reps as f64).sum(), wu))
            }),
        ),
        Metric::ExerciseReps => series(
            format!("{ex_name}: total reps"),
            "reps",
            per_workout(&|w| {
                let mut it = sets_of(w, &ex).peekable();
                it.peek()?;
                Some(it.map(|s| s.reps as f64).sum())
            }),
        ),
        Metric::Distance => series(
            "Distance".into(),
            distance_label(du),
            per_workout(&|w| {
                if !kind_ok(w, &distance_kinds) {
                    return None;
                }
                w.distance().filter(|d| *d > 0.0).map(|d| distance_value(d, du))
            }),
        ),
        Metric::Pace => GraphSeries {
            title: "Pace".into(),
            unit: format!("/{}", distance_label(du)),
            is_duration: true,
            points: per_workout(&|w| {
                if !kind_ok(w, &|k| matches!(k, WorkoutKind::Walk | WorkoutKind::Run | WorkoutKind::Hike)) {
                    return None;
                }
                let d = distance_value(w.distance()?, du);
                (d > 0.05).then(|| moving(w) / d)
            }),
        },
        Metric::Speed => series(
            "Average speed".into(),
            if du == DistanceUnit::Km { "km/h" } else { "mph" },
            per_workout(&|w| {
                if !kind_ok(w, &|k| matches!(k, WorkoutKind::Bike | WorkoutKind::StationaryBike)) {
                    return None;
                }
                let d = distance_value(w.distance()?, du);
                let t = moving(w);
                (d > 0.05 && t > 0.0).then(|| d / (t / 3600.0))
            }),
        ),
        Metric::Duration => series(
            "Duration".into(),
            "min",
            per_workout(&|w| kind_ok(w, &|_| true).then(|| w.duration_secs / 60.0)),
        ),
        Metric::ElevationGain => series(
            "Elevation gain".into(),
            if du == DistanceUnit::Km { "m" } else { "ft" },
            per_workout(&|w| {
                if !kind_ok(w, &|k| k.uses_gps()) {
                    return None;
                }
                let g = w.gps.as_ref()?.elevation_gain_m;
                Some(if du == DistanceUnit::Km { g } else { g * 3.28084 })
            }),
        ),
        Metric::BreathMaxHold => GraphSeries {
            title: "Longest breath hold".into(),
            unit: String::new(),
            is_duration: true,
            points: per_workout(&|w| w.breath.as_ref().and_then(|b| b.rounds.iter().map(|r| r.hold_secs).reduce(f64::max))),
        },
        Metric::BreathAverageHold => GraphSeries {
            title: "Average breath hold".into(),
            unit: String::new(),
            is_duration: true,
            points: per_workout(&|w| {
                let b = w.breath.as_ref()?;
                (!b.rounds.is_empty()).then(|| b.rounds.iter().map(|r| r.hold_secs).sum::<f64>() / b.rounds.len() as f64)
            }),
        },
        Metric::MeditationMinutes => series(
            "Meditation".into(),
            "min",
            per_workout(&|w| (w.kind == WorkoutKind::Meditation).then(|| w.duration_secs / 60.0)),
        ),
        Metric::WorkoutsPerWeek | Metric::MinutesPerWeek => {
            let offset = q.utc_offset_min as i64 * 60_000;
            const WEEK: i64 = 7 * 86_400_000;
            // 1970-01-01 was a Thursday (ISO day 4): the first week start
            // on or before it is this many days earlier.
            let start = db.settings.week_start_day.clamp(1, 7) as i64;
            let week0 = -((4 - start).rem_euclid(7)) * 86_400_000i64;
            let week_of = |t: i64| (t + offset - week0).div_euclid(WEEK);
            let mut weeks: Vec<(i64, f64)> = vec![];
            for w in &workouts {
                let k = week_of(w.started_at_ms);
                let v = if q.metric == Metric::WorkoutsPerWeek { 1.0 } else { w.duration_secs / 60.0 };
                match weeks.last_mut() {
                    Some((wk, total)) if *wk == k => *total += v,
                    _ => {
                        // Empty weeks between count as zero.
                        if let Some((last, _)) = weeks.last().copied() {
                            for e in last + 1..k {
                                weeks.push((e, 0.0));
                            }
                        }
                        weeks.push((k, v));
                    }
                }
            }
            let pts = weeks
                .into_iter()
                .map(|(k, v)| GraphPoint { t_ms: k * WEEK + week0 - offset, value: v, workout_id: None })
                .collect();
            if q.metric == Metric::WorkoutsPerWeek {
                series("Workouts per week".into(), "workouts", pts)
            } else {
                series("Minutes per week".into(), "min", pts)
            }
        }
    }
}
