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
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Handler;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class WorkingActivity extends AppCompatActivity {

    private TextView output_text;

    private LocationManager locationManager = null;

    Ticket ticket;
    private String IMEI = "";
    private String description = "";
    private String tId = "88888";

    private String mLatitude = null;
    private String mLongitude = null;
    private String curTime = null;

    private static final String TAG = "TESTGPS";

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
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            if (mLatitude != null && mLongitude != null) {
                String temp = output_text.getText().toString();
                String location = "" + mLatitude + "; " + mLongitude + "; " + curTime;
                String nfcid = ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));
                description = nfcid + "; " + location;
                output_text.setText(temp + nfcid + "; " + location + "\n" );
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

    private String getImei() {
        String imei = null;
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(getApplicationContext().TELEPHONY_SERVICE);
            imei = telephonyManager.getDeviceId();
        }
        return imei;
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
