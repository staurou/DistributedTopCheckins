package ssn;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestToMapper implements Cloneable {
    private long requestId;
    private int mappersCount;
    private LocationStatsRequest locationStatsRequest;
    private int mapperId;

    public RequestToMapper(long requestId, int mappersCount,
            LocationStatsRequest locationStatsRequest) {
        this.requestId = requestId;
        this.mappersCount = mappersCount;
        this.locationStatsRequest = locationStatsRequest;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getMappersCount() {
        return mappersCount;
    }

    public void setMappersCount(int mappersCount) {
        this.mappersCount = mappersCount;
    }

    public LocationStatsRequest getLocationStatsRequest() {
        return locationStatsRequest;
    }

    public void setLocationStatsRequest(LocationStatsRequest locationStatsRequest) {
        this.locationStatsRequest = locationStatsRequest;
    }

    public int getMapperId() {
        return mapperId;
    }

    public void setMapperId(int mapperId) {
        this.mapperId = mapperId;
    }
    
}
