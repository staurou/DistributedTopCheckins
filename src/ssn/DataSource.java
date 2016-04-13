package ssn;

import ssn.models.*;
import java.sql.*;
import java.util.*;
import static ssn.Constants.DATE_FORMAT;

public class DataSource {

    final com.mysql.jdbc.jdbc2.optional.MysqlDataSource ds;
    final String getCheckinsOrderByPoiSql = 
            "SELECT id, user, POI, POI_name, latitude, POI_category_id, longitude, time"
            + " FROM checkins"
            + " WHERE (latitude BETWEEN ? AND ?)"
            + " AND (longitude BETWEEN ? AND ?)"
            + " AND (time BETWEEN ? AND ?)"
            + " ORDER BY poi, user, time;";
    
    
    public DataSource(String host, String dbName, String username, String password) throws SQLException {
        ds = new com.mysql.jdbc.jdbc2.optional.MysqlDataSource();
        ds.setServerName(host);
        ds.setDatabaseName(dbName);
        ds.setUser(username);
        ds.setPassword(password);
    }
    
    public List<Checkin> getCheckinsOrderByPoiPhotos(RequestToMapper req) {
        LocationStatsRequest area = req.getLocationStatsRequest();
        String statement = "SELECT id, user, POI, POI_name, latitude, POI_category_id, longitude, time, photos"
            + " FROM checkins"
            + " WHERE (latitude BETWEEN "+area.getLatitudeFrom()+" AND "+area.getLatitudeTo()+")"
            + " AND (longitude BETWEEN "+area.getLongitudeFrom()+" AND "+area.getLongitudeTo()+")"
            + " AND (time BETWEEN '"+DATE_FORMAT.format(area.getTimeFrom())+"' AND '"+DATE_FORMAT.format(area.getTimeTo())+"')"
            + " AND ABS(FLOOR(latitude*100)+FLOOR(longitude*100))%"+req.getMappersCount()+"="+req.getMapperId()
            + " ORDER BY poi, photos;";
         
        List<Checkin> list = new LinkedList<>();
        
        System.out.println("Executing query "+statement);
        long start = System.currentTimeMillis();
        try (Connection conn = ds.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs = stat.executeQuery(statement);) {
            while (rs.next()) {
                list.add(new Checkin(rs.getInt("id"), rs.getInt("user"), rs.getString("POI").intern(),
                        rs.getString("POI_name").intern(), /*rs.getString("POI_category")*/null,
                        rs.getInt("POI_category_id")/*-1*/, rs.getDouble("latitude"),
                        rs.getDouble("longitude"), new java.util.Date(rs.getTimestamp("time").getTime()),
                        rs.getString("photos").intern()));
            }
            
        } catch (SQLException ex) {
            throw new IllegalStateException("Error communicating with database", ex);
        }
        System.out.println("Executed in "+((System.currentTimeMillis()-start)/1000.0)+", Fetched "+list.size()+" rows");
        return list;
    }
}
