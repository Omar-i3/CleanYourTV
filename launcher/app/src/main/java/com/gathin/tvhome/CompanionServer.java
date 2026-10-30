package com.gathin.tvhome;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * خادم ويب محلي خفيف ومستقل (Mobile Web Companion Server):
 * يتيح التحكم بالريموت وإرسال النصوص والرسائل العائلية ورفع الصور من الجوال مباشرة.
 */
public final class CompanionServer {

    private static final String TAG = "TvHome.Companion";
    public static final int PORT = 8080;

    private static CompanionServer instance;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;

    public interface MessageListener {
        void onMessageReceived(String message);
    }

    private static MessageListener messageListener;

    public static void setMessageListener(MessageListener listener) {
        messageListener = listener;
    }

    private CompanionServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized CompanionServer getInstance(Context context) {
        if (instance == null) {
            instance = new CompanionServer(context);
        }
        return instance;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        threadPool = Executors.newCachedThreadPool();
        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "Companion server started on port " + PORT);
                while (isRunning && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    threadPool.execute(() -> handleClient(client));
                }
            } catch (Exception e) {
                Log.e(TAG, "Server error: " + e.getMessage());
            }
        });
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (threadPool != null) threadPool.shutdownNow();
        } catch (Exception ignored) {}
    }

    public static String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return Formatter.formatIpAddress(ip);
                }
            }
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) return;

            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];

            // قراءة Headers
            int contentLength = 0;
            String contentType = "";
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (lower.startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                }
            }

            // معالجة الـ API والصفحات
            if (path.equals("/") || path.equals("/index.html")) {
                sendHtmlResponse(out, getCompanionHtml());
            } else if (path.startsWith("/api/key") && method.equalsIgnoreCase("POST")) {
                String body = readBody(reader, contentLength);
                handleKeyCommand(body);
                sendJsonResponse(out, "{\"status\":\"ok\"}");
            } else if (path.startsWith("/api/message") && method.equalsIgnoreCase("POST")) {
                String body = readBody(reader, contentLength);
                handleMessageCommand(body);
                sendJsonResponse(out, "{\"status\":\"ok\"}");
            } else if (path.startsWith("/api/config") && method.equalsIgnoreCase("POST")) {
                String body = readBody(reader, contentLength);
                handleConfigCommand(body);
                sendJsonResponse(out, "{\"status\":\"ok\"}");
            } else if (path.startsWith("/api/status")) {
                String status = "{\"city\":\"" + PrayerManager.getCityName(context) + "\"}";
                sendJsonResponse(out, status);
            } else {
                send404(out);
            }

        } catch (Exception ignored) {}
    }

    private String readBody(BufferedReader reader, int len) throws Exception {
        if (len <= 0) return "";
        char[] buf = new char[len];
        int read = 0;
        while (read < len) {
            int r = reader.read(buf, read, len - read);
            if (r == -1) break;
            read += r;
        }
        return new String(buf, 0, read);
    }

    private void handleKeyCommand(String body) {
        Map<String, String> params = parseParams(body);
        String key = params.get("key");
        if (key == null) return;

        int keyCode = 0;
        switch (key.toUpperCase()) {
            case "UP": keyCode = KeyEvent.KEYCODE_DPAD_UP; break;
            case "DOWN": keyCode = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "LEFT": keyCode = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "RIGHT": keyCode = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "OK": keyCode = KeyEvent.KEYCODE_DPAD_CENTER; break;
            case "BACK": keyCode = KeyEvent.KEYCODE_BACK; break;
            case "HOME": keyCode = KeyEvent.KEYCODE_HOME; break;
        }

        if (keyCode != 0) {
            final int code = keyCode;
            threadPool.execute(() -> {
                try {
                    new Instrumentation().sendKeyDownUpSync(code);
                } catch (Exception ignored) {}
            });
        }
    }

    private void handleMessageCommand(String body) {
        Map<String, String> params = parseParams(body);
        String text = params.get("text");
        if (text != null && !text.isEmpty()) {
            mainHandler.post(() -> {
                Toast.makeText(context, "💌 رسالة من الجوال:\n" + text, Toast.LENGTH_LONG).show();
                if (messageListener != null) {
                    messageListener.onMessageReceived(text);
                }
            });
        }
    }

    private void handleConfigCommand(String body) {
        Map<String, String> params = parseParams(body);
        String cityName = params.get("city");
        String teamName = params.get("team");
        String teamIdStr = params.get("team_id");

        if (cityName != null) {
            for (PrayerManager.City c : PrayerManager.CITIES) {
                if (c.name.equals(cityName)) {
                    PrayerManager.setSelectedCity(context, c);
                    break;
                }
            }
        }

        String team2Name = params.get("team2");
        String team2IdStr = params.get("team2_id");
        String font = params.get("font");
        String greeting = params.get("greeting");

        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (teamName != null) editor.putString("selected_team_name", teamName);
        if (teamIdStr != null) {
            try {
                editor.putInt("selected_team_id", Integer.parseInt(teamIdStr));
            } catch (Exception ignored) {}
        }
        if (team2Name != null) editor.putString("selected_team2_name", team2Name);
        if (team2IdStr != null) {
            try {
                editor.putInt("selected_team2_id", Integer.parseInt(team2IdStr));
            } catch (Exception ignored) {}
        }
        if (font != null) editor.putString("selected_font", font);
        if (greeting != null) editor.putString("greeting_text", greeting);
        editor.apply();

        // بث التحديث لشاشة التوقف
        try {
            Intent intent = new Intent("com.chelseasaver.dream.ACTION_UPDATE_CONFIG");
            intent.putExtra("city_name", cityName != null ? cityName : PrayerManager.getCityName(context));
            intent.putExtra("city_lat", PrayerManager.getCityLat(context));
            intent.putExtra("city_lon", PrayerManager.getCityLon(context));
            intent.putExtra("team_name", prefs.getString("selected_team_name", "الهلال"));
            intent.putExtra("team_id", prefs.getInt("selected_team_id", 929));
            intent.putExtra("team2_name", prefs.getString("selected_team2_name", ""));
            intent.putExtra("team2_id", prefs.getInt("selected_team2_id", 0));
            intent.putExtra("show_news", prefs.getBoolean("ss_show_news", true));
            intent.putExtra("match_rotate_sec", prefs.getInt("ss_match_rotate_sec", 10));
            intent.putExtra("arabic_numerals", prefs.getBoolean("ss_arabic_numerals", true));
            intent.putExtra("font_family", prefs.getString("selected_font", "thmanyah"));
            intent.putExtra("font_scale", prefs.getString("font_scale", "normal"));
            intent.putExtra("bg_dim", prefs.getString("bg_dim", "medium"));
            intent.putExtra("clock_format", prefs.getBoolean("clock_24h", false) ? "24h" : "12h");
            intent.putExtra("clock_seconds", prefs.getBoolean("clock_seconds", false));
            intent.putExtra("greeting_text", prefs.getString("greeting_text", "منزل العائلة المبارك 🌿"));
            intent.putExtra("news_speed_sec", prefs.getInt("news_speed_sec", 10));
            intent.setPackage("com.chelseasaver.dream");
            context.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private Map<String, String> parseParams(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx != -1) {
                    String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(k, v);
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private void sendHtmlResponse(OutputStream out, String html) throws Exception {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void sendJsonResponse(OutputStream out, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void send404(OutputStream out) throws Exception {
        String msg = "404 Not Found";
        out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: " + msg.length() + "\r\n\r\n" + msg).getBytes());
        out.flush();
    }

    private String getCompanionHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"ar\" dir=\"rtl\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "<title>CleanYourTV Companion</title>\n" +
                "<style>\n" +
                ":root { --bg: #070D18; --card: rgba(15, 23, 42, 0.75); --blue: #38BDF8; --gold: #F6C85F; --accent: #005CE6; }\n" +
                "* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; }\n" +
                "body { background: var(--bg); color: #fff; font-family: system-ui, -apple-system, sans-serif; text-align: center; padding: 16px; user-select: none; }\n" +
                ".header { margin-bottom: 20px; }\n" +
                ".header h1 { font-size: 20px; color: var(--blue); font-weight: 700; }\n" +
                ".header p { font-size: 13px; color: #94A3B8; margin-top: 4px; }\n" +
                ".dpad-box { background: var(--card); border: 1px solid rgba(255,255,255,0.1); border-radius: 24px; padding: 24px 16px; max-width: 320px; margin: 0 auto 20px; backdrop-filter: blur(12px); }\n" +
                ".dpad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; align-items: center; justify-items: center; }\n" +
                ".btn { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12); color: #fff; border-radius: 16px; width: 68px; height: 68px; font-size: 20px; font-weight: bold; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: 0.1s; }\n" +
                ".btn:active { background: var(--accent); transform: scale(0.92); }\n" +
                ".btn-ok { background: linear-gradient(135deg, #005CE6, #38BDF8); font-size: 16px; width: 72px; height: 72px; border-radius: 50%; box-shadow: 0 4px 16px rgba(0,92,230,0.4); }\n" +
                ".row-btns { display: flex; justify-content: space-around; margin-top: 18px; }\n" +
                ".row-btns .btn { width: 45%; height: 48px; font-size: 14px; border-radius: 12px; }\n" +
                ".card-action { background: var(--card); border: 1px solid rgba(255,255,255,0.1); border-radius: 20px; padding: 16px; max-width: 320px; margin: 0 auto 16px; text-align: right; }\n" +
                ".card-action h3 { font-size: 14px; color: var(--gold); margin-bottom: 10px; }\n" +
                ".input-text { width: 100%; background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.15); border-radius: 10px; padding: 10px; color: #fff; font-size: 14px; outline: none; margin-bottom: 8px; }\n" +
                ".btn-send { width: 100%; background: var(--accent); color: #fff; border: none; border-radius: 10px; padding: 10px; font-weight: bold; cursor: pointer; }\n" +
                ".btn-send:active { opacity: 0.8; }\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class=\"header\">\n" +
                "  <h1>📱 تحكم CleanYourTV الذكي</h1>\n" +
                "  <p>متصل بالتلفاز بنجاح عبر الشبكة المحلية</p>\n" +
                "</div>\n" +
                "<div class=\"dpad-box\">\n" +
                "  <div class=\"dpad\">\n" +
                "    <div></div><div class=\"btn\" onclick=\"sendKey('UP')\">▲</div><div></div>\n" +
                "    <div class=\"btn\" onclick=\"sendKey('LEFT')\">◀</div>\n" +
                "    <div class=\"btn btn-ok\" onclick=\"sendKey('OK')\">OK</div>\n" +
                "    <div class=\"btn\" onclick=\"sendKey('RIGHT')\">▶</div>\n" +
                "    <div></div><div class=\"btn\" onclick=\"sendKey('DOWN')\">▼</div><div></div>\n" +
                "  </div>\n" +
                "  <div class=\"row-btns\">\n" +
                "    <div class=\"btn\" onclick=\"sendKey('BACK')\">↩ رجوع</div>\n" +
                "    <div class=\"btn\" onclick=\"sendKey('HOME')\">⌂ الرئيسية</div>\n" +
                "  </div>\n" +
                "</div>\n" +
                "<div class=\"card-action\">\n" +
                "  <h3>💌 إرسال رسالة عائلية للشاشة</h3>\n" +
                "  <input type=\"text\" id=\"msgText\" class=\"input-text\" placeholder=\"مثال: العشاء جاهز 🍲\">\n" +
                "  <button class=\"btn-send\" onclick=\"sendMsg()\">إرسال إلى التلفاز</button>\n" +
                "</div>\n" +
                "<div class=\"card-action\">\n" +
                "  <h3>⚽ الفريق الأساسي (الأول)</h3>\n" +
                "  <select id=\"teamSelect\" class=\"input-text\" onchange=\"changeTeam()\">\n" +
                "    <option value=\"929|الهلال\">الهلال</option>\n" +
                "    <option value=\"931|النصر\">النصر</option>\n" +
                "    <option value=\"928|الاتحاد\">الاتحاد</option>\n" +
                "    <option value=\"927|الأهلي\">الأهلي</option>\n" +
                "    <option value=\"86|ريال مدريد\">ريال مدريد</option>\n" +
                "    <option value=\"83|برشلونة\">برشلونة</option>\n" +
                "    <option value=\"364|ليفربول\">ليفربول</option>\n" +
                "    <option value=\"382|مانشستر سيتي\">مانشستر سيتي</option>\n" +
                "  </select>\n" +
                "</div>\n" +
                "<div class=\"card-action\">\n" +
                "  <h3>⚽ النادي الثاني لمتابعة نتائجه معاً</h3>\n" +
                "  <select id=\"team2Select\" class=\"input-text\" onchange=\"changeTeam2()\">\n" +
                "    <option value=\"0|\">🚫 بدون (نادٍ واحد فقط)</option>\n" +
                "    <option value=\"929|الهلال\">الهلال</option>\n" +
                "    <option value=\"931|النصر\">النصر</option>\n" +
                "    <option value=\"928|الاتحاد\">الاتحاد</option>\n" +
                "    <option value=\"927|الأهلي\">الأهلي</option>\n" +
                "    <option value=\"86|ريال مدريد\">ريال مدريد</option>\n" +
                "    <option value=\"83|برشلونة\">برشلونة</option>\n" +
                "    <option value=\"364|ليفربول\">ليفربول</option>\n" +
                "    <option value=\"382|مانشستر سيتي\">مانشستر سيتي</option>\n" +
                "  </select>\n" +
                "</div>\n" +
                "<div class=\"card-action\">\n" +
                "  <h3>🔤 نوع الخط المفضل</h3>\n" +
                "  <select id=\"fontSelect\" class=\"input-text\" onchange=\"changeFont()\">\n" +
                "    <option value=\"thmanyah\">👑 خط ثمانية الفاخر</option>\n" +
                "    <option value=\"dubai\">📺 خط دبي التلفزيوني</option>\n" +
                "    <option value=\"system\">⚙️ خط النظام القياسي</option>\n" +
                "  </select>\n" +
                "</div>\n" +
                "<script>\n" +
                "function haptic() { if (navigator.vibrate) navigator.vibrate(25); }\n" +
                "function sendKey(k) { haptic(); fetch('/api/key', { method: 'POST', body: 'key=' + k }); }\n" +
                "function sendMsg() {\n" +
                "  var t = document.getElementById('msgText').value.trim();\n" +
                "  if (!t) return;\n" +
                "  haptic();\n" +
                "  fetch('/api/message', { method: 'POST', body: 'text=' + encodeURIComponent(t) });\n" +
                "  document.getElementById('msgText').value = '';\n" +
                "  alert('تم إرسال الرسالة إلى شاشة التلفاز');\n" +
                "}\n" +
                "function changeTeam() {\n" +
                "  var val = document.getElementById('teamSelect').value.split('|');\n" +
                "  haptic();\n" +
                "  fetch('/api/config', { method: 'POST', body: 'team_id=' + val[0] + '&team=' + encodeURIComponent(val[1]) });\n" +
                "}\n" +
                "function changeTeam2() {\n" +
                "  var val = document.getElementById('team2Select').value.split('|');\n" +
                "  haptic();\n" +
                "  fetch('/api/config', { method: 'POST', body: 'team2_id=' + val[0] + '&team2=' + encodeURIComponent(val[1]) });\n" +
                "}\n" +
                "function changeFont() {\n" +
                "  var val = document.getElementById('fontSelect').value;\n" +
                "  haptic();\n" +
                "  fetch('/api/config', { method: 'POST', body: 'font=' + encodeURIComponent(val) });\n" +
                "}\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }
}

