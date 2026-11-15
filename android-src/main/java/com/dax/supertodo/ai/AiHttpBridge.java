package com.dax.supertodo.ai;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 原生 AI HTTP 请求桥。
 *
 * <p>在 Android 原生层直接发起 HttpURLConnection 请求，避免 WebView 受到
 * 第三方接口缺失 CORS 响应头的拦截，解决打包后依然出现 Failed to fetch 的问题。</p>
 */
public class AiHttpBridge {

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 90000;
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String MIME_JSON_UTF8 = "application/json; charset=UTF-8";

    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 创建原生 AI 请求桥接对象。
     *
     * @param activity 宿主 Activity
     * @param webView 当前承载前端页面的 WebView，用于回调 JS 结果
     */
    public AiHttpBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /**
     * 异步发起 JSON POST 请求，并在完成后回调前端 window.onNativeAiResponse。
     *
     * @param requestId 前端分配的请求标识
     * @param urlString 完整请求地址
     * @param apiKey Bearer 鉴权密钥
     * @param bodyJson 请求体 JSON 字符串
     */
    @JavascriptInterface
    public void postJson(
            final String requestId,
            final String urlString,
            final String apiKey,
            final String bodyJson
    ) {
        if (activity == null || webView == null) {
            return;
        }
        executor.execute(() -> {
            int statusCode = -1;
            String responseBody = "";
            String errorMsg = "";
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestProperty(HEADER_CONTENT_TYPE, MIME_JSON_UTF8);
                if (apiKey != null && !apiKey.isEmpty()) {
                    connection.setRequestProperty(HEADER_AUTHORIZATION, "Bearer " + apiKey);
                }
                byte[] payload = (bodyJson != null ? bodyJson : "{}").getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload);
                    os.flush();
                }
                statusCode = connection.getResponseCode();
                InputStream stream = statusCode >= 200 && statusCode < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                if (stream != null) {
                    responseBody = readAll(stream);
                }
            } catch (Exception e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Native network error";
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            deliverResult(requestId, statusCode, responseBody, errorMsg);
        });
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void deliverResult(String requestId, int statusCode, String responseBody, String errorMsg) {
        if (webView == null) {
            return;
        }
        String quotedId = JSONObject.quote(requestId != null ? requestId : "");
        String quotedBody = JSONObject.quote(responseBody != null ? responseBody : "");
        String quotedErr = JSONObject.quote(errorMsg != null ? errorMsg : "");
        String script = "if(window.onNativeAiResponse){window.onNativeAiResponse("
                + quotedId + "," + statusCode + "," + quotedBody + "," + quotedErr + ");}";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
