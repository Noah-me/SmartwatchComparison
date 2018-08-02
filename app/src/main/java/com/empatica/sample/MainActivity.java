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
import org.apache.commons.math3.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;


import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


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
    private TextView filenameLabel;

    private LinearLayout dataCollectionLayout;

    private ArrayList<String> rawData;                  //stores raw data line by line in CSV format
    private ArrayList<String> featureDataGeorgia;       //features from Georgia Tech
    //private ArrayList<String> featureDataCalifornia;    //features from UCLA

    private File rawDataFile;                           //contains raw data
    private File featureFileGeorgia;                    //contains Georgia Tech features
    //private File featureFileCalifornia;

    private DescriptiveStatistics xFeatures;
    private DescriptiveStatistics yFeatures;
    private DescriptiveStatistics zFeatures;

    float[] accValues = new float[3];                   //raw data for x, y, z

    //Time and data for file name
    private SimpleDateFormat date = new SimpleDateFormat("MM-dd-yyyy_HH.mm", Locale.US);
    private SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US); //How many digits for millisec?

    /**
     *
     * @param savedInstanceState
     */
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
        filenameLabel = findViewById(R.id.filename);

        dataCollectionLayout = findViewById(R.id.dataArea);

        final Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dumpData(rawDataFile, rawData);
                //dumpData(featureFileCalifornia, featureDataCalifornia);
                dumpData(featureFileGeorgia, featureDataGeorgia);
                if (deviceManager != null) deviceManager.disconnect();
            }
        });

        if (allPermissionsGranted(Constants.REQUESTED_PERMISSIONS))
            initEmpaticaDeviceManager();
        else
            ActivityCompat.requestPermissions(this,
                    Constants.REQUESTED_PERMISSIONS,
                    Constants.REQUEST_CODE_LOCATION_NETWORK_BT);

        rawData = new ArrayList<String>();
        featureDataGeorgia = new ArrayList<String>();
        //featureDataCalifornia = new ArrayList<String>();

        //Add header lines
        rawData.add("Time,X,Y,Z\n");

        featureDataGeorgia.add("Time,Mean-X,Variance-X,Skewness-X,Kurtosis-X,RMS-X,Mean-Y,Variance-Y," +
                "Skewness-Y,Kurtosis-Y,RMS-Y,Mean-Z,Variance-Z,Skewness-Z,Kurtosis-Z,RMS-Z\n");

        /*featureDataCalifornia.add("Time,Amplitude-X,Median-X,Mean-X,Max-X,Min-X,Peak-to-Peak-X," +
                "Variance-X,Std Dev-X,RMS-X,Skewness-X,Amplitude-Y,Median-Y,Mean-Y,Max-Y,Min-Y," +
                "Peak-to-Peak-Y,Variance-Y,Std Dev-Y,RMS-Y,Skewness-Y,Amplitude-Z,Median-Z,Mean-Z," +
                "Max-Z,Min-Z,Peak-to-Peak-Z,Variance-Z,Std Dev-Z,RMS-Z,Skewness-Z\n");*/

        xFeatures = new DescriptiveStatistics(32); //about 1 second
        yFeatures = new DescriptiveStatistics(32);
        zFeatures = new DescriptiveStatistics(32);
        try {
            //Create directory
            File dir = new File(MainActivity.this.getApplicationContext().getExternalFilesDir(null), "SensorData");

            if (!dir.exists()) {
                dir.mkdir();
            }

            //Create other files
            rawDataFile = new File(dir, "E4_" + date.format((Calendar.getInstance()).getTime()) + ".csv");
            featureFileGeorgia = new File(dir, "E4_GA_" + date.format((Calendar.getInstance()).getTime()) + ".csv");
            //featureFileCalifornia = new File(dir, "E4_CA_" + date.format((Calendar.getInstance()).getTime()) + ".csv");
            if (!rawDataFile.exists()) {
                rawDataFile.createNewFile();
            }
            if (!featureFileGeorgia.exists()) {
                featureFileGeorgia.createNewFile();
            }
            /*if (!featureFileCalifornia.exists()) {
                featureFileCalifornia.createNewFile();
            }*/

            updateLabel(filenameLabel, rawDataFile.getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param requestedPermissions
     * @return
     */
    private boolean allPermissionsGranted(String[] requestedPermissions) {
        boolean allPermissionsGranted = true;
        for (String permission : requestedPermissions) {
            boolean wasThisPermissionGranted =
                    (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED);
            allPermissionsGranted &= wasThisPermissionGranted;

        }
        return allPermissionsGranted;
    }

    /**
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
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

    /**
     *
     */
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

    /**
     *
     * @return
     */
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

    /**
     *
     * @param bluetoothDevice
     * @param deviceName
     * @param rssi
     * @param allowed
     */
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

    /**
     *
     */
    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    /**
     *
     */
    @Override
    public void didEstablishConnection() {
        showDataCollectionFields();
    }

    /**
     *
     * @param status
     */
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

    /**
     *
     * @param status
     * @param type
     */
    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
        didUpdateOnWristStatus(status);
    }

    /**
     *
     * @param status
     */
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

    /**
     * Receives information from accelerometer, stores data
     * @param x - value for x axis
     * @param y - value for y axis
     * @param z - value for z axis
     * @param timestamp - time associated with event
     */
    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);

        accValues[0] = (float) (x/64.0);        //divide by 64 to convert to g
        accValues[1] = (float) (y/64.0);
        accValues[2] = (float) (z/64.0);


        //Used system time to ensure timestamps of both devices come from the paired smartphone's time
        String currentTime = time.format(System.currentTimeMillis());

        String line = "" + currentTime + "," + (x/64.0) + "," + (y/64.0) + "," + (z/64.0) + "\n";
        this.rawData.add(line);

        calculateFeatures(currentTime, accValues);
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


    /**
     * Writes data to file line by line
     * @param file - file to be written to
     * @param data - contains data in CSV format
     */
    private void dumpData(File file, ArrayList<String> data) {
        try {
            FileWriter writer = new FileWriter(file);
            int size = data.size();
            for (int i = 0; i < size; i++) {
                writer.write(data.get(i));
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Stores values, calls feature methods
     * @param timestamp
     * @param values
     */
    private void calculateFeatures(String timestamp, float[] values) {
        xFeatures.addValue(values[0]);
        yFeatures.addValue(values[1]);
        zFeatures.addValue(values[2]);

        calculateGeorgia(timestamp);
//        calculateCalifornia(timestamp);

    }

    /**
     * Calculates mean, variance, skewness, kurtosis, RMS for each axis
     * @param timestamp
     */
    private void calculateGeorgia(String timestamp) {
        String line = timestamp + "," + xFeatures.getMean() + "," + xFeatures.getVariance() + ","
                + xFeatures.getSkewness() + "," + xFeatures.getKurtosis() + ","
                + calculateRMS(xFeatures) + "," + yFeatures.getMean() + ","
                + yFeatures.getVariance() + "," + yFeatures.getSkewness() + ","
                + yFeatures.getKurtosis() + "," + calculateRMS(yFeatures) + ","
                + zFeatures.getMean() + "," + zFeatures.getVariance() + ","
                + zFeatures.getSkewness() + "," + zFeatures.getKurtosis() + ","
                + calculateRMS(zFeatures) + "\n";

        featureDataGeorgia.add(line);
    }

/*    private void calculateCalifornia(String timestamp) {
        double[] xSorted = xFeatures.getSortedValues();
        double[] ySorted = yFeatures.getSortedValues();
        double[] zSorted = zFeatures.getSortedValues();

        double xMedian = xSorted[(int) xFeatures.getN() / 2];
        double yMedian = ySorted[(int) yFeatures.getN() / 2];
        double zMedian = zSorted[(int) zFeatures.getN() / 2];

        String line = timestamp + "," + Math.abs(accValues[0]) + "," + xMedian + ","
                + xFeatures.getMean() + "," + xFeatures.getMax() + "," + xFeatures.getMin() + ","
                + (xFeatures.getMax() - xFeatures.getMin()) + "," + xFeatures.getVariance() + ","
                + xFeatures.getStandardDeviation() + "," + calculateRMS(xFeatures) + ","
                + xFeatures.getSkewness() + "," + Math.abs(accValues[1]) + "," + yMedian + "," + yFeatures.getMean() + ","
                + yFeatures.getMax() + "," + yFeatures.getMin() + "," + (yFeatures.getMax()
                - yFeatures.getMin()) + "," + yFeatures.getVariance() + ","
                + yFeatures.getStandardDeviation() + "," + calculateRMS(yFeatures) + "," + yFeatures.getSkewness()
                + "," + Math.abs(accValues[2]) + "," + zMedian + ","
                + zFeatures.getMean() + "," + zFeatures.getMax() + "," + zFeatures.getMin() + ","
                + (zFeatures.getMax() - zFeatures.getMin()) + "," + zFeatures.getVariance() + ","
                + zFeatures.getStandardDeviation() + "," + calculateRMS(zFeatures) + "," + zFeatures.getSkewness() +"\n";

        featureDataCalifornia.add(line);
    }*/


    /**
     * Calculate RMS (sum of squares divided by n)
     * @param signal - window of axis
     * @return RMS as a String
     */
    private String calculateRMS(DescriptiveStatistics signal) {
        return "" + (signal.getSumsq() / signal.getN());
    }



}