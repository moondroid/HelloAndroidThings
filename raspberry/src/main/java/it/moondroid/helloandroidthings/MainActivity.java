package it.moondroid.helloandroidthings;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by Marco on 21/12/2017.
 */

public class MainActivity extends Activity {

    // adb connect Android.local
    // adb connect <ip address> (192.168.2.14)
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;
    private static final int INTERVAL_BETWEEN_STATUS_MS = 1000 * 60; // 1 minute
    private static final String BCM5 = "BCM5";
    private static final String BCM6 = "BCM6";

    private Handler mHandler = new Handler();
    private Gpio mLedGpio;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        configureOutputGpio();
        configureInputGpio();
        initFirebase();
//        startBlinking();

        startSendingStatus();
    }

    private void startSendingStatus(){
        Log.d(TAG, "Start sending Status");
        mHandler.post(mStatusRunnable);
    }

    private void writeStatus(){

        WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));

        Log.v(TAG, "IP Address: " + ip);
        TextView textView = findViewById(R.id.android_device_ip_address);
        textView.setText("Your Device IP Address: " + ip);

        // Write ip to Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference ipRef = database.getReference("status/ip");
        ipRef.setValue(ip);

        long yourmilliseconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy HH:mm", Locale.ITALIAN);
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
        Date resultdate = new Date(yourmilliseconds);
        DatabaseReference dateRef = database.getReference("status/date");
        String formattedDate = sdf.format(resultdate);
        dateRef.setValue(formattedDate);
        Log.v(TAG, "Date: " + formattedDate);
    }

    private void configureOutputGpio(){
        PeripheralManagerService service = new PeripheralManagerService();
        try {
            mLedGpio = service.openGpio(BCM6);
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Log.d(TAG, "GPIO pin : " + mLedGpio.getName() + " DIRECTION_OUT_INITIALLY_LOW");
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void startBlinking(){
        Log.d(TAG, "Start blinking GPIO pin : " + mLedGpio.getName());
        mHandler.post(mBlinkRunnable);
    }

    private boolean setLedGpioValue(boolean value){
        if (mLedGpio != null) {
            try {
                // Toggle the GPIO state
                mLedGpio.setValue(value);
                Log.v(TAG, "State set to " + mLedGpio.getValue());
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
                return false;
            }
        }
        return false;
    }

    private void initFirebase(){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference bcm6Ref = database.getReference(BCM6);

        // Read from the database
        bcm6Ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                Boolean value = dataSnapshot.getValue(Boolean.class);
                Log.d(TAG, "Firebase value is: " + value);
                if (value != null){
                    setLedGpioValue(value);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Firebase failed to read value.", error.toException());
            }
        });
    }

    private void configureInputGpio() {
        PeripheralManagerService manager = new PeripheralManagerService();

        try {
            Gpio gpio = manager.openGpio(BCM5);

            // Initialize the pin as an input
            gpio.setDirection(Gpio.DIRECTION_IN);
            // Low voltage is considered active
            gpio.setActiveType(Gpio.ACTIVE_LOW);
            // Register for all state changes (other types are bugged)
            gpio.setEdgeTriggerType(Gpio.EDGE_BOTH);
            gpio.registerGpioCallback(new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    // Read the active low pin state
                    try {
                        boolean pressed = gpio.getValue();
                        Log.v(TAG, "GPIO value is: " + pressed);
                        if (pressed){
                            toggleOutput();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error on PeripheralIO API", e);
                    }
                    // Continue listening for more interrupts
                    return true;
                }

                @Override
                public void onGpioError(Gpio gpio, int error) {
                    Log.e(TAG, gpio.getName() + " Error on PeripheralIO API " + error);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void toggleOutput(){
        // Write a message to the database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        final DatabaseReference bcm6Ref = database.getReference(BCM6);
        bcm6Ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Boolean value = dataSnapshot.getValue(Boolean.class);
                Log.d(TAG, "Firebase value is: " + value);
                if (value != null){
                    bcm6Ref.setValue(!value);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, databaseError.getMessage());
            }
        });

    }

    private Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                boolean success = setLedGpioValue(!mLedGpio.getValue());
                if (success) mHandler.postDelayed(mBlinkRunnable, INTERVAL_BETWEEN_BLINKS_MS);
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };

    private Runnable mStatusRunnable = new Runnable() {
        @Override
        public void run() {
            writeStatus();
            mHandler.postDelayed(mStatusRunnable, INTERVAL_BETWEEN_STATUS_MS);
        }
    };
}
