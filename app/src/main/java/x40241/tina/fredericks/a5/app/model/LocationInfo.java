package x40241.tina.fredericks.a5.app.model;

import android.location.Location;

/**
 * Created by Tina on 6/16/2015.
 */
public class LocationInfo {
    private Location mLocation;
    private long mSecondsElapsed;

    public LocationInfo(Location location, long secondsElapsed){
        mLocation = location;
        mSecondsElapsed = secondsElapsed;
    }

    public Location getLocation() {
        return mLocation;
    }

    public void setLocation(Location location) {
        mLocation = location;
    }

    public long getSecondsElapsed() {
        return mSecondsElapsed;
    }

    public void setSecondsElapsed(long secondsElapsed) {
        mSecondsElapsed = secondsElapsed;
    }
}
