package io.gleap;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND;

import androidx.annotation.RequiresApi;

class ScreenshotUtil {
    /**
     * Takes a screenshot of the current activity (or asks the app's GetBitmapCallback for one).
     * Once the session is loaded the callback always runs exactly once: with the screenshot, or
     * with null when none could be taken. Without a loaded session it does not run and the
     * activation methods are resumed.
     */
    public static void takeScreenshot(GetImageCallback getImageCallback) {
        GleapSessionController sessionController = GleapSessionController.getInstance();
        if (sessionController == null || !sessionController.isSessionLoaded()) {
            GleapDetectorUtil.resumeAllDetectors();
            return;
        }

        final GetImageCallback callback = once(getImageCallback);
        try {
            Bitmap bitmap = null;
            if (GleapCallbacks.getInstance().getGetBitmapCallback() != null) {
                bitmap = GleapCallbacks.getInstance().getGetBitmapCallback().getBitmap();
                if(bitmap != null) {
                    callback.getImage(getResizedBitmap(bitmap));
                } else {
                    takeNativeScreenshot(callback);
                }
            } else {
                takeNativeScreenshot(callback);
            }
        } catch (Exception | OutOfMemoryError ex) {
            callback.getImage(null);
        }
    }

    private static GetImageCallback once(final GetImageCallback getImageCallback) {
        final AtomicBoolean called = new AtomicBoolean(false);
        return new GetImageCallback() {
            @Override
            public void getImage(Bitmap bitmap) {
                if (called.compareAndSet(false, true)) {
                    getImageCallback.getImage(bitmap);
                }
            }
        };
    }

    private static void takeNativeScreenshot(GetImageCallback getImageCallback) {
        View view = ActivityUtil.getCurrentActivity().getWindow().getDecorView().getRootView();
        Window window = ActivityUtil.getCurrentActivity().getWindow();
        Bitmap bitmap = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view.isHardwareAccelerated()) {
            captureView(view, window, new PixelCopyTask.ImageTaken() {
                @Override
                public void invoke(Bitmap bitmap) {
                    getImageCallback.getImage(bitmap != null ? getResizedBitmap(bitmap) : null);
                }
            });
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            bitmap = Bitmap.createBitmap(view.getWidth(),
                    view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            getImageCallback.getImage(getResizedBitmap(bitmap));
        } else {
            bitmap = generateBitmap(ActivityUtil.getCurrentActivity());
            getImageCallback.getImage(bitmap != null ? getResizedBitmap(bitmap) : null);
        }
    }

    private static Bitmap generateBitmap(Activity activity) {
        try {
            final List<ViewMeta> viewRoots = getAvailableViewsEnriched(activity);
            int maxWidth = Integer.MIN_VALUE;
            int maxHeight = Integer.MIN_VALUE;
            for (ViewMeta viewMeta : viewRoots) {
                maxWidth = Math.max(viewMeta.getFrame().right, maxWidth);
                maxHeight = Math.max(viewMeta.getFrame().bottom, maxHeight);
            }
            if (maxWidth < 1 && maxHeight < 1) {
                return null;
            }
            final Bitmap bitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888);
            for (ViewMeta rootData : viewRoots) {
                if ((rootData.getLayoutParams().flags & FLAG_DIM_BEHIND) == FLAG_DIM_BEHIND) {
                    Canvas dimCanvas = new Canvas(bitmap);
                    int alpha = (int) (255 * rootData.getLayoutParams().dimAmount);
                    dimCanvas.drawARGB(alpha, 0, 0, 0);
                }
                Canvas canvas = new Canvas(bitmap);
                canvas.translate(rootData.getFrame().left, rootData.getFrame().top);
                rootData.getView().draw(canvas);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private static void getOffset(List<ViewMeta> rootViews) {
        int minTop = Integer.MAX_VALUE;
        int minLeft = Integer.MAX_VALUE;
        for (ViewMeta rootView : rootViews) {
            if (rootView.getFrame().top < minTop) {
                minTop = rootView.getFrame().top;
            }
            if (rootView.getFrame().left < minLeft) {
                minLeft = rootView.getFrame().left;
            }
        }
        for (ViewMeta rootView : rootViews) {
            rootView.getFrame().offset(-minLeft, -minTop);
        }
    }

    private static boolean isPortrait(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        return bitmap.getHeight() > bitmap.getWidth();
    }

    private static Bitmap getResizedBitmap(Bitmap bm) {
        try {
            int width = bm.getWidth();
            int height = bm.getHeight();
            Matrix matrix = new Matrix();
            if (isPortrait(bm)) {
                matrix.postScale(0.7f, 0.7f);
            } else {
                matrix.postScale(0.5f, 0.5f);
            }
            Bitmap bitmap = Bitmap.createBitmap(
                    bm, 0, 0, width, height, matrix, false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, out);
            Bitmap decoded = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
            return decoded;
        } catch (OutOfMemoryError | Exception error) {
        }
        return null;
    }

    private static Field getFieldForName(String name, Object obj) throws NullPointerException {
        Class currentClass = obj.getClass();
        while (currentClass != Object.class && currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new NullPointerException();
    }

    private static Object getField(String fieldName, Object target) {
        try {
            Field field = getFieldForName(fieldName, target);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<ViewMeta> getAvailableViewsEnriched(Activity activity) {
        if (activity != null) {
            Object globalWindowManager = getField("mGlobal", activity.getWindowManager());
            Object rootObjects = getField("mRoots", globalWindowManager);
            Object paramsObject = getField("mParams", globalWindowManager);
            Object[] roots = ((List) rootObjects).toArray();
            List<WindowManager.LayoutParams> paramsList = (List<WindowManager.LayoutParams>) paramsObject;
            WindowManager.LayoutParams[] params = paramsList.toArray(new WindowManager.LayoutParams[0]);
            List<ViewMeta> rootViews = enrichViewsWithMeta(roots, params);
            if (rootViews.isEmpty()) {
                return Collections.emptyList();
            }
            getOffset(rootViews);
            reArrangeViews(rootViews);
            return rootViews;
        }
        return new LinkedList<>();
    }

    private static void reArrangeViews(List<ViewMeta> metaViews) {
        if (metaViews.size() <= 1) {
            return;
        }
        for (int dialogIndex = 0; dialogIndex < metaViews.size() - 1; dialogIndex++) {
            ViewMeta viewRoot = metaViews.get(dialogIndex);
            if (!viewRoot.isDialogType()) {
                continue;
            }
            if (viewRoot.getWindowToken() == null) {
                return;
            }
            for (int parentIndex = dialogIndex + 1; parentIndex < metaViews.size(); parentIndex++) {
                ViewMeta possibleParent = metaViews.get(parentIndex);
                if (possibleParent.isActivityType()
                        && possibleParent.getWindowToken() == viewRoot.getWindowToken()) {
                    metaViews.remove(possibleParent);
                    metaViews.add(dialogIndex, possibleParent);
                    break;
                }
            }
        }
    }

    private static List<ViewMeta> enrichViewsWithMeta(Object[] roots, WindowManager.LayoutParams[] params) {
        List<ViewMeta> metaViews = new ArrayList<>();
        int currIndex = 0;
        for (Object view : roots) {
            View rootView = (View) getField("mView", view);
            if (rootView == null) {
                currIndex++;
                continue;
            }
            if (!rootView.isShown()) {
                currIndex++;
                continue;
            }
            int[] xyDimension = new int[2];
            rootView.getLocationOnScreen(xyDimension);

            int left = xyDimension[0];
            int top = xyDimension[1];
            Rect rect = new Rect(left, top, left + rootView.getWidth(), top + rootView.getHeight());
            if (!rootView.isHardwareAccelerated()) {
                metaViews.add(new ViewMeta(rootView, rect, params[currIndex]));
            }
            currIndex++;
        }
        return metaViews;
    }

    public static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] b = baos.toByteArray();
        String imageEncoded = Base64.encodeToString(b, Base64.NO_WRAP);
        return imageEncoded;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void captureView(View view, Window window, PixelCopyTask.ImageTaken imageTaken) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), ARGB_8888);
        int[] location = new int[2];
        view.getLocationInWindow(location);
        GleapMainThread.runWithActivity(new Runnable() {
            @Override
            public void run() {
                try {
                    PixelCopy.request(window,
                            new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight()),
                            bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                                @Override
                                public void onPixelCopyFinished(int copyResult) {
                                    imageTaken.invoke(copyResult == PixelCopy.SUCCESS ? bitmap : null);
                                }
                            },
                            new Handler(Looper.getMainLooper())
                    );
                } catch (Exception e) {
                    // E.g. the window has no surface (anymore).
                    imageTaken.invoke(null);
                }
            }
        });
    }

    public interface GetImageCallback {
        void getImage(Bitmap bitmap);
    }
}
