package com.pomogrow.pomosolo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

/**
 * PomoSolo Android —— v0 外壳（安卓端部门 · 自研代码）。
 *
 * 设计说明：
 * 1. 用 {@link WebView} 加载随 APK 打包进 assets 的 PWA 构建产物（见 README「同步 PWA 产物」）；
 * 2. 资源通过 {@link WebViewAssetLoader} 以 https://appassets.androidplatform.net 虚拟源提供，
 *    规避 file:// 下同源/CORS/缓存等限制，XHR/WS/媒体可正常向云端 API 发起请求；
 * 3. 外链（登录/帮助等页面跳转）一律交给系统浏览器，避免应用内被导航走；
 * 4. 计时专注场景保持屏幕常亮（后续由用户在设置里接管）。
 */
public class MainActivity extends Activity {

    private static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net";
    private static final String LOCAL_HOST = "appassets.androidplatform.net";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // assets/ 根目录直接映射到虚拟源的 "/"
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);            // localStorage
        settings.setDatabaseEnabled(true);              // IndexedDB 需要
        settings.setAllowFileAccess(false);             // 只走 assets 虚拟源
        settings.setMediaPlaybackRequiresUserGesture(false); // 音乐自动续播
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (LOCAL_HOST.equals(url.getHost())) {
                    return false; // 本地虚拟源正常加载
                }
                openExternal(url); // 其余页面导航交给系统浏览器
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                // v0 开发期便于排查；正式版可移除
                android.util.Log.d("PomoSolo.WebView",
                        consoleMessage.messageLevel() + ": " + consoleMessage.message());
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        webView.loadUrl(LOCAL_ORIGIN + "/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void openExternal(Uri url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (ActivityNotFoundException e) {
            // 没有可用的浏览器，提示即可
        }
    }
}
