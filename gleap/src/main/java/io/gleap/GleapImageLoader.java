package io.gleap;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Loads remote images downsampled to their destination size and serves them
 * from an in-memory LRU cache that shrinks under memory pressure. All remote
 * images must go through this loader — Play's Android Vitals flags manual
 * full-size network decodes as excessive bitmap memory usage.
 */
class GleapImageLoader {
    private static final int ROUND_BITMAP_SIZE = 500;
    private static final int TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GleapImageLoader");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Bitmaps handed out can be redrawn at any time, so entries are never
    // recycled — eviction just drops the reference.
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(cacheSizeKb()) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            // A consumer that recycled a shared entry would otherwise throw
            // IllegalStateException here during evictAll/trimToSize.
            return bitmap.isRecycled() ? 0 : bitmap.getByteCount() / 1024;
        }
    };
    private static boolean trimCallbacksRegistered = false;

    private GleapImageLoader() {
    }

    static void load(String url, ImageView imageView, GleapImageLoaded imageLoaded) {
        request(url, imageView, imageLoaded, false, 0);
    }

    // For views that aren't measured and carry no layout params yet.
    static void load(String url, ImageView imageView, int targetSizePx, GleapImageLoaded imageLoaded) {
        request(url, imageView, imageLoaded, false, targetSizePx);
    }

    static void loadRound(String url, ImageView imageView, GleapImageLoaded imageLoaded) {
        request(url, imageView, imageLoaded, true, 0);
    }

    private static void request(final String url, final ImageView imageView, final GleapImageLoaded imageLoaded, final boolean round, final int targetSizePx) {
        if (url == null || url.length() == 0 || imageView == null) {
            return;
        }

        registerTrimCallbacks(imageView.getContext());

        final int targetWidth;
        final int targetHeight;
        if (round) {
            targetWidth = ROUND_BITMAP_SIZE;
            targetHeight = ROUND_BITMAP_SIZE;
        } else if (targetSizePx > 0) {
            targetWidth = targetSizePx;
            targetHeight = targetSizePx;
        } else {
            targetWidth = targetDimension(imageView.getWidth(), layoutParamsWidth(imageView), screenWidth());
            targetHeight = targetDimension(imageView.getHeight(), layoutParamsHeight(imageView), screenHeight());
        }
        final String cacheKey = (round ? "round|" : "plain|") + targetWidth + "x" + targetHeight + "|" + url;

        Bitmap cached = cache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            deliver(cached, imageView, imageLoaded, round);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = downloadDownsampled(url, targetWidth, targetHeight);
                    if (bitmap == null) {
                        return;
                    }
                    if (round) {
                        Bitmap roundBitmap = getRoundedCroppedBitmap(bitmap, ROUND_BITMAP_SIZE);
                        if (roundBitmap != bitmap) {
                            bitmap.recycle();
                        }
                        bitmap = roundBitmap;
                    }

                    final Bitmap result = bitmap;
                    cache.put(cacheKey, result);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            deliver(result, imageView, imageLoaded, round);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void deliver(Bitmap bitmap, ImageView imageView, GleapImageLoaded imageLoaded, boolean round) {
        try {
            imageView.setImageBitmap(bitmap);
            if (!round) {
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.requestLayout();
            }
            if (imageLoaded != null) {
                imageLoaded.invoke(bitmap);
            }
        } catch (Exception ignored) {
        }
    }

    private static Bitmap downloadDownsampled(String url, int targetWidth, int targetHeight) throws Exception {
        byte[] data = downloadBytes(url);
        if (data == null || data.length == 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static byte[] downloadBytes(String url) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoInput(true);
            connection.connect();
            input = connection.getInputStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (reqWidth <= 0 || reqHeight <= 0) {
            return inSampleSize;
        }
        final int halfWidth = options.outWidth / 2;
        final int halfHeight = options.outHeight / 2;
        while ((halfWidth / inSampleSize) >= reqWidth && (halfHeight / inSampleSize) >= reqHeight) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private static int targetDimension(int measured, int fromLayoutParams, int fallback) {
        if (measured > 0) {
            return measured;
        }
        if (fromLayoutParams > 0) {
            return fromLayoutParams;
        }
        return fallback;
    }

    private static int layoutParamsWidth(ImageView imageView) {
        return imageView.getLayoutParams() != null ? imageView.getLayoutParams().width : 0;
    }

    private static int layoutParamsHeight(ImageView imageView) {
        return imageView.getLayoutParams() != null ? imageView.getLayoutParams().height : 0;
    }

    private static int screenWidth() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private static int screenHeight() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    private static int cacheSizeKb() {
        int maxMemoryKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return Math.min(maxMemoryKb / 32, 8 * 1024);
    }

    private static synchronized void registerTrimCallbacks(Context context) {
        if (trimCallbacksRegistered || context == null) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            return;
        }
        applicationContext.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int level) {
                // Runs on the main thread; must never crash the host app.
                try {
                    if (level >= TRIM_MEMORY_BACKGROUND) {
                        cache.evictAll();
                    } else if (level >= TRIM_MEMORY_UI_HIDDEN) {
                        cache.trimToSize(cache.size() / 2);
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
            }

            @Override
            public void onLowMemory() {
                try {
                    cache.evictAll();
                } catch (Exception ignored) {
                }
            }
        });
        trimCallbacksRegistered = true;
    }

    private static Bitmap getRoundedCroppedBitmap(Bitmap bitmap, int radius) {
        Bitmap finalBitmap;
        if (bitmap.getWidth() != radius || bitmap.getHeight() != radius)
            finalBitmap = Bitmap.createScaledBitmap(bitmap, radius, radius,
                    false);
        else
            finalBitmap = bitmap;
        Bitmap output = Bitmap.createBitmap(finalBitmap.getWidth(),
                finalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, finalBitmap.getWidth(),
                finalBitmap.getHeight());

        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(finalBitmap.getWidth() / 2f + 0.7f,
                finalBitmap.getHeight() / 2f + 0.7f,
                finalBitmap.getWidth() / 2f + 0.1f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(finalBitmap, rect, rect, paint);

        if (finalBitmap != bitmap && finalBitmap != output) {
            finalBitmap.recycle();
        }
        return output;
    }
}
