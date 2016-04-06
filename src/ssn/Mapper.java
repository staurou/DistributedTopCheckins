package ssn;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static java.lang.Math.*;
import static ssn.Constants.*;

public class Mapper {
    private static final int DUPLICATE_TIME_THRESHOLD = 120;
    private DataSource ds;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    
    public void initialize() throws IOException, SQLException {
        ds = new DataSource("83.212.117.76/ds_systems_2016", "omada35", "omada35db");
    }
    
    private RequestToReducer map(RequestToMapper requestToMapper) {
        final List<LocationStatsRequest> sr = requestToMapper.getMySubRequest();
        final List<List<Checkin>> checkins = new LinkedList<>();
        final List<PoiStats> pois = new LinkedList<>();

        sr.parallelStream().map((area) -> {
                // concurrently request checkins of areas from db
                return pool.submit(() -> {
                    final List<Checkin> areaCheckins = ds.getCheckinsOrderByPoi(
                            area.getLongitudeFrom(), area.getLatitudeTo(),
                            area.getLongitudeFrom(), area.getLongitudeTo(),
                            area.getCaptureTimeFrom(), area.getCaptureTimeTo());
                    synchronized (checkins) {
                        checkins.add(areaCheckins);
                    }
                });
            }
        ).forEach(dataFetchAction -> {
            // wait all data fetch actions to complete
            while (!dataFetchAction.isDone()) {
                try {
                    dataFetchAction.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) { /* retry */ }
            }
            
        });
        
        // because checkins are properlly sorted, duplicates must be subsequent
        final Checkin[] prev = {null};
        Predicate<Checkin> duplicateRemover = checkin -> {
            boolean isDuplicate = prev[0] != null && isDuplicate(checkin, prev[0]);
            prev[0] = checkin;
            return isDuplicate;
        };
        checkins.parallelStream().forEach(areaCheckins -> {
            final PoiStats[] currentPoi = {null};  // wrap current poi in an array to ba able to assign to it from lambda
            areaCheckins.stream()
                .filter(duplicateRemover)
                .forEach(checkin -> {
                    if (currentPoi[0] != null && Objects.equals(currentPoi[0].getPOI(), checkin.getPoi())) { // checkin of the same poi
                        currentPoi[0].setCount(currentPoi[0].getCount()+1);
                    } else { // poi has no more checkins
                        currentPoi[0] = new PoiStats(checkin.getPoi(), checkin.getLatitude(), checkin.getLongitude(), 0);
                        synchronized (pois) {
                            pois.add(currentPoi[0]);
                        }
                    }
                }
            );
        });
        
        pois.sort((poiA, poiB) -> poiB.getCount() - poiA.getCount());
        final int poiCount = max(REDUCE_LIMIT, pois.size());
        PoiStats[] poiArray = pois.subList(0, poiCount).toArray(new PoiStats[poiCount]);
        
        return new RequestToReducer(requestToMapper.getRequestId(), requestToMapper.getMappersCount(), poiArray);
    }
    
    private static boolean isDuplicate(Checkin a, Checkin b) {
        return Objects.equals(a.getPoi(), b.getPoi())
                && a.getUserId() == b.getUserId()
                && a.getTime() != null && a.getTime() != null
                && abs(a.getTime().toInstant().getEpochSecond() - b.getTime().toInstant().getEpochSecond()) < DUPLICATE_TIME_THRESHOLD;
    }
}