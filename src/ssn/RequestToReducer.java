package ssn;

public class RequestToReducer {
    private long requestId;
    private int mapperCount;
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

    public int getMapperCount() {
        return mapperCount;
    }

    public void setMapperCount(int mapperCount) {
        this.mapperCount = mapperCount;
    }
    
}
