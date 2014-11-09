/*
 * Copyright (C) 2010 ZXing authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jwetherell.quick_response_code.camera;

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ggstudios.swapp.MainActivity;
import com.ggstudios.swapp.OcrRecognizeAsyncTask;
import com.jwetherell.quick_response_code.IDecoderActivity;

final class PreviewCallback implements Camera.PreviewCallback {

    private static final String TAG = PreviewCallback.class.getSimpleName();

    private final CameraConfigurationManager configManager;
    private Handler previewHandler;
    private int previewMessage;

    private Handler previewReadyHandler;

    private boolean isOcr;

    PreviewCallback(CameraConfigurationManager configManager) {
        this.configManager = configManager;
    }

    PreviewCallback(CameraConfigurationManager configManager, boolean isOcr, Handler previewReadyHandler) {
        this.configManager = configManager;
        this.isOcr = isOcr;
        this.previewReadyHandler = previewReadyHandler;
    }

    void setHandler(Handler previewHandler, int previewMessage) {
        this.previewHandler = previewHandler;
        this.previewMessage = previewMessage;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Point cameraResolution = configManager.getCameraResolution();
        if (!isOcr) {
            Handler thePreviewHandler = previewHandler;
            if (thePreviewHandler != null) {
                Message message = thePreviewHandler.obtainMessage(previewMessage, cameraResolution.x, cameraResolution.y, data);
                message.sendToTarget();
                previewHandler = null;
            } else {
                Log.d(TAG, "Got preview callback, but no handler for it");
            }
        } else {
            Message message = previewReadyHandler.obtainMessage(MainActivity.PREVIEW_READY, cameraResolution.x, cameraResolution.y, data);
            message.sendToTarget();
        }
    }

}
