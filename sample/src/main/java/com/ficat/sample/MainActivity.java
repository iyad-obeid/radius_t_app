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
import android.widget.TextView;
import android.widget.Toast;

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
import com.ficat.sample.utils.ByteUtils;
import com.ficat.sample.utils.Properties;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 *this class is essentially the main menu of the app. "activities" are essentially pages
 *
 *
 */


// we don't need the specific mac address for each sensor
// instead the ble code looks for the very specific uuids which only occur for this
// particular sensor

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    /* constants */
    private String UserToken = Properties.OBEID_TOKEN;
    private final static String radius_start_xmit = "C502160117C9";
    private final static int MAX_NUMBER_SENSORS = 2;

    private final static String TAG = "EasyBle";
    private RecyclerView rv;   //recyclerView are essentially lists that can be modified/updated
    private BleManager manager;
    private List<BleDevice> deviceList = new ArrayList<>();
    private List<String> deviceAddressList = new ArrayList<>();
    private ArrayList<BleDevice> connectedDevices = new ArrayList<BleDevice>();
    private ScanDeviceAdapter adapter;  //adapter objects modify recylerview lists
    private DataAdapter adptr2;

    public List<String[]> outputData = new ArrayList<String[]>();
    public boolean started;
    public boolean intendedDisconnect;

    //onCreate initializes GUI elements and functionalities on the particular page (activity)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); //starts main page
        setContentView(R.layout.activity_main); //initializes GUI layout of main page
        initView(); //initializes scan button, recyclerView (connects graphic elements to coded methods)
        initBleManager();   //inits logger, scanner, etc
        showDevicesByRv();
        deviceAddressList.clear();//Rv is short for recyclerView. now that layout and elemends are intialized, this uses the adapter to produce the gui elements
        deviceList.clear();
        initDataRv();
//        UserToken = readFromFile();
//        UserToken = OBEID_TOKEN;
    }

    //connects GUI elements to their functionalities
    private void initView() {
        //instantiate the scan button in backend = connect it to GUI element in XML
        Button btnScan = findViewById(R.id.btn_scan);
        //this white space denotes the OG insantiations above and mine below
        Button btnRetry = findViewById(R.id.btn_retry);
        btnRetry.setVisibility(View.INVISIBLE);
        Button btnContinue = findViewById(R.id.btn_continue);
        btnContinue.setVisibility(View.INVISIBLE);
        TextView tvFailure = findViewById(R.id.tv_fail);
        tvFailure.setVisibility(View.INVISIBLE);
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
        rv.setAdapter(adapter);
    }

    //checks permissions, ensures user allows location services for BT
    @Override
    public void onClick(View v) {
        Button btnScan = findViewById(R.id.btn_scan);
        switch (v.getId()) {
            case R.id.btn_scan:
                if(started != true) {
                    deviceList.clear();
                    deviceAddressList.clear();
                    intendedDisconnect = true;
                    btnScan.setText("connecting...");
                    findViewById(R.id.btn_scan).setClickable(false);
                    outputData.clear();
                    outputData.add(new String[] {"device id ","time", "temp"});
                    adptr2.notifyDataSetChanged();
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
                    endSesh();
                    break;
                }
            case R.id.btn_retry:
                startScan();
                findViewById(R.id.btn_retry).setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_continue).setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_scan).setClickable(false);
                findViewById(R.id.btn_retry).setClickable(false);
                findViewById(R.id.btn_continue).setClickable(false);
                findViewById(R.id.tv_fail).setVisibility(View.INVISIBLE);
                TextView tvFail = findViewById(R.id.tv_fail);
                tvFail.setText("Connection failed!");
                break;
            case R.id.btn_continue:
                findViewById(R.id.btn_retry).setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_continue).setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_retry).setClickable(false);
                findViewById(R.id.btn_continue).setClickable(false);
                findViewById(R.id.tv_fail).setVisibility(View.INVISIBLE);
                TextView tvfail = findViewById(R.id.tv_fail);
                tvfail.setText("Connection failed!");
                btnScan.setText("Tap to End Session");
                findViewById(R.id.btn_scan).setClickable(true);
                started = true;

                for(BleDevice d : deviceList){
                    if(d.connected) {
                        //Logger.e("can check");
                        bhSendInitPayload(d);
                    }
                }
                break;
            default:
                break;
        }
    }

    //the callback checks for permissions and contains the actual scanning methods
    public void startScan() {
        findViewById(R.id.btn_retry).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_continue).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_scan).setClickable(false);
        findViewById(R.id.btn_retry).setClickable(false);
        manager.startScan(new BleScanCallback() {
            //if the devices is already added, ignore it
            @Override
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                if (connectedDevices.size() == MAX_NUMBER_SENSORS){      // limits us to the first two detected Radius-T sensors
                    onFinish();                         // - can be changed
                }
                for (BleDevice d : deviceList) {
                    if (device.address.equals(d.address)) {
                        return;
                    }
                }
                //otherwise add it to the array
                if (device.getDevice().getBondState() == 12) {      // 12 is magic "bonded" code
                    deviceAddressList.add(device.address);
                    deviceList.add(device);//IMPORTANT! this is what i modified to ensure only bonded devices are handled further
                    BleManager.getInstance().connect(device, bhConnectCallback);
                    //add device to array
                    adapter.notifyDataSetChanged(); //updates adapter/recycler which produces GUI updates

                }
            }

            //if a scan already occurred, clear the list
            @Override
            public void onStart(boolean startScanSuccess, String info) {
                Log.e(TAG, "start scan = " + startScanSuccess + "   info: " + info);
                if (startScanSuccess) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFinish() {
                manager.stopScan();
                if(connectedDevices.size() !=MAX_NUMBER_SENSORS){
                    TextView tvFail = findViewById(R.id.tv_fail);
                    Button btnRetry = findViewById(R.id.btn_retry);
                    Button btnContinue = findViewById(R.id.btn_continue);

                    tvFail.setVisibility(View.VISIBLE);
                    btnContinue.setVisibility(View.VISIBLE);
                    btnRetry.setVisibility(View.VISIBLE);

                    btnContinue.setOnClickListener(MainActivity.this);
                    btnRetry.setOnClickListener(MainActivity.this);
                    if(connectedDevices.size() > 0){
                        tvFail.setText("Only one device found:");
                    }
                }
                else {
                    for (int i = 0; i < deviceList.size(); i++) {
                        bhSendInitPayload(deviceList.get(i));
                    }
                }
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

    //this handles a lot of logging and backend
    private BleConnectCallback bhConnectCallback = new BleConnectCallback() {
        @Override
        public void onFailure(int failCode, String info, BleDevice device) {
            Logger.e("connect fail:" + info);
            deviceList.remove(device);
            Reconnect();
        }

        @Override
        public void onStart(boolean startConnectSuccess, String info, BleDevice device) {

            Logger.e("start connecting:" + startConnectSuccess + "    info=" + info);

        }

        @Override
        public void onConnected(BleDevice device) {
            connectedDevices.add(device);
            TextView tvConnectionState = ((TextView) rv.findViewHolderForAdapterPosition(deviceAddressList.indexOf(device.address)).itemView.findViewById(R.id.tv_connection_state));
            tvConnectionState.setText("connected");
            tvConnectionState.setTextColor(getResources().getColor(device.connected ? R.color.bright_blue : R.color.bright_red));
        }


        @Override
        public void onDisconnected(String info, int status, BleDevice device) {

            if (intendedDisconnect == false){
                TextView tvConnectionState = ((TextView) rv.findViewHolderForAdapterPosition(deviceAddressList.indexOf(device.address)).itemView.findViewById(R.id.tv_connection_state));
                tvConnectionState.setText("disconnected");
                tvConnectionState.setText("disconnected");
                tvConnectionState.setTextColor(getResources().getColor(R.color.bright_red));
                Reconnect();
            }
            Logger.e(device.address + " disconnected!");
        }
    };

    public void bhSendInitPayload(BleDevice device){
        Logger.e("initted " + device.address);
        String writeUuid = null;
        String characteristicUuid = null;
        String notifyUuid = null;
        int i = 0;
        //start sending data command

        //avoid crashes by insuring device is actually connected before acquiring uuid
        while (i < 20) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i++;
            if(BleManager.getInstance().isConnected(device.address)){
                //Logger.e("its says connected");
                break;
            }
        }

        //get the uuids
        if (BleManager.getInstance().isConnected(device.address)) {
            //Logger.e("connection checked");
            //devices have 'service' (parent) and 'characteristic' (child) uuids. so its map time
            List<ServiceInfo> groupList = new ArrayList<>();
            List<List<CharacteristicInfo>> childList = new ArrayList<>();
            Map<ServiceInfo, List<CharacteristicInfo>> deviceInfo = BleManager.getInstance().getDeviceServices(device.address);
            //put the uuids in their own lists for iteration
            for (Map.Entry<ServiceInfo, List<CharacteristicInfo>> e : deviceInfo.entrySet()) {
                groupList.add(e.getKey());
                childList.add(e.getValue());
                Logger.e("entries set");
            }
            //new loop - not dependent on '5'. looks for writable+notReadable (we found the write uuid, characteristic, then loop for notify)
            outerloop:
            for(i=0; i<groupList.size();i++){
                for(int j=0; j<childList.get(i).size(); j++){
                    if( (childList.get(i).get(j).writable) && ((!childList.get(i).get(j).readable))){
                        writeUuid = groupList.get(i).uuid;
                        characteristicUuid = childList.get(i).get(j).uuid;
                        break outerloop;
                    }
                }
            }
            for(int j=0; j<childList.get(i).size();j++){

                if(childList.get(i).get(j).notify){
                    notifyUuid = childList.get(i).get(j).uuid;
                }
            }
            Logger.e("uuid placed");
            //now that we have the uuids, we can send that hardcoded start command
            BleManager.getInstance().write(device, writeUuid, characteristicUuid, ByteUtils.hexStr2Bytes(radius_start_xmit), writeCallback);
            BleManager.getInstance().notify(device, writeUuid, notifyUuid, notifyCallback);
        }
    }


    private BleWriteCallback writeCallback = new BleWriteCallback() {
        @Override
        public void onWriteSuccess(byte[] data, BleDevice device) {
            intendedDisconnect = false;
            TextView topbuttn = findViewById(R.id.btn_scan);
            topbuttn.setText("Tap to End Session");
            topbuttn.setClickable(true);
            TextView tvFail = findViewById(R.id.tv_fail);
            tvFail.setText("Data collection in progress...");
            tvFail.setVisibility(View.VISIBLE);
            Logger.e("write success:" + ByteUtils.bytes2HexStr(data) + " from blewritecallback");
        }

        @Override
        public void onFailure(int failCode, String info, BleDevice device) {
            Logger.e("write fail:" + info);
            for(BleDevice elements : BleManager.getInstance().getConnectedDevices()){
                BleManager.getInstance().disconnect(elements);
            }
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
//            dTemp = Math.round(temp / 100); // drop off last two decimal places 39046 -> 390
//            dTemp /=10;                     // place the decimal 390 -> 39.0

            dTemp = (double)temp / 1000; // 39046 -> 39.046

            String dataStr = ByteUtils.bytes2HexStr(data);      //full hex bytestream for logcat output (same as in wireshark)
            String timeStr = ByteUtils.bytes2HexStr(timeBytes); //parsed time hex for logcat output
            String tempStr = ByteUtils.bytes2HexStr(tempBytes); //""temp

            String tempCelsStr = String.valueOf(dTemp);         //convert to string for logcat
            Logger.e("onCharacteristicChanged: " + dataStr + " from: "+ device.address + "  time:" + timeStr + " or: " + currentDateandTime + "  temp:" + tempStr + " C: " + tempCelsStr );

            outputData.add( new String[] {device.address, currentDateandTime, tempCelsStr});
            adptr2.notifyDataSetChanged();
            JSONObject jData = jMake(currentDateandTime,dTemp,device.address);
            new NetworkOperation().execute(jData.toString(), UserToken);
            Reconnect();

        }

        @Override
        public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {
            findViewById(R.id.tv_fail).setVisibility(View.VISIBLE);
            Logger.e("notify success uuid:" + notifySuccessUuid);
        }

        @Override
        public void onFailure(int failCode, String info, BleDevice device) {
            Logger.e("notify fail:" + info);
            bhSendInitPayload(device);
        }
    };

    private JSONObject jMake(String date, double temp, String id){
        JSONObject obj = new JSONObject();
        try {
            obj.put("date", date);
            obj.put("temp", temp);
            obj.put("sensor_id", id);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    private void Reconnect(){

        if(intendedDisconnect == false) {
            for (BleDevice elements : deviceList) {
                if (BleManager.getInstance().isConnected(elements.address) != true) {
                    BleManager.getInstance().connect(elements.getDevice().getAddress(), bhConnectCallback);
                    int i = 0;
                    while ((BleManager.getInstance().isConnected(elements.address) != true) && (i < 20)) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if(BleManager.getInstance().isConnected(elements.address)){break;}
                        i++;
                    }
                    bhSendInitPayload(elements);
                    Logger.e("reconnecting attmpt to " + elements.address);
                }
            }
        }
    }

    private void endSesh(){
        Logger.e("ending sesh");
        writeToFile();
        started=false;
        intendedDisconnect = true;
        Logger.e(String.valueOf(deviceList.size()));
        for(BleDevice elements : deviceList){
            TextView tvConnectionState = ((TextView) rv.findViewHolderForAdapterPosition(deviceAddressList.indexOf(elements.address)).itemView.findViewById(R.id.tv_connection_state));
            tvConnectionState.setText("disconnected");
            tvConnectionState.setTextColor(getResources().getColor(R.color.bright_red));
            BleManager.getInstance().disconnect(elements);
        }
        deviceList.clear();
        connectedDevices.clear();
        deviceAddressList.clear();
        Button btnScan = findViewById(R.id.btn_scan);
        btnScan.setText("Tap to Start Session");
        findViewById(R.id.tv_fail).setVisibility(View.INVISIBLE);

    }

    private void writeToFile() {
        try{
            // Logger.e("writing file");
            File path = getExternalFilesDir(null);
            Date date = new Date() ;
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'H'-HH") ;
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
            bw.flush();
            bw.close();
            //Logger.e("written");

        } catch (IOException e) {
            e.printStackTrace();
            Logger.e("didnt write");
        }
    }

    private String readFromFile() {
        String ret = "";
        try {
            File path = getExternalFilesDir(null);
            File file = new File(path,
                    "config.txt");
            FileInputStream inputStream = new FileInputStream(file);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString);
                    Logger.e("reading config");
                }

                inputStream.close();
                bufferedReader.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("login activity", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e.toString());
        }
        return ret;
    }

    private void initDataRv(){
        RecyclerView recyclerView = findViewById(R.id.rvdata);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adptr2 = new DataAdapter(this,  outputData);
        recyclerView.setAdapter(adptr2);
    }


}//end
