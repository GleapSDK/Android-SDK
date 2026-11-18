package io.gleap;

import androidx.core.content.FileProvider;

/**
 * Shares the protected conversation files the SDK downloaded with the app that opens them
 * ({@link GleapProtectedFiles}). A subclass of its own, so it never clashes with a
 * {@link FileProvider} the app declares.
 */
public class GleapFileProvider extends FileProvider {
    /**
     * The provider's authority, as declared in the SDK's manifest.
     */
    static String authority(android.content.Context context) {
        return context.getPackageName() + ".gleap.files";
    }
}
