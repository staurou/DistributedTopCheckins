package ssn;

import ssn.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static java.util.Arrays.asList;
import static ssn.Utils.*;
import static ssn.Constants.*;

public class Master {
    
    private class MapperRec {
        final String host;
        final int port;

        public MapperRec(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 29 * hash + Objects.hashCode(this.host);
            hash = 29 * hash + this.port;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MapperRec other = (MapperRec) obj;
            if (!Objects.equals(this.host, other.host)) {
                return false;
            }
            return this.port == other.port;
        }
        
    }
    
    private final ConcurrentLinkedQueue<MapperRec> mappers = new ConcurrentLinkedQueue<>();
    
    private final Map<Long, AsynchronousSocketChannel> initialClientRequestChannels
            = new ConcurrentHashMap<>();
    
    private final AtomicLong requestIds = new AtomicLong(0);
    
    private AsynchronousServerSocketChannel clientChannel;
    private AsynchronousServerSocketChannel controlChannel;
    private AsynchronousChannelGroup channelGroup;
    
    
    public void initialize(int clientPort, int controlPort, String reducerAddress, int reducerPort) throws IOException {
        channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        
        clientChannel = AsynchronousServerSocketChannel.open(channelGroup);
        clientChannel.bind(new InetSocketAddress(clientPort));
        clientChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                clientChannel.accept(null, this);
                onNewRequestConnection(result);
            }

            @Override public void failed(Throwable exc, Void attachment) {
                clientChannel.accept(null, this);
                exc.printStackTrace();
            }
        });
        
        controlChannel = AsynchronousServerSocketChannel.open(channelGroup);
        controlChannel.bind(new InetSocketAddress(controlPort));
        controlChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                controlChannel.accept(null, this);
                if (!validateRemoteIpHost(result, asList(reducerAddress, "localhost", "127.0.0.1"))) {
                    try {
                        System.err.println("Unknown host connection rejected "+result.getRemoteAddress());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return;
                }
                receiveControlRequest(result);
            }

            @Override public void failed(Throwable exc, Void attachment) {
                controlChannel.accept(null, this);
                exc.printStackTrace();
            }
        });
        System.out.println("Listening on "+clientPort+" for client requests and "+controlPort+" for control requests....");
        while (true) {
            try {
                channelGroup.awaitTermination(15, TimeUnit.DAYS);
            } catch (InterruptedException ex) {
            }
        }
    }
    
    public void addMapper(String host, int port) {
        mappers.add(new MapperRec(host, port));
    }
    
    public void removeMapper(String host, int port) {
        mappers.remove(new MapperRec(host, port));
    }
    
    private void onNewRequestConnection(AsynchronousSocketChannel channel) {
        long id = requestIds.incrementAndGet();
//        final ByteBuffer buffer = ByteBuffer.allocate(356);
        readAll(channel, id, new DataHandler<Long>() {
            @Override
            public void handleData(String data, Long id) {
                System.out.println("Request "+id+" "+data);
                try {
                    final ObjectMapper m = new ObjectMapper();
                    Request req = m.readValue(data, Request.class);
                    if ("locationStats".equals(req.getAction())) {
                        initialClientRequestChannels.put(id, channel);
                        sendToMappers(id, req.getBodyAs(LocationStatsRequest.class));
                    } else {
                        writeJsonAndClose(channel, new ErrorReply("Unknown action "+req.getAction(), 400), null);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    writeJsonAndClose(channel, new ErrorReply(ex.getMessage(), 500), null);
                }
            }

            @Override
            public void fail(Throwable exc, Long id) {
                exc.printStackTrace();
                writeJsonAndClose(channel, new ErrorReply(exc.getMessage(), 500), null);
            }
        });
    }
    
    private void sendToMappers(long id, LocationStatsRequest req) throws IOException {
        RequestToMapper reqToMap = new RequestToMapper(id, mappers.size(), req);
        int i = 0;
        for (MapperRec mapper : mappers) {
            int mapperNumber = i++;
            reqToMap.setMapperId(mapperNumber);
            try {
                byte[] reqToMapB;
                reqToMapB = new ObjectMapper().writeValueAsString(Request.fromObject("locationStats", reqToMap)).getBytes();
                System.out.println("Sending request #"+id+" to mapper #"+mapperNumber);
                connectWriteClose(new InetSocketAddress(mapper.host, mapper.port),
                    reqToMapB,
                    new SuccessHandler<byte[]>() {
                        @Override public void success(byte[] data) {
                            System.out.println("Sent request with id "+id+" to mapper #"+mapperNumber);
                        }

                        @Override public void fail(Throwable exc, byte[] data) {
                            exc.printStackTrace();
                        }
                    },
                    channelGroup);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private void receiveControlRequest(AsynchronousSocketChannel channel){
        final ByteBuffer buffer = ByteBuffer.allocateDirect(356);
        readAll(channel, null, new DataHandler<Void>() {
            @Override
            public void handleData(String data, Void attachment) {
                try {
                    System.out.println("Control request data "+data);
                    final ObjectMapper m = new ObjectMapper();
                    Request req = m.readValue(data, Request.class );
                if ("locationStatsSendResponce".equals(req.getAction())) {
                        locationStatsSendResponce(req.getBodyAs(ReplyFromReducer.class));
                    } else {
                        writeJsonAndClose(channel, new ErrorReply("Unknown action "+req.getAction(), 400), null);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    writeJsonAndClose(channel, new ErrorReply(ex.getMessage(), 500), null);
                }
            }

            @Override
            public void fail(Throwable exc, Void attachment) {
                exc.printStackTrace();
                writeJsonAndClose(channel, new ErrorReply(exc.getMessage(), 500), null);
            }

           
        });
    }
    
    private void locationStatsSendResponce(ReplyFromReducer rep) {
         long id = rep.getRequestId();
         System.out.println("resp len "+rep.getReducerReply().length);
         AsynchronousSocketChannel ch = initialClientRequestChannels.remove(id);
         if (ch != null) {
             writeJsonAndClose(ch, rep.getReducerReply(), new SuccessHandler<PoiStats[]>() {

                 @Override
                 public void success(PoiStats[] data) {
                     System.out.println("Sent reply to client. Request id "+id);
                 }

                 @Override
                 public void fail(Throwable exc, PoiStats[] data) {
                     exc.printStackTrace();
                 }
             });
         } else {
             System.err.println("RequestID not found "+id);
         }            
    }
    
    
    static final String USAGE = "MASTER USAGE\n"
            + "Arguments: [-p CLIENT_PORT] [-cp CONTROL_PORT]"
            + " -m MAPPER_ADDRESS MAPPER_PORT [MAPPER_ADDRESS MAPPER_PORT]..."
            + " [-r REDUCER_ADDRESS [REDUCER_PORT]]"
            + "\nDefault CLIENT_PORT is "+DEFAULT_MASTER_CLIENT_PORT
            + ", \nDefault CONTROL_PORT is "+DEFAULT_MASTER_CONTROL_PORT
            + ", \nDefault REDUCER_ADDRESS is localhost"
            + ", \nDefault REDUCER_PORT is "+DEFAULT_REDUCER_PORT;
    
    public static void main(String[] args) throws IOException {
        Master instance = new Master();
        
        Map<String, List<String>> options = parseArgs(args, Arrays.asList("-p", "-cp", "-m", "-r", "-h"));
        if (options.containsKey("-h") || options.size() <= 1) {
            System.out.println(USAGE);
            System.exit(0);
        }
        
        final Integer pArg = firstIntOrNull(options.get("-p"));
        int port = pArg != null ? pArg : DEFAULT_MASTER_CLIENT_PORT;
        
        final Integer cpArg = firstIntOrNull(options.get("-cp"));
        int controlPort = cpArg != null ? cpArg : DEFAULT_MASTER_CONTROL_PORT;
        
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
        
        List<String> mappers = options.get("-m");
        for (int i = 0; i < mappers.size(); i+=2) {
            instance.addMapper(mappers.get(i), Integer.parseInt(mappers.get(i+1)));
        }
        
        instance.initialize(port, controlPort, reducerAddress, reducerPort);
    }
}
