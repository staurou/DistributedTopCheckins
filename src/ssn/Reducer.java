package ssn;

import ssn.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.time.Instant;
import java.util.*;
import static java.util.Arrays.asList;
import java.util.concurrent.*;
import static ssn.Constants.*;
import static ssn.Utils.*;

public class Reducer {
    
    private static class ClientRequest {
        final long id;
        List<RequestToReducer> requests;
        final Instant time;

        public ClientRequest(long id, List<RequestToReducer> requests) {
            this.id = id;
            this.requests = new LinkedList<>(requests);
            time = Instant.now();
        }
    }
    
    private final Map<Long, ClientRequest> clientRequests = new ConcurrentHashMap<>();
    
    private AsynchronousServerSocketChannel serverChannel;
    private AsynchronousChannelGroup channelGroup;
    
    private String masterAddress;
    private int masterPort;
    
    private final ConcurrentLinkedQueue<String> mappers = new ConcurrentLinkedQueue<>();
    
    public void initialize(int port, String masterAddress, int masterPort) throws IOException {
        this.masterAddress = masterAddress;
        this.masterPort = masterPort;
        
        channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        
        serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                serverChannel.accept(null, this);
                List<String> allowedOrigins = new ArrayList<>(mappers.size()+3);
                allowedOrigins.addAll(mappers);
                allowedOrigins.addAll(asList(masterAddress, "localhost", "127.0.0.1"));
                if (!validateRemoteIpHost(result, allowedOrigins)) {
                    try {
                        System.err.println("Unknown host connection rejected "+result.getRemoteAddress());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return;
                }
                onConnection(result);
            }

            @Override public void failed(Throwable exc, Void attachment) {
                serverChannel.accept(null, this);
                exc.printStackTrace();
            }
        });
        
        Timer timer = new Timer("tideup");
        timer.schedule(new TimerTask() {
            @Override public void run() {
                clearTimedoutRequests();
            }
        }, 2000, 2000);
        
        System.out.println("Listening on "+port+"....");
        while (true) {
            try {
                channelGroup.awaitTermination(15, TimeUnit.DAYS);
            } catch (InterruptedException ex) {
            }
        }
    }
    
    public void addMapper(String host) {
        mappers.add(host);
        System.out.println("Added one mapper at "+host);
    }
    
    public void removeMapper(String host) {
        mappers.remove(host);
        System.out.println("Removed one mapper at "+host);
    }
    
    private void onConnection(AsynchronousSocketChannel channel) {
        readAll(channel, null, new Utils.DataHandler<Void>() {
            @Override
            public void handleData(String data, Void id) {
                try {
                    final ObjectMapper m = new ObjectMapper();
                    Request req = m.readValue(data, Request.class);
                    if ("locationStats".equals(req.getAction())) {
                        handleMapperRequest(req.getBodyAs(RequestToReducer.class));
                    } else if ("mapperAddRemove".equals(req.getAction())) {
                        MapperAddRemove mapperAddRemove = req.getBodyAs(MapperAddRemove.class);
                        if (mapperAddRemove.isAdd()) {
                            addMapper(mapperAddRemove.getHost());
                        } else {
                            removeMapper(mapperAddRemove.getHost());
                        }
                    } else {
                        writeJsonAndClose(channel, new ErrorReply("Unknown action "+req.getAction(), 400), null);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    writeJsonAndClose(channel, new ErrorReply(ex.getMessage(), 500), null);
                }
            }

            @Override
            public void fail(Throwable exc, Void id) {
                exc.printStackTrace();
                writeJsonAndClose(channel, new ErrorReply(exc.getMessage(), 500), null);
            }
        });
    }
        
    private void handleMapperRequest(RequestToReducer req) throws IOException {
        if (req.getMapperCount() <= 0)
            throw new IllegalStateException("Request from mapper "+req.getRequestId()
                    +" states that mappers count is "+req.getMapperCount()+" <= 0");

        if (req.getMapperCount() == 1) { // if only one mapper, reduce immediatelly
            sendResponceToMaster(req.getRequestId(), reduce(asList(req), REDUCE_LIMIT));
            if (clientRequests.remove(req.getRequestId()) != null) {
                System.err.println("Ignoring previous request(s) from mapper because current request stated that mappers count is 1. Req id "+req.getRequestId());
            }
            return;
        }

        clientRequests.merge(req.getRequestId(), new ClientRequest(req.getRequestId(), asList(req)),
            (cliReq, ignore) -> {
                List<RequestToReducer> reqList = cliReq.requests;
                reqList.add(req);
                if (reqList.size() >= req.getMapperCount()) {
                    try {
                        sendResponceToMaster(req.getRequestId(), reduce(reqList, REDUCE_LIMIT));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return null; // remove the mappping
                } else {
                    return cliReq; // with the new request from mapper added
                }
            });
    }
        
    private PoiStats[] reduce(List<RequestToReducer> mapperRequests, int limit) {
        // put all pois in a sorted set, sorting by checkin count
        TreeSet<PoiStats> sortedSet = new TreeSet<>((PoiStats o1, PoiStats o2) -> {
                int countDiff = o2.getCount() - o1.getCount();
                return countDiff != 0
                        ? countDiff
                        : o1.getPoi().compareTo(o2.getPoi());
            });
        mapperRequests.stream()
                .map(req -> asList(req.getPoiStats()))
                .forEach(sortedSet::addAll); // sequential because TreeSet is not thread safe
        
        // select the top pois from the sorted set
        int responceSize = Math.min(limit, sortedSet.size());
        PoiStats[] result = new PoiStats[responceSize];
        int i = 0;
        for (PoiStats poi : sortedSet) {
            if (i == responceSize) break;
            result[i++] = poi;
        }
        
        return result;
    }
    
    private void sendResponceToMaster(long requestId, PoiStats[] result) throws IOException {
        System.out.println("Resp size "+result.length);
        Utils.connectWriteJsonClose(new InetSocketAddress(masterAddress, masterPort),
            Request.fromObject("locationStatsSendResponce", new ReplyFromReducer(requestId, result)),
            new SuccessHandler<Request>() {

                @Override
                public void success(Request data) {
                    System.out.println("Sent reply to Master. Request id "+requestId);
                }

                @Override
                public void fail(Throwable exc, Request data) {
                    exc.printStackTrace();
                }
            },
            channelGroup);
    }
    

    private void clearTimedoutRequests() {
        Iterator<ClientRequest> it = clientRequests.values().iterator();

        Instant limit = Instant.now().minusSeconds(DEFAULT_CLIENT_TIMEOUT_SEC);
        while (it.hasNext()) {
            ClientRequest req = it.next();
            if (limit.isAfter(req.time)) {
                it.remove();
                System.out.println("Cleared timed out request #"+req.id);
            }
        }
    }
    
    static final String USAGE = "REDUCER USAGE\n"
        + "Arguments: [-p PORT]"
        + " -m MAPPER_ADDRESS [MAPPER_ADDRESS]..."
        + " [-s MASTER_ADDRESS [MASTER_CONTROL_PORT]]"
        + "\nDefault PORT is "+DEFAULT_REDUCER_PORT
        + ",\nDefault MASTER_ADDRESS is localhost"
        + ",\nDefault MASTER_CONTROL_PORT is "+DEFAULT_MASTER_CONTROL_PORT;
    
    public static void main(String[] args) throws IOException {
        Reducer instance = new Reducer();
        
        Map<String, List<String>> options = parseArgs(args, Arrays.asList("-p", "-m", "-s", "-h"));
        if (options.containsKey("-h") || options.size() <= 1) {
            System.out.println(USAGE);
            System.exit(0);
        }
        
        final Integer pArg = firstIntOrNull(options.get("-p"));
        int port = pArg != null ? pArg : DEFAULT_REDUCER_PORT;
        
        String masterAddress = "localhost";
        int masterPort = DEFAULT_MASTER_CONTROL_PORT;
        
        List<String> rArg = options.get("-s");
        if (rArg != null) {
            if (rArg.size() > 0) {
                masterAddress = rArg.get(0);
            }
            if (rArg.size() > 1) {
                masterPort = Integer.parseInt(rArg.get(1));
            }
        }
        
        List<String> mappers = options.get("-m");
        mappers.stream().forEach(instance::addMapper);
        
        instance.initialize(port, masterAddress, masterPort);
    }
}
                                            