package com.sapra.codered;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.melnykov.fab.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class Background extends AppCompatActivity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, ResultCallback<LocationSettingsResult> {
    private static final int EDIT_CONTACTS_CODE = 200;
    private SensorManager sensorMan;
    private Sensor accelerometer;
    private int SERVICE_RUNNING_ID=1;
    private boolean sensors_enabled = false;
    private ArrayList<Contact> active_contacts;
    private ListAdapter customAdapter;
    private long lastSensorUpdate = 0, lastMessageSent = 0;
    private float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 1300;
    private String MESSAGE = "Test message, sorry I'm testing an app and I don't mean to send this to you.";

    protected GoogleApiClient mGoogleApiClient;
    protected LocationRequest mLocationRequest;
    private Location currentLocation;
    // Used for checking settings to determine if the device has optimal location settings
    protected LocationSettingsRequest mLocationSettingsRequest;

    private Notifier mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBoundService = ((Notifier.LocalBinder)service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background);

        ListView listView = (ListView) findViewById(R.id.list);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.attachToListView(listView);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(v.getContext(), ContactPicker.class);
                i.putParcelableArrayListExtra("CONTACTS", active_contacts);
                startActivityForResult(i, EDIT_CONTACTS_CODE);
            }
        });


        //Initialize sensors
        sensorMan = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMan.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        initializeActiveContacts();

        //Turn on location services
        buildGoogleApiClient();
        createLocationRequest();
        buildLocationSettingsRequest();
        checkLocationSettings();

        activateSensors();

        customAdapter = new ListAdapter(this, R.layout.active_contact_list_item);
        listView.setAdapter(customAdapter);
    }

    private void activateSensors() {
        sensors_enabled = (active_contacts.size() > 0);
        if (sensors_enabled){
            doBindService();
        }
    }
    private void sendNotification(){
        if (sensors_enabled){
            String message_content = (currentLocation != null) ?
                                "Sent your location and notified your emergency contacts!" :
                                "Notified your emergency contacts!";
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(this)
                            .setColor(getResources().getColor(R.color.icon_color))
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("CodeRed Emergency")
                            .setContentText(message_content);
            int mNotificationId = (int) (Math.random() * 100);
            NotificationManager mNotifyMgr =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            Intent resultIntent = new Intent(this, Background.class);
            resultIntent.setAction(Intent.ACTION_MAIN);
            resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent resultPendingIntent = PendingIntent.getActivity(this,0,resultIntent,0);

            mBuilder.setContentIntent(resultPendingIntent);

            mNotifyMgr.notify(mNotificationId, mBuilder.build());
        }
    }

    /*
        Helper method to retrieve selected contacts from SharedPreferences and store in active_contacts
    */
    private void initializeActiveContacts() {
        active_contacts = new ArrayList<Contact>();
        SharedPreferences prefs = this.getSharedPreferences("CONTACTS", Context.MODE_PRIVATE);
        String contacts_as_json = prefs.getString("CONTACTS", "");
        JSONArray contacts;
        try{
            contacts = new JSONArray(contacts_as_json);
            for (int i = 0; i < contacts.length(); i++) {
                active_contacts.add(new Contact(contacts.getJSONObject(i)));
            }
        } catch(JSONException je){
            je.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_background, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            //TODO open settings activity
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        switch(requestCode){
            case EDIT_CONTACTS_CODE:
                switch(resultCode){
                    case RESULT_OK:
                        ArrayList<Contact> selected_contacts = intent.getParcelableArrayListExtra("CONTACTS");
                        SharedPreferences.Editor editor = getSharedPreferences("CONTACTS", MODE_PRIVATE).edit();
                        String json_string = convertListToJSON(selected_contacts);
                        editor.putString("CONTACTS", json_string);
                        editor.commit();

                        Toast.makeText(this.getApplicationContext(), "Contacts updated successfully", Toast.LENGTH_LONG).show();
                        initializeActiveContacts();
                        activateSensors();
                        customAdapter.notifyDataSetChanged();
                        break;
                    default:
                        break;
                }
                break;
            case 300:
                switch(resultCode){
                    case Activity.RESULT_OK:
                        Log.i("LOCATION", "User agreed to make required location settings changes.");
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i("LOCATION", "User chose not to make required location settings changes.");
                        break;
                }
                break;
        }
    }

    /**Converts an arraylist to a json string
     * @param selected_contacts The ArrayList of Contact objects
     * @return a string repreentation of JSONArray of contacts
     */
    private String convertListToJSON(List<Contact> selected_contacts) {
        JSONArray contacts = new JSONArray();
        for (Contact c : selected_contacts){
            contacts.put(c.toJSON());
        }
        return contacts.toString();
    }

    /*Detects accelerometer and determines if the phone was shaken
     */
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            long curTime = System.currentTimeMillis();
            if ((curTime - lastSensorUpdate) > 100) {
                long diffTime = (curTime - lastSensorUpdate);
                lastSensorUpdate = curTime;
                float speed = Math.abs(x + y + z - last_x - last_y - last_z)/ diffTime * 10000;
                //Only send if the shaking is above a threshold and a message hasn't been sent for 30 seconds
                if (speed > SHAKE_THRESHOLD && curTime - lastMessageSent > 30000) {
                    lastMessageSent = curTime;
                    for (Contact emergency_contact : active_contacts){
                        Log.i("NUMBER", emergency_contact.getNumber());
                        sendSMS("16306961597", MESSAGE);
                    }
                    sendNotification();
                }

                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    /**Helper method to send a message using SmsManager
     * @param message The message to send
     * @param number The number to send the message to
     */
    public void sendSMS(final String number, final String message){
        final SmsManager sms = SmsManager.getDefault();
        //Send current location in Google Maps URL format
        if (currentLocation != null){
            double latitude = currentLocation.getLatitude();
            double longitude = currentLocation.getLongitude();
            String uri = "http://maps.google.com/?q=" + latitude+","+longitude;
            sms.sendTextMessage(number, null, Uri.parse(uri).toString(), null, null);
        }
        //Sends normal message
        sms.sendTextMessage(number, null, message, null, null);
    }

    public class ListAdapter extends ArrayAdapter<Contact> {

        private int resourceID;
        public ListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            this.resourceID = textViewResourceId;
        }

        private class ViewHolder {
            TextView name;
            ImageView image;
        }

        @Override
        public int getCount() {
            return active_contacts.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(resourceID, null);

                holder = new ViewHolder();
                holder.image = (ImageView) convertView.findViewById(R.id.person_image);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }


            Contact c = active_contacts.get(position);
            holder.name.setText(c.getName());
            holder.image.setImageURI(c.getImageUrl());
            if (holder.image.getDrawable() == null){
                holder.image.setImageResource(R.drawable.person);
            }

            return convertView;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }


    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    void doBindService() {
        startService(new Intent(Background.this, Notifier.class));
        bindService(new Intent(Background.this,
                Notifier.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorMan.registerListener((SensorEventListener) this, accelerometer,
                SensorManager.SENSOR_DELAY_UI);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(30000);
        mLocationRequest.setFastestInterval(15000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    //Updates location to global variable
    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onConnected(Bundle connectionHint) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    protected void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);
        mLocationSettingsRequest = builder.build();
    }

    protected void checkLocationSettings() {
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        mGoogleApiClient,
                        mLocationSettingsRequest
                );
        result.setResultCallback(this);
    }
    public void onResult(LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                Log.i("LOCATION", "All location settings are satisfied.");
                startLocationUpdates();
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i("LOCATION", "Location settings are not satisfied. Show the user a dialog to" +
                        "upgrade location settings ");
                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().
                    status.startResolutionForResult(Background.this, 300);
                } catch (IntentSender.SendIntentException e) {
                    Log.i("LOCATION", "PendingIntent unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                Log.i("LOCATION", "Location settings are inadequate, and cannot be fixed here. Dialog " +
                        "not created.");
                break;
        }
    }
}
