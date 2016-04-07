/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssn;
import ssn.models.Checkin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
/**
 *
 * @author sterg
 */
public class DataSource {
   private Connection connection;
   private PreparedStatement statement;//h protash
   private ResultSet resultSet;
   private boolean connectedToDatabase = false;
   
   public DataSource( String url, String username, String password) throws SQLException
   {         
      // connect to database
      connection = DriverManager.getConnection( url, username, password );
      connectedToDatabase = true;
      //setQuery( query );// set query and execute it
   }
   

   public List<Checkin> getCheckinsOrderByPoi(double longtiduteFrom,
           double longtiduteTo,double latiduteFrom,double latiduteTo,Date dateCaptureFrom,Date dateCaptureTo){
       List<Checkin> list = new LinkedList<Checkin>();
       try {
           statement = connection.prepareStatement("SELECT * FROM checkins WRERE (latitude BETWEEN latiduteFrom  AND latiduteTo) AND (longtidute BETWEEN longtiduteFrom  AND longtiduteTo) AND (dateCapture BETWEEN dateCaptureFrom  AND dateCaptureTo);");
           setQuery();
       } catch (SQLException sqlException) {
           sqlException.printStackTrace();
           System.exit(1);
       }
       while(resultSet.next()){
           //analoga me ta orismata toy constructor 
           list.add(new Checkin(resultSet.getString(),resultSet.getDouble(),resultSet.getDouble(),resultSet.getDate()));
       }
       return list;
   }

   
   // set new database query string
   public void setQuery( String query ) throws SQLException, IllegalStateException {
	   
        if ( !connectedToDatabase ){
            throw new IllegalStateException( "Not Connected to Database" );
        }

      resultSet = statement.executeQuery( query );// specify query and execute it
   }
   
   public void setQuery() throws SQLException, IllegalStateException {
	   
        if ( !connectedToDatabase ){
            throw new IllegalStateException( "Not Connected to Database" );
        }

      resultSet = statement.executeQuery();// specify query and execute it
   }

   // close Connection               
   public void disconnectFromDatabase(){              
      if ( connectedToDatabase ){           
         try{                                            
            resultSet.close();                        
            statement.close();                        
            connection.close();                       
         }                              
         catch ( SQLException sqlException ){                                            
            sqlException.printStackTrace();           
         }                              
         finally{                                            
            connectedToDatabase = false;              
         }                           
      }
   }          
}
