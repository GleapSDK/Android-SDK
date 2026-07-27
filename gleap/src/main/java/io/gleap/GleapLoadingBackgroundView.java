package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Loading placeholder shown while the messenger webview boots. Mirrors the
 * messenger's home background (and the web/iOS SDK loaders) so the hand-off to
 * the webview is seamless:
 *
 * - v4 (v >= 4 or unset — matches the messenger's resolveHomeVersion): a
 *   header-high (~360dp) brand surface — image or headerColor → headerColor2
 *   gradient — easing into the background color across the composer zone via a
 *   SMOOTHSTEP ramp (zero slope at both ends; sparse linear stops show a
 *   visible "hard switch" Mach band where the fade begins).
 * - v1-3 classic: diagonal headerColor2 → headerColor region fading into the
 *   background (geometry from BGclassic.svg / BGclassicnofade.svg).
 * - v1-3 gradient: the three BG.svg colour blobs. With bgBlur (default) the
 *   messenger shows them behind a 30px backdrop blur, so they are pre-rendered
 *   into a bitmap and softened via iterative filtered downscale/upscale — the
 *   loader is static, so this is visually equivalent to the live blur.
 * - image bgType: the background image cover-cropped into the SAME box the
 *   messenger uses (v4: header box; v1/v2: above the 80dp docked tab bar;
 *   v3: full-bleed) — a different box computes a different crop and the image
 *   would visibly shift at the reveal. The image fades in once downloaded.
 *
 * All geometry constants are hand-copied from the Messenger-App
 * (AgentHomeV4.scss, HomePage.scss, BG*.svg) and must track it.
 */
class GleapLoadingBackgroundView extends View {
    private static final float HEADER_HEIGHT_DP = 360f;
    private static final float DOCKED_TAB_BAR_DP = 80f;
    private static final int IMAGE_FADE_IN_MS = 250;

    // BG.svg blob polygons (viewBox 403x598, straight segments only).
    private static final float[][] BLOB_HEADER = {{0, 0}, {403, 0}, {403, 308.5f}, {350.5f, 298.5f}, {294, 298.5f}, {144.5f, 250}, {78.5f, 152.5f}, {27, 125}, {0, 104}};
    private static final float[][] BLOB_HEADER2 = {{0, 151}, {0, 101.5f}, {137, 101.5f}, {156, 151}, {352, 300}, {106, 340}, {0, 344.5f}};
    private static final float[][] BLOB_HEADER3 = {{254.5f, 118}, {331.5f, 94}, {403, 85}, {403, 318}, {347.5f, 318}, {221, 207}};

    private final int backgroundColor;
    private final int headerColor;
    private final int headerColor2;
    private final int headerColor3;
    private final String bgType;
    private final boolean isV4;
    private final int homeVersion;
    private final boolean fadeBg;
    private final boolean bgBlur;
    private final float density;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap blobBitmap; // cached pre-blurred blobs for the current size
    private Bitmap imageBitmap; // downloaded background image
    private long imageLoadedAt = 0;
    // API 31+: the blob blur runs as a TRUE gaussian on the GPU (RenderEffect,
    // clamped edges — matches the messenger's 30px backdrop blur exactly);
    // the paths are then drawn sharp and the view-level effect does the rest.
    private boolean useRenderEffectBlur = false;
    // The webview is inset below the status bar (see the insets listener in
    // GleapMainActivity), so the messenger's header geometry is measured from
    // THERE — not from the top of this (edge-to-edge) view. Content is drawn
    // shifted down by this inset, with a mirrored bleed filling the status-bar
    // band (the same trick the v4 header uses for overscroll).
    private int topInset = 0;

    GleapLoadingBackgroundView(Context context) {
        super(context);

        GleapConfig config = GleapConfig.getInstance();
        this.backgroundColor = parseColorSafe(config.getBackgroundColor(), Color.WHITE);
        this.headerColor = parseColorSafe(config.getHeaderColor(), Color.parseColor("#485bff"));
        this.headerColor2 = parseColorSafe(config.getHeaderColor2(), this.headerColor);
        this.headerColor3 = parseColorSafe(config.getHeaderColor3(), this.headerColor);
        this.bgType = config.getBgType() != null ? config.getBgType() : "";
        this.homeVersion = config.getHomeVersion();
        this.isV4 = !(homeVersion == 1 || homeVersion == 2 || homeVersion == 3);
        this.fadeBg = config.isFadeBg();
        this.bgBlur = config.isBgBlur();
        this.density = getResources().getDisplayMetrics().density;

        String bgImage = config.getBgImage();
        if ("image".equals(this.bgType) && bgImage != null && !bgImage.isEmpty()) {
            loadImageAsync(bgImage);
        }

        boolean isBlobVariant = !"image".equals(this.bgType) && !this.isV4 && !"classic".equals(this.bgType);
        if (isBlobVariant && this.bgBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                // CSS blur(30px) is a gaussian with sigma = 30dp; RenderEffect's
                // radius parameter maps to sigma = radius/2 (verified by
                // measuring the painted blur extent on-device), so double it.
                float radius = 60f * this.density;
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
                this.useRenderEffectBlur = true;
            } catch (Exception ignore) {
            }
        }
    }

    private static int parseColorSafe(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception ex) {
            return fallback;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        blobBitmap = null; // re-render for the new size
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        canvas.drawColor(backgroundColor);

        // The insets listener path is unreliable under the translucent widget
        // window (never dispatched to this programmatically-added view), so
        // read the inset directly each draw — cheap and deterministic.
        updateTopInset();

        // Draw the content in the webview's coordinate space (origin at the
        // status bar's bottom edge) so header heights match the messenger 1:1.
        int contentHeight = h - topInset;
        if (contentHeight <= 0) {
            return;
        }
        canvas.save();
        canvas.translate(0, topInset);
        drawContent(canvas, w, contentHeight);
        canvas.restore();

        // Status-bar band: mirrored continuation of the content's top edge —
        // solid brand colour for the gradients, mirrored image for photos.
        if (topInset > 0) {
            canvas.save();
            canvas.clipRect(0, 0, w, topInset);
            canvas.translate(0, topInset);
            canvas.scale(1, -1);
            drawContent(canvas, w, contentHeight);
            canvas.restore();
        }
    }

    private void updateTopInset() {
        int newInset = 0;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.view.WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    newInset = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.view.WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    newInset = insets.getSystemWindowInsetTop();
                }
            }
        } catch (Exception ignore) {
        }
        if (newInset != topInset) {
            topInset = newInset;
            blobBitmap = null;
        }
    }

    private void drawContent(Canvas canvas, int w, int h) {
        if ("image".equals(bgType)) {
            drawImageVariant(canvas, w, h);
        } else if (isV4) {
            drawV4Colour(canvas, w, h);
        } else if ("classic".equals(bgType)) {
            drawClassic(canvas, w, h);
        } else {
            drawGradientBlobs(canvas, w, h);
        }
    }

    // ---- v4 colour header --------------------------------------------------

    private void drawV4Colour(Canvas canvas, int w, int h) {
        float box = Math.min(HEADER_HEIGHT_DP * density, h);
        if (box <= 0) {
            return;
        }

        paint.setShader(new LinearGradient(0, 0, 0, box, headerColor, headerColor2, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, box, paint);
        paint.setShader(null);

        if (fadeBg) {
            drawSmoothstepRamp(canvas, w, box, box - 135f * density, box - 33f * density, null);
        }
    }

    // ---- image header ------------------------------------------------------

    private void drawImageVariant(Canvas canvas, int w, int h) {
        float box = h;
        if (isV4) {
            box = Math.min(HEADER_HEIGHT_DP * density, h);
        } else if (homeVersion == 1 || homeVersion == 2) {
            box = Math.max(0, h - DOCKED_TAB_BAR_DP * density);
        }
        if (box <= 0) {
            return;
        }

        if (imageBitmap != null) {
            // Fade the image in over the plain background once downloaded.
            long elapsed = SystemClock.uptimeMillis() - imageLoadedAt;
            int alpha = (int) Math.min(255, elapsed * 255 / IMAGE_FADE_IN_MS);
            paint.setAlpha(alpha);
            canvas.drawBitmap(imageBitmap, coverCropSource(imageBitmap, w, box), new RectF(0, 0, w, box), paint);
            paint.setAlpha(255);
            if (alpha < 255) {
                postInvalidateOnAnimation();
            }
        }

        if (isV4) {
            // Top legibility scrim + smoothstep whiten into the composer,
            // mirroring the v4 image header's overlay.
            drawSmoothstepRamp(canvas, w, box, box - 85f * density, box - 21f * density, new float[]{60f * density});
        }
    }

    // Source rect for center-cover-cropping a bitmap into a w x boxH target —
    // the same crop CSS object-fit: cover computes.
    private static Rect coverCropSource(Bitmap bitmap, float targetW, float targetH) {
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        float scale = Math.max(targetW / bw, targetH / bh);
        float srcW = targetW / scale;
        float srcH = targetH / scale;
        float left = (bw - srcW) / 2f;
        float top = (bh - srcH) / 2f;
        return new Rect(Math.round(left), Math.round(top), Math.round(left + srcW), Math.round(top + srcH));
    }

    // ---- v1-3 classic ------------------------------------------------------

    private void drawClassic(Canvas canvas, int w, int h) {
        float scale = w / 403f;
        float baseHeight = (fadeBg ? 503f : 362f) * scale;

        paint.setShader(new LinearGradient(0, 0, w, baseHeight * 0.5f, headerColor2, headerColor, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, baseHeight, paint);
        paint.setShader(null);

        if (fadeBg) {
            // Vertical fade into the background (BGclassic.svg: y 158 → 473).
            float fadeTop = 158f * scale;
            float fadeBottom = 473f * scale;
            paint.setShader(new LinearGradient(0, fadeTop, 0, fadeBottom,
                    withAlpha(backgroundColor, 0), backgroundColor, Shader.TileMode.CLAMP));
            canvas.drawRect(0, fadeTop, w, h, paint);
            paint.setShader(null);
        }
    }

    // ---- v1-3 gradient blobs -----------------------------------------------

    private void drawGradientBlobs(Canvas canvas, int w, int h) {
        if (!bgBlur || useRenderEffectBlur) {
            // Sharp vector paths — either bgBlur is off (the app also shows
            // them sharp) or the view-level RenderEffect gaussian blurs them.
            float scale = w / 403f;
            paint.setColor(headerColor);
            canvas.drawPath(blobPath(BLOB_HEADER, scale), paint);
            paint.setColor(headerColor2);
            canvas.drawPath(blobPath(BLOB_HEADER2, scale), paint);
            paint.setColor(headerColor3);
            canvas.drawPath(blobPath(BLOB_HEADER3, scale), paint);
            return;
        }

        if (blobBitmap == null) {
            blobBitmap = renderBlurredBlobs(w, h);
        }
        if (blobBitmap != null) {
            canvas.drawBitmap(blobBitmap, null, new Rect(0, 0, w, h), paint);
        }
    }

    // Pre-blurred blob bitmap (API < 31 fallback): render at quarter size,
    // collapse to ~76dp cells, then upscale in THREE filtered steps — the
    // iterated bilinear resampling approximates the messenger's 30px gaussian
    // backdrop blur (verified against a reference gaussian; a single mild
    // downscale leaves the polygons visibly sharp).
    private Bitmap renderBlurredBlobs(int w, int h) {
        try {
            int qw = Math.max(1, w / 4);
            int qh = Math.max(1, h / 4);
            Bitmap quarter = Bitmap.createBitmap(qw, qh, Bitmap.Config.ARGB_8888);
            Canvas quarterCanvas = new Canvas(quarter);
            quarterCanvas.drawColor(backgroundColor);
            float scale = qw / 403f;
            Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blobPaint.setColor(headerColor);
            quarterCanvas.drawPath(blobPath(BLOB_HEADER, scale), blobPaint);
            blobPaint.setColor(headerColor2);
            quarterCanvas.drawPath(blobPath(BLOB_HEADER2, scale), blobPaint);
            blobPaint.setColor(headerColor3);
            quarterCanvas.drawPath(blobPath(BLOB_HEADER3, scale), blobPaint);

            int cell = Math.max(8, Math.round(76f * density));
            int tw = Math.max(2, w / cell);
            int th = Math.max(2, h / cell);
            Bitmap tiny = Bitmap.createScaledBitmap(quarter, tw, th, true);
            Bitmap up1 = Bitmap.createScaledBitmap(tiny, tw * 4, th * 4, true);
            Bitmap up2 = Bitmap.createScaledBitmap(up1, tw * 16, th * 16, true);
            return Bitmap.createScaledBitmap(up2, w, h, true);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Path blobPath(float[][] points, float scale) {
        Path path = new Path();
        for (int i = 0; i < points.length; i++) {
            float x = points[i][0] * scale;
            float y = points[i][1] * scale;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }

    // ---- shared ------------------------------------------------------------

    /**
     * Draws the composer fade over the header box: optional top scrim (black
     * 20% → clear over scrimEnd[0]) plus a smoothstep whiten band from rampTop
     * to rampBottom — eased at BOTH ends so there is no visible onset line.
     */
    private void drawSmoothstepRamp(Canvas canvas, int w, float box, float rampTop, float rampBottom, float[] scrimEnd) {
        int steps = 12;
        boolean hasScrim = scrimEnd != null && scrimEnd.length > 0;
        int stopCount = (hasScrim ? 2 : 0) + steps + 1;
        int[] colors = new int[stopCount];
        float[] positions = new float[stopCount];
        int index = 0;
        if (hasScrim) {
            colors[index] = Color.argb(51, 0, 0, 0);
            positions[index++] = 0f;
            colors[index] = Color.argb(0, 0, 0, 0);
            positions[index++] = Math.min(1f, scrimEnd[0] / box);
        }
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float eased = t * t * (3f - 2f * t);
            float y = rampTop + (rampBottom - rampTop) * t;
            colors[index] = withAlpha(backgroundColor, Math.round(eased * 255f));
            positions[index++] = Math.max(0f, Math.min(1f, y / box));
        }
        paint.setShader(new LinearGradient(0, 0, 0, box, colors, positions, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, box, paint);
        paint.setShader(null);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void loadImageAsync(final String urlString) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    InputStream inputStream = connection.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    if (bitmap != null) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                imageBitmap = bitmap;
                                imageLoadedAt = SystemClock.uptimeMillis();
                                invalidate();
                            }
                        });
                    }
                } catch (Exception ignore) {
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }
}
