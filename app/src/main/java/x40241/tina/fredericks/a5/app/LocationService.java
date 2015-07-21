package x40241.tina.fredericks.a5.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.apache.commons.lang3.time.StopWatch;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//import x40241.tina.fredericks.a5.db.DBHelper;

//TODO: go over this checklist
//http://blog.teamtreehouse.com/beginners-guide-location-android
//TODO: test that getting # seconds from Chronometer is working and accurate
//TODO: remove commented out code

/**
 * Created by Tina on 6/18/2015.
 */
public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    //DEBUG
    private static final String LOGTAG = "LOCATION_SERVICE";
    private static final boolean DEBUG = true;

    //CONSTANTS
    private static int NOTIFICATION = x40241.tina.fredericks.a5.app.R.string.service_started;
    private static int NOTIFICATION_STOPPED = x40241.tina.fredericks.a5.app.R.string.service_stopped;


    protected static final String SERVICE_HAS_DATA = "x40241.tina.fredericks.a5.app.SERVICE_HAS_DATA";
    protected static final String GOOGLEAPI_CONNECTION_FAILURE = "x40241.tina.fredericks.a5.app.GOOGLEAPI_CONNECTION_FAILURE";
    public static final String LOCATION_SERVICE_INTENT = "x40241.tina.fredericks.a5.app.LOCATION_SERVICE";
    public static final String SERVICE_TIME_LIMIT = "x40241.tina.fredericks.a5.app.SERVICE_TIME_LIMIT";


    // Track if a client Activity is bound to us.
    private boolean isBound = false;

    // Track if we've been started at least once.
    private boolean initialized   = false;

    //  for managing notifications
    private NotificationManager mNotificationManager;

    // Location list
    private List<Location> mLocationList = new ArrayList<Location>();
    StopWatch mStopWatch = new StopWatch();
    private Date mStartDate;
    long mTimeElapsed = 0; //in seconds

    //LocationRequest settings
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    private static long UPDATE_INTERVAL = 10 * 1000; //10 seconds
    private static long FASTEST_INTERVAL = 5 * 1000; //5 second
    private static long DISPLACEMENT = 1; //meters //displacement takes precedent over interval/fastestInterval
    private static long STOP_SERVICE_TIME_LIMIT = 30 * 60 * 1000; // 30 minutes
    private static final long LOCATION_DATA_PERIOD = 15; //number of seconds for the cycle of updating MainActivityt UI, if this number is too small, it will block the UI.
    private static final long LOCATION_ACCURACY = 20; //within 20 meter accuracy
    private boolean mIsTimeLimit = false;


    //executor
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> sScheduledFuture;

    //database objects
    //DBHelper mDBHelper;

    //**********************************************************************************************
    //  onCreate()
    //**********************************************************************************************

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(LOGTAG, "*** onCreate(): STARTING");

        if(DEBUG) Log.d(LOGTAG, "*** onCreate(): ENDING");
    }

    //**********************************************************************************************
    //  onStartCommand()
    //**********************************************************************************************

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

       if (DEBUG) {
            Log.d(LOGTAG, "*** onStartCommand(): STARTING; initialized=" + initialized);
            Log.d(LOGTAG, "*** onStartCommand(): flags=" + flags);
            Log.d(LOGTAG, "*** onStartCommand(): intent=" + intent);
        }

        if (initialized)
            return START_STICKY;
        initialize();

        // Display a notification about us starting. We put an icon in the status bar.
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showNotification();

        if (DEBUG) Log.d(LOGTAG, "*** onStart(): ENDING");
        // We want this service to continue running until it is explicitly stopped.
        return START_STICKY;
    }

    //**********************************************************************************************
    //  initialize() - occurs once by using initialized flag
    //**********************************************************************************************

    private void initialize() {
        if (DEBUG) Log.d(LOGTAG, "*** initialize()");

        //instantiate DBHelper, to be closed in onDestroy()
        //mDBHelper = new DBHelper(LocationService.this);

        //start gathering location data
        getRunData();

        //start stopwatch
        mStopWatch.start();

        //start sending updates to MainActivity
        sScheduledFuture = executor.scheduleWithFixedDelay(new Runnable(){
            public void run(){
                if (mLocationList.size() > 1 ) {
                    if (DEBUG) Log.d(LOGTAG, "SERVICE_HAS_DATA: # of locations:" + mLocationList.size());
                    sendMessage(SERVICE_HAS_DATA);
                }
            }}, 1, LOCATION_DATA_PERIOD, TimeUnit.SECONDS);

        initialized = true;

    }


    @Override
    public void onLocationChanged(Location location) {
        if (DEBUG) Log.d(LOGTAG, "onLocationChanged");

        if (DEBUG) Log.d(LOGTAG, "Accuracy: " + location.getAccuracy());

        //only track data when it has high level of accuracy
        if (location.getAccuracy() < LOCATION_ACCURACY){
            //update cache
            updateCache(location);
        }

        if(mStopWatch.getTime() > STOP_SERVICE_TIME_LIMIT && !mIsTimeLimit){
            mIsTimeLimit = true;
            sendMessage(SERVICE_TIME_LIMIT);
            stopServices();
        }

    }

    private void stopServices(){
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected())
            stopLocationUpdates();
        if (!executor.isTerminated())
            sScheduledFuture.cancel(true);
        if(mNotificationManager != null)
            mNotificationManager.cancel(NOTIFICATION_STOPPED);

    }

    //PERIODIC LOCATION UPDATES
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);//get location updates every 10 seconds
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);//not to exceed location updates every 5 seconds
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(LocationService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();

    }

    //PERIODIC LOCATION UPDATES
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    private void getRunData() {

        //create instance of LocationRequest
        createLocationRequest();

        //create instance of Google Play Services API client
        buildGoogleApiClient();

    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (DEBUG) Log.d(LOGTAG, "GoogleAPIClient Connection successful.");

        //start getting location data after there is a connection
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        //TODO: do something if connection suspended
        //need to stop service, return back to Main and stop run
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult) {
        //TODO: do something if connection failed
        if (DEBUG) Log.d(LOGTAG, "GoogleAPIClient Connection failed.");
        sendMessage(GOOGLEAPI_CONNECTION_FAILURE + connectionResult.toString());

    }

    //**********************************************************************************************
    //  sendMessage()
    // Send an Intent with an action name stored in "message". The Intent
    // sent should be received by the ReceiverActivity.
    //**********************************************************************************************

    private void sendMessage(String message) {
        if(DEBUG) Log.d("sender", "Broadcasting message");

        Intent intent = new Intent(LOCATION_SERVICE_INTENT);
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(LocationService.this).sendBroadcast(intent);

    }

    //**********************************************************************************************
    //  showNotification()
    //**********************************************************************************************

    private void showNotification() {

        // The PendingIntent to launch our activity if the user selects this notification


        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        Notification notification = new Notification.Builder(this)
                .setContentIntent(contentIntent)
                .setContentTitle(getText(x40241.tina.fredericks.a5.app.R.string.service_started))
                .setSmallIcon(x40241.tina.fredericks.a5.app.R.drawable.ic_launcher)
                .build();

        // Send the notification.
        mNotificationManager.notify(NOTIFICATION, notification);

    }

    //**********************************************************************************************
    //  LocalBinder
    //**********************************************************************************************


    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */

    public class LocalBinder extends Binder
    {
        public LocationService getService() {
            return LocationService.this;
        }
    }


    //**********************************************************************************************
    //  onBind()
    //**********************************************************************************************

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) Log.d(LOGTAG, "*** onBind()");
        if (DEBUG) {
            Log.d (LOGTAG, "*** onBind(): action="+intent.getAction());
            Log.d (LOGTAG, "*** onBind(): toString="+intent.toString());
        }
        isBound = true;

        return mBinder;
    }

    //**********************************************************************************************
    //  onUnbind()
    //**********************************************************************************************


    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) Log.d (LOGTAG, "*** onUnbind()");
        if (DEBUG) {
            Log.d (LOGTAG, "*** onUnbind(): action="+intent.getAction());
            Log.d (LOGTAG, "*** onUnbind(): toString="+intent.toString());
        }

        isBound = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        if (DEBUG) Log.d(LOGTAG, "*** onRebind()");
        if (DEBUG) {
            Log.d (LOGTAG, "*** onUnbind(): action="+intent.getAction());
            Log.d (LOGTAG, "*** onUnbind(): toString="+intent.toString());
        }    }


    //**********************************************************************************************
    //  updateCache()   /* saves data to cache*/
    //**********************************************************************************************

    private void  updateCache(Location location) {
        if (DEBUG) Log.d(LOGTAG, "updateCache()");

        //save current location
        mLocationList.add(location);


        //save time elapsed
        //get time from Chronometer in MainActivity

        //mTimeElapsed = TimeUnit.MILLISECONDS.toSeconds(mStopWatch.getTime());
        mTimeElapsed = MainActivity.getSecondsFromChronometer();    }



    //**********************************************************************************************
    //  insertRunDetails()
    // Send an Intent with an action name stored in "message". The Intent
    // sent should be received by the ReceiverActivity.
    //**********************************************************************************************
    /*
    private void insertRun(float distance, long elapsedTime){


        //TODO: calculate distance

        //TODO: calculate time elapsed in seconds (save it in mTimeElapsed)
        try{
            runId = mDBHelper.insertRunData(distance, mStartDate, elapsedTime);
        }catch(Exception e){
            e.printStackTrace();

        }
    }
*/



    @Override
    public void onDestroy() {
        if(DEBUG) Log.d(LOGTAG, "*** onDestroy()");

        //mNotificationManager.cancel(NOTIFICATION_STOPPED);

        /*
        if(mDBHelper != null){
            mDBHelper.close();
            mDBHelper = null;
        }
*/
        //
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        if(DEBUG) Log.d(LOGTAG, "*** onLowMemory()");
    }

    @Override
    public void onTrimMemory(int level) {
        if(DEBUG) Log.d(LOGTAG, "*** onTrimMemory()");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(LOGTAG, "*** onTaskRemoved()");
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
    }

    //**********************************************************************************************
    //  getLocationList()
    //**********************************************************************************************

    public List<Location> getLocationList(){
        return mLocationList;
    }

    //**********************************************************************************************
    //  saveAndClearRunData()
    //**********************************************************************************************

    public void saveAndClearRunData(){
        //TODO: call insert DB method
        mLocationList.clear();
    }

    //**********************************************************************************************
    //  getRunDetails()
    //  get run details by runId
    //**********************************************************************************************

    /*
    public RunInfo getRunDetails(long runId){

        RunInfo runInfo =  mDBHelper.getRunById(runId);
        return runInfo;

    }*/

    //TODO:
    //**********************************************************************************************
    //  getRunHistory()
    //  gets the most recent (10) run details
    //**********************************************************************************************

}



