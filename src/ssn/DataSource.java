package ssn;

import ssn.models.*;
import java.sql.*;
import java.util.*;

public class DataSource {

    final com.mysql.jdbc.jdbc2.optional.MysqlDataSource ds;
    final String getCheckinsOrderByPoiSql = 
            "SELECT * FROM checkins"
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

    public List<Checkin> getCheckinsOrderByPoi(double latiduteFrom, double latiduteTo,
            double longtiduteFrom, double longtiduteTo,
            java.util.Date timeFrom, java.util.Date timeTo) {
        List<Checkin> list = new LinkedList<>();
        
        try (Connection conn = ds.getConnection();
            PreparedStatement stat = conn.prepareStatement(getCheckinsOrderByPoiSql);) {
            stat.setDouble(1, latiduteFrom);
            stat.setDouble(2, latiduteTo);
            stat.setDouble(3, longtiduteFrom);
            stat.setDouble(4, longtiduteTo);
            stat.setTimestamp(5, timeFrom!=null ? new Timestamp(timeFrom.getTime()) : null);
            stat.setTimestamp(6, timeTo!=null ? new Timestamp(timeTo.getTime()) : null);
            
            try (ResultSet rs = stat.executeQuery()) {
                while (rs.next()) {
                    list.add(new Checkin(rs.getInt("id"), rs.getInt("user"), rs.getString("POI"),
                            rs.getString("POI_name"), rs.getString("POI_category"),
                            rs.getInt("POI_category_id"), rs.getDouble("latitude"),
                            rs.getDouble("longitude"), new java.util.Date(rs.getTimestamp("time").getTime()),
                            rs.getString("photos")));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not connect to database", ex);
        }
        
        return list;
    }
}
