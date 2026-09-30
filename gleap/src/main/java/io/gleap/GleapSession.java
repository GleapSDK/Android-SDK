package io.gleap;

class GleapSession {
    private String id;
    private String hash;
    // Authenticated conversation files: whether the project protects them, and the short-lived
    // file session a verified identify handed out. Kept in memory only, never stored.
    private volatile boolean authenticatedFilesRequired;
    private String fileAccessToken;
    private String fileAccessExpiresAt;

    public GleapSession(String id, String hash){
        this.id = id;
        this.hash = hash;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    boolean isAuthenticatedFilesRequired() {
        return authenticatedFilesRequired;
    }

    void setAuthenticatedFilesRequired(boolean authenticatedFilesRequired) {
        this.authenticatedFilesRequired = authenticatedFilesRequired;
    }

    synchronized String getFileAccessToken() {
        return fileAccessToken;
    }

    /**
     * The expiry of the file session, ISO-8601 as the API sends it.
     */
    synchronized String getFileAccessExpiresAt() {
        return fileAccessExpiresAt;
    }

    synchronized void setFileAccess(String token, String expiresAt) {
        this.fileAccessToken = token;
        this.fileAccessExpiresAt = token != null ? expiresAt : null;
    }

    /**
     * @return the file session token while it is valid for at least {@code marginMs} more, else null
     */
    synchronized String validFileAccessToken(long marginMs) {
        return GleapFileAccess.isValid(fileAccessToken, fileAccessExpiresAt, marginMs) ? fileAccessToken : null;
    }
}
