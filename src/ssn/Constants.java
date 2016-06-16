package ssn;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public interface Constants {
    static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    static final int REDUCE_LIMIT = 10;
    
    static final String DEFAULT_DATASOURCE_HOST = "83.212.117.76";
    static final String DEFAULT_DATASOURCE_SCHEMA = "ds_systems_2016_omada35";
    static final String DEFAULT_DATASOURCE_USERNAME = "omada35";
    static final String DEFAULT_DATASOURCE_PASSWORD = "omada35db";
    
    static final String DEFAULT_IMAGE_FILEPATH = "disttopcheimages";
    
    static final int DEFAULT_MASTER_HTTP_PORT = 8000;
    static final int DEFAULT_MASTER_CLIENT_PORT = 25697;
    static final int DEFAULT_MASTER_CONTROL_PORT = 25698;
    static final int DEFAULT_REDUCER_PORT = 25700;
    static final int DEFAULT_MAPPER_PORT = 25701;
    
    static final int DEFAULT_MAPPER_FAIL_THRESHOLD = 3;
    static final int DEFAULT_CLIENT_TIMEOUT_SEC = 120;
    
}
