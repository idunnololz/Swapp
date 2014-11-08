package com.jwetherell.quick_response_code;

import com.google.zxing.ResultPoint;

public interface QrViewListener {
    public void addPossibleResultPoint(ResultPoint point);
    public void drawViewfinder();
}
