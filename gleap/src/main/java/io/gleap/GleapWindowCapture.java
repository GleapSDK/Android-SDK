package io.gleap;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import io.gleap.callbacks.GetBitmapCallback;

/**
 * Captures what the app shows in the current activity: its window and the windows above it
 * that belong to it (dialogs, bottom sheets, popups), in window order. For screenshots and the
 * frames of recordings.
 * <ul>
 * <li>API 26+: every window with PixelCopy (off the main thread); SurfaceViews (video, maps,
 * GL, Flutter) are copied on their own and drawn underneath their window, which only has a
 * hole there.</li>
 * <li>Below API 26 (or a window that is not hardware accelerated): {@link View#draw} into a
 * bitmap on the main thread; SurfaceViews are still copied with PixelCopy on API 24+.</li>
 * <li>Windows with FLAG_SECURE, password fields and the views masked with
 * {@link Gleap#maskView} are painted black.</li>
 * <li>A wrapper SDK that renders its own content (Flutter's GetBitmapCallback) provides the
 * picture instead; the masks above are drawn over it all the same.</li>
 * </ul>
 * {@link #capture} must be called on the main thread; the frame is delivered on the given
 * handler's thread.
 */
final class GleapWindowCapture {
    // Views scanned per window at most (for SurfaceViews and masked views). A bigger window
    // cannot be checked for masks: its frame is dropped.
    private static final int MAX_SCANNED_VIEWS = 50000;
    // Masks are bigger than the view, against rounding and a scroll between the scan and the copy.
    private static final float MASK_PADDING_DP = 8f;

    // Tests only: copy windows the way API 26-33 does (their Window) on API 34+ as well.
    static volatile boolean forceWindowCopy = false;

    // The SDK's own windows (the capture bar, the recording preview): never captured and never
    // the window the bar is attached to. Weak, guarded by itself.
    private static final Set<View> ownWindows = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private static volatile Field decorWindowField;
    private static volatile boolean decorWindowFieldLooked;
    private static volatile Field globalViewsField;
    private static volatile Object windowManagerGlobal;

    private GleapWindowCapture() {
    }

    interface Callback {
        /**
         * @param frame the captured frame, or null when there was nothing to capture
         */
        void onFrame(Frame frame);
    }

    /**
     * One layer of a frame, in frame pixels.
     */
    static final class Layer {
        static final int DIM = 0;
        static final int BITMAP = 1;
        static final int BLACK = 2;

        final int type;
        final RectF rect;
        final float alpha;
        // BITMAP only: null while it is copied, or when the copy failed (the layer is left out).
        volatile Bitmap bitmap;
        // The bitmap came from the app (GetBitmapCallback): never recycled or pooled by the SDK.
        boolean external;

        private Layer(int type, RectF rect, float alpha) {
            this.type = type;
            this.rect = rect;
            this.alpha = alpha;
        }
    }

    /**
     * The captured windows as layers, bottom first. Its size is the activity's size times the scale.
     */
    static final class Frame {
        final int areaWidth;
        final int areaHeight;
        final float scale;
        final int width;
        final int height;
        final List<Layer> layers = Collections.synchronizedList(new ArrayList<Layer>());
        // What capturing cost on the main thread, including a redraw after a failed copy.
        volatile long mainThreadMs;
        // The masks could not be worked out: the frame is dropped, never delivered.
        volatile boolean failed;

        Frame(int areaWidth, int areaHeight, float scale) {
            this.areaWidth = areaWidth;
            this.areaHeight = areaHeight;
            this.scale = scale;
            this.width = Math.max(1, Math.round(areaWidth * scale));
            this.height = Math.max(1, Math.round(areaHeight * scale));
        }

        /**
         * Draws the layers at {@code (left, top)}, scaled by {@code drawScale}.
         */
        void draw(Canvas canvas, float left, float top, float drawScale) {
            Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            Paint black = new Paint();
            black.setColor(Color.BLACK);
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(drawScale, drawScale);
            canvas.clipRect(0, 0, width, height);
            synchronized (layers) {
                for (Layer layer : layers) {
                    if (layer.type == Layer.DIM) {
                        Paint dim = new Paint();
                        dim.setColor(Color.argb(Math.round(Math.max(0f, Math.min(1f, layer.alpha)) * 255), 0, 0, 0));
                        canvas.drawRect(0, 0, width, height, dim);
                    } else if (layer.type == Layer.BLACK) {
                        canvas.drawRect(layer.rect, black);
                    } else {
                        Bitmap bitmap = layer.bitmap;
                        if (bitmap != null && !bitmap.isRecycled()) {
                            canvas.drawBitmap(bitmap, null, layer.rect, bitmapPaint);
                        }
                    }
                }
            }
            canvas.restore();
        }

        /**
         * The frame as one bitmap of {@code width x height}.
         */
        Bitmap render() {
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.BLACK);
            draw(canvas, 0, 0, 1f);
            return output;
        }

        /**
         * Gives the layers' bitmaps back to the pool (or recycles them without one).
         */
        void release(BitmapPool pool) {
            synchronized (layers) {
                for (Layer layer : layers) {
                    Bitmap bitmap = layer.bitmap;
                    layer.bitmap = null;
                    if (bitmap == null || layer.external) {
                        continue;
                    }
                    if (pool != null) {
                        pool.release(bitmap);
                    } else {
                        bitmap.recycle();
                    }
                }
            }
        }

        boolean hasContent() {
            synchronized (layers) {
                for (Layer layer : layers) {
                    if (layer.type == Layer.BITMAP && layer.bitmap != null) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Reuses the bitmaps of recording frames (they have the same sizes from frame to frame).
     */
    static final class BitmapPool {
        private static final int MAX_FREE = 8;
        private final ArrayList<Bitmap> free = new ArrayList<>();

        synchronized Bitmap obtain(int width, int height) {
            for (int i = 0; i < free.size(); i++) {
                Bitmap bitmap = free.get(i);
                if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
                    free.remove(i);
                    return bitmap;
                }
            }
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }

        synchronized void release(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            if (free.size() >= MAX_FREE) {
                Bitmap oldest = free.remove(0);
                oldest.recycle();
            }
            free.add(bitmap);
        }

        synchronized void clear() {
            for (Bitmap bitmap : free) {
                bitmap.recycle();
            }
            free.clear();
        }
    }

    /**
     * Counts the copies of a frame that are still running; delivers the frame after the last one.
     */
    private static final class Pending {
        private final AtomicInteger outstanding = new AtomicInteger(1);
        private final Frame frame;
        private final Handler handler;
        private final Callback callback;
        private final BitmapPool pool;

        Pending(Frame frame, Handler handler, Callback callback, BitmapPool pool) {
            this.frame = frame;
            this.handler = handler;
            this.callback = callback;
            this.pool = pool;
        }

        void add() {
            outstanding.incrementAndGet();
        }

        void done() {
            if (outstanding.decrementAndGet() == 0) {
                if (frame.failed) {
                    frame.release(pool);
                    deliver(handler, callback, null);
                } else {
                    deliver(handler, callback, frame);
                }
            }
        }
    }

    /**
     * The mask rects of the previous recording frame, per view: a mask covers where the view is
     * and where it was, since the copied pixels can be a frame older than the view's position
     * (fast scrolling). Main thread only.
     */
    static final class MaskHistory {
        private Map<View, RectF> previous = new WeakHashMap<>();
        private float scale = -1f;
    }

    /**
     * Captures the activity's windows. Main thread only.
     * <p>
     * Every frame is masked (FLAG_SECURE windows, sensitive fields, masked views); when the masks
     * cannot be worked out the frame is dropped (the callback gets null): no unmasked pixels.
     *
     * @param scale         frame pixels per window pixel (1 = full resolution)
     * @param excludedRoots the root views of windows to leave out (the SDK's own bar)
     * @param resultHandler where the copies finish and the frame is delivered (not the main thread)
     * @param pool          bitmaps to reuse, or null
     * @param history       the masks of the previous frame of a recording, or null
     */
    static void capture(Activity activity, float scale, Set<View> excludedRoots, Handler resultHandler,
                        BitmapPool pool, MaskHistory history, Callback callback) {
        long startedAt = SystemClock.uptimeMillis();
        View decor = null;
        try {
            decor = activity != null && activity.getWindow() != null ? activity.getWindow().peekDecorView() : null;
        } catch (Throwable ignore) {
        }
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0 || decor.getWindowToken() == null) {
            deliver(resultHandler, callback, null);
            return;
        }

        int[] origin = new int[2];
        decor.getLocationOnScreen(origin);
        Frame frame = new Frame(decor.getWidth(), decor.getHeight(), scale);
        Pending pending = new Pending(frame, resultHandler, callback, pool);
        try {
            Set<View> masked = GleapCapture.maskedViews();
            List<View> roots = windowRoots(activity, decor, excludedRoots);
            Map<View, RectF> masks = new WeakHashMap<>();
            float padding = MASK_PADDING_DP * decor.getResources().getDisplayMetrics().density * scale;
            // Below API 24 the content of a SurfaceView (Flutter) cannot be copied: a wrapper that
            // renders its own content hands over its picture of the activity instead.
            Bitmap hostBitmap = Build.VERSION.SDK_INT < Build.VERSION_CODES.N ? hostBitmap() : null;
            if (hostBitmap != null) {
                Layer layer = new Layer(Layer.BITMAP, new RectF(0, 0, frame.width, frame.height), 1f);
                layer.bitmap = hostBitmap;
                layer.external = true;
                frame.layers.add(layer);
                for (View root : roots) {
                    addMasks(root, origin, frame, masked, padding, history, masks);
                }
            } else {
                for (View root : roots) {
                    addWindow(activity, decor, root, origin, frame, masked, padding, history, masks, pool, pending, resultHandler);
                }
            }
            if (history != null) {
                history.previous = masks;
                history.scale = scale;
            }
        } catch (Throwable error) {
            // Without its masks the frame must not leave the device.
            GleapLog.w("Could not mask a capture: the frame is dropped", error);
            frame.failed = true;
        }
        frame.mainThreadMs = SystemClock.uptimeMillis() - startedAt;
        pending.done();
    }

    private static void addWindow(Activity activity, View decor, View root, int[] origin, Frame frame,
                                  Set<View> masked, float padding, MaskHistory history, Map<View, RectF> masks,
                                  BitmapPool pool, Pending pending, Handler resultHandler) {
        WindowManager.LayoutParams params = windowParams(root);
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        float scale = frame.scale;
        RectF windowRect = scaled(location[0] - origin[0], location[1] - origin[1], root.getWidth(), root.getHeight(), scale);

        if (root != decor && params != null && (params.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
            frame.layers.add(new Layer(Layer.DIM, null, params.dimAmount));
        }
        if (params != null && (params.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            // The app keeps this window out of screenshots: so does the SDK.
            frame.layers.add(new Layer(Layer.BLACK, windowRect, 1f));
            return;
        }

        List<SurfaceView> surfaces = new ArrayList<>();
        List<View> sensitive = new ArrayList<>();
        scan(root, masked, surfaces, sensitive);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (SurfaceView surface : surfaces) {
                addSurface(surface, origin, frame, pool, pending, resultHandler);
            }
        }

        Layer windowLayer = new Layer(Layer.BITMAP, windowRect, 1f);
        frame.layers.add(windowLayer);
        copyWindow(activity, decor, root, windowLayer, pool, pending, resultHandler);

        addMaskLayers(sensitive, location, origin, frame, padding, history, masks);
    }

    /**
     * Black boxes for a window over a picture the SDK did not take itself (GetBitmapCallback,
     * drawn over the whole activity): its sensitive views, or all of it with FLAG_SECURE.
     */
    private static void addMasks(View root, int[] origin, Frame frame, Set<View> masked, float padding,
                                 MaskHistory history, Map<View, RectF> masks) {
        WindowManager.LayoutParams params = windowParams(root);
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        if (params != null && (params.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            frame.layers.add(new Layer(Layer.BLACK, scaled(location[0] - origin[0], location[1] - origin[1],
                    root.getWidth(), root.getHeight(), frame.scale), 1f));
            return;
        }
        List<SurfaceView> surfaces = new ArrayList<>();
        List<View> sensitive = new ArrayList<>();
        scan(root, masked, surfaces, sensitive);
        addMaskLayers(sensitive, location, origin, frame, padding, history, masks);
    }

    /**
     * The visible part of each view, from window to frame coordinates, padded, joined with where
     * the view was in the previous frame. Throws when a mask cannot be worked out (the frame is
     * then dropped).
     */
    private static void addMaskLayers(List<View> views, int[] windowLocation, int[] origin, Frame frame, float padding,
                                      MaskHistory history, Map<View, RectF> masks) {
        Rect visible = new Rect();
        for (View view : views) {
            if (!view.getGlobalVisibleRect(visible)) {
                // Nothing of it is on screen.
                continue;
            }
            RectF mask = scaled(visible.left + windowLocation[0] - origin[0], visible.top + windowLocation[1] - origin[1],
                    visible.width(), visible.height(), frame.scale);
            mask.inset(-padding, -padding);
            masks.put(view, new RectF(mask));
            if (history != null && history.scale == frame.scale) {
                RectF previous = history.previous.get(view);
                if (previous != null) {
                    mask.union(previous);
                }
            }
            frame.layers.add(new Layer(Layer.BLACK, mask, 1f));
        }
    }

    private static RectF scaled(float left, float top, float width, float height, float scale) {
        return new RectF(left * scale, top * scale, (left + width) * scale, (top + height) * scale);
    }

    private static int scaledSize(int size, float scale) {
        return Math.max(1, Math.round(size * scale));
    }

    // ---------------------------------------------------------------------------------------------
    // Copying

    private static void copyWindow(final Activity activity, final View decor, final View root, final Layer layer,
                                   final BitmapPool pool, final Pending pending, final Handler resultHandler) {
        final float scale = layer.rect.width() / Math.max(1, root.getWidth());
        final Bitmap bitmap = obtain(pool, scaledSize(root.getWidth(), scale), scaledSize(root.getHeight(), scale));
        if (bitmap == null) {
            return;
        }
        boolean hardware = root.isHardwareAccelerated();
        boolean started = false;
        try {
            CopyResult result = new CopyResult() {
                @Override
                public void onResult(boolean success) {
                    finishWindowCopy(root, layer, bitmap, scale, success, pool, pending);
                }
            };
            if (Build.VERSION.SDK_INT >= 34 && hardware && !forceWindowCopy) {
                pending.add();
                started = true;
                requestWindowCopy(root, bitmap, result, resultHandler);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hardware) {
                Window window = windowOf(activity, decor, root);
                if (window != null) {
                    pending.add();
                    started = true;
                    requestWindowCopy(window, bitmap, result, resultHandler);
                    return;
                }
            }
        } catch (Throwable error) {
            // E.g. the window has no surface (anymore): draw it instead. The copy did not start.
            if (started) {
                pending.done();
            }
        }
        drawWindow(root, layer, bitmap, scale, pool);
    }

    interface CopyResult {
        void onResult(boolean success);
    }

    @TargetApi(34)
    private static void requestWindowCopy(View root, Bitmap bitmap, final CopyResult result, final Handler handler) {
        // The window's own area: without a source rect ofWindow(View) takes the surface insets
        // (a dialog's shadow) as the source rect, and the whole surface is copied, scaled down.
        PixelCopy.Request request = PixelCopy.Request.Builder.ofWindow(root)
                .setSourceRect(new Rect(0, 0, root.getWidth(), root.getHeight()))
                .setDestinationBitmap(bitmap)
                .build();
        PixelCopy.request(request, executor(handler), new java.util.function.Consumer<PixelCopy.Result>() {
            @Override
            public void accept(PixelCopy.Result copy) {
                result.onResult(copy.getStatus() == PixelCopy.SUCCESS);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static void requestWindowCopy(Window window, Bitmap bitmap, final CopyResult result, Handler handler) {
        PixelCopy.request(window, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int copyResult) {
                result.onResult(copyResult == PixelCopy.SUCCESS);
            }
        }, handler);
    }

    // On the result thread: a failed copy is drawn on the main thread instead.
    private static void finishWindowCopy(final View root, final Layer layer, final Bitmap bitmap, final float scale,
                                         boolean success, final BitmapPool pool, final Pending pending) {
        if (success) {
            layer.bitmap = bitmap;
            pending.done();
            return;
        }
        GleapMainThread.post(new Runnable() {
            @Override
            public void run() {
                long startedAt = SystemClock.uptimeMillis();
                try {
                    drawWindow(root, layer, bitmap, scale, pool);
                } finally {
                    pending.frame.mainThreadMs += SystemClock.uptimeMillis() - startedAt;
                    pending.done();
                }
            }
        });
    }

    /**
     * Draws the window's views into the bitmap (main thread). Views that cannot be drawn in
     * software (e.g. hardware bitmaps) leave the window out.
     */
    private static void drawWindow(View root, Layer layer, Bitmap bitmap, float scale, BitmapPool pool) {
        try {
            bitmap.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(bitmap.getWidth() / (float) Math.max(1, root.getWidth()),
                    bitmap.getHeight() / (float) Math.max(1, root.getHeight()));
            root.draw(canvas);
            layer.bitmap = bitmap;
        } catch (Throwable error) {
            GleapLog.w("Could not draw a window for the capture", error);
            release(pool, bitmap);
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static void addSurface(SurfaceView surface, int[] origin, Frame frame, final BitmapPool pool,
                                   final Pending pending, Handler resultHandler) {
        try {
            if (surface.getWidth() <= 0 || surface.getHeight() <= 0 || !surface.getHolder().getSurface().isValid()) {
                return;
            }
            int[] location = new int[2];
            surface.getLocationOnScreen(location);
            final Layer layer = new Layer(Layer.BITMAP,
                    scaled(location[0] - origin[0], location[1] - origin[1], surface.getWidth(), surface.getHeight(), frame.scale), 1f);
            final Bitmap bitmap = obtain(pool, scaledSize(surface.getWidth(), frame.scale), scaledSize(surface.getHeight(), frame.scale));
            if (bitmap == null) {
                return;
            }
            // Below the window: the window only has a hole where the surface shows.
            frame.layers.add(layer);
            pending.add();
            try {
                PixelCopy.request(surface, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                    @Override
                    public void onPixelCopyFinished(int copyResult) {
                        if (copyResult == PixelCopy.SUCCESS) {
                            layer.bitmap = bitmap;
                        } else {
                            release(pool, bitmap);
                        }
                        pending.done();
                    }
                }, resultHandler);
            } catch (Throwable error) {
                release(pool, bitmap);
                pending.done();
            }
        } catch (Throwable ignore) {
        }
    }

    private static Bitmap obtain(BitmapPool pool, int width, int height) {
        try {
            return pool != null ? pool.obtain(width, height) : Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable error) {
            // Out of memory: the window is left out.
            GleapLog.w("No memory for a capture bitmap", error);
            return null;
        }
    }

    private static void release(BitmapPool pool, Bitmap bitmap) {
        if (pool != null) {
            pool.release(bitmap);
        } else if (bitmap != null) {
            bitmap.recycle();
        }
    }

    private static Executor executor(final Handler handler) {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    private static void deliver(Handler handler, final Callback callback, final Frame frame) {
        Runnable delivery = new Runnable() {
            @Override
            public void run() {
                try {
                    callback.onFrame(frame);
                } catch (Throwable error) {
                    GleapLog.w("Could not handle a captured frame", error);
                }
            }
        };
        if (handler != null) {
            handler.post(delivery);
        } else {
            new Handler(Looper.getMainLooper()).post(delivery);
        }
    }

    private static Bitmap hostBitmap() {
        try {
            GetBitmapCallback bitmapCallback = GleapCallbacks.getInstance().getGetBitmapCallback();
            if (bitmapCallback == null) {
                return null;
            }
            Bitmap bitmap = bitmapCallback.getBitmap();
            return bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 ? bitmap : null;
        } catch (Throwable error) {
            GleapLog.w("The app's bitmap callback failed", error);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Windows

    /**
     * The windows of the activity to capture, bottom first: the activity's window, then its
     * dialogs in the order they were shown, each followed by its panels (popups, menus).
     */
    static List<View> windowRoots(Activity activity, View decor, Set<View> excludedRoots) {
        List<View> ordered = new ArrayList<>();
        ordered.add(decor);
        List<View> all = globalWindowViews();
        if (all.isEmpty()) {
            return ordered;
        }

        IBinder appToken = null;
        try {
            appToken = activity.getWindow().getAttributes().token;
        } catch (Throwable ignore) {
        }

        List<View> topLevel = new ArrayList<>();
        topLevel.add(decor);
        for (View view : all) {
            if (view == decor || !isCapturable(view, excludedRoots)) {
                continue;
            }
            WindowManager.LayoutParams params = windowParams(view);
            if (params == null || appToken == null || params.token != appToken) {
                continue;
            }
            if (params.type > WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                    && params.type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
                    && params.type != WindowManager.LayoutParams.TYPE_APPLICATION_STARTING) {
                topLevel.add(view);
            }
        }

        ordered.clear();
        for (View window : topLevel) {
            ordered.add(window);
            IBinder parentToken = window.getWindowToken();
            if (parentToken == null) {
                continue;
            }
            for (View view : all) {
                if (view == window || !isCapturable(view, excludedRoots)) {
                    continue;
                }
                WindowManager.LayoutParams params = windowParams(view);
                if (params != null && params.token == parentToken
                        && params.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
                        && params.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                    ordered.add(view);
                }
            }
        }
        return ordered;
    }

    /**
     * The window the SDK's bar belongs on: the topmost dialog of the activity, else the
     * activity's own window.
     */
    static View topWindow(Activity activity, View decor, Set<View> excludedRoots) {
        View top = decor;
        IBinder appToken;
        try {
            appToken = activity.getWindow().getAttributes().token;
        } catch (Throwable error) {
            return decor;
        }
        for (View view : globalWindowViews()) {
            if (view == decor || !isCapturable(view, excludedRoots)) {
                continue;
            }
            WindowManager.LayoutParams params = windowParams(view);
            if (params != null && appToken != null && params.token == appToken
                    && params.type > WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                    && params.type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
                    && params.type != WindowManager.LayoutParams.TYPE_APPLICATION_STARTING
                    && view.getWindowToken() != null) {
                top = view;
            }
        }
        return top;
    }

    static void addOwnWindow(View root) {
        if (root != null) {
            synchronized (ownWindows) {
                ownWindows.add(root);
            }
        }
    }

    static void removeOwnWindow(View root) {
        if (root != null) {
            synchronized (ownWindows) {
                ownWindows.remove(root);
            }
        }
    }

    private static boolean isOwnWindow(View root) {
        synchronized (ownWindows) {
            return ownWindows.contains(root);
        }
    }

    private static boolean isCapturable(View view, Set<View> excludedRoots) {
        return view != null
                && !isOwnWindow(view)
                && (excludedRoots == null || !excludedRoots.contains(view))
                && view.getWindowToken() != null
                && view.getVisibility() == View.VISIBLE
                && view.getWidth() > 0
                && view.getHeight() > 0;
    }

    static WindowManager.LayoutParams windowParams(View root) {
        ViewGroup.LayoutParams params = root.getLayoutParams();
        return params instanceof WindowManager.LayoutParams ? (WindowManager.LayoutParams) params : null;
    }

    /**
     * The root views of all windows of the process (WindowInspector on API 29+, WindowManagerGlobal
     * below); empty when they cannot be read.
     */
    @SuppressWarnings("unchecked")
    static List<View> globalWindowViews() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return new ArrayList<>(WindowInspector.getGlobalWindowViews());
            }
            Field field = globalViewsField;
            Object global = windowManagerGlobal;
            if (field == null || global == null) {
                Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
                global = globalClass.getMethod("getInstance").invoke(null);
                field = globalClass.getDeclaredField("mViews");
                field.setAccessible(true);
                windowManagerGlobal = global;
                globalViewsField = field;
            }
            Object views = field.get(global);
            if (views instanceof List) {
                return new ArrayList<>((List<View>) views);
            }
        } catch (Throwable ignore) {
        }
        return new ArrayList<>();
    }

    /**
     * The Window of a root view: the activity's, or a dialog's (its DecorView knows it).
     */
    private static Window windowOf(Activity activity, View decor, View root) {
        if (root == decor) {
            return activity.getWindow();
        }
        try {
            if (!decorWindowFieldLooked) {
                Field field = null;
                for (Class<?> type = root.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                    if (type.getName().endsWith("DecorView")) {
                        field = type.getDeclaredField("mWindow");
                        field.setAccessible(true);
                        break;
                    }
                }
                decorWindowField = field;
                decorWindowFieldLooked = field != null;
            }
            Field field = decorWindowField;
            if (field != null && field.getDeclaringClass().isInstance(root)) {
                Object window = field.get(root);
                return window instanceof Window ? (Window) window : null;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    /**
     * Finds the SurfaceViews and the views to paint black (password fields, masked views) in a
     * window. Hidden subtrees are skipped; a masked view's children are covered by its mask.
     */
    private static void scan(View root, Set<View> masked, List<SurfaceView> surfaces, List<View> masks) {
        ArrayDeque<View> stack = new ArrayDeque<>();
        stack.push(root);
        int budget = MAX_SCANNED_VIEWS;
        while (!stack.isEmpty()) {
            if (budget-- <= 0) {
                throw new IllegalStateException("Too many views to check for masks");
            }
            View view = stack.pop();
            if (view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (masked.contains(view) || isSensitive(view)) {
                masks.add(view);
                continue;
            }
            if (view instanceof SurfaceView) {
                surfaces.add((SurfaceView) view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = group.getChildCount() - 1; i >= 0; i--) {
                    View child = group.getChildAt(i);
                    if (child != null) {
                        stack.push(child);
                    }
                }
            }
        }
    }

    /**
     * Fields painted black in every capture: passwords (text, number and web passwords, also
     * while shown), and fields the app marked for autofill as a password, a payment card or a
     * one-time code.
     */
    static boolean isSensitive(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (isPasswordInputType(text.getInputType())
                    || text.getTransformationMethod() instanceof PasswordTransformationMethod) {
                return true;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String[] hints = view.getAutofillHints();
            if (hints != null) {
                for (String hint : hints) {
                    if (isSensitiveAutofillHint(hint)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Autofill hints of passwords, payment cards and one-time codes (the framework's View
     * AUTOFILL_HINT_* and androidx HintConstants names, e.g. password, newPassword,
     * creditCardNumber, creditCardSecurityCode, creditCardExpirationDate, smsOTPCode).
     */
    static boolean isSensitiveAutofillHint(String hint) {
        if (hint == null) {
            return false;
        }
        String value = hint.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return value.contains("password") || value.contains("creditcard") || value.contains("otp")
                || value.contains("onetimecode") || value.contains("securitycode") || value.contains("cvc")
                || value.contains("cvv") || value.equals("pin");
    }

    static boolean isPasswordInputType(int inputType) {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
}
