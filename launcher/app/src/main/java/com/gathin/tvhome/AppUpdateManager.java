package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.Executors;

/**
 * نظام التحديث الهوائي الذكي (In-App OTA Updater)
 * يفحص GitHub Releases للإصدارات الجديدة ويحمّلها ويثبّتها بضغطة زر واحدة.
 */
public class AppUpdateManager {

    public static final String GITHUB_REPO = "Omar-i3/CleanYourTV";
    public static final String RELEASES_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    public interface UpdateCheckCallback {
        void onUpdateAvailable(String newVersion, String changelog, String downloadUrl);
        void onUpToDate();
        void onError(String message);
    }

    /**
     * فحص وجود تحديث جديد في الخلفية
     */
    public static void checkForUpdate(Context context, UpdateCheckCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            try {
                URL url = new URL(RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "CleanYourTV-Launcher");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A");
                    String response = scanner.hasNext() ? scanner.next() : "";
                    in.close();

                    JSONObject releaseJson = new JSONObject(response);
                    String tagName = releaseJson.optString("tag_name", "").replace("v", "").trim();
                    String changelog = releaseJson.optString("body", "تحديث جديد وتحسينات في الأداء");

                    String downloadUrl = null;
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    String currentVersion = BuildConfig.VERSION_NAME;
                    if (isNewerVersion(currentVersion, tagName) && downloadUrl != null) {
                        final String finalDownloadUrl = downloadUrl;
                        mainHandler.post(() -> callback.onUpdateAvailable(tagName, changelog, finalDownloadUrl));
                    } else {
                        mainHandler.post(callback::onUpToDate);
                    }
                } else {
                    int respCode = conn.getResponseCode();
                    mainHandler.post(() -> callback.onError("رمز الاستجابة: " + respCode));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * فحص صامت عند فتح اللانشر: يعرض تنبيهاً فقط إذا وجد تحديثاً جديداً
     */
    public static void checkSilently(Activity activity) {
        checkForUpdate(activity, new UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String changelog, String downloadUrl) {
                showUpdateDialog(activity, newVersion, changelog, downloadUrl);
            }

            @Override
            public void onUpToDate() {}

            @Override
            public void onError(String message) {}
        });
    }

    /**
     * نافذة تأكيد التحديث بتصميم تلفزيوني
     */
    public static void showUpdateDialog(Activity activity, String newVersion, String changelog, String downloadUrl) {
        if (activity.isFinishing()) return;

        new AlertDialog.Builder(activity)
                .setTitle("🎉 يتوفر تحديث جديد للواجهة (v" + newVersion + ")")
                .setMessage("المميزات والتحسينات في هذا الإصدار:\n\n" + changelog + "\n\nهل ترغب في تحميل وتثبيت التحديث الآن؟")
                .setPositiveButton("✓ تحديث الآن", (dialog, which) -> downloadAndInstall(activity, downloadUrl, newVersion))
                .setNegativeButton("لاحقاً", null)
                .show();
    }

    /**
     * تحميل الـ APK ثم فتح شاشة تثبيت الأندرويد مباشرة
     */
    public static void downloadAndInstall(Activity activity, String downloadUrl, String versionName) {
        Toast.makeText(activity, "⏳ جاري تحميل التحديث الجديد في الخلفية...", Toast.LENGTH_LONG).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "CleanYourTV-Launcher");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                File downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir == null) downloadDir = activity.getCacheDir();
                File apkFile = new File(downloadDir, "CleanYourTV_v" + versionName + ".apk");

                InputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream out = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                out.flush();
                out.close();
                in.close();

                mainHandler.post(() -> {
                    try {
                        Toast.makeText(activity, "✓ اكتمل التحميل! جاري التثبيت...", Toast.LENGTH_SHORT).show();

                        Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(installIntent);
                    } catch (Exception e) {
                        Toast.makeText(activity, "خطأ أثناء بدء التثبيت: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "تعذر تحميل التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static boolean isNewerVersion(String currentVersion, String remoteVersion) {
        try {
            String[] currParts = currentVersion.split("\\.");
            String[] remParts = remoteVersion.split("\\.");
            int length = Math.max(currParts.length, remParts.length);

            for (int i = 0; i < length; i++) {
                int curr = i < currParts.length ? Integer.parseInt(currParts[i].replaceAll("\\D+", "")) : 0;
                int rem = i < remParts.length ? Integer.parseInt(remParts[i].replaceAll("\\D+", "")) : 0;
                if (rem > curr) return true;
                if (rem < curr) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }
}

