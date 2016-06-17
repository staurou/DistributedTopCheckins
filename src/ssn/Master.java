package ssn;

import ssn.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static java.util.Arrays.asList;
import static ssn.Utils.*;
import static ssn.Constants.*;

public class Master {
    
    private static class ClientRequest {
        final long id;
        final AsynchronousSocketChannel channel;
        final HttpExchange httpExchange;
        final Instant time;
        volatile boolean failed;

        public ClientRequest(long id, AsynchronousSocketChannel channel) {
            this.id = id;
            this.channel = channel;
            httpExchange = null;
            time = Instant.now();
        }

        public ClientRequest(long id, HttpExchange httpExchange) {
            this.id = id;
            this.httpExchange = httpExchange;
            channel = null;
            time = Instant.now();
        }
    }
    
    private static class MapperRec {
        final String host;
        final int port;
        final AtomicInteger fails = new AtomicInteger(0);

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
    
    private final Map<Long, ClientRequest> clientRequests
            = new ConcurrentHashMap<>();
    
    private final AtomicLong requestIds = new AtomicLong(0);
    
    private AsynchronousServerSocketChannel clientChannel;
    private AsynchronousServerSocketChannel controlChannel;
    private AsynchronousChannelGroup channelGroup;
    
    private String reducerAddress;
    private int reducerPort;
    
    private DataSource ds;
    private String imageFilePath = DEFAULT_IMAGE_FILEPATH;
    
    public void initialize(int clientPort, int controlPort, String reducerAddress,
            int reducerPort, int httpPort,
            String dbHost, String dbSchema, String dbUser, String dbPass) throws IOException, SQLException {
        this.reducerAddress = reducerAddress;
        this.reducerPort = reducerPort;
        
        ds = new DataSource(dbHost,  dbSchema, dbUser, dbPass);
        Files.createDirectories(Paths.get(imageFilePath));
        
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
        
        HttpServer server = HttpServer.create(new InetSocketAddress(httpPort), 0);
        server.createContext("/locationStats", this::onLocationStatsHttp);
        server.createContext("/", this::onHomeHttp);
        server.createContext("/newCheckin", this::onNewCheckinHttp);
        server.createContext("/photo", this::onPhotoHttp);
        server.createContext("/poiPhotos", this::onPoiPhotosHttp);
        server.setExecutor(null); // creates a default executor
        server.start();
        
        Timer timer = new Timer("tideup");
        timer.schedule(new TimerTask() {
            @Override public void run() {
                clearTimedoutRequests();
            }
        }, 2000, 2000);
        
        System.out.println("Listening on "+clientPort+" for client requests, on "+controlPort+" for control requests and on "+httpPort+" for http requests....");
        while (true) {
            try {
                channelGroup.awaitTermination(15, TimeUnit.DAYS);
            } catch (InterruptedException ex) {
            }
        }
    }
    
    public void addMapperPropagate(String host, int port) {
        System.out.println("Adding mapper "+host+":"+port);
        mappers.add(new MapperRec(host, port));
        connectWriteJsonClose(new InetSocketAddress(reducerAddress, reducerPort),
            Request.fromObject("mapperAddRemove", new MapperAddRemove(true, host, port)),
            new SuccessHandler<Request>() {
                @Override public void success(Request data) {
                    System.out.println("Sent add mapper "+host+":"+port+" request to reducer");
                }

                @Override public void fail(Throwable exc, Request data) {
                    System.err.println("Failed to send add mapper "+host+":"+port+" request to reducer");
                }
            },
            channelGroup);
    }
    
    public void removeMapperPropagate(String host, int port) {
        System.out.println("Removing mapper "+host+":"+port);
        mappers.remove(new MapperRec(host, port));
        connectWriteJsonClose(new InetSocketAddress(reducerAddress, reducerPort),
            Request.fromObject("mapperAddRemove", new MapperAddRemove(false, host, port)),
            new SuccessHandler<Request>() {
                @Override public void success(Request data) {
                    System.out.println("Sent remove mapper "+host+":"+port+" request to reducer");
                }

                @Override public void fail(Throwable exc, Request data) {
                    System.err.println("Failed to send remove mapper "+host+":"+port+" request to reducer");
                }
            },
            channelGroup);
    }
    
    public void addMapper(String host, int port) {
        System.out.println("Adding mapper "+host+":"+port);
        mappers.add(new MapperRec(host, port));
    }
    
    public void removeMapper(String host, int port) {
        System.out.println("Removing mapper "+host+":"+port);
        mappers.remove(new MapperRec(host, port));
    }
    
    private void onNewRequestConnection(AsynchronousSocketChannel channel) {
        long id = requestIds.incrementAndGet();
        readAll(channel, id, new DataHandler<Long>() {
            @Override
            public void handleData(String data, Long id) {
                System.out.println("Request "+id+" "+data);
                try {
                    final ObjectMapper m = new ObjectMapper();
                    Request req = m.readValue(data, Request.class);
                    if ("locationStats".equals(req.getAction())) {
                        clientRequests.put(id, new ClientRequest(id, channel));
                        sendToMappers(id, req.getBodyAs(LocationStatsRequest.class));
                    } else {
                        writeJsonAndClose(channel, new ErrorReply("Unknown action "+req.getAction(), 400), null);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    writeJsonAndClose(channel, new ErrorReply(ex.toString(), 500), null);
                }
            }

            @Override
            public void fail(Throwable exc, Long id) {
                exc.printStackTrace();
                writeJsonAndClose(channel, new ErrorReply(exc.getMessage(), 500), null);
            }
        });
    }
    
    private void onLocationStatsHttp(HttpExchange he) throws IOException {
        long id = requestIds.incrementAndGet();
        try {
            final ObjectMapper m = new ObjectMapper();
            m.setDateFormat(DATE_FORMAT);
            LocationStatsRequest req = m.readValue(he.getRequestBody(), LocationStatsRequest.class);
            clientRequests.put(id, new ClientRequest(id, he));
            sendToMappers(id, req);

        } catch (Exception ex) {
            ex.printStackTrace();
            markFailed(id);
            httpWriteJsonAndClose(he, 500, new ErrorReply(ex.toString(), 500));
        }

    }
    
    private void onHomeHttp(HttpExchange he) throws IOException {
        try (BufferedInputStream file = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream("home.html"))) {
            he.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            he.sendResponseHeaders(200, file.available());
            try (OutputStream os = new BufferedOutputStream(he.getResponseBody())) {
                int b;
                while ((b=file.read()) != -1) {
                    os.write(b);
                }
            }
            he.close();
        }
    }
    
    private void onPhotoHttp(HttpExchange he) throws IOException {
        try (BufferedInputStream file
                = new BufferedInputStream(
                new FileInputStream(imageFilePath+File.separator+he.getRequestURI().getQuery()));) {
            he.getResponseHeaders().set("Content-Type", "image/jpeg;");
            he.sendResponseHeaders(200, file.available());
            try (OutputStream os = new BufferedOutputStream(he.getResponseBody())) {
                int b;
                while ((b=file.read()) != -1) {
                    os.write(b);
                }
            }
            he.close();
        } catch (FileNotFoundException e) {
            httpWriteJsonAndClose(he, 404, new ErrorReply(e.toString(), 404));
        } catch (Exception e) {
            e.printStackTrace();
            httpWriteJsonAndClose(he, 500, new ErrorReply(e.toString(), 500));
        }
    }
    
        private void onPoiPhotosHttp(HttpExchange he) throws IOException {
        try {
            httpWriteJsonAndClose(he, 200, ds.getPoiPhotos(he.getRequestURI().getQuery()));
        } catch (Exception e) {
            e.printStackTrace();
            httpWriteJsonAndClose(he, 500, new ErrorReply(e.toString(), 500));
        }
    }
    
    private void onNewCheckinHttp(HttpExchange he) throws IOException {
        System.out.println("Create checkin request");
        try {
            final ObjectMapper m = new ObjectMapper();
            CheckinRequest req = m.readValue(he.getRequestBody(), CheckinRequest.class);
            String photoUrl = System.currentTimeMillis()+"_"+Math.floor(Math.random()*100)+".jpg";
            Files.write(Paths.get(imageFilePath, photoUrl), Base64.getDecoder().decode(req.getPhotoData().getBytes(StandardCharsets.UTF_8)));
            Checkin ch = new Checkin(0, 0, req.getPoi(), req.getPoiName(),
                    req.getPoiCategory(), req.getPoiCategory().hashCode(),
                    req.getLatitude(), req.getLongitude(), new Date(), "/photo?"+photoUrl);
            ds.createCheckin(ch);
            httpWriteJsonAndClose(he, 200, "ok");
        } catch (Exception ex) {
            ex.printStackTrace();
            httpWriteJsonAndClose(he, 500, new ErrorReply(ex.toString(), 500));
        }
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
                            int fails = mapper.fails.incrementAndGet();
                            System.err.println("Mapper "+mapper.host+":"+mapper.port+" failed. Fail count is now "+fails);
                            if (fails >= DEFAULT_MAPPER_FAIL_THRESHOLD) {
                                System.out.println("IMPORTANT! Mapper "+mapper.host+":"+mapper.port+" reached fail threshold and thus it will be removed");
                                removeMapperPropagate(mapper.host, mapper.port);
                            }
                            markFailed(id);
                        }
                    },
                    channelGroup);
            } catch (Exception ex) {
                ex.printStackTrace();
                int fails = mapper.fails.incrementAndGet();
                System.err.println("Mapper "+mapper.host+":"+mapper.port+" failed. Fail count is now "+fails);
                if (fails >= DEFAULT_MAPPER_FAIL_THRESHOLD) {
                    System.out.println("IMPORTANT! Mapper "+mapper.host+":"+mapper.port+" reached fail threshold and thus it will be removed");
                    removeMapperPropagate(mapper.host, mapper.port);
                }
                markFailed(id);
            }
        }
    }
    
    private void receiveControlRequest(AsynchronousSocketChannel channel){
        readAll(channel, null, new DataHandler<Void>() {
            @Override
            public void handleData(String data, Void attachment) {
                try {
                    final ObjectMapper m = new ObjectMapper();
                    Request req = m.readValue(data, Request.class );
                    if ("locationStatsSendResponce".equals(req.getAction())) {
                        locationStatsSendResponce(req.getBodyAs(ReplyFromReducer.class));
                    } else if ("mapperAddRemove".equals(req.getAction())) {
                        MapperAddRemove mapperAddRemove = req.getBodyAs(MapperAddRemove.class);
                        if (mapperAddRemove.isAdd()) {
                            addMapperPropagate(mapperAddRemove.getHost(), mapperAddRemove.getPort());
                        } else {
                            removeMapperPropagate(mapperAddRemove.getHost(), mapperAddRemove.getPort());
                        }
                    } else {
                        writeJsonAndClose(channel, new ErrorReply("Unknown action "+req.getAction(), 400), null);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    writeJsonAndClose(channel, new ErrorReply(ex.toString(), 500), null);
                }
            }

            @Override
            public void fail(Throwable exc, Void attachment) {
                exc.printStackTrace();
                writeJsonAndClose(channel, new ErrorReply(exc.getMessage(), 500), null);
            }

           
        });
    }
    
    private void locationStatsSendResponce(ReplyFromReducer rep) throws IOException {
         long id = rep.getRequestId();
         System.out.println("resp len "+rep.getReducerReply().length);
         ClientRequest req = clientRequests.remove(id);
         if (req.channel != null) {
             AsynchronousSocketChannel ch = req.channel;
             writeJsonAndClose(ch, rep.getReducerReply(), new SuccessHandler<PoiStats[]>() {

                 @Override public void success(PoiStats[] data) {
                     System.out.println("Sent reply to client. Request id "+id);
                 }

                 @Override public void fail(Throwable exc, PoiStats[] data) {
                     exc.printStackTrace();
                 }
             });
         } else {
            HttpExchange he = req.httpExchange;
            if (he != null) {
                httpWriteJsonAndClose(he, 200, rep.getReducerReply());
            } else {
                System.err.println("RequestID not found "+id);
            }
         }            
    }
    
    private void markFailed(long requestId) {
        clientRequests.computeIfPresent(requestId, (id, clientRequest) -> {
            clientRequest.failed = true;
            return clientRequest;
        });
    }
    
    private void clearTimedoutRequests() {
        Iterator<ClientRequest> it = clientRequests.values().iterator();

        Instant limit = Instant.now().minusSeconds(DEFAULT_CLIENT_TIMEOUT_SEC);
        while (it.hasNext()) {
            ClientRequest req = it.next();
            if (req.failed || limit.isAfter(req.time)) {
                it.remove();
                System.out.println("Cleared "+ (req.failed ? "failed" : "timed out") +" request #"+req.id);
                try {
                    if (req.channel != null) {
                        writeJsonAndClose(req.channel, new ErrorReply("Please try again shortly", 503), null);
                    } else if (req.httpExchange != null) {
                        httpWriteJsonAndClose(req.httpExchange, 503, new ErrorReply("Please try again shortly", 503));
                    }
                } catch (IOException e) {}
            }
        }
    }
    
    static final String USAGE = "MASTER USAGE\n"
            + "Arguments: [-p CLIENT_PORT] [-cp CONTROL_PORT]"
            + " -m MAPPER_ADDRESS MAPPER_PORT [MAPPER_ADDRESS MAPPER_PORT]..."
            + " [-r REDUCER_ADDRESS [REDUCER_PORT]] [-http HTTP_PORT]"
            + " [-dbhost DATASOURCE_HOST]"
            + " [-dbschema DATASOURCE_SCHEMA]"
            + " [-dbuser DATASOURCE_USERNAME]"
            + " [-dbpass DATASOURCE_PASSWORD]"
            + " [-addmapper|-removemapper]"
            + "\nDefault CLIENT_PORT is "+DEFAULT_MASTER_CLIENT_PORT
            + ", \nDefault CONTROL_PORT is "+DEFAULT_MASTER_CONTROL_PORT
            + ", \nDefault REDUCER_ADDRESS is localhost"
            + ", \nDefault REDUCER_PORT is "+DEFAULT_REDUCER_PORT
            + ", \nDefault HTTP_PORT is "+DEFAULT_MASTER_HTTP_PORT
            + ", \nDefault DATASOURCE_HOST is "+DEFAULT_DATASOURCE_HOST
            + ", \nDefault DATASOURCE_SCHEMA is "+DEFAULT_DATASOURCE_SCHEMA;
    
    public static void main(String[] args) throws IOException, SQLException {
        Master instance = new Master();
        
        Map<String, List<String>> options = parseArgs(args, Arrays.asList("-p", "-cp", "-m", "-r", "-h",
                "-http", "-addmapper", "-removemapper",
                "-dbhost", "-dbschema", "-dbuser", "-dbpass"));
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
        
        final Integer httpArg = firstIntOrNull(options.get("-http"));
        int httpPort = httpArg != null ? httpArg : DEFAULT_MASTER_HTTP_PORT;
        
        String dbHost = options.getOrDefault("-dbhost", asList(DEFAULT_DATASOURCE_HOST)).get(0);
        String dbSchema = options.getOrDefault("-dbschema", asList(DEFAULT_DATASOURCE_SCHEMA)).get(0);
        String dbUser = options.getOrDefault("-dbuser", asList(DEFAULT_DATASOURCE_USERNAME)).get(0);
        String dbPass = options.getOrDefault("-dbpass", asList(DEFAULT_DATASOURCE_PASSWORD)).get(0);
        
        if (options.containsKey("-addmapper") || options.containsKey("-removemapper")) {
            boolean isAdd = options.containsKey("-addmapper");
            AtomicInteger sentRequests = new AtomicInteger();
            for (MapperRec mapper : instance.mappers) {
                connectWriteJsonClose(new InetSocketAddress("localhost", controlPort),
                    Request.fromObject("mapperAddRemove", new MapperAddRemove(isAdd, mapper.host, mapper.port)),
                    new SuccessHandler<Request>() {
                        @Override public void success(Request data) {
                            System.out.println("Sent add mapper "+mapper.host+":"+mapper.port+" request");
                            if (sentRequests.incrementAndGet() == instance.mappers.size()) {
                                System.exit(0);
                            }
                        }

                        @Override public void fail(Throwable exc, Request data) {
                            System.err.println("Failed to send add mapper "+mapper.host+":"+mapper.port+" request");
                            if (sentRequests.incrementAndGet() == instance.mappers.size()) {
                                System.exit(0);
                            }
                        }
                    },
                    null);
            }
            while (sentRequests.get() != instance.mappers.size()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
            }
        }

        instance.initialize(port, controlPort, reducerAddress, reducerPort, httpPort,
                dbHost, dbSchema, dbUser, dbPass);
    }
}
