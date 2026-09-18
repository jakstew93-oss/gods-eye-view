package uk.jak.godseyefold;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed, keyless upstream feeds for the bundled app; never proxies arbitrary URLs. */
public final class LiveData {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long TLE_TTL = 6 * 3600_000L;
    private static final java.util.List<String> GROUPS = Arrays.asList(
        "stations", "visual", "gps-ops", "glo-ops", "galileo", "geo", "starlink");
    private final File directory;
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>();

    public LiveData(File directory) { this.directory = directory; }
    public static final class Result {
        public final int status;
        public final String mime, body;
        public final Map<String, String> headers;
        Result(int status, String mime, String body, Map<String, String> headers) {
            this.status = status; this.mime = mime; this.body = body; this.headers = headers;
        }
    }
    private static final class Entry {
        final Result value;
        final long at, ttl;
        Entry(Result value, long at, long ttl) { this.value = value; this.at = at; this.ttl = ttl; }
    }
    static final class Route {
        final String url, group;
        final boolean flights;
        final long ttl;
        Route(String url, String group, boolean flights, long ttl) {
            this.url = url; this.group = group; this.flights = flights; this.ttl = ttl;
        }
    }

    static Route route(URI uri) {
        String path = uri.getPath();
        if ("/api/opensky".equals(path)) {
            Map<String, String> query = new HashMap<>();
            if (uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    String[] pair = part.split("=", 2);
                    try { query.put(URLDecoder.decode(pair[0], "UTF-8"),
                        pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : ""); }
                    catch (Exception error) { throw new IllegalArgumentException("Invalid query"); }
                }
            }
            // The frontend supplies its current viewport centre. Require both together.
            boolean hasLat = query.containsKey("lat"), hasLon = query.containsKey("lon");
            if (hasLat != hasLon) throw new IllegalArgumentException("Both coordinates are required");
            double lat = hasLat ? Double.parseDouble(query.get("lat")) : 30.2672;
            double lon = hasLon ? Double.parseDouble(query.get("lon")) : -97.7431;
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
                throw new IllegalArgumentException("Invalid coordinates");
            // Match the upstream regional fallback: 250 nautical miles around the viewed area.
            lat = Math.round(lat * 4) / 4.0; lon = Math.round(lon * 4) / 4.0;
            return new Route("https://api.adsb.lol/v2/point/" + lat + "/" + lon + "/250",
                null, true, 12000);
        }
        if ("/api/adsblol/mil".equals(path))
            return new Route("https://api.adsb.lol/v2/mil", null, false, 12000);
        if (path != null && path.startsWith("/api/celestrak/")) {
            String group = path.substring("/api/celestrak/".length());
            if (!GROUPS.contains(group)) throw new IllegalArgumentException("Unknown satellite group");
            return new Route("https://celestrak.org/NORAD/elements/gp.php?GROUP="
                + group + "&FORMAT=tle", group, false, TLE_TTL);
        }
        return null;
    }

    private static Double number(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) return null;
        Object value = row.opt(key);
        if (value instanceof String && ((String) value).trim().isEmpty()) return null;
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException error) { return null; }
    }
    private static Object scaled(Double value, double factor) {
        return value == null ? JSONObject.NULL : value * factor;
    }
    private static String text(JSONObject row, String key) { return row.optString(key, "").trim(); }

    /** Keep aircraft age and units in the OpenSky state-vector contract used by the frontend. */
    static JSONObject aircraft(JSONObject payload, long fallbackNowMs) throws Exception {
        JSONArray contacts = payload.optJSONArray("ac");
        if (contacts == null) throw new IllegalArgumentException("Malformed aircraft snapshot");
        Double responseTime = number(payload, "now");
        double seconds = Math.floor(responseTime == null ? fallbackNowMs / 1000.0
            : responseTime > 10_000_000_000.0 ? responseTime / 1000 : responseTime);
        JSONArray states = new JSONArray();
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject row = contacts.optJSONObject(i);
            if (row == null) continue;
            String hex = text(row, "hex").toLowerCase(java.util.Locale.ROOT);
            Double lat = number(row, "lat"), lon = number(row, "lon");
            if (!hex.matches("[0-9a-f]{6}") || lat == null || lon == null
                || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
            Double seenPos = number(row, "seen_pos"), seen = number(row, "seen");
            double positionAge = Math.max(0, seenPos != null ? seenPos : seen != null ? seen : 0);
            double contactAge = Math.max(0, seen != null ? seen : positionAge);
            boolean ground = "ground".equals(row.optString("alt_baro"));
            Double rate = number(row, "baro_rate");
            if (rate == null) rate = number(row, "geom_rate");
            String callsign = text(row, "flight");
            if (callsign.isEmpty()) callsign = text(row, "r");
            JSONArray state = new JSONArray();
            state.put(hex).put(callsign.isEmpty() ? JSONObject.NULL : callsign).put(JSONObject.NULL)
                .put(Math.max(0, seconds - positionAge)).put(Math.max(0, seconds - contactAge))
                .put(lon).put(lat).put(ground ? JSONObject.NULL : scaled(number(row, "alt_baro"), 0.3048))
                .put(ground).put(scaled(number(row, "gs"), 0.514444))
                .put(scaled(number(row, "track"), 1)).put(scaled(rate, 0.00508))
                .put(JSONObject.NULL).put(scaled(number(row, "alt_geom"), 0.3048))
                .put(row.opt("squawk") == null ? JSONObject.NULL : row.opt("squawk"))
                .put(row.optInt("spi") == 1).put(0).put(category(text(row, "category")));
            states.put(state);
        }
        return new JSONObject().put("time", seconds).put("states", states);
    }
    private static int category(String value) {
        switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "A1": return 2; case "A2": return 3; case "A3": return 4;
            case "A4": return 5; case "A5": return 6; case "A6": return 7;
            case "A7": return 8; case "B1": return 9; case "B2": return 10;
            case "B3": return 11; case "B4": return 12; case "B6": return 14;
            case "B7": return 15; default: return 0;
        }
    }
    private static String read(InputStream stream) throws Exception {
        if (stream == null) throw new IllegalArgumentException("Empty response");
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > MAX_BYTES) throw new IllegalArgumentException("Response too large");
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        }
    }
    private static Result error(int status, String message) {
        JSONObject json = new JSONObject();
        try { json.put("error", message); } catch (Exception ignored) {}
        return new Result(status, "application/json", json.toString(),
            java.util.Collections.singletonMap("Cache-Control", "no-store"));
    }

    /** Called on a WebView worker thread. Synchronization coalesces simultaneous layer polls. */
    public synchronized Result fetch(String address, String method) {
        Route route;
        try { route = route(URI.create(address)); }
        catch (Exception invalid) { return error(400, "Invalid live-feed request"); }
        if (route == null) return null;
        if (!"GET".equals(method)) return error(405, "Only GET is supported");
        long now = System.currentTimeMillis();
        Entry entry = cache.get(route.url);
        if (entry != null && now >= entry.at && now - entry.at < entry.ttl) {
            Map<String, String> headers = new HashMap<>(entry.value.headers);
            if (headers.containsKey("X-ADS-B-Cache")) {
                headers.put("X-ADS-B-Cache", "HIT");
                headers.put("X-ADS-B-Cache-Age-Ms", Long.toString(now - entry.at));
            }
            return new Result(entry.value.status, entry.value.mime, entry.value.body, headers);
        }
        File disk = route.group == null ? null : new File(directory, route.group + ".json");
        if (disk != null && disk.isFile()) {
            try {
                JSONObject stored = new JSONObject(read(new FileInputStream(disk)));
                long at = stored.getLong("at");
                String body = stored.getString("body");
                if (now >= at && now - at < TLE_TTL && validTle(body))
                    return new Result(200, "text/plain", body,
                        java.util.Collections.singletonMap("X-TLE-Cache", "HIT"));
            } catch (Exception ignored) { /* Refresh damaged/expired files. */ }
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(route.url).toURL().openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent",
                "Gods-Eye-Fold/0.3 (+https://github.com/jakstew93-oss/gods-eye-view)");
            connection.setRequestProperty("Accept", route.group == null ? "application/json" : "text/plain");
            int status = connection.getResponseCode();
            if (status != 200) {
                Result failed = error(status >= 400 && status <= 599 ? status : 502,
                    "Live provider unavailable (HTTP " + status + ")");
                long cooldown = 30000;
                if (status == 429) {
                    cooldown = 60000;
                    try { cooldown = Math.max(cooldown, Math.min(1800000,
                        Long.parseLong(connection.getHeaderField("Retry-After")) * 1000)); }
                    catch (Exception ignored) {}
                }
                remember(route.url, new Entry(failed, now, cooldown));
                return failed;
            }
            String body = read(connection.getInputStream());
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            if (route.group != null) {
                if (!validTle(body)) throw new IllegalArgumentException("Malformed satellite catalog");
                headers.put("X-TLE-Cache", "MISS");
                // Persistent catalog cache prevents repeated fetching across app restarts.
                try {
                    directory.mkdirs();
                    File temporary = new File(directory, route.group + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(temporary)) {
                        out.write(new JSONObject().put("at", now).put("body", body)
                            .toString().getBytes(StandardCharsets.UTF_8));
                    }
                    if (!temporary.renameTo(disk)) temporary.delete();
                } catch (Exception ignored) { /* In-memory cache still applies. */ }
            } else {
                JSONObject payload = new JSONObject(body);
                if (payload.optJSONArray("ac") == null)
                    throw new IllegalArgumentException("Malformed aircraft snapshot");
                if (route.flights) {
                    body = aircraft(payload, now).toString();
                    headers.put("X-Flight-Source", "adsb.lol");
                    headers.put("X-Flight-Coverage", "regional 250 nm around viewed area");
                    headers.put("X-OpenSky-Auth", "native-keyless");
                } else {
                    headers.put("X-ADS-B-Cache", "MISS");
                    headers.put("X-ADS-B-Cache-Age-Ms", "0");
                }
            }
            Result value = new Result(200, route.group == null ? "application/json" : "text/plain", body, headers);
            remember(route.url, new Entry(value, now, route.ttl));
            return value;
        } catch (Exception failure) {
            String detail = failure instanceof java.net.UnknownHostException ? "DNS lookup failed"
                : failure instanceof java.net.SocketTimeoutException ? "Connection timed out"
                : failure instanceof javax.net.ssl.SSLException ? "Secure connection failed"
                : failure instanceof IllegalArgumentException ? "Provider returned invalid data"
                : "Network or provider response failed";
            Result failed = error(502, detail);
            remember(route.url, new Entry(failed, now, 30000));
            return failed;
        } finally { if (connection != null) connection.disconnect(); }
    }
    private void remember(String key, Entry entry) {
        cache.remove(key); cache.put(key, entry);
        while (cache.size() > 16) cache.remove(cache.keySet().iterator().next());
    }
    static boolean validTle(String body) {
        return java.util.regex.Pattern.compile("^1 .+$", java.util.regex.Pattern.MULTILINE).matcher(body).find()
            && java.util.regex.Pattern.compile("^2 .+$", java.util.regex.Pattern.MULTILINE).matcher(body).find();
    }
}
