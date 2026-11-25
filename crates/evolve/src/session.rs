//! The workout under way: what it is, how long it has run (less pauses),
//! and each kind's own progress. Kept in `session.json` after every change
//! so a workout survives the app being closed.

use serde::{Deserialize, Serialize};

use crate::geo;
use crate::model::*;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct LiftingSession {
    pub routine_id: Option<String>,
    pub routine_name: String,
    pub exercises: Vec<ExerciseLog>,
    /// The exercise shown first.
    pub current: u32,
    /// When the rest after the last set ends.
    pub rest_until_ms: Option<i64>,
    pub rest_total_secs: u32,
}

#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize, uniffi::Record)]
pub struct GpsLive {
    pub distance_m: f64,
    pub elevation_gain_m: f64,
    pub moving_secs: f64,
    pub points: u32,
    pub start_lat: Option<f64>,
    pub start_lon: Option<f64>,
    pub last_lat: Option<f64>,
    pub last_lon: Option<f64>,
    /// Where the workout started, by name ("Central Park"), once found.
    pub place_name: String,
    /// The current segment (one more after each pause).
    pub seg: u32,
    /// The altitude elevation gain is measured from (hysteresis).
    pub ele_ref: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TimerSession {
    pub mode: TimerMode,
    pub program: Option<TimerProgram>,
    pub countdown_secs: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
#[serde(rename_all = "snake_case")]
pub enum BreathPhase {
    /// Deep breaths, as many as the round asks.
    Breathing,
    /// Breath out and held: the stopwatch runs.
    Hold,
    /// The recovery breath, held for a few seconds.
    Recovery,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct BreathSession {
    pub phase: BreathPhase,
    pub phase_started_ms: i64,
    /// 1-based.
    pub round: u32,
    pub rounds: Vec<BreathRound>,
    pub target_breaths: u32,
    pub recovery_secs: u32,
    pub pace_secs: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct MeditationSession {
    pub target_secs: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct Session {
    pub kind: WorkoutKind,
    pub started_at_ms: i64,
    pub paused_at_ms: Option<i64>,
    /// Time spent paused, not counting a pause still going.
    pub paused_ms: i64,
    pub keep_screen_on: bool,
    pub lifting: Option<LiftingSession>,
    pub gps: Option<GpsLive>,
    pub timer: Option<TimerSession>,
    pub breath: Option<BreathSession>,
    pub meditation: Option<MeditationSession>,
}

impl Session {
    fn new(kind: WorkoutKind, now_ms: i64, keep_screen_on: bool) -> Session {
        Session {
            kind,
            started_at_ms: now_ms,
            paused_at_ms: None,
            paused_ms: 0,
            keep_screen_on,
            lifting: None,
            gps: None,
            timer: None,
            breath: None,
            meditation: None,
        }
    }

    pub fn lifting(routine: Option<&Routine>, library: &[Exercise], default_rest: u32, now_ms: i64, screen: bool) -> Session {
        let mut s = Session::new(WorkoutKind::Lifting, now_ms, screen);
        let exercises = routine
            .map(|r| {
                r.exercises
                    .iter()
                    .filter_map(|re| {
                        let ex = library.iter().find(|e| e.id == re.exercise_id)?;
                        Some(ExerciseLog {
                            exercise_id: ex.id.clone(),
                            name: ex.name.clone(),
                            equipment: ex.equipment,
                            rest_secs: if re.rest_secs == 0 { default_rest } else { re.rest_secs },
                            sets: re
                                .sets
                                .iter()
                                .map(|p| SetLog { target_reps: p.reps, reps: p.reps, weight_kg: p.weight_kg, done: false, done_at_ms: None })
                                .collect(),
                        })
                    })
                    .collect()
            })
            .unwrap_or_default();
        s.lifting = Some(LiftingSession {
            routine_id: routine.map(|r| r.id.clone()),
            routine_name: routine.map(|r| r.name.clone()).unwrap_or_else(|| "Weights".into()),
            exercises,
            current: 0,
            rest_until_ms: None,
            rest_total_secs: 0,
        });
        s
    }

    pub fn gps(kind: WorkoutKind, now_ms: i64, screen: bool) -> Session {
        let mut s = Session::new(kind, now_ms, screen);
        s.gps = Some(GpsLive::default());
        s
    }

    pub fn timer(kind: WorkoutKind, mode: TimerMode, program: Option<TimerProgram>, countdown_secs: u32, now_ms: i64, screen: bool) -> Session {
        let mut s = Session::new(kind, now_ms, screen);
        s.timer = Some(TimerSession { mode, program, countdown_secs });
        s
    }

    pub fn meditation(target_secs: u32, now_ms: i64, screen: bool) -> Session {
        let mut s = Session::new(WorkoutKind::Meditation, now_ms, screen);
        s.meditation = Some(MeditationSession { target_secs });
        s
    }

    pub fn breathing(settings: &Settings, now_ms: i64, screen: bool) -> Session {
        let mut s = Session::new(WorkoutKind::Breathing, now_ms, screen);
        s.breath = Some(BreathSession {
            phase: BreathPhase::Breathing,
            phase_started_ms: now_ms,
            round: 1,
            rounds: vec![],
            target_breaths: settings.breath_count.max(1),
            recovery_secs: settings.breath_recovery_secs,
            pace_secs: settings.breath_pace_secs,
        });
        s
    }

    /// How long the workout has run, less pauses.
    pub fn elapsed_ms(&self, now_ms: i64) -> i64 {
        let paused_now = self.paused_at_ms.map(|p| (now_ms - p).max(0)).unwrap_or(0);
        (now_ms - self.started_at_ms - self.paused_ms - paused_now).max(0)
    }

    pub fn pause(&mut self, now_ms: i64) {
        if self.paused_at_ms.is_none() {
            self.paused_at_ms = Some(now_ms);
        }
    }

    pub fn resume(&mut self, now_ms: i64) {
        if let Some(p) = self.paused_at_ms.take() {
            self.paused_ms += (now_ms - p).max(0);
            if let Some(g) = self.gps.as_mut() {
                g.seg += 1;
            }
        }
    }

    // ------------------------------------------------------------ lifting

    pub fn complete_set(&mut self, ex: usize, set: usize, reps: u32, now_ms: i64) {
        let Some(l) = self.lifting.as_mut() else { return };
        let Some(e) = l.exercises.get_mut(ex) else { return };
        let rest = e.rest_secs;
        let Some(s) = e.sets.get_mut(set) else { return };
        s.reps = reps;
        s.done = true;
        s.done_at_ms = Some(now_ms);
        let exercise_done = e.sets.iter().all(|s| s.done);
        let all_done = l.exercises.iter().all(|e| e.sets.iter().all(|s| s.done));
        if all_done {
            l.rest_until_ms = None;
            return;
        }
        l.rest_until_ms = Some(now_ms + rest as i64 * 1000);
        l.rest_total_secs = rest;
        if exercise_done {
            // On to the next exercise with sets to do.
            let n = l.exercises.len();
            if let Some(next) = (1..=n).map(|k| (ex + k) % n).find(|&i| l.exercises[i].sets.iter().any(|s| !s.done)) {
                l.current = next as u32;
            }
        }
    }

    pub fn undo_set(&mut self, ex: usize, set: usize) {
        let Some(l) = self.lifting.as_mut() else { return };
        if let Some(s) = l.exercises.get_mut(ex).and_then(|e| e.sets.get_mut(set)) {
            s.done = false;
            s.done_at_ms = None;
            s.reps = s.target_reps;
        }
    }

    /// The weight of one set, or with `set` = None of every set not yet done.
    pub fn set_weight(&mut self, ex: usize, set: Option<usize>, weight_kg: f64) {
        let Some(e) = self.lifting.as_mut().and_then(|l| l.exercises.get_mut(ex)) else { return };
        let w = weight_kg.max(0.0);
        match set {
            Some(i) => {
                if let Some(s) = e.sets.get_mut(i) {
                    s.weight_kg = w;
                }
            }
            None => e.sets.iter_mut().filter(|s| !s.done).for_each(|s| s.weight_kg = w),
        }
    }

    pub fn set_target_reps(&mut self, ex: usize, set: usize, reps: u32) {
        if let Some(s) = self.lifting.as_mut().and_then(|l| l.exercises.get_mut(ex)).and_then(|e| e.sets.get_mut(set)) {
            s.target_reps = reps;
            if !s.done {
                s.reps = reps;
            }
        }
    }

    pub fn add_set(&mut self, ex: usize) {
        if let Some(e) = self.lifting.as_mut().and_then(|l| l.exercises.get_mut(ex)) {
            let last = e.sets.last().cloned().unwrap_or(SetLog { target_reps: 10, reps: 10, weight_kg: 0.0, done: false, done_at_ms: None });
            e.sets.push(SetLog { reps: last.target_reps, done: false, done_at_ms: None, ..last });
        }
    }

    pub fn remove_set(&mut self, ex: usize, set: usize) {
        if let Some(e) = self.lifting.as_mut().and_then(|l| l.exercises.get_mut(ex)) {
            if set < e.sets.len() {
                e.sets.remove(set);
            }
        }
    }

    pub fn add_exercise(&mut self, ex: &Exercise, rest_secs: u32) {
        if let Some(l) = self.lifting.as_mut() {
            l.exercises.push(ExerciseLog {
                exercise_id: ex.id.clone(),
                name: ex.name.clone(),
                equipment: ex.equipment,
                rest_secs,
                sets: (0..3).map(|_| SetLog { target_reps: 10, reps: 10, weight_kg: 0.0, done: false, done_at_ms: None }).collect(),
            });
            l.current = l.exercises.len() as u32 - 1;
        }
    }

    pub fn remove_exercise(&mut self, ex: usize) {
        if let Some(l) = self.lifting.as_mut() {
            if ex < l.exercises.len() {
                l.exercises.remove(ex);
                l.current = l.current.min(l.exercises.len().saturating_sub(1) as u32);
            }
        }
    }

    // ---------------------------------------------------------------- GPS

    /// Takes a GPS fix: returns it as it is to be kept in the trail, or None
    /// when it's too inaccurate or too close to the last one to count (GPS
    /// jitter while standing still mustn't add distance).
    pub fn add_point(&mut self, p: TrackPoint, last: Option<&TrackPoint>) -> Option<TrackPoint> {
        if self.paused_at_ms.is_some() {
            return None;
        }
        let g = self.gps.as_mut()?;
        if !(p.lat.is_finite() && p.lon.is_finite()) || p.accuracy > 40.0 {
            return None;
        }
        let p = TrackPoint { seg: g.seg, ..p };
        if g.start_lat.is_none() {
            g.start_lat = Some(p.lat);
            g.start_lon = Some(p.lon);
        }
        g.last_lat = Some(p.lat);
        g.last_lon = Some(p.lon);
        let accept = match last {
            None => true,
            Some(l) if l.seg != p.seg => true,
            Some(l) => {
                let d = geo::haversine_m(l.lat, l.lon, p.lat, p.lon);
                let needed = (p.accuracy.min(20.0) * 0.6).max(4.0);
                if d < needed {
                    false
                } else {
                    let dt = (p.t_ms - l.t_ms).max(0) as f64 / 1000.0;
                    // A jump faster than any human leg or bike is a bad fix.
                    if dt > 0.0 && d / dt > 40.0 {
                        return None;
                    }
                    g.distance_m += d;
                    // Long gaps (standing still, then moving) count at walking pace.
                    g.moving_secs += if dt <= 30.0 { dt } else { (d / 1.3).min(dt) };
                    true
                }
            }
        };
        if !accept {
            return None;
        }
        if let Some(alt) = p.alt {
            match g.ele_ref {
                None => g.ele_ref = Some(alt),
                Some(r) if alt - r > 4.0 => {
                    g.elevation_gain_m += alt - r;
                    g.ele_ref = Some(alt);
                }
                Some(r) if r - alt > 4.0 => g.ele_ref = Some(alt),
                _ => {}
            }
        }
        g.points += 1;
        Some(p)
    }

    // ------------------------------------------------------------ breath

    /// The button: done breathing → hold; can't hold any longer → recovery
    /// breath; recovery over → the next round.
    pub fn breath_advance(&mut self, now_ms: i64) {
        let Some(b) = self.breath.as_mut() else { return };
        match b.phase {
            BreathPhase::Breathing => b.phase = BreathPhase::Hold,
            BreathPhase::Hold => {
                b.rounds.push(BreathRound {
                    breaths: b.target_breaths,
                    hold_secs: (now_ms - b.phase_started_ms).max(0) as f64 / 1000.0,
                    recovery_secs: b.recovery_secs,
                });
                b.phase = BreathPhase::Recovery;
            }
            BreathPhase::Recovery => {
                b.phase = BreathPhase::Breathing;
                b.round += 1;
            }
        }
        b.phase_started_ms = now_ms;
    }
}

// ------------------------------------------------------------------ timer

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct FlatStep {
    pub label: String,
    pub secs: u32,
    pub intensity: Intensity,
    /// Which pass through its block this is (1-based), of `reps`.
    pub rep: u32,
    pub reps: u32,
}

/// A program's intervals in the order they play, blocks repeated.
pub fn flatten(program: &TimerProgram) -> Vec<FlatStep> {
    let mut out = vec![];
    for b in &program.blocks {
        let reps = b.repeats.max(1);
        for rep in 1..=reps {
            for i in &b.intervals {
                if i.secs == 0 {
                    continue;
                }
                out.push(FlatStep { label: i.label.clone(), secs: i.secs, intensity: i.intensity, rep, reps });
            }
        }
    }
    out
}

pub fn program_secs(program: &TimerProgram) -> u64 {
    flatten(program).iter().map(|s| s.secs as u64).sum()
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TimerStatus {
    pub mode: TimerMode,
    pub elapsed_ms: i64,
    /// The interval playing (0-based) of `count`.
    pub index: u32,
    pub count: u32,
    pub label: String,
    pub intensity: Intensity,
    pub step_remaining_ms: i64,
    pub step_total_ms: i64,
    pub total_remaining_ms: i64,
    pub total_ms: i64,
    pub rep: u32,
    pub reps: u32,
    pub next_label: Option<String>,
    pub finished: bool,
}

pub fn timer_status(t: &TimerSession, elapsed_ms: i64) -> TimerStatus {
    let base = TimerStatus {
        mode: t.mode,
        elapsed_ms,
        index: 0,
        count: 1,
        label: String::new(),
        intensity: Intensity::Moderate,
        step_remaining_ms: 0,
        step_total_ms: 0,
        total_remaining_ms: 0,
        total_ms: 0,
        rep: 1,
        reps: 1,
        next_label: None,
        finished: false,
    };
    match t.mode {
        TimerMode::Stopwatch => TimerStatus { label: "Ride".into(), ..base },
        TimerMode::Countdown => {
            let total = t.countdown_secs as i64 * 1000;
            let left = (total - elapsed_ms).max(0);
            TimerStatus {
                label: "Ride".into(),
                step_remaining_ms: left,
                step_total_ms: total,
                total_remaining_ms: left,
                total_ms: total,
                finished: left == 0,
                ..base
            }
        }
        TimerMode::Program => {
            let steps = t.program.as_ref().map(flatten).unwrap_or_default();
            let total: i64 = steps.iter().map(|s| s.secs as i64 * 1000).sum();
            let count = steps.len() as u32;
            let mut at = 0i64;
            for (i, s) in steps.iter().enumerate() {
                let len = s.secs as i64 * 1000;
                if elapsed_ms < at + len {
                    return TimerStatus {
                        index: i as u32,
                        count,
                        label: s.label.clone(),
                        intensity: s.intensity,
                        step_remaining_ms: at + len - elapsed_ms,
                        step_total_ms: len,
                        total_remaining_ms: total - elapsed_ms,
                        total_ms: total,
                        rep: s.rep,
                        reps: s.reps,
                        next_label: steps.get(i + 1).map(|n| n.label.clone()),
                        ..base
                    };
                }
                at += len;
            }
            TimerStatus {
                index: count,
                count,
                label: "Done".into(),
                intensity: Intensity::Rest,
                total_ms: total,
                finished: true,
                ..base
            }
        }
    }
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BreathStatus {
    pub phase: BreathPhase,
    pub round: u32,
    pub phase_elapsed_ms: i64,
    /// Breaths so far by the pacing guide (0 without it).
    pub breaths_guided: u32,
    /// 0..1 through the current breath: in for the first half, out for the second.
    pub breath_progress: f64,
    pub recovery_remaining_ms: i64,
    pub best_hold_secs: f64,
    pub last_hold_secs: Option<f64>,
}

pub fn breath_status(b: &BreathSession, now_ms: i64) -> BreathStatus {
    let el = (now_ms - b.phase_started_ms).max(0);
    let pace_ms = (b.pace_secs * 1000.0) as i64;
    let (guided, progress) = if b.phase == BreathPhase::Breathing && pace_ms > 0 {
        (((el / pace_ms) as u32).min(b.target_breaths), (el % pace_ms) as f64 / pace_ms as f64)
    } else {
        (0, 0.0)
    };
    BreathStatus {
        phase: b.phase,
        round: b.round,
        phase_elapsed_ms: el,
        breaths_guided: guided,
        breath_progress: progress,
        recovery_remaining_ms: if b.phase == BreathPhase::Recovery { (b.recovery_secs as i64 * 1000 - el).max(0) } else { 0 },
        best_hold_secs: b.rounds.iter().map(|r| r.hold_secs).fold(0.0, f64::max),
        last_hold_secs: b.rounds.last().map(|r| r.hold_secs),
    }
}
