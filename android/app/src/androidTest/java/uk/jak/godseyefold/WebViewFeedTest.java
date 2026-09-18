package uk.jak.godseyefold;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WebViewFeedTest {
    private String evaluate(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> output = new AtomicReference<>();
        scenario.onActivity(activity -> activity.browserForTest().evaluateJavascript(script, value -> {
            output.set(value); done.countDown();
        }));
        assertTrue("WebView callback timed out", done.await(15, TimeUnit.SECONDS));
        return output.get();
    }

    @Test public void browserFetchesNativePublicFeeds() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("connection", Context.MODE_PRIVATE).edit()
            .clear().putBoolean("explained", true).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.browserForTest().loadUrl(
                "https://appassets.androidplatform.net/feed-test.html"));
            long deadline = System.currentTimeMillis() + 60000;
            while (!"true".equals(evaluate(scenario, "typeof window.foldLiveCheck === 'function'"))) {
                assertTrue("Native controls were not injected: " + evaluate(scenario,
                    "JSON.stringify({url:location.href,ready:document.readyState,html:document.documentElement.outerHTML,ua:navigator.userAgent})"),
                    System.currentTimeMillis() < deadline);
                Thread.sleep(250);
            }
            evaluate(scenario, "window.foldLiveCheck();");
            deadline = System.currentTimeMillis() + 65000;
            String encoded;
            do {
                encoded = evaluate(scenario, "JSON.stringify(window.foldLiveDiagnostic)");
                assertTrue("Browser feed test timed out: " + encoded, System.currentTimeMillis() < deadline);
                Thread.sleep(500);
            } while (!encoded.contains("Feed check complete"));
            JSONObject report = new JSONObject(new JSONArray("[" + encoded + "]").getString(0));
            JSONArray results = report.getJSONArray("results");
            assertEquals(report.toString(), 3, results.length());
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                assertEquals(result.toString(), 200, result.getInt("status"));
                assertTrue(result.toString(), result.getInt("count") > 0);
            }
        }
    }
}
