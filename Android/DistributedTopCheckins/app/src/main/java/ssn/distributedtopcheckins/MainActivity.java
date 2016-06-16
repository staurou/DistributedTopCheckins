package ssn.distributedtopcheckins;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    public static final int TAKE_PICTURE = 0;

    static class JsHandle {
        Activity activity;
        WebView webView;

        public JsHandle(WebView webView, Activity activity) {
            this.webView = webView;
            this.activity = activity;
        }

        @JavascriptInterface
        void uploadPhoto() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    activity.startActivityForResult(takePicture, TAKE_PICTURE);
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        if (resultCode == RESULT_OK){
            final Uri selectedImage = imageReturnedIntent.getData();
            final WebView webview = (WebView) findViewById(R.id.webView);
            try (InputStream in = new BufferedInputStream(getContentResolver().openInputStream(selectedImage))) {
                Log.d("APP", "avail "+in.available());
                final byte[] data = new byte[in.available()];
                in.read(data);
                webview.post(new Runnable() {
                    @Override
                    public void run() {
                            String imageData = Base64.encodeToString(data, Base64.DEFAULT);
                            Log.d("APP", imageData);
                            webview.loadUrl("javascript:onload= function () { receivePhoto('"+ imageData+"');}", null);
                    }
                });

            } catch (final IOException e) {
                webview.post(new Runnable() {
                    @Override public void run() {
                        webview.loadUrl("javascript:alert('Something went wrong: "+e.getMessage()+"');");
                    }
                });
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WebView webview = (WebView) findViewById(R.id.webView);


        webview.setWebChromeClient(new WebChromeClient() {
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });


        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAppCacheEnabled(true);
        settings.setDomStorageEnabled(true);

        webview.addJavascriptInterface(new JsHandle(webview, this), "Android");

        webview.loadUrl("http://192.168.1.120:8000");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }
}
