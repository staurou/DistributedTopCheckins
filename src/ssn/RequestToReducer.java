package ssn;

public class RequestToReducer {
    private long requestId;
    private PoiStats[] poiStats;

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public PoiStats[] getPoiStats() {
        return poiStats;
    }

    public void setPoiStats(PoiStats[] poiStats) {
        this.poiStats = poiStats;
    }
    
    
}
