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
     Reply [] reducerReply;

    public ReplyFromReducer(long requestId, Reply[] reducerReply) {
        this.requestId = requestId;
        this.reducerReply = reducerReply;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public Reply[] getReducerReply() {
        return reducerReply;
    }

    public void setReducerReply(Reply[] reducerReply) {
        this.reducerReply = reducerReply;
    }
    
}
