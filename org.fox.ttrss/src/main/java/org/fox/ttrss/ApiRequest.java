package org.fox.ttrss;

import static org.fox.ttrss.ApiCommon.ApiError;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiRequest implements ApiCommon.ApiCaller {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    protected int m_apiStatusCode = 0;

    private final Context m_context;
    private final Handler m_mainHandler = new Handler(Looper.getMainLooper());
    protected String m_lastErrorMessage;

    protected ApiError m_lastError;

    public ApiRequest(Context context) {
        m_context = context;
        m_lastError = ApiError.UNKNOWN_ERROR;
    }

    public void execute(HashMap<String, String> map) {
        EXECUTOR.submit(() -> {
            final JsonElement result = ApiCommon.performRequest(m_context, map, this);
            m_mainHandler.post(() -> onPostExecute(result));
        });
    }

    protected void onPostExecute(JsonElement result) {
    }

    public int getErrorMessage() {
        return ApiCommon.getErrorMessage(m_lastError);
    }

    @Override
    public void setStatusCode(int statusCode) {
        m_apiStatusCode = statusCode;
    }

    @Override
    public void setLastError(ApiError lastError) {
        m_lastError = lastError;
    }

    @Override
    public void setLastErrorMessage(String message) {
        m_lastErrorMessage = message;
    }

    @Override
    public void notifyProgress(int progress) {
    }
}
