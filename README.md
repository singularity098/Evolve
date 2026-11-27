# Evolve

A fitness tracker for Android: a Rust engine with a native
[Jetpack Compose](https://developer.android.com/compose) interface, styled
after [Chippy](https://github.com/singularity098/Chippy) (its dark looks,
Jost type and painted highlights, plus *Lotus*, the icon's blues).
Everything stays on the phone, and can be exported and imported whole.

## Using it

Six tabs along the bottom, and a swipe sideways moves between them (except
on a GPS activity's map, which takes the drag for itself):

* **Home**: choose today's activity, see this week's count and your streak,
  log your body weight (any day, active or not), or enter a past activity
  by hand.
* **In progress**: only while an activity is under way.
* **History**: every activity, newest first, filtered by kind or searched by
  name and notes. Tap one for everything about it: the map of a hike, every
  set of a lift, each breath hold. Rename, add notes, delete, or save a
  GPS track as GPX.
* **Graphs**: body weight; for any exercise its heaviest set, estimated
  one-rep max, volume and reps; lifting volume; distance, pace, speed,
  climb and duration (by kind); breath holds; meditation; activities and
  minutes per week. Over a month, three, six, a year or all time; tap or
  hold the chart to read a point.
* **Calendar**: the month, with the days you worked out marked by what you
  did. Tap a day to open its activity in History (or pick from a list when
  there were several).
* **Settings**: design routines and interval programs, the exercise
  library, the weigh-in log, units, sounds (and whether other apps' music is left alone, turned down or paused while one plays), the backup folder, the look
  (a theme, and a contrast level: Standard, High or Maximum, for brighter
  text and plainer edges, whatever the theme). Weeks start on Sunday.

### Activities

* **Weights.** Routines are designed in detail in Settings: any number of
  barbell, dumbbell, kettlebell, machine, cable or bodyweight exercises,
  each with its own sets (reps and weight per set) and rest. During the
  activity each set is a round button showing its reps (a dashed ring until it's done, then a solid painted one): tap it when done
  and the rest timer starts, with a sound and a buzz when it's over (also
  with the screen off). Hold a set to record fewer reps or change its
  weight; the ± buttons change the weight for the sets still to do. Add
  exercises or sets as you go. An exercise with every set done is marked
  off and folds down to a thin row with a tick (tap it to open it again) until another set is
  added; the list stays where it is rather than scrolling to the next. The activity
  notification shows the current exercise's sets as circles, to tap off from the lock screen
  or another app. Until today's body weight is logged, a reminder at the top offers to log
  it. When saving, the routine can take on today's weights.
* **Walk, run, hike, bike ride.** GPS tracking on a map, the trail drawn
  behind you so the way back is easy to find, with distance, pace or
  speed, and climb. Pause and resume (the trail breaks while paused). Going
  far too fast for the activity for a while (driving home from the trail
  without ending the hike: over ~12 mph for a walk or hike, ~20 mph for a
  run, ~45 mph for a ride, for 20–30 s) pauses it by itself, back at the
  moment the speed went up, so the drive isn't counted. When it's saved the name defaults to where you started ("Central Park").
* **Stationary bike.** A free-ride stopwatch, a countdown, or an interval
  program: blocks of timed intervals, each block repeated as many times as
  you like. For example: get ready 10 s, warm-up 5 min, then 5 × (all out
  20 s, recover 2 min), then cool-down 5 min. Each interval change sounds
  (high for hard work, low for rest) with ticks for the last three seconds.
  The ring counts down smoothly at the screen's frame rate; under it the
  whole program is one bar, each interval to scale in its intensity's colour
  with a marker where you are, and the next three intervals are listed below.
* **Meditation.** A quiet countdown with a singing bowl at the end, or enter
  a session done elsewhere afterwards.
* **Breathing.** Breath-hold rounds: breathe deeply 40 times (an optional
  pacing circle grows and shrinks with each breath), tap, breathe out fully
  and hold while a stopwatch runs; tap when you must breathe, take a full
  recovery breath and hold it for 10 seconds; then the next round begins.
  The counts are settings, and the Graphs tab tracks your longest hold.

The screen can be kept on during activities (a setting, and the sun button
in each activity changes it just for that one). An activity carries on in the
background with a notification, and survives the app being closed.

### Maps

Maps are [MapLibre](https://maplibre.org) with
[OpenFreeMap](https://openfreemap.org)'s vector tiles: free, no API key, no
usage limits, and offline use allowed (OpenStreetMap's own tile servers
forbid bulk downloads, so they can't be used for this). At a GPS activity's
first fix, the map within 10 miles in every direction (a setting) is
downloaded for offline use, at every zoom, and again whenever you've gone
halfway to the edge; it's a few megabytes. Areas older than two weeks are
deleted. Place names come from OpenStreetMap's Nominatim, then Android's
geocoder.

The map is dark indoors and at night and light outside in daylight, by
the light sensor (or always light, or always dark: a setting); the sun and
moon button on a map switches it for that activity. The light map is
OpenFreeMap's Bright; the dark one, Midnight, is OpenFreeMap's Positron
repainted by the app in deep navy with bolder roads and labels. Both use
the same tiles and the same offline cache.

Other good free map sources, should OpenFreeMap ever not suit:
[MapTiler](https://www.maptiler.com) and [Stadia Maps](https://stadiamaps.com)
(free tiers, with a key), [Thunderforest](https://www.thunderforest.com)
(OpenCycleMap and Outdoors styles, free tier with a key) and
[OpenTopoMap](https://opentopomap.org) (lovely for hiking, but no bulk
downloading).

### Your data

*Settings → Your data*: choose a folder, then **Export now** writes
everything (settings, the exercise library, routines, programs, every
activity with its GPS track, and every weigh-in) to one gzipped JSON file
there (`.json.gz`). **Export after every activity** does it automatically
as soon as an activity is saved, keeping the last ten (a setting). **Import**
merges an export (adding what isn't here yet) or replaces everything with
it; older plain `.json` exports still import.

## Building

The engine (`crates/evolve`) keeps the data, runs the activities' logic
(sets and rests, GPS distance with jitter filtering, interval timing,
breath rounds), computes the graphs and does import and export. UniFFI
generates its Kotlin bindings; the Compose app is in `android/`.

```bash
cargo test
```

```bash
ANDROID_HOME=~/Android/Sdk ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.2.12479018 android/build-apk.sh release
```

It needs the SDK's platform and build-tools 35, the NDK, the Rust target
`aarch64-linux-android`, `cargo install cargo-ndk`, and Gradle 8. Every push
to GitHub builds the APK too: download it from the run's *Artifacts* on the
Actions tab. Every build is signed with the same (public) key in
`android/app/evolve-ci.keystore`, so each one installs over the last.

## Licence

GPL-3.0-or-later, as Chippy. Jost is under the SIL Open Font Licence
(`assets/OFL-Jost.txt`).
