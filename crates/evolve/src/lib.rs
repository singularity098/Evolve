//! Evolve's engine. The Android app (Jetpack Compose) drives it through the
//! [Engine] object, which UniFFI exposes to Kotlin. It keeps everything in
//! a folder of JSON files:
//!
//! * `data.json`: settings, exercises, routines, programs, workouts, weights
//! * `tracks/<workout id>.json`: each GPS workout's trail
//! * `session.json` and `session-track.ndjson`: the workout under way
//!
//! Every change is written at once (to a temporary file, then renamed), so
//! nothing is lost if Android ends the app.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

pub mod format;
pub mod geo;
pub mod model;
pub mod seed;
pub mod session;
pub mod stats;

pub use model::*;
pub use session::*;
pub use stats::*;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EvolveError {
    #[error("{msg}")]
    Io { msg: String },
    #[error("{msg}")]
    BadData { msg: String },
    #[error("{msg}")]
    NoSession { msg: String },
}

impl From<std::io::Error> for EvolveError {
    fn from(e: std::io::Error) -> Self {
        EvolveError::Io { msg: e.to_string() }
    }
}

impl From<serde_json::Error> for EvolveError {
    fn from(e: serde_json::Error) -> Self {
        EvolveError::BadData { msg: e.to_string() }
    }
}

type Result<T> = std::result::Result<T, EvolveError>;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Library {
    pub settings: Settings,
    pub exercises: Vec<Exercise>,
    pub routines: Vec<Routine>,
    pub programs: Vec<TimerProgram>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WorkoutSummary {
    pub id: String,
    pub kind: WorkoutKind,
    pub name: String,
    pub started_at_ms: i64,
    pub duration_secs: f64,
    pub headline: String,
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct HistoryFilter {
    /// Only these kinds; empty for all.
    pub kinds: Vec<WorkoutKind>,
    /// Only names or notes containing this (any case).
    pub query: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GpsUpdate {
    /// The fix as kept in the trail, or None if it didn't count.
    pub kept: Option<TrackPoint>,
    pub live: GpsLive,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ImportResult {
    pub workouts: u32,
    pub body_weights: u32,
    pub exercises: u32,
    pub routines: u32,
    pub programs: u32,
}

/// An export: the database with every track in it.
#[derive(Debug, Serialize, Deserialize)]
struct ExportFile {
    app: String,
    exported_at_ms: i64,
    #[serde(flatten)]
    db: Database,
    #[serde(default)]
    tracks: HashMap<String, Vec<TrackPoint>>,
}

struct Inner {
    dir: PathBuf,
    db: Database,
    session: Option<Session>,
    /// The workout under way's trail.
    track: Vec<TrackPoint>,
}

#[derive(uniffi::Object)]
pub struct Engine {
    inner: Mutex<Inner>,
}

fn now_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

static ID_COUNTER: AtomicU64 = AtomicU64::new(0);

/// Unique enough across devices too: time, a counter, and the clock's nanoseconds.
pub fn new_id() -> String {
    let nanos = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.subsec_nanos()).unwrap_or(0);
    let n = ID_COUNTER.fetch_add(1, Ordering::Relaxed);
    format!("{:x}-{:x}{:x}", now_ms(), nanos & 0xffff, n & 0xfff)
}

fn write_atomic(path: &Path, bytes: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = path.with_extension("tmp");
    {
        let mut f = fs::File::create(&tmp)?;
        f.write_all(bytes)?;
        f.sync_all()?;
    }
    fs::rename(&tmp, path)?;
    Ok(())
}

impl Inner {
    fn data_path(&self) -> PathBuf {
        self.dir.join("data.json")
    }
    fn session_path(&self) -> PathBuf {
        self.dir.join("session.json")
    }
    fn session_track_path(&self) -> PathBuf {
        self.dir.join("session-track.ndjson")
    }
    fn track_path(&self, id: &str) -> PathBuf {
        let safe: String = id.chars().filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_').collect();
        self.dir.join("tracks").join(format!("{safe}.json"))
    }

    fn save(&self) -> Result<()> {
        write_atomic(&self.data_path(), &serde_json::to_vec(&self.db)?)
    }

    fn save_session(&self) -> Result<()> {
        match &self.session {
            Some(s) => write_atomic(&self.session_path(), &serde_json::to_vec(s)?),
            None => {
                let _ = fs::remove_file(self.session_path());
                let _ = fs::remove_file(self.session_track_path());
                Ok(())
            }
        }
    }

    fn load_track(&self, id: &str) -> Vec<TrackPoint> {
        fs::read(self.track_path(id)).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
    }

    fn session_mut(&mut self) -> Result<&mut Session> {
        self.session.as_mut().ok_or(EvolveError::NoSession { msg: "No workout is under way".into() })
    }

    fn start(&mut self, s: Session) -> Result<Session> {
        self.track.clear();
        let _ = fs::remove_file(self.session_track_path());
        self.session = Some(s.clone());
        self.save_session()?;
        Ok(s)
    }

    fn summary(&self, w: &Workout) -> WorkoutSummary {
        WorkoutSummary {
            id: w.id.clone(),
            kind: w.kind,
            name: w.name.clone(),
            started_at_ms: w.started_at_ms,
            duration_secs: w.duration_secs,
            headline: format::headline(w, &self.db.settings),
        }
    }

    fn insert_workout(&mut self, w: Workout) {
        let at = self.db.workouts.partition_point(|x| x.started_at_ms > w.started_at_ms);
        self.db.workouts.insert(at, w);
    }
}

#[uniffi::export]
impl Engine {
    /// Opens (or starts) the data in `dir`, the app's private files folder.
    #[uniffi::constructor]
    pub fn new(dir: String) -> Result<Arc<Engine>> {
        let dir = PathBuf::from(dir);
        fs::create_dir_all(dir.join("tracks"))?;
        let mut inner = Inner { dir, db: Database::default(), session: None, track: vec![] };
        let data = inner.data_path();
        inner.db = if data.exists() {
            let bytes = fs::read(&data)?;
            match serde_json::from_slice::<Database>(&bytes) {
                Ok(db) => db,
                Err(e) => {
                    // Keep the unreadable file aside rather than lose it.
                    let _ = fs::copy(&data, inner.dir.join(format!("data-unreadable-{}.json", now_ms())));
                    return Err(EvolveError::BadData { msg: format!("data.json could not be read: {e}") });
                }
            }
        } else {
            let db = seed::database();
            inner.db = db.clone();
            inner.save()?;
            db
        };
        inner.db.workouts.sort_by(|a, b| b.started_at_ms.cmp(&a.started_at_ms));
        if let Ok(bytes) = fs::read(inner.session_path()) {
            inner.session = serde_json::from_slice(&bytes).ok();
            if inner.session.is_some() {
                if let Ok(text) = fs::read_to_string(inner.session_track_path()) {
                    inner.track = text.lines().filter_map(|l| serde_json::from_str(l).ok()).collect();
                }
            }
        }
        Ok(Arc::new(Engine { inner: Mutex::new(inner) }))
    }

    // ------------------------------------------------------------ library

    pub fn library(&self) -> Library {
        let g = self.lock();
        Library {
            settings: g.db.settings.clone(),
            exercises: g.db.exercises.clone(),
            routines: g.db.routines.clone(),
            programs: g.db.programs.clone(),
        }
    }

    pub fn set_settings(&self, settings: Settings) -> Result<()> {
        let mut g = self.lock();
        g.db.settings = settings;
        g.save()
    }

    /// Adds the exercise (an empty id gets one) or replaces the one with its id.
    pub fn save_exercise(&self, exercise: Exercise) -> Result<Exercise> {
        let mut g = self.lock();
        let mut e = exercise;
        if e.id.is_empty() {
            e.id = new_id();
        }
        match g.db.exercises.iter_mut().find(|x| x.id == e.id) {
            Some(x) => *x = e.clone(),
            None => g.db.exercises.push(e.clone()),
        }
        g.db.exercises.sort_by_key(|x| x.name.to_lowercase());
        g.save()?;
        Ok(e)
    }

    /// Removes the exercise from the library and from routines (not from history).
    pub fn delete_exercise(&self, id: String) -> Result<()> {
        let mut g = self.lock();
        g.db.exercises.retain(|e| e.id != id);
        for r in &mut g.db.routines {
            r.exercises.retain(|e| e.exercise_id != id);
        }
        g.save()
    }

    pub fn save_routine(&self, routine: Routine) -> Result<Routine> {
        let mut g = self.lock();
        let mut r = routine;
        if r.id.is_empty() {
            r.id = new_id();
        }
        match g.db.routines.iter_mut().find(|x| x.id == r.id) {
            Some(x) => *x = r.clone(),
            None => g.db.routines.push(r.clone()),
        }
        g.save()?;
        Ok(r)
    }

    pub fn delete_routine(&self, id: String) -> Result<()> {
        let mut g = self.lock();
        g.db.routines.retain(|r| r.id != id);
        g.save()
    }

    pub fn save_program(&self, program: TimerProgram) -> Result<TimerProgram> {
        let mut g = self.lock();
        let mut p = program;
        if p.id.is_empty() {
            p.id = new_id();
        }
        match g.db.programs.iter_mut().find(|x| x.id == p.id) {
            Some(x) => *x = p.clone(),
            None => g.db.programs.push(p.clone()),
        }
        g.save()?;
        Ok(p)
    }

    pub fn delete_program(&self, id: String) -> Result<()> {
        let mut g = self.lock();
        g.db.programs.retain(|p| p.id != id);
        g.save()
    }

    // -------------------------------------------------------- body weight

    /// Newest first.
    pub fn body_weights(&self) -> Vec<BodyWeight> {
        let g = self.lock();
        let mut v = g.db.body_weights.clone();
        v.sort_by(|a, b| b.at_ms.cmp(&a.at_ms));
        v
    }

    pub fn add_body_weight(&self, at_ms: i64, weight_kg: f64, note: String) -> Result<BodyWeight> {
        let mut g = self.lock();
        let b = BodyWeight { id: new_id(), at_ms, weight_kg, note };
        g.db.body_weights.push(b.clone());
        g.save()?;
        Ok(b)
    }

    pub fn delete_body_weight(&self, id: String) -> Result<()> {
        let mut g = self.lock();
        g.db.body_weights.retain(|b| b.id != id);
        g.save()
    }

    // ------------------------------------------------------------ history

    /// Newest first.
    pub fn history(&self, filter: HistoryFilter) -> Vec<WorkoutSummary> {
        let g = self.lock();
        let q = filter.query.trim().to_lowercase();
        g.db
            .workouts
            .iter()
            .filter(|w| filter.kinds.is_empty() || filter.kinds.contains(&w.kind))
            .filter(|w| q.is_empty() || w.name.to_lowercase().contains(&q) || w.notes.to_lowercase().contains(&q))
            .map(|w| g.summary(w))
            .collect()
    }

    /// Workouts that started between the two times (for the calendar).
    pub fn workouts_between(&self, from_ms: i64, to_ms: i64) -> Vec<WorkoutSummary> {
        let g = self.lock();
        g.db.workouts.iter().filter(|w| w.started_at_ms >= from_ms && w.started_at_ms < to_ms).map(|w| g.summary(w)).collect()
    }

    pub fn workout(&self, id: String) -> Option<Workout> {
        self.lock().db.workouts.iter().find(|w| w.id == id).cloned()
    }

    pub fn track(&self, id: String) -> Vec<TrackPoint> {
        self.lock().load_track(&id)
    }

    pub fn update_workout(&self, id: String, name: String, notes: String) -> Result<()> {
        let mut g = self.lock();
        if let Some(w) = g.db.workouts.iter_mut().find(|w| w.id == id) {
            w.name = name;
            w.notes = notes;
        }
        g.save()
    }

    pub fn delete_workout(&self, id: String) -> Result<()> {
        let mut g = self.lock();
        g.db.workouts.retain(|w| w.id != id);
        let _ = fs::remove_file(g.track_path(&id));
        g.save()
    }

    /// A workout entered after the fact (a meditation timed elsewhere, say).
    pub fn log_manual(
        &self,
        kind: WorkoutKind,
        name: String,
        started_at_ms: i64,
        duration_secs: f64,
        distance_m: Option<f64>,
        notes: String,
    ) -> Result<String> {
        let mut g = self.lock();
        let id = new_id();
        let name = if name.trim().is_empty() { kind.label().to_string() } else { name };
        g.insert_workout(Workout {
            id: id.clone(),
            kind,
            name,
            started_at_ms,
            duration_secs,
            notes,
            manual: true,
            distance_m,
            lifting: None,
            gps: None,
            timer: None,
            breath: None,
        });
        g.save()?;
        Ok(id)
    }

    pub fn graph(&self, query: GraphQuery) -> GraphSeries {
        stats::graph(&self.lock().db, &query)
    }

    // ------------------------------------------------------------ session

    pub fn session(&self) -> Option<Session> {
        self.lock().session.clone()
    }

    /// The workout under way's trail so far.
    pub fn session_track(&self) -> Vec<TrackPoint> {
        self.lock().track.clone()
    }

    pub fn start_lifting(&self, routine_id: Option<String>, now_ms: i64) -> Result<Session> {
        let mut g = self.lock();
        let routine = routine_id.and_then(|id| g.db.routines.iter().find(|r| r.id == id).cloned());
        let s = Session::lifting(routine.as_ref(), &g.db.exercises, g.db.settings.default_rest_secs, now_ms, g.db.settings.keep_screen_on);
        g.start(s)
    }

    pub fn start_gps(&self, kind: WorkoutKind, now_ms: i64) -> Result<Session> {
        let mut g = self.lock();
        let s = Session::gps(kind, now_ms, g.db.settings.keep_screen_on);
        g.start(s)
    }

    pub fn start_timer(
        &self,
        kind: WorkoutKind,
        mode: TimerMode,
        program_id: Option<String>,
        countdown_secs: u32,
        now_ms: i64,
    ) -> Result<Session> {
        let mut g = self.lock();
        let program = program_id.and_then(|id| g.db.programs.iter().find(|p| p.id == id).cloned());
        let mode = if mode == TimerMode::Program && program.is_none() { TimerMode::Stopwatch } else { mode };
        let s = Session::timer(kind, mode, program, countdown_secs, now_ms, g.db.settings.keep_screen_on);
        g.start(s)
    }

    pub fn start_meditation(&self, target_secs: u32, now_ms: i64) -> Result<Session> {
        let mut g = self.lock();
        g.db.settings.meditation_minutes = (target_secs / 60).max(1);
        g.save()?;
        let s = Session::meditation(target_secs, now_ms, g.db.settings.keep_screen_on);
        g.start(s)
    }

    pub fn start_breathing(&self, now_ms: i64) -> Result<Session> {
        let mut g = self.lock();
        let s = Session::breathing(&g.db.settings, now_ms, g.db.settings.keep_screen_on);
        g.start(s)
    }

    pub fn pause(&self, now_ms: i64) -> Result<Session> {
        self.change(|s| s.pause(now_ms))
    }

    pub fn resume(&self, now_ms: i64) -> Result<Session> {
        self.change(|s| s.resume(now_ms))
    }

    pub fn set_session_screen_on(&self, on: bool) -> Result<Session> {
        self.change(|s| s.keep_screen_on = on)
    }

    pub fn complete_set(&self, exercise: u32, set: u32, reps: u32, now_ms: i64) -> Result<Session> {
        self.change(|s| s.complete_set(exercise as usize, set as usize, reps, now_ms))
    }

    pub fn undo_set(&self, exercise: u32, set: u32) -> Result<Session> {
        self.change(|s| s.undo_set(exercise as usize, set as usize))
    }

    /// One set's weight, or with `set` None every set of the exercise not yet done.
    pub fn set_weight(&self, exercise: u32, set: Option<u32>, weight_kg: f64) -> Result<Session> {
        self.change(|s| s.set_weight(exercise as usize, set.map(|v| v as usize), weight_kg))
    }

    pub fn set_target_reps(&self, exercise: u32, set: u32, reps: u32) -> Result<Session> {
        self.change(|s| s.set_target_reps(exercise as usize, set as usize, reps))
    }

    pub fn add_set(&self, exercise: u32) -> Result<Session> {
        self.change(|s| s.add_set(exercise as usize))
    }

    pub fn remove_set(&self, exercise: u32, set: u32) -> Result<Session> {
        self.change(|s| s.remove_set(exercise as usize, set as usize))
    }

    pub fn select_exercise(&self, exercise: u32) -> Result<Session> {
        self.change(|s| {
            if let Some(l) = s.lifting.as_mut() {
                l.current = exercise.min(l.exercises.len().saturating_sub(1) as u32);
            }
        })
    }

    pub fn add_session_exercise(&self, exercise_id: String) -> Result<Session> {
        let (ex, rest) = {
            let g = self.lock();
            (g.db.exercises.iter().find(|e| e.id == exercise_id).cloned(), g.db.settings.default_rest_secs)
        };
        let ex = ex.ok_or(EvolveError::BadData { msg: "No such exercise".into() })?;
        self.change(|s| s.add_exercise(&ex, rest))
    }

    pub fn remove_session_exercise(&self, exercise: u32) -> Result<Session> {
        self.change(|s| s.remove_exercise(exercise as usize))
    }

    /// Ends the rest early (`add_secs` 0) or makes it longer.
    pub fn adjust_rest(&self, add_secs: i32, now_ms: i64) -> Result<Session> {
        self.change(|s| {
            if let Some(l) = s.lifting.as_mut() {
                if add_secs == 0 {
                    l.rest_until_ms = None;
                } else if let Some(u) = l.rest_until_ms {
                    let nu = u.max(now_ms) + add_secs as i64 * 1000;
                    l.rest_until_ms = if nu <= now_ms { None } else { Some(nu) };
                    l.rest_total_secs = (l.rest_total_secs as i32 + add_secs).max(1) as u32;
                }
            }
        })
    }

    pub fn add_point(&self, point: TrackPoint) -> Result<GpsUpdate> {
        let mut g = self.lock();
        let last = g.track.last().cloned();
        let s = g.session_mut()?;
        let kept = s.add_point(point, last.as_ref());
        let live = s.gps.clone().unwrap_or_default();
        if let Some(p) = &kept {
            g.track.push(p.clone());
            let line = serde_json::to_string(p)? + "\n";
            let mut f = fs::OpenOptions::new().create(true).append(true).open(g.session_track_path())?;
            f.write_all(line.as_bytes())?;
            // The stats, now and then (the trail file has every point anyway).
            if live.points % 10 == 1 {
                g.save_session()?;
            }
        }
        Ok(GpsUpdate { kept, live })
    }

    pub fn set_place_name(&self, name: String) -> Result<Session> {
        self.change(|s| {
            if let Some(g) = s.gps.as_mut() {
                g.place_name = name;
            }
        })
    }

    pub fn timer_status(&self, now_ms: i64) -> Option<TimerStatus> {
        let g = self.lock();
        let s = g.session.as_ref()?;
        s.timer.as_ref().map(|t| session::timer_status(t, s.elapsed_ms(now_ms)))
    }

    pub fn breath_advance(&self, now_ms: i64) -> Result<Session> {
        self.change(|s| s.breath_advance(now_ms))
    }

    pub fn breath_status(&self, now_ms: i64) -> Option<BreathStatus> {
        let g = self.lock();
        g.session.as_ref()?.breath.as_ref().map(|b| session::breath_status(b, now_ms))
    }

    /// What the workout under way would be saved as.
    pub fn default_session_name(&self) -> String {
        let g = self.lock();
        let Some(s) = &g.session else { return String::new() };
        if let Some(l) = &s.lifting {
            return l.routine_name.clone();
        }
        if let Some(gps) = &s.gps {
            if !gps.place_name.is_empty() {
                return gps.place_name.clone();
            }
        }
        if let Some(t) = &s.timer {
            if let (TimerMode::Program, Some(p)) = (t.mode, &t.program) {
                return format!("{}: {}", s.kind.label(), p.name);
            }
        }
        s.kind.label().to_string()
    }

    /// Saves the workout under way to the history; returns its id.
    pub fn finish_session(
        &self,
        now_ms: i64,
        name: String,
        notes: String,
        distance_m: Option<f64>,
        update_routine: bool,
    ) -> Result<String> {
        let default_name = self.default_session_name();
        let mut g = self.lock();
        let mut s = g.session.clone().ok_or(EvolveError::NoSession { msg: "No workout is under way".into() })?;
        s.resume(now_ms);
        let elapsed = s.elapsed_ms(now_ms) as f64 / 1000.0;
        let id = new_id();
        let mut duration = elapsed;
        let lifting = s.lifting.as_ref().map(|l| LiftingLog {
            routine_id: l.routine_id.clone(),
            routine_name: l.routine_name.clone(),
            exercises: l.exercises.clone(),
        });
        let gps = s.gps.as_ref().map(|gl| GpsLog {
            distance_m: gl.distance_m,
            elevation_gain_m: gl.elevation_gain_m,
            moving_secs: gl.moving_secs,
            start_lat: gl.start_lat.unwrap_or(0.0),
            start_lon: gl.start_lon.unwrap_or(0.0),
            point_count: gl.points,
        });
        let timer = s.timer.as_ref().map(|t| {
            let st = session::timer_status(t, s.elapsed_ms(now_ms));
            if st.finished && t.mode != TimerMode::Stopwatch && st.total_ms > 0 {
                duration = st.total_ms as f64 / 1000.0;
            }
            TimerLog {
                mode: t.mode,
                program_name: t.program.as_ref().map(|p| p.name.clone()).unwrap_or_default(),
                intervals_done: st.index.min(st.count),
                intervals_total: st.count,
            }
        });
        let breath = s.breath.as_ref().map(|b| BreathLog { rounds: b.rounds.clone() });
        let w = Workout {
            id: id.clone(),
            kind: s.kind,
            name: if name.trim().is_empty() { default_name } else { name.trim().to_string() },
            started_at_ms: s.started_at_ms,
            duration_secs: duration,
            notes,
            manual: false,
            distance_m: distance_m.filter(|d| *d > 0.0),
            lifting,
            gps,
            timer,
            breath,
        };
        if update_routine {
            if let Some(l) = &w.lifting {
                if let Some(r) = l.routine_id.as_ref().and_then(|rid| g.db.routines.iter_mut().find(|r| &r.id == rid)) {
                    for re in &mut r.exercises {
                        if let Some(done) = l.exercises.iter().find(|e| e.exercise_id == re.exercise_id) {
                            for (i, plan) in re.sets.iter_mut().enumerate() {
                                if let Some(s) = done.sets.get(i) {
                                    plan.weight_kg = s.weight_kg;
                                    plan.reps = s.target_reps;
                                }
                            }
                        }
                    }
                }
            }
        }
        if w.gps.is_some() && !g.track.is_empty() {
            let path = g.track_path(&id);
            write_atomic(&path, &serde_json::to_vec(&g.track)?)?;
        }
        g.insert_workout(w);
        g.save()?;
        g.session = None;
        g.track.clear();
        g.save_session()?;
        Ok(id)
    }

    pub fn discard_session(&self) -> Result<()> {
        let mut g = self.lock();
        g.session = None;
        g.track.clear();
        g.save_session()
    }

    // ----------------------------------------------------- import/export

    /// Everything, GPS tracks included, as one JSON document.
    pub fn export_json(&self, now_ms: i64) -> Result<String> {
        let g = self.lock();
        let tracks = g
            .db
            .workouts
            .iter()
            .filter(|w| w.gps.is_some())
            .filter_map(|w| {
                let t = g.load_track(&w.id);
                (!t.is_empty()).then(|| (w.id.clone(), t))
            })
            .collect();
        let mut db = g.db.clone();
        db.version = DB_VERSION;
        let file = ExportFile { app: "evolve".into(), exported_at_ms: now_ms, db, tracks };
        Ok(serde_json::to_string_pretty(&file)?)
    }

    /// Reads an export. `replace` wipes what's here first; otherwise what
    /// isn't here yet (by id) is added and nothing is overwritten.
    pub fn import_json(&self, json: String, replace: bool) -> Result<ImportResult> {
        let file: ExportFile = serde_json::from_str(&json)?;
        if file.app != "evolve" {
            return Err(EvolveError::BadData { msg: "This isn't an Evolve export".into() });
        }
        let mut g = self.lock();
        let mut result = ImportResult { workouts: 0, body_weights: 0, exercises: 0, routines: 0, programs: 0 };
        if replace {
            for w in &g.db.workouts {
                let _ = fs::remove_file(g.track_path(&w.id));
            }
            let export_uri = g.db.settings.export_uri.clone();
            let mut db = file.db;
            db.settings.export_uri = export_uri;
            result.workouts = db.workouts.len() as u32;
            result.body_weights = db.body_weights.len() as u32;
            result.exercises = db.exercises.len() as u32;
            result.routines = db.routines.len() as u32;
            result.programs = db.programs.len() as u32;
            for (id, t) in &file.tracks {
                write_atomic(&g.track_path(id), &serde_json::to_vec(t)?)?;
            }
            db.workouts.sort_by(|a, b| b.started_at_ms.cmp(&a.started_at_ms));
            g.db = db;
        } else {
            macro_rules! merge {
                ($field:ident, $count:ident) => {{
                    let have: HashSet<String> = g.db.$field.iter().map(|x| x.id.clone()).collect();
                    for x in file.db.$field {
                        if !have.contains(&x.id) {
                            result.$count += 1;
                            g.db.$field.push(x);
                        }
                    }
                }};
            }
            merge!(exercises, exercises);
            merge!(routines, routines);
            merge!(programs, programs);
            merge!(body_weights, body_weights);
            let have: HashSet<String> = g.db.workouts.iter().map(|x| x.id.clone()).collect();
            for w in file.db.workouts {
                if have.contains(&w.id) {
                    continue;
                }
                if let Some(t) = file.tracks.get(&w.id) {
                    write_atomic(&g.track_path(&w.id), &serde_json::to_vec(t)?)?;
                }
                result.workouts += 1;
                g.insert_workout(w);
            }
        }
        g.save()?;
        Ok(result)
    }

    /// A GPS workout's trail as GPX, for other apps.
    pub fn gpx(&self, id: String) -> Option<String> {
        let g = self.lock();
        let w = g.db.workouts.iter().find(|w| w.id == id)?;
        let t = g.load_track(&id);
        (!t.is_empty()).then(|| geo::gpx(&w.name, &t))
    }
}

impl Engine {
    fn lock(&self) -> MutexGuard<'_, Inner> {
        self.inner.lock().unwrap_or_else(|p| p.into_inner())
    }

    fn change(&self, f: impl FnOnce(&mut Session)) -> Result<Session> {
        let mut g = self.lock();
        let s = g.session_mut()?;
        f(s);
        let s = s.clone();
        g.save_session()?;
        Ok(s)
    }
}

// ------------------------------------------------------------- formatting
// The same words and numbers everywhere: Kotlin calls these too.

#[uniffi::export]
pub fn format_duration(secs: f64) -> String {
    format::duration(secs)
}

#[uniffi::export]
pub fn format_weight(kg: f64, unit: WeightUnit) -> String {
    format::weight(kg, unit)
}

/// A weight in kg, in the unit given.
#[uniffi::export]
pub fn weight_in(kg: f64, unit: WeightUnit) -> f64 {
    format::weight_value(kg, unit)
}

/// A weight in the unit given, in kg.
#[uniffi::export]
pub fn weight_to_kg(value: f64, unit: WeightUnit) -> f64 {
    format::weight_from(value, unit)
}

#[uniffi::export]
pub fn format_number(value: f64, decimals: u32) -> String {
    format::number(value, decimals as usize)
}

#[uniffi::export]
pub fn format_distance(m: f64, unit: DistanceUnit) -> String {
    format::distance(m, unit)
}

#[uniffi::export]
pub fn distance_in(m: f64, unit: DistanceUnit) -> f64 {
    format::distance_value(m, unit)
}

#[uniffi::export]
pub fn distance_to_m(value: f64, unit: DistanceUnit) -> f64 {
    match unit {
        DistanceUnit::Km => value * 1000.0,
        DistanceUnit::Mi => value * M_PER_MI,
    }
}

#[uniffi::export]
pub fn format_pace(m: f64, secs: f64, unit: DistanceUnit) -> String {
    format::pace(m, secs, unit)
}

#[uniffi::export]
pub fn format_speed(m: f64, secs: f64, unit: DistanceUnit) -> String {
    format::speed(m, secs, unit)
}

#[uniffi::export]
pub fn format_elevation(m: f64, unit: DistanceUnit) -> String {
    format::elevation(m, unit)
}

#[uniffi::export]
pub fn kind_label(kind: WorkoutKind) -> String {
    kind.label().to_string()
}

#[uniffi::export]
pub fn kind_uses_gps(kind: WorkoutKind) -> bool {
    kind.uses_gps()
}

#[uniffi::export]
pub fn flatten_program(program: TimerProgram) -> Vec<FlatStep> {
    session::flatten(&program)
}

#[uniffi::export]
pub fn program_total_secs(program: TimerProgram) -> u64 {
    session::program_secs(&program)
}

#[uniffi::export]
pub fn haversine_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    geo::haversine_m(lat1, lon1, lat2, lon2)
}

/// A file name for an export made at this time (local time given as an
/// offset east of UTC): evolve-<prefix>-2026-11-25_183012.json.
#[uniffi::export]
pub fn export_file_name(prefix: String, now_ms: i64, utc_offset_min: i32) -> String {
    let local = now_ms + utc_offset_min as i64 * 60_000;
    let iso = geo::iso8601(local); // 2026-11-25T18:30:12Z
    let stamp = iso.trim_end_matches('Z').replace('T', "_").replace(':', "");
    format!("evolve-{prefix}-{stamp}.json")
}
