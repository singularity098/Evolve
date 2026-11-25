//! Distances on the Earth, and GPX for tracks.

use crate::model::TrackPoint;

const EARTH_RADIUS_M: f64 = 6_371_008.8;

pub fn haversine_m(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let (p1, p2) = (lat1.to_radians(), lat2.to_radians());
    let dp = p2 - p1;
    let dl = (lon2 - lon1).to_radians();
    let a = (dp / 2.0).sin().powi(2) + p1.cos() * p2.cos() * (dl / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_M * a.sqrt().asin()
}

/// The track's length, segments not joined across pauses.
pub fn track_length_m(track: &[TrackPoint]) -> f64 {
    track
        .windows(2)
        .filter(|w| w[0].seg == w[1].seg)
        .map(|w| haversine_m(w[0].lat, w[0].lon, w[1].lat, w[1].lon))
        .sum()
}

fn escape(s: &str) -> String {
    s.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;")
}

/// An ISO 8601 UTC time from milliseconds since 1970.
pub fn iso8601(ms: i64) -> String {
    let secs = ms.div_euclid(1000);
    let days = secs.div_euclid(86_400);
    let rem = secs.rem_euclid(86_400);
    let (y, m, d) = civil_from_days(days);
    format!("{y:04}-{m:02}-{d:02}T{:02}:{:02}:{:02}Z", rem / 3600, rem % 3600 / 60, rem % 60)
}

/// Days since 1970-01-01 to (year, month, day), proleptic Gregorian.
pub fn civil_from_days(z: i64) -> (i64, u32, u32) {
    let z = z + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let m = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    (if m <= 2 { y + 1 } else { y }, m, d)
}

pub fn gpx(name: &str, track: &[TrackPoint]) -> String {
    let mut out = String::from(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Evolve\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n",
    );
    out.push_str(&format!("  <trk>\n    <name>{}</name>\n", escape(name)));
    let mut seg: Option<u32> = None;
    for p in track {
        if seg != Some(p.seg) {
            if seg.is_some() {
                out.push_str("    </trkseg>\n");
            }
            out.push_str("    <trkseg>\n");
            seg = Some(p.seg);
        }
        out.push_str(&format!("      <trkpt lat=\"{:.7}\" lon=\"{:.7}\">", p.lat, p.lon));
        if let Some(a) = p.alt {
            out.push_str(&format!("<ele>{a:.1}</ele>"));
        }
        out.push_str(&format!("<time>{}</time></trkpt>\n", iso8601(p.t_ms)));
    }
    if seg.is_some() {
        out.push_str("    </trkseg>\n");
    }
    out.push_str("  </trk>\n</gpx>\n");
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn one_degree_of_latitude() {
        let d = haversine_m(0.0, 0.0, 1.0, 0.0);
        assert!((d - 111_195.0).abs() < 10.0, "{d}");
    }

    #[test]
    fn dates() {
        assert_eq!(iso8601(0), "1970-01-01T00:00:00Z");
        assert_eq!(iso8601(1_700_000_000_000), "2023-11-14T22:13:20Z");
        assert_eq!(civil_from_days(19_782), (2024, 2, 29));
    }
}
