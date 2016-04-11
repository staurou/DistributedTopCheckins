package ssn.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class Request {
    private String action;
    private String body;

    public Request() {
    }

    public Request(String action, String body) {
        this.action = action;
        this.body = body;
    }
    
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBody() {
        return body;
    }
    
    public <T> T getBodyAs(Class<T> clazz) {
        ObjectMapper m = new ObjectMapper();
        try {
            return m.readValue(body.replace("\\\"", "\""), clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Request body could not be converted to "+clazz, ex);
        }
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        try {
            ObjectMapper m = new ObjectMapper();
            return m.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Request could not be converted to JSON", ex);
        }
    }
    
    public static Request fromObject(String action, Object body) {
        try {
            ObjectMapper m = new ObjectMapper();
            return new Request(action, m.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Given body object could not be converted to JSON", ex);
        }
    }
    
}
