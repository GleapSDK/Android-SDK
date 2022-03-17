package io.gleap;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.PixelCopy;
import android.view.SurfaceView;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Bitmap.Config.HARDWARE;
import static android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.json.JSONObject;

class ScreenshotUtil {
    public static void takeScreenshot(GetImageCallback getImageCallback) throws GleapSessionNotInitialisedException, InterruptedException, ExecutionException {
        if (!UserSessionController.getInstance().isSessionLoaded()) {
            throw new GleapSessionNotInitialisedException();
        }
        try {
            Bitmap bitmap = null;
            if (GleapConfig.getInstance().getGetBitmapCallback() != null) {
                bitmap = GleapConfig.getInstance().getGetBitmapCallback().getBitmap();
                if (bitmap != null) {
                    getImageCallback.getImage(getResizedBitmap(bitmap));
                }
            } else {
                View view = ActivityUtil.getCurrentActivity().getWindow().getDecorView().getRootView();
                Window window = ActivityUtil.getCurrentActivity().getWindow();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view.isHardwareAccelerated()) {
                    captureView(view, window, new PixelCopyTask.ImageTaken() {
                        @Override
                        public void invoke(Bitmap bitmap) {
                            getImageCallback.getImage(getResizedBitmap(bitmap));
                        }
                    });
                } else {
                    bitmap = generateBitmap(ActivityUtil.getCurrentActivity());
                    if (bitmap != null) {
                        getImageCallback.getImage(getResizedBitmap(bitmap));
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println(ex);
            GleapDetectorUtil.resumeAllDetectors();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Bitmap takeScreenshot(float downScale) {
        Bitmap bitmap = null;
        if (GleapConfig.getInstance().getGetBitmapCallback() != null) {
            bitmap = GleapConfig.getInstance().getGetBitmapCallback().getBitmap();
        } else {
            bitmap = generateBitmap(ActivityUtil.getCurrentActivity());

        }
        if (bitmap != null) {
            return getResizedBitmap(bitmap, downScale);
        }
        return null;
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
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
        Bitmap decoded = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
        return decoded;
    }

    public static Bitmap getResizedBitmap(Bitmap bm, float downScale) {

        int width = bm.getWidth();
        int height = bm.getHeight();
        Matrix matrix = new Matrix();
        if (isPortrait(bm)) {
            matrix.postScale(downScale, downScale);
        } else {
            matrix.postScale(downScale - 0.2f, downScale - 0.2f);
        }
        Bitmap bitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 40, out);
        Bitmap decoded = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
        return decoded;
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
            e.printStackTrace();
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
        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PixelCopy.request(window,
                        new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight()),
                        bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                            @Override
                            public void onPixelCopyFinished(int copyResult) {
                                if (copyResult == PixelCopy.SUCCESS) {
                                    imageTaken.invoke(bitmap);
                                }
                            }
                        },
                        new Handler()
                );
            }
        });
    }

    public interface GetImageCallback {
        void getImage(Bitmap bitmap);
    }
}
