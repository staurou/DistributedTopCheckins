package ssn.models;

import java.util.Date;

public class Checkin {
    private long id;
    private long userId;
    private String poi;
    private String poiName;
    private String poiCategory;
    private long poiCategoryId;
    private double latitude;
    private double longitude;
    private Date time;
    private String photos;

    public Checkin() {
    }

    public Checkin(long id, long userId, String poi, String poiName,
            String poiCategory, long poiCategoryId, double latitude,
            double longitude, Date time, String photos) {
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
    
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
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

    public long getPoiCategoryId() {
        return poiCategoryId;
    }

    public void setPoiCategoryId(long poiCategoryId) {
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
