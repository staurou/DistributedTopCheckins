package ssn.distributedtopcheckins;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    public static final int TAKE_PICTURE = 0;


    public class JsHandle {
        WebView webView;

        public JsHandle(WebView webView) {
            this.webView = webView;
        }

        @JavascriptInterface
        public void uploadPhoto() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    // Ensure that there's a camera activity to handle the intent
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        // Create the File where the photo should go
                        File photoFile = null;
                        try {
                            photoFile = createImageFile();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        // Continue only if the File was successfully created
                        if (photoFile != null) {
                            Uri photoURI = Uri.fromFile(photoFile);
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                            waitingPhoto = true;
                            startActivityForResult(takePictureIntent, TAKE_PICTURE);
                        }
                    }

                }
            });
        }

        @JavascriptInterface
        public String getPhotoData() {
            return readAsBase64(mCurrentPhotoPath);
        }
    }

    private String mCurrentPhotoPath;
    private String server;
    private boolean waitingPhoto;
    private boolean reloadedWhileWaitingPhoto;
    private LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalCacheDir();
//        File storageDir = new File("/");
        Log.d("APP", storageDir+"");
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private String readAsBase64(String path) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(path))) {
            final byte[] data = new byte[in.available()];
            in.read(data);
            return new String(Base64.encode(data, Base64.NO_WRAP), "UTF-8");
        } catch (final IOException e) {
            Log.e("APP", e.toString());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        waitingPhoto = false;
        if (resultCode == RESULT_OK) {
            final WebView webview = (WebView) findViewById(R.id.webView);
            final String command = !reloadedWhileWaitingPhoto ?
                    "receivePhoto();" :
                    "window.setTimeout(function () {receivePhoto();}, 2000);";
            assert webview != null;
            if (reloadedWhileWaitingPhoto) {
                webview.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        webview.evaluateJavascript(command, null);
                    }
                }, 2000);
            } else {
                webview.post(new Runnable() {
                    @Override
                    public void run() {
                        webview.evaluateJavascript(command, null);
                    }
                });
            }
        } else {
            mCurrentPhotoPath = null;
        }
        reloadedWhileWaitingPhoto = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("mCurrentPhotoPath", mCurrentPhotoPath);
        outState.putString("server", server);
        outState.putBoolean("waitingPhoto", waitingPhoto);
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

            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("APP", cm.message() + " -- From line "
                        + cm.lineNumber() + " of "
                        + cm.sourceId());
                return true;
            }

        });


        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAppCacheEnabled(true);
        settings.setDomStorageEnabled(true);

        webview.addJavascriptInterface(new JsHandle(webview), "Android");

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    10);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        11);
        }
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }


        if (savedInstanceState != null) {
            mCurrentPhotoPath = savedInstanceState.getString("mCurrentPhotoPath");
            server = savedInstanceState.getString("server");
            waitingPhoto = savedInstanceState.getBoolean("waitingPhoto");
            if (waitingPhoto) reloadedWhileWaitingPhoto = true;
        } else {
            if (!((LocationManager) getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "For best results enable GPS", Toast.LENGTH_LONG).show();
            }
        }

        if ((savedInstanceState == null) || ((server = savedInstanceState.getString("server")) == null)) {
            final EditText txtUrl = new EditText(this);
            txtUrl.setHint("http://snf-581842.vm.okeanos.grnet.gr:8000");
            txtUrl.setText("http://snf-581842.vm.okeanos.grnet.gr:8000");

            new AlertDialog.Builder(this)
                    .setTitle("Server:")
                    .setView(txtUrl)
                    .setPositiveButton("CONNECT", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            server = txtUrl.getText().toString();
                            webview.loadUrl(server);
                        }
                    })
                    .show();
        } else {
            webview.loadUrl(server);
        }
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }



    @Override
    public void onConnected(Bundle connectionHint) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    public void onLocationChanged(final Location location) {
        final WebView webview = (WebView) findViewById(R.id.webView);
        webview.post(new Runnable() {
            @Override
            public void run() {
                webview.evaluateJavascript("geo_success({coords: {latitude:"+location.getLatitude()
                        +",longitude:"+location.getLongitude()+",accuracy:"+location.getAccuracy()+"}})", null);
            }
        });
    }


}
