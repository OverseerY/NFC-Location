package com.yaroslav.materialapptest;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.GsonBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class WorkingActivity extends AppCompatActivity {

    private TextView output_text;

    private LocationManager locationManager = null;

    Ticket ticket;
    private String IMEI = "";
    private String description = "";
    private String tId = "88888";

    private View mLayout;

    private String mLatitude = null;
    private String mLongitude = null;
    private String curTime = null;

    private static final String TAG = "TESTGPS";

    private static final int PERMISSION_REQUEST_IMEI = 0;
    private static final int PERMISSION_REQUEST_LOCATION = 0;

    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 1f;

    private final String[][] techList = new String[][]{
            new String[]{
                    NfcA.class.getName(),
                    NfcB.class.getName(),
                    NfcV.class.getName(),
                    IsoDep.class.getName(),
                    MifareClassic.class.getName(),
                    MifareUltralight.class.getName(),
                    Ndef.class.getName()
            }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_working);
        mLayout = findViewById(R.id.main_layout);

        output_text = (TextView) findViewById(R.id.output_text);
        output_text.setMovementMethod(new ScrollingMovementMethod());

        initializeLocationManager();
        initializeLocationProvider();

        IMEI = getImei();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, new IntentFilter[] {filter}, this.techList);
    }

    @Override
    protected void onPause() {
        super.onPause();
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String s = "";
        // parse through all NDEF messages and their records and pick text type only
        Parcelable[] data = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (data != null) {
            try {
                for (int i = 0; i < data.length; i++) {
                    NdefRecord[] recs = ((NdefMessage) data[i]).getRecords();
                    for (int j = 0; j < recs.length; j++) {
                        if (recs[j].getTnf() == NdefRecord.TNF_WELL_KNOWN &&
                                Arrays.equals(recs[j].getType(), NdefRecord.RTD_TEXT)) {
                            byte[] payload = recs[j].getPayload();
                            String textEncoding = ((payload[0] & 0200) == 0) ? "UTF-8" : "UTF-16";
                            int langCodeLen = payload[0] & 0077;

                            s += (new String(payload, langCodeLen + 1, payload.length - langCodeLen - 1, textEncoding) + "\n");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("TagDispatch", e.toString());
            }
        }
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            if (mLatitude != null && mLongitude != null) {
                String temp = output_text.getText().toString();
                String location = "" + mLatitude + "; " + mLongitude + "; " + curTime;
                String nfcid = ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));
                description = nfcid + "; " + location;
                output_text.setText(temp + nfcid + ", " + s + "; " + location + "\n" );
                new UploadJsonTask().execute();
            } else {
                String temp = output_text.getText().toString();
                output_text.setText(temp + "" + ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)) + "\n");
                Toast.makeText(this, "Tag was read successfully, but location is not reachable", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Tag was not read.\nTry again or read next", Toast.LENGTH_LONG).show();
        }
    }

    private String ByteArrayToHexString(byte [] inarray) {
        int i, j, in;
        String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
        String out= "";
        for(j = 0 ; j < inarray.length ; ++j)
        {
            in = (int) inarray[j] & 0xff;
            i = (in >> 4) & 0x0f;
            out += hex[i];
            i = in & 0x0f;
            out += hex[i];
        }
        return out;
    }

    //==============================================================================================
    private String converteTime(long locTime) {
        DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date date = new Date(locTime);
        String formatted = format.format(date);
        return formatted;
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (locationManager == null) {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            Toast.makeText(this, "Location Manager Initialized", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Location Manager does not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeLocationProvider() {
        Log.e(TAG, "initializeLocationProvider");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, locationListener);
            } catch (java.lang.SecurityException ex) {
                Log.i(TAG, "Fail to request location update, ignore", ex);
                Toast.makeText(this, "Fail to request location update", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "Network provider does not exist, " + ex.getMessage());
                Toast.makeText(this, "Network provider does not exist", Toast.LENGTH_SHORT);
            }
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, locationListener);
            } catch (java.lang.SecurityException ex) {
                Log.i(TAG, "Fail to request location update, ignore", ex);
                Toast.makeText(this, "Fail to request location update", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "Gps provider does not exist " + ex.getMessage());
                Toast.makeText(this, "Gps provider does not exist", Toast.LENGTH_SHORT).show();
            }
        } else {
            requestLocationPermissions();
        }
    }

    // Define a listener that responds to location updates
    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            mLongitude = location.convert(location.getLongitude(), location.FORMAT_DEGREES);
            mLatitude = location.convert(location.getLatitude(), location.FORMAT_DEGREES);
            curTime = converteTime(location.getTime());
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };

    //==============================================================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_IMEI) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(mLayout, R.string.imei_granted, Snackbar.LENGTH_SHORT).show();
                IMEI = getImei();
            } else {
                Snackbar.make(mLayout, R.string.imei_denied, Snackbar.LENGTH_SHORT).show();
            }
        }
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(mLayout, R.string.imei_granted, Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(mLayout, R.string.imei_denied, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private String getImei() {
        String imei = null;
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(getApplicationContext().TELEPHONY_SERVICE);
            imei = telephonyManager.getDeviceId();
        } else {
            requestImeiPermission();
        }
        return imei;
    }

    private void requestLocationPermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Snackbar.make(mLayout, R.string.location_permission_required, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActivityCompat.requestPermissions(WorkingActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
                }
            }).show();
        } else {
            Snackbar.make(mLayout, R.string.location_unavailable, Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        }
    }

    private void requestImeiPermission() {
        // Permission has not been granted and must be requested.
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_PHONE_STATE)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with cda button to request the missing permission.
            Snackbar.make(mLayout, R.string.imei_permission_required,
                    Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Request the permission
                    ActivityCompat.requestPermissions(WorkingActivity.this,
                            new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_IMEI);
                }
            }).show();

        } else {
            Snackbar.make(mLayout, R.string.imei_unavailable, Snackbar.LENGTH_SHORT).show();
            // Request the permission. The result will be received in onRequestPermissionResult().
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_IMEI);
        }
    }

    public static void executeJson(String id, String name, String imei) {
        Map<String, String> ticket = new HashMap<String, String>();
        ticket.put("Id", id);
        ticket.put("Name", name);
        ticket.put("IMEI", imei);
        String json = new GsonBuilder().create().toJson(ticket, Map.class);
        makeRequest("http://testapi312.azurewebsites.net/api/values/", json);
    }

    public static HttpResponse makeRequest(String uri, String json) {
        try {
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setEntity(new StringEntity(json));
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            return new DefaultHttpClient().execute(httpPost);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class UploadJsonTask extends AsyncTask<URL, Integer, String> {
        @Override
        protected String doInBackground(URL... urls) {
            executeJson(tId, description, IMEI);
            return null;
        }
    }

}
