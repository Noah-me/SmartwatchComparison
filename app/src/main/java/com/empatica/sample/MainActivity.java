package com.empatica.sample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;


public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private EmpaDeviceManager deviceManager = null;

    private TextView accel_xLabel, accel_yLabel, accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView statusLabel;
    private TextView deviceNameLabel;

    private LinearLayout dataCollectionLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accel_xLabel = findViewById(R.id.accel_x);
        accel_yLabel = findViewById(R.id.accel_y);
        accel_zLabel = findViewById(R.id.accel_z);

        bvpLabel = findViewById(R.id.bvp);
        edaLabel = findViewById(R.id.eda);
        ibiLabel = findViewById(R.id.ibi);
        temperatureLabel = findViewById(R.id.temperature);

        batteryLabel = findViewById(R.id.battery);
        statusLabel = findViewById(R.id.status);
        deviceNameLabel = findViewById(R.id.deviceName);

        dataCollectionLayout = findViewById(R.id.dataArea);

        final Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deviceManager != null) deviceManager.disconnect();
            }
        });

        if (allPermissionsGranted(Constants.REQUESTED_PERMISSIONS))
            initEmpaticaDeviceManager();
        else
            ActivityCompat.requestPermissions(this,
                Constants.REQUESTED_PERMISSIONS,
                Constants.REQUEST_CODE_LOCATION_NETWORK_BT);
    }

    private boolean allPermissionsGranted(String[] requestedPermissions) {
        boolean allPermissionsGranted = true;
        for (String permission : requestedPermissions) {
            boolean wasThisPermissionGranted =
                    (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED);
            allPermissionsGranted &= wasThisPermissionGranted;

        }
        return allPermissionsGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constants.REQUEST_CODE_LOCATION_NETWORK_BT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initEmpaticaDeviceManager();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Required Permissions")
                        .setMessage("Bluetooth, Coarse Location, and Network Access are required for this application.")
                        .setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        Constants.REQUESTED_PERMISSIONS,
                                        Constants.REQUEST_CODE_LOCATION_NETWORK_BT);
                            }
                        })
                        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                finish();   // without required permissions, exit is the only way
                            }
                        })
                        .show();
                    return;
                }
                break;
        }
    }

    private void initEmpaticaDeviceManager() {
        // Check #1 - make sure that the developer has provided a valid API key
        if (TextUtils.isEmpty(Constants.EMPATICA_API_KEY)) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Please insert your API KEY")
                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();   // without API key, exit is the only way
                        }
                    })
                    .show();
            return;
        }

        // Check #2 - make sure device is connected to active Wifi or cellular network
        if (!isConnectedToActiveNetwork()) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Active network connection required to proceed.")
                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();   // without network connection, exit is the only way
                        }
                    })
                    .show();
            return;
        }

        // Check #3 - are we EXTRA sure we have the Bluetooth permissions?
        /*if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
        }*/

        // ----------------------------------------------------------------------------------------
        // ----------------------------------------------------------------------------------------
        //
        // If we've gotten this far, it means we've received all required permissions and passed
        // the preliminary checks ... carry on!
        //
        // ----------------------------------------------------------------------------------------
        // ----------------------------------------------------------------------------------------

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

        // Initialize the Device Manager using your API key
        deviceManager.authenticateWithAPIKey(Constants.EMPATICA_API_KEY);
    }

    private boolean isConnectedToActiveNetwork() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------
    //
    //                                  STATUS DELEGATE METHODS
    //
    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            deviceManager.stopScanning();
            try {
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    @Override
    public void didEstablishConnection() {
        showDataCollectionFields();
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        updateLabel(statusLabel, status.name());
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            deviceManager.startScanning();
            hideDataCollectionFields();
        } else if (status == EmpaStatus.CONNECTED) {
            showDataCollectionFields();
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
            hideDataCollectionFields();
        }
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
        didUpdateOnWristStatus(status);
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status == EmpaSensorStatus.ON_WRIST) {
                    ((TextView) findViewById(R.id.wrist_status_label)).setText("ON WRIST");
                } else {
                    ((TextView) findViewById(R.id.wrist_status_label)).setText("NOT ON WRIST");
                }
            }
        });
    }

    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------
    //
    //                                  DATA DELEGATE METHODS
    //
    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, "" + ibi);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
    }

    @Override
    public void didReceiveTag(double timestamp) {
        // TODO - implement if necesary
    }

    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------
    //
    //                               ACTIVITY LIFECYCLE METHODS
    //
    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // The user chose not to enable Bluetooth ...
            // TODO - deal with this
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------
    //
    //                                  PRIVATE UTILITY METHODS
    //
    // ----------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------

    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    private void showDataCollectionFields() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dataCollectionLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideDataCollectionFields() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dataCollectionLayout.setVisibility(View.INVISIBLE);
            }
        });
    }

}