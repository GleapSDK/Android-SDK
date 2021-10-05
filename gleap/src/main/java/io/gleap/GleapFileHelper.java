package io.gleap;

import java.io.File;

public class GleapFileHelper {
    private static final int MAX_AMOUNT = 3;
    private File[] files = new File[MAX_AMOUNT];
    private int curreIndex = 0;
    private static GleapFileHelper instance;

    public static GleapFileHelper getInstance() {
        if(instance == null){
            instance = new GleapFileHelper();
        }
        return instance;
    }

    public void addAttachment(File file) {
        if(curreIndex < MAX_AMOUNT) {
            files[curreIndex] = file;
            curreIndex++;
        }else {
            System.err.println("Gleap: Already " + MAX_AMOUNT + " appended. This is the maximum amount.");
        }
    }

    public void clearAttachments() {
        curreIndex = 0;
        files = new File[MAX_AMOUNT];
    }

    public File[] getAttachments() {
        return files;
    }
}
