package gleap.io.gleap_android_sdk;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import java.io.File;
import io.gleap.Gleap;

public class Attachments extends AppCompatActivity {


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attachments);

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to read the contacts
            }

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    1564324);

            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant that should be quite unique

            return;
        }

        findViewById(R.id.bck_attach).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.attach).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    startActivityForResult(
                            Intent.createChooser(intent, "Select a File to Upload"),
                            0);
                } catch (android.content.ActivityNotFoundException ex) {


                }
            }
        });

        findViewById(R.id.attach2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               Gleap.getInstance().removeAllAttachments();
            }
        });

        findViewById(R.id.attach_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().addAttachment(null);
            }
        });

        findViewById(R.id.attach_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("Gleap Identity Check" + (int)Math.floor(Math.random() * 1000) , Gleap.SEVERITY.LOW);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 0:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                   FileUtilsInternal fileUtilsInternal = new FileUtilsInternal(this.getApplicationContext());
                   String str = fileUtilsInternal.getPath(data.getData());

                    Gleap.getInstance().addAttachment(new File(str));
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}