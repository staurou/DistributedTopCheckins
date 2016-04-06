package ssn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;


public class Utils {
    private static final int DEFAULT_BUFFER_SIZE = 256;
    
    public static interface DataHandler<A> {
        void handleData(String data, A attatcment);
        void fail(Throwable exc, A attachment);
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
         
    
    public static void writeAndClose(AsynchronousSocketChannel ch, byte[] data) {
        ch.write(ByteBuffer.wrap(data), null, new CompletionHandler<Integer, Void>() {
            @Override public void completed(Integer result, Void attachment) {
                try {
                    ch.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            @Override public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });
    }
}
