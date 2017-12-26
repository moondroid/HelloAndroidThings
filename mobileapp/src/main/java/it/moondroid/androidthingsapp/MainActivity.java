package it.moondroid.androidthingsapp;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Created by Marco on 22/12/2017.
 */

public class MainActivity extends Activity {

    // https://console.firebase.google.com/u/0/project/helloandroidthings/database/helloandroidthings/data
    private static final String TAG = MainActivity.class.getSimpleName();

    private Switch bcm6Switch;
    private TextView iPTextView;
    private TextView dateTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        iPTextView = findViewById(R.id.textViewIP);
        dateTextView = findViewById(R.id.textViewDate);
        bcm6Switch = findViewById(R.id.switchBCM6);
        bcm6Switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Toast.makeText(MainActivity.this, "led: " + (b ? "acceso" : "spento"), Toast.LENGTH_SHORT).show();

                // Write a message to the database
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference myRef = database.getReference("BCM6");
                myRef.setValue(b);
            }
        });

        initFirebase();
        readStatus();
    }

    private void initFirebase(){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference bcm6Ref = database.getReference("BCM6");

        // Read from the database
        bcm6Ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                Boolean value = dataSnapshot.getValue(Boolean.class);
                Log.d(TAG, "Firebase value is: " + value);
                if (value != null){
                    bcm6Switch.setChecked(value);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Firebase failed to read value.", error.toException());
            }
        });
    }

    private void readStatus(){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference statusRef = database.getReference("status");
        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Status status = dataSnapshot.getValue(Status.class);
                Log.d(TAG, "Firebase status is: " + status);
                if (status != null){
                    iPTextView.setText(String.format("Raspberry Pi 3 IP Address: %s", status.ip));
                    dateTextView.setText(status.date);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Firebase failed to read value.", error.toException());
            }
        });
    }

    private static class Status {
        String ip;
        String date;

        @Override
        public String toString() {
            return "Status{" +
                    "ip='" + ip + '\'' +
                    ", date='" + date + '\'' +
                    '}';
        }
    }
}
