package gleap.io.gleap_android_sdk;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

import io.gleap.Gleap;
import io.gleap.SurveyType;


public class MainActivity extends AppCompatActivity {
    public static String[] storge_permissions = {
            // ... already existing permissions
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static String[] storge_permissions_33 = {
            // ... already existing permissions
            Manifest.permission.READ_MEDIA_IMAGES,
    };

    public static String[] permissions() {
        String[] permissionsToRequest;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest = storge_permissions_33;
        } else {
            permissionsToRequest = storge_permissions;
        }
        return permissionsToRequest;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.feedback).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Gleap.getInstance().open();
            /*    String[] permissions = new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
                ActivityCompat.requestPermissions(MainActivity.this, permissions, 101);*/

                String fileName = "random.txt";
                try {
                    FileOutputStream fileOutputStream;
                    if (false) {
                        fileOutputStream = MainActivity.this.openFileOutput(fileName.toString(), Context.MODE_PRIVATE);
                    } else {
                        File file = new File(MainActivity.this.getCacheDir(), fileName);
                        fileOutputStream = new FileOutputStream(file);
                    }
                    fileOutputStream.write("fileContents.getText().toString()".getBytes(Charset.forName("UTF-8")));
                    Toast.makeText(MainActivity.this, String.format("Write to file %s sucess", fileName), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, String.format("Write to file %s failed", fileName), Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.crash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject obj  = new JSONObject();
                try{
                    obj.put("id", "lAEhlcHvGvQFf9QClHnV6yFBTlxdcXVPpaW2MG4VdGLTJ40H6yJLdRpXq17gKPK0x9eDAy");
                    obj.put("type","conversation");
                }catch (Exception ex) {}
                Gleap.getInstance().handlePushNotification(obj);
            }
        });

        findViewById(R.id.event).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, EventLogging.class);
                startActivity(intent);

            }
        });

        findViewById(R.id.identify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, IdentifyUser.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.attach).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, Attachments.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.custom).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, CustomData.class);
                startActivity(intent);
            }
        });


        findViewById(R.id.network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, NetworkLogging.class);
                startActivity(intent);
            }
        });
    }

    private float readUsage() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (Exception e) {
            }

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" +");

            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float) (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    /*
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent returnIntent) {
        if (requestCode == 1) {
            ValueCallback<Uri[]> mUploadMessage = Gleap.getInstance().getmUploadMessage();
            if (mUploadMessage == null || intent == null || resultCode != RESULT_OK) {
                return;
            }

            Uri[] result = null;
            String dataString = intent.getDataString();

            if (dataString != null) {
                result = new Uri[]{Uri.parse(dataString)};
            }

            mUploadMessage.onReceiveValue(result);
            GleapConfig.getInstance().setmUploadMessage(null);
        }
    }*/

}

