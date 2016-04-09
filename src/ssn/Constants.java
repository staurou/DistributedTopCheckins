package ssn;

public interface Constants {
    static final int REDUCE_LIMIT = 10;
    static final int DUPLICATE_TIME_THRESHOLD = 120000;
    
    static final String DEFAULT_DATASOURCE_URL = "localhost/test";
    static final String DEFAULT_DATASOURCE_USERNAME = "";
    static final String DEFAULT_DATASOURCE_PASSWORD = "";
    
    static final int DEFAULT_MASTER_CLIENT_PORT = 25697;
    static final int DEFAULT_MASTER_CONTROL_PORT = 25698;
    static final int DEFAULT_REDUCER_PORT = 25700;
    static final int DEFAULT_MAPPER_PORT = 25701;
}
