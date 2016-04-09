package ssn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Utils {
    private static final int DEFAULT_BUFFER_SIZE = 256;
    
    public static interface DataHandler<A> {
        void handleData(String data, A attatcment);
        void fail(Throwable exc, A attachment);
    }
    
    public static interface SuccessHandler<T> {
        void success(T data);
        void fail(Throwable exc, T data);
    }
    
    
    public static <A> void readAll(AsynchronousSocketChannel chanel, A attachment,
            DataHandler<A> handler, ByteBuffer buffer) {
        ByteBuffer b;
        if (buffer == null) {
            b = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
        } else {
            b = buffer;
        }
        final StringBuilder sb = new StringBuilder(b.capacity());
        b.clear();
        chanel.read(b, attachment, new CompletionHandler<Integer, A>() {
            @Override
            public void completed(Integer bytesRead, A attachment) {
                if (bytesRead < 0) {
                    handler.handleData(sb.toString(), attachment);
                    return;
                }
                sb.append(b.asCharBuffer(), 0, b.position()-0);
                b.clear();
                chanel.read(b, attachment, this);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.fail(exc, attachment);
            }
            
        });
    }
    
    public static <A> void writeAll(AsynchronousSocketChannel chanel, A attachment,
            DataHandler<A> handler, ByteBuffer b) {
        chanel.write(b, attachment, new CompletionHandler<Integer, A>() {
            @Override
            public void completed(Integer bytesRead, A attachment) {
                if (b.remaining() <= 0) {
                    handler.handleData(null, attachment);
                    return;
                }
                chanel.write(b, attachment, this);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.fail(exc, attachment);
            }
            
        });
    }
         
    
    public static void writeAndClose(AsynchronousSocketChannel ch, byte[] data, SuccessHandler<byte[]> handler) {
        SuccessHandler<byte[]> handle = nonNullOrDefault(handler);
        ch.write(ByteBuffer.wrap(data), null, new CompletionHandler<Integer, Void>() {
            @Override public void completed(Integer result, Void attachment) {
                try {
                    ch.close();
                    handle.success(data);
                } catch (IOException ex) {
                    handle.fail(ex, data);
                }
            }

            @Override public void failed(Throwable exc, Void attachment) {
                handle.fail(exc, data);
            }
        });
    }
    
    public static <T> void writeJsonAndClose(AsynchronousSocketChannel ch, T data, SuccessHandler<T> handler) {
        SuccessHandler<T> handle = nonNullOrDefault(handler);
        try {
            byte[] dataB;
            dataB = new ObjectMapper().writeValueAsBytes(data);
            writeAndClose(ch, dataB, new SuccessHandler<byte[]>() {
                @Override public void success(byte[] ignore) {
                    handle.success(data);
                }

                @Override public void fail(Throwable exc, byte[] ignore) {
                    handle.fail(exc, data);
                }

            });
        } catch (JsonProcessingException ex) {
            handle.fail(ex, data);
        }
    }
    
    public static <T> void connectWriteJsonClose(SocketAddress address, T data,
            SuccessHandler<T> handler, AsynchronousChannelGroup channelGroup) {
        SuccessHandler<T> handle = nonNullOrDefault(handler);
        try {
            AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(channelGroup);
            ch.connect(address, null, new CompletionHandler<Void, Void>() {
                @Override  public void completed(Void ignore, Void ignore2) {
                    writeJsonAndClose(ch, data, handle);
                }
                
                @Override public void failed(Throwable exc, Void attachment) {
                    handle.fail(exc, data);
                }
                
            });
        } catch (IOException ex) {
            handle.fail(ex, data);
        }
    }
    
    public static void connectWriteClose(SocketAddress address, byte[] data,
            SuccessHandler<byte[]> handler, AsynchronousChannelGroup channelGroup) {
        SuccessHandler<byte[]> handle = nonNullOrDefault(handler);
        try {
            AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(channelGroup);
            ch.connect(address, null, new CompletionHandler<Void, Void>() {
                @Override  public void completed(Void ignore, Void ignore2) {
                    writeAndClose(ch, data, handle);
                }
                
                @Override public void failed(Throwable exc, Void attachment) {
                    handle.fail(exc, data);
                }
                
            });
        } catch (IOException ex) {
            handle.fail(ex, data);
        }
    }
    
    private static <T> SuccessHandler<T> nonNullOrDefault(SuccessHandler<T> h) {
        return h != null ? h : new SuccessHandler<T>() {

            @Override
            public void success(T data) {}

            @Override
            public void fail(Throwable exc, T data) {
                exc.printStackTrace();
            }
        };
    }
    
    public static boolean validateRemoteIpHost(AsynchronousSocketChannel ch, Collection<String> allowedHosts) {
        try {
            if (! (ch.getRemoteAddress() instanceof InetSocketAddress)) {
                throw new IllegalArgumentException("Remote address is not an IP address");
            }
            return allowedHosts.contains(((InetSocketAddress) ch.getRemoteAddress()).getHostString());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not determine remote address", ex);
        }
    }
    
    public static Map<String, List<String>> parseArgs(String[] args, Collection<String> params) {
        Set<String> paramSet = new HashSet<>(params);
        Map<String, List<String>> result = new HashMap<>();
        
        String currentParam = null;
        result.put(null, new ArrayList<>());  // initial arguments not following any param specifier
        for (String arg : args) {
            if (paramSet.contains(arg)) {
                currentParam = arg;
                result.putIfAbsent(currentParam, new ArrayList());
            } else {
                result.get(currentParam).add(arg);
            }
        }
        
        return result;
    }
    
    public static <T> T firstOrNull(List<? extends T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }
    
    public static Integer firstIntOrNull(List<String> list) {
        String firstOrNull = firstOrNull(list);
        if (firstOrNull != null) {
            return Integer.parseInt(firstOrNull);
        } else {
            return null;
        }
    }
}
