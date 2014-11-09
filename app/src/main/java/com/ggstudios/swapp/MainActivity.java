package com.ggstudios.swapp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.ggstudios.widgets.GifView;
import com.ggstudios.widgets.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.jwetherell.quick_response_code.DecoderActivityHandler;
import com.jwetherell.quick_response_code.IDecoderActivity;
import com.jwetherell.quick_response_code.QrViewListener;
import com.jwetherell.quick_response_code.camera.CameraManager;
import com.jwetherell.quick_response_code.data.Contents;
import com.jwetherell.quick_response_code.qrcode.QRCodeEncoder;
import com.ocr.OcrResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;


public class MainActivity extends Activity implements IDecoderActivity, SurfaceHolder.Callback,
        ConfirmContactDialogFragment.ConfirmContactDialogListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String PREF_ADD_HISTORY = "add_history";

    private static final int ANIMATION_DURATION = 200;
    private static final int FADE_OUT_DURATION = 500;

    private static final int MAX_HISTORY = 15;

    int smallerDimension;

    protected boolean hasSurface = false;

    protected ViewfinderView viewfinderView = null;
    protected DecoderActivityHandler handler = null;
    protected CameraManager cameraManager = null;
    protected Collection<BarcodeFormat> decodeFormats = null;
    protected String characterSet = null;

    private SurfaceView surfaceView;
    ImageView imgQr;
    TextView txtName;
    TextView txtNumber;
    TextView txtEmail;
    GifView gifView;
    View addContactContainer;
    View textContainer;
    DrawerLayout drawer;
    ListView listHistory;

    private TessBaseAPI baseApi;

    Handler callbackHandler;
    View qrContainer;

    public static final int PREVIEW_READY = 0x1234;

    private List<ContactHistoryItem> history;
    ContactHistoryAdapter adapter;

    //boolean

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        decodeFormats = new ArrayList<BarcodeFormat>();
        decodeFormats.add(BarcodeFormat.QR_CODE);

        CheckBox toggle = (CheckBox) findViewById(R.id.toggle);
        surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        imgQr = (ImageView) findViewById(R.id.qr_code);
        qrContainer = findViewById(R.id.qr_container);
        txtName = (TextView) findViewById(R.id.txt_name);
        txtNumber = (TextView) findViewById(R.id.txt_number);
        txtEmail = (TextView) findViewById(R.id.txt_email);
        gifView = (GifView) findViewById(R.id.gifView);
        addContactContainer = findViewById(R.id.add_contact_container);
        textContainer = findViewById(R.id.txt_container);
        final ImageButton btnDrawer = (ImageButton) findViewById(R.id.btn_drawer);
        ImageButton btnClose = (ImageButton) findViewById(R.id.btn_close);
        listHistory = (ListView) findViewById(R.id.history);
        Button btnEditProfile = (Button) findViewById(R.id.btn_edit_profile);

        qrContainer.setVisibility(View.INVISIBLE);
        addContactContainer.setVisibility(View.INVISIBLE);

        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        smallerDimension = width < height ? width : height;
        smallerDimension = smallerDimension * 7 / 8;

        callbackHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == PREVIEW_READY) {
                    cameraManager.releaseOcr();
                    new OcrRecognizeAsyncTask(MainActivity.this,
                            getBaseApi(), (byte[]) msg.obj, msg.arg1, msg.arg2).execute();
                    handler.reset();
                }
            }
        };

        toggle.setChecked(true);
        toggle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                return false;
            }
        });
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    qrContainer.setVisibility(View.INVISIBLE);
                    handler.rearm();
                } else {
                    genQrIfNeeded();
                    qrContainer.setVisibility(View.VISIBLE);
                    handler.disarm();
                }
            }
        });

        btnDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawer.openDrawer(Gravity.RIGHT);
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawer.closeDrawer(Gravity.RIGHT);
            }
        });

        btnEditProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String[] SELF_PROJECTION = new String[]{
                        ContactsContract.CommonDataKinds.Phone._ID,};
                Cursor c = getContentResolver().query(ContactsContract.Profile.CONTENT_URI, SELF_PROJECTION, null, null, null);
                int count = c.getCount();
                String[] columnNames = c.getColumnNames();
                c.moveToFirst();
                int position = c.getPosition();

                if (count == 1 && position == 0) {
                    long id = c.getLong(c.getColumnIndex(columnNames[0]));

                    Intent i = new Intent(Intent.ACTION_EDIT);
                    Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
                    i.setData(contactUri);
                    startActivity(i);
                }
            }
        });

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        handler = null;
        hasSurface = false;

//        btnCapture.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                handler.ocrDecode();
//            }
//        });

        setupDrawer();

        SharedPreferences prefs = getPreferences(0);
        history = new ArrayList<ContactHistoryItem>();
        if (prefs.contains(PREF_ADD_HISTORY)) {
            try {
                JSONArray arr = new JSONArray(prefs.getString(PREF_ADD_HISTORY, ""));

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = (JSONObject) arr.get(i);
                    history.add(new ContactHistoryItem(o));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
        }

        adapter = new ContactHistoryAdapter(this, history);
        listHistory.setAdapter(adapter);
    }

    private void setupDrawer() {
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer,
                R.drawable.btn_bg, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                //getActionBar().setTitle(mTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                //getActionBar().setTitle(mDrawerTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        drawer.setDrawerListener(toggle);
    }

    private void genQrIfNeeded() {
        if (imgQr.getDrawable() == null) {
            TelephonyManager tMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String phoneNumber = tMgr.getLine1Number();

            final String[] SELF_PROJECTION = new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,};
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
    }

    boolean animating = false;
    private void parseAndAddContact(String rawString) {
        if (animating) return;

        animating = true;
        Log.d(TAG, "rawString " + rawString);
        String[] toks = rawString.split("\\|");
        final String number = toks[0];
        final String name = toks[1];

        addToHistory(name);

        Log.d(TAG, String.format("number: %s; name: %s", number, name));

        //ConfirmContactDialogFragment frag = ConfirmContactDialogFragment.newInstance(name, number);
        //frag.show(getFragmentManager(), "dialog");

        //textContainer.setVisibility(View.INVISIBLE);
        txtName.setText(name);
        txtNumber.setText(number);

        gifView.setVisibility(View.INVISIBLE);

        Animation ani = new AlphaAnimation(0f, 1f);
        ani.setDuration(ANIMATION_DURATION);
        ani.setFillAfter(true);

        addContactContainer.setVisibility(View.VISIBLE);
        addContactContainer.startAnimation(ani);

        ani.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
//                Animation ani = new AlphaAnimation(0f, 1f);
//                ani.setDuration(ANIMATION_DURATION);
//                ani.setFillAfter(true);
//                textContainer.setVisibility(View.VISIBLE);
//                textContainer.startAnimation(ani);

                gifView.setVisibility(View.VISIBLE);
                //gifView.setGIFResource(R.drawable.transition);

                textContainer.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Animation ani = new AlphaAnimation(1f, 0f);
                        ani.setDuration(FADE_OUT_DURATION);
                        ani.setFillAfter(true);
                        ani.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {

                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                addContactContainer.setVisibility(View.INVISIBLE);
                                onOkClicked(name, number);
                                animating = false;
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {

                            }
                        });
                        addContactContainer.startAnimation(ani);
                    }
                }, 1000);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
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
        if (cameraManager == null) cameraManager = new CameraManager(getApplication(), callbackHandler);

        if (viewfinderView == null) {
            viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
            viewfinderView.setCameraManager(cameraManager);

            viewfinderView.post(new Runnable() {
                @Override
                public void run() {
                    int b = (int) Utils.convertDpToPixel(110, MainActivity.this);
                    int m = (int) Utils.convertDpToPixel(20, MainActivity.this);
                    Rect rect = new Rect();
                    rect.left = m;
                    rect.top = m + b;
                    rect.right = viewfinderView.getWidth() - m;
                    rect.bottom = viewfinderView.getHeight() - b - m;
                    viewfinderView.setFrameRect(rect);
                }
            });
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

        JSONArray arr = new JSONArray();
        for (ContactHistoryItem item : history) {
            try {
                arr.put(item.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        SharedPreferences.Editor editor = getPreferences(0).edit();
        editor.putString(PREF_ADD_HISTORY, arr.toString());
        editor.apply();
    }

    private void addToHistory(String contactName) {
        ContactHistoryItem i = new ContactHistoryItem();
        i.name = contactName;
        Calendar c = Calendar.getInstance();
        i.timeAdded = c.getTime();

        history.add(i);

        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public TessBaseAPI getBaseApi() {
        if (baseApi == null) {
            baseApi = new TessBaseAPI();

            unpackOsd();
            try {
                baseApi.init(getCacheDir().getCanonicalPath() + "/tesseract-ocr-3.01.osd/tesseract-ocr" + File.separator, "eng", TessBaseAPI.OEM_TESSERACT_ONLY);//;_CUBE_COMBINED);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return baseApi;
    }

    private void unpackOsd() {
        try {
            File f = new File(getCacheDir().getCanonicalPath() + "/tesseract-ocr-3.01.osd/tesseract-ocr/tessdata");
            f.mkdirs();
            f = new File(getCacheDir().getCanonicalPath() + "/tesseract-ocr-3.01.osd/tesseract-ocr/tessdata/eng.traineddata");
            if (!f.exists()) {

                InputStream is = getAssets().open("eng.traineddata");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();


                FileOutputStream fos = new FileOutputStream(f);
                fos.write(buffer);
                fos.close();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
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
    public boolean handleOcrDecode(OcrResult ocrResult) {
        Log.d(TAG, "result: " + ocrResult.getText());
        return false;
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
        //Utils.addContact(this, name, number);
        handler.reset();
    }

    @Override
    public void onCancelClicked() {
        handler.reset();
    }

    private static class ViewHolder {
        TextView txtName;
        TextView txtDate;
    }

    private static class ContactHistoryItem {
        String name;
        Date timeAdded;

        public ContactHistoryItem() {}

        public ContactHistoryItem(JSONObject o) throws JSONException {
            name = o.getString("name");
            timeAdded = new Date(o.getLong("timeAdded"));
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("timeAdded", timeAdded.getTime());
            return o;
        }
    }

    private static final class ContactHistoryAdapter extends BaseAdapter {

        private LayoutInflater inflater;
        private List<ContactHistoryItem> history;
        private DateFormat df;

        private ContactHistoryAdapter(Context context, List<ContactHistoryItem> history) {
            this.history = history;
            inflater = LayoutInflater.from(context);
            df = new SimpleDateFormat("MMM. dd, yyyy, HH:mmaa");
        }

        @Override
        public int getCount() {
            return history.size();
        }

        @Override
        public ContactHistoryItem getItem(int i) {
            return history.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ContactHistoryItem item = getItem(i);
            ViewHolder holder;
            if (view == null) {
                holder = new ViewHolder();
                view = inflater.inflate(R.layout.item_contact, viewGroup, false);
                holder.txtName = (TextView) view.findViewById(R.id.txt_name);
                holder.txtDate = (TextView) view.findViewById(R.id.txt_date);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            holder.txtName.setText(item.name);
            holder.txtDate.setText(df.format(item.timeAdded));

            return view;
        }
    }
}
