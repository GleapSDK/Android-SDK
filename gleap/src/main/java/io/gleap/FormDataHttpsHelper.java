package io.gleap;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

/**
 * Upload the image as form-data
 */
class FormDataHttpsHelper {
    private final HttpURLConnection httpConn;
    //private final DataOutputStream request;
    private final String boundary = "BBBOUNDARY";
    private final String crlf = "\r\n";
    private final String twoHyphens = "--";
    private Date date;

    private OutputStream outputStream;
    private PrintWriter writer;

    /**
     * This constructor initializes a new HTTPS POST request with content type
     * is set to multipart/form-data
     *
     * @param requestURL gleap url
     * @param apiToken token for the project
     */
    public FormDataHttpsHelper(String requestURL, String apiToken)
            throws IOException {

        URL url = new URL(requestURL);
        if (requestURL.contains("https")) {
            httpConn = (HttpURLConnection) url.openConnection();
        } else {
            httpConn = (HttpURLConnection) url.openConnection();
        }
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true); // indicates POST method
        httpConn.setDoInput(true);

        httpConn.setRequestMethod("POST");
        httpConn.setRequestProperty("Connection", "Keep-Alive");
        httpConn.setRequestProperty("Cache-Control", "no-cache");
        httpConn.setRequestProperty("api-token", apiToken);
        httpConn.setRequestProperty("Accept","*/*");
        UserSession userSession = UserSessionController.getInstance().getUserSession();
        httpConn.setRequestProperty("gleap-id", userSession.getId());
        httpConn.setRequestProperty("gleap-hash", userSession.getHash());

        httpConn.setRequestProperty(
                "Content-Type", "multipart/form-data;boundary=" + this.boundary);

        outputStream = httpConn.getOutputStream();
        writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8.newEncoder()),
                true);
      //  request =  new DataOutputStream(httpConn.getOutputStream());
    }

    /**
     * Adds a upload file section to the request
     * default name is file
     * @param uploadFile a File to be uploaded
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void addFilePart(File uploadFile)
            throws IOException {
        String fileName = uploadFile.getName();
        writer.append(this.twoHyphens + this.boundary + this.crlf);
        writer.append("Content-Disposition: form-data; name=\"file\";filename=\"" +
                fileName + "\"" + this.crlf);
        writer.append("Content-Type: " +
                "" + URLConnection.guessContentTypeFromName(fileName) + this.crlf);
        writer.append("Content-Transfer-Encoding: binary").append(this.crlf);
        writer.append(this.crlf);
        writer.flush();

        int size = (int) uploadFile.length();
        byte[] bytes = new byte[size];

        FileInputStream inputStream = new FileInputStream(uploadFile);
        int bytesRead = -1;
        while ((bytesRead = inputStream.read(bytes)) != -1) {
            outputStream.write(bytes, 0, bytesRead);
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();

        writer.append(this.crlf);
        writer.flush();

        httpConn.disconnect();
    }

    /**
     * Completes the request and receives response from the server.
     *
     * @return the response of the server
     * @throws IOException error when the file cant be uploaded
     */
    public String finishAndUpload() throws IOException {
        String response;
        writer.append(this.twoHyphens + this.boundary + this.twoHyphens + this.crlf);
        writer.close();

        int status = httpConn.getResponseCode();


        this.date = new Date();

        // checks server's status code first

        if (status == HttpURLConnection.HTTP_OK) {
            InputStream responseStream = new
                    BufferedInputStream(httpConn.getInputStream());

            BufferedReader responseStreamReader =
                    new BufferedReader(new InputStreamReader(responseStream));

            String line;
            StringBuilder stringBuilder = new StringBuilder();

            while ((line = responseStreamReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }

            responseStreamReader.close();

            response = stringBuilder.toString();
            httpConn.disconnect();
        } else {
            throw new IOException("Server returned non-OK status: " + status);
        }

        return response;
    }
}