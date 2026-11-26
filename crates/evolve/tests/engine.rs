use evolve::*;

fn engine() -> (tempfile::TempDir, std::sync::Arc<Engine>) {
    let dir = tempfile::tempdir().unwrap();
    let e = Engine::new(dir.path().to_string_lossy().into()).unwrap();
    (dir, e)
}

#[test]
fn starts_with_a_library() {
    let (_d, e) = engine();
    let lib = e.library();
    assert!(lib.exercises.len() > 10);
    assert!(!lib.routines.is_empty());
    assert_eq!(lib.settings.theme, "Lotus");
}

#[test]
fn lifting_session_rest_and_save() {
    let (dir, e) = engine();
    let s = e.start_lifting(Some("5x5-a".into()), 1_000).unwrap();
    let l = s.lifting.unwrap();
    assert_eq!(l.exercises.len(), 3);
    assert_eq!(l.exercises[0].sets.len(), 5);

    let s = e.complete_set(0, 0, 5, 10_000).unwrap();
    let l = s.lifting.unwrap();
    assert!(l.exercises[0].sets[0].done);
    assert_eq!(l.rest_until_ms, Some(10_000 + 180_000));

    // Finishing the first exercise moves on to the next.
    for i in 1..5 {
        e.complete_set(0, i, 5, 20_000 + i as i64).unwrap();
    }
    assert_eq!(e.session().unwrap().lifting.unwrap().current, 1);

    e.set_weight(1, None, 50.0).unwrap();
    e.complete_set(1, 0, 4, 30_000).unwrap();
    let id = e.finish_session(60_000, "".into(), "good".into(), None, true).unwrap();
    assert!(e.session().is_none());
    let w = e.workout(id.clone()).unwrap();
    assert_eq!(w.name, "5×5 A");
    assert_eq!(w.duration_secs, 59.0);
    let bench = &w.lifting.as_ref().unwrap().exercises[1];
    assert_eq!(bench.sets[0].reps, 4);
    // The routine took the new weight.
    let r = e.library().routines.into_iter().find(|r| r.id == "5x5-a").unwrap();
    assert_eq!(r.exercises[1].sets[0].weight_kg, 50.0);

    // Survives a restart.
    drop(e);
    let e = Engine::new(dir.path().to_string_lossy().into()).unwrap();
    assert_eq!(e.history(HistoryFilter::default()).len(), 1);
    assert!(e.history(HistoryFilter { kinds: vec![WorkoutKind::Hike], query: "".into() }).is_empty());
}

#[test]
fn gps_filters_jitter_and_survives_restart() {
    let (dir, e) = engine();
    e.start_gps(WorkoutKind::Hike, 0).unwrap();
    let p = |lat: f64, t: i64| TrackPoint { lat, lon: -73.97, alt: Some(30.0), t_ms: t, accuracy: 5.0, seg: 0 };
    assert!(e.add_point(p(40.7800, 0)).unwrap().kept.is_some());
    // Half a metre: jitter.
    assert!(e.add_point(p(40.780005, 1000)).unwrap().kept.is_none());
    // ~111 m north in 60 s.
    let u = e.add_point(p(40.7810, 60_000)).unwrap();
    assert!(u.kept.is_some());
    assert!((u.live.distance_m - 111.0).abs() < 2.0, "{}", u.live.distance_m);
    // A teleport is dropped.
    assert!(e.add_point(p(41.0, 61_000)).unwrap().kept.is_none());
    e.set_place_name("Central Park".into()).unwrap();

    drop(e);
    let e = Engine::new(dir.path().to_string_lossy().into()).unwrap();
    assert_eq!(e.session_track().len(), 2);
    assert_eq!(e.default_session_name(), "Central Park");
    let id = e.finish_session(120_000, "".into(), "".into(), None, false).unwrap();
    assert_eq!(e.track(id.clone()).len(), 2);
    assert!(e.gpx(id).unwrap().contains("<trkpt"));
}

#[test]
fn gps_pauses_itself_when_driven_away() {
    let (_dir, e) = engine();
    e.start_gps(WorkoutKind::Hike, 0).unwrap();
    // North at 1.5 m/s for two minutes, a fix a second.
    let deg_per_m = 1.0 / 111_195.0;
    let p = |m: f64, t: i64| TrackPoint { lat: 40.0 + m * deg_per_m, lon: -73.97, alt: None, t_ms: t, accuracy: 5.0, seg: 0 };
    let mut last_kept = 0;
    for s in 0..=120 {
        let u = e.add_point(p(1.5 * s as f64, s * 1000)).unwrap();
        assert!(!u.paused);
        if u.kept.is_some() {
            last_kept = s * 1000;
        }
    }
    // A GPS glitch: five seconds far off pace, then back on the trail.
    let walked = 1.5 * 120.0;
    for s in 121..=125 {
        assert!(!e.add_point(p(walked + 30.0 * (s - 120) as f64, s * 1000)).unwrap().paused);
    }
    for s in 126..=150 {
        let u = e.add_point(p(1.5 * s as f64, s * 1000)).unwrap();
        assert!(!u.paused);
        if u.kept.is_some() {
            last_kept = s * 1000;
        }
    }
    assert!(e.session().unwrap().paused_at_ms.is_none());
    let before_car = e.session().unwrap().gps.unwrap().distance_m;
    // Then into the car: 18 m/s (40 mph).
    let walked = 1.5 * 150.0;
    let mut paused_at = None;
    for s in 151..=200 {
        let u = e.add_point(p(walked + 18.0 * (s - 150) as f64, s * 1000)).unwrap();
        if u.paused {
            paused_at = Some(s);
            assert!(u.live.auto_paused);
            break;
        }
    }
    let paused_at = paused_at.expect("never paused");
    assert!(paused_at <= 175, "{paused_at}");
    let session = e.session().unwrap();
    let at = session.paused_at_ms.unwrap();
    assert!(at <= 150_000 && at >= 140_000, "{at}");
    // The trail stops there: none of the drive is in it.
    let trail = e.session_track();
    assert!(trail.last().unwrap().t_ms <= at);
    assert!(trail.last().unwrap().t_ms >= last_kept - 12_000);
    let gps = session.gps.unwrap();
    // Less up to ten seconds of walking, the distance is what it was before the drive.
    assert!(gps.distance_m <= before_car + 0.1 && gps.distance_m > before_car - 20.0, "{} {before_car}", gps.distance_m);
    // Resumed, it starts afresh.
    let resumed = e.resume(300_000).unwrap();
    assert!(!resumed.gps.unwrap().auto_paused);
}

#[test]
fn interval_program() {
    let (_d, e) = engine();
    e.start_timer(WorkoutKind::StationaryBike, TimerMode::Program, Some("hiit".into()), 0, 0).unwrap();
    let st = e.timer_status(5_000).unwrap();
    assert_eq!(st.label, "Get ready");
    assert_eq!(st.count, 2 + 10 + 1);
    // 10 s get ready + 300 s warm-up, then the first sprint.
    let st = e.timer_status(311_000).unwrap();
    assert_eq!((st.label.as_str(), st.rep, st.reps), ("All out", 1, 5));
    assert_eq!(st.step_remaining_ms, 19_000);
    // Pausing stops the clock.
    e.pause(311_000).unwrap();
    assert_eq!(e.timer_status(400_000).unwrap().step_remaining_ms, 19_000);
    e.resume(400_000).unwrap();
    let total = 10 + 300 + 5 * 140 + 300;
    let st = e.timer_status((total as i64 + 89) * 1000 + 10).unwrap();
    assert!(st.finished);
}

#[test]
fn breathing_rounds() {
    let (_d, e) = engine();
    e.start_breathing(0).unwrap();
    e.breath_advance(120_000).unwrap(); // done breathing: hold
    e.breath_advance(210_000).unwrap(); // 90 s hold
    let st = e.breath_status(215_000).unwrap();
    assert_eq!(st.phase, BreathPhase::Recovery);
    assert_eq!(st.recovery_remaining_ms, 5_000);
    e.breath_advance(220_000).unwrap();
    assert_eq!(e.breath_status(220_000).unwrap().round, 2);
    let id = e.finish_session(300_000, "".into(), "".into(), None, false).unwrap();
    let w = e.workout(id).unwrap();
    assert_eq!(w.breath.unwrap().rounds[0].hold_secs, 90.0);
}

#[test]
fn export_and_import() {
    let (_d, e) = engine();
    e.add_body_weight(1_000, 80.0, "".into()).unwrap();
    e.log_manual(WorkoutKind::Meditation, "".into(), 5_000, 600.0, None, "".into()).unwrap();
    e.start_gps(WorkoutKind::Walk, 10_000).unwrap();
    e.add_point(TrackPoint { lat: 1.0, lon: 1.0, alt: None, t_ms: 10_000, accuracy: 3.0, seg: 0 }).unwrap();
    e.finish_session(20_000, "Walk".into(), "".into(), None, false).unwrap();
    let json = e.export_json(30_000).unwrap();

    let (_d2, other) = engine();
    other.log_manual(WorkoutKind::Run, "Mine".into(), 1, 60.0, Some(1000.0), "".into()).unwrap();
    let r = other.import_json(json.clone(), false).unwrap();
    assert_eq!(r.workouts, 2);
    assert_eq!(r.body_weights, 1);
    assert_eq!(other.history(HistoryFilter::default()).len(), 3);
    // Merging again adds nothing.
    assert_eq!(other.import_json(json.clone(), false).unwrap().workouts, 0);
    other.import_json(json, true).unwrap();
    let h = other.history(HistoryFilter::default());
    assert_eq!(h.len(), 2);
    let walk = h.iter().find(|w| w.kind == WorkoutKind::Walk).unwrap();
    assert_eq!(other.track(walk.id.clone()).len(), 1);
    assert!(other.import_json("{}".into(), false).is_err());
}

#[test]
fn graphs() {
    let (_d, e) = engine();
    e.add_body_weight(2_000, 80.0, "".into()).unwrap();
    e.add_body_weight(1_000, 81.0, "".into()).unwrap();
    let q = |metric| GraphQuery { metric, exercise_id: Some("squat".into()), kinds: vec![], since_ms: None, utc_offset_min: 0 };
    let g = e.graph(q(Metric::BodyWeight));
    assert_eq!(g.points.len(), 2);
    assert!(g.points[0].t_ms < g.points[1].t_ms);
    assert_eq!(g.unit, "lb");

    e.start_lifting(Some("5x5-a".into()), 0).unwrap();
    e.set_weight(0, None, 100.0).unwrap();
    e.complete_set(0, 0, 5, 1).unwrap();
    e.complete_set(0, 1, 3, 2).unwrap();
    e.finish_session(100, "".into(), "".into(), None, false).unwrap();
    let g = e.graph(q(Metric::ExerciseOneRepMax));
    assert_eq!(g.points.len(), 1);
    let expect = weight_in(100.0 * (1.0 + 5.0 / 30.0), WeightUnit::Lb);
    assert!((g.points[0].value - expect).abs() < 1e-6);
    let g = e.graph(q(Metric::WorkoutsPerWeek));
    assert_eq!(g.points.len(), 1);
}

#[test]
fn file_names() {
    assert_eq!(export_file_name("auto".into(), 1_700_000_000_000, -300), "evolve-auto-2023-11-14_171320.json");
}

#[test]
fn weeks_start_on_sunday() {
    let (_d, e) = engine();
    const DAY: i64 = 86_400_000;
    // 2023-11-12 was a Sunday; log a workout on Sunday and one on Monday.
    let sunday = 1_699_747_200_000i64;
    e.log_manual(WorkoutKind::Meditation, "".into(), sunday + 3_600_000, 600.0, None, "".into()).unwrap();
    e.log_manual(WorkoutKind::Meditation, "".into(), sunday + DAY + 3_600_000, 600.0, None, "".into()).unwrap();
    let q = GraphQuery { metric: Metric::WorkoutsPerWeek, exercise_id: None, kinds: vec![], since_ms: None, utc_offset_min: 0 };

    // Weeks start on Sunday: both in one week, which starts that Sunday,
    // even with an old Monday setting.
    let mut s = e.library().settings;
    s.week_start_day = 1;
    e.set_settings(s).unwrap();
    let g = e.graph(q);
    assert_eq!(g.points.len(), 1);
    assert_eq!(g.points[0].t_ms, sunday);
    assert_eq!(g.points[0].value, 2.0);
}

#[test]
fn old_settings_take_new_defaults() {
    let s: Settings = serde_json::from_str(r#"{"theme":"Iris","week_start_day":1}"#).unwrap();
    assert_eq!(s.theme, "Iris");
    assert_eq!(s.week_start_day, 1);
    assert_eq!(s.auto_export_keep, 10);
    assert_eq!(s.map_mode, MapMode::Auto);
    assert_eq!(s.map_light_style, "Bright");
    assert_eq!(s.map_dark_style, "Midnight");
    assert_eq!(s.contrast, Contrast::Standard);
    let back: Settings = serde_json::from_str(&serde_json::to_string(&s).unwrap()).unwrap();
    assert_eq!(back, s);
}
