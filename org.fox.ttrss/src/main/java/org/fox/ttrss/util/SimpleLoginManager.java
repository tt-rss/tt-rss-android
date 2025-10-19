package org.fox.ttrss.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.fox.ttrss.ApiRequest;

import java.util.HashMap;

public abstract class SimpleLoginManager {
    private static final String TAG = SimpleLoginManager.class.getSimpleName();

    protected class LoginRequest extends ApiRequest {
        private final int m_requestId;
        protected String m_sessionId;
        protected int m_apiLevel;
        protected Context m_context;

        public LoginRequest(Context context, int requestId) {
            super(context);
            m_context = context;
            m_requestId = requestId;
        }

        protected void onPostExecute(JsonElement result) {
            Log.d(TAG, "onPostExecute");

            if (result != null) {
                try {
                    JsonObject content = result.getAsJsonObject();
                    if (content != null) {
                        m_sessionId = content.get("session_id").getAsString();

                        Log.d(TAG, "[SLM] Authenticated!");

                        ApiRequest req = new ApiRequest(m_context) {
                            protected void onPostExecute(JsonElement result) {
                                m_apiLevel = 0;

                                if (result != null) {
                                    try {
                                        m_apiLevel = result.getAsJsonObject()
                                                .get("level").getAsInt();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                Log.d(TAG, "[SLM] Received API level: " + m_apiLevel);

                                onLoginSuccess(m_requestId, m_sessionId, m_apiLevel);
                            }
                        };

                        HashMap<String, String> map = new HashMap<>();
                        map.put("sid", m_sessionId);
                        map.put("op", "getApiLevel");

                        req.execute(map);

                        return;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            m_sessionId = null;

            onLoginFailed(m_requestId, this);
        }

    }

    public void logIn(Context context, int requestId, final String login, final String password) {
        LoginRequest ar = new LoginRequest(context, requestId);

        HashMap<String, String> map = new HashMap<>();
        map.put("op", "login");
        map.put("user", login.trim());
        map.put("password", password.trim());

        onLoggingIn(requestId);

        ar.execute(map);
    }

    protected abstract void onLoggingIn(int requestId);

    protected abstract void onLoginSuccess(int requestId, String sessionId, int apiLevel);

    protected abstract void onLoginFailed(int requestId, ApiRequest ar);

}
