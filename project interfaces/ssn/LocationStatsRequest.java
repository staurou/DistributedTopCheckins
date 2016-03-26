/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssn;

import java.util.Date;

/**
 *
 * @author sta
 */
public class LocationStatsRequest {
    private double longitudeFrom;
    private double longitudeTo;
    private double latitudeFrom;
    private double latitudeTo;
    private Date captureTimeFrom;
    private Date captureTimeTo;

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

    public Date getCaptureTimeFrom() {
        return captureTimeFrom;
    }

    public void setCaptureTimeFrom(Date captureTimeFrom) {
        this.captureTimeFrom = captureTimeFrom;
    }

    public Date getCaptureTimeTo() {
        return captureTimeTo;
    }

    public void setCaptureTimeTo(Date captureTimeTo) {
        this.captureTimeTo = captureTimeTo;
    }
    
    
    
}
