package com.ficat.sample;

import android.Manifest;
import android.content.Context;
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
import com.ficat.sample.NetworkOperation;
import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.Logger;
import com.ficat.easyble.gatt.bean.CharacteristicInfo;
import com.ficat.easyble.gatt.bean.ServiceInfo;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easypermissions.EasyPermissions;
import com.ficat.easypermissions.RequestExecutor;
import com.ficat.easypermissions.bean.Permission;
import com.ficat.sample.adapter.ScanDeviceAdapter;
import com.ficat.sample.adapter.CommonRecyclerViewAdapter;
import com.ficat.sample.utils.ByteUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

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

    public List<String[]> outputData = new ArrayList<String[]>();
    public boolean started;

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
        //this white space denotes the OG insantiations above and mine below
        Button btnUuid = findViewById(R.id.btn_uuid);
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
                if(started != true) {
                    outputData.clear();
                    outputData.add(new String[] {"device id ","time", "temp"});
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
                                            started = true;
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
                }else{
                    writeToFile();
                    started=false;
                    for(BleDevice elements : deviceList){
                        BleManager.getInstance().disconnect(elements);
                    }
                }
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
               // if(deviceList.size() !=2){
                    //TODO handle scan errors/issues. ask user to retry, ensure green lights are blinking, or continue
                //}
                //else
                    for(int i = 0; i < deviceList.size(); i++){
                        bhConnect(deviceList.get(i));
                       if(deviceList.get(i).connected){
                        bhSendInitPayload(deviceList.get(i));
                        }else bhConnect(deviceList.get(i));
                        bhSendInitPayload(deviceList.get(i));
                    }
                Log.e(TAG, "scan finish");
                    //serverConnect();

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

            for(BleDevice elements : BleManager.getInstance().getConnectedDevices()){
                BleManager.getInstance().disconnect(elements);
            }
            started = false;
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
        String notifyUuid = null;
        int i = 0;
        //start sending data command
        String cmd1 = "C502160117C9";
//        String cmd2 = "C5091743616C69336E74650AC9";
//        String cmd3 = "C5011010C9";
//        String cmd4 = "C505732A453F92B3C9";

        //avoid crashes by insuring device is actually connected before acquring uuid
        while( (BleManager.getInstance().isConnected(device.address) != true) && (i<20) ){
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i++;
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

            for (i = 0; i < groupList.size(); i++) {
                if (groupList.get(i).uuid.charAt(0) == '5') {
                    writeUuid = groupList.get(i).uuid;
                    break;
                }
            }

            //for the characteristic uuid, we want writable (vs readable or notify)
            for (int j = 0; j < childList.get(i).size(); j++) {
                if (childList.get(i).get(j).writable) {
                    characteristicUuid = childList.get(i).get(j).uuid;
                } else if (childList.get(i).get(j).notify) {
                    notifyUuid = childList.get(i).get(j).uuid;
                }
            }


            //now that we have the uuids, we can send that hardcoded start command
            BleManager.getInstance().write(device, writeUuid, characteristicUuid, ByteUtils.hexStr2Bytes(cmd1), writeCallback);
            BleManager.getInstance().notify(device, writeUuid, notifyUuid, notifyCallback);
        }
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
            for(BleDevice elements : BleManager.getInstance().getConnectedDevices()){
                BleManager.getInstance().disconnect(elements);
            }
            started = false;
            //tvWriteResult.setText("write fail:" + info);
        }
    };

    private BleNotifyCallback notifyCallback = new BleNotifyCallback() {

        @Override
        public void onCharacteristicChanged(byte[] data, BleDevice device) throws JSONException, IOException {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            String currentDateandTime = sdf.format(new Date()) + "Z";
            int temp;                                    //init temp data - this is written directly from bytestream wrapper
            double dTemp;
            byte[] timeBytes = new byte[1];                 //array to hold parsed time bytes
            byte[] tempBytes = new byte[2];                 //""for temp - part of my problem is that I'm a little confused as to how long the packet is
                                                            //looking at this with fresh eyes I see I manufactured bad conversion with the array size and conflicts below

            //"data" variable is bytestream direct from device, same as in wireshark. mentions of "stream" reference this
           // if(data.length == 12) {                         //avoid array size collisions, only collect data after initial..initialization...has initiated
                for(int i = 3; i < data.length; i++){       //start parse header out
                    if(i==3){                                //parse time bytes
                        timeBytes[0] = data[i];         //slap em in the array
                    }
                    if( (i>3) && (i<6)) {                 //""for temp - obvious issue i mentioned earlier, array size is 6, only collects 2
                        tempBytes[(i-4)] = data[i];
                    }
                }
                ByteBuffer buff = ByteBuffer.wrap(tempBytes) ;  //bytestream wrapper for temp. take the array, wrap it to convert it to primitive types
                temp = buff.getShort();
                temp = temp & 0xffff;
                dTemp = Math.round(temp / 100);
                dTemp /=10;
                //attempted to get int from stream and AND to handle 2's compliment. I really need to revisit the subject
            //}
            String dataStr = ByteUtils.bytes2HexStr(data);      //full hex bytestream for logcat output (same as in wireshark)
            String timeStr = ByteUtils.bytes2HexStr(timeBytes); //parsed time hex for logcat output
            String tempStr = ByteUtils.bytes2HexStr(tempBytes); //""temp

          // dTemp = (dTemp / 1e6) * 1.8 + 32;                    //trying to convert to F for my own debugging sake
            String tempCelsStr = String.valueOf(dTemp);         //convert to string for logcat

            //logcat output:                  -pure hex stream- "from" -mac address-  "time:" -time hex (streamed)- "or" -system time- "temp:" -temp hex (streamed)- "fahr:" -my conversion attempt, warts and all-
            Logger.e("onCharacteristicChanged:" + dataStr + " from: "+ device.address + "  time:" + timeStr + " or: " + currentDateandTime + "  temp:" + tempStr + " C: " + tempCelsStr );

            //end to do
            outputData.add( new String[] {device.address, currentDateandTime, tempCelsStr});
            JSONObject jData = jMake(currentDateandTime,dTemp);
            //Logger.e(jData.toString());
            new NetworkOperation().execute(jData.toString());
            //uploadData(jData);
            //updateNotificationInfo(s);          -this is deprecated since i pulled the meat of the method from operateActivity. here for reference
            //TODO look for disconnected devices. check thru list for devices, if not connected, attempt reconnect
        }

        @Override
        public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {
            Logger.e("notify success uuid:" + notifySuccessUuid);
//            tvInfoNotification.setVisibility(View.VISIBLE);
//            if (!notifySuccessUuids.contains(notifySuccessUuid)) {
//                notifySuccessUuids.add(notifySuccessUuid);
//            }
//            updateNotificationInfo("");
        }

        @Override
        public void onFailure(int failCode, String info, BleDevice device) {
            Logger.e("notify fail:" + info);
            //Toast.makeText(OperateActivity.this, "notify fail:" + info, Toast.LENGTH_LONG).show();
            for(BleDevice elements : BleManager.getInstance().getConnectedDevices()){
                BleManager.getInstance().disconnect(elements);

            }
            started = false;
        }
    };

    private void writeToFile() {
        try {

            File path = getExternalFilesDir(null);
            Date date = new Date() ;
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd") ;
            File file = new File(path,dateFormat.format(date) + ".csv") ;
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            out.write("Writing to file");
            out.close();

            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < outputData.size(); i++) {
                sb.append(Arrays.toString(outputData.get(i)));
                sb.append("\n");
            }
            bw.write(sb.toString());
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //-----------------------------------http stuff
//    private void uploadData(String data) throws IOException {
//        String https_url = "https://register.bpchildresearch.org/wstg/tempsensor/temp ";
//        String token = "HFKjzEZjHnea";
//        byte[] input = data.getBytes("utf-8");
//        URL url;
//        try {
//
//
//
//
//            url = new URL(https_url);
//            HttpURLConnection con = (HttpURLConnection)url.openConnection();
//            //
//            con.setRequestProperty("Authorization", "Bearer "+ token);
//            con.setRequestMethod("POST");
//            con.setRequestProperty("Content-Type", "application/json");
//            con.setRequestProperty("Accept", "application/json");
//            con.setDoOutput(true);
//
//            con.connect();
//           //OutputStream os = con.getOutputStream());
//
//            //os.write(input, 0, input.length);
//
//
//
//            Logger.e("attempted jawn");
//        } catch (MalformedURLException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }

    private JSONObject jMake(String date, double time){
        JSONObject obj = new JSONObject();
        try {
            obj.put("date", date);
        obj.put("time", time);


        } catch (JSONException e) {
            e.printStackTrace();
        }

        return obj;
    }

}//end
