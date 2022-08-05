package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

import org.json.JSONException;

import java.io.IOException;

public interface BleNotifyCallback extends BleCallback {
    void onCharacteristicChanged(byte[] data, BleDevice device) throws IOException, JSONException;

    void onNotifySuccess(String notifySuccessUuid, BleDevice device);
}
