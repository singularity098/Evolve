//! What a new install starts with: a library of common lifts, two routines
//! and two interval programs to edit or delete.

use crate::model::*;

fn ex(id: &str, name: &str, equipment: Equipment) -> Exercise {
    Exercise { id: id.into(), name: name.into(), equipment, notes: String::new() }
}

fn lb(v: f64) -> f64 {
    v / LB_PER_KG
}

fn plan(id: &str, sets: u32, reps: u32, weight_lb: f64, rest: u32) -> RoutineExercise {
    RoutineExercise {
        exercise_id: id.into(),
        sets: (0..sets).map(|_| SetPlan { reps, weight_kg: lb(weight_lb) }).collect(),
        rest_secs: rest,
    }
}

fn iv(label: &str, secs: u32, intensity: Intensity) -> TimerInterval {
    TimerInterval { label: label.into(), secs, intensity }
}

pub fn database() -> Database {
    let exercises = vec![
        ex("squat", "Squat", Equipment::Barbell),
        ex("bench", "Bench press", Equipment::Barbell),
        ex("deadlift", "Deadlift", Equipment::Barbell),
        ex("ohp", "Overhead press", Equipment::Barbell),
        ex("row", "Barbell row", Equipment::Barbell),
        ex("rdl", "Romanian deadlift", Equipment::Barbell),
        ex("db-bench", "Dumbbell bench press", Equipment::Dumbbell),
        ex("db-press", "Dumbbell shoulder press", Equipment::Dumbbell),
        ex("db-row", "Dumbbell row", Equipment::Dumbbell),
        ex("db-curl", "Dumbbell curl", Equipment::Dumbbell),
        ex("db-lunge", "Dumbbell lunge", Equipment::Dumbbell),
        ex("db-lateral", "Lateral raise", Equipment::Dumbbell),
        ex("goblet", "Goblet squat", Equipment::Dumbbell),
        ex("pullup", "Pull-up", Equipment::Bodyweight),
        ex("pushup", "Push-up", Equipment::Bodyweight),
        ex("dip", "Dip", Equipment::Bodyweight),
    ];
    let routines = vec![
        Routine {
            id: "5x5-a".into(),
            name: "5×5 A".into(),
            notes: "Squat, bench, row".into(),
            exercises: vec![plan("squat", 5, 5, 135.0, 180), plan("bench", 5, 5, 95.0, 180), plan("row", 5, 5, 95.0, 120)],
        },
        Routine {
            id: "5x5-b".into(),
            name: "5×5 B".into(),
            notes: "Squat, press, deadlift".into(),
            exercises: vec![plan("squat", 5, 5, 135.0, 180), plan("ohp", 5, 5, 65.0, 180), plan("deadlift", 1, 5, 185.0, 180)],
        },
        Routine {
            id: "dumbbells".into(),
            name: "Dumbbell full body".into(),
            notes: String::new(),
            exercises: vec![
                plan("goblet", 3, 10, 40.0, 90),
                plan("db-bench", 3, 10, 35.0, 90),
                plan("db-row", 3, 10, 35.0, 90),
                plan("db-press", 3, 10, 25.0, 90),
                plan("db-curl", 3, 12, 20.0, 60),
            ],
        },
    ];
    let programs = vec![
        TimerProgram {
            id: "hiit".into(),
            name: "Sprints 5 × 20 s".into(),
            blocks: vec![
                TimerBlock { repeats: 1, intervals: vec![iv("Get ready", 10, Intensity::Rest), iv("Warm-up", 300, Intensity::Easy)] },
                TimerBlock { repeats: 5, intervals: vec![iv("All out", 20, Intensity::Max), iv("Recover", 120, Intensity::Easy)] },
                TimerBlock { repeats: 1, intervals: vec![iv("Cool-down", 300, Intensity::Easy)] },
            ],
        },
        TimerProgram {
            id: "pyramid".into(),
            name: "Pyramid 30 min".into(),
            blocks: vec![
                TimerBlock { repeats: 1, intervals: vec![iv("Warm-up", 300, Intensity::Easy)] },
                TimerBlock {
                    repeats: 2,
                    intervals: vec![
                        iv("Moderate", 120, Intensity::Moderate),
                        iv("Hard", 60, Intensity::Hard),
                        iv("Moderate", 120, Intensity::Moderate),
                        iv("Max", 30, Intensity::Max),
                        iv("Easy", 150, Intensity::Easy),
                    ],
                },
                TimerBlock { repeats: 1, intervals: vec![iv("Cool-down", 300, Intensity::Easy)] },
            ],
        },
    ];
    Database { version: DB_VERSION, settings: Settings::default(), exercises, routines, programs, workouts: vec![], body_weights: vec![] }
}
