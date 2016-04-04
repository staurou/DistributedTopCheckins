package ssn;


public class PoiStats {
    private double longitude;
    private double latitude;
    private int counts;
    private String POI;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public int getCounts() {
        return counts;
    }

    public void setCounts(int counts) {
        this.counts = counts;
    }

    public String getPOI() {
        return POI;
    }

    public void setPOI(String POI) {
        this.POI = POI;
    }
}
