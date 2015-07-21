package x40241.tina.fredericks.a5.app;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    //DEBUG
    private static final String LOG = "MAIN_ACTIVITY";
    private static final boolean DEBUG = true;

    //CONSTANTS
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private static final float METER_MILE_CONVERSION = 0.00062137f;
    private static final float METER_FEET_CONVERSION = 3.28084f;
    private static final float FEET_COIN_CONVERSION = 0.5f; //2 feet = 1 coin

    //UNITS
    private static final int MILES = 0;
    private static final int FEET = 1;
    private static final int METERS = 2;

    LocationService mBoundService;

    //save all location points during location updates
    ArrayList<Location> mLocationList;
    protected static boolean mRequestedService = false;
    long mTimeElapsed = 0; //in seconds

    //state flags
    private boolean mIsStatView = false;

    //UI widgets
    private TextView mResults;
    private TextView mStatsTextView;
    private Button mStartButton;
    private Button mStopButton;
    private static Chronometer mChronometer;
    private TextView mDistance;
    private TextView mCoins;

    //audio player
    MediaPlayer mp;

    //ScheduledThreadPoolExecutor
    ScheduledThreadPoolExecutor executor;

    //local cache
    private int mTotalCoins = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(LOG, "onCreate()");
        initialize();
        super.onCreate(savedInstanceState);

        //fix the orientation to portrait
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        //TODO: how to end this elegantly?
        if(!checkPlayServices()){
            Toast.makeText(this, "You need to install Google Play Services for this app to work.", Toast.LENGTH_LONG);
            finish();
        }

        //wire up UI widgets
        mStartButton = (Button)findViewById(R.id.startButton);
        mResults = (TextView)findViewById(R.id.results);
        mStatsTextView = (TextView)findViewById(R.id.stats);
        mChronometer = (Chronometer)findViewById(R.id.chronometer);
        mDistance = (TextView)findViewById(R.id.distance);
        mCoins = (TextView)findViewById(R.id.coins);

        //**********************************************************************************************
        //  onClickListeners
        //**********************************************************************************************


        mStartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {


                if (mStartButton.getText().equals(getResources().getString(R.string.go))) {
                    // Perform action on click

                    startRun();

                } else if (mStartButton.getText().equals(getResources().getString(R.string.stop))) {

                    stopRun();

                } else if (mStartButton.getText().equals(getResources().getString(R.string.done))) {

                    mStartButton.setText(getString(R.string.again));


                    displayRewards();

                    //TODO: display history

                } else if (mStartButton.getText().equals(getResources().getString(R.string.again))) {

                    //clear data
                    clearCacheAndUI();

                    restartRun();
                }
            }
        });
    }

    private void initialize(){
        if (DEBUG) Log.d (LOG, "initialize(): STARTING");

        //there are three main bind/unbind groupings: onCreate() and onDestroy(), onStart() and onStop(), and onResume() and onPause()
        //http://stackoverflow.com/questions/1992676/i-cant-get-rid-of-this-error-message-activity-app-name-has-leaked-servicecon
        doBindService();

        if (DEBUG) Log.d (LOG, "initialize(): COMPLETE");

    }


    @Override
    protected void onDestroy() {
        if (DEBUG) Log.d(LOG, "onDestroy");

        //always stop service then unbind from it
        //http://stackoverflow.com/questions/3385554/do-i-need-to-call-both-unbindservice-and-stopservice-for-android-services
        stopLocationService();
        doUnbindService();

        super.onDestroy();
    }

    private void startRun(){

        mStartButton.setText(getResources().getString(R.string.stop));

        setRunProperties();

    }

    private void restartRun(){

        //call public method in LocationService
        if(isBound()){
            mRequestedService = true;

            mStartButton.setText(getResources().getString(R.string.stop));
            mBoundService.saveAndClearRunData();

            setRunProperties();
        }
    }

    private void setRunProperties(){

        mRequestedService = true;

        //explicitly start service
        Bundle extras = new Bundle();
        extras.putBoolean("RequestingServiceUpdate", true);
        startService(new Intent(MainActivity.this, LocationService.class));


        //play start sound effect
        playSound(R.raw.bike_horn);

        //play sound effect, look using ExecutorService
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(new SoundForLooper(), 2, 10, TimeUnit.SECONDS);


        //chronometer settings, set base time right before starting the chronometer
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
    }

    private class SoundForLooper implements Runnable {

        public void run(){
            playSound(R.raw.crowd_cheer);
        }
    }

    private void displayResults(String message){

        mResults.setText(message);
        playSound(R.raw.cat_meow);

    }

    private void playSound(int resourceId){


        mp = MediaPlayer.create(getApplicationContext(), resourceId);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                // TODO Auto-generated method stub
                mp.reset();
                mp.release();
                mp=null;
            }

        });

        //set volume
        int maxVolume = 20;
        float desiredVolume = 5;
        float log1=(float)(Math.log(maxVolume-desiredVolume)/Math.log(maxVolume));
        mp.setVolume(1-log1, 1-log1);

        //start MediaPlayer
        mp.start();


    }

    private void stopRun(){

        mRequestedService = false;

        //get latest data
        getData();

        mStartButton.setText(getString(R.string.done));

        //stop audio player, for looping
        executor.shutdownNow();

        //stop chronometer
        mChronometer.stop();

        displayResults("You earned " + mCoins.getText() + " coins!");


    }



    private void stopLocationService(){
        mRequestedService = false;
        stopService(new Intent(MainActivity.this, LocationService.class));

    }



    //**********************************************************************************************
    //  onStart()
    //**********************************************************************************************

    @Override
    protected void onStart() {
        if (DEBUG) Log.d(LOG, "onStart");
        super.onStart();

        // Register to receive Intents with actions named DATA_SERVICE_INTENT.
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter(LocationService.LOCATION_SERVICE_INTENT));
    }

    private void getData(){
        mLocationList = (ArrayList)mBoundService.getLocationList();
        //
        //mTimeElapsed = mBoundService.getTimeElapsed();

        mTimeElapsed = getSecondsFromChronometer();

    }


    //**********************************************************************************************
    //  onStop()
    //**********************************************************************************************

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(LOG, "onStop");
        super.onStop();
    }

    //**********************************************************************************************
    //  doBindService()
    //**********************************************************************************************

    void doBindService() {
        if (DEBUG) Log.d(LOG, "doBindService()");

        if(!isBound()) {
            if (DEBUG) Log.d(LOG, "Binding Service");
            bindService(new Intent(MainActivity.this, LocationService.class), mConnection, Context.BIND_AUTO_CREATE);

        }
    }

    //**********************************************************************************************
    //  doUnBindService()
    //**********************************************************************************************
    void doUnbindService() {
        if (DEBUG) Log.d(LOG, "doUnbindService()");

        if (isBound()) {
            if (DEBUG) Log.d(LOG, "Unbinding Service");

            //detach our existing connection.
            unbindService(mConnection);
        }
    }

    //**********************************************************************************************
    //  ServiceConnection
    //**********************************************************************************************

    private ServiceConnection mConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.d(LOG, "onServiceConnected - Bound");
            mBoundService = ((LocationService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.d(LOG, "onServiceDisconnected - Unbound");
            mBoundService = null;


            //TODO: not sure if this is correct
            //is there another way that the system will unbind besides the user clicking Stop button
            //displayAlertDialog(getString(R.string.lost_connection), getString(R.string.connection_lost_restart));
            //stopLocationService();
        }
    };

    //**********************************************************************************************
    //  isBound()  returns true if service is connected, otherwise return false
    //**********************************************************************************************

    private boolean isBound(){
        return(mBoundService != null);
    }


    //**********************************************************************************************
    //  onPause() - Activity is partially obscured by another app but still partially visible and not the activity in focus
    //**********************************************************************************************

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(LOG, "onPause");

        //do nothing, we want to continue to collect location data until user clicks Stop button
        //other apps may run concurrently, such as music player

        super.onPause();
    }

    //**********************************************************************************************
    //  onResume()
    //**********************************************************************************************

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(LOG, "onResume");

        super.onResume();
    }

    //**********************************************************************************************
    //  BroadcastReceiver mMessageReceiver
    //**********************************************************************************************

    // Handler for received Intents. This will be called whenever an Intent
    // with an action named DATA_SERVICE_INTENT is broadcasted.
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");

            if (DEBUG) Log.d(LOG, "BroadcastReceiver - onReceive(): message: " + message);


            //let Service drive the update in the MainActivity UI
            //Service will send a message periodically until service is stopped
            if(mRequestedService && message.equals(LocationService.SERVICE_HAS_DATA)){
                if(isBound()){
                    getData();
                    displayCurrent();
                }
            }
            else if(message.contains(LocationService.GOOGLEAPI_CONNECTION_FAILURE)){
                String connectionResult = message.substring(LocationService.GOOGLEAPI_CONNECTION_FAILURE.length() - 1, (message.length() - 1));
                String errorMessage = "Google Play Services connection failure: " + connectionResult + ". Try again.";
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG);

            }
            else if(message.equals(LocationService.SERVICE_TIME_LIMIT)){

                displayAlertDialog(getString(R.string.time_limit), getString(R.string.reached_time_limit_30_minutes));
                stopRun();
            }

        }
    };

    private void displayAlertDialog(String title, String message){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                MainActivity.this);

        // set title
        alertDialogBuilder.setTitle(title);

        // set dialog message
        alertDialogBuilder
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        // if this button is clicked, close
                        // current activity
                    }
                })
                ;

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();

    }

    private void clearCacheAndUI(){
        mLocationList.clear();
        mTimeElapsed = 0;

        //clear any textfields
        mCoins.setText("0");
        mDistance.setText("0");
        mResults.setText("");

    }

    private void displayCurrent(){
        if (DEBUG) Log.d(LOG, "displayCurrent: intervalCount" + mLocationList.size());

        if (mLocationList.size() > 1){

            //update distance textview
            float distanceFeet = getDistance(1);
            mDistance.setText(String.format("%d", (int)distanceFeet));

            //number of coins earned
            int totalCoins = (int) (distanceFeet * FEET_COIN_CONVERSION);

            //compare previous totalCoins to current one
            int delta = totalCoins - mTotalCoins;

            if(delta > 0 ){
                playSound(R.raw.cat_meow);
            }

            //update coins
            mCoins.setText(String.format("%d", totalCoins));

            //save latest total number of coins
            mTotalCoins = totalCoins;


        }
    }


    private float getDistance(int units){

        float[] intervalDistance = new float[3];
        float totalDistance = 0.0f;

        //DEBUG
        //StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0 ; i < mLocationList.size() - 1; i++){
            Location.distanceBetween(mLocationList.get(i).getLatitude(),mLocationList.get(i).getLongitude(),mLocationList.get(i+1).getLatitude(),mLocationList.get(i + 1).getLongitude(), intervalDistance);
            totalDistance += Math.abs(intervalDistance[0]);
            //DEBUG
            //stringBuilder.append("\n" + i + ": " + intervalDistance[0] + " meters");

        }

        switch(units){
            case 0:
                //convert meters to miles
                totalDistance *= METER_MILE_CONVERSION;
                break;
            case 1:
                //convert to feet
                totalDistance *= METER_FEET_CONVERSION;
                break;

            default:
                //do nothing, units are in meters already
                break;

        }

        //TODO: delete
        //DEBUG
        // mStatsTextView.setText(stringBuilder);

        return totalDistance;
    }

    /**
     * Method to verify google play services on the device
     * */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                if (DEBUG) Log.d(LOG, "This device does not support Google Play Services.");
                finish();
            }
            return false;
        }
        return true;
    }


    protected static int getSecondsFromChronometer(){

        String string = mChronometer.getText().toString();

        String [] parts = string.split(":");

        // Wrong format, no value for you.
        if(parts.length < 2 || parts.length > 3)
            return 0;

        int seconds = 0, minutes = 0, hours = 0;

        if(parts.length == 2){
            seconds = Integer.parseInt(parts[1]);
            minutes = Integer.parseInt(parts[0]);
        }
        else if(parts.length == 3){
            seconds = Integer.parseInt(parts[2]);
            minutes = Integer.parseInt(parts[1]);
            hours = Integer.parseInt(parts[1]);
        }

        return seconds + (minutes*60) + (hours*3600);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.action_rewards:
                displayRewards();
                return true;
            case R.id.action_settings:
                //TODO:  openSettings();
                return true;

            default:
        }

        return super.onOptionsItemSelected(item);

    }

    private void displayRewards(){
        Intent intent = new Intent(this, ViewRewards.class);
        startActivity(intent);
    }


}
