package ssn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    
    
    public static <A> void readAll(AsynchronousSocketChannel channel, A attachment,
            DataHandler<A> handler) {
        ByteBuffer b = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        final StringBuilder sb = new StringBuilder(b.capacity());
        
        channel.read(b, attachment, new CompletionHandler<Integer, A>() {
            @Override
            public void completed(Integer bytesRead, A attachment) {
                if (bytesRead < 0) {
                    handler.handleData(sb.toString(), attachment);
                    return;
                }
                sb.append(new String(b.array(), 0, bytesRead, StandardCharsets.UTF_8));
                b.clear();
                channel.read(b, attachment, this);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.fail(exc, attachment);
            }
            
        });
    }
    
//    public static <A> void writeAll(AsynchronousSocketChannel chanel, A attachment,
//            DataHandler<A> handler, ByteBuffer b) {
//        chanel.write(b, attachment, new CompletionHandler<Integer, A>() {
//            @Override
//            public void completed(Integer bytesRead, A attachment) {
//                if (b.remaining() <= 0) {
//                    handler.handleData(null, attachment);
//                    return;
//                }
//                chanel.write(b, attachment, this);
//            }
//
//            @Override
//            public void failed(Throwable exc, A attachment) {
//                handler.fail(exc, attachment);
//            }
//            
//        });
//    }
         
    
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
    
    
    public static void httpWriteJsonAndClose(HttpExchange he, int code, Object data) throws IOException {
        try {
            ObjectMapper m = new ObjectMapper();
            byte[] response = m.writeValueAsBytes(data);
            he.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            he.sendResponseHeaders(code, response.length);
            OutputStream os = he.getResponseBody();
            os.write(response);
            he.close();
        } catch (JsonProcessingException ex) {
            byte[] response = ex.getMessage().getBytes(StandardCharsets.UTF_8);
            he.sendResponseHeaders(500, response.length);
            OutputStream os = he.getResponseBody();
            os.write(response);
            he.close();
            ex.printStackTrace();
        }
    }
    
    
    public static <T, TO extends T> void addSortedIfTop(TO obj, List<T> list, Comparator<? super T> cmp, int n) {
        if (list.size() > n && cmp.compare(list.get(list.size()-1), obj) >= 0) {
            return;
        }
        boolean added = false;
        if (list instanceof RandomAccess) {
            for (int i = list.size()-1; i >= 0; i--) {
                if (cmp.compare(list.get(i), obj) >= 0) {
                    list.add(i+1, obj);
                    added = true;
                    break;
                }
            }
        } else {
            ListIterator<T> it = list.listIterator(list.size());
            while (it.hasPrevious()) {
                if (cmp.compare(it.previous(), obj) >= 0) {
                    it.next();
                    it.add(obj);
                    added = true;
                    break;
                }
            }
        }
        if (!added) list.add(0, obj);
        if (list.size() > n) list.remove(list.size()-1);
    }
}
