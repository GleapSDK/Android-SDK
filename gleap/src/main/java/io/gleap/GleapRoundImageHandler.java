package io.gleap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class GleapRoundImageHandler extends AsyncTask<Void, Void, Bitmap> {

    private String url;
    private ImageView imageView;
    private GleapImageLoaded imageLoaded;

    public GleapRoundImageHandler(String url, ImageView imageView) {
        this.url = url;
        this.imageView = imageView;
    }

    public GleapRoundImageHandler(String url, ImageView imageView, GleapImageLoaded imageLoaded) {
        this.url = url;
        this.imageView = imageView;
        this.imageLoaded = imageLoaded;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        try {
                URL urlConnection = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlConnection
                        .openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        super.onPostExecute(result);
        try {
            Bitmap croppedBitmap = getRoundedCroppedBitmap(result, 500);
            if(this.imageLoaded != null) {
                this.imageLoaded.invoke(croppedBitmap);
            }
            result.recycle();
            imageView.setImageBitmap(croppedBitmap);
        } catch (Exception ex) {

        }
    }

    private Bitmap getRoundedCroppedBitmap(Bitmap bitmap, int radius) {
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

        return output;
    }
}
