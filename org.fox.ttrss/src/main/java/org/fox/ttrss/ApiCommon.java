package org.fox.ttrss;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class ApiCommon {
    public static final String TAG = ApiCommon.class.getName();

    private static final int API_STATUS_OK = 0;
    private static final int API_STATUS_ERR = 1;

    private static final MediaType TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    public interface ApiCaller {
        void setStatusCode(int statusCode);

        void setLastError(ApiError lastError);

        void setLastErrorMessage(String message);

        void notifyProgress(int progress);
    }

    public enum ApiError {
        SUCCESS, UNKNOWN_ERROR, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND, HTTP_BAD_REQUEST,
        HTTP_SERVER_ERROR, HTTP_OTHER_ERROR, SSL_REJECTED, SSL_HOSTNAME_REJECTED, PARSE_ERROR, IO_ERROR, OTHER_ERROR, API_DISABLED,
        API_UNKNOWN, LOGIN_FAILED, INVALID_URL, API_INCORRECT_USAGE, NETWORK_UNAVAILABLE, API_UNKNOWN_METHOD
    }

    public static int getErrorMessage(ApiError error) {
        switch (error) {
            case SUCCESS:
                return R.string.error_success;
            case UNKNOWN_ERROR:
                return R.string.error_unknown;
            case HTTP_UNAUTHORIZED:
                return R.string.error_http_unauthorized;
            case HTTP_BAD_REQUEST:
                return R.string.error_bad_request;
            case HTTP_FORBIDDEN:
                return R.string.error_http_forbidden;
            case HTTP_NOT_FOUND:
                return R.string.error_http_not_found;
            case HTTP_SERVER_ERROR:
                return R.string.error_http_server_error;
            case HTTP_OTHER_ERROR:
                return R.string.error_http_other_error;
            case SSL_REJECTED:
                return R.string.error_ssl_rejected;
            case SSL_HOSTNAME_REJECTED:
                return R.string.error_ssl_hostname_rejected;
            case PARSE_ERROR:
                return R.string.error_parse_error;
            case IO_ERROR:
                return R.string.error_io_error;
            case OTHER_ERROR:
                return R.string.error_other_error;
            case API_DISABLED:
                return R.string.error_api_disabled;
            case API_UNKNOWN:
                return R.string.error_api_unknown;
            case API_UNKNOWN_METHOD:
                return R.string.error_api_unknown_method;
            case LOGIN_FAILED:
                return R.string.error_login_failed;
            case INVALID_URL:
                return R.string.error_invalid_api_url;
            case API_INCORRECT_USAGE:
                return R.string.error_api_incorrect_usage;
            case NETWORK_UNAVAILABLE:
                return R.string.error_network_unavailable;
            default:
                Log.d(TAG, "getErrorMessage: unknown error code=" + error);
                return R.string.error_unknown;
        }
    }

    static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        // if no network is available networkInfo will be null
        // otherwise check if we are connected
        return networkInfo != null && networkInfo.isConnected();
    }

    static JsonElement performRequest(Context context, @NonNull HashMap<String, String> m_params,
                                      @NonNull ApiCommon.ApiCaller caller) {
        try {
            if (!ApiCommon.isNetworkAvailable(context)) {
                caller.setLastError(ApiError.NETWORK_UNAVAILABLE);
                return null;
            }

            SharedPreferences m_prefs = PreferenceManager.getDefaultSharedPreferences(context);

            boolean m_transportDebugging = m_prefs.getBoolean("transport_debugging", false);

            Gson gson = new Gson();

            String payload = gson.toJson(new HashMap<>(m_params));
            String apiUrl = m_prefs.getString("ttrss_url", "").trim() + "/api/";

            if (m_transportDebugging) Log.d(TAG, ">>> " + payload + " -> " + apiUrl);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", getUserAgent(context))
                    .post(RequestBody.create(TYPE_JSON, payload));

            String httpLogin = m_prefs.getString("http_login", "").trim();
            String httpPassword = m_prefs.getString("http_password", "").trim();

            if (!httpLogin.isEmpty()) {
                if (m_transportDebugging) Log.d(TAG, "Using HTTP Basic authentication.");

                requestBuilder.addHeader("Authorization", Credentials.basic(httpLogin, httpPassword));
            }

            Request request = requestBuilder.build();

            ResponseProgressListener listener = new ResponseProgressListener() {
                @Override
                public void update(HttpUrl url, long bytesRead, long contentLength) {
                    // Log.d(TAG, "[progress] " + url + " " + bytesRead + " of " + contentLength);

                    if (contentLength > 0)
                        caller.notifyProgress((int) (bytesRead * 100f / contentLength));
                }
            };

            /* lets shamelessly hijack OkHttpProgressGlideModule */

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addNetworkInterceptor(createInterceptor(listener))
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful()) {
                String payloadReceived = response.body().string();

                if (m_transportDebugging) Log.d(TAG, "<<< " + payloadReceived);

                JsonElement result = JsonParser.parseString(payloadReceived);
                JsonObject resultObj = result.getAsJsonObject();

                int statusCode = resultObj.get("status").getAsInt();

                caller.setStatusCode(statusCode);

                switch (statusCode) {
                    case API_STATUS_OK:
                        caller.setLastError(ApiError.SUCCESS);
                        return result.getAsJsonObject().get("content");
                    case API_STATUS_ERR:
                        JsonObject contentObj = resultObj.get("content").getAsJsonObject();
                        String error = contentObj.get("error").getAsString();

                        switch (error) {
                            case "LOGIN_ERROR":
                            case "NOT_LOGGED_IN":
                                caller.setLastError(ApiError.LOGIN_FAILED);
                                break;
                            case "API_DISABLED":
                                caller.setLastError(ApiError.API_DISABLED);
                                break;
                            case "INCORRECT_USAGE":
                                caller.setLastError(ApiError.API_INCORRECT_USAGE);
                                break;
                            case "UNKNOWN_METHOD":
                                caller.setLastError(ApiError.API_UNKNOWN_METHOD);
                                break;
                            default:
                                Log.d(TAG, "Unknown API error: " + error);
                                caller.setLastErrorMessage(error);
                                caller.setLastError(ApiError.API_UNKNOWN);
                                break;
                        }
                }

            } else {
                switch (response.code()) {
                    case 400:
                        caller.setLastError(ApiError.HTTP_BAD_REQUEST);
                        break;
                    case 401:
                        caller.setLastError(ApiError.HTTP_UNAUTHORIZED);
                        break;
                    case 403:
                        caller.setLastError(ApiError.HTTP_FORBIDDEN);
                        break;
                    case 404:
                        caller.setLastError(ApiError.HTTP_NOT_FOUND);
                        break;
                    case 500:
                    case 501:
                        caller.setLastError(ApiError.HTTP_SERVER_ERROR);
                        break;
                    default:
                        Log.d(TAG, "HTTP response code: " + response.code());
                        caller.setLastErrorMessage("HTTP response code: " + response.code());
                        caller.setLastError(ApiError.HTTP_OTHER_ERROR);
                        break;
                }
            }

            return null;
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            caller.setLastError(ApiError.SSL_REJECTED);
            caller.setLastErrorMessage(e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            caller.setLastError(ApiError.IO_ERROR);
            caller.setLastErrorMessage(e.getMessage());

            if (e.getMessage() != null) {
                if (e.getMessage().matches("Hostname [^ ]+ was not verified")) {
                    caller.setLastError(ApiError.SSL_HOSTNAME_REJECTED);
                }
            }

            e.printStackTrace();
        } catch (com.google.gson.JsonSyntaxException e) {
            caller.setLastError(ApiError.PARSE_ERROR);
            caller.setLastErrorMessage(e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            caller.setLastError(ApiError.OTHER_ERROR);
            caller.setLastErrorMessage(e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private interface ResponseProgressListener {
        void update(HttpUrl url, long bytesRead, long contentLength);
    }

    private static Interceptor createInterceptor(final ResponseProgressListener listener) {
        return chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            return response.newBuilder()
                    .body(new ProgressResponseBody(request.url(), response.body(), listener))
                    .build();
        };
    }

    private static class ProgressResponseBody extends ResponseBody {
        private final HttpUrl url;
        private final ResponseBody responseBody;
        private final ResponseProgressListener progressListener;
        private BufferedSource bufferedSource;

        public ProgressResponseBody(HttpUrl url, ResponseBody responseBody,
                                    ResponseProgressListener progressListener) {

            this.url = url;
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override
        public long contentLength() {
            return responseBody.contentLength();
        }

        @Override
        public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(new ForwardingSource(responseBody.source()) {
                    long totalBytesRead = 0L;

                    @Override
                    public long read(Buffer sink, long byteCount) throws IOException {
                        long bytesRead = super.read(sink, byteCount);
                        long fullLength = responseBody.contentLength();
                        if (bytesRead == -1) { // this source is exhausted
                            totalBytesRead = fullLength;
                        } else {
                            totalBytesRead += bytesRead;
                        }
                        progressListener.update(url, totalBytesRead, fullLength);
                        return bytesRead;
                    }
                });
            }
            return bufferedSource;
        }
    }

    private static String getUserAgent(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().
                    getPackageInfo(context.getPackageName(), 0);

            return String.format(Locale.ENGLISH,
                    "Tiny Tiny RSS (Android) %1$s (%2$d) %3$s",
                    packageInfo.versionName,
                    packageInfo.versionCode,
                    System.getProperty("http.agent"));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();

            return String.format(Locale.ENGLISH,
                    "Tiny Tiny RSS (Android) Unknown %1$s",
                    System.getProperty("http.agent"));
        }
    }
}
