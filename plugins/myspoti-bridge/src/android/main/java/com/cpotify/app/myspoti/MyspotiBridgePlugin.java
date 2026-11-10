package com.cpotify.app.myspoti;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebViewClient;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.UserAgentMetadata;
import androidx.webkit.ProfileStore;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "MyspotiBridge")
public class MyspotiBridgePlugin extends Plugin {

    private static final String TAG = "MyspotiBridge";
    private static final String PLAYER_URL = "https://open.spotify.com/";
    private static final String PLAYER_HOST = "open.spotify.com";
    private static final String LOGIN_URL = "https://accounts.spotify.com/login";
    private static final String LICENSE_PATH = "/widevine-license";
    private static final int PAGE_LOAD_TIMEOUT_MS = 30000;
    private static final int LOAD_WATCH_MS = 8000;
    private static final int LOAD_RETRIES = 3;
    private static final int DRM_WATCH_MS = 15000;
    private static final int DRM_REBUILD_WINDOW_MS = 10 * 60 * 1000;
    private static final int DRM_REBUILDS_MAX = 2;
    private static final int LIMIT_WINDOW_MS = 4000;
    private static final int LIMIT_COUNT = 3;
    private static final int LIMIT_HOLD_MS = 5000;
    private static final int LIMIT_HOLD_MAX_MS = 60000;
    private static final int LIMIT_FORGET_MS = 30000;
    private static final int LICENSE_ANSWERS_LOGGED = 5;
    private static final int REBUILD_WATCH_MS = 1500;
    private static final String ROOT_KEY = "player_root_url";
    private static final String SIGNED_IN_KEY = "signed_in_since";
    private static final Set<String> LICENSE_HEADERS = Set.of("retry-after", "www-authenticate", "x-spotify-license-error");
    private static final List<Long> RETRY_PAGE_MS = Arrays.asList(5000L, 15000L, 30000L, 60000L);
    private static final String CONSOLE_NOISE = ".*(font|preload|storage|access|refused|blocked|cors|mixed|content).*";

    private Handler mainHandler;
    private WebPlayerHost webPlayerHost;
    private final CopyOnWriteArrayList<PluginCall> pendingCalls = new CopyOnWriteArrayList<>();
    private final Map<String, PluginCall> callMap = new ConcurrentHashMap<>();

    @Override
    public void load() {
        super.load();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize WebPlayerHost on main thread
        mainHandler.post(() -> {
            Context context = getContext();
            if (context != null) {
                webPlayerHost = new WebPlayerHost(context, this);
                webPlayerHost.load();
                
                // Process any pending calls
                for (PluginCall call : pendingCalls) {
                    handleCall(call);
                }
                pendingCalls.clear();
            }
        });
    }

    private void handleCall(PluginCall call) {
        if (webPlayerHost == null) {
            pendingCalls.add(call);
            return;
        }
        
        String methodName = call.getMethodName();
        try {
            Method method = getClass().getMethod(methodName, PluginCall.class);
            method.invoke(this, call);
        } catch (NoSuchMethodException e) {
            call.reject("Method not found: " + methodName);
        } catch (Exception e) {
            Log.e(TAG, "Error calling " + methodName, e);
            call.reject("Error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void loadPlayer(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.load();
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void play(PluginCall call) {
        if (webPlayerHost != null) {
            String contextUri = call.getString("contextUri");
            String trackUri = call.getString("trackUri");
            String trackUid = call.getString("trackUid");
            Integer index = call.getInt("index");
            Long positionMs = call.getLong("positionMs");
            
            webPlayerHost.callBridge("player.play", buildPlayArgs(contextUri, trackUri, trackUid, index, positionMs));
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void pause(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridge("player.pause", new JSONObject());
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void resume(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridge("player.resume", new JSONObject());
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void seek(PluginCall call) {
        if (webPlayerHost != null) {
            long positionMs = call.getLong("positionMs", 0);
            try {
                JSONObject args = new JSONObject();
                args.put("positionMs", positionMs);
                webPlayerHost.callBridge("player.seek", args);
            } catch (JSONException e) {
                call.reject("Invalid args");
                return;
            }
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void next(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridge("player.next", new JSONObject());
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void previous(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridge("player.previous", new JSONObject());
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setShuffle(PluginCall call) {
        if (webPlayerHost != null) {
            boolean on = call.getBoolean("on", false);
            try {
                JSONObject args = new JSONObject();
                args.put("on", on);
                webPlayerHost.callBridge("player.setShuffle", args);
            } catch (JSONException e) {
                call.reject("Invalid args");
                return;
            }
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setRepeat(PluginCall call) {
        if (webPlayerHost != null) {
            int mode = call.getInt("mode", 0);
            try {
                JSONObject args = new JSONObject();
                args.put("mode", mode);
                webPlayerHost.callBridge("player.setRepeat", args);
            } catch (JSONException e) {
                call.reject("Invalid args");
                return;
            }
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setSpeed(PluginCall call) {
        if (webPlayerHost != null) {
            double speed = call.getDouble("speed", 1.0);
            try {
                JSONObject args = new JSONObject();
                args.put("speed", speed);
                webPlayerHost.callBridge("player.setSpeed", args);
            } catch (JSONException e) {
                call.reject("Invalid args");
                return;
            }
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void getState(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("player.state", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void getQueue(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("player.queue", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void getAudioInfo(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("player.audioInfo", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setAudioQuality(PluginCall call) {
        if (webPlayerHost != null) {
            String level = call.getString("level", "auto");
            try {
                JSONObject args = new JSONObject();
                args.put("level", level);
                webPlayerHost.callBridge("player.setAudioQuality", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void preloadNext(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("player.preloadNext", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void addToQueue(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray urisArray = call.getArray("uris");
            if (urisArray == null) {
                call.reject("Missing uris array");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("uris", urisArray);
                webPlayerHost.callBridge("queue.add", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void removeFromQueue(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray itemsArray = call.getArray("items");
            if (itemsArray == null) {
                call.reject("Missing items array");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("items", itemsArray);
                webPlayerHost.callBridge("queue.remove", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void clearQueue(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridge("queue.clear", new JSONObject());
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void skipTo(PluginCall call) {
        if (webPlayerHost != null) {
            String uri = call.getString("uri");
            String uid = call.getString("uid");
            try {
                JSONObject args = new JSONObject();
                args.put("uri", uri);
                if (uid != null) args.put("uid", uid);
                webPlayerHost.callBridge("queue.skipTo", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void insertIntoQueue(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray urisArray = call.getArray("uris");
            JSONObject before = call.getObject("before");
            if (urisArray == null || before == null) {
                call.reject("Missing uris or before");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("uris", urisArray);
                args.put("before", before);
                webPlayerHost.callBridge("queue.insert", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void reorderQueue(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray itemsArray = call.getArray("items");
            JSONObject before = call.getObject("before");
            JSONObject after = call.getObject("after");
            if (itemsArray == null || (before == null && after == null)) {
                call.reject("Missing items or before/after");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("items", itemsArray);
                if (before != null) args.put("before", before);
                if (after != null) args.put("after", after);
                webPlayerHost.callBridge("queue.reorder", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void mute(PluginCall call) {
        if (webPlayerHost != null) {
            boolean on = call.getBoolean("on", true);
            try {
                JSONObject args = new JSONObject();
                args.put("on", on);
                webPlayerHost.callBridgeWithCallback("media.mute", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void hush(PluginCall call) {
        if (webPlayerHost != null) {
            boolean on = call.getBoolean("on", true);
            try {
                JSONObject args = new JSONObject();
                args.put("on", on);
                webPlayerHost.callBridgeWithCallback("media.hush", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void getMediaState(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("media.state", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setMediaVolume(PluginCall call) {
        if (webPlayerHost != null) {
            double level = call.getDouble("level", 1.0);
            try {
                JSONObject args = new JSONObject();
                args.put("level", level);
                webPlayerHost.callBridgeWithCallback("media.volume", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void restoreMediaVolume(PluginCall call) {
        if (webPlayerHost != null) {
            try {
                JSONObject args = new JSONObject();
                args.put("restore", true);
                webPlayerHost.callBridgeWithCallback("media.volume", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void fadeMediaVolume(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray stepsArray = call.getArray("steps");
            int ms = call.getInt("ms", 12);
            boolean restore = call.getBoolean("restore", false);
            JSONObject rise = call.getObject("rise");
            
            if (stepsArray == null) {
                call.reject("Missing steps array");
                return;
            }
            
            try {
                JSONObject args = new JSONObject();
                args.put("steps", stepsArray);
                args.put("ms", ms);
                args.put("restore", restore);
                if (rise != null) args.put("rise", rise);
                webPlayerHost.callBridgeWithCallback("media.fade", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void rampMediaVolume(PluginCall call) {
        if (webPlayerHost != null) {
            double to = call.getDouble("to", 1.0);
            int ms = call.getInt("ms", 150);
            try {
                JSONObject args = new JSONObject();
                args.put("to", to);
                args.put("ms", ms);
                webPlayerHost.callBridgeWithCallback("media.ramp", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void riseMediaVolume(PluginCall call) {
        if (webPlayerHost != null) {
            int ms = call.getInt("ms", 500);
            int wait = call.getInt("wait", 3000);
            boolean keep = call.getBoolean("keep", false);
            boolean switch_ = call.getBoolean("switch", false);
            try {
                JSONObject args = new JSONObject();
                args.put("ms", ms);
                args.put("wait", wait);
                args.put("keep", keep);
                args.put("switch", switch_);
                webPlayerHost.callBridgeWithCallback("media.rise", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void getVolume(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("volume.get", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        if (webPlayerHost != null) {
            double level = call.getDouble("level", 1.0);
            try {
                JSONObject args = new JSONObject();
                args.put("level", level);
                webPlayerHost.callBridge("volume.set", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void checkLibrary(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray urisArray = call.getArray("uris");
            if (urisArray == null) {
                call.reject("Missing uris array");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("uris", urisArray);
                webPlayerHost.callBridgeWithCallback("library.contains", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void addToLibrary(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray urisArray = call.getArray("uris");
            if (urisArray == null) {
                call.reject("Missing uris array");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("uris", urisArray);
                webPlayerHost.callBridge("library.add", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void removeFromLibrary(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray urisArray = call.getArray("uris");
            if (urisArray == null) {
                call.reject("Missing uris array");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("uris", urisArray);
                webPlayerHost.callBridge("library.remove", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void listServices(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("service.list", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void callService(PluginCall call) {
        if (webPlayerHost != null) {
            String service = call.getString("service");
            String method = call.getString("method");
            JSONArray argsArray = call.getArray("args");
            if (service == null || method == null) {
                call.reject("Missing service or method");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("service", service);
                args.put("method", method);
                if (argsArray != null) args.put("args", argsArray);
                webPlayerHost.callBridgeWithCallback("service.call", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setPlaylistCover(PluginCall call) {
        if (webPlayerHost != null) {
            String uri = call.getString("uri");
            String jpegBase64 = call.getString("jpegBase64");
            if (uri == null) {
                call.reject("Missing uri");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("uri", uri);
                if (jpegBase64 != null) args.put("jpeg", jpegBase64);
                webPlayerHost.callBridge("playlist.setCover", args);
                call.resolve();
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void gqlQuery(PluginCall call) {
        if (webPlayerHost != null) {
            String name = call.getString("name");
            String hash = call.getString("hash");
            JSONObject variables = call.getObject("variables");
            if (name == null || hash == null) {
                call.reject("Missing name or hash");
                return;
            }
            try {
                JSONObject args = new JSONObject();
                args.put("name", name);
                args.put("hash", hash);
                if (variables != null) args.put("variables", variables);
                webPlayerHost.callBridgeWithCallback("gql.query", args, result -> {
                    if (result.has("ok") && result.optBoolean("ok")) {
                        call.resolve(result.optJSONObject("value"));
                    } else {
                        call.reject(result.optString("error", "Unknown error"));
                    }
                });
            } catch (JSONException e) {
                call.reject("Invalid args");
            }
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void getToken(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.callBridgeWithCallback("token.get", new JSONObject(), result -> {
                if (result.has("ok") && result.optBoolean("ok")) {
                    call.resolve(result.optJSONObject("value"));
                } else {
                    call.reject(result.optString("error", "Unknown error"));
                }
            });
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setAdBlock(PluginCall call) {
        if (webPlayerHost != null) {
            boolean enabled = call.getBoolean("enabled", true);
            webPlayerHost.setAdBlockEnabled(enabled);
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void setBlockRules(PluginCall call) {
        if (webPlayerHost != null) {
            JSONArray rulesArray = call.getArray("rules");
            if (rulesArray != null) {
                List<String> rules = new java.util.ArrayList<>();
                for (int i = 0; i < rulesArray.length(); i++) {
                    rules.add(rulesArray.optString(i));
                }
                webPlayerHost.setBlockRules(rules);
            }
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    @PluginMethod
    public void reload(PluginCall call) {
        if (webPlayerHost != null) {
            webPlayerHost.reload();
            call.resolve();
        } else {
            call.reject("WebPlayerHost not initialized");
        }
    }

    private JSONObject buildPlayArgs(String contextUri, String trackUri, String trackUid, Integer index, Long positionMs) {
        try {
            JSONObject args = new JSONObject();
            args.put("contextUri", contextUri);
            if (trackUri != null) args.put("trackUri", trackUri);
            if (trackUid != null) args.put("trackUid", trackUid);
            if (index != null) args.put("index", index);
            if (positionMs != null) args.put("positionMs", positionMs);
            return args;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // Callback interface for bridge responses
    public interface BridgeCallback {
        void onResult(JSONObject result);
    }
}