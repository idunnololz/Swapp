package com.ggstudios.swapp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import com.ggstudios.widgets.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.jwetherell.quick_response_code.DecoderActivityHandler;
import com.jwetherell.quick_response_code.IDecoderActivity;
import com.jwetherell.quick_response_code.QrViewListener;
import com.jwetherell.quick_response_code.camera.CameraManager;
import com.jwetherell.quick_response_code.data.Contents;
import com.jwetherell.quick_response_code.qrcode.QRCodeEncoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;


public class MainActivity extends Activity implements IDecoderActivity, SurfaceHolder.Callback, ConfirmContactDialogFragment.ConfirmContactDialogListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int ACTIVITY_QR_SCANNER = 1;

    int smallerDimension;

    protected boolean hasSurface = false;

    protected ViewfinderView viewfinderView = null;
    protected DecoderActivityHandler handler = null;
    protected CameraManager cameraManager = null;
    protected Collection<BarcodeFormat> decodeFormats = null;
    protected String characterSet = null;

    private SurfaceView surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        decodeFormats = new ArrayList<BarcodeFormat>();
        decodeFormats.add(BarcodeFormat.QR_CODE);

        Button btnGen = (Button) findViewById(R.id.btn_gen_qr);
        Button btnScan = (Button) findViewById(R.id.btn_scan_qr);
        Button btnCapture = (Button) findViewById(R.id.btn_capture);
        surfaceView = (SurfaceView) findViewById(R.id.preview_view);

        final ImageView imgQr = (ImageView) findViewById(R.id.qr_code);

        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        smallerDimension = width < height ? width : height;
        smallerDimension = smallerDimension * 7 / 8;

        btnGen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TelephonyManager tMgr = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                String phoneNumber = tMgr.getLine1Number();

                final String[] SELF_PROJECTION = new String[] {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, };
                Cursor c = getContentResolver().query(ContactsContract.Profile.CONTENT_URI, SELF_PROJECTION, null, null, null);
                int count = c.getCount();
                String[] columnNames = c.getColumnNames();
                c.moveToFirst();
                int position = c.getPosition();

                if (count == 1 && position == 0) {
                    String displayName = c.getString(c.getColumnIndex(columnNames[0]));

                    String compact = phoneNumber + "|" + displayName;

                    Log.d(TAG, "compact: " + compact);

                    QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(compact,
                            null,
                            Contents.Type.TEXT,
                            BarcodeFormat.QR_CODE.toString(),
                            smallerDimension);
                    try {
                        Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
                        imgQr.setImageBitmap(bitmap);
                    } catch (WriterException e) {
                        e.printStackTrace();
                    }
                }
                c.close();
            }
        });

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: switch views
            }
        });

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        handler = null;
        hasSurface = false;

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
    }

    private void parseAndAddContact(String rawString) {
        Log.d(TAG, "rawString " + rawString);
        String[] toks = rawString.split("\\|");
        String number = toks[0];
        String name = toks[1];

        Log.d(TAG, String.format("number: %s; name: %s", number, name));

        ConfirmContactDialogFragment frag = ConfirmContactDialogFragment.newInstance(name, number);
        frag.show(getFragmentManager(), "dialog");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        // CameraManager must be initialized here, not in onCreate().
        if (cameraManager == null) cameraManager = new CameraManager(getApplication());

        if (viewfinderView == null) {
            viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
            viewfinderView.setCameraManager(cameraManager);
        }

        showScanner();

        final SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.

            surfaceView.post(new Runnable() {
                @Override
                public void run() {
                    initCamera(surfaceHolder, surfaceView.getWidth(), surfaceView.getHeight());
                }
            });
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraManager.closeDriver();
    }

    @Override
    public QrViewListener getViewfinder() {
        return viewfinderView;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

    @Override
    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public void handleDecode(Result rawResult, Bitmap barcode) {
        parseAndAddContact(rawResult.getText());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null)
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder, surfaceView.getWidth(), surfaceView.getHeight());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        hasSurface = false;
    }

    protected void showScanner() {
        viewfinderView.setVisibility(View.VISIBLE);
    }

    protected void initCamera(SurfaceHolder surfaceHolder, int w, int h) {
        try {

            Point size = cameraManager.openDriver(surfaceHolder, w, h);

            surfaceView.getLayoutParams().width = size.y;
            surfaceView.getLayoutParams().height = size.x;
            surfaceView.requestLayout();

            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) handler = new DecoderActivityHandler(this, decodeFormats, characterSet, cameraManager);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
        }
    }

    @Override
    public void onOkClicked(String name, String number) {
        Utils.addContact(this, name, number);
        handler.reset();
    }

    @Override
    public void onCancelClicked() {
        handler.reset();
    }
}
