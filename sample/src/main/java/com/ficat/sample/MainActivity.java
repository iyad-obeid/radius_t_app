package com.ficat.sample;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.location.LocationManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.Logger;
import com.ficat.easyble.gatt.bean.CharacteristicInfo;
import com.ficat.easyble.gatt.bean.ServiceInfo;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easypermissions.EasyPermissions;
import com.ficat.easypermissions.RequestExecutor;
import com.ficat.easypermissions.bean.Permission;
import com.ficat.sample.adapter.ScanDeviceAdapter;
import com.ficat.sample.adapter.CommonRecyclerViewAdapter;
import com.ficat.sample.utils.ByteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 *this class is essentially the main menu of the app. "activities" are essentially pages
 *
 * any method, class, etc. that I wrote myself will begin with bh to denote its custom nature
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String TAG = "EasyBle";
    private RecyclerView rv;    //recyclerView are essentially lists that can be modified/updated
    private BleManager manager;
    private List<BleDevice> deviceList = new ArrayList<>();
    private ScanDeviceAdapter adapter;  //adapter objects modify recylerview lists


    //this white space denotes the OG insantiations above and mine below
    private Button btnUuid;

    //onCreate initializes GUI elements and functionalities on the particular page (activity)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); //starts main page
        setContentView(R.layout.activity_main); //initializes GUI layout of main page
        initView(); //initializes scan button, recyclerView (connects graphic elements to coded methods)
        initBleManager();   //inits logger, scanner, etc
        showDevicesByRv();  //Rv is short for recyclerView. now that layout and elemends are intialized, this uses the adapter to produce the gui elements
    }

    //connects GUI elements to their functionalities
    private void initView() {
        //instantiate the scan button in backend = connect it to GUI element in XML
        Button btnScan = findViewById(R.id.btn_scan);
        btnUuid = findViewById(R.id.btn_uuid);
        btnUuid.setVisibility(View.INVISIBLE);
        //connect already instantiated recyclerView to = XML element
        rv = findViewById(R.id.rv);

        //enable the button to 'listen' for a click(tap). argument is "context" of the activity (in this case main activity)
        btnScan.setOnClickListener(this);
    }

    //methods for scanning and connecting with timeout functionality
    private void initBleManager() {
        //check if this android device supports ble
        if (!BleManager.supportBle(this)) {
            return;
        }
        //open bluetooth without a request dialog
        BleManager.toggleBluetooth(true);

        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(8000)
                .scanDeviceName(null);

        BleManager.ConnectOptions connectOptions = BleManager.ConnectOptions
                .newInstance()
                .connectTimeout(12000);

        manager = BleManager
                .getInstance()
                .setScanOptions(scanOptions)
                .setConnectionOptions(connectOptions)
                .setLog(true, "EasyBle")
                .init(this.getApplication());
    }

    //handles gui elements when scanning
    private void showDevicesByRv() {
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.top = 3;
            }
        });
        //device scan result GUI elements
        SparseArray<int[]> res = new SparseArray<>();
        res.put(R.layout.item_rv_scan_devices, new int[]{R.id.tv_name, R.id.tv_address, R.id.tv_connection_state});
        adapter = new ScanDeviceAdapter(this, deviceList, res);

        //clicking each scan result produces a new page - lets modify this to simply connect to the device
        adapter.setOnItemClickListener(new CommonRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View itemView, int position) {
                manager.stopScan();
                BleDevice device = deviceList.get(position);

                /*VVVVV
                   *just a visual aid to find the custom function call
                   *
                   *
                   *
                 */
                bhConnect(device);
                bhSendInitPayload(device);
                /*^^^^^^
                 *just a visual aid to find the custom function call
                 *
                 *
                 *
                 */

                //intent changes the activity (basically the page). this is the original code for posterity
//                Intent intent = new Intent(MainActivity.this, OperateActivity.class);
//                intent.putExtra(OperateActivity.KEY_DEVICE_INFO, device);
//                startActivity(intent);
            }
        });
        rv.setAdapter(adapter);
    }

    //checks permissions, ensures user allows location services for BT
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_scan:
                if (!BleManager.isBluetoothOn()) {
                    BleManager.toggleBluetooth(true);
                }
                //for most devices whose version is over Android6,scanning may need GPS permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isGpsOn()) {
                    Toast.makeText(this, getResources().getString(R.string.tips_turn_on_gps), Toast.LENGTH_LONG).show();
                    return;
                }
                EasyPermissions
                        .with(this)
                        .request(Manifest.permission.ACCESS_FINE_LOCATION)
                        .autoRetryWhenUserRefuse(true, null)
                        .result(new RequestExecutor.ResultReceiver() {
                            //if permissions are enabled, scan for bluetooth - this is called through the callback in the next method
                            @Override
                            public void onPermissionsRequestResult(boolean grantAll, List<Permission> results) {
                                if (grantAll) {
                                    if (!manager.isScanning()) {
                                        startScan();
                                    }
                                } else {    //if user declines location permissions, this message appears.
                                    // "toast" are the bubble notifications that appear at the bottom of the screen
                                    Toast.makeText(MainActivity.this,
                                            getResources().getString(R.string.tips_go_setting_to_grant_location),
                                            Toast.LENGTH_LONG).show();
                                    EasyPermissions.goToSettingsActivity(MainActivity.this);
                                }
                            }
                        });
                break;
            default:
                break;
        }
    }

    //the callback checks for permissions and contains the actual scanning methods
    private void startScan() {
        manager.startScan(new BleScanCallback() {
            //if the devices is already added, ignore it
            @Override
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                for (BleDevice d : deviceList) {
                    if (device.address.equals(d.address)) {
                        return;
                    }
                }
                //otherwise add it to the array
                if (device.getDevice().getBondState() == 12) {           //IMPORTANT! this is what i modified to ensure only bonded devices are handled further
                    deviceList.add(device); //add device to array
                    adapter.notifyDataSetChanged(); //updates adapter/recycler which produces GUI updates
                }
            }

            //if a scan already occurred, clear the list
            @Override
            public void onStart(boolean startScanSuccess, String info) {
                Log.e(TAG, "start scan = " + startScanSuccess + "   info: " + info);
                if (startScanSuccess) {
                    //deviceList.clear();
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFinish() {
                Log.e(TAG, "scan finish");
            }
        });
    }

    //checks for permission
    private boolean isGpsOn() {
        LocationManager locationManager
                = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    //i guess this is garbage handling?
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (manager != null) {
            //you must call BleManager#destroy() to release resources
            manager.destroy();
        }
    }


    /*
     *this white space separates the OG code above from my code below
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */

    private void bhConnect(BleDevice device) {
        BleManager.getInstance().connect(device.address, bhConnectCallback);
    }


    //this handles a lot of logging and backend handling.
    //methods are commented out because they were pulled from the unused activity
    private BleConnectCallback bhConnectCallback = new BleConnectCallback() {
        @Override
        public void onFailure(int failCode, String info, BleDevice device) {
            Logger.e("connect fail:" + info);
            Toast.makeText(MainActivity.this,
                    getResources().getString(failCode == BleConnectCallback.FAIL_CONNECT_TIMEOUT ?
                            R.string.tips_connect_timeout : R.string.tips_connect_fail), Toast.LENGTH_LONG).show();

            //deprecated from og. likely will need to handle reset functionality
//            reset();
//            updateConnectionStateUi(false);
        }

        @Override
        public void onStart(boolean startConnectSuccess, String info, BleDevice device) {

            Logger.e("start connecting:" + startConnectSuccess + "    info=" + info);

//            MainActivity.this.device = device;
//            updateConnectionStateUi(false);
//            if (!startConnectSuccess) {
//                Toast.makeText(OperateActivity.this, "start connecting fail:" + info, Toast.LENGTH_LONG).show();
//            }
        }

        @Override
        public void onConnected(BleDevice device) {
//            updateConnectionStateUi(true);
//            addDeviceInfoDataAndUpdate();
        }


        @Override
        public void onDisconnected(String info, int status, BleDevice device) {
            Logger.e("disconnected!");
        }


    };

    public void bhSendInitPayload(BleDevice device){
        String writeUuid = null;
        String characteristicUuid = null;
        //start sending data command
        String str = "C502160117C9";

        //avoid crashes by insuring device is actually connected before acquring uuid
        while(BleManager.getInstance().isConnected(device.address) != true){
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //get the uuids
        if(BleManager.getInstance().isConnected(device.address) == true) {
            //devices have 'service' (parent) and 'characteristic' (child) uuids. so its map time
            List<ServiceInfo> groupList = new ArrayList<>();
            List<List<CharacteristicInfo>> childList = new ArrayList<>();
            Map<ServiceInfo, List<CharacteristicInfo>> deviceInfo = BleManager.getInstance().getDeviceServices(device.address);

            //put the uuids in their own lists for iteration
            for (Map.Entry<ServiceInfo, List<CharacteristicInfo>> e : deviceInfo.entrySet()) {
                groupList.add(e.getKey());
                childList.add(e.getValue());
            }

            //iterate thru the parent, all the uuid's look to be the same so the one we want starts with '5'
            int i;
            for (i = 0; i < groupList.size(); i++) {
                if (groupList.get(i).uuid.charAt(0) == '5') {
                    writeUuid = groupList.get(i).uuid;
                    break;
                }
            }

            //for the characteristic uuid, we want writable (vs readable or notify)
            for (int j = 0; j<childList.get(i).size(); j++) {
                if (childList.get(i).get(j).writable) {
                        characteristicUuid = childList.get(i).get(j).uuid;
                }
            }

        }

        //now that we have the uuids, we can send that hardcoded start command
        BleManager.getInstance().write(device, writeUuid, characteristicUuid, ByteUtils.hexStr2Bytes(str), writeCallback);
    }

    //callback just for logging
    private BleWriteCallback writeCallback = new BleWriteCallback() {
        @Override
        public void onWriteSuccess(byte[] data, BleDevice device) {
            Logger.e("write success:" + ByteUtils.bytes2HexStr(data));
           // tvWriteResult.setText(ByteUtils.bytes2HexStr(data));
        }

        @Override
        public void onFailure(int failCode, String info, BleDevice device) {
            Logger.e("write fail:" + info);
            //tvWriteResult.setText("write fail:" + info);
        }
    };



}//end
