package ssn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import static ssn.Utils.*;

public class MapReduceMaster {
    private class Mapper {
        final String host;
        final int port;

        public Mapper(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
    
    private final List<Mapper> mappers = Arrays.asList(
            new Mapper("localhost", 45748),
            new Mapper("localhost", 45564),
            new Mapper("localhost", 45448)
    );
    
    private final Map<Long, AsynchronousSocketChannel> initialClientRequestChannels
            = new ConcurrentHashMap<>();
    
    private final AtomicLong requestIds = new AtomicLong(0);
    
    private AsynchronousServerSocketChannel clientChannel;
    private AsynchronousServerSocketChannel reducerChannel;
    private AsynchronousChannelGroup channelGroup;
    
    public void initialize() throws IOException {
        channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        
        clientChannel = AsynchronousServerSocketChannel.open(channelGroup);
        clientChannel.bind(new InetSocketAddress(5484));
        clientChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                onNewRequestConnection(result);
            }

            @Override public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });
        
        reducerChannel = AsynchronousServerSocketChannel.open(channelGroup);
        reducerChannel.bind(new InetSocketAddress(5485));
        reducerChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel result, Void attachment) {
                receiveFromReducer(result);
            }

            @Override public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });
    }
    
    private void onNewRequestConnection(AsynchronousSocketChannel channel) {
        long id = requestIds.incrementAndGet();
        final ByteBuffer buffer = ByteBuffer.allocateDirect(356);
        readAll(channel, id, new DataHandler<Long>() {
            @Override
            public void handleData(String data, Long id) {
                try {
                    final ObjectMapper m = new ObjectMapper();
                    LocationStatsRequest req = m.readValue(data, LocationStatsRequest.class);
                    initialClientRequestChannels.put(id, channel);
                    sendToMappers(id, req);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void fail(Throwable exc, Long id) {
                exc.printStackTrace();
            }
        }, buffer);
    }
    
    private void sendToMappers(long id, LocationStatsRequest req) throws IOException {
        RequestToMapper reqToMap = new RequestToMapper(id, mappers.size(), req);
        for (int i = 0; i < mappers.size(); i++) {
            final Mapper mapper = mappers.get(i);
            int mapperNumber = i;
            reqToMap.setMapperId(mapperNumber);
            try {
                byte[] reqToMapB;
                reqToMapB = new ObjectMapper().writeValueAsString(reqToMap).getBytes();
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
            } catch (JsonProcessingException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private void receiveFromReducer(AsynchronousSocketChannel channel){
        final ByteBuffer buffer = ByteBuffer.allocateDirect(356);
        readAll(channel, null, new DataHandler<Void>() {
            @Override
            public void handleData(String data, Void attachment) {
                try {
                  final ObjectMapper m = new ObjectMapper();
                  ReplyFromReducer rep = m.readValue(data, ReplyFromReducer.class );
                  sendToClient(rep);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void fail(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }

           
        }, buffer);
    }
    
    private void sendToClient(ReplyFromReducer rep) {
         long id = rep.getRequestId();
         AsynchronousSocketChannel ch = initialClientRequestChannels.remove(id);
         if (ch != null) {
             writeJsonAndClose(ch, rep, new SuccessHandler<ReplyFromReducer>() {

                 @Override
                 public void success(ReplyFromReducer data) {
                     System.out.println("Sent reply to client. Request id "+id);
                 }

                 @Override
                 public void fail(Throwable exc, ReplyFromReducer data) {
                     exc.printStackTrace();
                 }
             });
         } else {
             System.err.println("RequestID not found "+id);
         }            
    }
}
