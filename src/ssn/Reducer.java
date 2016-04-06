package ssn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import static java.util.Arrays.asList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import static ssn.Constants.*;
import static ssn.Utils.readAll;

public class Reducer {
    private final Map<Long, List<RequestToReducer>> id_requestToReducer = new ConcurrentHashMap<>();
    
    private AsynchronousServerSocketChannel mapperChannel;
    private AsynchronousChannelGroup channelGroup;
    
    private String masterAddress;
    private int masterPort;
    
    
    public void initialize() throws IOException {
        channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        
        mapperChannel = AsynchronousServerSocketChannel.open(channelGroup);
        mapperChannel.bind(new InetSocketAddress(5489));
        mapperChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                onMapperConnection(result);
            }

            @Override public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });
    }
    
    private void onMapperConnection(AsynchronousSocketChannel channel) {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(356);
        readAll(channel, null, new Utils.DataHandler<Void>() {
            @Override
            public void handleData(String data, Void id) {
                try {
                    final ObjectMapper m = new ObjectMapper();
                    RequestToReducer req = m.readValue(data, RequestToReducer.class);
                    
                    if (req.getMapperCount() <= 1)
                        throw new IllegalStateException("Request from mapper "+req.getRequestId()
                                +" states that mappers count is "+req.getMapperCount()+" <= 0");
                    
                    if (req.getMapperCount() == 1) { // if only one mapper, reduce immediatelly
                        sendResponceToMaster(req.getRequestId(), reduce(asList(req), REDUCE_LIMIT));
                        if (id_requestToReducer.remove(req.getRequestId()) != null) {
                            System.err.println("Ignoring previous request(s) from mapper because current request stated that mappers count is 1. Req id "+req.getRequestId());
                        }
                        return;
                    }
                    
                    id_requestToReducer.merge(req.getRequestId(), new LinkedList<>(),
                        (reqList, ignore) -> {
                            reqList.add(req);
                            if (reqList.size() >= req.getMapperCount()) {
                                try {
                                    sendResponceToMaster(req.getRequestId(), reduce(reqList, REDUCE_LIMIT));
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                                return null; // remove the mappping
                            } else {
                                return reqList; // with the new request from mapper added
                            }
                        });
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void fail(Throwable exc, Void id) {
                exc.printStackTrace();
            }
        }, buffer);
    }
        
        
    private PoiStats[] reduce(List<RequestToReducer> mapperRequests, int limit) {
        // put all pois in a sorted set, sorting by checkin count
        TreeSet<PoiStats> sortedSet = new TreeSet<>((PoiStats o1, PoiStats o2) -> o2.getCount() - o1.getCount());
        mapperRequests.parallelStream()
                .map(req -> asList(req.getPoiStats()))
                .sequential().forEach(sortedSet::addAll); // sequential because TreeSet is not thread safe
        
        // select the top pois from the sorted set
        int responceSize = Math.max(limit, sortedSet.size());
        PoiStats[] result = new PoiStats[responceSize];
        int i = 0;
        for (PoiStats poi : sortedSet) {
            if (i == responceSize) break;
            result[i++] = poi;
        }
        
        return result;
    }
    
    private void sendResponceToMaster(long requestId, PoiStats[] result) throws IOException {
        AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(channelGroup);
        ch.connect(new InetSocketAddress(masterAddress, masterPort), result, new CompletionHandler<Void, PoiStats[]>() {
            @Override  public void completed(Void ignore, PoiStats[] result) {
                byte[] reqToMasterB;
                try {
                    reqToMasterB = new ObjectMapper().writeValueAsString(new ReplyFromReducer(requestId, result)).getBytes();
                    Utils.writeAndClose(ch, reqToMasterB);
                } catch (JsonProcessingException ex) {
                    ex.printStackTrace();
                }
            }

            @Override public void failed(Throwable exc, PoiStats[] attachment) {
                exc.printStackTrace();
            }

        });
    }
    

}
                                            