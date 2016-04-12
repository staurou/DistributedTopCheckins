package ssn;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static java.lang.Math.*;
import java.net.*;
import java.nio.channels.*;
import static java.util.Arrays.asList;
import java.util.stream.Stream;
import static ssn.Constants.*;
import static ssn.Utils.*;
import ssn.models.*;

public class Mapper {
    
    private DataSource ds;
    
    private AsynchronousServerSocketChannel serverChannel;
    private AsynchronousChannelGroup channelGroup;
    
    private SocketAddress reducerAddress;
    
    public void initialize(int port, String masterAddress, SocketAddress reducerAddress) throws IOException, SQLException {
        ds = new DataSource(DEFAULT_DATASOURCE_HOST,  DEFAULT_DATASOURCE_SCHEMA, DEFAULT_DATASOURCE_USERNAME, DEFAULT_DATASOURCE_PASSWORD);
        
        this.reducerAddress = reducerAddress;
        
        channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                serverChannel.accept(null, this);
                if (!validateRemoteIpHost(result, asList(masterAddress, "localhost", "127.0.0.1"))) {
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
        System.out.println("Listening on "+port+"....");
        while (true) {
            try {
                channelGroup.awaitTermination(15, TimeUnit.DAYS);
            } catch (InterruptedException ex) {
            }
        }
    }
    
    private void onConnection(AsynchronousSocketChannel channel) {
//        final ByteBuffer buffer = ByteBuffer.allocateDirect(356);
        readAll(channel, null, new Utils.DataHandler<Void>() {
            @Override
            public void handleData(String data, Void id) {
                try {
                    ObjectMapper m = new ObjectMapper();
                    Request req = m.readValue(data, Request.class);
                    System.out.println("Request "+data);
                    if ("locationStats".equals(req.getAction())) {
                        sendToReducer(map(req.getBodyAs(RequestToMapper.class)));
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
        }, null);
    }
    
    private void sendToReducer(RequestToReducer result) throws IOException {
        connectWriteJsonClose(reducerAddress,
            Request.fromObject("locationStats", result),
            new SuccessHandler<Request>() {
                @Override public void success(Request data) {
                    System.out.println("Sent result to reducer. Request id "+result.getRequestId());
                    Arrays.fill(result.getPoiStats(), null);
                }

                @Override public void fail(Throwable exc, Request data) {
                    exc.printStackTrace();
                    Arrays.fill(result.getPoiStats(), null);
                }
            },
            channelGroup);
    }
    
    private RequestToReducer map(RequestToMapper requestToMapper) {
        final List<Checkin> checkins = ds.getCheckinsOrderByPoiPhotos(requestToMapper);
        final List<PoiStats> pois = new ArrayList<>();
        
        final PoiStats[] currentPoi = {null};
        Stream<Checkin> stream = checkins.stream();
        if (!requestToMapper.getLocationStatsRequest().isCountDuplicatePhotos()) {
            // because checkins are properlly sorted, duplicates must be subsequent
            final Checkin[] prev = {null}; // wrap prev checkin in an array to ba able to assign to it from lambda
            Predicate<Checkin> duplicateRemover = checkin -> {
                boolean isDuplicate = prev[0] != null && isDuplicate(checkin, prev[0]);
                prev[0] = checkin;
                return !isDuplicate;
            };
            stream = stream.filter(duplicateRemover);
        }
        stream.forEach(checkin -> {
            if (currentPoi[0] != null && Objects.equals(currentPoi[0].getPoi(), checkin.getPoi())) { // checkin of the same poi
                currentPoi[0].setCount(currentPoi[0].getCount()+1);
            } else { // poi has no more checkins
                if (currentPoi[0] != null) {
                    addSortedIfTop(currentPoi[0], pois, (poiA, poiB) ->  poiA.getCount()-poiB.getCount(), REDUCE_LIMIT);
                }
                currentPoi[0] = new PoiStats(checkin.getPoi(), checkin.getPoiName(), checkin.getLatitude(), checkin.getLongitude(), 1);
            }
        });
        checkins.clear();
        if (currentPoi[0] != null) {
            addSortedIfTop(currentPoi[0], pois, (poiA, poiB) ->  poiA.getCount()-poiB.getCount(), REDUCE_LIMIT);
        }
        
        final int poiCount = min(REDUCE_LIMIT, pois.size());
        PoiStats[] poiArray = pois.subList(0, poiCount).toArray(new PoiStats[poiCount]);
        pois.clear();
        
        return new RequestToReducer(requestToMapper.getRequestId(), requestToMapper.getMappersCount(), poiArray);
    }
    
    private static boolean isDuplicate(Checkin a, Checkin b) {
        return Objects.equals(a.getPoi(), b.getPoi())
                && a.getPhotos() != null && !"Not exists".equals(a.getPhotos())
                && b.getPhotos() != null && !"Not exists".equals(b.getPhotos())
                && Objects.equals(a.getPhotos(), b.getPhotos());
    }
    
    
    static final String USAGE = "MAPPER USAGE\n"
            + "Arguments: [-p PORT]"
            + " [-r REDUCER_ADDRESS [REDUCER_PORT]]"
            + " [-s MASTER_ADDRESS]"
            + "\nDefault PORT is "+DEFAULT_MAPPER_PORT
            + ", \nDefault MASTER_ADDRESS is localhost"
            + ", \nDefault REDUCER_ADDRESS is localhost"
            + ", \nDefault REDUCER_PORT is "+DEFAULT_REDUCER_PORT;
    
    public static void main(String[] args) throws IOException, SQLException {
        Mapper instance = new Mapper();
        
        Map<String, List<String>> options = parseArgs(args, Arrays.asList("-p", "-cp", "-s", "-r", "-h"));
        if (options.containsKey("-h")) {
            System.out.println(USAGE);
            System.exit(0);
        }
        
        final Integer pArg = firstIntOrNull(options.get("-p"));
        int port = pArg != null ? pArg : DEFAULT_MAPPER_PORT;
        final String mArg = firstOrNull(options.get("-s"));
        String master = mArg != null ? mArg : "localhost";
        
        String reducerAddress = "localhost";
        int reducerPort = DEFAULT_REDUCER_PORT;
        
        List<String> rArg = options.get("-r");
        if (rArg != null) {
            if (rArg.size() > 0) {
                reducerAddress = rArg.get(0);
            }
            if (rArg.size() > 1) {
                reducerPort = Integer.parseInt(rArg.get(1));
            }
        }
        
        instance.initialize(port, master, new InetSocketAddress(reducerAddress, reducerPort));
    }
}