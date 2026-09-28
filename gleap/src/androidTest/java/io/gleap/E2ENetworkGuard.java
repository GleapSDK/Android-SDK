package io.gleap;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the test process off the internet, so no request can reach a real Gleap host:
 * <ul>
 *     <li>Java HTTP stacks (the SDK's HttpURLConnection requests, its OkHttp WebSocket and the test's
 *     OkHttp clients) ask the default {@link ProxySelector} for every connection. Loopback goes
 *     direct; every other host is recorded and sent to a local "blackhole" proxy that records the
 *     request line and closes the connection.</li>
 *     <li>The SDK's WebViews get the same blackhole proxy through androidx.webkit's proxy override;
 *     loopback bypasses it.</li>
 *     <li>The SDK's API requests (GleapHttp) are checked once more before a connection is opened.</li>
 * </ul>
 * The blackhole answers with a malformed status line: HTTP clients treat that as a protocol error
 * and do not fall back to a direct connection (Android's HttpURLConnection does so after a plain
 * proxy connection failure).
 * The tests assert after each test that nothing but loopback was contacted.
 */
final class E2ENetworkGuard extends ProxySelector {
    private static final String TAG = "GleapE2E";

    // Non-loopback requests of the Java HTTP stacks (blocked).
    private static final List<String> blockedJavaRequests = new CopyOnWriteArrayList<>();
    // First line of every request that reached the blackhole proxy (Java or WebView).
    private static final List<String> blackholeRequestLines = new CopyOnWriteArrayList<>();

    private static volatile boolean installed = false;
    private static volatile boolean webViewProxyInstalled = false;
    private static ServerSocket blackhole;

    private E2ENetworkGuard() {
    }

    /**
     * Installs the Java proxy selector and starts the blackhole proxy. Idempotent.
     */
    static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            blackhole = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            throw new AssertionError("Could not start the blackhole proxy", e);
        }
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!blackhole.isClosed()) {
                    try {
                        final Socket socket = blackhole.accept();
                        recordAndClose(socket);
                    } catch (IOException ignore) {
                    }
                }
            }
        }, "e2e-blackhole-proxy");
        acceptor.setDaemon(true);
        acceptor.start();

        ProxySelector.setDefault(new E2ENetworkGuard());
        GleapHttp.setConnectionFactoryForTesting(new GleapHttp.ConnectionFactory() {
            @Override
            public HttpURLConnection open(URL url) throws IOException {
                if (!isLoopback(url.getHost())) {
                    String description = "GleapHttp " + url;
                    blockedJavaRequests.add(description);
                    Log.e(TAG, "Blocked an SDK request to a non-loopback host: " + url);
                    throw new IOException("Blocked by the e2e network guard: " + url);
                }
                return (HttpURLConnection) url.openConnection();
            }
        });
        installed = true;
        Log.i(TAG, "Network guard installed, blackhole proxy on port " + blackhole.getLocalPort());
    }

    /**
     * Routes the WebViews' non-loopback traffic to the blackhole too. Call off the main thread.
     */
    static synchronized void installWebViewProxy() {
        if (webViewProxyInstalled) {
            return;
        }
        install();
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w(TAG, "WebView proxy override not supported: WebView traffic is not guarded");
            return;
        }
        final ProxyConfig config = new ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:" + blackhole.getLocalPort())
                .addBypassRule("127.0.0.1")
                .addBypassRule("localhost")
                .build();
        final CountDownLatch applied = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ProxyController.getInstance().setProxyOverride(config, new java.util.concurrent.Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        applied.countDown();
                    }
                });
            }
        });
        try {
            if (!applied.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("The WebView proxy override was not applied");
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        webViewProxyInstalled = true;
        Log.i(TAG, "WebView proxy override installed");
    }

    private static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("127.0.0.1") || h.equals("localhost") || h.equals("::1") || h.equals("[::1]");
    }

    /**
     * Non-loopback hosts the process tried to reach so far (Java stacks and WebViews), or an empty
     * list.
     */
    static List<String> externalAttempts() {
        List<String> attempts = new ArrayList<>(blockedJavaRequests);
        attempts.addAll(blackholeRequestLines);
        return attempts;
    }

    @Override
    public List<Proxy> select(URI uri) {
        String description = String.valueOf(uri);
        if (uri != null && isLoopback(uri.getHost())) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }
        blockedJavaRequests.add(description);
        Log.e(TAG, "Blocked a request to a non-loopback host: " + description);
        return Collections.singletonList(new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress("127.0.0.1", blackhole.getLocalPort())));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    }

    private static void recordAndClose(Socket socket) {
        try {
            socket.setSoTimeout(2000);
            InputStream in = socket.getInputStream();
            StringBuilder line = new StringBuilder();
            int b;
            while ((b = in.read()) != -1 && b != '\n' && line.length() < 2048) {
                if (b != '\r') {
                    line.append((char) b);
                }
            }
            String requestLine = new String(line.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            blackholeRequestLines.add(requestLine);
            Log.e(TAG, "Blackhole proxy dropped: " + requestLine);
            // A protocol error, so the client gives up instead of trying a direct connection.
            OutputStream out = socket.getOutputStream();
            out.write("HTTP/1.1 E2E-BLOCKED\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException ignore) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
