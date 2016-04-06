package ssn;


public class PoiStats {
    private String POI;
    private double latitude;
    private double longitude;
    private int count;

    public PoiStats() {
    }

    public PoiStats(String POI, double latitude, double longitude, int count) {
        this.POI = POI;
        this.latitude = latitude;
        this.longitude = longitude;
        this.count = count;
    }
    
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

    public void setCount(int count) {
        this.count = count;
    }

    public String getPOI() {
        return POI;
    }

    public void setPOI(String POI) {
        this.POI = POI;
    }
}
