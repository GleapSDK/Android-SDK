package io.gleap;

import java.io.File;

class GleapFileHelper {
    private static final int MAX_AMOUNT = 6;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;
    private File[] files = new File[MAX_AMOUNT];
    private int currentIndex = 0;
    // Created with the class: getInstance() is called from several threads.
    private static final GleapFileHelper instance = new GleapFileHelper();

    public static GleapFileHelper getInstance() {
        return instance;
    }

    public void addAttachment(File file) {
        if(file != null) {
            if (file.length() <= MAX_FILE_SIZE) {
                if (currentIndex < MAX_AMOUNT) {
                    files[currentIndex] = file;
                    currentIndex++;
                } else {
                    GleapLog.w("Already " + MAX_AMOUNT + " attachments added. This is the maximum amount.");
                }
            } else {
                GleapLog.w("File is too big. The maximum attachment size is " + (MAX_FILE_SIZE / (1024 * 1024)) + " MB.");
            }
        }
    }

    public void clearAttachments() {
        currentIndex = 0;
        files = new File[MAX_AMOUNT];
    }

    public File[] getAttachments() {
        return files;
    }
}
