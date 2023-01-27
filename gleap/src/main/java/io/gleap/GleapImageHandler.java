package io.gleap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class GleapImageHandler extends AsyncTask<Void, Void, Bitmap> {

    private String url;
    private ImageView imageView;
    private Bitmap bitmap;
    private GleapImageLoaded gleapImageLoaded;

    public GleapImageHandler(String url, ImageView imageView, GleapImageLoaded gleapImageLoaded) {
        this.url = url;
        this.imageView = imageView;
        this.gleapImageLoaded = gleapImageLoaded;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        try {
            if(bitmap == null) {
                URL urlConnection = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlConnection
                        .openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
            }
            return  bitmap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        super.onPostExecute(result);
        try {
            imageView.setImageBitmap(getRoundedBitmap(result,20, Color.RED));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            gleapImageLoaded.invoke();
        }catch (Exception ex) {

        }
    }

    public static Bitmap getRoundedBitmap(Bitmap bitmap, int pixels, int color) {

        Bitmap inpBitmap = bitmap;
        int width = 0;
        int height = 0;
        width = inpBitmap.getWidth();
        height = inpBitmap.getHeight();

        if (width <= height) {
            height = width;
        } else {
            width = height;
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, width, height);
        final RectF rectF = new RectF(rect);
        final float roundPx = pixels;

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(inpBitmap, rect, rect, paint);

        return output;
    }
}
