package gleap.io.gleap_android_sdk;

import android.app.Application;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.gleap.Gleap;
import io.gleap.GleapUserSession;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
       // GleapUserSession gleapUserSession = new GleapUserSession("12", "3ec01e9f99aa53258626cd85bde0d3af859004f904c2ab30725de2720196526e", "Niklas", "n@a.at");
        Gleap.initialize("UkzcTBCsX5nmsu2cV5hEcENkNuAT838O",this);
        File fileDirectory = new File(this.getCacheDir(), "/NewTextFile.json");
        try {
            FileWriter fileWriter = new FileWriter(fileDirectory);
            fileWriter.write("HEY YOU");
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        Gleap.getInstance().addAttachment(fileDirectory);
    }
}
