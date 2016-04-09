package ssn.models;


public class PoiStats {
    private String poi;
    private String poiName;
    private double latitude;
    private double longitude;
    private int count;

    public PoiStats() {
    }

    public PoiStats(String poi, String poiName, double latitude, double longitude, int count) {
        this.poi = poi;
        this.poiName = poiName;
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

    public String getPoi() {
        return poi;
    }

    public void setPoi(String poi) {
        this.poi = poi;
    }

    public String getPoiName() {
        return poiName;
    }

    public void setPoiName(String poiName) {
        this.poiName = poiName;
    }

}
