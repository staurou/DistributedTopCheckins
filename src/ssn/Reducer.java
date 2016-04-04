package ssn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static ssn.Utils.readAll;

public class Reducer {
    private static final int REDUCE_LIMIT = 10;
    
    private final Map<Long, List<RequestToReducer>> id_requestToReducer = new ConcurrentHashMap<>();
    
    private AsynchronousServerSocketChannel mapperChannel;
    private AsynchronousChannelGroup channelGroup;
    
    private final Object mapRequestMonitor = new Object();
    
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
                    List<RequestToReducer> reqList;
                    boolean respondNow = false;
                    synchronized (mapRequestMonitor) {
                        id_requestToReducer.putIfAbsent(req.getRequestId(), new LinkedList<>());
                        reqList = id_requestToReducer.get(req.getRequestId());
                        if (reqList.size() >= req.getMapperCount()) {
                            respondNow = true;
                            id_requestToReducer.remove(req.getRequestId());
                        } else {
                            reqList.add(req);
                        }
                    }
                    if (respondNow) {
                        sendResponceToMaster(req.getRequestId(), reduce(reqList, REDUCE_LIMIT));
                        
                    }
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
        TreeSet<PoiStats> sortedSet = new TreeSet<>(new Comparator<PoiStats>() {

            @Override public int compare(PoiStats o1, PoiStats o2) {
                return o2.getCount() - o1.getCount();
            }
        });
        
        for (RequestToReducer req : mapperRequests) {
            for (PoiStats poi : req.getPoiStats()) {
                sortedSet.add(poi);
            }
        }
        
        
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
                    Utils.connectAndWrite(ch, reqToMasterB);
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
                                            