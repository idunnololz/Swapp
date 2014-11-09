package com.jwetherell.quick_response_code;

import android.graphics.Bitmap;
import android.os.Handler;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.jwetherell.quick_response_code.camera.CameraManager;
import com.google.zxing.Result;
import com.ocr.OcrResult;

public interface IDecoderActivity {

    TessBaseAPI getBaseApi();

    public QrViewListener getViewfinder();

    public Handler getHandler();

    public CameraManager getCameraManager();

    public void handleDecode(Result rawResult, Bitmap barcode);

    boolean handleOcrDecode(OcrResult ocrResult);
}
