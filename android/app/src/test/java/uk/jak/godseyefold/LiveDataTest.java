package uk.jak.godseyefold;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.net.URI;
import org.json.JSONObject;
import org.json.JSONArray;

public class LiveDataTest {
    @Test public void mapsViewCentreAndRejectsBadInput() {
        assertEquals("https://api.adsb.lol/v2/point/52.5/-1.25/250",
            LiveData.route(URI.create("https://local/api/opensky?lat=52.6&lon=-1.2")).url);
        for (String query : new String[]{"lat=91&lon=1", "lat=1", "lat=NaN&lon=1", "lat=1&lon=Infinity"}) {
            try { LiveData.route(URI.create("https://local/api/opensky?" + query)); fail(query); }
            catch (IllegalArgumentException expected) {}
        }
        assertNull(LiveData.route(URI.create("https://local/api/not-supported?url=https://example.org")));
        try { LiveData.route(URI.create("https://local/api/celestrak/arbitrary")); fail(); }
        catch (IllegalArgumentException expected) {}
    }
    @Test public void preservesUnitsGroundAndObservationAge() throws Exception {
        JSONObject row = new JSONObject("{\"hex\":\"ABC123\",\"lat\":52,\"lon\":-1,\"flight\":\" TEST \","
            + "\"alt_baro\":10000,\"alt_geom\":11000,\"gs\":100,\"track\":90,\"baro_rate\":100,"
            + "\"seen_pos\":4,\"seen\":2,\"category\":\"A3\"}");
        JSONObject payload = new JSONObject().put("now", 1700000000000L)
            .put("ac", new JSONArray().put(row));
        JSONArray state = LiveData.aircraft(payload, 0).getJSONArray("states").getJSONArray(0);
        assertEquals("abc123", state.getString(0));
        assertEquals("TEST", state.getString(1));
        assertEquals(1699999996, state.getDouble(3), 0);
        assertEquals(1699999998, state.getDouble(4), 0);
        assertEquals(3048, state.getDouble(7), 0.000001);
        assertEquals(51.4444, state.getDouble(9), 0.000001);
        assertEquals(0.508, state.getDouble(11), 0.000001);
        assertEquals(4, state.getInt(17));
        row.put("alt_baro", "ground");
        state = LiveData.aircraft(payload, 0).getJSONArray("states").getJSONArray(0);
        assertTrue(state.getBoolean(8)); assertTrue(state.isNull(7));
    }
    @Test public void filtersInvalidContactsAndRejectsMalformedSnapshots() throws Exception {
        JSONObject payload = new JSONObject("{\"now\":1700000000,\"ac\":["
            + "{\"hex\":\"abc123\",\"lat\":null,\"lon\":1},"
            + "{\"hex\":\"bad-id\",\"lat\":1,\"lon\":1},"
            + "{\"hex\":\"abc123\",\"lat\":1,\"lon\":181}]}");
        assertEquals(0, LiveData.aircraft(payload, 0).getJSONArray("states").length());
        try { LiveData.aircraft(new JSONObject(), 0); fail(); }
        catch (IllegalArgumentException expected) {}
        assertFalse(LiveData.validTle("<html>rate limited</html>"));
        assertTrue(LiveData.validTle("ISS\n1 25544 valid\n2 25544 valid\n"));
    }
    @Test public void rejectsWrongMethodBeforeNetworkRequest() {
        LiveData feeds = new LiveData(new File(System.getProperty("java.io.tmpdir"), "fold-test"));
        assertEquals(405, feeds.fetch("https://local/api/adsblol/mil", "POST").status);
        assertEquals(400, feeds.fetch("https://local/api/opensky?lat=invalid&lon=1", "GET").status);
    }
    @Test public void publicFeedSmoke() throws Exception {
        org.junit.Assume.assumeTrue("1".equals(System.getenv("FOLD_LIVE_SMOKE")));
        LiveData feeds = new LiveData(new File(System.getProperty("java.io.tmpdir"), "fold-live-smoke"));
        for (String route : new String[]{"/api/opensky?lat=52.6&lon=-1.2", "/api/adsblol/mil", "/api/celestrak/stations"}) {
            LiveData.Result result = feeds.fetch("https://local" + route, "GET");
            System.out.println("Public feed smoke: " + route + " HTTP " + result.status);
            assertEquals(route + ": " + result.body.substring(0, Math.min(200, result.body.length())), 200, result.status);
            if (route.startsWith("/api/opensky"))
                assertNotNull(new JSONObject(result.body).getJSONArray("states"));
        }
    }
}
