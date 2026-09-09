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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;

import java.util.Collections;

/**
 * PomoSolo Android —— v0 外壳（安卓端部门 · 自研代码）。
 *
 * 设计说明：
 * 1. 用 {@link WebView} 加载随 APK 打包进 assets 的 PWA 构建产物（见 README「同步 PWA 产物」）；
 * 2. 资源通过 {@link WebViewAssetLoader} 以 https://appassets.androidplatform.net 虚拟源提供，
 *    规避 file:// 下同源/CORS/缓存等限制，XHR/WS/媒体可正常向云端 API 发起请求；
 * 3. 外链（登录/帮助等页面跳转）一律交给系统浏览器，避免应用内被导航走；
 * 4. 计时专注场景保持屏幕常亮（后续由用户在设置里接管）。
 *
 * 云端 API 域名适配（详见 {@link #REMOTE_API_ADAPTER_JS}）：
 * PWA 构建产物按"生产同源"（VITE_API_ORIGIN 为空）打出的包，其 /api、/ws、/music
 * 请求会相对当前域名解析 → 落到不存在的虚拟源 host，导致连接失败。
 * 本壳在页面启动前注入适配脚本，把这些请求改写为真实云端域名 api.pomogrow.top
 * （跨域由服务器 CORS 白名单放行 appassets.androidplatform.net）。
 */
public class MainActivity extends Activity {

    private static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net";
    private static final String LOCAL_HOST = "appassets.androidplatform.net";

    /** 云端 API/WS 权威域名（与服务器部门约定，见原仓库 server-planning/EXTERNAL-INTERFACES.md） */
    private static final String REMOTE_ORIGIN = "https://api.pomogrow.top";

    /**
     * 页面启动前注入的适配脚本（安卓端部门自研）：
     * 仅把「本地虚拟源 + /api|/ws|/music 路径」的请求主机改写为云端域名，
     * 其余本地资源（index/assets/tracks/manifest 等）保持不变。
     * 同时给 WebSocket 补上 wss 协议改写。
     */
    private static final String REMOTE_API_ADAPTER_JS = """
            (() => {
              const REMOTE = "https://api.pomogrow.top";
              const LOCAL = location.origin;
              const isRemotePath = (p) =>
                p === "/api/status" || p.startsWith("/api/") ||
                p.startsWith("/ws") || p.startsWith("/music/");
              const remap = (input) => {
                try {
                  const url = new URL(String(input), location.href);
                  if (url.origin !== LOCAL || !isRemotePath(url.pathname)) return url.href;
                  return REMOTE + url.pathname + url.search;
                } catch (e) {
                  return String(input);
                }
              };
              const origFetch = window.fetch;
              window.fetch = (input, init) => {
                if (typeof input === "string") return origFetch(remap(input), init);
                if (input instanceof Request) {
                  const u = remap(input.url);
                  input = u === input.url ? input : new Request(u, input);
                }
                return origFetch(input, init);
              };
              const origOpen = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function (method, url) {
                return origOpen.apply(this,
                  [method, remap(url)].concat(Array.prototype.slice.call(arguments, 2)));
              };
              if (window.WebSocket) {
                const NativeWS = window.WebSocket;
                const PatchedWS = function (url, protocols) {
                  const target = remap(url).replace(/^http:/, "ws:").replace(/^https:/, "wss:");
                  return protocols === undefined
                    ? new NativeWS(target)
                    : new NativeWS(target, protocols);
                };
                PatchedWS.prototype = NativeWS.prototype;
                PatchedWS.CONNECTING = NativeWS.CONNECTING;
                PatchedWS.OPEN = NativeWS.OPEN;
                PatchedWS.CLOSING = NativeWS.CLOSING;
                PatchedWS.CLOSED = NativeWS.CLOSED;
                window.WebSocket = PatchedWS;
              }
              console.log("[AndroidShell] cloud adapter installed -> " + REMOTE);
            })();
            """;

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
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 关键：把请求交给 WebViewAssetLoader 拦截。
                // appassets.androidplatform.net 是虚拟源，必须在此返回 assets 内容；
                // 否则 WebView 会真的去解析 DNS → ERR_NAME_NOT_RESOLVED。
                // 非本地源（真实外链/API）会返回 null，继续走正常网络。
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

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

        // 页面启动前注入云端域名适配脚本（必须在首次 loadUrl 之前注册，只对本虚拟源生效）
        WebViewCompat.addDocumentStartJavaScript(
                webView, REMOTE_API_ADAPTER_JS, Collections.singleton(LOCAL_ORIGIN));

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
