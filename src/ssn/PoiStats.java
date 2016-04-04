package ssn;


public class PoiStats {
    private double longitude;
    private double latitude;
    private int count;
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

    public int getCount() {
        return count;
    }

    public void setCount(int counts) {
        this.count = counts;
    }

    public String getPOI() {
        return POI;
    }

    public void setPOI(String POI) {
        this.POI = POI;
    }
}
