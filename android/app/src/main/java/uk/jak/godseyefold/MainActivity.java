package uk.jak.godseyefold;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String LOCAL = "https://" + HOST + "/index.html";
    private WebView web;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> fileResult;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 20, 30));
        // Apply system and keyboard insets without recreating the globe when folding.
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout toolbar = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText("EYE FOLD 0.3.0");
        title.setTextColor(Color.rgb(0, 212, 255));
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.setPadding(16, 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button settings = new Button(this);
        settings.setText("Live");
        settings.setOnClickListener(view -> showLiveStatus());
        settings.setOnLongClickListener(view -> { showConnection(); return true; });
        toolbar.addView(settings);
        root.addView(toolbar);
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        WebSettings config = web.getSettings();
        config.setJavaScriptEnabled(true);
        config.setDomStorageEnabled(true);
        config.setAllowFileAccess(false);
        config.setAllowContentAccess(false);
        config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        config.setMediaPlaybackRequiresUserGesture(true);
        LiveData live = new LiveData(new java.io.File(getCacheDir(), "satellite-catalogs"));
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost()) && uri.getPath().startsWith("/api/")) {
                    LiveData.Result result = live.fetch(uri.toString(), request.getMethod());
                    if (result != null) return new WebResourceResponse(result.mime, "UTF-8", result.status,
                        result.status == 200 ? "OK" : "Live feed unavailable", result.headers,
                        new ByteArrayInputStream(result.body.getBytes(StandardCharsets.UTF_8)));
                    byte[] body = "{\"error\":\"This feed needs a connected server. Tap Connect.\"}"
                        .getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("application/json", "UTF-8", 503,
                        "Server connection required", Collections.singletonMap("Cache-Control", "no-store"),
                        new ByteArrayInputStream(body));
                }
                return loader.shouldInterceptRequest(uri);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                Uri origin = Uri.parse(startUrl());
                if ("https".equals(uri.getScheme()) && origin.getHost().equals(uri.getHost())
                    && origin.getPort() == uri.getPort()) return false;
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException error) { Toast.makeText(MainActivity.this,
                        "No browser available", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                try {
                    String css;
                    try (var stream = getAssets().open("fold.css")) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        css = bytes.toString(StandardCharsets.UTF_8.name());
                    }
                    view.evaluateJavascript("document.documentElement.dataset.foldApp='true';"
                        + "document.documentElement.dataset.bundledGlobe="
                        + JSONObject.quote(Boolean.toString(HOST.equals(Uri.parse(url).getHost()))) + ";"
                        + "(()=>{let s=document.getElementById('native-fold-style');"
                        + "if(!s){s=document.createElement('style');s.id='native-fold-style';"
                        + "document.head.append(s);}s.textContent=" + JSONObject.quote(css) + ";})();", null);
                    try (var script = getAssets().open("live-controls.js")) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = script.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        view.evaluateJavascript(bytes.toString(StandardCharsets.UTF_8.name()), null);
                    }
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Fold controls could not load", Toast.LENGTH_LONG).show();
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                FileChooserParams params) {
                if (fileResult != null) fileResult.onReceiveValue(null);
                fileResult = callback;
                try { startActivityForResult(params.createIntent(), 100); return true; }
                catch (ActivityNotFoundException error) {
                    fileResult.onReceiveValue(null); fileResult = null; return false;
                }
            }
        });
        web.loadUrl(startUrl());
        if (!prefs.getBoolean("explained", false)) {
            new AlertDialog.Builder(this).setTitle("Your first Fold test")
                .setMessage("The globe is bundled with this app. Internet is needed for map imagery. "
                    + "Aircraft and satellite catalogs now load directly. Ships, cameras and AI still need a server. "
                    + "Voice and downloading scene exports are not supported in this first build.")
                .setPositiveButton("Open globe", (dialog, which) ->
                    prefs.edit().putBoolean("explained", true).apply()).show();
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String startUrl() { return prefs.getString("url", LOCAL); }

    WebView browserForTest() { return web; }

    private void showLiveStatus() {
        web.evaluateJavascript("window.foldLiveStart?.();window.foldLiveCheck?.();", null);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Live feeds — 0.3.0")
            .setMessage("Checking feeds…").setPositiveButton("Close", null)
            .setNeutralButton("Server settings", (d, which) -> showConnection())
            .setNegativeButton("Refresh", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v ->
            web.evaluateJavascript("window.foldLiveStart?.();window.foldLiveCheck?.();", null));
        Runnable poll = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing() || isDestroyed()) return;
                web.evaluateJavascript("JSON.stringify({check:window.foldLiveDiagnostic||{state:'Globe loading…',results:[]},"
                    + "layers:['flights','military','satellites'].map(id=>({id,text:document.querySelector('[data-layer-id='+id+']')?.innerText||'Not ready'}))})",
                    encoded -> {
                        if (!dialog.isShowing()) return;
                        try {
                            String decoded = new org.json.JSONArray("[" + encoded + "]").getString(0);
                            JSONObject value = new JSONObject(decoded);
                            JSONObject check = value.getJSONObject("check");
                            StringBuilder message = new StringBuilder(check.getString("state"));
                            org.json.JSONArray results = check.getJSONArray("results");
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject result = results.getJSONObject(i);
                                message.append("\n\n").append(result.getString("name")).append(": ");
                                if (result.optInt("status") == 200)
                                    message.append(result.optInt("count")).append(" records");
                                else message.append("HTTP ").append(result.optInt("status"))
                                    .append(" — ").append(result.optString("detail"));
                            }
                            message.append("\n\nDisplayed layers:");
                            org.json.JSONArray layers = value.getJSONArray("layers");
                            for (int i = 0; i < layers.length(); i++)
                                message.append("\n").append(layers.getJSONObject(i).getString("text"));
                            message.append("\n\nAircraft check uses Leicester. The globe shows aircraft around your current view.");
                            dialog.setMessage(message.toString());
                        } catch (Exception error) { dialog.setMessage("Globe is still loading. Tap Refresh when ready."); }
                    });
                web.postDelayed(this, 1000);
            }
        };
        web.post(poll);
    }

    private void showConnection() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://your-server.example");
        if (!LOCAL.equals(startUrl())) input.setText(startUrl());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connect your server")
            .setMessage("Enter the HTTPS address hosting your God's Eye View frontend and APIs. "
                + "Use a server you control. Leave blank to return to the bundled globe.")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Connect", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String value = input.getText().toString().trim();
                Uri uri = Uri.parse(value);
                if (!value.isEmpty() && (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || HOST.equals(uri.getHost()))) {
                    input.setError("Enter a valid HTTPS server address"); return;
                }
                prefs.edit().putString("url", value.isEmpty() ? LOCAL : value).apply();
                web.loadUrl(startUrl());
                dialog.dismiss();
            }));
        dialog.show();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        web.requestLayout();
        web.post(() -> web.evaluateJavascript("window.dispatchEvent(new Event('resize'))", null));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100 && fileResult != null) {
            fileResult.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileResult = null;
        }
    }
    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (fileResult != null) fileResult.onReceiveValue(null);
        web.destroy(); super.onDestroy();
    }
}
