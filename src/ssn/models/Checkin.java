package ssn.models;

import java.util.Date;

public class Checkin {
    private int id;
    private int userId;
    private String poi;
    private String poiName;
    private String poiCategory;
    private int poiCategoryId;
    private double latitude;
    private double longitude;
    private Date time;
    private String photos;

    public Checkin() {
    }

    public Checkin(int id, int userId, String poi, String poiName, String poiCategory, int poiCategoryId, double latitude, double longitude, Date time, String photos) {
        this.id = id;
        this.userId = userId;
        this.poi = poi;
        this.poiName = poiName;
        this.poiCategory = poiCategory;
        this.poiCategoryId = poiCategoryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.time = time;
        this.photos = photos;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

    public String getPoiCategory() {
        return poiCategory;
    }

    public void setPoiCategory(String poiCategory) {
        this.poiCategory = poiCategory;
    }

    public int getPoiCategoryId() {
        return poiCategoryId;
    }

    public void setPoiCategoryId(int poiCategoryId) {
        this.poiCategoryId = poiCategoryId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public String getPhotos() {
        return photos;
    }

    public void setPhotos(String photos) {
        this.photos = photos;
    }

    
}
