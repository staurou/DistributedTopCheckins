/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssn;

/**
 *
 * @author stavroula
 */
public class ReplyFromReducer {
     private long requestId;
     reply [] reducerReply;

    public ReplyFromReducer(long requestId, reply[] reducerReply) {
        this.requestId = requestId;
        this.reducerReply = reducerReply;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public reply[] getReducerReply() {
        return reducerReply;
    }

    public void setReducerReply(reply[] reducerReply) {
        this.reducerReply = reducerReply;
    }
    
}
