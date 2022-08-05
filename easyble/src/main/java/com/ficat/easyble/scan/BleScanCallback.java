package com.ficat.easyble.scan;


import com.ficat.easyble.BleDevice;

import java.io.IOException;

public interface BleScanCallback {
    void onLeScan(BleDevice device, int rssi, byte[] scanRecord);

    void onStart(boolean startScanSuccess, String info);

    void onFinish() throws IOException;
}
