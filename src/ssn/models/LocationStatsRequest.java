package ssn.models;

import java.util.Date;

public class LocationStatsRequest {
    private double longitudeFrom;
    private double longitudeTo;
    private double latitudeFrom;
    private double latitudeTo;
    private Date timeFrom;
    private Date timeTo;
    
    private boolean countDuplicatePhotos;
    
    
    public double getLongitudeFrom() {
        return longitudeFrom;
    }

    public void setLongitudeFrom(double longitudeFrom) {
        this.longitudeFrom = longitudeFrom;
    }

    public double getLongitudeTo() {
        return longitudeTo;
    }

    public void setLongitudeTo(double longitudeTo) {
        this.longitudeTo = longitudeTo;
    }

    public double getLatitudeFrom() {
        return latitudeFrom;
    }

    public void setLatitudeFrom(double latitudeFrom) {
        this.latitudeFrom = latitudeFrom;
    }

    public double getLatitudeTo() {
        return latitudeTo;
    }

    public void setLatitudeTo(double latitudeTo) {
        this.latitudeTo = latitudeTo;
    }

    public Date getTimeFrom() {
        return timeFrom;
    }

    public void setTimeFrom(Date timeFrom) {
        this.timeFrom = timeFrom;
    }

    public Date getTimeTo() {
        return timeTo;
    }

    public void setTimeTo(Date timeTo) {
        this.timeTo = timeTo;
    }

    public boolean isCountDuplicatePhotos() {
        return countDuplicatePhotos;
    }

    public void setCountDuplicatePhotos(boolean countDuplicatePhotos) {
        this.countDuplicatePhotos = countDuplicatePhotos;
    }
    
}
