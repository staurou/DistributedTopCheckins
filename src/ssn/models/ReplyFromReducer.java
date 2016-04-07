package ssn.models;

public class ReplyFromReducer {
     private long requestId;
     PoiStats [] reducerReply;

    public ReplyFromReducer() {
    }

    public ReplyFromReducer(long requestId, PoiStats[] reducerReply) {
        this.requestId = requestId;
        this.reducerReply = reducerReply;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public PoiStats[] getReducerReply() {
        return reducerReply;
    }

    public void setReducerReply(PoiStats[] reducerReply) {
        this.reducerReply = reducerReply;
    }
    
}
