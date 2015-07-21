package x40241.tina.fredericks.a5.app.model;

import java.util.Date;

/**
 * Created by Tina on 6/18/2015.
 */
public class RunInfo {

    private long mRunId;
    private Date dateTime;
    private float distanceMiles;
    private float elapsedMinutes;

    public long getRunId() {
        return mRunId;
    }

    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(Date dateTime) {
        this.dateTime = dateTime;
    }

    public float getDistanceMiles() {
        return distanceMiles;
    }

    public void setDistanceMiles(float distanceMiles) {
        this.distanceMiles = distanceMiles;
    }

    public float getElapsedMinutes() {
        return elapsedMinutes;
    }

    public void setElapsedMinutes(float elapsedMinutes) {
        this.elapsedMinutes = elapsedMinutes;
    }
}
