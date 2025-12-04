package com.satyam.assignmenttracker.Adapters;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * Uploads file Uri to Cloudinary using unsigned preset (streaming).
 * Set CLOUD_NAME and UPLOAD_PRESET to your settings.
 */
public class CloudinaryUploader {
    private static final String TAG = "CloudinaryUploader";

    // TODO: set your actual Cloudinary account name and unsigned preset
    public static String CLOUD_NAME = "dquoscoly";
    public static String UPLOAD_PRESET = "unsigned_assignments";

    /**
     * Upload a content Uri by streaming it to Cloudinary. Caller must run off UI thread.
     * Returns secure_url on success.
     */
    public static String uploadUriStreaming(ContentResolver resolver, Uri uri, String fileName, String mime) throws Exception {
        final int MAX_ATTEMPTS = 3;
        long backoff = 1500;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(180, TimeUnit.SECONDS)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(180, TimeUnit.SECONDS)
                    .build();

            RequestBody fileBody = new RequestBody() {
                @Override public MediaType contentType() { return mime != null ? MediaType.parse(mime) : MediaType.parse("application/octet-stream"); }
                @Override public void writeTo(BufferedSink sink) {
                    InputStream is = null;
                    try {
                        is = resolver.openInputStream(uri);
                        if (is == null) throw new RuntimeException("Cannot open input stream");
                        byte[] buf = new byte[8*1024];
                        int r;
                        while ((r = is.read(buf)) != -1) sink.write(buf, 0, r);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        try { if (is != null) is.close(); } catch (Exception ignored) {}
                    }
                }
            };

            MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .addFormDataPart("upload_preset", UPLOAD_PRESET);

            Request req = new Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/auto/upload")
                    .post(mb.build())
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    Log.w(TAG, "upload failed attempt " + attempt + " code=" + resp.code() + " body=" + body);
                    if (attempt == MAX_ATTEMPTS) throw new RuntimeException("Upload failed: " + resp.code() + " " + body);
                    Thread.sleep(backoff);
                    backoff *= 2;
                    continue;
                }
                String respStr = resp.body() != null ? resp.body().string() : null;
                if (respStr == null) throw new RuntimeException("Empty response from cloudinary");
                JSONObject js = new JSONObject(respStr);
                String secureUrl = js.optString("secure_url", js.optString("url", null));
                if (secureUrl == null) throw new RuntimeException("No secure_url in Cloudinary response: " + respStr);
                return secureUrl;
            } catch (Exception ex) {
                Log.w(TAG, "upload attempt " + attempt + " error: " + ex.getMessage(), ex);
                if (attempt == MAX_ATTEMPTS) throw ex;
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff *= 2;
            }
        }
        throw new RuntimeException("Upload failed after attempts");
    }

    // convenience
    public static String uploadUriStreaming(ContentResolver resolver, Uri uri, String fileName) throws Exception {
        String mime = resolver.getType(uri);
        return uploadUriStreaming(resolver, uri, fileName, mime);
    }
}
