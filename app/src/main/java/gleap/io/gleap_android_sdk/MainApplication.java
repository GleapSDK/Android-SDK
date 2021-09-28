package gleap.io.gleap_android_sdk;

import android.app.Application;

import gleap.io.gleap.Gleap;
import gleap.io.gleap.GleapUserSession;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        GleapUserSession gleapUserSession = new GleapUserSession("12", "3ec01e9f99aa53258626cd85bde0d3af859004f904c2ab30725de2720196526e", "Niklas", "n@a.at");
       Gleap.initialize("fFI920hdGVZUu7POH0wOkJcbrMoVW3DW", gleapUserSession,this);
     //   Gleap.initialize("fFI920hdGVZUu7POH0wOkJcbrMoVW3DW",this);


    Gleap.getInstance().clearUserSession();
    }
}
